package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Non-consumable module that replaces recipe Source cost with extra FE usage.
 */
public final class SourceConversionModuleItem extends Item {
    public SourceConversionModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(
                "item.mekanism_magic.source_conversion_module.tooltip"));
    }
}
