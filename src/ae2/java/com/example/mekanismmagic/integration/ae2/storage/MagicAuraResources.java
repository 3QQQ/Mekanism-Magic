package com.example.mekanismmagic.integration.ae2.storage;

import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/** Loaded only while Nature's Aura is installed. */
final class MagicAuraResources {
    private MagicAuraResources() { }
    static boolean contains(ResourceLocation id) { return NaturesAuraAPI.AURA_TYPES.containsKey(id); }
    static Integer color(ResourceLocation id) {
        var type = NaturesAuraAPI.AURA_TYPES.get(id);
        return type == null ? null : type.getColor();
    }
    static Set<ResourceLocation> ids() { return Set.copyOf(NaturesAuraAPI.AURA_TYPES.keySet()); }
}
