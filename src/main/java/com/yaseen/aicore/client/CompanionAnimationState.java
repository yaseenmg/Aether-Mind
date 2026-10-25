package com.yaseen.aicore.client;

import com.yaseen.aicore.entity.CompanionEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side animation state derived from the same entity state the AI uses.
 * This deliberately avoids a second, fake AI state machine on the client.
 */
public final class CompanionAnimationState {
    public enum State {
        IDLE, WALK, SPRINT, SNEAK, SWIM, FALL, JUMP,
        ATTACK, USE_ITEM, EAT, BLOCK, BOW, CROSSBOW, HURT, DEATH
    }

    private CompanionAnimationState() {}

    public static State resolve(CompanionEntity e) {
        if (e.isDeadOrDying() || e.deathTime > 0) return State.DEATH;
        if (e.getAnimationAction() == 1) return State.USE_ITEM; // mining override; model renders a tool swing
        if (e.getAnimationAction() == 2) return State.USE_ITEM; // placement override; model renders a tool swing
        if (e.hurtTime > 0) return State.HURT;
        if (e.isSwimming()) return State.SWIM;
        if (!e.onGround() && e.getDeltaMovement().y < -0.08D) return State.FALL;
        if (!e.onGround() && e.getDeltaMovement().y > 0.08D) return State.JUMP;
        if (e.isUsingItem()) {
            ItemStack used = e.getUseItem();
            if (e.getUsedItemHand() == InteractionHand.OFF_HAND
                    && used.getItem() instanceof net.minecraft.world.item.ShieldItem) return State.BLOCK;
            if (used.getItem() instanceof BowItem) return State.BOW;
            if (CrossbowItem.isCharged(used)) return State.CROSSBOW;
            if (used.isEdible()) return State.EAT;
            return State.USE_ITEM;
        }
        if (e.swinging || e.attackAnim > 0.0F) return State.ATTACK;
        if (e.isCrouching()) return State.SNEAK;
        double horizontal = e.getDeltaMovement().horizontalDistanceSqr();
        if (e.isSprinting() && horizontal > 0.0004D) return State.SPRINT;
        if (horizontal > 0.00012D) return State.WALK;
        return State.IDLE;
    }

    public static float movementAmount(CompanionEntity e) {
        double h = e.getDeltaMovement().horizontalDistance();
        return (float) Math.min(1.0D, h * 5.0D);
    }
}
