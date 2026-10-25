package com.yaseen.aicore.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public final class VoiceReplyPacket {
    private final byte[] wav;

    public VoiceReplyPacket(byte[] wav) { this.wav = wav; }

    public static void encode(VoiceReplyPacket msg, FriendlyByteBuf buf) {
        buf.writeByteArray(msg.wav);
    }

    public static VoiceReplyPacket decode(FriendlyByteBuf buf) {
        return new VoiceReplyPacket(buf.readByteArray(ModNetwork.MAX_VOICE_BYTES));
    }

    public static void handle(VoiceReplyPacket msg, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.yaseen.aicore.client.VoicePlayback.play(msg.wav)));
        ctx.setPacketHandled(true);
    }
}
