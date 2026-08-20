package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.api.source.SourceProvider;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mekanism machine base that participates in Ars Nouveau's Source network.
 */
public abstract class ArsSourceMachineBlockEntity
        extends NativeMagicMachineBlockEntity implements ISourceTile {
    public static final int SOURCE_CONVERSION_MODULE_SLOT = 42;
    private static final String SOURCE_NBT = "ars_source";

    private SourceStorage sourceStorage;
    private SourceProvider sourceProvider;
    protected BasicInventorySlot sourceConversionModuleSlot;

    protected ArsSourceMachineBlockEntity(Holder<Block> block, BlockPos pos,
                                          BlockState state) {
        super(block, pos, state);
    }

    public final SourceStorage getSourceStorage() {
        if (sourceStorage == null) {
            sourceStorage = new SourceStorage(
                    ArsNouveauMachineConfig.SOURCE_CAPACITY,
                    sourceMaxReceive(),
                    sourceMaxExtract()) {
                @Override
                public void onContentsChanged() {
                    setChanged();
                }
            };
        }
        return sourceStorage;
    }

    protected int sourceMaxReceive() {
        return ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE;
    }

    protected int sourceMaxExtract() {
        return ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE;
    }

    protected final BasicInventorySlot addSourceConversionModuleSlot(
            InventorySlotHelper helper, IContentsListener listener,
            int x, int y) {
        sourceConversionModuleSlot = registerLogicalSlot(helper,
                SOURCE_CONVERSION_MODULE_SLOT,
                BasicInventorySlot.at(
                        stack -> stack.is(
                                ArsNouveauRegistries.SOURCE_CONVERSION_MODULE.get()),
                        listener, x, y));
        return sourceConversionModuleSlot;
    }

    protected final boolean hasSourceConversionModule() {
        return sourceConversionModuleSlot != null
                && !sourceConversionModuleSlot.getStack().isEmpty();
    }

    protected final void setupArsItemIO(
            java.util.List<? extends IInventorySlot> inputs,
            java.util.List<? extends IInventorySlot> outputs,
            java.util.List<? extends IInventorySlot> extras) {
        setupNativeItemIO(inputs, outputs, extras);
    }

    @Override
    protected long energyUsagePerTick(MachineRecipeResult recipe) {
        long base = baseEnergyPerTick();
        int sourceCost = recipe.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
        if (sourceCost > 0 && hasSourceConversionModule()) {
            base += Math.max(1L, Math.ceilDiv(
                    sourceCost * ArsNouveauMachineConfig.FE_PER_SOURCE,
                    Math.max(1, recipe.duration())));
        }
        return mekanism.common.util.MekanismUtils.getEnergyPerTick(this, base);
    }

    @Override
    protected boolean hasRecipeResources(MachineRecipeResult recipe) {
        int sourceCost = recipe.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
        return sourceCost <= 0 || hasSourceConversionModule()
                || getSource() >= sourceCost;
    }

    @Override
    protected boolean consumeRecipeResources(MachineRecipeResult recipe) {
        int sourceCost = recipe.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
        if (sourceCost <= 0 || hasSourceConversionModule()) {
            return true;
        }
        if (getSource() < sourceCost) {
            return false;
        }
        getSourceStorage().extractSource(sourceCost, false);
        return true;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(this::getSource,
                this::setSource));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide()) {
            if (sourceProvider == null) {
                sourceProvider = new SourceProvider(this, worldPosition) {
                    @Override
                    public boolean isValid() {
                        return !ArsSourceMachineBlockEntity.this.isRemoved()
                                && ArsSourceMachineBlockEntity.this.level != null
                                && ArsSourceMachineBlockEntity.this.level
                                .getBlockEntity(worldPosition)
                                == ArsSourceMachineBlockEntity.this;
                    }
                };
            }
            SourceManager.INSTANCE.addInterface(level, sourceProvider);
        }
    }

    @Override
    public int getTransferRate() {
        return ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE;
    }

    @Override
    public boolean canAcceptSource() {
        return getSource() < getMaxSource();
    }

    @Override
    public boolean canProvideSource() {
        return getSource() > 0;
    }

    @Override
    public int getSource() {
        return getSourceStorage().getSource();
    }

    @Override
    public int getMaxSource() {
        return getSourceStorage().getSourceCapacity();
    }

    @Override
    public int setSource(int amount) {
        getSourceStorage().setSource(amount);
        return getSource();
    }

    @Override
    public int addSource(int amount) {
        return setSource(getSource() + amount);
    }

    @Override
    public int removeSource(int amount) {
        if (amount != 0) {
            setSource(getSource() - amount);
        }
        return getSource();
    }

    public final double getSourceScale() {
        return getMaxSource() <= 0 ? 0
                : getSource() / (double) getMaxSource();
    }

    public final ItemStack getSourceConversionModuleStack() {
        return sourceConversionModuleSlot == null ? ItemStack.EMPTY
                : sourceConversionModuleSlot.getStack();
    }

    @Override
    public void saveAdditional(CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(SOURCE_NBT, getSource());
    }

    @Override
    public void loadAdditional(CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        setSource(tag.getInt(SOURCE_NBT));
    }
}
