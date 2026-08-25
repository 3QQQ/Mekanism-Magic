package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.api.source.SourceProvider;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.tile.component.config.ConfigInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Mekanism machine base that participates in Ars Nouveau's Source network.
 */
public abstract class ArsSourceMachineBlockEntity
        extends NativeMagicMachineBlockEntity
        implements ISourceTile, ArsSourceModeHost {
    public enum SourceMode {
        NONE,
        INPUT,
        OUTPUT,
        INPUT_OUTPUT;

        public SourceMode next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    private static final String SOURCE_NBT = "ars_source";
    private static final String SOURCE_MODE_NBT = "ars_source_modes";

    private SourceStorage sourceStorage;
    private SourceProvider sourceProvider;
    private final java.util.EnumMap<Direction, SourceMode> sourceModes =
            new java.util.EnumMap<>(Direction.class);

    protected ArsSourceMachineBlockEntity(Holder<Block> block, BlockPos pos,
                                          BlockState state) {
        super(block, pos, state);
        for (Direction direction : Direction.values()) {
            sourceModes.put(direction, SourceMode.INPUT_OUTPUT);
        }
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

    public final SourceMode getSourceMode(Direction side) {
        return sourceModes.getOrDefault(side, SourceMode.NONE);
    }

    public final void cycleSourceMode(int index) {
        Direction[] directions = Direction.values();
        if (index < 0 || index >= directions.length) {
            return;
        }
        Direction side = directions[index];
        sourceModes.put(side, getSourceMode(side).next());
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    public final boolean sourceSideEnabled(Direction side) {
        return getSourceMode(side) != SourceMode.NONE;
    }

    public final SourceStorage getSourceStorage(Direction side) {
        return sourceSideEnabled(side) ? getSourceStorage() : null;
    }

    protected int sourceMaxReceive() {
        return ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE;
    }

    protected int sourceMaxExtract() {
        return ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE;
    }

    protected final boolean hasCreativeSourceUpgrade() {
        mekanism.api.Upgrade upgrade =
                ArsNouveauRegistries.creativeSourceUpgrade();
        return upgrade != null && upgradeComponent != null
                && upgradeComponent.getUpgrades(upgrade) > 0;
    }

    @Override
    protected boolean canRunWithoutEnergy(MachineRecipeResult recipe) {
        int sourceCost = recipe.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
        return sourceCost > 0
                && !hasCreativeSourceUpgrade()
                && getSource() >= sourceCost;
    }

    @Override
    protected int energylessTickInterval(MachineRecipeResult recipe) {
        return ArsNouveauMachineConfig.ENERGYLESS_TICK_INTERVAL;
    }

    protected final ConfigInfo setupArsItemIO(
            java.util.List<? extends IInventorySlot> inputs,
            java.util.List<? extends IInventorySlot> outputs,
            java.util.List<? extends IInventorySlot> extras) {
        return setupNativeItemIO(inputs, outputs, extras);
    }

    @Override
    public java.util.List<IInventorySlot> mekanismMagicPersistentInputs() {
        return java.util.List.of();
    }

    @Override
    protected long energyUsagePerTick(MachineRecipeResult recipe) {
        long base = baseEnergyPerTick();
        int sourceCost = recipe.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
        if (sourceCost > 0 && hasCreativeSourceUpgrade()) {
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
        return sourceCost <= 0 || hasCreativeSourceUpgrade()
                || getSource() >= sourceCost;
    }

    @Override
    protected boolean consumeRecipeResources(MachineRecipeResult recipe) {
        int sourceCost = recipe.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
        if (sourceCost <= 0 || hasCreativeSourceUpgrade()) {
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
        for (Direction direction : Direction.values()) {
            container.track(SyncableInt.create(
                    () -> getSourceMode(direction).ordinal(),
                    value -> sourceModes.put(direction,
                            SourceMode.values()[Math.max(0, Math.min(
                                    SourceMode.values().length - 1,
                                    value))])));
        }
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

    @Override
    public void saveAdditional(CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(SOURCE_NBT, getSource());
        CompoundTag modes = new CompoundTag();
        for (Direction direction : Direction.values()) {
            modes.putInt(direction.getName(), getSourceMode(direction).ordinal());
        }
        tag.put(SOURCE_MODE_NBT, modes);
    }

    @Override
    public void loadAdditional(CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        setSource(tag.getInt(SOURCE_NBT));
        CompoundTag modes = tag.getCompound(SOURCE_MODE_NBT);
        for (Direction direction : Direction.values()) {
            if (modes.contains(direction.getName())) {
                int value = Math.max(0, Math.min(
                        SourceMode.values().length - 1,
                        modes.getInt(direction.getName())));
                sourceModes.put(direction, SourceMode.values()[value]);
            }
        }
    }
}
