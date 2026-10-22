package com.example.mekanismmagic.integration.forbiddenarcanus;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;

/** Stateless Mekanism tank view over a machine's existing integer essence ledger. */
public final class EssenceBankFluidTank implements IExtendedFluidTank {
    private final EssenceFluidBankHost host;
    private final int channel;
    private final boolean input;
    private final boolean output;
    private final IContentsListener listener;

    EssenceBankFluidTank(EssenceFluidBankHost host, int channel,
                         boolean input, boolean output, IContentsListener listener) {
        if (channel < 0 || channel >= EssenceFluidCodec.CHANNELS) {
            throw new IndexOutOfBoundsException(channel);
        }
        this.host = host;
        this.channel = channel;
        this.input = input;
        this.output = output;
        this.listener = listener;
    }

    @Override
    public FluidStack getFluid() {
        return EssenceFluidCodec.canonicalStack(channel, getFluidAmount());
    }

    @Override
    public int getFluidAmount() {
        return Math.max(0, host.getEssenceFluidStored(channel));
    }

    @Override
    public int getCapacity() {
        return Math.max(0, host.getEssenceFluidCapacity(channel));
    }

    @Override
    public boolean isFluidValid(FluidStack stack) {
        return EssenceFluidCodec.channelOf(stack) == channel;
    }

    /** Mek's multi-tank dispatcher checks equality before calling insert. */
    @Override
    public boolean isFluidEqual(FluidStack other) {
        // Source, flowing and an unambiguous c: essence alias all feed the
        // same scalar bank, even after it holds canonical fluid. Output-only
        // tanks still expose and extract only their canonical identity.
        return input ? isFluidValid(other)
                : FluidStack.isSameFluidSameComponents(getFluid(), other);
    }

    @Override
    public FluidStack insert(FluidStack stack, Action action,
                             AutomationType automationType) {
        if (!input || stack.isEmpty() || !isFluidValid(stack)) return stack;
        int accepted = Math.clamp(host.insertEssenceFluid(channel,
                stack.getAmount(), action), 0, stack.getAmount());
        if (action.execute() && accepted > 0) onContentsChanged();
        return accepted <= 0 ? stack : stack.copyWithAmount(stack.getAmount() - accepted);
    }

    @Override
    public FluidStack extract(int amount, Action action,
                              AutomationType automationType) {
        if (!output || amount <= 0 || EssenceFluidCodec.source(channel).isEmpty()) {
            return FluidStack.EMPTY;
        }
        int extracted = Math.clamp(host.extractEssenceFluid(channel,
                amount, action), 0, amount);
        if (action.execute() && extracted > 0) onContentsChanged();
        return EssenceFluidCodec.canonicalStack(channel, extracted);
    }

    @Override
    public boolean isEmpty() {
        return getFluidAmount() <= 0 || EssenceFluidCodec.source(channel).isEmpty();
    }

    @Override
    public void onContentsChanged() {
        if (listener != null) listener.onContentsChanged();
    }

    /** Native machine NBT and integer menu trackers already own this balance. */
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return new CompoundTag();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        // Do not restore a second copy of the same native ledger.
    }

    @Override
    public void setStack(FluidStack stack) {
        // All authoritative mutations must pass through exact bank transactions.
    }

    @Override
    public void setStackUnchecked(FluidStack stack) {
        // Fluid menu sync is redundant with the existing integer bank trackers.
    }

    @Override
    public int setStackSize(int amount, Action action) {
        return getFluidAmount();
    }

    @Override
    public int growStack(int amount, Action action) {
        return 0;
    }

    @Override
    public int shrinkStack(int amount, Action action) {
        return 0;
    }

    @Override
    public void setEmpty() {
        // Do not let an internal convenience method erase the native bank.
    }
}
