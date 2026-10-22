package com.example.mekanismmagic.integration.forbiddenarcanus;

import mekanism.api.Action;

/** One existing machine ledger exposed as fluid, never a second fluid balance. */
public interface EssenceFluidBankHost extends com.example.mekanismmagic.integration.common.resource.EssenceResourceHost {
    int getEssenceFluidStored(int channel);

    int getEssenceFluidCapacity(int channel);

    /** Returns accepted native essence points; simulation must not mutate state. */
    int insertEssenceFluid(int channel, int requested, Action action);

    /** Returns withdrawn native essence points; simulation must not mutate state. */
    int extractEssenceFluid(int channel, int requested, Action action);

    @Override
    default int getStoredEssence(int channel) { return getEssenceFluidStored(channel); }

    @Override
    default int getEssenceCapacity(int channel) { return getEssenceFluidCapacity(channel); }

    @Override
    default int insertEssence(int channel, int requested, Action action) {
        return insertEssenceFluid(channel, requested, action);
    }

    @Override
    default int extractEssence(int channel, int requested, Action action) {
        return extractEssenceFluid(channel, requested, action);
    }
}
