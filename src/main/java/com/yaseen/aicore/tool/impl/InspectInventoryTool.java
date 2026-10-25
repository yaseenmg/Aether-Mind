package com.yaseen.aicore.tool.impl;

import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.knowledge.ModpackKnowledge;
import com.yaseen.aicore.tool.AITool;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class InspectInventoryTool implements AITool {
    public String name() { return "inspect_inventory"; }
    public String description() { return "Read the owner's real inventory from the live server."; }
    public String schema() { return "{}"; }
    public String execute(CompanionEntity companion, ServerPlayer player, String arguments) {
        StringBuilder b = new StringBuilder("REAL_INVENTORY\n");
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty()) b.append(i).append('=').append(ModpackKnowledge.itemInfo(s)).append(" x").append(s.getCount()).append('\n');
        }
        return b.toString();
    }
}
