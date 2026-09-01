package com.example.mekanismmagic.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IRecipeEntityDisplay;
import com.mojang.blaze3d.vertex.PoseStack;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders the current recipe's entity with its native entity renderer. Cached
 * entities are display-only: they are not added to the level and never tick
 * AI, collisions, sounds, loot or capabilities.
 */
final class RecipeEntityMachineRenderer {
    private final EntityRenderDispatcher entityRenderer;
    private final Map<TileEntityMekanism, DisplayEntry> displays =
            new WeakHashMap<>();

    RecipeEntityMachineRenderer(EntityRenderDispatcher entityRenderer) {
        this.entityRenderer = entityRenderer;
    }

    void render(TileEntityMekanism tile,
                MagicMachineAnimationRenderer.Kind kind,
                float partialTick,
                PoseStack poseStack,
                MultiBufferSource bufferSource,
                int packedLight) {
        if (!(tile instanceof IRecipeEntityDisplay source)) {
            return;
        }
        CompoundTag data = source.mekanismMagicRecipeEntityDisplay()
                .entityData();
        if (data.isEmpty()) {
            displays.remove(tile);
            return;
        }
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }

        DisplayEntry entry = displays.get(tile);
        if (entry == null || !entry.data.equals(data)) {
            entry = createDisplay(level, data);
            displays.put(tile, entry);
        }
        Entity entity = entry.entity;
        if (entity == null) {
            return;
        }

        DisplayPlacement placement = placement(kind);
        if (placement == null) {
            return;
        }

        float renderTime = level.getGameTime() + partialTick;
        prepareDisplay(entity, tile, renderTime);
        float width = Math.max(0.1F, entity.getBbWidth());
        float height = Math.max(0.1F, entity.getBbHeight());
        float scale = Math.min(placement.maxWidth / width,
                placement.maxHeight / height);
        scale = Mth.clamp(scale, placement.minScale,
                placement.maxScale);
        float bob = Mth.sin(renderTime * 0.095F) * placement.bob;
        float yaw = 180F + Mth.sin(renderTime * 0.028F) * 9F;
        setYaw(entity, yaw);

        poseStack.pushPose();
        poseStack.translate(placement.x,
                placement.floorY + bob, placement.z);
        poseStack.scale(scale, scale, scale);
        try {
            DisplayEntityRenderer.renderModel(entityRenderer, entity, yaw,
                    partialTick, poseStack, bufferSource, packedLight);
        } catch (RuntimeException | LinkageError failure) {
            // A third-party renderer may require a real world entity. Cache a
            // failed entry so one incompatible entity cannot crash or spam
            // every frame; the surrounding machine animation still renders.
            displays.put(tile, new DisplayEntry(data.copy(), null));
            MekanismMagic.LOGGER.warn(
                    "Unable to render recipe entity {} in machine at {}",
                    data.getString("id"), tile.getBlockPos(), failure);
        }
        poseStack.popPose();
    }

    private static DisplayEntry createDisplay(Level level,
                                               CompoundTag sourceData) {
        CompoundTag data = sourceData.copy();
        try {
            Entity entity = EntityType.create(data.copy(), level)
                    .orElse(null);
            if (entity != null) {
                entity.setNoGravity(true);
                entity.setInvisible(false);
                entity.setCustomNameVisible(false);
                entity.setDeltaMovement(Vec3.ZERO);
            }
            return new DisplayEntry(data, entity);
        } catch (RuntimeException | LinkageError failure) {
            MekanismMagic.LOGGER.warn(
                    "Unable to create recipe display entity {}",
                    data.getString("id"), failure);
            return new DisplayEntry(data, null);
        }
    }

    private static void prepareDisplay(Entity entity,
                                       TileEntityMekanism tile,
                                       float renderTime) {
        entity.tickCount = (int) (renderTime % Integer.MAX_VALUE);
        entity.setPos(tile.getBlockPos().getX() + 0.5D,
                tile.getBlockPos().getY() + 0.5D,
                tile.getBlockPos().getZ() + 0.5D);
        entity.setDeltaMovement(Vec3.ZERO);
    }

    private static void setYaw(Entity entity, float yaw) {
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        if (entity instanceof LivingEntity living) {
            living.setYHeadRot(yaw);
            living.yHeadRotO = yaw;
            living.setYBodyRot(yaw);
            living.yBodyRotO = yaw;
        }
    }

    private static DisplayPlacement placement(
            MagicMachineAnimationRenderer.Kind kind) {
        return switch (kind) {
            case SPIRIT -> new DisplayPlacement(
                    0.5F, 0.405F, 0.610F,
                    0.30F, 0.36F, 0.070F, 0.42F, 0.009F);
            case SPIRIT_FACTORY -> new DisplayPlacement(
                    0.5F, 0.400F, 0.610F,
                    0.31F, 0.37F, 0.070F, 0.43F, 0.009F);
            case RITUAL -> new DisplayPlacement(
                    0.5F, 0.395F, 0.5F,
                    0.38F, 0.42F, 0.075F, 0.46F, 0.012F);
            default -> null;
        };
    }

    private record DisplayEntry(CompoundTag data, Entity entity) {
    }

    private record DisplayPlacement(float x, float floorY, float z,
                                    float maxWidth, float maxHeight,
                                    float minScale, float maxScale,
                                    float bob) {
    }
}
