package com.example.mekanismmagic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Renders a machine's display entity without world-entity overlays. */
public final class DisplayEntityRenderer {
    private DisplayEntityRenderer() {
    }

    /**
     * Calls the entity model renderer directly. The dispatcher normally adds
     * debug hitboxes, ground shadows and fire after rendering the model; none
     * of those effects belong to a synthetic entity embedded in a machine.
     */
    public static <ENTITY extends Entity> void renderModel(
            EntityRenderDispatcher dispatcher, ENTITY entity,
            float yaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        EntityRenderer<? super ENTITY> renderer =
                dispatcher.getRenderer(entity);
        Vec3 offset = renderer.getRenderOffset(entity, partialTick);
        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        try {
            renderer.render(entity, yaw, partialTick, poseStack,
                    bufferSource, packedLight);
        } finally {
            poseStack.popPose();
        }
    }
}
