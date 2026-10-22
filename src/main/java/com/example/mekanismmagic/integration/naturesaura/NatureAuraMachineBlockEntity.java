package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import mekanism.common.inventory.container.MekanismContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Shared Nature's Aura machine foundation. The Aura state itself is composed
 * so that Mekanism factory tiles can reuse exactly the same capability,
 * persistence and type-safety rules despite their separate inheritance tree.
 */
public abstract class NatureAuraMachineBlockEntity
        extends NativeMagicMachineBlockEntity implements AuraSideModeHost {
    public enum AuraSideMode {
        NONE(false, false, "--"),
        INPUT(true, false, "IN"),
        OUTPUT(false, true, "OUT"),
        INPUT_OUTPUT(true, true, "IO");

        private final boolean input;
        private final boolean output;
        private final String shortName;

        AuraSideMode(boolean input, boolean output, String shortName) {
            this.input = input;
            this.output = output;
            this.shortName = shortName;
        }

        public AuraSideMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public AuraSideMode previous() {
            return values()[Math.floorMod(ordinal() - 1, values().length)];
        }

        public boolean allowsInput() {
            return input;
        }

        public boolean allowsOutput() {
            return output;
        }

        public String shortName() {
            return shortName;
        }
    }

    private final NatureAuraStorageComponent auraStorage;

    protected NatureAuraMachineBlockEntity(
            Holder<Block> block, BlockPos pos, BlockState state,
            int auraCapacity, int auraTransferRate,
            AuraSideMode defaultAuraSideMode) {
        super(block, pos, state);
        auraStorage = new NatureAuraStorageComponent(
                this::getLevel,
                this::hasCreativeAuraUpgrade,
                this::setChanged,
                () -> {
                    if (level != null) {
                        level.invalidateCapabilities(worldPosition);
                    }
                },
                this::onAuraChanged,
                auraCapacity, auraTransferRate, defaultAuraSideMode,
                this::getOperatingAuraType);
    }

    /** Consumers stay local; generators may explicitly select an output type. */
    protected @Nullable IAuraType getOperatingAuraType() {
        return level == null ? null : IAuraType.forLevel(level);
    }

    public final @Nullable IAuraContainer getAuraContainer(
            @Nullable Direction side) {
        return auraStorage.getContainer(side);
    }

    public final AuraSideMode getAuraSideMode(Direction side) {
        return auraStorage.getSideMode(side);
    }

    public final void cycleAuraSideMode(int directionIndex) {
        auraStorage.cycle(directionIndex);
    }

    @Override
    public final void setAuraSideMode(Direction side, AuraSideMode mode) {
        auraStorage.setAuraSideMode(side, mode);
    }

    @Override
    public final void setAuraSideModes(java.util.Map<Direction, AuraSideMode> modes) {
        auraStorage.setAuraSideModes(modes);
    }

    @Override
    public final boolean isCreativeAuraActive() {
        return auraStorage.isCreativeAuraActive();
    }

    public final int getStoredAura() {
        return auraStorage.getStored();
    }

    public final int getAuraCapacity() {
        return auraStorage.capacity();
    }

    public final int getAuraColor() {
        return auraStorage.color();
    }

    public final boolean hasAuraTypeMismatch() {
        return auraStorage.mismatch();
    }

    public final boolean hasNotEnoughAura() {
        return auraStorage.notEnough();
    }

    protected final boolean hasCreativeAuraUpgrade() {
        return getComponent() != null
                && getComponent().getUpgrades(
                MagicUpgrades.creativeMagic()) > 0;
    }

    protected final boolean refreshAuraTypeState() {
        return auraStorage.refreshTypeState();
    }

    protected final int budgetedStore(int amount, boolean simulate) {
        return auraStorage.budgetedStore(amount, simulate);
    }

    protected final int budgetedDrain(int amount, boolean simulate) {
        return auraStorage.budgetedDrain(amount, simulate);
    }

    protected final boolean storeAuraExact(int amount, boolean simulate) {
        return auraStorage.exactStore(amount, simulate);
    }

    protected final boolean drainAuraExact(int amount, boolean simulate) {
        return auraStorage.exactDrain(amount, simulate);
    }

    protected final NatureAuraStorageComponent.Snapshot
    snapshotNatureAuraForUpgrade() {
        return auraStorage.snapshot();
    }

    protected final void restoreNatureAuraFromUpgrade(
            NatureAuraStorageComponent.Snapshot snapshot) {
        auraStorage.restore(snapshot);
    }

    @Override
    protected boolean onUpdateServer() {
        beginNatureAuraUpdate();
        return super.onUpdateServer();
    }

    /** Shared prelude for specialized processing loops using nativeBaseUpdate(). */
    protected final void beginNatureAuraUpdate() {
        auraStorage.resetWarning();
        auraStorage.refreshTypeState();
    }

    @Override
    protected boolean hasRecipeResources(MachineRecipeResult recipe) {
        int cost = recipe.resourceCost(
                NatureAuraMachineConfig.AURA_RESOURCE);
        boolean available = cost <= 0 || hasCreativeAuraUpgrade()
                || auraStorage.exactDrain(cost, true);
        auraStorage.setNotEnough(!available);
        return available;
    }

    @Override
    protected boolean consumeRecipeResources(MachineRecipeResult recipe) {
        int cost = recipe.resourceCost(
                NatureAuraMachineConfig.AURA_RESOURCE);
        return cost <= 0 || hasCreativeAuraUpgrade()
                || auraStorage.exactDrain(cost, false);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        auraStorage.addTrackers(container);
    }

    @Override
    protected final void saveNativeMachineData(
            CompoundTag tag, HolderLookup.Provider registries) {
        super.saveNativeMachineData(tag, registries);
        auraStorage.save(tag, registries);
        saveNatureAuraMachineData(tag, registries);
    }

    @Override
    protected final void loadNativeMachineData(
            CompoundTag tag, HolderLookup.Provider registries) {
        super.loadNativeMachineData(tag, registries);
        auraStorage.load(tag, registries);
        loadNatureAuraMachineData(tag, registries);
    }

    protected void saveNatureAuraMachineData(
            CompoundTag tag, HolderLookup.Provider registries) {
    }

    protected void loadNatureAuraMachineData(
            CompoundTag tag, HolderLookup.Provider registries) {
    }

    /** Called after a real Aura amount change, never for client sync. */
    protected void onAuraChanged() {
    }
}
