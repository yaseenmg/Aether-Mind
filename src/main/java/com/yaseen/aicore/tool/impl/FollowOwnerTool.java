package com.yaseen.aicore.tool.impl;

import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.task.TaskManager;
import com.yaseen.aicore.tool.AITool;
import com.yaseen.aicore.tool.JsonArgs;
import net.minecraft.server.level.ServerPlayer;

public final class FollowOwnerTool implements AITool {
    public String name() { return "follow_owner"; }
    public String description() { return "Follow the player only when explicitly requested. Disabled by default; enabling cancels the previous navigation task."; }
    public String schema() { return "{\"enabled\":true|false}"; }
    public String execute(CompanionEntity companion, ServerPlayer player, String arguments) {
        Boolean enabled = JsonArgs.bool(arguments, "enabled");
        if (enabled == null) enabled = !arguments.contains("false");
        companion.setFollowEnabled(enabled);
        if (enabled) TaskManager.cancelCurrentTask(companion);
        return "RESULT follow_enabled=" + enabled;
    }
}
