package com.yaseen.aicore.client;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Serializes TTS chunks so long Arabic replies are spoken in order rather than overlapping. */
public final class VoicePlayback {
    private static final BlockingQueue<byte[]> QUEUE = new LinkedBlockingQueue<>(32);
    private static volatile boolean workerStarted;

    public static void play(byte[] wav) {
        if (wav == null || wav.length == 0) return;
        if (!QUEUE.offer(wav)) return;
        startWorker();
    }

    private static synchronized void startWorker() {
        if (workerStarted) return;
        workerStarted = true;
        Thread worker = new Thread(() -> {
            try {
                while (true) {
                    byte[] next = QUEUE.take();
                    playBlocking(next);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                workerStarted = false;
            }
        }, "aicore-tts-playback");
        worker.setDaemon(true);
        worker.start();
    }

    private static void playBlocking(byte[] wav) {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))) {
            Clip clip = AudioSystem.getClip();
            try {
                clip.open(in);
                clip.start();
                while (clip.isRunning()) Thread.sleep(20L);
            } finally {
                clip.close();
            }
        } catch (Exception ignored) {}
    }

    private VoicePlayback() {}
}
