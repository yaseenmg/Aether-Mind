package com.yaseen.aicore.tool.impl;
import com.yaseen.aicore.autonomy.ResourceLedger;import com.yaseen.aicore.entity.CompanionEntity;import com.yaseen.aicore.tool.AITool;import net.minecraft.server.level.ServerPlayer;
public final class ResourceLedgerTool implements AITool{
 public String name(){return "inspect_resource_ledger";} public String description(){return "Inspect the companion's real inventory resources, available counts after reservations, and reservations.";} public String schema(){return "{\"item\":\"namespace:item\"}";}
 public String execute(CompanionEntity c,ServerPlayer p,String a){String id=MoveToTool.str(a,"item");if(id!=null&&!id.isBlank())return "RESULT item="+id+" total="+ResourceLedger.count(c,id)+" reserved="+c.getAutonomy().reserved(id)+" available="+ResourceLedger.available(c,id);return "RESULT inventory="+ResourceLedger.snapshot(c)+" reservations="+c.getAutonomy().summary();}
}
