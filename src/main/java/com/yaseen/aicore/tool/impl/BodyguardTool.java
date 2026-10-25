package com.yaseen.aicore.tool.impl;
import com.yaseen.aicore.tool.JsonArgs;
import com.yaseen.aicore.advanced.BodyguardController;import com.yaseen.aicore.tool.AITool;import com.yaseen.aicore.entity.CompanionEntity;import net.minecraft.server.level.ServerPlayer;
public final class BodyguardTool implements AITool{public String name(){return "bodyguard_mode";}public String description(){return "Enable or disable real owner protection and rescue behavior.";}public String schema(){return "{\"enabled\":true}";}public String execute(CompanionEntity c,ServerPlayer p,String a){Boolean v=bool(a);if(v==null)return "RESULT enabled="+BodyguardController.enabled(c);BodyguardController.set(c,v);return "RESULT enabled="+v;}private Boolean bool(String s){return JsonArgs.bool(s,"enabled");}}
