package com.example.mekanismmagic.item;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.MiniPentacleDeployment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

        if (OccultismRecipeBridge.isRitualProjectionItem(reference)) {
            if (MiniPentacleDeployment.isDeployed(miniature)) {
                if (!level.isClientSide()) {
                    player.displayClientMessage(Component.translatable(
                            MiniPentacleDeployment.Status.ALREADY_DEPLOYED
                                    .translationKey()), true);
                }
                return InteractionResultHolder.fail(miniature);
            }
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
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack miniature = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            return isConfigured(miniature)
                    || MiniPentacleDeployment.isDeployed(miniature)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        MiniPentacleDeployment.Result result;
        if (player.isShiftKeyDown()) {
            result = MiniPentacleDeployment.recover(level, serverPlayer,
                    miniature, context.getClickedPos());
        } else if (context.getClickedFace()
                != net.minecraft.core.Direction.UP) {
            result = new MiniPentacleDeployment.Result(
                    MiniPentacleDeployment.Status.TOP_FACE_REQUIRED, 0);
        } else {
            result = MiniPentacleDeployment.deploy(level, serverPlayer,
                    miniature,
                    context.getClickedPos().relative(
                            context.getClickedFace()),
                    MiniPentacleDeployment.rotationFor(
                            player.getDirection()));
        }
        Component message = result.success()
                ? Component.translatable(
                result.status().translationKey(),
                result.changedBlocks())
                : Component.translatable(
                result.status().translationKey());
        player.displayClientMessage(message, true);
        return result.success()
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        var tag = data == null ? null : data.getUnsafe();
        if (data != null && !data.isEmpty()
                && (tag.contains("ritual") || tag.contains("pentacle"))) {
            if (tag.contains("ritual")) {
                tooltip.add(Component.literal(tag.getString("ritual")));
            } else {
                tooltip.add(Component.translatable(
                        "item.mekanism_magic.mini_ritual.pentacle_only"));
            }
            if (tag.contains("pentacle")) {
                tooltip.add(Component.translatable("item.mekanism_magic.mini_ritual.pentacle",
                        OccultismRecipeBridge.pentacleDisplayName(
                                pentacleId(stack))));
            }
            if (MiniPentacleDeployment.isDeployed(stack)) {
                tooltip.add(Component.translatable(
                        "item.mekanism_magic.mini_ritual.deployed"));
                MiniPentacleDeployment.location(stack).ifPresent(location ->
                        tooltip.add(Component.translatable(
                                "item.mekanism_magic.mini_ritual.deployed_at",
                                location.dimension(),
                                location.anchor().getX(),
                                location.anchor().getY(),
                                location.anchor().getZ())));
                tooltip.add(Component.translatable(
                        "item.mekanism_magic.mini_ritual.recover"));
            } else {
                tooltip.add(Component.translatable(
                        "item.mekanism_magic.mini_ritual.project"));
                tooltip.add(Component.translatable(
                        "item.mekanism_magic.mini_ritual.place"));
            }
        } else {
            tooltip.add(Component.translatable("item.mekanism_magic.mini_ritual.unconfigured"));
            tooltip.add(Component.translatable("item.mekanism_magic.mini_ritual.bind"));
        }
    }

    private static ResourceLocation ritualId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(data.getUnsafe().getString("ritual"));
    }

    private static boolean isConfigured(ItemStack stack) {
        return ritualId(stack) != null || pentacleId(stack) != null;
    }

    private static ResourceLocation pentacleId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(data.getUnsafe().getString("pentacle"));
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
