package com.example.mekanismmagic.integration.common.resource;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.capabilities.BlockCapability;

/** Optional machine endpoints, with no F&A, AE2, Ars, Nature or PFAA API link. */
public final class EssenceResourceCapabilities {
    public static final BlockCapability<EssenceResourceHost, net.minecraft.core.Direction> ESSENCE =
            BlockCapability.createSided(ResourceLocation.parse("mekanism_magic:essence"), EssenceResourceHost.class);

    private EssenceResourceCapabilities() { }
}
