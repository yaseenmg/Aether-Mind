package com.yaseen.aicore.ai;

import com.yaseen.aicore.agent.AgentEngine;
import com.yaseen.aicore.entity.CompanionEntity;
import net.minecraft.server.level.ServerPlayer;

/** Compatibility entry point; all reasoning now goes through the real server-side agent loop. */
public final class GroqBrain {
    public static void askAsync(CompanionEntity companion, ServerPlayer player, String userMessage) {
        AgentEngine.runAsync(companion, player, userMessage);
    }
    private GroqBrain() {}
}
