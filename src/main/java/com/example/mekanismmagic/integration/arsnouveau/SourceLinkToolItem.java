package com.example.mekanismmagic.integration.arsnouveau;

import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import com.hollingsworth.arsnouveau.setup.registry.SoundRegistry;
import mekanism.common.lib.security.BlockSecurityUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * A focused two-click alternative to the Dominion Wand for Source relays.
 * Its block-interaction event hook prevents machine GUIs from consuming the
 * second click before the item gets a chance to create the connection.
 */
public final class SourceLinkToolItem extends Item {
    private static final String SELECTED_DIMENSION = "source_link_dimension";
    private static final String SELECTED_POSITION = "source_link_position";
    private static final String SELECTED_FACE = "source_link_face";

    public SourceLinkToolItem(Properties properties) {
        super(properties);
    }

    /** Always give the tool priority over a machine's normal GUI action. */
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem()
                instanceof SourceLinkToolItem tool)) {
            return;
        }
        UseOnContext context = new UseOnContext(event.getEntity(),
                event.getHand(), event.getHitVec());
        InteractionResult result = tool.handleUseOn(context);
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return handleUseOn(context);
    }

    private InteractionResult handleUseOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack tool = context.getItemInHand();
        BlockPos clickedPosition = context.getClickedPos();
        Selection selected = selection(tool);
        if (player.isShiftKeyDown()) {
            if (selected != null) {
                clearSelection(tool);
                notify(player, "item.mekanism_magic.source_link_tool.cancelled");
            } else if (findSourceLinkHost(level, clickedPosition)
                    instanceof SourceLinkHost host
                    && canAccessHost(player, level, clickedPosition)
                    && host.mekanismMagicClearSourceJarLinks() > 0) {
                notify(player, "item.mekanism_magic.source_link_tool."
                        + "source_links_cleared");
            } else if (findWandable(level, clickedPosition)
                    instanceof IWandable wandable) {
                wandable.onClearConnections(player);
                notify(player, "item.mekanism_magic.source_link_tool."
                        + "connections_cleared");
            } else {
                notify(player,
                        "item.mekanism_magic.source_link_tool.nothing_to_clear");
            }
            play(level, clickedPosition,
                    SoundRegistry.DOMINION_WAND_CLEAR.get());
            return InteractionResult.CONSUME;
        }

        if (selected == null) {
            storeSelection(tool, level, clickedPosition,
                    context.getClickedFace());
            player.displayClientMessage(Component.translatable(
                    "item.mekanism_magic.source_link_tool.selected",
                    endpointName(level, clickedPosition),
                    positionText(clickedPosition)).withStyle(
                    ChatFormatting.AQUA), true);
            play(level, clickedPosition,
                    SoundRegistry.DOMINION_WAND_SELECT.get());
            return InteractionResult.CONSUME;
        }

        if (!selected.dimension().equals(level.dimension().location())) {
            clearSelection(tool);
            notify(player,
                    "item.mekanism_magic.source_link_tool.wrong_dimension");
            play(level, clickedPosition,
                    SoundRegistry.DOMINION_WAND_FAIL.get());
            return InteractionResult.CONSUME;
        }
        if (selected.position().equals(clickedPosition)) {
            clearSelection(tool);
            notify(player, "item.mekanism_magic.source_link_tool.cancelled");
            play(level, clickedPosition,
                    SoundRegistry.DOMINION_WAND_CLEAR.get());
            return InteractionResult.CONSUME;
        }
        LinkResult result = connect(level, selected.position(),
                selected.face(), clickedPosition, player);
        if (result == LinkResult.SUCCESS) {
            clearSelection(tool);
            player.displayClientMessage(Component.translatable(
                    "item.mekanism_magic.source_link_tool.linked",
                    positionText(selected.position()),
                    positionText(clickedPosition)).withStyle(
                    ChatFormatting.GREEN), true);
            play(level, clickedPosition,
                    SoundRegistry.DOMINION_WAND_SUCCESS.get());
        } else {
            notify(player, result.translationKey);
            play(level, clickedPosition,
                    SoundRegistry.DOMINION_WAND_FAIL.get());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                   InteractionHand hand) {
        ItemStack tool = player.getItemInHand(hand);
        if (!player.isShiftKeyDown() || selection(tool) == null) {
            return InteractionResultHolder.pass(tool);
        }
        if (!level.isClientSide()) {
            clearSelection(tool);
            notify(player, "item.mekanism_magic.source_link_tool.cancelled");
            play(level, player.blockPosition(),
                    SoundRegistry.DOMINION_WAND_CLEAR.get());
        }
        return InteractionResultHolder.sidedSuccess(tool,
                level.isClientSide());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return selection(stack) != null || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        Selection selected = selection(stack);
        if (selected == null) {
            tooltip.add(Component.translatable(
                    "item.mekanism_magic.source_link_tool.first_click")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(
                    "item.mekanism_magic.source_link_tool.selected_tooltip",
                    positionText(selected.position()))
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable(
                    "item.mekanism_magic.source_link_tool.second_click")
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(
                "item.mekanism_magic.source_link_tool.clear_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static LinkResult connect(Level level, BlockPos first,
                                      Direction firstFace, BlockPos second,
                                      Player player) {
        SourceLinkHost firstHost = findSourceLinkHost(level, first);
        SourceLinkHost secondHost = findSourceLinkHost(level, second);
        boolean firstJar = isSourceEndpoint(level, first);
        boolean secondJar = isSourceEndpoint(level, second);
        if (firstHost != null && secondJar) {
            if (!canAccessHost(player, level, first)) {
                return LinkResult.REJECTED;
            }
            return firstHost.mekanismMagicLinkSourceJar(second)
                    ? LinkResult.SUCCESS : LinkResult.REJECTED;
        }
        if (secondHost != null && firstJar) {
            if (!canAccessHost(player, level, second)) {
                return LinkResult.REJECTED;
            }
            return secondHost.mekanismMagicLinkSourceJar(first)
                    ? LinkResult.SUCCESS : LinkResult.REJECTED;
        }

        IWandable firstWandable = findWandable(level, first);
        IWandable secondWandable = findWandable(level, second);
        if (firstWandable == null && secondWandable == null) {
            return LinkResult.UNSUPPORTED;
        }

        GlobalPos firstPosition = new GlobalPos(level.dimension(), first);
        GlobalPos secondPosition = new GlobalPos(level.dimension(), second);
        boolean accepted = false;
        if (firstWandable != null) {
            accepted = firstWandable.onFirstConnection(secondPosition,
                    firstFace, null, player) != IWandable.Result.FAIL;
        }
        if (secondWandable != null) {
            accepted |= secondWandable.onLastConnection(firstPosition,
                    firstFace, null, player) != IWandable.Result.FAIL;
        }
        return accepted ? LinkResult.SUCCESS : LinkResult.REJECTED;
    }

    private static SourceLinkHost findSourceLinkHost(
            Level level, BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity instanceof SourceLinkHost host ? host : null;
    }

    private static boolean isSourceEndpoint(Level level, BlockPos position) {
        return !(level.getBlockEntity(position) instanceof SourceLinkHost)
                && SourceLinkState.isSourceEndpoint(level, position);
    }

    private static boolean canAccessHost(Player player, Level level,
                                         BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        return BlockSecurityUtils.get().canAccess(player, level, position,
                level.getBlockState(position), blockEntity);
    }

    private static IWandable findWandable(Level level,
                                          BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity instanceof IWandable wandable) {
            return wandable;
        }
        return level.getCapability(
                CapabilityRegistry.WANDABLE_BLOCK_CAPABILITY,
                position, null);
    }

    private static Component endpointName(Level level, BlockPos position) {
        return level.getBlockState(position).getBlock().getName();
    }

    private static Component positionText(BlockPos position) {
        return Component.literal(position.getX() + ", " + position.getY()
                + ", " + position.getZ());
    }

    private static void storeSelection(ItemStack stack, Level level,
                                       BlockPos position, Direction face) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString(SELECTED_DIMENSION,
                    level.dimension().location().toString());
            tag.putLong(SELECTED_POSITION, position.asLong());
            tag.putInt(SELECTED_FACE, face.get3DDataValue());
        });
    }

    private static Selection selection(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        var tag = data.copyTag();
        if (!tag.contains(SELECTED_DIMENSION)
                || !tag.contains(SELECTED_POSITION)) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(
                tag.getString(SELECTED_DIMENSION));
        Direction face = tag.contains(SELECTED_FACE)
                ? Direction.from3DDataValue(tag.getInt(SELECTED_FACE))
                : null;
        return dimension == null ? null : new Selection(dimension,
                BlockPos.of(tag.getLong(SELECTED_POSITION)), face);
    }

    private static void clearSelection(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(SELECTED_DIMENSION);
            tag.remove(SELECTED_POSITION);
            tag.remove(SELECTED_FACE);
        });
    }

    private static void notify(Player player, String translationKey) {
        player.displayClientMessage(Component.translatable(translationKey),
                true);
    }

    private static void play(Level level, BlockPos position,
                             SoundEvent sound) {
        level.playSound(null, position, sound, SoundSource.PLAYERS,
                1.0F, 1.0F);
    }

    private record Selection(ResourceLocation dimension, BlockPos position,
                             Direction face) {
    }

    private enum LinkResult {
        SUCCESS(""),
        UNSUPPORTED(
                "item.mekanism_magic.source_link_tool.unsupported"),
        REJECTED(
                "item.mekanism_magic.source_link_tool.rejected");

        private final String translationKey;

        LinkResult(String translationKey) {
            this.translationKey = translationKey;
        }
    }
}
