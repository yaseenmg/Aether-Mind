package com.yaseen.aicore.core;

import com.yaseen.aicore.advanced.AdaptiveCombatController;
import com.yaseen.aicore.advanced.BodyguardController;
import com.yaseen.aicore.advanced.EmergencyController;
import com.yaseen.aicore.advanced.ExplorationController;
import com.yaseen.aicore.advanced.SceneMemory;
import com.yaseen.aicore.advanced.SurvivalGuard;
import com.yaseen.aicore.advanced.WorldObservation;
import com.yaseen.aicore.autonomy.AutonomyController;
import com.yaseen.aicore.autonomy.NavigationArbiter;
import com.yaseen.aicore.autonomy.DailyRoutineController;
import com.yaseen.aicore.autonomy.SuggestionController;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.quest.DragonQuestDirector;
import com.yaseen.aicore.task.TaskManager;

/**
 * Version 60 single lifecycle owner for the companion's high-level control loop.
 *
 * <p>The old entity tick accumulated controllers over many releases. That made it easy for a
 * new controller to start writing navigation/targets without knowing what already owned the
 * companion. This kernel enforces an explicit order:</p>
 *
 * <pre>survival -> quest/combat -> active work -> guard/explore -> autonomy -> observation</pre>
 *
 * <p>Long-running work is owned by TaskManager; the LLM never moves the entity directly.</p>
 */
public final class CompanionKernel {
    private CompanionKernel() {}

    public static boolean tick(CompanionEntity c) {
        if (c == null || c.level().isClientSide() || !c.isAlive()) return false;
        NavigationArbiter.tick(c);

        if (SurvivalGuard.tick(c, c.survivalState())) return true;

        // The deterministic quest is allowed to own the work graph. The language model must not
        // start a second autonomous job on top of it.
        if (DragonQuestDirector.isActive(c)) {
            DragonQuestDirector.tick(c);
            AdaptiveCombatController.tick(c);
            return true;
        }

        AdaptiveCombatController.tick(c);
        if (c.getTarget() != null && c.getTarget().isAlive()) return true;

        // A running gather/move/mine/smelt plan is authoritative. No follow/explore/LLM cycle may
        // redirect navigation while the task executor is active.
        if (TaskManager.isActive(c)) return true;

        BodyguardController.tick(c);
        if (c.getTarget() != null && c.getTarget().isAlive()) return true;

        EmergencyController.tick(c);
        if (c.getTarget() != null && c.getTarget().isAlive()) return true;

        if (c.getAutonomy().mode() == com.yaseen.aicore.autonomy.AutonomyState.Mode.EXPLORER) {
            ExplorationController.tick(c);
            if (TaskManager.isActive(c)) return true;
        }

        AutonomyController.tick(c);
        WorldObservation.tick(c);
        SceneMemory.tick(c);
        DailyRoutineController.tick(c);
        SuggestionController.tick(c);
        return true;
    }
}
