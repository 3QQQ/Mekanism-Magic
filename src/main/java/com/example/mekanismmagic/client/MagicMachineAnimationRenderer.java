package com.example.mekanismmagic.client;

import com.example.mekanismmagic.config.MagicClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.client.render.RenderResizableCuboid.FaceDisplay;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Small, machine-specific moving mechanisms rendered over the static block
 * model. Only the working assembly moves; the chassis remains fixed.
 */
public final class MagicMachineAnimationRenderer<
        TILE extends TileEntityMekanism>
        implements BlockEntityRenderer<TILE> {
    public enum Kind {
        SOURCE("source_generator/core", 0xB86DFF, 0x5DEBFF),
        SOURCE_CONVERTER("source_converter/accent",
                0x55E8FF, 0xB66BFF),
        CATALYST_SCANNER("catalyst_identifier_assembler/accent",
                0xFFD36A, 0x63E9FF),
        IMBUEMENT("imbuement_processor/core",
                0xBD71FF, 0x5CE8FF),
        IMBUEMENT_FACTORY("imbuement_factory/top_core_active",
                0xB767FF, 0x60EAFF),
        ENCHANTING("enchanting_apparatus_processor/accent",
                0xD783FF, 0xFFD36A),
        SPIRIT("occult_machine/binding",
                0xA269FF, 0xF1D36A),
        SPIRIT_FACTORY("occult_machine/spirit",
                0xA65EFF, 0x70E4FF),
        DIMENSION("occult_machine/void",
                0x8855FF, 0x55DFFF),
        RITUAL("occult_machine/chalk",
                0xE5C85E, 0xA46BFF),
        SCRIBING("occult_machine/chalk",
                0xF0D66D, 0xB66DFF);

        private final ResourceLocation texture;
        private final int primary;
        private final int secondary;

        Kind(String texturePath, int primary, int secondary) {
            this(ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "block/" + texturePath),
                    primary, secondary);
        }

        Kind(ResourceLocation texture, int primary, int secondary) {
            this.texture = texture;
            this.primary = primary;
            this.secondary = secondary;
        }
    }

    private final Kind kind;
    private final Model3D box = new Model3D();
    private final RecipeEntityMachineRenderer recipeEntityRenderer;
    private final RecipeItemMachineRenderer recipeItemRenderer;
    private final Vector3f normalScratch = new Vector3f();

    /*
     * Sprite lookup ultimately walks the block-atlas map. Effect helpers such
     * as renderEnergyOrb are called dozens of times for a single machine, so
     * resolving the same path in every helper used to dominate the renderer's
     * constant overhead. The cache is invalidated after the block atlas is
     * stitched (including F3+T), so it never retains sprites from an old
     * atlas.
     */
    private static volatile int spriteCacheGeneration;
    private int resolvedSpriteCacheGeneration = -1;
    private TextureAtlasSprite machineSprite;
    private TextureAtlasSprite authoredArcaneCircleSprite;
    private TextureAtlasSprite fieldEffectSprite;
    private TextureAtlasSprite coreOrbSprite;
    private TextureAtlasSprite energyRibbonSprite;
    private TextureAtlasSprite precisionMetalSprite;
    private TextureAtlasSprite occultGoldSprite;
    private TextureAtlasSprite occultSpiritSprite;

    /**
     * The old block-space UV renderer enlarged tiny pieces of the particle
     * sprite, so the original radii were tuned much smaller than the actual
     * full-sprite billboards. Keep this scale in one place so every machine
     * gets equally legible particles without increasing particle count.
     */
    private static final float PARTICLE_VISUAL_SCALE = 1.55F;
    private static final double ANIMATION_VIEW_DISTANCE_SQUARED = 32D * 32D;
    private static final double ITEM_VIEW_DISTANCE_SQUARED = 24D * 24D;
    private static final double ENTITY_VIEW_DISTANCE_SQUARED = 12D * 12D;
    private static final float[] IMBUEMENT_FACTORY_LANES = {
            0.25F, 0.50F, 0.75F
    };
    private static final float[] SPIRIT_FACTORY_LANES = {
            0.21875F, 0.5F, 0.78125F
    };

    private static final ResourceLocation AUTHORED_ARCANE_CIRCLE =
            blockTexture("ars_series/arcane_focus_circle_crisp");
    private static final ResourceLocation PRECISION_METAL =
            blockTexture("ars_series/precision_metal");
    private static final ResourceLocation OCCULT_GOLD =
            blockTexture("occult_machine/gold");
    private static final ResourceLocation OCCULT_SPIRIT =
            blockTexture("occult_machine/spirit");
    private static final ResourceLocation OCCULT_SEAL =
            blockTexture("animation/occult_seal");
    private static final ResourceLocation PHASE_VORTEX =
            blockTexture("animation/phase_vortex");
    private static final ResourceLocation ARCANE_DISC =
            blockTexture("animation/arcane_disc");
    private static final ResourceLocation CORE_ORB =
            blockTexture("animation/core_orb");
    private static final ResourceLocation ENERGY_RIBBON =
            blockTexture("animation/energy_ribbon");
    public MagicMachineAnimationRenderer(
            BlockEntityRendererProvider.Context context, Kind kind) {
        this.kind = kind;
        recipeEntityRenderer = new RecipeEntityMachineRenderer(
                context.getEntityRenderer());
        recipeItemRenderer = new RecipeItemMachineRenderer();
    }

    @Override
    public void render(TILE tile, float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (!MagicClientConfig.animationsEnabled()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof AbstractContainerScreen<?>) {
            return;
        }
        Level level = tile.getLevel();
        if (level == null || !tile.getActive()
                && !MagicClientConfig.forceWorkingAnimations()) {
            return;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        double cameraDistanceSquared = camera.getPosition().distanceToSqr(
                tile.getBlockPos().getCenter());
        if (cameraDistanceSquared > ANIMATION_VIEW_DISTANCE_SQUARED) {
            return;
        }
        resolveSpriteCache();
        TextureAtlasSprite texture = machineSprite;
        if (texture == null) {
            return;
        }
        VertexConsumer buffer = bufferSource.getBuffer(
                Sheets.translucentCullBlockSheet());
        VertexConsumer mechanismBuffer = bufferSource.getBuffer(
                Sheets.cutoutBlockSheet());
        int glow = MekanismRenderer.calculateGlowLight(
                packedLight, 15);
        float time = level.getGameTime() + partialTick
                + (tile.getBlockPos().asLong() & 31L) * 0.17F;

        poseStack.pushPose();
        orientToMachine(tile, poseStack);
        switch (kind) {
            case SOURCE -> renderSource(tile, time, poseStack,
                    buffer, mechanismBuffer, texture, glow,
                    packedOverlay, camera);
            case SOURCE_CONVERTER -> renderConverter(tile, time,
                    poseStack, buffer, texture, glow,
                    packedOverlay, camera);
            case CATALYST_SCANNER -> renderScanner(tile, time,
                    poseStack, buffer, mechanismBuffer, texture, glow,
                    packedOverlay, camera);
            case IMBUEMENT -> renderImbuement(tile, time,
                    poseStack, buffer, mechanismBuffer, texture, glow,
                    packedOverlay, camera);
            case IMBUEMENT_FACTORY -> renderImbuementFactory(
                    tile, time, poseStack, buffer, mechanismBuffer,
                    texture, glow, packedOverlay, camera);
            case ENCHANTING -> renderEnchanting(tile, time,
                    poseStack, buffer, mechanismBuffer, texture, glow,
                    packedOverlay, camera);
            case SPIRIT -> renderSpirit(tile, time, poseStack,
                    buffer, mechanismBuffer, texture, glow,
                    packedOverlay, camera);
            case SPIRIT_FACTORY -> renderSpiritFactory(tile, time,
                    poseStack, buffer, mechanismBuffer, texture,
                    glow, packedOverlay, camera);
            case DIMENSION -> renderDimension(tile, time, poseStack,
                    buffer, mechanismBuffer, texture, glow,
                    packedOverlay, camera);
            case RITUAL -> renderRitual(tile, time, poseStack,
                    buffer, mechanismBuffer, texture, glow,
                    packedOverlay, camera);
            case SCRIBING -> renderScribing(tile, time, poseStack,
                    buffer, mechanismBuffer, texture, glow,
                    packedOverlay, camera);
        }
        if (cameraDistanceSquared <= ITEM_VIEW_DISTANCE_SQUARED) {
            recipeItemRenderer.render(tile, kind, partialTick, poseStack,
                    bufferSource, glow, cameraDistanceSquared);
        }
        if (cameraDistanceSquared <= ENTITY_VIEW_DISTANCE_SQUARED) {
            recipeEntityRenderer.render(tile, kind, partialTick, poseStack,
                    bufferSource, glow);
        }
        poseStack.popPose();
    }

    @Override
    public int getViewDistance() {
        return 32;
    }

    static void invalidateSpriteCache() {
        spriteCacheGeneration++;
    }

    private void resolveSpriteCache() {
        int generation = spriteCacheGeneration;
        if (resolvedSpriteCacheGeneration == generation
                && machineSprite != null) {
            return;
        }
        machineSprite = MekanismRenderer.getSprite(kind.texture);
        authoredArcaneCircleSprite = resolveSprite(
                AUTHORED_ARCANE_CIRCLE, machineSprite);
        coreOrbSprite = resolveSprite(CORE_ORB, machineSprite);
        energyRibbonSprite = resolveSprite(ENERGY_RIBBON, machineSprite);
        fieldEffectSprite = resolveSprite(switch (kind) {
            case SPIRIT, SPIRIT_FACTORY, RITUAL, SCRIBING -> OCCULT_SEAL;
            case DIMENSION -> PHASE_VORTEX;
            default -> ARCANE_DISC;
        }, machineSprite);
        if (kind == Kind.SPIRIT || kind == Kind.SPIRIT_FACTORY) {
            precisionMetalSprite = resolveSprite(
                    PRECISION_METAL, machineSprite);
            occultGoldSprite = resolveSprite(OCCULT_GOLD, machineSprite);
            occultSpiritSprite = resolveSprite(
                    OCCULT_SPIRIT, machineSprite);
        } else {
            precisionMetalSprite = null;
            occultGoldSprite = null;
            occultSpiritSprite = null;
        }
        resolvedSpriteCacheGeneration = generation;
    }

    private static TextureAtlasSprite resolveSprite(
            ResourceLocation location, TextureAtlasSprite fallback) {
        TextureAtlasSprite sprite = MekanismRenderer.getSprite(location);
        return sprite == null ? fallback : sprite;
    }

    private static ResourceLocation blockTexture(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "mekanism_magic", "block/" + path);
    }

    private static void orientToMachine(TileEntityMekanism tile,
                                        PoseStack poseStack) {
        poseStack.translate(0.5D, 0, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                180F - tile.getDirection().toYRot()));
        poseStack.translate(-0.5D, 0, -0.5D);
    }

    private void renderSource(TILE tile, float time,
                              PoseStack poseStack,
                              VertexConsumer buffer,
                              VertexConsumer glyphBuffer,
                              TextureAtlasSprite texture,
                              int light, int overlay, Camera camera) {
        float basePhase = Mth.frac(time * 0.026F);
        for (int index = 0; index < 3; index++) {
            float phase = Mth.frac(basePhase + index / 3F);
            float y = Mth.lerp(phase, 0.405F, 0.805F);
            float fade = Mth.sin(phase * Mth.PI);
            renderSquareLoop(tile, poseStack, glyphBuffer, texture, light,
                    overlay, camera, 0.5F, y, 0.5F,
                    0.084F, 0.011F, 0.010F,
                    color(index == 1 ? kind.secondary : kind.primary,
                            Math.round(70F + fade * 145F)));
        }
        // Counter-wound Source filaments make the generator read as a
        // controlled field rather than a stack of unrelated glowing plates.
        renderHelix(tile, poseStack, buffer, texture, light, overlay,
                camera, 0.5F, 0.430F, 0.790F, 0.5F,
                time * 0.014F, 2.0F, 0.112F,
                6, 0.009F, kind.primary, 178);
        renderHelix(tile, poseStack, buffer, texture, light, overlay,
                camera, 0.5F, 0.455F, 0.805F, 0.5F,
                time * 0.014F + 0.5F, -2.0F, 0.082F,
                5, 0.008F, kind.secondary, 190);
        float crownPulse = (Mth.sin(time * 0.20F) + 1F) * 0.5F;
        renderOrbitingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F, 0.842F, 0.5F,
                time * 2.8F, 0.064F, 3, 0.010F,
                kind.primary, 185);
        renderCenteredBox(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F, 0.858F, 0.5F,
                0.027F + crownPulse * 0.010F,
                0.028F + crownPulse * 0.012F,
                color(kind.secondary,
                        135 + Math.round(crownPulse * 90F)));
    }

    private void renderConverter(TILE tile, float time,
                                 PoseStack poseStack,
                                 VertexConsumer buffer,
                                 TextureAtlasSprite texture,
                                 int light, int overlay, Camera camera) {
        float exchange = (Mth.sin(time * 0.19F) + 1F) * 0.5F;
        renderEnergyOrb(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.397F, 0.496F, 0.5F,
                0.040F + exchange * 0.012F,
                0.035F + exchange * 0.010F, time * 2.1F,
                color(kind.primary, 95 + Math.round(exchange * 135F)));
        renderEnergyOrb(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.603F, 0.496F, 0.5F,
                0.040F + (1F - exchange) * 0.012F,
                0.035F + (1F - exchange) * 0.010F,
                -time * 2.1F,
                color(kind.secondary,
                        95 + Math.round((1F - exchange) * 135F)));
        renderRotatedCenteredBoxY(tile, poseStack, buffer, texture,
                light, overlay, camera, 0.5F, 0.491F, 0.5F,
                time * 2.05F, 0.039F, 0.052F,
                color(exchange > 0.5F ? kind.secondary : kind.primary,
                        175 + Math.round(Math.abs(exchange - 0.5F)
                                * 100F)));
        // FE and Source packets enter from opposite banks and are compressed
        // into the rotating conversion core.
        float packetPhase = Mth.frac(time * 0.034F);
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                0.325F, 0.496F, 0.5F,
                0.475F, 0.496F, 0.5F,
                packetPhase, 3, 0.008F, kind.primary, 205);
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                0.675F, 0.496F, 0.5F,
                0.525F, 0.496F, 0.5F,
                Mth.frac(packetPhase + 0.5F), 3, 0.008F,
                kind.secondary, 205);
        renderHelix(tile, poseStack, buffer, texture, light, overlay,
                camera, 0.5F, 0.525F, 0.655F, 0.5F,
                time * 0.020F, 1.25F, 0.052F,
                4, 0.007F, exchange > 0.5F
                        ? kind.secondary : kind.primary, 180);
        float balance = Mth.sin(time * 0.11F) * 0.035F;
        renderEnergyOrb(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F + balance, 0.680F, 0.385F,
                0.018F, 0.014F, time * 3F,
                color(kind.secondary, 185));
    }

    private void renderScanner(TILE tile, float time,
                               PoseStack poseStack,
                               VertexConsumer buffer,
                               VertexConsumer glyphBuffer,
                               TextureAtlasSprite texture,
                               int light, int overlay, Camera camera) {
        float scan = triangleWave(time * 0.024F);
        float scanY = Mth.lerp(scan, 0.465F, 0.605F);
        // Rotate a copy of the authored 512px sigillum on its exact plate
        // instead of covering it with a generic oversized disc.
        renderHorizontalField(tile, poseStack, glyphBuffer,
                authoredArcaneCircleSprite, light, overlay, camera,
                0.5F, 0.360F, 0.5F, time * 1.35F,
                0.125F, 0.0025F, color(0xFFFFFF, 255));
        renderSquareLoop(tile, poseStack, glyphBuffer, texture, light,
                overlay, camera, 0.5F, scanY, 0.5F,
                0.064F, 0.009F, 0.010F,
                color(kind.secondary,
                        125 + Math.round(Mth.sin(scan * Mth.PI) * 95F)));
        renderOrbitingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F, scanY, 0.5F,
                time * 3.1F, 0.103F, 4, 0.008F,
                kind.secondary, 185);
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                0.5F, 0.395F, 0.5F,
                0.5F, 0.625F, 0.5F,
                Mth.frac(time * 0.026F), 4, 0.006F,
                kind.primary, 170);
        float corePulse = (Mth.sin(time * 0.23F) + 1F) * 0.5F;
        renderCenteredBox(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F, 0.535F, 0.5F,
                0.022F + corePulse * 0.008F,
                0.045F + corePulse * 0.012F,
                color(kind.primary,
                        125 + Math.round(corePulse * 95F)));
    }

    private void renderImbuement(TILE tile, float time,
                                 PoseStack poseStack,
                                 VertexConsumer buffer,
                                 VertexConsumer glyphBuffer,
                                 TextureAtlasSprite texture,
                                 int light, int overlay, Camera camera) {
        // The authored magic circle is vertical, centered where the left,
        // right and overhead injectors meet. Keep every moving part on that
        // plane instead of floating a generic horizontal ring over the block.
        float centerX = 0.5F;
        float centerY = 0.492F;
        // Authored circle front: z=6.75/16. Keep a tiny forward offset only.
        float planeZ = 0.420F;
        float cycle = Mth.frac(time * 0.018F);
        float charge = cycle < 0.22F
                ? smoothStep(cycle / 0.22F)
                : cycle > 0.88F
                ? smoothStep((1F - cycle) / 0.12F) : 1F;
        float pulse = (Mth.sin(time * 0.24F) + 1F) * 0.5F;
        float leftFocus = (Mth.sin(time * 0.28F) + 1F) * 0.5F;
        float topFocus = (Mth.sin(time * 0.28F + 2.094F) + 1F) * 0.5F;
        float rightFocus = (Mth.sin(time * 0.28F + 4.189F) + 1F) * 0.5F;

        renderRibbonX(tile, poseStack, buffer, texture, light,
                overlay, camera, centerY, planeZ, 0.382F, 0.487F,
                0.022F,
                color(kind.primary,
                        Math.round((75F + leftFocus * 115F) * charge)));
        renderRibbonX(tile, poseStack, buffer, texture, light,
                overlay, camera, centerY, planeZ, 0.513F, 0.618F,
                0.022F,
                color(kind.primary,
                        Math.round((75F + rightFocus * 115F) * charge)));
        renderRibbonY(tile, poseStack, buffer, texture, light,
                overlay, camera, centerX, planeZ, 0.505F, 0.642F,
                0.022F,
                color(kind.secondary,
                        Math.round((85F + topFocus * 125F) * charge)));

        float injectionPhase = Mth.frac(time * 0.042F);
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                0.382F, centerY, planeZ,
                0.487F, centerY, planeZ,
                injectionPhase, 2, 0.006F, kind.primary,
                Math.round(220F * charge));
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                0.618F, centerY, planeZ,
                0.513F, centerY, planeZ,
                Mth.frac(injectionPhase + 0.33F), 2, 0.006F,
                kind.primary, Math.round(220F * charge));
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                centerX, 0.642F, planeZ,
                centerX, 0.505F, planeZ,
                Mth.frac(injectionPhase + 0.66F), 2, 0.006F,
                kind.secondary, Math.round(230F * charge));

        // The model already contains the exact 512px Ars circle. Rotate one
        // full-size copy on the same plane rather than layering two generic
        // seals over its geometry.
        renderVerticalField(tile, poseStack, glyphBuffer,
                authoredArcaneCircleSprite, light, overlay, camera,
                centerX, centerY, planeZ, time * 1.75F,
                0.15625F, 0.0025F, color(0xFFFFFF, 255));
        renderVerticalOrbitingNodes(tile, poseStack, buffer, texture,
                light, overlay, camera, centerX, centerY,
                planeZ - 0.008F, -time * 3.0F,
                0.111F, 6, 0.007F, kind.secondary,
                Math.round(190F * charge));

        float coreRadius = 0.020F + pulse * 0.008F;
        renderEnergyOrb(tile, poseStack, buffer, texture, light,
                overlay, camera, centerX, centerY,
                planeZ - 0.008F, coreRadius, coreRadius,
                time * 3.4F,
                color(kind.secondary,
                        Math.round((145F + pulse * 90F) * charge)));
    }

    private void renderImbuementFactory(
            TILE tile, float time, PoseStack poseStack,
            VertexConsumer buffer, VertexConsumer crispEffectBuffer,
            TextureAtlasSprite texture,
            int light, int overlay, Camera camera) {
        for (int index = 0; index < IMBUEMENT_FACTORY_LANES.length;
             index++) {
            float centerX = IMBUEMENT_FACTORY_LANES[index];
            float pulse = (Mth.sin(time * 0.21F
                    + index * 2.094F) + 1F) * 0.5F;
            // Authored rune plate: x centers 4/8/12, z center 7.55,
            // top y 6.75 (all in model units). The previous seal bobbed as
            // high as y=.443, inside the violet core whose bottom is .4281;
            // the core then hid one side of the seal and made it appear
            // laterally displaced in an oblique view. Keep the animated seal
            // in the narrow clear gap immediately above the static pattern.
            float runePlateY = 0.4239F;
            float runePlateZ = 0.471875F;
            renderHorizontalField(tile, poseStack, crispEffectBuffer,
                    authoredArcaneCircleSprite, light, overlay, camera,
                    centerX, runePlateY, runePlateZ,
                    time * (1.20F + index * 0.18F) + index * 30F,
                    0.0625F, 0.002F, color(0xFFFFFF, 255));
            renderCenteredBox(tile, poseStack, buffer, texture,
                    light, overlay, camera, centerX,
                    0.458F + pulse * 0.022F, 0.472F,
                    0.022F + pulse * 0.006F,
                    0.028F + pulse * 0.009F,
                    color(index == 1 ? kind.secondary : kind.primary,
                            255));
            renderEnergyOrb(tile, poseStack, buffer, texture, light,
                    overlay, camera, centerX,
                    0.500F + pulse * 0.010F, 0.522F,
                    0.014F + pulse * 0.004F,
                    0.020F + pulse * 0.006F,
                    time * 3F + index * 40F,
                    color(pulse > 0.55F
                            ? kind.secondary : kind.primary, 255));
            renderTravelingNodes(tile, poseStack, buffer, texture,
                    light, overlay, camera,
                    centerX, 0.545F, 0.548F,
                    centerX, 0.476F, 0.490F,
                    Mth.frac(time * 0.039F + index / 3F),
                    2, 0.006F,
                    index == 1 ? kind.secondary : kind.primary, 255);
            renderOrbitingNodes(tile, poseStack, buffer, texture,
                    light, overlay, camera,
                    centerX, 0.472F, 0.472F,
                    time * (2.4F + index * 0.25F),
                    0.043F + pulse * 0.005F,
                    3, 0.006F,
                    pulse > 0.5F ? kind.secondary : kind.primary, 255);
        }

        float busSweep = triangleWave(time * 0.018F);
        float busX = Mth.lerp(busSweep, 0.205F, 0.795F);
        renderEnergyOrb(tile, poseStack, buffer, texture, light,
                overlay, camera, busX, 0.651F, 0.564F,
                0.024F, 0.018F, time * 2.6F,
                color(busSweep > 0.5F
                        ? kind.secondary : kind.primary, 255));
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                0.205F, 0.651F, 0.564F,
                0.795F, 0.651F, 0.564F,
                Mth.frac(time * 0.024F), 5, 0.006F,
                kind.secondary, 255);

        // Animate the same circle used by the authored master-rune element.
        // Its exact colours and line weights remain identical to the model.
        renderVerticalField(tile, poseStack, crispEffectBuffer,
                authoredArcaneCircleSprite, light,
                overlay, camera, 0.5F, 0.613F, 0.676F,
                time * 1.25F, 0.150F, 0.003F,
                color(0xFFFFFF, 255));
    }

    private void renderEnchanting(TILE tile, float time,
                                  PoseStack poseStack,
                                  VertexConsumer buffer,
                                  VertexConsumer glyphBuffer,
                                  TextureAtlasSprite texture,
                                  int light, int overlay, Camera camera) {
        float weave = (Mth.sin(time * 0.18F) + 1F) * 0.5F;
        // Follow the authored arcane inlay at y=6.06/16; the higher rings are
        // separate suspended mechanisms, not replacements for its animation.
        renderHorizontalField(tile, poseStack, glyphBuffer,
                authoredArcaneCircleSprite, light, overlay, camera,
                0.5F, 0.381F, 0.5F, -time * 0.72F,
                0.125F, 0.002F, color(0xFFFFFF, 255));
        int beamAlpha = 95 + Math.round(weave * 100F);
        renderRibbonZ(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F, 0.429F,
                0.175F, 0.430F, 0.034F,
                color(kind.primary, beamAlpha));
        renderRibbonZ(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F, 0.429F,
                0.570F, 0.825F, 0.034F,
                color(kind.primary, 195 - Math.round(weave * 80F)));
        renderRibbonX(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.429F, 0.5F,
                0.175F, 0.430F, 0.034F,
                color(kind.secondary, 195 - Math.round(weave * 80F)));
        renderRibbonX(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.429F, 0.5F,
                0.570F, 0.825F, 0.034F,
                color(kind.secondary, beamAlpha));
        float convergence = Mth.frac(time * 0.036F);
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                0.5F, 0.438F, 0.180F,
                0.5F, 0.438F, 0.480F,
                convergence, 3, 0.007F, kind.primary, 205);
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                0.5F, 0.438F, 0.820F,
                0.5F, 0.438F, 0.520F,
                Mth.frac(convergence + 0.5F), 3, 0.007F,
                kind.primary, 205);
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                0.180F, 0.438F, 0.5F,
                0.480F, 0.438F, 0.5F,
                Mth.frac(convergence + 0.25F), 3, 0.007F,
                kind.secondary, 205);
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                0.820F, 0.438F, 0.5F,
                0.520F, 0.438F, 0.5F,
                Mth.frac(convergence + 0.75F), 3, 0.007F,
                kind.secondary, 205);
        renderHorizontalRing(tile, poseStack, glyphBuffer, texture, light,
                overlay, camera, 0.5F, 0.555F, 0.5F,
                time * 1.45F, 0.137F, 0.055F, 0.011F,
                0.011F, 8, color(kind.primary, 180));
        renderHorizontalRing(tile, poseStack, glyphBuffer, texture, light,
                overlay, camera, 0.5F, 0.582F, 0.5F,
                -time * 1.9F + 22.5F, 0.083F, 0.042F,
                0.009F, 0.009F, 4, color(kind.secondary, 175));
        renderOrbitingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F, 0.615F, 0.5F,
                time * 2.75F, 0.172F, 6, 0.009F,
                kind.primary, 180);
        renderCenteredBox(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F, 0.598F, 0.5F,
                0.022F + weave * 0.006F,
                0.030F + weave * 0.008F,
                color(kind.secondary, 155 + Math.round(weave * 70F)));
    }

    private void renderSpirit(TILE tile, float time,
                              PoseStack poseStack,
                              VertexConsumer buffer,
                              VertexConsumer mechanismBuffer,
                              TextureAtlasSprite texture,
                              int light, int overlay, Camera camera) {
        TextureAtlasSprite metal = precisionMetalSprite;
        TextureAtlasSprite gold = occultGoldSprite;
        TextureAtlasSprite spirit = occultSpiritSprite;

        // One continuous capture -> compression -> release cycle. The pulses
        // overlap and use circular distance, so no phase snaps at 0/1.
        float cycle = Mth.frac(time * 0.0105F);
        float capture = cyclicPulse(cycle, 0.18F, 0.31F);
        float compression = cyclicPulse(cycle, 0.48F, 0.28F);
        float release = cyclicPulse(cycle, 0.78F, 0.27F);
        float breath = cosineWave(time * 0.038F);

        // The rear vault is a restrained spirit prism, not a humanoid statue.
        // Counter-rotating collars make its containment read mechanically.
        for (int ring = 0; ring < 3; ring++) {
            float ringPulse = cosineWave(time * 0.045F + ring / 3F);
            renderHorizontalRing(tile, poseStack, mechanismBuffer, spirit,
                    light, overlay, camera,
                    0.5F, 0.562F + ring * 0.067F, 0.772F,
                    time * (ring == 1 ? -2.15F : 1.65F)
                            + ring * 30F,
                    0.062F + ringPulse * 0.012F,
                    0.034F, 0.007F, 0.007F, 8,
                    color(ring == 1 ? kind.secondary : kind.primary,
                            120 + Math.round(ringPulse * 95F)));
        }
        renderVerticalRing(tile, poseStack, mechanismBuffer, spirit, light,
                overlay, camera, 0.5F, 0.656F, 0.714F,
                time * 1.35F, 0.111F + breath * 0.009F,
                0.045F, 0.007F, 0.008F, 12,
                color(kind.primary, 150 + Math.round(breath * 85F)));
        renderVerticalRing(tile, poseStack, mechanismBuffer, spirit, light,
                overlay, camera, 0.5F, 0.656F, 0.711F,
                -time * 1.85F + 15F,
                0.066F + (1F - breath) * 0.007F,
                0.034F, 0.006F, 0.007F, 8,
                color(kind.secondary,
                        145 + Math.round((1F - breath) * 90F)));
        renderCenteredBox(tile, poseStack, buffer, spirit, light,
                overlay, camera, 0.5F, 0.656F, 0.708F,
                0.015F + breath * 0.003F,
                0.058F + breath * 0.011F,
                color(kind.secondary, 170 + Math.round(breath * 70F)));

        // Precision rams converge only during compression. Their metal bodies
        // stay connected to the authored nozzles while gold tips do the work.
        float leftTip = 0.355F + compression * 0.058F;
        float rightTip = 1F - leftTip;
        renderMechanicalBox(tile, poseStack, mechanismBuffer, metal, light, overlay,
                camera, 0.327F, 0.422F, 0.350F,
                leftTip, 0.446F, 0.390F,
                color(0xE7EDF1, 235));
        renderMechanicalBox(tile, poseStack, mechanismBuffer, gold, light, overlay,
                camera, leftTip - 0.012F, 0.414F, 0.342F,
                leftTip + 0.009F, 0.454F, 0.398F,
                color(kind.secondary, 190 + Math.round(compression * 60F)));
        renderMechanicalBox(tile, poseStack, mechanismBuffer, metal, light, overlay,
                camera, rightTip, 0.422F, 0.350F,
                0.673F, 0.446F, 0.390F,
                color(0xE7EDF1, 235));
        renderMechanicalBox(tile, poseStack, mechanismBuffer, gold, light, overlay,
                camera, rightTip - 0.009F, 0.414F, 0.342F,
                rightTip + 0.012F, 0.454F, 0.398F,
                color(kind.secondary, 190 + Math.round(compression * 60F)));

        // The conduits provide a continuous base flow. Full-UV orb sprites
        // travel over them, so density is preserved without UV-cropped boxes.
        int intakeAlpha = Math.round(185F
                * Math.max(capture, compression));
        renderRibbonY(tile, poseStack, buffer, spirit, light, overlay,
                camera, 0.5F, 0.700F, 0.500F, 0.650F,
                0.008F, color(kind.primary, intakeAlpha));
        renderRibbonZ(tile, poseStack, buffer, spirit, light, overlay,
                camera, 0.5F, 0.500F, 0.462F, 0.700F,
                0.008F, color(kind.primary, intakeAlpha));
        renderRibbonX(tile, poseStack, buffer, texture, light, overlay,
                camera, 0.405F, 0.438F, leftTip, 0.485F,
                0.007F,
                color(kind.secondary, Math.round(205F * compression)));
        renderRibbonX(tile, poseStack, buffer, texture, light, overlay,
                camera, 0.405F, 0.438F, 0.515F, rightTip,
                0.007F,
                color(kind.secondary, Math.round(205F * compression)));
        float intakePhase = Mth.frac(time * 0.046F);
        renderTravelingNodes(tile, poseStack, buffer, spirit, light,
                overlay, camera,
                0.5F, 0.650F, 0.700F,
                0.5F, 0.500F, 0.462F,
                intakePhase, 5, 0.0065F, kind.primary,
                Math.round(175F * Math.max(capture, compression)));
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                leftTip, 0.405F, 0.438F,
                0.485F, 0.405F, 0.438F,
                Mth.frac(intakePhase + 0.33F), 3, 0.0055F,
                kind.secondary, Math.round(190F * compression));
        renderTravelingNodes(tile, poseStack, buffer, texture, light,
                overlay, camera,
                rightTip, 0.405F, 0.438F,
                0.515F, 0.405F, 0.438F,
                Mth.frac(intakePhase + 0.66F), 3, 0.0055F,
                kind.secondary, Math.round(190F * compression));

        float sealRadius = 0.132F - compression * 0.035F;
        renderHorizontalRing(tile, poseStack, mechanismBuffer, texture, light,
                overlay, camera, 0.5F, 0.381F, 0.438F,
                time * 2.1F, sealRadius, 0.052F,
                0.008F, 0.007F, 12,
                color(kind.primary,
                        105 + Math.round(compression * 130F)));
        renderSquareLoop(tile, poseStack, mechanismBuffer, texture, light,
                overlay, camera, 0.5F, 0.383F, 0.438F,
                0.074F + breath * 0.010F,
                0.006F, 0.006F,
                color(kind.secondary,
                        90 + Math.round(compression * 145F)));

        // Finished essence leaves through the front channel instead of simply
        // disappearing when the cycle wraps.
        renderRibbonZ(tile, poseStack, buffer, spirit, light, overlay,
                camera, 0.5F, 0.397F, 0.165F, 0.410F,
                0.008F,
                color(kind.secondary, Math.round(205F * release)));
    }

    private void renderSpiritFactory(TILE tile, float time,
                                     PoseStack poseStack,
                                     VertexConsumer buffer,
                                     VertexConsumer mechanismBuffer,
                                     TextureAtlasSprite texture,
                                     int light, int overlay,
                                     Camera camera) {
        TextureAtlasSprite metal = precisionMetalSprite;
        TextureAtlasSprite gold = occultGoldSprite;
        TextureAtlasSprite spirit = occultSpiritSprite;

        float heart = cosineWave(time * 0.041F);
        // The shared core is a readable circular containment seal. The old
        // crossed bars looked like a large X in first-person and obscured the
        // spirit, especially across a wall of adjacent factories.
        renderVerticalRing(tile, poseStack, mechanismBuffer, spirit, light,
                overlay, camera, 0.5F, 0.652F, 0.718F,
                time * 1.35F, 0.124F + heart * 0.009F,
                0.050F, 0.007F, 0.008F, 12,
                color(kind.primary, 175 + Math.round(heart * 35F)));
        renderVerticalRing(tile, poseStack, mechanismBuffer, texture, light,
                overlay, camera, 0.5F, 0.652F, 0.714F,
                -time * 1.75F + 15F,
                0.075F + (1F - heart) * 0.007F,
                0.038F, 0.006F, 0.007F, 8,
                color(kind.secondary,
                        105 + Math.round((1F - heart) * 35F)));
        renderCenteredBox(tile, poseStack, buffer, spirit, light,
                overlay, camera, 0.5F, 0.652F, 0.710F,
                0.016F + heart * 0.003F,
                0.063F + heart * 0.010F,
                color(kind.secondary, 185 + Math.round(heart * 35F)));

        // A permanent T-shaped distribution manifold reads as controlled
        // spirit flow and does not rely on detached square particles.
        int manifoldAlpha = 120 + Math.round(heart * 60F);
        renderRibbonY(tile, poseStack, buffer, spirit, light, overlay,
                camera, 0.5F, 0.715F, 0.435F, 0.650F,
                0.008F, color(kind.primary, manifoldAlpha));
        renderRibbonZ(tile, poseStack, buffer, spirit, light, overlay,
                camera, 0.5F, 0.435F, 0.545F, 0.715F,
                0.008F, color(kind.primary, manifoldAlpha));
        renderRibbonX(tile, poseStack, buffer, spirit, light, overlay,
                camera, 0.435F, 0.545F, 0.21875F, 0.78125F,
                0.008F, color(kind.primary, manifoldAlpha));

        for (int lane = 0; lane < SPIRIT_FACTORY_LANES.length; lane++) {
            float centerX = SPIRIT_FACTORY_LANES[lane];
            float phase = Mth.frac(time * 0.0105F + lane / 3F);
            float travel = cosineWave(phase);
            float engage = cyclicPulse(phase, 0.52F, 0.31F);
            float discharge = cyclicPulse(phase, 0.84F, 0.24F);
            float carriageZ = Mth.lerp(travel, 0.242F, 0.485F);
            float carriageY = 0.389F + engage * 0.016F;
            int laneColor = lane == 1 ? kind.secondary : kind.primary;

            // Each lane now has a physical carriage and clamp, not a floating
            // scan line. The sinusoidal shuttle is velocity-continuous at both
            // ends of the track.
            renderMechanicalBox(tile, poseStack, mechanismBuffer, metal, light, overlay,
                    camera, centerX - 0.055F, carriageY - 0.010F,
                    carriageZ - 0.040F,
                    centerX + 0.055F, carriageY + 0.010F,
                    carriageZ + 0.040F, color(0xE7EDF1, 255));
            renderMechanicalBox(tile, poseStack, mechanismBuffer, gold, light, overlay,
                    camera, centerX - 0.032F, carriageY + 0.010F,
                    carriageZ - 0.030F,
                    centerX + 0.032F,
                    carriageY + 0.027F + engage * 0.010F,
                    carriageZ + 0.030F, color(laneColor, 255));
            renderHorizontalRing(tile, poseStack, mechanismBuffer, texture,
                    light, overlay, camera, centerX,
                    carriageY + 0.033F, carriageZ,
                    time * (lane == 1 ? -2.2F : 1.8F)
                            + lane * 28F,
                    0.052F - engage * 0.010F,
                    0.030F, 0.006F, 0.006F, 8,
                    color(laneColor, 130 + Math.round(engage * 65F)));
            renderSquareLoop(tile, poseStack, mechanismBuffer, texture, light,
                    overlay, camera, centerX, 0.386F, 0.413F,
                    0.052F + engage * 0.010F,
                    0.006F, 0.006F,
                    color(lane == 1 ? kind.primary : kind.secondary,
                            75 + Math.round(engage * 85F)));

            int feedAlpha = 85 + Math.round(engage * 105F);
            renderRibbonZ(tile, poseStack, buffer, spirit,
                    light, overlay, camera, centerX, 0.435F,
                    Math.min(carriageZ, 0.545F), 0.545F,
                    0.007F, color(laneColor, feedAlpha));
            renderRibbonZ(tile, poseStack, buffer, texture,
                    light, overlay, camera, centerX, 0.386F,
                    0.168F, carriageZ, 0.007F,
                    color(kind.secondary,
                            Math.round(185F
                                    * Math.max(engage, discharge))));
            renderEnergyOrb(tile, poseStack, buffer, texture, light,
                    overlay, camera, centerX,
                    carriageY + 0.034F, carriageZ,
                    0.009F + engage * 0.003F, 0.007F,
                    time * (lane == 1 ? -2.3F : 2.0F),
                    color(laneColor, 155 + Math.round(engage * 65F)));

            float flowPhase = Mth.frac(time * 0.043F + lane / 3F);
            renderTravelingNodes(tile, poseStack, buffer, spirit,
                    light, overlay, camera,
                    0.5F, 0.435F, 0.545F,
                    centerX, 0.435F, 0.545F,
                    flowPhase, 4, 0.005F,
                    laneColor, 175);
            renderTravelingNodes(tile, poseStack, buffer, texture,
                    light, overlay, camera,
                    centerX, 0.386F, carriageZ,
                    centerX, 0.386F, 0.168F,
                    Mth.frac(flowPhase + 0.5F), 3, 0.0048F,
                    kind.secondary,
                    Math.round(185F * Math.max(engage, discharge)));
        }

        renderVerticalOrbitingNodes(tile, poseStack, buffer, texture,
                light, overlay, camera, 0.5F, 0.652F, 0.706F,
                -time * 2.45F, 0.099F, 6, 0.006F,
                kind.secondary, 175);
    }

    private void renderDimension(TILE tile, float time,
                                 PoseStack poseStack,
                                 VertexConsumer buffer,
                                 VertexConsumer glyphBuffer,
                                 TextureAtlasSprite texture,
                                 int light, int overlay, Camera camera) {
        float aperturePulse = (Mth.sin(time * 0.17F) + 1F) * 0.5F;
        // Static dimensional aperture top: y=4.82/16=0.30125.
        float apertureY = 0.303F + aperturePulse * 0.003F;

        // The machine bores through a horizontal dimensional well rather than
        // presenting a decorative upright portal.
        renderHorizontalRing(tile, poseStack, glyphBuffer, texture, light,
                overlay, camera, 0.5F, apertureY, 0.469F,
                time * 1.45F, 0.145F, 0.078F,
                0.009F, 0.007F, 8,
                color(kind.primary,
                        135 + Math.round(aperturePulse * 85F)));
        renderHorizontalRing(tile, poseStack, glyphBuffer, texture, light,
                overlay, camera, 0.5F, apertureY + 0.008F, 0.469F,
                -time * 2.1F + 22.5F, 0.098F, 0.058F,
                0.008F, 0.006F, 4,
                color(kind.secondary,
                        145 + Math.round((1F - aperturePulse) * 75F)));
        renderVortexNodes(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F, apertureY + 0.018F, 0.469F,
                time * 0.017F, 7, 0.190F, 0.032F,
                0.008F, kind.secondary, 205);

        // Four phase streams converge around the suspended extraction nozzle.
        float streamTop = 0.326F + aperturePulse * 0.008F;
        renderRibbonY(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.452F, 0.457F,
                0.307F, streamTop, 0.018F,
                color(kind.secondary, 175));
        renderRibbonY(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.548F, 0.457F,
                0.307F, streamTop, 0.018F,
                color(kind.secondary, 175));
        renderRibbonY(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.475F, 0.415F,
                0.307F, streamTop, 0.018F,
                color(kind.primary, 175));
        renderRibbonY(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.525F, 0.523F,
                0.307F, streamTop, 0.018F,
                color(kind.primary, 175));

        // Extracted matter visibly travels from the foreign stratum toward the
        // front output chute.
        for (int packet = 0; packet < 3; packet++) {
            float phase = Mth.frac(time * 0.021F + packet / 3F);
            float returnZ = Mth.lerp(phase, 0.274F, 0.108F);
            float returnX = 0.485F
                    + (packet % 2 == 0 ? -0.014F : 0.014F);
            renderCenteredBox(tile, poseStack, buffer, texture, light,
                    overlay, camera, returnX,
                    0.329F + Mth.sin(phase * Mth.PI) * 0.006F,
                    returnZ, 0.011F, 0.009F,
                    color(packet == 1 ? kind.secondary : kind.primary,
                            125 + Math.round(
                                    Mth.sin(phase * Mth.PI) * 95F)));
        }
    }

    private void renderRitual(TILE tile, float time,
                              PoseStack poseStack,
                              VertexConsumer buffer,
                              VertexConsumer glyphBuffer,
                              TextureAtlasSprite texture,
                              int light, int overlay, Camera camera) {
        float sequence = Mth.frac(time * 0.032F) * 4F;
        int north = ritualLaneAlpha(sequence, 0);
        int east = ritualLaneAlpha(sequence, 1);
        int south = ritualLaneAlpha(sequence, 2);
        int west = ritualLaneAlpha(sequence, 3);
        int sealAlpha = (north + east + south + west) / 4;
        renderHorizontalField(tile, poseStack, glyphBuffer,
                fieldEffectSprite, light, overlay, camera,
                0.5F, 0.380F, 0.5F, time * 0.72F,
                0.245F, 0.0025F,
                color(kind.primary, sealAlpha));
        float bridgePulse = (Mth.sin(time * 0.20F) + 1F) * 0.5F;
        renderHorizontalRing(tile, poseStack, glyphBuffer, texture, light,
                overlay, camera, 0.5F, 0.423F, 0.5F,
                time * 1.45F, 0.145F, 0.054F,
                0.010F, 0.010F, 8,
                color(kind.secondary,
                        105 + Math.round(bridgePulse * 110F)));
        renderCenteredBox(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.5F, 0.462F, 0.5F,
                0.025F + bridgePulse * 0.009F,
                0.034F + bridgePulse * 0.010F,
                color(kind.primary,
                        145 + Math.round(bridgePulse * 75F)));
        float ritualPhase = Mth.frac(time * 0.032F);
        renderHelix(tile, poseStack, buffer, texture, light, overlay,
                camera, 0.5F, 0.438F, 0.620F, 0.5F,
                ritualPhase, 1.5F, 0.082F,
                6, 0.007F, kind.secondary, 185);
        renderCenteredBox(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.315F, 0.421F, 0.315F,
                0.011F, 0.008F,
                color(kind.primary, ritualLaneAlpha(sequence, 0)));
        renderCenteredBox(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.685F, 0.421F, 0.315F,
                0.011F, 0.008F,
                color(kind.primary, ritualLaneAlpha(sequence, 1)));
        renderCenteredBox(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.685F, 0.421F, 0.685F,
                0.011F, 0.008F,
                color(kind.primary, ritualLaneAlpha(sequence, 2)));
        renderCenteredBox(tile, poseStack, buffer, texture, light,
                overlay, camera, 0.315F, 0.421F, 0.685F,
                0.011F, 0.008F,
                color(kind.primary, ritualLaneAlpha(sequence, 3)));
    }

    private void renderScribing(TILE tile, float time,
                                PoseStack poseStack,
                                VertexConsumer buffer,
                                VertexConsumer glyphBuffer,
                                TextureAtlasSprite texture,
                                int light, int overlay, Camera camera) {
        float x = Mth.lerp(triangleWave(time * 0.018F),
                0.29F, 0.71F);
        float z = Mth.lerp(triangleWave(time * 0.011F + 0.25F),
                0.29F, 0.71F);
        float chalkPulse = (Mth.sin(time * 0.26F) + 1F) * 0.5F;
        renderSquareLoop(tile, poseStack, glyphBuffer, texture, light,
                overlay, camera, 0.5F, 0.370F, 0.5F,
                0.245F, 0.007F, 0.008F,
                color(kind.secondary, 70));
        renderHorizontalField(tile, poseStack, glyphBuffer,
                fieldEffectSprite, light, overlay, camera,
                x, 0.371F, z, time * 3.4F,
                0.052F + chalkPulse * 0.009F, 0.0025F,
                color(kind.primary,
                        150 + Math.round(chalkPulse * 75F)));
        renderEnergyOrb(tile, poseStack, buffer, texture, light,
                overlay, camera, x, 0.376F, z,
                0.013F + chalkPulse * 0.004F,
                0.011F + chalkPulse * 0.003F,
                -time * 4F, color(kind.secondary, 210));
        // A short geometric afterimage makes the scribing head feel fast
        // without spawning world particles or leaving the block bounds.
        for (int trail = 1; trail <= 4; trail++) {
            float delayed = trail * 1.55F;
            float trailX = Mth.lerp(
                    triangleWave((time - delayed) * 0.018F),
                    0.29F, 0.71F);
            float trailZ = Mth.lerp(
                    triangleWave((time - delayed) * 0.011F + 0.25F),
                    0.29F, 0.71F);
            renderCenteredBox(tile, poseStack, buffer, texture, light,
                    overlay, camera, trailX, 0.371F, trailZ,
                    0.006F - trail * 0.0007F, 0.004F,
                    color(trail % 2 == 0
                                    ? kind.secondary : kind.primary,
                            135 - trail * 22));
        }
    }

    private void renderTravelingNodes(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera,
            float startX, float startY, float startZ,
            float endX, float endY, float endZ,
            float phase, int nodes, float radius,
            int rgb, int alpha) {
        for (int index = 0; index < nodes; index++) {
            float progress = Mth.frac(phase + index / (float) nodes);
            float envelope = smoothStep(Mth.sin(progress * Mth.PI));
            // Do not let a node become fully invisible at a path endpoint.
            // The short visible hand-off removes the old blinking/reset look.
            float visibility = 0.28F + envelope * 0.72F;
            float x = Mth.lerp(progress, startX, endX);
            float y = Mth.lerp(progress, startY, endY);
            float z = Mth.lerp(progress, startZ, endZ);
            renderEnergyOrb(tile, poseStack, buffer, texture, light,
                    overlay, camera, x, y, z,
                    radius * PARTICLE_VISUAL_SCALE
                            * (0.62F + envelope * 0.68F),
                    radius * PARTICLE_VISUAL_SCALE
                            * (0.72F + envelope * 0.85F),
                    phase * 360F + index * 71F,
                    color(rgb, Math.round(alpha * visibility)));
        }
    }

    private void renderOrbitingNodes(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float centerX, float y, float centerZ,
            float angleDegrees, float orbitRadius, int nodes,
            float nodeRadius, int rgb, int alpha) {
        float baseAngle = angleDegrees * Mth.DEG_TO_RAD;
        for (int index = 0; index < nodes; index++) {
            float angle = baseAngle + index * Mth.TWO_PI / nodes;
            float pulse = 0.72F + 0.28F
                    * (Mth.sin(angle * 2F) + 1F) * 0.5F;
            float x = centerX + Mth.cos(angle) * orbitRadius;
            float nodeY = y + Mth.sin(angle * 2F) * 0.006F;
            float z = centerZ + Mth.sin(angle) * orbitRadius;
            renderEnergyOrb(tile, poseStack, buffer, texture, light,
                    overlay, camera, x, nodeY, z,
                    nodeRadius * PARTICLE_VISUAL_SCALE * pulse,
                    nodeRadius * PARTICLE_VISUAL_SCALE * pulse * 1.15F,
                    angle * Mth.RAD_TO_DEG,
                    color(rgb, Math.round(alpha * (0.18F + pulse * 0.82F))));
        }
    }

    private void renderVerticalOrbitingNodes(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float centerX, float centerY, float z,
            float angleDegrees, float orbitRadius, int nodes,
            float nodeRadius, int rgb, int alpha) {
        float baseAngle = angleDegrees * Mth.DEG_TO_RAD;
        for (int index = 0; index < nodes; index++) {
            float angle = baseAngle + index * Mth.TWO_PI / nodes;
            float pulse = 0.75F + 0.25F
                    * (Mth.cos(angle * 3F) + 1F) * 0.5F;
            renderEnergyOrb(tile, poseStack, buffer, texture, light,
                    overlay, camera,
                    centerX + Mth.cos(angle) * orbitRadius,
                    centerY + Mth.sin(angle) * orbitRadius,
                    z, nodeRadius * PARTICLE_VISUAL_SCALE * pulse,
                    nodeRadius * PARTICLE_VISUAL_SCALE * pulse,
                    angle * Mth.RAD_TO_DEG,
                    color(rgb, Math.round(alpha * (0.18F + pulse * 0.82F))));
        }
    }

    private void renderHelix(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float centerX, float minY, float maxY,
            float centerZ, float phase, float turns,
            float helixRadius, int nodes, float nodeRadius,
            int rgb, int alpha) {
        for (int index = 0; index < nodes; index++) {
            float progress = Mth.frac(phase + index / (float) nodes);
            float angle = progress * Mth.TWO_PI * turns;
            float envelope = Mth.sin(progress * Mth.PI);
            float visibility = 0.24F + envelope * 0.76F;
            renderEnergyOrb(tile, poseStack, buffer, texture, light,
                    overlay, camera,
                    centerX + Mth.cos(angle) * helixRadius,
                    Mth.lerp(progress, minY, maxY),
                    centerZ + Mth.sin(angle) * helixRadius,
                    nodeRadius * PARTICLE_VISUAL_SCALE
                            * (0.22F + envelope * 0.78F),
                    nodeRadius * PARTICLE_VISUAL_SCALE
                            * (0.22F + envelope * 0.78F),
                    angle * Mth.RAD_TO_DEG,
                    color(rgb, Math.round(alpha * visibility)));
        }
    }

    private void renderVortexNodes(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float centerX, float y, float centerZ,
            float phase, int nodes, float outerRadius,
            float innerRadius, float nodeRadius,
            int rgb, int alpha) {
        for (int index = 0; index < nodes; index++) {
            float progress = Mth.frac(phase + index / (float) nodes);
            float radius = Mth.lerp(progress, outerRadius, innerRadius);
            float angle = progress * Mth.TWO_PI * 2.5F
                    - phase * Mth.TWO_PI;
            float envelope = Mth.sin(progress * Mth.PI);
            float visibility = 0.25F + envelope * 0.75F;
            renderEnergyOrb(tile, poseStack, buffer, texture, light,
                    overlay, camera,
                    centerX + Mth.cos(angle) * radius,
                    y + envelope * 0.018F,
                    centerZ + Mth.sin(angle) * radius,
                    nodeRadius * PARTICLE_VISUAL_SCALE
                            * (0.68F + envelope * 0.45F),
                    nodeRadius * PARTICLE_VISUAL_SCALE
                            * (0.68F + envelope * 0.45F),
                    angle * Mth.RAD_TO_DEG,
                    color(rgb, Math.round(alpha * visibility)));
        }
    }

    private void renderHorizontalRing(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float centerX, float y, float centerZ,
            float angle, float radius, float length, float width,
            float height, int segments, int color) {
        renderHorizontalField(tile, poseStack, buffer,
                fieldEffectSprite, light, overlay, camera,
                centerX, y, centerZ, angle,
                radius + Math.max(length, width) * 0.45F,
                Math.max(0.0025F, height * 0.35F), color);
    }

    private void renderSquareLoop(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float centerX, float y, float centerZ,
            float halfSize, float width, float height, int color) {
        renderHorizontalField(tile, poseStack, buffer,
                fieldEffectSprite, light, overlay, camera,
                centerX, y, centerZ, 0,
                halfSize, Math.max(0.0025F, height * 0.35F), color);
    }

    private void renderRotatedCenteredBoxY(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float centerX, float y, float centerZ,
            float angle, float radius, float halfHeight, int color) {
        poseStack.pushPose();
        poseStack.translate(centerX, y, centerZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.translate(-centerX, -y, -centerZ);
        renderCenteredBox(tile, poseStack, buffer, texture, light,
                overlay, camera, centerX, y, centerZ,
                radius, halfHeight, color);
        poseStack.popPose();
    }

    private void renderVerticalRing(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float centerX, float centerY, float z,
            float angle, float radius, float length, float width,
            float depth, int segments, int color) {
        renderVerticalField(tile, poseStack, buffer,
                fieldEffectSprite, light, overlay, camera,
                centerX, centerY, z, angle,
                radius + Math.max(length, width) * 0.45F,
                Math.max(0.0025F, depth * 0.35F), color);
    }

    private void renderAxialRingX(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float x, float centerY, float centerZ,
            float angle, float radius, float length, float width,
            float depth, int segments, int color) {
        renderAxialFieldX(tile, poseStack, buffer,
                fieldEffectSprite, light, overlay, camera,
                x, centerY, centerZ, angle,
                radius + Math.max(length, width) * 0.45F,
                Math.max(0.0025F, depth * 0.35F), color);
    }

    private void renderCenteredBox(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float x, float y, float z,
            float radius, float halfHeight, int color) {
        renderEnergyOrb(tile, poseStack, buffer, texture, light,
                overlay, camera, x, y, z, radius, halfHeight,
                0, color);
    }

    private void renderHorizontalField(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float centerX, float y, float centerZ,
            float angle, float halfSize, float halfDepth, int color) {
        poseStack.pushPose();
        poseStack.translate(centerX, y, centerZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.translate(-centerX, -y, -centerZ);
        renderFullUvQuad(poseStack, buffer, texture, light, overlay,
                color, 0, 1, 0,
                centerX - halfSize, y, centerZ - halfSize,
                centerX - halfSize, y, centerZ + halfSize,
                centerX + halfSize, y, centerZ + halfSize,
                centerX + halfSize, y, centerZ - halfSize);
        poseStack.popPose();
    }

    private void renderVerticalField(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float centerX, float centerY, float z,
            float angle, float halfSize, float halfDepth, int color) {
        poseStack.pushPose();
        poseStack.translate(centerX, centerY, z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angle));
        poseStack.translate(-centerX, -centerY, -z);
        renderFullUvQuad(poseStack, buffer, texture, light, overlay,
                color, 0, 0, 1,
                centerX - halfSize, centerY - halfSize, z,
                centerX - halfSize, centerY + halfSize, z,
                centerX + halfSize, centerY + halfSize, z,
                centerX + halfSize, centerY - halfSize, z);
        poseStack.popPose();
    }

    private void renderAxialFieldX(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float x, float centerY, float centerZ,
            float angle, float halfSize, float halfDepth, int color) {
        poseStack.pushPose();
        poseStack.translate(x, centerY, centerZ);
        poseStack.mulPose(Axis.XP.rotationDegrees(angle));
        poseStack.translate(-x, -centerY, -centerZ);
        renderFullUvQuad(poseStack, buffer, texture, light, overlay,
                color, 1, 0, 0,
                x, centerY - halfSize, centerZ - halfSize,
                x, centerY + halfSize, centerZ - halfSize,
                x, centerY + halfSize, centerZ + halfSize,
                x, centerY - halfSize, centerZ + halfSize);
        poseStack.popPose();
    }

    /**
     * Draws a complete atlas sprite on one two-sided quad.
     *
     * <p>RenderResizableCuboid deliberately derives UVs from block-space
     * bounds so that fluids tile every block. That is correct for tanks but
     * wrong for an authored seal/orb: a 0.2-block field receives only the
     * 0.4..0.6 UV interval and enlarges the middle of the sprite into a solid
     * square. Effect geometry must always submit the full sprite UV range.</p>
     */
    private void renderFullUvQuad(
            PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay, int color,
            float normalX, float normalY, float normalZ,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4) {
        Matrix4f matrix = poseStack.last().pose();
        Vector3f normal = normalScratch.set(normalX, normalY, normalZ);
        poseStack.last().normal().transform(normal).normalize();
        int alpha = color >>> 24;
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        // TextureAtlasSprite#getU/getV use the traditional 0..16 sprite
        // coordinate range. Passing 1 therefore sampled only the first 1/16
        // of the image and stretched that fragment over the whole quad. Use
        // the explicit atlas bounds so the complete crisp motif is rendered.
        float minU = texture.getU0();
        float maxU = texture.getU1();
        float minV = texture.getV0();
        float maxV = texture.getV1();

        emitVertex(buffer, matrix, x1, y1, z1, minU, maxV,
                red, green, blue, alpha, light, overlay,
                normal.x(), normal.y(), normal.z());
        emitVertex(buffer, matrix, x2, y2, z2, minU, minV,
                red, green, blue, alpha, light, overlay,
                normal.x(), normal.y(), normal.z());
        emitVertex(buffer, matrix, x3, y3, z3, maxU, minV,
                red, green, blue, alpha, light, overlay,
                normal.x(), normal.y(), normal.z());
        emitVertex(buffer, matrix, x4, y4, z4, maxU, maxV,
                red, green, blue, alpha, light, overlay,
                normal.x(), normal.y(), normal.z());

        normal.negate();
        emitVertex(buffer, matrix, x4, y4, z4, maxU, maxV,
                red, green, blue, alpha, light, overlay,
                normal.x(), normal.y(), normal.z());
        emitVertex(buffer, matrix, x3, y3, z3, maxU, minV,
                red, green, blue, alpha, light, overlay,
                normal.x(), normal.y(), normal.z());
        emitVertex(buffer, matrix, x2, y2, z2, minU, minV,
                red, green, blue, alpha, light, overlay,
                normal.x(), normal.y(), normal.z());
        emitVertex(buffer, matrix, x1, y1, z1, minU, maxV,
                red, green, blue, alpha, light, overlay,
                normal.x(), normal.y(), normal.z());
    }

    private static void emitVertex(
            VertexConsumer buffer, Matrix4f matrix,
            float x, float y, float z, float u, float v,
            int red, int green, int blue, int alpha,
            int light, int overlay,
            float normalX, float normalY, float normalZ) {
        buffer.addVertex(matrix, x, y, z)
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(normalX, normalY, normalZ);
    }

    private void renderEnergyOrb(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite fallback, int light, int overlay,
            Camera camera, float x, float y, float z,
            float radius, float halfHeight, float angle, int color) {
        TextureAtlasSprite orb = coreOrbSprite;
        float width = Math.max(radius * 1.8F, 0.006F);
        float height = Math.max(halfHeight * 1.8F, 0.006F);
        renderVerticalField(tile, poseStack, buffer, orb, light,
                overlay, camera, x, y, z, angle,
                Math.max(width, height), 0.0018F, color);
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(90F));
        poseStack.translate(-x, -y, -z);
        renderVerticalField(tile, poseStack, buffer, orb, light,
                overlay, camera, x, y, z, -angle * 0.63F,
                Math.max(width, height), 0.0018F, color);
        poseStack.popPose();
    }

    private void renderRibbonZ(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite fallback, int light, int overlay,
            Camera camera, float x, float y, float minZ, float maxZ,
            float halfWidth, int color) {
        TextureAtlasSprite ribbon = energyRibbonSprite;
        renderFullUvQuad(poseStack, buffer, ribbon, light, overlay,
                color, 0, 1, 0,
                x - halfWidth, y, minZ,
                x - halfWidth, y, maxZ,
                x + halfWidth, y, maxZ,
                x + halfWidth, y, minZ);
    }

    private void renderRibbonX(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite fallback, int light, int overlay,
            Camera camera, float y, float z, float minX, float maxX,
            float halfWidth, int color) {
        float centerX = (minX + maxX) * 0.5F;
        float halfLength = (maxX - minX) * 0.5F;
        poseStack.pushPose();
        poseStack.translate(centerX, y, z);
        poseStack.mulPose(Axis.YP.rotationDegrees(90F));
        poseStack.translate(-centerX, -y, -z);
        renderRibbonZ(tile, poseStack, buffer, fallback, light,
                overlay, camera, centerX, y, z - halfLength,
                z + halfLength, halfWidth, color);
        poseStack.popPose();
    }

    private void renderRibbonY(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite fallback, int light, int overlay,
            Camera camera, float x, float z, float minY, float maxY,
            float halfWidth, int color) {
        TextureAtlasSprite ribbon = energyRibbonSprite;
        renderFullUvQuad(poseStack, buffer, ribbon, light, overlay,
                color, 0, 0, 1,
                x - halfWidth, minY, z,
                x - halfWidth, maxY, z,
                x + halfWidth, maxY, z,
                x + halfWidth, minY, z);
        renderFullUvQuad(poseStack, buffer, ribbon, light, overlay,
                color, 1, 0, 0,
                x, minY, z - halfWidth,
                x, maxY, z - halfWidth,
                x, maxY, z + halfWidth,
                x, minY, z + halfWidth);
    }

    /** Only solid moving mechanisms use Mekanism's block-space UV renderer. */
    private void renderMechanicalBox(
            TILE tile, PoseStack poseStack, VertexConsumer buffer,
            TextureAtlasSprite texture, int light, int overlay,
            Camera camera, float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ, int color) {
        box.setTexture(texture)
                .xBounds(minX, maxX)
                .yBounds(minY, maxY)
                .zBounds(minZ, maxZ);
        // The camera-side optimisation in RenderResizableCuboid evaluates an
        // axis-aligned box before our machine-facing and mechanism rotations
        // are applied. It therefore selects the wrong faces for rotated rings
        // and injectors, making parts disappear as the camera moves. Emitting
        // both windings keeps every transformed surface stable from all views.
        MekanismRenderer.renderObject(box, poseStack, buffer,
                color, light, overlay, FaceDisplay.BOTH,
                camera, tile.getBlockPos());
    }

    private static float triangleWave(float value) {
        return 1F - Math.abs(Mth.frac(value) * 2F - 1F);
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0F, 1F);
        return clamped * clamped * (3F - 2F * clamped);
    }

    private static float cosineWave(float value) {
        return 0.5F - 0.5F * Mth.cos(Mth.frac(value) * Mth.TWO_PI);
    }

    private static float cyclicPulse(float phase, float center,
                                     float halfWidth) {
        float distance = Math.abs(Mth.frac(phase - center + 0.5F) - 0.5F);
        return smoothStep(1F - distance / halfWidth);
    }

    private static int ritualLaneAlpha(float sequence, int lane) {
        float distance = Math.abs(sequence - lane);
        distance = Math.min(distance, 4F - distance);
        float pulse = smoothStep(1F - Math.min(1F, distance));
        return 70 + Math.round(pulse * 150F);
    }

    private static int color(int rgb, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | rgb;
    }
}
