package com.example.mekanismmagic.integration.mekenergistics;

import com.beipuo.mekenergistics.api.upgrade.MePatternAutomation;
import com.example.mekanismmagic.MekanismMagic;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional 3.0.6+ Mek Energistics bridge. This class is only compiled when
 * the supplied Mek Energistics API is available.
 */
public final class MekEnergisticsCompat {
    private MekEnergisticsCompat() {
    }

    public static void registerBlocks() {
        String[] paths = {
                "source_generator",
                "imbuement_processor",
                "enchanting_apparatus_processor",
                "spirit_processor",
                "ritual_engine",
                "mini_ritual_assembler",
                "basic_spirit_factory",
                "advanced_spirit_factory",
                "elite_spirit_factory",
                "ultimate_spirit_factory",
                "absolute_spirit_factory",
                "supreme_spirit_factory",
                "cosmic_spirit_factory",
                "infinite_spirit_factory"
        };
        for (String path : paths) {
            MePatternAutomation.registerBlock(
                    ResourceLocation.fromNamespaceAndPath(
                            MekanismMagic.MOD_ID, path));
        }
    }
}
