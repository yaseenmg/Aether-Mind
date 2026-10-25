package com.yaseen.aicore.network;

import com.yaseen.aicore.ai.GroqVoice;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoiceDataPacket {
    private static final long COOLDOWN_MS = 2500L;
    private static final ConcurrentHashMap<UUID,Long> LAST = new ConcurrentHashMap<>();
    private final byte[] wav;

    public VoiceDataPacket(byte[] wav) { this.wav = wav; }

    public static void encode(VoiceDataPacket msg, FriendlyByteBuf buf) {
        buf.writeByteArray(msg.wav);
    }

    public static VoiceDataPacket decode(FriendlyByteBuf buf) {
        return new VoiceDataPacket(buf.readByteArray(ModNetwork.MAX_VOICE_UPLOAD_BYTES));
    }

    public static void handle(VoiceDataPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            var sender = ctx.getSender();
            if (sender == null) return;
            long now = System.currentTimeMillis();
            if (LAST.size() > 128) LAST.values().removeIf(t -> now - t > 120_000L);
            Long previous = LAST.put(sender.getUUID(), now);
            if (previous != null && now - previous < COOLDOWN_MS) return;
            GroqVoice.transcribeAndAsk(sender, msg.wav);
        });
        ctx.setPacketHandled(true);
    }
}
