package com.yaseen.aicore.tool.impl;
import com.yaseen.aicore.advanced.CombatLearning;
import com.yaseen.aicore.advanced.EquipmentAnalyzer;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.tool.AITool;
import net.minecraft.server.level.ServerPlayer;
public final class CombatStatusTool implements AITool {
 public String name(){return "combat_status";} public String description(){return "يعطي حالة القتال والتجهيز والخبرة المتراكمة للمرافق.";} public String schema(){return "{}";}
 public String execute(CompanionEntity c,ServerPlayer p,String a){return "health="+c.getHealth()+"/"+c.getMaxHealth()+"\nmain="+EquipmentAnalyzer.describe(c.getMainHandItem())+"\noff="+EquipmentAnalyzer.describe(c.getOffhandItem())+"\narmor=\n"+EquipmentAnalyzer.armorSummary(c);}
}
