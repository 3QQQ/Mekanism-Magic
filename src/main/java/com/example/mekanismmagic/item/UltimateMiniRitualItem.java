package com.example.mekanismmagic.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Universal ritual selector. Supported Occultism 1.21.1 releases have no
 * duplicate ritual input signatures, so ingredient selection is deterministic.
 */
public final class UltimateMiniRitualItem extends Item {
    public UltimateMiniRitualItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable(
                "item.mekanism_magic.ultimate_mini_ritual.tooltip"));
    }
}
