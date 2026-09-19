package com.example.mekanismmagic.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.SpiritRecipeGroup;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class OccultismRecipeViewerTypes {
    private static final java.util.Map<SpiritRecipeGroup, IRecipeViewerRecipeType<
            OccultismRecipeBridge.SpiritJeiData>> SPIRIT_CATEGORIES = createSpiritCategories();
    public static final IRecipeViewerRecipeType<
            OccultismRecipeBridge.SpiritJeiData> SPIRIT = SPIRIT_CATEGORIES.get(SpiritRecipeGroup.SPIRIT_FIRE);
    public static final IRecipeViewerRecipeType<
            OccultismRecipeBridge.RitualJeiData> RITUAL = type(
            "ritual", "jei.mekanism_magic.ritual",
            NativeMekanismRegistries.RITUAL_BLOCK,
            OccultismRecipeBridge.RitualJeiData.class, 176, 110);
    public static final IRecipeViewerRecipeType<
            OccultismRecipeBridge.PentacleJeiData> MINI_RITUAL = type(
            "mini_ritual", "jei.mekanism_magic.mini_ritual",
            NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK,
            OccultismRecipeBridge.PentacleJeiData.class, 176, 84);
    public static final IRecipeViewerRecipeType<
            OccultismRecipeBridge.MinerJeiData> MINER = type(
            "miner", "jei.mekanism_magic.miner",
            NativeMekanismRegistries.DIMENSION_MINER_BLOCK,
            OccultismRecipeBridge.MinerJeiData.class, 176, 70);

    private OccultismRecipeViewerTypes() {
    }

    public static IRecipeViewerRecipeType<?>[] spiritCategories() {
        return SPIRIT_CATEGORIES.values().toArray(IRecipeViewerRecipeType[]::new);
    }

    private static java.util.Map<SpiritRecipeGroup, IRecipeViewerRecipeType<
            OccultismRecipeBridge.SpiritJeiData>> createSpiritCategories() {
        var categories = new java.util.EnumMap<SpiritRecipeGroup, IRecipeViewerRecipeType<
                OccultismRecipeBridge.SpiritJeiData>>(SpiritRecipeGroup.class);
        for (var group : SpiritRecipeGroup.values()) {
            categories.put(group, new MagicRecipeViewerType<>(group.id(),
                    Component.translatable(group.titleKey()), BuiltInRegistries.ITEM.get(group.iconId()),
                    OccultismRecipeBridge.SpiritJeiData.class, false, 176, 70,
                    NativeMekanismRegistries.SPIRIT_BLOCK));
        }
        return java.util.Collections.unmodifiableMap(categories);
    }

    private static <RECIPE> IRecipeViewerRecipeType<RECIPE> type(
            String path, String translationKey,
            net.minecraft.world.level.ItemLike machine,
            Class<? extends RECIPE> recipeClass,
            int width, int height) {
        return new MagicRecipeViewerType<>(
                ResourceLocation.fromNamespaceAndPath(
                        MekanismMagic.MOD_ID, path),
                Component.translatable(translationKey), machine,
                recipeClass, false, width, height, machine);
    }
}
