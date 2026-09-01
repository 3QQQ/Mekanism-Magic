package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.DisplayEntityRenderer;
import com.example.mekanismmagic.config.MagicClientConfig;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import com.hollingsworth.arsnouveau.common.entity.EntityDrygmy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders the simulated Drygmy with Ars Nouveau's native GeckoLib model and
 * animation controllers. The display entity is client-only and is never added
 * to the level, so it cannot run AI or affect gameplay.
 */
public final class DrygmySimulatorRenderer
        implements BlockEntityRenderer<DrygmySimulatorBlockEntity> {
    private static final float DISPLAY_SCALE = 0.30F;
    private static final double DISPLAY_FLOOR_Y = 6.05D / 16D;
    private static final String DISPLAY_COLOR = "cyan";

    private final EntityRenderDispatcher entityRenderer;
    private final Map<DrygmySimulatorBlockEntity, EntityDrygmy> displays =
            new WeakHashMap<>();

    public DrygmySimulatorRenderer(
            BlockEntityRendererProvider.Context context) {
        entityRenderer = context.getEntityRenderer();
    }

    @Override
    public void render(DrygmySimulatorBlockEntity tile,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        if (!MagicClientConfig.animationsEnabled()) {
            return;
        }
        if (Minecraft.getInstance().screen
                instanceof AbstractContainerScreen<?>) {
            return;
        }
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }

        EntityDrygmy drygmy = displays.computeIfAbsent(
                tile, ignored -> createDisplay(level));
        boolean active = MagicClientConfig.forceWorkingAnimations()
                || tile.isVisuallyProcessing();
        prepareDisplay(drygmy, tile, active);

        float renderTime = level.getGameTime() + partialTick;
        float observationTurn = Mth.sin(renderTime
                * (active ? 0.08F : 0.035F))
                * (active ? 2F : 4F);
        float yaw = tile.getDirection().toYRot() + observationTurn;
        setDisplayYaw(drygmy, yaw);

        float bob = Mth.sin(renderTime * (active ? 0.20F : 0.08F))
                * (active ? 0.026F : 0.009F);
        float workPulse = active
                ? (Mth.sin(renderTime * 0.31F) + 1F) * 0.5F : 0F;
        float displayScale = DISPLAY_SCALE
                * (1F + workPulse * 0.035F);
        float lean = active
                ? -5F + Mth.sin(renderTime * 0.14F) * 2F : 0F;

        poseStack.pushPose();
        poseStack.translate(0.5D, DISPLAY_FLOOR_Y + bob, 0.5D);
        poseStack.mulPose(Axis.XP.rotationDegrees(lean));
        poseStack.scale(displayScale, displayScale, displayScale);
        DisplayEntityRenderer.renderModel(entityRenderer, drygmy, yaw,
                partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    private static EntityDrygmy createDisplay(Level level) {
        EntityDrygmy drygmy = new EntityDrygmy(level, true);
        drygmy.getEntityData().set(EntityDrygmy.COLOR, DISPLAY_COLOR);
        drygmy.setNoGravity(true);
        return drygmy;
    }

    private static void prepareDisplay(EntityDrygmy drygmy,
                                       DrygmySimulatorBlockEntity tile,
                                       boolean active) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        drygmy.tickCount = (int) (level.getGameTime()
                % Integer.MAX_VALUE);
        drygmy.setPos(tile.getBlockPos().getX() + 0.5D,
                tile.getBlockPos().getY() + DISPLAY_FLOOR_Y,
                tile.getBlockPos().getZ() + 0.5D);
        drygmy.setChanneling(active);
        drygmy.setHoldingEssence(active);
    }

    private static void setDisplayYaw(EntityDrygmy drygmy, float yaw) {
        drygmy.setYRot(yaw);
        drygmy.yRotO = yaw;
        drygmy.setYHeadRot(yaw);
        drygmy.yHeadRotO = yaw;
        drygmy.setYBodyRot(yaw);
        drygmy.yBodyRotO = yaw;
    }
}
