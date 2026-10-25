package com.yaseen.aicore.agent;

import com.google.gson.JsonObject;
import com.yaseen.aicore.AICompanion;
import com.yaseen.aicore.debug.AuditLog;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.tool.AITool;
import com.yaseen.aicore.tool.ToolContract;
import com.yaseen.aicore.tool.ToolRegistry;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Single gateway for all 55 Minecraft tools.  Nothing from the model reaches a tool directly:
 * resolve -> contract validate -> server-thread execute -> audit.  This is the hard boundary
 * that makes the AI brain replaceable without making the world executor unsafe or brittle.
 */
public final class AgentToolRuntime {
    private static final int MAX_REPEATED_FAILURES = 2;
    private AgentToolRuntime() {}

    public record Result(String tool, String actionId, boolean accepted, boolean blocked, String result) {}

    public static Result invoke(CompanionEntity companion, ServerPlayer player, String name, String rawArgs) {
        return invoke(companion, player, name, rawArgs, UUID.randomUUID().toString());
    }

    public static Result invoke(CompanionEntity companion, ServerPlayer player, String name, String rawArgs, String actionId) {
        return InternalToolExecutor.invoke(companion, player, name, rawArgs, actionId);
    }
}
