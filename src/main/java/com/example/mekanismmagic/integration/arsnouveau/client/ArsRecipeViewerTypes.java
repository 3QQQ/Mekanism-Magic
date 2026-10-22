package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.client.MagicRecipeViewerType;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ArsApparatusJeiData;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ArsRecipeViewerTypes {
    public static final IRecipeViewerRecipeType<
            ArsNouveauRecipeBridge.CatalystIdentifierJeiData>
            CATALYST_IDENTIFIER = new MagicRecipeViewerType<>(
            ResourceLocation.fromNamespaceAndPath(
                    MekanismMagic.MOD_ID, "catalyst_identifier"),
            Component.translatable(
                    "jei.mekanism_magic.catalyst_identifier"),
            ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_BLOCK,
            ArsNouveauRecipeBridge.CatalystIdentifierJeiData.class,
            false, 126, 72,
            ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_BLOCK);

    public static final IRecipeViewerRecipeType<
            ArsNouveauRecipeBridge.IdentifierImbuementJeiData>
            IDENTIFIER_IMBUEMENT = new MagicRecipeViewerType<>(
            ResourceLocation.fromNamespaceAndPath(
                    MekanismMagic.MOD_ID, "identifier_imbuement"),
            Component.translatable(
                    "jei.mekanism_magic.identifier_imbuement"),
            ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK,
            ArsNouveauRecipeBridge.IdentifierImbuementJeiData.class,
            false, 150, 72,
            ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK);

    public static final IRecipeViewerRecipeType<ArsApparatusJeiData>
            ENCHANTING_APPARATUS = new MagicRecipeViewerType<>(
            ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID,
                    "apparatus"),
            Component.translatable("jei.mekanism_magic.apparatus"),
            ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK,
            ArsApparatusJeiData.class, false, 180, 110,
            ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK);

    private ArsRecipeViewerTypes() {
    }

}
