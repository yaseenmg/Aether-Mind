package com.yaseen.aicore.tool.impl;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.task.TaskManager;
import com.yaseen.aicore.tool.AITool;
import net.minecraft.server.level.ServerPlayer;
public final class TaskStatusTool implements AITool {
 public String name(){return "task_status";}
 public String description(){return "Read the actual status of the companion's current server-side task.";}
 public String schema(){return "{}";}
 public String execute(CompanionEntity c, ServerPlayer p, String a){return TaskManager.status(c);}
}
