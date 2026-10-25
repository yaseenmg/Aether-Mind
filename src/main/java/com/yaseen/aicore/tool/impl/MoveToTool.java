package com.yaseen.aicore.tool.impl;

import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.task.TaskManager;
import com.yaseen.aicore.tool.AITool;
import com.yaseen.aicore.tool.JsonArgs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

public final class MoveToTool implements AITool {
    public String name(){return "move_to";}
    public String description(){return "Move the companion through real Minecraft navigation to an absolute block position.";}
    public String schema(){return "{\"x\":number,\"y\":number,\"z\":number}";}
    public String execute(CompanionEntity c, ServerPlayer p, String a){
        Integer x=num(a,"x"), y=num(a,"y"), z=num(a,"z");
        if(x==null||y==null||z==null)return "ERROR arguments require x,y,z";
        return TaskManager.move(c,new BlockPos(x,y,z));
    }
    static Integer num(String s,String n){return JsonArgs.integer(s,n);}
    static String str(String s,String n){String v=JsonArgs.string(s,n);return v.isEmpty()?null:v;}
}
