package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.config.MagicClientConfig;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusForgeProcessorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Two original glyph textures above the blank pad; no spawned particles or world scans. */
public final class HephaestusForgeRenderer implements BlockEntityRenderer<HephaestusForgeProcessorBlockEntity> {
    static final float CENTER_X = 8F / 16F;
    static final float CENTER_Z = 7.25F / 16F;
    static final float INNER_Y = 4.65F / 16F;
    static final float OUTER_Y = 4.69F / 16F;
    static final float INNER_RADIUS = 2.35F / 16F;
    static final float OUTER_RADIUS = 2.70F / 16F;
    private static final ResourceLocation PREVIEW_CIRCLE = ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", "create_item");

    public HephaestusForgeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(HephaestusForgeProcessorBlockEntity tile, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!MagicClientConfig.animationsEnabled() || tile.getLevel() == null) return;
        boolean forced = MagicClientConfig.forceWorkingAnimations();
        if (!forced && (!tile.getActive() || tile.isPaidJobPaused())) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(tile.getBlockPos().getCenter()) > 32D * 32D) return;
        ResourceLocation id = tile.getActiveMagicCircleId();
        if (id == null && forced) id = PREVIEW_CIRCLE;
        if (id == null) return;
        var registry = tile.getLevel().registryAccess().registry(FARegistries.MAGIC_CIRCLE).orElse(null);
        var circle = registry == null ? null : registry.get(id);
        if (circle == null) return;
        // Direct ID lookups only. Missing custom textures are omitted, not rendered as error squares.
        if (minecraft.getResourceManager().getResource(circle.innerTexture()).isEmpty()
                || minecraft.getResourceManager().getResource(circle.outerTexture()).isEmpty()) return;
        // Both angular rates complete whole rotations in 7200 ticks; wrapping is seamless.
        float time = (tile.getLevel().getGameTime() % 7200L) + partialTick;
        pose.pushPose();
        pose.translate(.5D, 0, .5D);
        pose.mulPose(Axis.YP.rotationDegrees(180F - tile.getDirection().toYRot()));
        pose.translate(-.5D, 0, -.5D);
        quad(pose, buffers, circle.innerTexture(), INNER_Y, INNER_RADIUS, time * .65F);
        quad(pose, buffers, circle.outerTexture(), OUTER_Y, OUTER_RADIUS, -time * .35F);
        pose.popPose();
    }

    private static void quad(PoseStack pose, MultiBufferSource buffers, ResourceLocation texture,
                             float y, float radius, float angle) {
        pose.pushPose();
        pose.translate(CENTER_X, y, CENTER_Z);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityTranslucentEmissive(texture));
        vertex(pose, consumer, -radius, -radius, 0, 0);
        vertex(pose, consumer, -radius, radius, 0, 1);
        vertex(pose, consumer, radius, radius, 1, 1);
        vertex(pose, consumer, radius, -radius, 1, 0);
        pose.popPose();
    }

    private static void vertex(PoseStack pose, VertexConsumer buffer, float x, float z, float u, float v) {
        buffer.addVertex(pose.last().pose(), x, 0, z).setColor(255, 255, 255, 235)
                .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }

    @Override
    public boolean shouldRender(HephaestusForgeProcessorBlockEntity tile, Vec3 camera) {
        return camera.distanceToSqr(tile.getBlockPos().getCenter()) <= 32D * 32D;
    }
}
