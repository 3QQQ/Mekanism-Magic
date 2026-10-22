package com.example.mekanismmagic.integration.naturesaura;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Virtual JEI/AE marker carrying one natural altar recipe identity.
 *
 * <p>It has no recipe and is never a physical machine ingredient. AE keeps
 * it as pattern context while the real catalyst remains installed in the
 * machine and is not consumed.</p>
 */
public final class NatureAltarContextItem extends Item {
    public static final String RECIPE_KEY = "nature_altar_recipe";
    public static final String CATALOG_VERSION_KEY =
            "nature_altar_catalog_version";
    public static final String RECIPE_SIGNATURE_KEY =
            "nature_altar_recipe_signature";
    public static final String CATALYST_ITEM_KEY =
            "nature_altar_catalyst_item";

    public NatureAltarContextItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Item catalyst = catalystItem(stack);
        return catalyst == Items.AIR
                ? super.getName(stack)
                : Component.translatable(
                        "item.mekanism_magic.nature_altar_context.bound",
                        catalyst.getDescription());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ResourceLocation recipe = recipeId(stack);
        if (recipe == null) {
            tooltip.add(Component.translatable(
                    "item.mekanism_magic.nature_altar_context.unbound"));
            return;
        }
        tooltip.add(Component.translatable(
                "item.mekanism_magic.nature_altar_context.recipe",
                recipe.toString()));
        tooltip.add(Component.translatable(
                "item.mekanism_magic.nature_altar_context.virtual"));
    }

    public static ResourceLocation recipeId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return null;
        }
        String value = data.getUnsafe().getString(RECIPE_KEY);
        if (value.isBlank()) {
            return null;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null || parsed.getPath().isBlank()
                ? null : parsed;
    }

    private static Item catalystItem(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return Items.AIR;
        }
        String value = data.getUnsafe().getString(CATALYST_ITEM_KEY);
        ResourceLocation id = value.isBlank() ? null
                : ResourceLocation.tryParse(value);
        if (id != null && id.getPath().isBlank()) {
            id = null;
        }
        return id == null ? Items.AIR
                : BuiltInRegistries.ITEM.get(id);
    }
}
