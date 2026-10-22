package com.example.mekanismmagic.integration.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.common.resource.EssenceResources;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical AE keys over stable disk resource IDs, including optional providers. */
public final class MagicResourceKeys {
    public static final ResourceLocation SOURCE_ID = ResourceLocation.parse("ars_nouveau:source");
    public static final int ESSENCE_CHANNELS = EssenceResources.CHANNELS;
    public static final AEKeyType TYPE = new AEKeyType(ResourceLocation.parse("mekanism_magic:magic"),
            MagicResourceKey.class, Component.translatable("mekanism_magic.storage.magic")) {
        @Override public MapCodec<MagicResourceKey> codec() { return MagicResourceKey.CODEC; }
        @Override public AEKey readFromPacket(RegistryFriendlyByteBuf buffer) {
            return new MagicResourceKey(buffer.readResourceLocation());
        }
        @Override public int getAmountPerByte() { return unitsPerByte(); }
        @Override public int getAmountPerOperation() { return 1000; }
    };
    private static final Map<ResourceLocation, Definition> EXTENSIONS = new LinkedHashMap<>();
    private static AEKey nativeSource;
    private static boolean nativeSourceResolved;
    private static final AEKey OWN_SOURCE = new MagicResourceKey(SOURCE_ID);

    static {
        String[] names = {"aureal", "souls", "blood", "experience"};
        String[] icons = {"aureal_bottle", "soul", "blood_test_tube",
                "xpetrified_orb"};
        for (int channel = 0; channel < ESSENCE_CHANNELS; channel++) {
            register(EssenceResources.IDS.get(channel),
                    Component.translatable(
                            "mekanism_magic.storage.essence."
                                    + names[channel]),
                    ResourceLocation.fromNamespaceAndPath(
                            "forbidden_arcanus", icons[channel]));
        }
    }

    private MagicResourceKeys() { }

    public static int unitsPerByte() { return 1000; }

    /** Stable typed essence key; a unit is one native F&A essence point. */
    public static @Nullable AEKey essence(int channel) {
        return channel < 0 || channel >= ESSENCE_CHANNELS ? null
                : key(EssenceResources.IDS.get(channel));
    }

    public static int essenceChannel(ResourceLocation id) {
        return EssenceResources.channel(id);
    }

    public static synchronized AEKey source() {
        if (!nativeSourceResolved) {
            if (ModCompatibility.loaded(ModCompatibility.ARS_ENERGISTIQUE)) {
                try {
                    nativeSource = (AEKey) Class.forName("gripe._90.arseng.me.key.SourceKey")
                            .getField("KEY").get(null);
                } catch (ReflectiveOperationException | LinkageError failure) {
                    throw new IllegalStateException("Ars Energistique Source key is unavailable", failure);
                }
            }
            nativeSourceResolved = true;
        }
        return nativeSource == null ? OWN_SOURCE : nativeSource;
    }

    /** Register new resource families during common setup, before networks mount cells. */
    public static synchronized void register(ResourceLocation id, Component name, ResourceLocation icon) {
        if (id.equals(SOURCE_ID) || EXTENSIONS.putIfAbsent(id, new Definition(name, icon)) != null) {
            throw new IllegalArgumentException("Duplicate magic resource: " + id);
        }
    }

    public static @Nullable AEKey key(ResourceLocation id) {
        if (id.equals(SOURCE_ID)) {
            return ModCompatibility.arsNouveauLoaded() ? source() : null;
        }
        if (EXTENSIONS.containsKey(id) || ModCompatibility.naturesAuraLoaded() && MagicAuraResources.contains(id)) {
            return new MagicResourceKey(id);
        }
        return null;
    }

    /** Reject aliases at runtime: AE monitor deltas must use the same advertised key. */
    public static @Nullable ResourceLocation resourceId(AEKey candidate) {
        if (candidate == null) return null;
        ResourceLocation id = candidate instanceof MagicResourceKey magic ? magic.resource()
                : candidate.equals(source()) ? SOURCE_ID : null;
        if (id == null) return null;
        AEKey canonical = key(id);
        return canonical != null && canonical.equals(candidate) ? id : null;
    }

    public static Set<AEKeyType> keyTypes() {
        Set<AEKeyType> types = new HashSet<>();
        types.add(TYPE);
        if (ModCompatibility.arsNouveauLoaded()) types.add(source().getType());
        return Set.copyOf(types);
    }

    public static List<ResourceLocation> resources() {
        Set<ResourceLocation> values = new HashSet<>(EXTENSIONS.keySet());
        // This is a selector list, not the decoder's allowlist. Existing
        // disks retain and expose essence keys even while F&A is absent.
        if (!ModCompatibility.forbiddenArcanusLoaded()) {
            values.removeAll(EssenceResources.IDS);
        }
        if (ModCompatibility.arsNouveauLoaded()) values.add(SOURCE_ID);
        if (ModCompatibility.naturesAuraLoaded()) values.addAll(MagicAuraResources.ids());
        return values.stream().sorted().toList();
    }

    public static Component displayName(ResourceLocation id) {
        if (SOURCE_ID.equals(id)) return Component.translatable("mekanism_magic.storage.source");
        Definition definition = EXTENSIONS.get(id);
        if (definition != null) return definition.name();
        if (ModCompatibility.naturesAuraLoaded() && MagicAuraResources.contains(id)) {
            Component typeName = id.getNamespace().equals("naturesaura")
                    && Set.of("overworld", "nether", "end", "other").contains(id.getPath())
                    ? Component.translatable("mekanism_magic.storage.aura." + id.getPath())
                    : Component.literal(id.toString());
            return Component.translatable("mekanism_magic.storage.aura_type",
                    typeName);
        }
        return Component.literal(id.toString());
    }

    public static ResourceLocation icon(ResourceLocation id) {
        if (SOURCE_ID.equals(id)) return ResourceLocation.parse("ars_nouveau:source_gem");
        Definition definition = EXTENSIONS.get(id);
        return definition != null ? definition.icon() : ResourceLocation.parse("naturesaura:aura_bottle");
    }

    private record Definition(Component name, ResourceLocation icon) { }
}
