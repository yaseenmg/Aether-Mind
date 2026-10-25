package com.yaseen.aicore.client;

import com.yaseen.aicore.entity.CompanionEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * Player model with vanilla PlayerModel animation semantics.
 *
 * The previous version applied a second procedural animation layer after
 * PlayerModel.setupAnim(), which double-rotated arms/legs/body and made the
 * companion visibly unlike a real Minecraft player. Gameplay actions now use
 * the entity's real swing/use/crouch/movement flags and the vanilla player
 * model is the single source of truth for pose generation.
 */
public final class CompanionPlayerModel extends PlayerModel<CompanionEntity> {
    public CompanionPlayerModel(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Override
    public void setupAnim(CompanionEntity e, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        // Do not add a second animation state machine here. PlayerModel already
        // implements the same humanoid walk/sprint/crouch/use/attack semantics
        // used by the vanilla player renderer. CompanionEntity.swing() and the
        // normal LivingEntity flags feed directly into that implementation.
        super.setupAnim(e, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
