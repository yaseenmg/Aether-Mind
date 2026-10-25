package com.yaseen.aicore.tool.impl;

import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.knowledge.ModpackKnowledge;
import com.yaseen.aicore.tool.AITool;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class InspectCompanionInventoryTool implements AITool {
    public String name(){ return "inspect_companion_inventory"; }
    public String description(){ return "Read the companion's real persistent 36-slot inventory."; }
    public String schema(){ return "{}"; }
    public String execute(CompanionEntity c, ServerPlayer p, String a){
        Container inv=c.getCompanionInventory();
        StringBuilder b=new StringBuilder("REAL_COMPANION_INVENTORY\\n");
        for(int i=0;i<inv.getContainerSize();i++){
            ItemStack s=inv.getItem(i);
            if(!s.isEmpty()) b.append(i).append('=').append(ModpackKnowledge.itemInfo(s)).append(" x").append(s.getCount()).append('\n');
        }
        return b.toString();
    }
}
