package com.yaseen.aicore.advanced;

import com.yaseen.aicore.autonomy.NavigationArbiter;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.state.ActionPermissions;
import com.yaseen.aicore.task.TaskManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import java.util.UUID;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Real follow/protection behavior. It never targets the owner. */
public final class BodyguardController {
    private static final Map<UUID,Boolean> ENABLED=new ConcurrentHashMap<>();
    public static void set(CompanionEntity c,boolean v){ENABLED.put(c.getUUID(),v);}
    public static boolean enabled(CompanionEntity c){return ENABLED.getOrDefault(c.getUUID(),false);}
    public static void tick(CompanionEntity c){
        if(c.level().isClientSide()||c.tickCount%10!=0||!enabled(c))return;
        if(com.yaseen.aicore.quest.DragonQuestDirector.isActive(c)&&(c.getTarget()==null||!c.getTarget().isAlive()))return; // only fight, never wander, during the quest
        if(!(c.getOwnerPlayer() instanceof ServerPlayer p))return;
        if(c.getTarget()!=null && c.getTarget().isAlive()) return;
        // Bodyguard mode only reacts to an actual attack on the owner. A nearby
        // monster that is doing nothing is NOT a valid target.
        LivingEntity target=null; double best=Double.MAX_VALUE;
        for(LivingEntity e:c.level().getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().inflate(12),x->x.isAlive()&&x!=p&&x!=c)){
            if(!(e instanceof Monster)) continue;
            boolean attackingOwner = e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget()==p;
            if(!attackingOwner) continue;
            double d=p.distanceToSqr(e);
            if(d<best){best=d;target=e;}
        }
        if(target!=null){TaskManager.cancelCurrentTask(c);c.setTarget(target);NavigationArbiter.request(c, target, "bodyguard", 700, 1.25);}
        else if(p.getHealth()<=p.getMaxHealth()*.35 && c.distanceToSqr(p)>25){NavigationArbiter.request(c, p, "bodyguard", 700, 1.35);}
        else if(c.distanceToSqr(p)>36 && c.isFollowEnabled()){NavigationArbiter.request(c, p, "follow", 300, 1.25);}
    }
    public static void clear(UUID id){ENABLED.remove(id);}
    private BodyguardController(){}
}
