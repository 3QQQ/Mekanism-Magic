package com.example.mekanismmagic.integration.mekenergistics;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Keeps the optional Mek Energistics bridge safe in release jars. The bridge
 * classes are compiled when the API jar is available, but are only applied
 * when the actual mod is present at runtime.
 */
public final class MekEnergisticsCompatMixinPlugin
        implements IMixinConfigPlugin {
    private static final String[] REQUIRED_ABI_CLASSES = {
            "com.beipuo.mekenergistics.api.upgrade."
                    + "IMePatternAutomationHost",
            "com.beipuo.mekenergistics.api.upgrade.MePatternAutomation",
            "com.beipuo.mekenergistics.blockentity.support."
                    + "AbstractMeAeSupport",
            "com.beipuo.mekenergistics.blockentity.support."
                    + "MeSmartPatternMultiplication",
            "com.beipuo.mekenergistics.common.machine.MeMekanismMachine",
            "com.beipuo.mekenergistics.item.MeTierInstallerItem",
            "com.beipuo.mekenergistics.item.MeInstallerUpgradeHandler",
            "com.beipuo.mekenergistics.item.MeInstallerTargetResolver",
            "com.beipuo.mekenergistics.upgrade."
                    + "MePatternAutomationProfiles",
            "com.beipuo.mekenergistics.upgrade.MeUpgradeMachineProfile",
            "com.beipuo.mekenergistics.upgrade."
                    + "MeUpgradeRecipeMachineAdapter",
            "com.beipuo.mekenergistics.upgrade.MeUpgradeType"
    };

    private static boolean loaded() {
        // Mixin plugins run before the normal FML ModList is ready. Querying
        // ModList here made every compatibility mixin silently opt out even
        // with Mek Energistics 3.0.6 installed. MixinService is the loader's
        // authoritative early resource view; this list is the exact ABI
        // surface referenced by the optional bridge mixins.
        for (String className : REQUIRED_ABI_CLASSES) {
            if (!classPresent(className)) {
                return false;
            }
        }
        return true;
    }

    private static boolean classPresent(String className) {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream stream = MixinService.getService()
                .getResourceAsStream(resource)) {
            return stream != null;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName,
                                    String mixinClassName) {
        return loaded();
    }

    @Override
    public void acceptTargets(Set<String> myTargets,
                              Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass,
                         String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass,
                          String mixinClassName, IMixinInfo mixinInfo) {
    }
}
