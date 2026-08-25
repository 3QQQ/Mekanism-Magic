package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.MagicSourceNetwork;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.client.render.MekanismRenderer.Model3D.ModelBoundsSetter;
import mekanism.client.render.RenderResizableCuboid.FaceDisplay;
import mekanism.client.render.transmitter.RenderTransmitterBase;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.util.EnumUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RenderMechanicalPipe copied for Source. Geometry, fill bounds and
 * PUSH/PULL contents models are Mekanism's; only sprite and tint differ.
 */
public final class MagicSourcePipeRenderer
        extends RenderTransmitterBase<MagicSourcePipeBlockEntity> {
    private static final int STAGES = 100;
    private static final float HEIGHT = 0.45F;
    private static final float OFFSET = 0.02F;
    // Match the purple Source bar used by the Ars Nouveau integration.
    private static final int SOURCE_PURPLE_COLOR = 0xC0985AF5;
    private static final Int2ObjectMap<Int2ObjectMap<Model3D>>
            CACHED_SOURCE = new Int2ObjectArrayMap<>(8);

    public MagicSourcePipeRenderer(
            BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void render(
            MagicSourcePipeBlockEntity tile, float partialTick,
            PoseStack matrix, MultiBufferSource renderer,
            int light, int overlayLight, ProfilerFiller profiler) {
        MagicSourceTransmitter pipe = tile.getTransmitter();
        MagicSourceNetwork network = pipe.getTransmitterNetwork();
        TextureAtlasSprite texture = MekanismRenderer.energyIcon;
        if (network == null || texture == null
                || network.currentScale <= 0) {
            return;
        }
        int stage = Math.max(3, Math.min(STAGES - 1,
                Math.round(network.currentScale * (STAGES - 1))));
        int glow = MekanismRenderer.calculateGlowLight(light, 15);
        List<String> connectionContents = new ArrayList<>();
        boolean[] renderSides = new boolean[6];
        boolean hasHorizontalSide = false;
        int verticalSides = 0;
        VertexConsumer buffer = renderer.getBuffer(
                Sheets.translucentCullBlockSheet());
        Camera camera = getCamera();

        for (Direction side : EnumUtils.DIRECTIONS) {
            ConnectionType connectionType =
                    pipe.getConnectionType(side);
            if (connectionType == ConnectionType.NORMAL) {
                MekanismRenderer.renderObject(
                        getModel(side, texture, stage), matrix,
                        buffer, SOURCE_PURPLE_COLOR, glow, overlayLight,
                        FaceDisplay.FRONT, camera, tile.getBlockPos());
            } else if (connectionType != ConnectionType.NONE) {
                connectionContents.add(side.getSerializedName()
                        + connectionType.getSerializedName()
                        .toUpperCase(Locale.ROOT));
            }
            renderSides[side.ordinal()] =
                    connectionType != ConnectionType.NORMAL;
            if (connectionType != ConnectionType.NONE) {
                if (side.getAxis().isHorizontal()) {
                    hasHorizontalSide = true;
                } else {
                    verticalSides++;
                }
            }
        }

        boolean renderBase = hasHorizontalSide || verticalSides < 2;
        Model3D model = getModel(texture, stage, renderBase);
        for (Direction side : EnumUtils.DIRECTIONS) {
            model.setSideRender(side,
                    renderSides[side.ordinal()]
                            || side.getAxis().isVertical()
                            && renderBase && stage != STAGES - 1);
        }
        MekanismRenderer.renderObject(model, matrix, buffer,
                SOURCE_PURPLE_COLOR, glow, overlayLight,
                FaceDisplay.FRONT, camera, tile.getBlockPos());

        if (!connectionContents.isEmpty()) {
            matrix.pushPose();
            matrix.translate(0.5, 0.5, 0.5);
            renderModel(tile, matrix, buffer,
                    MekanismRenderer.getRed(SOURCE_PURPLE_COLOR),
                    MekanismRenderer.getGreen(SOURCE_PURPLE_COLOR),
                    MekanismRenderer.getBlue(SOURCE_PURPLE_COLOR),
                    MekanismRenderer.getAlpha(SOURCE_PURPLE_COLOR),
                    glow, overlayLight, texture,
                    connectionContents);
            matrix.popPose();
        }
    }

    @Override
    protected String getProfilerSection() {
        return "magicSourcePipe";
    }

    @Override
    protected boolean shouldRenderTransmitter(
            MagicSourcePipeBlockEntity tile, Vec3 camera) {
        if (!super.shouldRenderTransmitter(tile, camera)) {
            return false;
        }
        MagicSourceNetwork network =
                tile.getTransmitter().getTransmitterNetwork();
        return network != null && network.currentScale > 0;
    }

    private static Model3D getModel(
            TextureAtlasSprite texture, int stage,
            boolean hasSides) {
        return getModel(null, texture, stage, hasSides);
    }

    private static Model3D getModel(
            Direction side, TextureAtlasSprite texture, int stage) {
        return getModel(side, texture, stage, false);
    }

    private static Model3D getModel(
            @Nullable Direction side, TextureAtlasSprite texture,
            int stage, boolean renderBase) {
        int sideOrdinal = side == null
                ? renderBase ? 7 : 6 : side.ordinal();
        Int2ObjectMap<Model3D> modelMap =
                CACHED_SOURCE.computeIfAbsent(
                        sideOrdinal,
                        ignored -> new Int2ObjectOpenHashMap<>());
        Model3D model = modelMap.get(stage);
        if (model != null) {
            return model;
        }
        model = new Model3D().setTexture(texture);
        float stageRatio = stage / (float) STAGES * HEIGHT;
        if (side == null) {
            float min;
            float max;
            if (renderBase) {
                min = 0.25F + OFFSET;
                max = 0.75F - OFFSET;
            } else {
                min = 0.5F - stageRatio / 2;
                max = 0.5F + stageRatio / 2;
            }
            model.xBounds(min, max)
                    .yBounds(0.25F + OFFSET,
                            0.25F + OFFSET + stageRatio)
                    .zBounds(min, max);
        } else {
            model.setSideRender(side, false)
                    .setSideRender(side.getOpposite(), false);
            if (side.getAxis().isHorizontal()) {
                model.yBounds(0.25F + OFFSET,
                        0.25F + OFFSET + stageRatio);
                if (side.getAxis() == Axis.Z) {
                    setHorizontalBounds(side,
                            model::xBounds, model::zBounds);
                } else {
                    setHorizontalBounds(side,
                            model::zBounds, model::xBounds);
                }
            } else {
                float min = 0.5F - stageRatio / 2;
                float max = 0.5F + stageRatio / 2;
                model.xBounds(min, max).zBounds(min, max);
                if (side == Direction.DOWN) {
                    model.yBounds(0, 0.25F + OFFSET);
                } else {
                    model.yBounds(
                            0.25F + OFFSET + stageRatio, 1);
                }
            }
        }
        modelMap.put(stage, model);
        return model;
    }

    private static Model3D setHorizontalBounds(
            Direction horizontal, ModelBoundsSetter axisBased,
            ModelBoundsSetter directionBased) {
        axisBased.set(0.25F + OFFSET, 0.75F - OFFSET);
        if (horizontal.getAxisDirection()
                == AxisDirection.POSITIVE) {
            return directionBased.set(0.75F - OFFSET, 1);
        }
        return directionBased.set(0, 0.25F + OFFSET);
    }
}
