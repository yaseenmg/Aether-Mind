package com.yaseen.aicore.tool.impl;
import com.yaseen.aicore.advanced.EnergyInspector;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.tool.AITool;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class InspectEnergyTool implements AITool {
 public String name(){return "inspect_forge_energy";}
 public String description(){return "يفحص Forge Energy الحقيقية في عنصر أو في Block الذي ينظر إليه اللاعب.";}
 public String schema(){return "{target:'hand|look_block',distance:6}";}
 public String execute(CompanionEntity c,ServerPlayer p,String a){
   if(a.contains("look_block")){
     int distance=InspectNearbyEntitiesTool.number(a,"distance",6);
     HitResult hit=p.pick(Math.max(1,Math.min(distance,32)),0.0F,false);
     if(hit.getType()!=HitResult.Type.BLOCK)return "RESULT no_block_hit=true";
     BlockHitResult block=(BlockHitResult)hit;
     return "RESULT block_pos="+block.getBlockPos()+" "+EnergyInspector.block(p.serverLevel(),block.getBlockPos());
   }
   return "RESULT item="+EnergyInspector.item(c.getMainHandItem());
 }
}
