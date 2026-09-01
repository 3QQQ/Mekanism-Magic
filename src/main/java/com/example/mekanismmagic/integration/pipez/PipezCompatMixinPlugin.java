package com.example.mekanismmagic.integration.pipez;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

/** Keeps the optional Pipez bridge inert when Pipez is not installed. */
public final class PipezCompatMixinPlugin implements IMixinConfigPlugin {
    private static final String ITEM_PIPE_TYPE =
            "de/maxhenkel/pipez/blocks/tileentity/types/ItemPipeType.class";

    private static boolean pipezPresent() {
        try (InputStream stream = MixinService.getService()
                .getResourceAsStream(ITEM_PIPE_TYPE)) {
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
        return pipezPresent();
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
