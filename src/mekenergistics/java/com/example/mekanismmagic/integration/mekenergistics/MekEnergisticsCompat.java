package com.example.mekanismmagic.integration.mekenergistics;

import com.beipuo.mekenergistics.api.upgrade.MePatternAutomation;
import com.example.mekanismmagic.MekanismMagic;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional 3.0.6+ Mek Energistics bridge. This class is only compiled when
 * the supplied Mek Energistics API is available.
 */
public final class MekEnergisticsCompat {
    private static final String[] SAFETY_GATE_CLASSES = {
            "com.beipuo.mekenergistics.item.MeTierInstallerItem",
            "com.beipuo.mekenergistics.item.MeInstallerUpgradeHandler",
            "com.beipuo.mekenergistics.item.MeInstallerTargetResolver"
    };

    private MekEnergisticsCompat() {
    }

    public static void registerBlocks() {
        verifySafetyGateClasses();
        String[] paths = {
                "imbuement_processor",
                "basic_imbuement_factory",
                "advanced_imbuement_factory",
                "elite_imbuement_factory",
                "ultimate_imbuement_factory",
                "absolute_imbuement_factory",
                "supreme_imbuement_factory",
                "cosmic_imbuement_factory",
                "infinite_imbuement_factory",
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
        MekanismMagic.LOGGER.info(
                "Registered {} Mekanism Magic machines for in-place ME "
                        + "upgrades; replacement safety gates verified",
                paths.length);
    }

    /**
     * Force the three replacement entry points to transform during startup.
     * With defaultRequire=1 this turns a future incompatible Mek Energistics
     * ABI into an immediate, visible compatibility failure instead of leaving
     * a dormant unsafe path that can replace a player's machine later.
     */
    private static void verifySafetyGateClasses() {
        ClassLoader loader = MekEnergisticsCompat.class.getClassLoader();
        for (String className : SAFETY_GATE_CLASSES) {
            try {
                Class.forName(className, true, loader);
            } catch (ClassNotFoundException exception) {
                throw new IllegalStateException(
                        "Missing required Mek Energistics safety gate: "
                                + className, exception);
            }
        }
    }
}
