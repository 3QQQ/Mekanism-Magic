package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.inventory.CreativeMagicUpgradeSlot;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.upgrade.CreativeMagicUpgradeMigration;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.api.source.SourceProvider;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
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
        implements ISourceTile, ArsSourceModeHost, SourceLinkHost,
        SourceDisplayHost {
    public enum SourceMode {
        NONE,
        INPUT,
        OUTPUT,
        INPUT_OUTPUT;

        public SourceMode next() {
            return shift(1);
        }

        public SourceMode shift(int delta) {
            return values()[Math.floorMod(ordinal() + delta,
                    values().length)];
        }

        public boolean allowsInput() {
            return this == INPUT || this == INPUT_OUTPUT;
        }

        public boolean allowsOutput() {
            return this == OUTPUT || this == INPUT_OUTPUT;
        }
    }

    private static final String SOURCE_NBT = "ars_source";
    private static final String SOURCE_MODE_NBT = "ars_source_modes";

    private SourceStorage sourceStorage;
    private SourceProvider sourceProvider;
    protected BasicInventorySlot legacyCreativeMagicUpgradeSlot;
    private final java.util.EnumMap<Direction, SourceMode> sourceModes =
            new java.util.EnumMap<>(Direction.class);
    private final java.util.EnumMap<Direction, ISourceCap> sidedSourceCaps =
            new java.util.EnumMap<>(Direction.class);
    private final SourceLinkState sourceLinks = new SourceLinkState();
    private final SourceDisplaySnapshot sourceDisplay =
            new SourceDisplaySnapshot();
    private long nextNearbySourcePullGameTime = Long.MIN_VALUE;

    protected ArsSourceMachineBlockEntity(Holder<Block> block, BlockPos pos,
                                          BlockState state) {
        super(block, pos, state);
        for (Direction direction : Direction.values()) {
            sourceModes.put(direction, SourceMode.INPUT_OUTPUT);
            sidedSourceCaps.put(direction, new SourceModeCapability(
                    this::getSourceStorage,
                    () -> getSourceMode(direction)));
        }
    }

    public final SourceStorage getSourceStorage() {
        if (sourceStorage == null) {
            sourceStorage = new InputGatedSourceStorage(
                    sourceCapacity(),
                    sourceMaxReceive(),
                    sourceMaxExtract(),
                    () -> !hasCreativeSourceUpgrade(),
                    this::setChanged);
        }
        return sourceStorage;
    }

    public final SourceMode getSourceMode(Direction side) {
        return sourceModes.getOrDefault(side, SourceMode.NONE);
    }

    protected final java.util.EnumMap<Direction, SourceMode>
    sourceModesForUpgrade() {
        return new java.util.EnumMap<>(sourceModes);
    }

    public final void cycleSourceMode(int index, int delta) {
        Direction[] directions = Direction.values();
        if (index < 0 || index >= directions.length) {
            return;
        }
        Direction side = directions[index];
        sourceModes.put(side, getSourceMode(side).shift(delta));
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    public final boolean sourceSideEnabled(Direction side) {
        return getSourceMode(side) != SourceMode.NONE;
    }

    public final ISourceCap getSourceStorage(Direction side) {
        return hasInternalSourceBuffer() && sourceSideEnabled(side)
                ? sidedSourceCaps.get(side) : null;
    }

    /**
     * Some Ars machines interact with nearby Source blocks directly and do
     * not expose a Source tank of their own.
     */
    protected boolean hasInternalSourceBuffer() {
        return true;
    }

    protected int sourceCapacity() {
        return ArsNouveauMachineConfig.SOURCE_CAPACITY;
    }

    protected int sourceMaxReceive() {
        return ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE;
    }

    protected int sourceMaxExtract() {
        return ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE;
    }

    @Override
    protected void createMachineAddonSlots(InventorySlotHelper helper,
                                           IContentsListener listener) {
        if (!hasLegacyCreativeMagicUpgradeSlot()) {
            return;
        }
        // Keep the old serialized slot index for one-way save migration. It
        // is no longer exposed by any GUI and is not part of installation.
        legacyCreativeMagicUpgradeSlot =
                CreativeMagicUpgradeSlot.create(listener);
        helper.addSlot(legacyCreativeMagicUpgradeSlot);
    }

    protected boolean hasLegacyCreativeMagicUpgradeSlot() {
        return true;
    }

    protected final boolean hasCreativeSourceUpgrade() {
        return getComponent() != null
                && getComponent().getUpgrades(
                MagicUpgrades.creativeMagic()) > 0;
    }

    protected int sourceInteractionRadius() {
        return ArsNouveauMachineConfig.SOURCE_INTERACTION_RADIUS;
    }

    protected final boolean hasSourceForRecipe(int sourceCost) {
        return ArsSourceInteraction.hasSource(this, level, worldPosition,
                sourceInteractionRadius(), sourceCost);
    }

    protected final boolean consumeSourceForRecipe(int sourceCost) {
        return ArsSourceInteraction.consumeSource(this, level,
                worldPosition, sourceInteractionRadius(), sourceCost);
    }

    /**
     * Mirrors Ars Nouveau's Imbuement Chamber refill cadence. Consumer
     * machines continuously top up a non-full tank, prioritizing explicitly
     * linked jars and then discovering nearby jars/providers. Keeping this
     * separate from recipe withdrawal lets the GUI show incoming Source even
     * while the machine is idle.
     */
    protected final boolean autoPullNearbySource() {
        if (hasCreativeSourceUpgrade()) {
            nextNearbySourcePullGameTime = Long.MIN_VALUE;
            return false;
        }
        if (!hasInternalSourceBuffer() || !supportsAutomaticSourcePull()
                || level == null || level.isClientSide()
                || getSource() >= getMaxSource()) {
            return false;
        }
        long gameTime = level.getGameTime();
        if (nextNearbySourcePullGameTime != Long.MIN_VALUE
                && gameTime < nextNearbySourcePullGameTime) {
            return false;
        }
        int request = Math.min(Math.max(1, getTransferRate()),
                getMaxSource() - getSource());
        int moved = sourceLinks.pullInto(this, level, request);
        if (moved < request) {
            moved += ArsSourceInteraction.pullNearbySource(this, level,
                    worldPosition, sourceInteractionRadius(),
                    request - moved);
        }
        nextNearbySourcePullGameTime = gameTime + (moved > 0
                ? ArsNouveauMachineConfig.NEARBY_SOURCE_PULL_INTERVAL
                : unsuccessfulSourcePullInterval());
        return moved > 0;
    }

    /** Idle consumers may back off expensive world scans after a miss. */
    protected int unsuccessfulSourcePullInterval() {
        return ArsNouveauMachineConfig.NEARBY_SOURCE_PULL_INTERVAL;
    }

    /** Source-producing machines override this to avoid draining jars. */
    protected boolean supportsAutomaticSourcePull() {
        return true;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sourceChanged = autoPullNearbySource();
        boolean changed = super.onUpdateServer();
        return sourceChanged || changed;
    }

    @Override
    public final boolean mekanismMagicLinkSourceJar(BlockPos sourceJar) {
        if (!hasInternalSourceBuffer() || !supportsAutomaticSourcePull()
                || level == null
                || !(level.getBlockEntity(sourceJar)
                instanceof com.hollingsworth.arsnouveau.common.block.tile
                        .SourceJarTile)) {
            return false;
        }
        boolean linked = sourceLinks.link(level.dimension().location(),
                worldPosition, sourceJar);
        if (linked) {
            nextNearbySourcePullGameTime = Long.MIN_VALUE;
            setChanged();
        }
        return linked;
    }

    @Override
    public final int mekanismMagicClearSourceJarLinks() {
        int removed = sourceLinks.clear();
        if (removed > 0) {
            setChanged();
        }
        return removed;
    }

    protected final java.util.List<BlockPos> sourceLinksForUpgrade() {
        return sourceLinks.snapshot();
    }

    protected final void setSourceLinksFromUpgrade(
            java.util.Collection<BlockPos> links) {
        sourceLinks.replace(links, level == null ? null
                : level.dimension().location());
        nextNearbySourcePullGameTime = Long.MIN_VALUE;
    }

    @Override
    protected boolean canRunWithoutEnergy(MachineRecipeResult recipe) {
        int sourceCost = recipe.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
        return sourceCost > 0
                && !hasCreativeSourceUpgrade()
                && hasSourceForRecipe(sourceCost);
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
        return mekanism.common.util.MekanismUtils.getEnergyPerTick(
                this, baseEnergyPerTick());
    }

    @Override
    protected boolean hasRecipeResources(MachineRecipeResult recipe) {
        int sourceCost = recipe.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
        return sourceCost <= 0 || hasCreativeSourceUpgrade()
                || hasSourceForRecipe(sourceCost);
    }

    @Override
    protected boolean consumeRecipeResources(MachineRecipeResult recipe) {
        int sourceCost = recipe.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
        if (sourceCost <= 0 || hasCreativeSourceUpgrade()) {
            return true;
        }
        return consumeSourceForRecipe(sourceCost);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        if (!hasInternalSourceBuffer()) {
            return;
        }
        sourceDisplay.addTrackers(container, this::getMaxSource,
                this::hasCreativeSourceUpgrade);
        container.track(SyncableInt.create(this::getSource,
                this::setSourceFromContainer));
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
    protected final void onNativeMachineLoaded() {
        super.onNativeMachineLoaded();
        CreativeMagicUpgradeMigration.migrate(
                this, legacyCreativeMagicUpgradeSlot);
        if (hasInternalSourceBuffer()
                && level != null && !level.isClientSide()) {
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
        onArsMachineLoaded();
    }

    /** Ars-specific load hook; shared Source setup has already completed. */
    protected void onArsMachineLoaded() {
    }

    @Override
    public int getTransferRate() {
        return ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE;
    }

    @Override
    public boolean canAcceptSource() {
        return getSourceStorage().canAcceptSource(1);
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
        if (amount > 0 && hasCreativeSourceUpgrade()) {
            return getSource();
        }
        return setSource(getSource() + amount);
    }

    @Override
    public int addSource(int amount, boolean simulate) {
        return getSourceStorage().receiveSource(amount, simulate);
    }

    @Override
    public int removeSource(int amount) {
        if (amount != 0) {
            setSource(getSource() - amount);
        }
        return getSource();
    }

    @Override
    public int removeSource(int amount, boolean simulate) {
        return getSourceStorage().extractSource(amount, simulate);
    }

    public final double getSourceScale() {
        return getMaxSource() <= 0 ? 0
                : getSource() / (double) getMaxSource();
    }

    @Override
    public final boolean mekanismMagicCreativeSourceActive() {
        return sourceDisplay.creativeOr(hasCreativeSourceUpgrade());
    }

    @Override
    public final int mekanismMagicDisplayedSourceCapacity() {
        return sourceDisplay.capacityOr(getMaxSource());
    }

    private void setSourceFromContainer(int amount) {
        SourceStorage storage = getSourceStorage();
        if (level != null && level.isClientSide()) {
            int requiredCapacity = Math.max(Math.max(0, amount),
                    sourceDisplay.capacityOr(
                            storage.getSourceCapacity()));
            if (storage.getSourceCapacity() < requiredCapacity) {
                storage.setMaxSource(requiredCapacity);
            }
        }
        storage.setSource(amount);
    }

    @Override
    protected final void saveNativeMachineData(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.saveNativeMachineData(tag, registries);
        tag.putInt(SOURCE_NBT, getSource());
        CompoundTag modes = new CompoundTag();
        for (Direction direction : Direction.values()) {
            modes.putInt(direction.getName(), getSourceMode(direction).ordinal());
        }
        tag.put(SOURCE_MODE_NBT, modes);
        sourceLinks.save(tag);
        saveArsMachineData(tag, registries);
    }

    @Override
    protected final void loadNativeMachineData(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.loadNativeMachineData(tag, registries);
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
        sourceLinks.load(tag);
        nextNearbySourcePullGameTime = Long.MIN_VALUE;
        loadArsMachineData(tag, registries);
    }

    /** Machine-specific NBT written after the common Source state. */
    protected void saveArsMachineData(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
    }

    /** Machine-specific NBT read after the common Source state. */
    protected void loadArsMachineData(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
    }
}
