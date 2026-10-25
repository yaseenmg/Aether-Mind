package com.yaseen.aicore.client;

import com.yaseen.aicore.AICompanion;
import com.yaseen.aicore.entity.CompanionEntity;
import com.yaseen.aicore.entity.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.BeeStingerLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Player2NPC-style humanoid renderer.  The important detail is that armor is
 * an explicit HumanoidArmorLayer: a plain HumanoidMobRenderer does not by
 * itself guarantee that a custom humanoid entity's EquipmentSlots are drawn.
 */
@Mod.EventBusSubscriber(modid = AICompanion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientRenderEvents {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.COMPANION.get(), CompanionRenderer::new);
    }

    private static final class CompanionRenderer
            extends LivingEntityRenderer<CompanionEntity, CompanionPlayerModel> {
        private static final ResourceLocation TEXTURE =
                new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");

        private CompanionRenderer(EntityRendererProvider.Context context) {
            super(context, new CompanionPlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);

            // Copy the proven Player2NPC rendering stack.  These layers read the
            // entity's normal getItemBySlot()/equipment slots, so armor equipped
            // on the server becomes visible on every client.
            this.addLayer(new HumanoidArmorLayer<>(
                    this,
                    new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                    new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                    context.getModelManager()));
            this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
            this.addLayer(new ArrowLayer<>(context, this));
            this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
            this.addLayer(new ElytraLayer<>(this, context.getModelSet()));
            this.addLayer(new BeeStingerLayer<>(this));
        }

        @Override
        public ResourceLocation getTextureLocation(CompanionEntity entity) {
            return TEXTURE;
        }

        @Override
        protected boolean shouldShowName(CompanionEntity entity) {
            return true;
        }

        @Override
        protected void renderNameTag(CompanionEntity entity, Component ignored,
                                     PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
            String health = bar(entity.getHealth(), entity.getMaxHealth(), "♥");
            String food = bar(entity.getFoodLevel(), entity.getMaxFoodLevel(), "■");
            super.renderNameTag(entity,
                    Component.literal(entity.getName().getString() + "  " + health + "  " + food),
                    poseStack, buffer, packedLight);
        }

        private static String bar(float value, float max, String glyph) {
            int filled = Math.max(0, Math.min(10,
                    Math.round((value / Math.max(1f, max)) * 10f)));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 10; i++) b.append(i < filled ? glyph : "·");
            return b.toString();
        }
    }

    private ClientRenderEvents() {}
}
