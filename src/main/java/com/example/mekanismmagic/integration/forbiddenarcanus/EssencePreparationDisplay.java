package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import com.stal111.forbidden_arcanus.common.essence.EssenceStorage;
import com.stal111.forbidden_arcanus.common.essence.EssenceValue;
import com.stal111.forbidden_arcanus.core.init.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Four retained native essence samples; these are guides, not item-consumption recipes. */
public record EssencePreparationDisplay(Kind kind, ItemStack sample, int essencePerCycle,
                                        int baseTicks, int baseEnergyPerTick) {
    public EssencePreparationDisplay {
        sample = sample.copy();
    }

    @Override public ItemStack sample() { return sample.copy(); }
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("mekanism_magic", "essence_condenser/" + kind.path);
    }

    public static List<EssencePreparationDisplay> recipes() {
        List<EssencePreparationDisplay> displays = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            ItemStack sample = sample(kind);
            var value = HephaestusForge261Bridge.previewRetainedEssenceSample(sample);
            if (value.isEmpty() || value.get().channel() != kind.channel) continue;
            displays.add(new EssencePreparationDisplay(kind, sample, value.get().amount(),
                    EssenceCondenserBlockEntity.BASE_TICKS[kind.channel],
                    EssenceCondenserBlockEntity.ENERGY_PER_TICK[kind.channel]));
        }
        return List.copyOf(displays);
    }

    private static ItemStack sample(Kind kind) {
        ItemStack stack = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(
                "forbidden_arcanus", kind.sampleId)).orElseThrow().getDefaultInstance();
        if (kind == Kind.BLOOD) {
            stack.set(ModDataComponents.ESSENCE_STORAGE,
                    new EssenceStorage(EssenceValue.of(EssenceType.BLOOD, 25), 3_000, true));
        }
        return stack;
    }

    public enum Kind {
        AUREAL("aureal", "aureal_bottle", EssenceCondenserBlockEntity.AUREAL),
        SOULS("souls", "soul", EssenceCondenserBlockEntity.SOULS),
        BLOOD("blood", "blood_test_tube", EssenceCondenserBlockEntity.BLOOD),
        EXPERIENCE("experience", "xpetrified_orb", EssenceCondenserBlockEntity.EXPERIENCE);

        public final String path;
        public final String sampleId;
        public final int channel;
        Kind(String path, String sampleId, int channel) {
            this.path = path;
            this.sampleId = sampleId;
            this.channel = channel;
        }
    }
}
