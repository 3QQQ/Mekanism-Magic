package com.example.mekanismmagic.integration.arsnouveau;

import java.util.List;
import mekanism.common.util.text.TextUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** Item form of a Source pipe with Mekanism-style visible tier statistics. */
public final class MagicSourcePipeItem extends BlockItem {
    private final MagicSourcePipeTier tier;

    public MagicSourcePipeItem(MagicSourcePipeBlock block,
                               MagicSourcePipeTier tier,
                               Item.Properties properties) {
        super(block, properties);
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(stat("tooltip.mekanism_magic.magic_source_pipe.capacity",
                tier.capacity()));
        tooltip.add(stat(
                "tooltip.mekanism_magic.magic_source_pipe.pull_rate",
                tier.pullRate()));
        tooltip.add(Component.translatable(
                        "tooltip.mekanism_magic.magic_source_pipe.transfers")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Component stat(String key, int value) {
        Component amount = Component.literal(TextUtils.format(value))
                .withStyle(ChatFormatting.GRAY);
        return Component.translatable(key, amount)
                .withStyle(ChatFormatting.DARK_AQUA);
    }
}
