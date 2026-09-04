package com.example.mekanismmagic.integration.arsnouveau;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/** Keeps the optional Ars production hooks inert when Ars is not installed. */
public final class ArsSourceProductionMixinPlugin
        implements IMixinConfigPlugin {
    private static final String SOURCELINK_TILE =
            "com/hollingsworth/arsnouveau/common/block/tile/"
                    + "SourcelinkTile.class";

    private static boolean arsPresent() {
        try (InputStream stream = MixinService.getService()
                .getResourceAsStream(SOURCELINK_TILE)) {
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
        return arsPresent();
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
