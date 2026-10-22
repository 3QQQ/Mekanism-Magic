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
    private static final ResourceLocation AURA_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("mekanism", "liquid/energy");
    private static final Map<MagicSourceTransmitter, FillState> AURA_SCALES = new WeakHashMap<>();
    private static final Int2ObjectMap<Int2ObjectMap<Model3D>>
            CACHED_SOURCE = new Int2ObjectArrayMap<>(14);
    private static final Map<MagicSourceNetwork, FillState>
            DISPLAYED_SCALES = new WeakHashMap<>();
    private static final Map<MagicSourceTransmitter, ConnectionRenderState>
            CONNECTION_STATES = new WeakHashMap<>();

    public MagicSourcePipeRenderer(
            BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** Discards every object that may retain sprites from the old atlas. */
    public static void invalidateSpriteCache() {
        CACHED_SOURCE.clear();
        AURA_SCALES.clear();
        DISPLAYED_SCALES.clear();
        CONNECTION_STATES.clear();
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
        float renderTime = (tile.getLevel() == null ? 0 : tile.getLevel().getGameTime()) + partialTick;
        float auraScale = animatedAuraScale(pipe, renderTime);
        boolean force = MagicClientConfig.forceWorkingAnimations();
        float sourceScale = network == null ? 0 : animatedScale(network, renderTime);
        if (force) {
            if (com.example.mekanismmagic.integration.ModCompatibility.arsNouveauMachineContentEnabled()) {
                sourceScale = Math.max(TEST_DISPLAY_SCALE, sourceScale);
            }
            if (com.example.mekanismmagic.integration.ModCompatibility.naturesAuraLoaded()) {
                auraScale = Math.max(TEST_DISPLAY_SCALE, auraScale);
            }
        }
        if (auraScale > 0.001F) {
            TextureAtlasSprite aura = MekanismRenderer.getSprite(AURA_TEXTURE);
            // Separate radii keep both resource channels visible without coplanar faces.
            renderChannel(tile, partialTick, matrix, renderer, light, overlayLight,
                    aura, aura, aura, aura, 0.60F + 0.35F * auraScale,
                    pipe.extension().renderColor(), 92);
        }
        if (sourceScale > 0.001F) {
            renderChannel(tile, partialTick, matrix, renderer, light, overlayLight,
                    MekanismRenderer.getSprite(SOURCE_TEXTURE_U),
                    MekanismRenderer.getSprite(SOURCE_TEXTURE_V),
                    MekanismRenderer.getSprite(SOURCE_TEXTURE_U_REVERSE),
                    MekanismRenderer.getSprite(SOURCE_TEXTURE_V_REVERSE),
                    auraScale > 0.001F ? Math.min(0.45F, sourceScale) : sourceScale,
                    SOURCE_TEXTURE_RGB, 136);
        }
    }

    private void renderChannel(MagicSourcePipeBlockEntity tile, float partialTick,
                               PoseStack matrix, MultiBufferSource renderer, int light, int overlayLight,
                               TextureAtlasSprite textureU, TextureAtlasSprite textureV,
                               TextureAtlasSprite textureUReverse, TextureAtlasSprite textureVReverse,
                               float displayedScale, int rgb, int baseAlpha) {
        if (textureU == null || textureV == null || textureUReverse == null || textureVReverse == null) {
            return;
        }
        MagicSourceTransmitter pipe = tile.getTransmitter();
        float renderTime = (tile.getLevel() == null ? 0 : tile.getLevel().getGameTime()) + partialTick;
        int stage = Math.max(3, Math.min(STAGES - 1, Math.round(displayedScale * (STAGES - 1))));
        float basePulse = (Mth.sin(renderTime * 0.09F) + 1F) * 0.5F;
        int glow = MekanismRenderer.calculateGlowLight(light, 8 + Math.round(displayedScale * 4F + basePulse * 2F));
        int sourceColor = colorWithAlpha(rgb, baseAlpha + Math.round(displayedScale * 36F + basePulse * 32F));
        int nonNormalSideMask = 0;
        int connectionFingerprint = 0;
        boolean hasHorizontalSide = false;
        int verticalSides = 0;
        int connectedAxes = 0;
        VertexConsumer buffer = renderer.getBuffer(
                Sheets.translucentCullBlockSheet());
        Camera camera = getCamera();

        for (Direction side : EnumUtils.DIRECTIONS) {
            ConnectionType connectionType =
                    pipe.getConnectionType(side);
            connectionFingerprint |= connectionType.ordinal()
                    << side.ordinal() * 2;
            if (connectionType == ConnectionType.NORMAL) {
                MekanismRenderer.renderObject(
                        getModel(side, textureU, textureV,
                                textureUReverse, textureVReverse, stage), matrix,
                        buffer, sourceColor, glow, overlayLight,
                        FaceDisplay.FRONT, camera, tile.getBlockPos());
            }
            if (connectionType != ConnectionType.NORMAL) {
                nonNormalSideMask |= 1 << side.ordinal();
            }
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
                    (nonNormalSideMask & 1 << side.ordinal()) != 0
                            || side.getAxis().isVertical()
                            && renderBase && stage != STAGES - 1);
        }
        MekanismRenderer.renderObject(model, matrix, buffer,
                sourceColor, glow, overlayLight,
                FaceDisplay.FRONT, camera, tile.getBlockPos());

        List<String> connectionContents = connectionContents(
                pipe, connectionFingerprint);
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
        FillState auraState = AURA_SCALES.get(tile.getTransmitter());
        if (tile.getTransmitter().extension().renderScale() > 0
                || auraState != null && auraState.scale > 0.001F) {
            return true;
        }
        if (network == null) {
            return false;
        }
        FillState state = DISPLAYED_SCALES.get(network);
        return network.currentScale > 0
                || state != null && state.scale > 0.001F;
    }

    private static float animatedAuraScale(MagicSourceTransmitter pipe, float time) {
        float target = Mth.clamp(pipe.extension().renderScale(), 0, 1);
        FillState state = AURA_SCALES.computeIfAbsent(pipe, ignored -> new FillState(target, time));
        float elapsed = Mth.clamp(time - state.lastRenderTime, 0, 5);
        state.lastRenderTime = time;
        state.scale = Mth.approach(state.scale, target, elapsed * (target > state.scale ? FILL_SPEED : DRAIN_SPEED));
        return state.scale;
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

    private static List<String> connectionContents(
            MagicSourceTransmitter pipe, int fingerprint) {
        ConnectionRenderState cached = CONNECTION_STATES.get(pipe);
        if (cached != null && cached.fingerprint == fingerprint) {
            return cached.contents;
        }
        List<String> contents = new ArrayList<>(2);
        for (Direction side : EnumUtils.DIRECTIONS) {
            ConnectionType connection = pipe.getConnectionType(side);
            if (connection != ConnectionType.NONE
                    && connection != ConnectionType.NORMAL) {
                contents.add(side.getSerializedName()
                        + connection.getSerializedName()
                        .toUpperCase(Locale.ROOT));
            }
        }
        List<String> immutable = List.copyOf(contents);
        CONNECTION_STATES.put(pipe,
                new ConnectionRenderState(fingerprint, immutable));
        return immutable;
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
                        sideOrdinal + (textureU.contents().name().equals(AURA_TEXTURE) ? 16 : 0),
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

    private record ConnectionRenderState(
            int fingerprint, List<String> contents) {
    }
}
