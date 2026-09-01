package com.example.mekanismmagic.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class OccultismRecipeViewerTypes {
    public static final IRecipeViewerRecipeType<
            OccultismRecipeBridge.SpiritJeiData> SPIRIT = type(
            "spirit", "jei.mekanism_magic.spirit",
            NativeMekanismRegistries.SPIRIT_BLOCK,
            OccultismRecipeBridge.SpiritJeiData.class, 176, 70);
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
