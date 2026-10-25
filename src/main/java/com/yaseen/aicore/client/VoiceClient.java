package com.yaseen.aicore.client;

import com.yaseen.aicore.AICompanion;
import com.yaseen.aicore.network.ModNetwork;
import com.yaseen.aicore.network.VoiceDataPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Mod.EventBusSubscriber(modid = AICompanion.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class VoiceClient {
    public static final KeyMapping PUSH_TO_TALK = new KeyMapping("key.aicore.push_to_talk", GLFW.GLFW_KEY_V, "key.categories.aicore");

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(PUSH_TO_TALK);
    }

    private VoiceClient() {}
}
