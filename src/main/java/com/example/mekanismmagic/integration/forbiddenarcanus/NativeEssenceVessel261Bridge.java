package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.stal111.forbidden_arcanus.common.block.entity.EssenceUtremJarBlockEntity;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import com.stal111.forbidden_arcanus.common.essence.EssenceStorage;
import com.stal111.forbidden_arcanus.common.essence.EssenceValue;
import com.stal111.forbidden_arcanus.common.item.BloodTestTubeItem;
import com.stal111.forbidden_arcanus.core.init.ModDataComponents;
import com.stal111.forbidden_arcanus.core.init.ModItems;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** Native 2.6.1 vessels only; never grants essence to an arbitrary component-tagged item. */
public final class NativeEssenceVessel261Bridge {
    private NativeEssenceVessel261Bridge() { }

    public static boolean accepts(ItemStack stack, int channel) {
        return inspect(stack, channel).isPresent();
    }

    /** Automation may take only an actual, correctly typed, completely filled native vessel. */
    public static boolean isFullVesselForChannel(ItemStack stack, int channel) {
        if (stack == null || stack.isEmpty() || channel < 0 || channel >= EssenceType.values().length
                || stack.get(ModDataComponents.ESSENCE_VALUE) != null) return false;
        boolean nativeVessel = stack.is(ModItems.ESSENCE_UTREM_JAR.get())
                || channel == EssenceCondenserBlockEntity.AUREAL && stack.is(ModItems.AUREAL_TANK.get())
                || channel == EssenceCondenserBlockEntity.BLOOD && stack.is(ModItems.BLOOD_TEST_TUBE.get());
        if (!nativeVessel) return false;
        EssenceStorage storage = stack.get(ModDataComponents.ESSENCE_STORAGE);
        return storage != null && storage.value().type() == EssenceType.values()[channel]
                && storage.limit() > 0 && storage.value().amount() == storage.limit();
    }

    /** One physical container receives as much existing essence as its native capacity permits. */
    public static Optional<FillPlan> previewFill(ItemStack stack, int channel, int available) {
        if (available <= 0) return Optional.empty();
        var inspected = inspect(stack, channel);
        if (inspected.isEmpty()) return Optional.empty();
        Vessel vessel = inspected.get();
        int room = vessel.storage().limit() - vessel.storage().value().amount();
        int debit = Math.min(available, room);
        if (debit <= 0) return Optional.empty();
        EssenceStorage prior = vessel.storage();
        ItemStack output = vessel.item().copy();
        output.set(ModDataComponents.ESSENCE_STORAGE, new EssenceStorage(
                EssenceValue.of(prior.value().type(), prior.value().amount() + debit),
                prior.limit(), prior.showInTooltip()));
        return Optional.of(new FillPlan(output, debit));
    }

    private static Optional<Vessel> inspect(ItemStack stack, int channel) {
        if (stack == null || stack.isEmpty() || channel < 0 || channel >= EssenceType.values().length) {
            return Optional.empty();
        }
        EssenceType type = EssenceType.values()[channel];
        ItemStack single = stack.copyWithCount(1);
        if (single.get(ModDataComponents.ESSENCE_VALUE) != null) return Optional.empty();
        EssenceStorage storage;
        if (single.is(ModItems.UTREM_JAR.get())) {
            if (single.get(ModDataComponents.ESSENCE_STORAGE) != null) return Optional.empty();
            single = single.transmuteCopy(ModItems.ESSENCE_UTREM_JAR.get(), 1);
            storage = EssenceStorage.createEmpty(type, EssenceUtremJarBlockEntity.DEFAULT_LIMIT);
        } else if (single.is(ModItems.ESSENCE_UTREM_JAR.get())) {
            storage = single.get(ModDataComponents.ESSENCE_STORAGE);
            if (storage == null) storage = EssenceStorage.createEmpty(type, EssenceUtremJarBlockEntity.DEFAULT_LIMIT);
        } else if (channel == EssenceCondenserBlockEntity.AUREAL && single.is(ModItems.AUREAL_TANK.get())) {
            storage = single.get(ModDataComponents.ESSENCE_STORAGE);
            if (storage == null) return Optional.empty();
        } else if (channel == EssenceCondenserBlockEntity.BLOOD && single.is(ModItems.TEST_TUBE.get())) {
            if (single.get(ModDataComponents.ESSENCE_STORAGE) != null) return Optional.empty();
            single = single.transmuteCopy(ModItems.BLOOD_TEST_TUBE.get(), 1);
            storage = BloodTestTubeItem.DEFAULT_DATA;
        } else if (channel == EssenceCondenserBlockEntity.BLOOD && single.is(ModItems.BLOOD_TEST_TUBE.get())) {
            storage = single.get(ModDataComponents.ESSENCE_STORAGE);
            if (storage == null) return Optional.empty();
        } else {
            return Optional.empty();
        }
        return storage.value().type() == type && storage.value().amount() >= 0
                && storage.limit() > storage.value().amount() && storage.limit() > 0
                ? Optional.of(new Vessel(single, storage)) : Optional.empty();
    }

    public record FillPlan(ItemStack result, int debitedPoints) {
        public FillPlan { result = result.copy(); }
        @Override public ItemStack result() { return result.copy(); }
    }

    private record Vessel(ItemStack item, EssenceStorage storage) { }
}
