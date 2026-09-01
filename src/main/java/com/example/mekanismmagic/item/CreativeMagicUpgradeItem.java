package com.example.mekanismmagic.item;

import com.example.mekanismmagic.upgrade.MagicUpgrades;
import java.util.List;
import mekanism.common.item.ItemUpgrade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;

/**
 * Magic-resource plugin installed by Mekanism's native upgrade component.
 * Its upgrade type remains independent from Mekanism Extras' creative type.
 */
public final class CreativeMagicUpgradeItem extends ItemUpgrade {
    public CreativeMagicUpgradeItem(Properties properties) {
        super(MagicUpgrades.creativeMagic(), properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack,
                                @NotNull Item.TooltipContext context,
                                @NotNull List<Component> tooltip,
                                @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(
                        "item.mekanism_magic.creative_source_upgrade.effect")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(
                        "item.mekanism_magic.creative_source_upgrade.restriction")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
