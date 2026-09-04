package com.example.mekanismmagic.integration.ae2;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * Prevents the identifier-pattern mixin from resolving unless both AE2 and
 * the Ars recipe API referenced by its decoder are present.
 */
public final class Ae2CompatMixinPlugin implements IMixinConfigPlugin {
    private static boolean aePresent() {
        return resourcePresent(
                "appeng/crafting/pattern/AEPatternDecoder.class");
    }

    private static boolean arsPresent() {
        return resourcePresent(
                "com/hollingsworth/arsnouveau/common/crafting/recipes/"
                        + "ImbuementRecipe.class");
    }

    private static boolean resourcePresent(String path) {
        try (InputStream stream = MixinService.getService()
                .getResourceAsStream(path)) {
            return stream != null;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(
            String targetClassName, String mixinClassName) {
        if (!aePresent()) {
            return false;
        }
        return arsPresent();
    }
    @Override public void acceptTargets(
            Set<String> myTargets, Set<String> otherTargets) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName,
                                   ClassNode targetClass,
                                   String mixinClassName,
                                   IMixinInfo mixinInfo) { }
    @Override public void postApply(String targetClassName,
                                    ClassNode targetClass,
                                    String mixinClassName,
                                    IMixinInfo mixinInfo) { }
}
