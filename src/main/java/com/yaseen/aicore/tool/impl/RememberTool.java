package com.yaseen.aicore.tool.impl;

import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.memory.MemoryStore;
import com.yaseen.aicore.tool.AITool;
import com.yaseen.aicore.tool.JsonArgs;
import net.minecraft.server.level.ServerPlayer;

public final class RememberTool implements AITool {
    public String name() { return "remember"; }
    public String description() { return "Store an explicit player-approved fact in persistent memory for future sessions."; }
    public String schema() { return "{\"fact\":\"text\"}"; }
    public String execute(CompanionEntity companion, ServerPlayer player, String arguments) {
        String fact = text(arguments);
        if (fact.isBlank()) return "RESULT remembered=false reason=empty_fact";
        MemoryStore.add(player, "MEMORY_FACT: " + fact);
        return "RESULT remembered=true";
    }
    private String text(String json) { return JsonArgs.string(json,"fact"); }

}
