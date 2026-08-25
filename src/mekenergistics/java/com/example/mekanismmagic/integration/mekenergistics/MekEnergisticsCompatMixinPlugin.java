package com.example.mekanismmagic.integration.mekenergistics;

import com.example.mekanismmagic.integration.ModCompatibility;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Keeps the optional Mek Energistics bridge safe in release jars. The bridge
 * classes are compiled when the API jar is available, but are only applied
 * when the actual mod is present at runtime.
 */
public final class MekEnergisticsCompatMixinPlugin
        implements IMixinConfigPlugin {
    private static final String API_CLASS =
            "com.beipuo.mekenergistics.api.upgrade.IMePatternAutomationHost";

    private static boolean loaded() {
        String resource = API_CLASS.replace('.', '/') + ".class";
        if (MekEnergisticsCompatMixinPlugin.class.getClassLoader()
                .getResource(resource) == null) {
            return false;
        }
        try {
            return ModCompatibility.mekenergisticsAutomationSupported();
        } catch (Throwable ignored) {
            // A partially initialized loader must never make the optional
            // integration prevent the game from starting.
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
