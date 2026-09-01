package com.example.mekanismmagic.client;

import com.example.mekanismmagic.api.IRecipeItemDisplay;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/** Renders real recipe item models inside the authored machine work area. */
final class RecipeItemMachineRenderer {
    private static final float[] FACTORY_LANES = {
            0.21875F, 0.5F, 0.78125F
    };
    private static final ItemPlacement CATALYST_SCANNER_PLACEMENT =
            new ItemPlacement(0.5F, 0.425F, 0.49F,
                    0.70F, 0.20F, 0.19F, 0.29F, false);
    private static final ItemPlacement IMBUEMENT_PLACEMENT =
            new ItemPlacement(0.5F, 0.420F, 0.43F,
                    0.68F, 0.18F, 0.18F, 0.28F, false);
    private static final ItemPlacement ENCHANTING_PLACEMENT =
            new ItemPlacement(0.5F, 0.430F, 0.50F,
                    0.72F, 0.20F, 0.22F, 0.28F, false);
    private static final ItemPlacement SPIRIT_PLACEMENT =
            new ItemPlacement(0.5F, 0.425F, 0.40F,
                    0.64F, 0.17F, 0.16F, 0.25F, false);
    private static final ItemPlacement RITUAL_PLACEMENT =
            new ItemPlacement(0.5F, 0.430F, 0.50F,
                    0.73F, 0.18F, 0.25F, 0.25F, false);
    private static final ItemPlacement SCRIBING_PLACEMENT =
            new ItemPlacement(0.5F, 0.405F, 0.50F,
                    0.70F, 0.20F, 0.22F, 0.24F, false);
    private static final ItemPlacement IMBUEMENT_FACTORY_PLACEMENT =
            new ItemPlacement(0.5F, 0.410F, 0.40F,
                    0.57F, 0.19F, 0.02F, 0.24F, true);
    private static final ItemPlacement SPIRIT_FACTORY_PLACEMENT =
            new ItemPlacement(0.5F, 0.410F, 0.40F,
                    0.57F, 0.19F, 0.02F, 0.24F, true);
    private static final ItemPlacement DIMENSION_PLACEMENT =
            new ItemPlacement(0.5F, 0.445F, 0.50F,
                    0.72F, 0.18F, 0.18F, 0.27F, false);

    void render(TileEntityMekanism tile,
                MagicMachineAnimationRenderer.Kind kind,
                float partialTick,
                PoseStack poseStack,
                MultiBufferSource bufferSource,
                int packedLight,
                double cameraDistanceSquared) {
        if (!(tile instanceof IRecipeItemDisplay source)) {
            return;
        }
        List<RecipeItemDisplayState.Entry> entries = source
                .mekanismMagicRecipeItemDisplay().entries();
        ItemPlacement placement = placement(kind);
        Level level = tile.getLevel();
        if (entries.isEmpty() || placement == null || level == null) {
            return;
        }

        float time = level.getGameTime() + partialTick
                + (tile.getBlockPos().asLong() & 31L) * 0.19F;
        if (placement.factory) {
            renderFactory(entries, placement, time, tile, poseStack,
                    bufferSource, packedLight, cameraDistanceSquared);
        } else {
            renderMachine(entries, placement, time, tile, poseStack,
                    bufferSource, packedLight, cameraDistanceSquared);
        }
    }

    private static void renderMachine(
            List<RecipeItemDisplayState.Entry> entries,
            ItemPlacement placement, float time,
            TileEntityMekanism tile, PoseStack poseStack,
            MultiBufferSource bufferSource, int light,
            double cameraDistanceSquared) {
        RecipeItemDisplayState.Entry primary = entries.getFirst();
        if (!primary.output().isEmpty()) {
            renderTransformation(primary, placement.centerX,
                    placement, time, tile, poseStack,
                    bufferSource, light, 0);
        } else {
            renderOrbiting(primary.input(), placement.centerX,
                    placement.centerY, placement.centerZ,
                    placement.orbitRadius * 0.34F,
                    time * 1.9F, placement.scale,
                    tile, poseStack, bufferSource, light, 0);
        }

        int satelliteLimit = cameraDistanceSquared <= 8D * 8D ? 7
                : cameraDistanceSquared <= 16D * 16D ? 3 : 1;
        int satellites = Math.min(satelliteLimit, entries.size() - 1);
        for (int index = 0; index < satellites; index++) {
            ItemStack stack = entries.get(index + 1).input();
            float angle = time * (1.15F + index * 0.035F)
                    + index * 360F / Math.max(1, satellites);
            renderOrbiting(stack, placement.centerX,
                    placement.centerY, placement.centerZ,
                    placement.orbitRadius, angle,
                    placement.scale * 0.78F,
                    tile, poseStack, bufferSource, light,
                    index + 1);
        }
    }

    private static void renderFactory(
            List<RecipeItemDisplayState.Entry> entries,
            ItemPlacement placement, float time,
            TileEntityMekanism tile, PoseStack poseStack,
            MultiBufferSource bufferSource, int light,
            double cameraDistanceSquared) {
        int laneLimit = cameraDistanceSquared <= 10D * 10D ? 3
                : cameraDistanceSquared <= 18D * 18D ? 2 : 1;
        int visible = Math.min(Math.min(FACTORY_LANES.length, laneLimit),
                entries.size());
        for (int index = 0; index < visible; index++) {
            RecipeItemDisplayState.Entry entry = entries.get(index);
            float phaseTime = time + index * 11.7F;
            if (!entry.output().isEmpty()) {
                renderTransformation(entry, FACTORY_LANES[index],
                        placement, phaseTime, tile, poseStack,
                        bufferSource, light, entry.lane());
            } else {
                renderOrbiting(entry.input(), FACTORY_LANES[index],
                        placement.centerY, placement.centerZ,
                        0.022F, phaseTime * 2.2F,
                        placement.scale, tile, poseStack,
                        bufferSource, light, entry.lane());
            }
        }
    }

    private static void renderTransformation(
            RecipeItemDisplayState.Entry entry, float x,
            ItemPlacement placement, float time,
            TileEntityMekanism tile, PoseStack poseStack,
            MultiBufferSource bufferSource, int light, int seed) {
        float cycle = Mth.frac(time * 0.0115F);
        if (cycle < 0.50F && !entry.input().isEmpty()) {
            float travel = smoothStep(Mth.clamp(cycle / 0.46F,
                    0F, 1F));
            float scaleEnvelope = smoothWindow(cycle,
                    0F, 0.12F, 0.37F, 0.49F);
            float z = Mth.lerp(travel,
                    placement.intakeZ, placement.centerZ);
            float y = placement.centerY
                    + Mth.sin(travel * Mth.PI) * 0.055F;
            renderItem(entry.input(), x, y, z,
                    time * 3.1F + seed * 29F,
                    placement.scale * scaleEnvelope,
                    tile, poseStack, bufferSource, light, seed);
        } else if (!entry.output().isEmpty()) {
            float outputPhase = Mth.clamp((cycle - 0.50F) / 0.50F,
                    0F, 1F);
            float travel = smoothStep(outputPhase);
            float scaleEnvelope = smoothWindow(outputPhase,
                    0F, 0.18F, 0.70F, 1F);
            float z = Mth.lerp(travel,
                    placement.centerZ, placement.outputZ);
            float y = placement.centerY
                    + Mth.sin(travel * Mth.PI) * 0.045F;
            renderItem(entry.output(), x, y, z,
                    -time * 2.45F - seed * 31F,
                    placement.scale * scaleEnvelope,
                    tile, poseStack, bufferSource, light, seed + 37);
        }
    }

    private static void renderOrbiting(
            ItemStack stack, float centerX, float centerY, float centerZ,
            float radius, float angleDegrees, float scale,
            TileEntityMekanism tile, PoseStack poseStack,
            MultiBufferSource bufferSource, int light, int seed) {
        if (stack.isEmpty()) {
            return;
        }
        float angle = angleDegrees * Mth.DEG_TO_RAD;
        float x = centerX + Mth.cos(angle) * radius;
        float z = centerZ + Mth.sin(angle) * radius;
        float y = centerY + Mth.sin(angle * 2F) * 0.018F;
        renderItem(stack, x, y, z, angleDegrees * 1.8F,
                scale, tile, poseStack, bufferSource, light, seed);
    }

    private static void renderItem(
            ItemStack stack, float x, float y, float z,
            float rotation, float scale,
            TileEntityMekanism tile, PoseStack poseStack,
            MultiBufferSource bufferSource, int light, int seed) {
        if (stack.isEmpty() || scale <= 0.001F) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.mulPose(Axis.XP.rotationDegrees(-12F));
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.GROUND, light,
                OverlayTexture.NO_OVERLAY, poseStack, bufferSource,
                tile.getLevel(), (int) (tile.getBlockPos().asLong() ^ seed));
        poseStack.popPose();
    }

    private static float smoothWindow(float value, float fadeInStart,
                                      float fadeInEnd, float fadeOutStart,
                                      float fadeOutEnd) {
        float fadeIn = smoothStep(Mth.clamp(
                (value - fadeInStart) /
                        Math.max(0.0001F, fadeInEnd - fadeInStart),
                0F, 1F));
        float fadeOut = 1F - smoothStep(Mth.clamp(
                (value - fadeOutStart) /
                        Math.max(0.0001F, fadeOutEnd - fadeOutStart),
                0F, 1F));
        return fadeIn * fadeOut;
    }

    private static float smoothStep(float value) {
        return value * value * (3F - 2F * value);
    }

    private static ItemPlacement placement(
            MagicMachineAnimationRenderer.Kind kind) {
        return switch (kind) {
            case CATALYST_SCANNER -> CATALYST_SCANNER_PLACEMENT;
            case IMBUEMENT -> IMBUEMENT_PLACEMENT;
            case ENCHANTING -> ENCHANTING_PLACEMENT;
            case SPIRIT -> SPIRIT_PLACEMENT;
            case RITUAL -> RITUAL_PLACEMENT;
            case SCRIBING -> SCRIBING_PLACEMENT;
            case IMBUEMENT_FACTORY -> IMBUEMENT_FACTORY_PLACEMENT;
            case SPIRIT_FACTORY -> SPIRIT_FACTORY_PLACEMENT;
            case DIMENSION -> DIMENSION_PLACEMENT;
            default -> null;
        };
    }

    private record ItemPlacement(float centerX, float centerY,
                                 float centerZ, float intakeZ,
                                 float outputZ, float orbitRadius,
                                 float scale, boolean factory) {
    }
}
