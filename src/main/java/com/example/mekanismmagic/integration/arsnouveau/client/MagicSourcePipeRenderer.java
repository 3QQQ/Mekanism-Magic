package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.config.MagicClientConfig;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * RenderMechanicalPipe adapted for Source. The casing and PUSH/PULL models
 * stay aligned with Mekanism, while the contents render as a centred magical
 * stream instead of a liquid level pooled at the bottom of the pipe.
 */
public final class MagicSourcePipeRenderer
        extends RenderTransmitterBase<MagicSourcePipeBlockEntity> {
    private static final int STAGES = 100;
    private static final float HEIGHT = 0.45F;
    private static final float FILL_SPEED = 0.07F;
    private static final float DRAIN_SPEED = 0.045F;
    private static final float TEST_DISPLAY_SCALE = 0.78F;
    private static final ResourceLocation SOURCE_TEXTURE_U =
            ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "block/magic_source_flow");
    private static final ResourceLocation SOURCE_TEXTURE_V =
            ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "block/magic_source_flow_v");
    private static final ResourceLocation SOURCE_TEXTURE_U_REVERSE =
            ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "block/magic_source_flow_reverse");
    private static final ResourceLocation SOURCE_TEXTURE_V_REVERSE =
            ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "block/magic_source_flow_v_reverse");
    private static final int SOURCE_TEXTURE_RGB = 0xFFFFFF;
    private static final Int2ObjectMap<Int2ObjectMap<Model3D>>
            CACHED_SOURCE = new Int2ObjectArrayMap<>(14);
    private static final Map<MagicSourceNetwork, FillState>
            DISPLAYED_SCALES = new WeakHashMap<>();

    public MagicSourcePipeRenderer(
            BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void render(
            MagicSourcePipeBlockEntity tile, float partialTick,
            PoseStack matrix, MultiBufferSource renderer,
            int light, int overlayLight, ProfilerFiller profiler) {
        if (!MagicClientConfig.animationsEnabled()) {
            return;
        }
        if (Minecraft.getInstance().screen
                instanceof AbstractContainerScreen<?>) {
            return;
        }
        MagicSourceTransmitter pipe = tile.getTransmitter();
        MagicSourceNetwork network = pipe.getTransmitterNetwork();
        boolean forceWorkingAnimation =
                MagicClientConfig.forceWorkingAnimations();
        // A single seamless animated sprite spans every contents cuboid.
        // Keeping all motion in this one layer avoids translucent overlaps
        // and bright seams at the boundary between neighbouring pipes.
        TextureAtlasSprite textureU = MekanismRenderer.getSprite(
                SOURCE_TEXTURE_U);
        TextureAtlasSprite textureV = MekanismRenderer.getSprite(
                SOURCE_TEXTURE_V);
        TextureAtlasSprite textureUReverse = MekanismRenderer.getSprite(
                SOURCE_TEXTURE_U_REVERSE);
        TextureAtlasSprite textureVReverse = MekanismRenderer.getSprite(
                SOURCE_TEXTURE_V_REVERSE);
        if (network == null && !forceWorkingAnimation
                || textureU == null || textureV == null
                || textureUReverse == null || textureVReverse == null) {
            return;
        }
        float renderTime = (tile.getLevel() == null
                ? 0 : tile.getLevel().getGameTime()) + partialTick;
        float displayedScale = network == null
                ? TEST_DISPLAY_SCALE : animatedScale(network, renderTime);
        if (forceWorkingAnimation) {
            displayedScale = Math.max(TEST_DISPLAY_SCALE, displayedScale);
        }
        if (displayedScale <= 0.001F) {
            return;
        }
        int stage = Math.max(3, Math.min(STAGES - 1,
                Math.round(displayedScale * (STAGES - 1))));
        float basePulse = (Mth.sin(renderTime * 0.09F) + 1F) * 0.5F;
        int glow = MekanismRenderer.calculateGlowLight(light,
                8 + Math.round(displayedScale * 4F + basePulse * 2F));
        int sourceColor = colorWithAlpha(SOURCE_TEXTURE_RGB,
                136 + Math.round(displayedScale * 48F
                        + basePulse * 48F));
        List<String> connectionContents = new ArrayList<>();
        boolean[] renderSides = new boolean[6];
        boolean hasHorizontalSide = false;
        int verticalSides = 0;
        int connectedAxes = 0;
        VertexConsumer buffer = renderer.getBuffer(
                Sheets.translucentCullBlockSheet());
        Camera camera = getCamera();

        for (Direction side : EnumUtils.DIRECTIONS) {
            ConnectionType connectionType =
                    pipe.getConnectionType(side);
            if (connectionType == ConnectionType.NORMAL) {
                MekanismRenderer.renderObject(
                        getModel(side, textureU, textureV,
                                textureUReverse, textureVReverse, stage), matrix,
                        buffer, sourceColor, glow, overlayLight,
                        FaceDisplay.FRONT, camera, tile.getBlockPos());
            } else if (connectionType != ConnectionType.NONE) {
                connectionContents.add(side.getSerializedName()
                        + connectionType.getSerializedName()
                        .toUpperCase(Locale.ROOT));
            }
            renderSides[side.ordinal()] =
                    connectionType != ConnectionType.NORMAL;
            if (connectionType != ConnectionType.NONE) {
                connectedAxes |= 1 << side.getAxis().ordinal();
                if (side.getAxis().isHorizontal()) {
                    hasHorizontalSide = true;
                } else {
                    verticalSides++;
                }
            }
        }

        boolean renderBase = hasHorizontalSide || verticalSides < 2;
        Axis flowAxis = singleConnectedAxis(connectedAxes);
        Model3D model = getModel(
                textureU, textureV, textureUReverse, textureVReverse,
                stage, renderBase, flowAxis);
        for (Direction side : EnumUtils.DIRECTIONS) {
            model.setSideRender(side,
                    renderSides[side.ordinal()]
                            || side.getAxis().isVertical()
                            && renderBase && stage != STAGES - 1);
        }
        MekanismRenderer.renderObject(model, matrix, buffer,
                sourceColor, glow, overlayLight,
                FaceDisplay.FRONT, camera, tile.getBlockPos());

        if (!connectionContents.isEmpty()) {
            matrix.pushPose();
            matrix.translate(0.5, 0.5, 0.5);
            renderModel(tile, matrix, buffer,
                    MekanismRenderer.getRed(sourceColor),
                    MekanismRenderer.getGreen(sourceColor),
                    MekanismRenderer.getBlue(sourceColor),
                    MekanismRenderer.getAlpha(sourceColor),
                    glow, overlayLight, textureU,
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
        if (MagicClientConfig.animationsEnabled()
                && MagicClientConfig.forceWorkingAnimations()) {
            return true;
        }
        MagicSourceNetwork network =
                tile.getTransmitter().getTransmitterNetwork();
        if (network == null) {
            return false;
        }
        FillState state = DISPLAYED_SCALES.get(network);
        return network.currentScale > 0
                || state != null && state.scale > 0.001F;
    }

    private static float animatedScale(
            MagicSourceNetwork network, float renderTime) {
        FillState state = DISPLAYED_SCALES.computeIfAbsent(
                network, ignored -> new FillState(
                        Mth.clamp(network.currentScale, 0, 1),
                        renderTime));
        float elapsed = Mth.clamp(renderTime - state.lastRenderTime,
                0, 5);
        state.lastRenderTime = renderTime;
        float target = Mth.clamp(network.currentScale, 0, 1);
        float speed = target > state.scale ? FILL_SPEED : DRAIN_SPEED;
        state.scale = Mth.approach(state.scale, target, elapsed * speed);
        return state.scale;
    }

    private static int colorWithAlpha(int rgb, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | rgb;
    }

    private static @Nullable Axis singleConnectedAxis(int connectedAxes) {
        if (Integer.bitCount(connectedAxes) != 1) {
            return null;
        }
        for (Axis axis : Axis.values()) {
            if ((connectedAxes & 1 << axis.ordinal()) != 0) {
                return axis;
            }
        }
        return null;
    }

    private static Model3D getModel(
            TextureAtlasSprite textureU,
            TextureAtlasSprite textureV,
            TextureAtlasSprite textureUReverse,
            TextureAtlasSprite textureVReverse,
            int stage, boolean renderBase,
            @Nullable Axis flowAxis) {
        return getModel(null, textureU, textureV,
                textureUReverse, textureVReverse,
                stage, renderBase, flowAxis);
    }

    private static Model3D getModel(
            Direction side,
            TextureAtlasSprite textureU,
            TextureAtlasSprite textureV,
            TextureAtlasSprite textureUReverse,
            TextureAtlasSprite textureVReverse,
            int stage) {
        return getModel(side, textureU, textureV,
                textureUReverse, textureVReverse,
                stage, false, side.getAxis());
    }

    private static Model3D getModel(
            @Nullable Direction side,
            TextureAtlasSprite textureU,
            TextureAtlasSprite textureV,
            TextureAtlasSprite textureUReverse,
            TextureAtlasSprite textureVReverse,
            int stage, boolean renderBase,
            @Nullable Axis flowAxis) {
        int sideOrdinal;
        if (side == null) {
            int axisIndex = flowAxis == null ? 3 : flowAxis.ordinal();
            sideOrdinal = 6 + (renderBase ? 4 : 0) + axisIndex;
        } else {
            sideOrdinal = side.ordinal();
        }
        Int2ObjectMap<Model3D> modelMap =
                CACHED_SOURCE.computeIfAbsent(
                        sideOrdinal,
                        ignored -> new Int2ObjectOpenHashMap<>());
        Model3D model = modelMap.get(stage);
        if (model != null) {
            return model;
        }
        model = applyFlowAxisTextures(
                new Model3D(), flowAxis, textureU, textureV,
                textureUReverse, textureVReverse);
        float stageRatio = stage / (float) STAGES * HEIGHT;
        if (side == null) {
            float min = 0.5F - stageRatio / 2;
            float max = 0.5F + stageRatio / 2;
            model.xBounds(min, max)
                    .yBounds(min, max)
                    .zBounds(min, max);
        } else {
            model.setSideRender(side, false)
                    .setSideRender(side.getOpposite(), false);
            float min = 0.5F - stageRatio / 2;
            float max = 0.5F + stageRatio / 2;
            if (side.getAxis().isHorizontal()) {
                model.yBounds(min, max);
                if (side.getAxis() == Axis.Z) {
                    setHorizontalBounds(side,
                            model::xBounds, model::zBounds, min, max);
                } else {
                    setHorizontalBounds(side,
                            model::zBounds, model::xBounds, min, max);
                }
            } else {
                model.xBounds(min, max).zBounds(min, max);
                if (side == Direction.DOWN) {
                    model.yBounds(0, min);
                } else {
                    model.yBounds(max, 1);
                }
            }
        }
        modelMap.put(stage, model);
        return model;
    }

    private static Model3D applyFlowAxisTextures(
            Model3D model, @Nullable Axis flowAxis,
            TextureAtlasSprite textureU,
            TextureAtlasSprite textureV,
            TextureAtlasSprite textureUReverse,
            TextureAtlasSprite textureVReverse) {
        model.setTexture(textureU);
        if (flowAxis == Axis.X) {
            // North/south map world X to decreasing U, while top/bottom map
            // it to increasing U.
            return model.setTexture(Direction.NORTH, textureUReverse)
                    .setTexture(Direction.SOUTH, textureUReverse)
                    .setTexture(Direction.UP, textureU)
                    .setTexture(Direction.DOWN, textureU);
        }
        if (flowAxis == Axis.Y) {
            // Every visible vertical-pipe face maps increasing world Y to
            // decreasing V.
            return model.setTexture(Direction.NORTH, textureVReverse)
                    .setTexture(Direction.SOUTH, textureVReverse)
                    .setTexture(Direction.WEST, textureVReverse)
                    .setTexture(Direction.EAST, textureVReverse)
                    .setTexture(Direction.UP, textureVReverse)
                    .setTexture(Direction.DOWN, textureVReverse);
        }
        if (flowAxis == Axis.Z) {
            // Opposing faces use opposite texture coordinates for world Z.
            return model.setTexture(Direction.WEST, textureU)
                    .setTexture(Direction.EAST, textureUReverse)
                    .setTexture(Direction.UP, textureV)
                    .setTexture(Direction.DOWN, textureVReverse);
        }
        // Junctions without one dominant axis intentionally remain neutral.
        return model;
    }

    private static Model3D setHorizontalBounds(
            Direction horizontal, ModelBoundsSetter axisBased,
            ModelBoundsSetter directionBased,
            float minCross, float maxCross) {
        axisBased.set(minCross, maxCross);
        if (horizontal.getAxisDirection()
                == AxisDirection.POSITIVE) {
            return directionBased.set(maxCross, 1);
        }
        return directionBased.set(0, minCross);
    }

    private static final class FillState {
        private float scale;
        private float lastRenderTime;

        private FillState(float scale, float lastRenderTime) {
            this.scale = scale;
            this.lastRenderTime = lastRenderTime;
        }
    }
}
