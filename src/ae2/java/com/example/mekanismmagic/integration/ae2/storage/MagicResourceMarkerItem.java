package com.example.mekanismmagic.integration.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.items.misc.WrappedGenericStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

/** A reusable, zero-amount filter selector. It never represents owned energy. */
public final class MagicResourceMarkerItem extends WrappedGenericStack {
    public MagicResourceMarkerItem(Properties properties) { super(properties); }

    public static ResourceLocation selectedResource(ItemStack stack) {
        CompoundTag data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ResourceLocation saved = ResourceLocation.tryParse(data.getString("magic_resource"));
        if (saved != null) return saved;
        return MagicResourceKeys.key(MagicResourceKeys.SOURCE_ID) != null ? MagicResourceKeys.SOURCE_ID
                : MagicResourceKeys.resources().stream().findFirst().orElse(MagicResourceKeys.SOURCE_ID);
    }

    public static void select(ItemStack stack, ResourceLocation resource) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack,
                tag -> tag.putString("magic_resource", resource.toString()));
    }

    @Override public AEKey unwrapWhat(ItemStack stack) { return MagicResourceKeys.key(selectedResource(stack)); }
    @Override public long unwrapAmount(ItemStack stack) { return 0L; }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        List<ResourceLocation> resources = MagicResourceKeys.resources();
        if (!resources.isEmpty() && !level.isClientSide) {
            int index = resources.indexOf(selectedResource(stack));
            int direction = player.isShiftKeyDown() ? -1 : 1;
            ResourceLocation selected = resources.get(Math.floorMod(index + direction, resources.size()));
            select(stack, selected);
            player.displayClientMessage(Component.translatable("tooltip.mekanism_magic.magic_resource_marker.selected",
                    MagicResourceKeys.displayName(selected)), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack carried, Slot slot,
            ClickAction click, Player player, SlotAccess access) {
        // The native wrapped-stack handler can fill carried containers. A selector must never do so.
        return false;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                         TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.mekanism_magic.magic_resource_marker.selected",
                MagicResourceKeys.displayName(selectedResource(stack))));
        tooltip.add(Component.translatable("tooltip.mekanism_magic.magic_resource_marker.usage"));
        tooltip.add(Component.translatable("tooltip.mekanism_magic.magic_resource_marker.config"));
        tooltip.add(Component.translatable("tooltip.mekanism_magic.magic_resource_marker.empty"));
    }
}
