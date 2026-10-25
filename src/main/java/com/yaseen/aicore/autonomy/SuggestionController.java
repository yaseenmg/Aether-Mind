package com.yaseen.aicore.autonomy;

import com.yaseen.aicore.entity.CompanionEntity;
import net.minecraft.server.level.ServerPlayer;

/** Produces evidence-backed suggestions, throttled so the companion does not spam the player. */
public final class SuggestionController {
    public static void tick(CompanionEntity c){
        if(!c.getAutonomy().suggestions()||c.level().isClientSide()||c.tickCount%200!=0)return;
        if(com.yaseen.aicore.quest.DragonQuestDirector.isActive(c))return;
        if(!(c.getOwnerPlayer() instanceof ServerPlayer p))return;
        long now=p.serverLevel().getGameTime();String msg=null;
        if(c.getGoal().active()&&c.getAutonomy().lastRoutineTime()!=Long.MIN_VALUE&&now-c.getAutonomy().lastRoutineTime()>1200)msg="لدي هدف مستمر لم يُحدّث منذ فترة؛ يمكنني استئنافه أو إعادة تخطيطه.";
        if(msg!=null){c.say(msg);c.getCompanionState().positiveEvent();}
    }
    private SuggestionController(){}
}
