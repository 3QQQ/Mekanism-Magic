package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public final class CatalystIdentifierItem extends Item {
    public CatalystIdentifierItem(Properties properties) {
        super(properties);
    }

    public static ResourceLocation catalystId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "catalyst/unknown");
        }
        ResourceLocation id = ResourceLocation.tryParse(
                data.copyTag().getString("catalyst_id"));
        return id == null ? ResourceLocation.fromNamespaceAndPath(
                "mekanism_magic", "catalyst/unknown") : id;
    }

    public static boolean matchesRecipe(ItemStack stack, ResourceLocation id) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return false;
        }
        var recipes = data.copyTag().getList("recipes", 8);
        for (int index = 0; index < recipes.size(); index++) {
            if (id.toString().equals(recipes.getString(index))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            tooltip.add(Component.translatable(
                    "item.mekanism_magic.catalyst_identifier.unbound"));
            return;
        }
        var tag = data.copyTag();
        tooltip.add(Component.translatable(
                "item.mekanism_magic.catalyst_identifier.id",
                tag.getString("catalyst_id")));
        if (tag.contains("recipes")) {
            tooltip.add(Component.translatable(
                    "item.mekanism_magic.catalyst_identifier.recipes",
                    tag.getList("recipes", 8).size()));
        }
    }
}
