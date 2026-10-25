package com.yaseen.aicore;

import com.mojang.logging.LogUtils;
import com.yaseen.aicore.entity.ModEntities;
import com.yaseen.aicore.event.ServerEvents;
import com.yaseen.aicore.command.AICommand;
import com.yaseen.aicore.chat.ChatBridge;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.yaseen.aicore.network.ModNetwork;
import org.slf4j.Logger;

@Mod(AICompanion.MOD_ID)
public final class AICompanion {
    public static final String MOD_ID = "aicore";
    public static final Logger LOG = LogUtils.getLogger();

    public AICompanion() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(ServerEvents::onEntityAttributes);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // All Forge runtime handlers are instance methods; register exactly one listener.
        MinecraftForge.EVENT_BUS.register(new ServerEvents());
        MinecraftForge.EVENT_BUS.register(new AICommand());
        MinecraftForge.EVENT_BUS.register(new ChatBridge());
        ModNetwork.init();
        LOG.info("AI Companion core initialized.");
    }
}
