package com.example.mekanismmagic.integration.ae2;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/** Prevents the optional AE2 mixin from resolving when AE2 is absent. */
public final class Ae2CompatMixinPlugin implements IMixinConfigPlugin {
    private static boolean ae2Present() {
        try (InputStream stream = MixinService.getService()
                .getResourceAsStream(
                        "appeng/crafting/pattern/AEPatternDecoder.class")) {
            return stream != null;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(
            String targetClassName, String mixinClassName) {
        return ae2Present();
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
