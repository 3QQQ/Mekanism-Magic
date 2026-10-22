package com.example.mekanismmagic.integration.forbiddenarcanus;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/** Optional PackagedFAA 1.0.3 fluid identity; one mB equals one native essence point. */
public final class EssenceFluidCodec {
    public static final int CHANNELS = 4;
    private static final String[] NAMES = {"aureal", "souls", "blood", "experience"};
    private static final ResourceLocation[] SOURCE_IDS = new ResourceLocation[CHANNELS];
    private static final ResourceLocation[] FLOWING_IDS = new ResourceLocation[CHANNELS];
    @SuppressWarnings("unchecked")
    private static final TagKey<Fluid>[] TAGS = new TagKey[CHANNELS];

    static {
        for (int channel = 0; channel < CHANNELS; channel++) {
            SOURCE_IDS[channel] = ResourceLocation.fromNamespaceAndPath(
                    "packaged_faa", "fluid_" + NAMES[channel]);
            // Published PackagedFAA spells this suffix without an underscore.
            FLOWING_IDS[channel] = ResourceLocation.fromNamespaceAndPath(
                    "packaged_faa", "fluid_" + NAMES[channel] + "flowing");
            TAGS[channel] = TagKey.create(Registries.FLUID,
                    ResourceLocation.fromNamespaceAndPath("c", NAMES[channel]));
        }
    }

    private EssenceFluidCodec() { }

    public static Optional<Fluid> source(int channel) {
        if (channel < 0 || channel >= CHANNELS) return Optional.empty();
        return BuiltInRegistries.FLUID.getOptional(SOURCE_IDS[channel])
                .filter(fluid -> fluid != Fluids.EMPTY);
    }

    /** The optional fluid layer is all-or-nothing; incomplete registries never expose ports. */
    public static boolean available() {
        for (int channel = 0; channel < CHANNELS; channel++) {
            if (source(channel).isEmpty()) return false;
        }
        return true;
    }

    public static FluidStack canonicalStack(int channel, int amount) {
        if (amount <= 0) return FluidStack.EMPTY;
        return source(channel).map(fluid -> new FluidStack(fluid, amount))
                .orElse(FluidStack.EMPTY);
    }

    /** Exact published identities win; one matching c: tag is an alias, two are ambiguous. */
    public static int channelOf(FluidStack stack) {
        if (stack.isEmpty() || !FluidStack.isSameFluidSameComponents(
                stack, new FluidStack(stack.getFluid(), stack.getAmount()))) {
            return -1;
        }
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        for (int channel = 0; channel < CHANNELS; channel++) {
            if (SOURCE_IDS[channel].equals(id) || FLOWING_IDS[channel].equals(id)) {
                return channel;
            }
        }
        int found = -1;
        for (int channel = 0; channel < CHANNELS; channel++) {
            if (stack.is(TAGS[channel])) {
                if (found != -1) return -1;
                found = channel;
            }
        }
        return found;
    }
}
