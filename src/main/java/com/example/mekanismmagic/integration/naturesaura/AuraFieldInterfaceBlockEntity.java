package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import de.ellpeck.naturesaura.api.aura.chunk.IAuraChunk;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.EnergyUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Optional;

/**
 * Explicit bridge between one loaded world Aura chunk and an internal Aura
 * buffer. It moves existing Aura only; FE pays for transport and never creates
 * Aura.
 */
public final class AuraFieldInterfaceBlockEntity
        extends NatureAuraMachineBlockEntity {
    private static final String FIELD_MODE = "nature_aura_field_mode";
    private static final String LAST_AREA_AURA = "nature_aura_last_area";

    public enum FieldMode {
        ABSORB("gui.mekanism_magic.aura_mode_absorb"),
        RELEASE("gui.mekanism_magic.aura_mode_release"),
        BALANCE("gui.mekanism_magic.aura_mode_balance");

        private final String translationKey;

        FieldMode(String translationKey) {
            this.translationKey = translationKey;
        }

        public FieldMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private FieldMode fieldMode = FieldMode.ABSORB;
    private int lastAreaAura = IAuraChunk.DEFAULT_AURA;
    private long nextFieldOperation = Long.MIN_VALUE;
    private int idleBackoff = NatureAuraMachineConfig.FIELD_INTERVAL;
    private long activeUntil;
    private boolean hadOperationEnergy;

    public AuraFieldInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(NaturesAuraRegistries.AURA_FIELD_INTERFACE_BLOCK.get()
                        .builtInRegistryHolder(), pos, state,
                NatureAuraMachineConfig.FIELD_CAPACITY,
                NatureAuraMachineConfig.FIELD_TRANSFER,
                AuraSideMode.INPUT_OUTPUT);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        setupNativeItemIO(java.util.List.of(), java.util.List.of(),
                java.util.List.of());
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return Optional.empty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return (int) Math.min(Integer.MAX_VALUE, baseEnergyUsage());
    }

    /** Registry rates use Joules; field transport is explicitly priced in FE. */
    public static long baseEnergyUsage() {
        return NatureMachineEnergyMath.averageFieldJoulesPerTick(
                NatureAuraMachineConfig.FIELD_TRANSFER, NatureAuraMachineConfig.FIELD_INTERVAL,
                NatureAuraMachineConfig.FIELD_FE_PER_AURA, EnergyUnit.FORGE_ENERGY.getConversion());
    }

    private long fePerAura() {
        return Math.max(1L, MekanismUtils.getEnergyPerTick(this, NatureAuraMachineConfig.FIELD_FE_PER_AURA));
    }

    @Override
    protected int energySlotX() {
        return 28;
    }

    @Override
    protected int energySlotY() {
        return 35;
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    public FieldMode getFieldMode() {
        return fieldMode;
    }

    public int getLastAreaAura() {
        return lastAreaAura;
    }

    public void cycleFieldMode() {
        fieldMode = fieldMode.next();
        idleBackoff = NatureAuraMachineConfig.FIELD_INTERVAL;
        nextFieldOperation = Long.MIN_VALUE;
        setChanged();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = nativeBaseUpdate();
        if (!(level instanceof ServerLevel serverLevel)) {
            return changed;
        }
        long gameTime = serverLevel.getGameTime();
        long minimumOperationEnergy = NatureMachineEnergyMath.fieldBatchJoules(
                1, fePerAura(), EnergyUnit.FORGE_ENERGY.getConversion());
        boolean hasOperationEnergy = energyContainer != null
                && energyContainer.getEnergy() >= minimumOperationEnergy;
        if (hasOperationEnergy && !hadOperationEnergy
                && nextFieldOperation != Long.MIN_VALUE) {
            idleBackoff = NatureAuraMachineConfig.FIELD_INTERVAL;
            nextFieldOperation = gameTime;
        }
        hadOperationEnergy = hasOperationEnergy;
        if (nextFieldOperation == Long.MIN_VALUE) {
            nextFieldOperation = gameTime + Math.floorMod(
                    worldPosition.hashCode(),
                    NatureAuraMachineConfig.FIELD_INTERVAL);
        }
        if (gameTime >= nextFieldOperation) {
            clearNativeRecipeWarnings();
            int moved = canFunction() ? exchangeWithField(serverLevel) : 0;
            if (moved > 0) {
                changed = true;
                idleBackoff = NatureAuraMachineConfig.FIELD_INTERVAL;
                activeUntil = gameTime + 5;
            } else {
                idleBackoff = Math.min(
                        NatureAuraMachineConfig.FIELD_MAX_BACKOFF,
                        Math.max(NatureAuraMachineConfig.FIELD_INTERVAL,
                                idleBackoff * 2));
            }
            nextFieldOperation = gameTime + idleBackoff;
        }
        return finishServerUpdate(changed, gameTime < activeUntil);
    }

    private int exchangeWithField(ServerLevel serverLevel) {
        if (!serverLevel.hasChunkAt(worldPosition)) {
            return 0;
        }
        IAuraType worldType = IAuraType.forLevel(serverLevel);
        if (worldType == null || !refreshAuraTypeState()) {
            return 0;
        }

        lastAreaAura = IAuraChunk.getAuraInArea(serverLevel,
                worldPosition, NatureAuraMachineConfig.FIELD_RADIUS);
        FieldMode direction = effectiveDirection(lastAreaAura);
        int requested = requestedTransfer(lastAreaAura);
        if (requested <= 0 || energyContainer == null) {
            return 0;
        }
        requested = direction == FieldMode.ABSORB
                ? budgetedStore(requested, true)
                : budgetedDrain(requested, true);
        if (requested <= 0) {
            return 0;
        }
        long unitFe = fePerAura();
        double joulesPerFe = EnergyUnit.FORGE_ENERGY.getConversion();
        int affordable = NatureMachineEnergyMath.affordableFieldAura(requested,
                energyContainer.getEnergy(), unitFe, joulesPerFe);
        if (affordable > 0 && affordable < requested) {
            setReducedEnergyWarning(true);
        }
        requested = Math.min(requested, affordable);
        if (requested <= 0) {
            setNotEnoughEnergyWarning(true);
            return 0;
        }

        BlockPos target = direction == FieldMode.ABSORB
                ? IAuraChunk.getHighestSpot(serverLevel, worldPosition,
                NatureAuraMachineConfig.FIELD_RADIUS, worldPosition)
                : IAuraChunk.getLowestSpot(serverLevel, worldPosition,
                NatureAuraMachineConfig.FIELD_RADIUS, worldPosition);
        if (target == null || serverLevel.getChunkSource().getChunkNow(
                target.getX() >> 4, target.getZ() >> 4) == null) {
            return 0;
        }
        IAuraChunk chunk = IAuraChunk.getAuraChunk(serverLevel, target);
        if (chunk == null || chunk.getType() == null
                || !chunk.getType().isSimilar(worldType)) {
            return 0;
        }
        long reserved = NatureMachineEnergyMath.fieldBatchJoules(requested, unitFe, joulesPerFe);
        if (reserved == Long.MAX_VALUE || energyContainer.extract(reserved, Action.SIMULATE,
                AutomationType.INTERNAL) != reserved) {
            return 0;
        }
        long extracted = energyContainer.extract(reserved, Action.EXECUTE, AutomationType.INTERNAL);
        if (extracted != reserved) {
            refundFieldEnergy(extracted);
            return 0;
        }
        int moved = switch (direction) {
            case ABSORB -> absorbFromWorld(chunk, target, requested);
            case RELEASE -> releaseToWorld(chunk, target, requested);
            case BALANCE -> 0;
        };
        if (moved < 0 || moved > requested) {
            throw new IllegalStateException("Aura field transfer exceeded its reserved amount");
        }
        long actualCost = NatureMachineEnergyMath.fieldBatchJoules(moved, unitFe, joulesPerFe);
        refundFieldEnergy(reserved - actualCost);
        return moved;
    }

    private void refundFieldEnergy(long amount) {
        if (amount > 0 && refundProcessingEnergy(amount) != amount) {
            throw new IllegalStateException("Unable to refund Aura field energy reservation");
        }
    }

    private int requestedTransfer(int areaAura) {
        int limit = NatureAuraMachineConfig.FIELD_TRANSFER;
        if (fieldMode != FieldMode.BALANCE) {
            return limit;
        }
        long difference = (long) areaAura - IAuraChunk.DEFAULT_AURA;
        long magnitude = Math.abs(difference);
        if (magnitude <= NatureAuraMachineConfig.FIELD_BALANCE_DEAD_ZONE) {
            return 0;
        }
        return (int) Math.min(limit, magnitude);
    }

    private FieldMode effectiveDirection(int areaAura) {
        if (fieldMode != FieldMode.BALANCE) {
            return fieldMode;
        }
        return areaAura > IAuraChunk.DEFAULT_AURA
                ? FieldMode.ABSORB : FieldMode.RELEASE;
    }

    private int absorbFromWorld(IAuraChunk chunk, BlockPos target,
                                int request) {
        int accepted = budgetedStore(request, true);
        if (accepted <= 0) {
            return 0;
        }
        int available = chunk.drainAura(target, accepted,
                false, true);
        int planned = Math.min(accepted, Math.max(0, available));
        if (planned <= 0) {
            return 0;
        }
        int drained = chunk.drainAura(target, planned,
                false, false);
        int stored = budgetedStore(Math.max(0, drained), false);
        if (stored < drained) {
            chunk.storeAura(target, drained - stored,
                    false, false);
        }
        return stored;
    }

    private int releaseToWorld(IAuraChunk chunk, BlockPos target,
                               int request) {
        int extractable = budgetedDrain(request, true);
        if (extractable <= 0) {
            return 0;
        }
        int accepted = chunk.storeAura(target, extractable,
                false, true);
        int planned = Math.min(extractable, Math.max(0, accepted));
        if (planned <= 0) {
            return 0;
        }
        int extracted = budgetedDrain(planned, false);
        int stored = chunk.storeAura(target, extracted,
                false, false);
        if (stored < extracted
                && !storeAuraExact(extracted - stored, false)) {
            throw new IllegalStateException(
                    "Unable to roll back Aura field transfer");
        }
        return stored;
    }

    @Override
    protected void onAuraChanged() {
        idleBackoff = NatureAuraMachineConfig.FIELD_INTERVAL;
        nextFieldOperation = Long.MIN_VALUE;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(this::getLastAreaAura,
                value -> lastAreaAura = value));
        container.track(SyncableInt.create(
                () -> fieldMode.ordinal(),
                value -> fieldMode = FieldMode.values()[Math.max(0,
                        Math.min(FieldMode.values().length - 1, value))]));
    }

    @Override
    protected void saveNatureAuraMachineData(
            CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString(FIELD_MODE, fieldMode.name());
        tag.putInt(LAST_AREA_AURA, lastAreaAura);
    }

    @Override
    protected void loadNatureAuraMachineData(
            CompoundTag tag, HolderLookup.Provider registries) {
        try {
            fieldMode = FieldMode.valueOf(tag.getString(FIELD_MODE));
        } catch (IllegalArgumentException ignored) {
            fieldMode = FieldMode.ABSORB;
        }
        lastAreaAura = tag.contains(LAST_AREA_AURA)
                ? tag.getInt(LAST_AREA_AURA) : IAuraChunk.DEFAULT_AURA;
        nextFieldOperation = Long.MIN_VALUE;
        idleBackoff = NatureAuraMachineConfig.FIELD_INTERVAL;
    }
}
