package com.example.mekanismmagic.integration.ae2.storage;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEItemKey;
import appeng.client.gui.style.Blitter;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Resource imagery shared by cell tooltips, terminals, configuration slots and storage monitors. */
final class MagicResourceKeyRenderHandler implements AEKeyRenderHandler<MagicResourceKey> {
    // Same atlas sprite and 16x16 GUI presentation as Ars Energistique 2.1.1-beta SourceRenderer.
    // Referencing Ars' resource ID needs neither an ArsEng class nor its storage-key registration.
    static final Visual SOURCE = new Visual(ResourceLocation.parse("ars_nouveau:block/mana_still"), true, false, -1);
    static final ResourceLocation AURA_TEXTURE = ResourceLocation.parse("naturesaura:textures/particles/magic_round.png");

    record Visual(ResourceLocation texture, boolean atlas, boolean blending, int color) { }

    static Visual aura(int nativeColor) {
        return new Visual(AURA_TEXTURE, false, true, 0xFF000000 | (nativeColor & 0xFFFFFF));
    }

    private static @Nullable Visual visual(MagicResourceKey key) {
        if (MagicResourceKeys.SOURCE_ID.equals(key.resource())) return SOURCE;
        if (ModCompatibility.naturesAuraLoaded()) {
            Integer color = MagicAuraResources.color(key.resource());
            if (color != null) return aura(color);
        }
        // Preserve the documented item-icon API for explicitly registered third-party resource families.
        return null;
    }

    @Override
    public void drawInGui(Minecraft mc, GuiGraphics graphics, int x, int y, MagicResourceKey key) {
        Visual visual = visual(key);
        if (visual == null) {
            AEKeyRendering.drawInGui(mc, graphics, x, y, extensionIcon(key));
            return;
        }
        drawInGui(graphics, x, y, visual);
    }

    static void drawInGui(GuiGraphics graphics, int x, int y, Visual visual) {
        // Resolve the sprite on each draw: atlas animation and resource reloads remain native.
        // Tint belongs to this blit, never mutable global shader state leaking into adjacent cells.
        Blitter blitter = visual.atlas()
                ? Blitter.sprite(new Material(InventoryMenu.BLOCK_ATLAS, visual.texture()).sprite())
                : Blitter.texture(visual.texture(), 16, 16).src(0, 0, 16, 16);
        blitter.blending(visual.blending()).colorArgb(visual.color()).dest(x, y, 16, 16).blit(graphics);
    }

    @Override
    public void drawOnBlockFace(PoseStack poses, MultiBufferSource buffers, MagicResourceKey key,
                                float scale, int light, Level level) {
        Visual visual = visual(key);
        if (visual == null) {
            AEKeyRendering.drawOnBlockFace(poses, buffers, extensionIcon(key), scale, light, level);
            return;
        }
        float size = faceSize(scale);
        if (size <= 0) return;
        float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        RenderType layer;
        if (visual.atlas()) {
            var sprite = new Material(InventoryMenu.BLOCK_ATLAS, visual.texture()).sprite();
            u0 = sprite.getU0(); u1 = sprite.getU1(); v0 = sprite.getV0(); v1 = sprite.getV1();
            layer = RenderType.solid();
        } else {
            layer = RenderType.entityTranslucent(visual.texture());
        }
        emitFace(poses, buffers.getBuffer(layer), size, u0, u1, v0, v1, visual.color(), light);
    }

    static float faceSize(float scale) {
        return Float.isFinite(scale) ? Math.max(0, scale - 0.05F) : 0;
    }

    /** Same centered inset quad as the source disk; a pure vertex boundary allows headless verification. */
    static void emitFace(PoseStack poses, VertexConsumer vertices, float size,
                         float u0, float u1, float v0, float v1, int color, int light) {
        if (!(size > 0) || !Float.isFinite(size)) return;
        poses.pushPose();
        try {
            poses.translate(0, 0, 0.01F);
            var pose = poses.last();
            float half = size / 2;
            vertex(vertices, pose, -half, -half, u0, v1, color, light);
            vertex(vertices, pose, half, -half, u1, v1, color, light);
            vertex(vertices, pose, half, half, u1, v0, color, light);
            vertex(vertices, pose, -half, half, u0, v0, color, light);
        } finally {
            poses.popPose();
        }
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               float x, float y, float u, float v, int color, int light) {
        vertices.addVertex(pose.pose(), x, y, 0).setColor(color).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, 1);
    }

    private static AEItemKey extensionIcon(MagicResourceKey key) {
        // Dormant essence balances remain readable when F&A is absent; its
        // native item icon cannot exist in that registry, so render a generic
        // magic crystal without changing the stored key or amount.
        var item = BuiltInRegistries.ITEM.getOptional(MagicResourceKeys.icon(key.resource()))
                .filter(found -> found != Items.AIR).orElse(Items.AMETHYST_SHARD);
        return AEItemKey.of(item);
    }

    @Override public Component getDisplayName(MagicResourceKey key) { return key.getDisplayName(); }
}
