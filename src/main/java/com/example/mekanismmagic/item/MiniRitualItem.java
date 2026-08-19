package com.example.mekanismmagic.item;

import com.example.mekanismmagic.blockentity.OccultismRecipeBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * A compact ritual selector. Set the ritual recipe id with custom data, for
 * example: /give @s mekanism_magic:mini_ritual[minecraft:custom_data={ritual:"occultism:craft_soul_gem"}]
 */
public final class MiniRitualItem extends Item {
    public MiniRitualItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack miniature = player.getItemInHand(hand);
        ItemStack reference = player.getItemInHand(
                hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);

        if (OccultismRecipeBridge.isRitualSelector(reference)) {
            if (!level.isClientSide()) {
                OccultismRecipeBridge.findProjection(level, reference).ifPresent(projection -> {
                    OccultismRecipeBridge.bindMiniRitual(miniature, projection);
                    player.displayClientMessage(Component.translatable(
                            "item.mekanism_magic.mini_ritual.bound",
                            OccultismRecipeBridge.pentacleDisplayName(
                                    projection.pentacleId())), true);
                });
            }
            return InteractionResultHolder.sidedSuccess(miniature, level.isClientSide());
        }

        ResourceLocation ritualId = ritualId(miniature);
        ResourceLocation pentacleId = pentacleId(miniature);
        if (level.isClientSide() && ritualId != null) {
            OccultismRecipeBridge.findProjection(level, ritualId).ifPresent(projection ->
                    showProjection(projection.multiblock(),
                            OccultismRecipeBridge.pentacleDisplayName(
                                    projection.pentacleId())));
            return InteractionResultHolder.success(miniature);
        }
        if (level.isClientSide() && ritualId == null && pentacleId != null) {
            OccultismRecipeBridge.findProjectionByPentacle(level, pentacleId).ifPresent(projection ->
                    showProjection(projection.multiblock(),
                            OccultismRecipeBridge.pentacleDisplayName(
                                    projection.pentacleId())));
            return InteractionResultHolder.success(miniature);
        }
        return InteractionResultHolder.pass(miniature);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        net.minecraft.nbt.CompoundTag data = stack.getTag();
        if (data != null && (data.contains("ritual")
                || data.contains("pentacle"))) {
            if (data.contains("ritual")) {
                tooltip.add(Component.literal(data.getString("ritual")));
            } else {
                tooltip.add(Component.translatable(
                        "item.mekanism_magic.mini_ritual.pentacle_only"));
            }
            if (data.contains("pentacle")) {
                tooltip.add(Component.translatable("item.mekanism_magic.mini_ritual.pentacle",
                        OccultismRecipeBridge.pentacleDisplayName(
                                pentacleId(stack))));
            }
            tooltip.add(Component.translatable("item.mekanism_magic.mini_ritual.project"));
        } else {
            tooltip.add(Component.translatable("item.mekanism_magic.mini_ritual.unconfigured"));
            tooltip.add(Component.translatable("item.mekanism_magic.mini_ritual.bind"));
        }
    }

    private static ResourceLocation ritualId(ItemStack stack) {
        net.minecraft.nbt.CompoundTag data = stack.getTag();
        if (data == null) {
            return null;
        }
        return ResourceLocation.tryParse(data.getString("ritual"));
    }

    private static ResourceLocation pentacleId(ItemStack stack) {
        net.minecraft.nbt.CompoundTag data = stack.getTag();
        if (data == null) {
            return null;
        }
        return ResourceLocation.tryParse(data.getString("pentacle"));
    }

    private static void showProjection(Object multiblock, Component name) {
        try {
            Class<?> multiblockClass =
                    Class.forName("com.klikli_dev.modonomicon.api.multiblock.Multiblock");
            Class<?> rendererClass =
                    Class.forName("com.klikli_dev.modonomicon.client.render.MultiblockPreviewRenderer");
            Method setMultiblock = rendererClass.getMethod(
                    "setMultiblock", multiblockClass, Component.class, boolean.class);
            setMultiblock.invoke(null, multiblock, name, false);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Modonomicon is an Occultism dependency, but reflection keeps the
            // common item class safe on a dedicated server.
        }
    }
}
