package com.yaseen.aicore.tool.impl;
import com.yaseen.aicore.entity.CompanionEntity;import com.yaseen.aicore.tool.AITool;import net.minecraft.server.level.ServerPlayer;
public final class WorldMemoryTool implements AITool{public String name(){return "inspect_world_memory";}public String description(){return "Read persistent observations, visited chunks, verified successes and failures learned only from the real world.";}public String schema(){return "{}";}public String execute(CompanionEntity c,ServerPlayer p,String a){return "RESULT "+c.getWorldMemory().summary();}}
