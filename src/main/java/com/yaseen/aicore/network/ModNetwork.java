package com.yaseen.aicore.network;

import com.yaseen.aicore.AICompanion;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    public static final int MAX_VOICE_BYTES = 8 * 1024 * 1024;       // server -> client TTS replies
    public static final int MAX_VOICE_UPLOAD_BYTES = 1024 * 1024;   // client -> server (client records <= ~640 KB)
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(AICompanion.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static int id = 0;

    public static void init() {
        CHANNEL.registerMessage(id++, VoiceDataPacket.class,
                VoiceDataPacket::encode, VoiceDataPacket::decode, VoiceDataPacket::handle);
        CHANNEL.registerMessage(id++, VoiceReplyPacket.class,
                VoiceReplyPacket::encode, VoiceReplyPacket::decode, VoiceReplyPacket::handle);
    }

    private ModNetwork() {}
}
