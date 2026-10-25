package com.yaseen.aicore.client;

import com.yaseen.aicore.AICompanion;
import com.yaseen.aicore.network.ModNetwork;
import com.yaseen.aicore.network.VoiceDataPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;

@Mod.EventBusSubscriber(modid = AICompanion.MOD_ID, value = Dist.CLIENT)
public final class VoiceClientTick {
    private static final int SAMPLE_RATE = 16000;
    private static final int MAX_RECORDING_BYTES = 2_500_000; // enough for ~20s at 48 kHz; downsampled before upload
    private static final Object LOCK = new Object();
    private static TargetDataLine line;
    private static ByteArrayOutputStream buffer;
    private static volatile boolean recording;
    private static volatile boolean awaitRelease;
    private static volatile boolean limitReached;
    private static Thread captureThread;
    private static int tailTicks;
    private static AudioFormat captureFormat;

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean pressed = VoiceClient.PUSH_TO_TALK.isDown();
        if (pressed && !recording && !awaitRelease && mc.player != null) start();
        if (!pressed && recording) {
            // Keep recording ~200ms after release so the end of the last word isn't clipped.
            if (++tailTicks >= 4) { tailTicks = 0; stopAndSend(); }
        } else {
            tailTicks = 0;
        }
        if (!pressed && !recording && awaitRelease) {
            awaitRelease = false;
            if (limitReached) { limitReached = false; stopAndSend(); }
        }
    }

    private static void start() {
        Minecraft mc = Minecraft.getInstance();
        synchronized (LOCK) {
            if (recording) return;
            try {
                AudioFormat format = chooseCaptureFormat();
                TargetDataLine newLine = AudioSystem.getTargetDataLine(format);
                newLine.open(format);
                newLine.start();
                line = newLine;
                captureFormat = format;
                buffer = new ByteArrayOutputStream();
                recording = true;
                limitReached = false;
                awaitRelease = false;
                captureThread = new Thread(() -> captureLoop(newLine), "aicore-microphone");
                captureThread.setDaemon(true);
                captureThread.start();
                if (mc.player != null) mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("🎤 تسجيل... اترك V للإرسال"), true);
            } catch (Exception e) {
                recording = false;
                line = null;
                buffer = null;
                if (mc.player != null) mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("الميكروفون غير متاح: " + e.getClass().getSimpleName()), true);
            }
        }
    }

    private static void captureLoop(TargetDataLine captureLine) {
        byte[] data = new byte[4096];
        try {
            while (recording && captureLine == line) {
                int n = captureLine.read(data, 0, data.length);
                if (n <= 0) continue;
                synchronized (LOCK) {
                    if (buffer == null) break;
                    if (buffer.size() + n >= MAX_RECORDING_BYTES) {
                        buffer.write(data, 0, Math.max(0, MAX_RECORDING_BYTES - buffer.size()));
                        recording = false;
                        limitReached = true;
                        awaitRelease = true;
                        break;
                    }
                    buffer.write(data, 0, n);
                }
            }
        } catch (Exception ignored) {
            recording = false;
        }
    }

    private static void stopAndSend() {
        byte[] pcm;
        synchronized (LOCK) {
            if (!recording && buffer == null) return;
            recording = false;
            try {
                if (line != null) {
                    line.stop();
                    line.close();
                }
            } catch (Exception ignored) {}
            line = null;
            pcm = buffer == null ? new byte[0] : buffer.toByteArray();
            buffer = null;
            captureThread = null;
        }

        Minecraft mc = Minecraft.getInstance();
        if (pcm.length < 3200 || mc.player == null) return;
        AudioFormat sourceFormat = captureFormat;
        captureFormat = null;
        if (sourceFormat == null) sourceFormat = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        pcm = to16kMonoPcm(pcm, sourceFormat);
        byte[] cleaned = cleanPcm(pcm);
        if (cleaned == null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("🎤 ما سمعت صوت واضح، قرّب من المايك وأعد"), true);
            return;
        }
        pcm = cleaned;
        try {
            byte[] wav = WavUtil.wrapPcm16Mono(pcm, SAMPLE_RATE);
            if (wav.length > ModNetwork.MAX_VOICE_BYTES) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("التسجيل أطول من الحد المسموح."), true);
                return;
            }
            ModNetwork.CHANNEL.sendToServer(new VoiceDataPacket(wav));
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("🎤 تم الإرسال..."), true);
        } catch (Exception e) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("تعذر إرسال التسجيل."), true);
        }
    }

    private static AudioFormat chooseCaptureFormat() {
        AudioFormat[] candidates = new AudioFormat[]{
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 16000, 16, 1, 2, 16000, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 1, 2, 48000, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 1, 2, 44100, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 2, 4, 48000, false),
                new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4, 44100, false)
        };
        for (AudioFormat f : candidates) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, f);
            if (AudioSystem.isLineSupported(info)) return f;
        }
        throw new IllegalStateException("No supported PCM microphone format");
    }

    /** Converts the capture device's PCM to the exact 16 kHz mono PCM expected by the STT path. */
    private static byte[] to16kMonoPcm(byte[] pcm, AudioFormat source) {
        if (source.getSampleSizeInBits() != 16 || !AudioFormat.Encoding.PCM_SIGNED.equals(source.getEncoding())) return pcm;
        int channels = Math.max(1, source.getChannels());
        int frameBytes = source.getFrameSize();
        int frames = pcm.length / frameBytes;
        if (frames <= 0) return new byte[0];
        double rate = source.getSampleRate();
        int outFrames = Math.max(1, (int) Math.round(frames * SAMPLE_RATE / rate));
        short[] mono = new short[frames];
        boolean big = source.isBigEndian();
        for (int i = 0; i < frames; i++) {
            long sum = 0;
            int base = i * frameBytes;
            for (int ch = 0; ch < channels; ch++) {
                int o = base + ch * 2;
                int lo = pcm[o] & 0xFF, hi = pcm[o + 1] & 0xFF;
                int v = big ? (short)((hi << 8) | lo) : (short)((lo) | (hi << 8));
                sum += v;
            }
            mono[i] = (short)(sum / channels);
        }
        byte[] out = new byte[outFrames * 2];
        for (int i = 0; i < outFrames; i++) {
            double src = i * (frames - 1.0) / Math.max(1, outFrames - 1);
            int a = (int)Math.floor(src), b = Math.min(frames - 1, a + 1);
            double f = src - a;
            int v = (int)Math.round(mono[a] * (1.0 - f) + mono[b] * f);
            out[2*i] = (byte)(v & 0xFF);
            out[2*i+1] = (byte)((v >>> 8) & 0xFF);
        }
        return out;
    }

    /** Trims leading/trailing silence, boosts quiet recordings, rejects near-silence (Whisper hallucinates on it). */
    private static byte[] cleanPcm(byte[] pcm) {
        int samples = pcm.length / 2;
        short[] s = new short[samples];
        int peak = 0;
        for (int i = 0; i < samples; i++) {
            s[i] = (short) ((pcm[2 * i] & 0xFF) | (pcm[2 * i + 1] << 8));
            peak = Math.max(peak, Math.abs((int) s[i]));
        }
        if (peak < 500) return null;
        int thr = Math.max(300, (int) (peak * 0.08));
        int first = -1, last = -1;
        for (int i = 0; i < samples; i++) {
            if (Math.abs((int) s[i]) > thr) { if (first < 0) first = i; last = i; }
        }
        if (first < 0) return null;
        int pad = SAMPLE_RATE / 5; // 200ms
        first = Math.max(0, first - pad);
        last = Math.min(samples - 1, last + pad);
        if (last - first < SAMPLE_RATE / 4) return null; // < 250ms
        double gain = Math.min(8.0, 26000.0 / peak);
        if (gain < 1.0) gain = 1.0;
        byte[] out = new byte[(last - first + 1) * 2];
        for (int i = first, o = 0; i <= last; i++, o += 2) {
            int v = (int) Math.max(-32768, Math.min(32767, Math.round(s[i] * gain)));
            out[o] = (byte) (v & 0xFF);
            out[o + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }

    private VoiceClientTick() {}
}
