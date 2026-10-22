package com.example.mekanismmagic.integration.common.resource;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** Stable resource identities, independent of optional F&A or fluid implementation classes. */
public final class EssenceResources {
    public static final int AUREAL = 0;
    public static final int SOULS = 1;
    public static final int BLOOD = 2;
    public static final int EXPERIENCE = 3;
    public static final int CHANNELS = 4;
    public static final List<ResourceLocation> IDS = List.of(
            ResourceLocation.parse("forbidden_arcanus:essence_aureal"),
            ResourceLocation.parse("forbidden_arcanus:essence_souls"),
            ResourceLocation.parse("forbidden_arcanus:essence_blood"),
            ResourceLocation.parse("forbidden_arcanus:essence_experience"));

    private EssenceResources() { }

    public static boolean validChannel(int channel) {
        return channel >= 0 && channel < CHANNELS;
    }

    public static int channel(ResourceLocation resource) {
        return resource == null ? -1 : IDS.indexOf(resource);
    }
}
