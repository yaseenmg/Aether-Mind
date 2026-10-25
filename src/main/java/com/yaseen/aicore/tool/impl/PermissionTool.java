package com.yaseen.aicore.tool.impl;

import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.tool.AITool;
import net.minecraft.server.level.ServerPlayer;

/** Read-only diagnostic tool. Permission changes must come from the owner's command. */
public final class PermissionTool implements AITool {
    @Override public String name() { return "inspect_permissions"; }
    @Override public String description() {
        return "Read the companion's explicit safety permissions. Permission changes are owner-only via /aic permission set.";
    }
    @Override public String schema() { return "{\"action\":\"status\"}"; }
    @Override public String execute(CompanionEntity c, ServerPlayer p, String a) {
        return "RESULT permissions=" + c.getPermissions().summary();
    }
}
