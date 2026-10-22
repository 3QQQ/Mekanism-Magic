package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Optional;

/** Shared closed Aura buffer, redstone gate and bounded generator output. */
public abstract class NatureAuraGeneratorBlockEntity
        extends NatureAuraMachineBlockEntity {
    public static final int CYCLE_AURA_TYPE_BUTTON_ID = 9352;
    private static final String AURA_TYPE_MODE = "nature_aura_generation_type_mode";
    private int selectedAuraTypeMode;
    private int lastGeneratedAura;
    private int nextOutputSide;
    private boolean updatingGenerator;
    private boolean auraOutputTransaction;

    protected NatureAuraGeneratorBlockEntity(
            Holder<Block> block, BlockPos pos, BlockState state) {
        super(block, pos, state,
                NatureAuraMachineConfig.GENERATOR_AURA_CAPACITY,
                NatureAuraMachineConfig.GENERATOR_AURA_TRANSFER,
                AuraSideMode.OUTPUT);
    }

    @Override
    protected final Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return Optional.empty();
    }

    @Override
    public final boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    /** Returns only Aura that was actually committed to the internal buffer. */
    protected abstract int generateAura(ServerLevel serverLevel);

    /** Refreshes live operating limits even while redstone pauses generation. */
    protected void beforeGeneratorTick(ServerLevel serverLevel) {
    }

    public final int getLastGeneratedAura() {
        return lastGeneratedAura;
    }

    /** 0 follows the current world; fixed modes never reinterpret stored Aura. */
    public final int getSelectedAuraTypeMode() {
        return selectedAuraTypeMode;
    }

    public final IAuraType getGenerationAuraType() {
        return switch (selectedAuraTypeMode) {
            case 1 -> NaturesAuraAPI.TYPE_OVERWORLD;
            case 2 -> NaturesAuraAPI.TYPE_NETHER;
            case 3 -> NaturesAuraAPI.TYPE_END;
            default -> level == null ? null : IAuraType.forLevel(level);
        };
    }

    @Override
    protected final IAuraType getOperatingAuraType() {
        return getGenerationAuraType();
    }

    protected boolean hasPendingGeneration() {
        return false;
    }

    public final boolean canCycleAuraTypeMode() {
        return getStoredAura() == 0 && !hasPendingGeneration()
                && !updatingGenerator && !auraOutputTransaction;
    }

    /** Keep the selected type fixed while an optional network callback runs. */
    public final boolean beginAuraOutputTransaction() {
        if (level == null || level.isClientSide() || auraOutputTransaction
                || getGenerationAuraType() == null) return false;
        auraOutputTransaction = true;
        return true;
    }

    public final void endAuraOutputTransaction() {
        auraOutputTransaction = false;
    }

    /**
     * Restores only an unaccepted part of the active, same-type output
     * reservation. Output-only sides remain closed to ordinary insertion.
     */
    public final int refundGeneratedAura(IAuraType type, int amount) {
        if (!(level instanceof ServerLevel) || isRemoved()
                || !auraOutputTransaction || amount <= 0 || type == null
                || isCreativeAuraActive() || !refreshAuraTypeState()) return 0;
        IAuraType current = getGenerationAuraType();
        if (current == null || !current.isSimilar(type)) return 0;
        int restored = Math.min(amount,
                Math.max(0, getAuraCapacity() - getStoredAura()));
        return restored > 0 && storeAuraExact(restored, false)
                ? restored : 0;
    }

    /** Menu ownership/security is checked before this server-only operation. */
    public final boolean cycleAuraTypeMode() {
        if (level == null || level.isClientSide() || !canCycleAuraTypeMode()) {
            return false;
        }
        selectedAuraTypeMode = (selectedAuraTypeMode + 1) % 4;
        refreshAuraTypeState();
        level.invalidateCapabilities(worldPosition);
        setChanged();
        sendUpdatePacket();
        return true;
    }

    @Override
    protected final boolean onUpdateServer() {
        if (updatingGenerator) {
            return false;
        }
        updatingGenerator = true;
        try {
            beginNatureAuraUpdate();
            boolean changed = nativeBaseUpdate();
            clearNativeRecipeWarnings();
            lastGeneratedAura = 0;
            if (!(level instanceof ServerLevel serverLevel)) {
                return finishServerUpdate(changed, false);
            }
            beforeGeneratorTick(serverLevel);
            if (canFunction() && getGenerationAuraType() != null
                    && refreshAuraTypeState()) {
                lastGeneratedAura = Math.max(0, generateAura(serverLevel));
                changed |= lastGeneratedAura > 0;
            }
            // Like native item ejection, moving already stored output does
            // not consume a generation tick or depend on redstone control.
            changed |= ejectAura(serverLevel);
            return finishServerUpdate(changed, lastGeneratedAura > 0);
        } finally {
            updatingGenerator = false;
        }
    }

    private boolean ejectAura(ServerLevel serverLevel) {
        IAuraType type = getGenerationAuraType();
        if (type == null || !refreshAuraTypeState()
                || budgetedDrain(Integer.MAX_VALUE, true) <= 0) {
            return false;
        }
        boolean changed = false;
        Direction[] directions = Direction.values();
        int firstSide = nextOutputSide;
        nextOutputSide = (nextOutputSide + 1) % directions.length;
        for (int offset = 0; offset < directions.length; offset++) {
            Direction side = directions[(firstSide + offset) % directions.length];
            if (!getAuraSideMode(side).allowsOutput()) {
                continue;
            }
            int available = budgetedDrain(Integer.MAX_VALUE, true);
            if (available <= 0) {
                break;
            }
            BlockPos targetPos = worldPosition.relative(side);
            if (!serverLevel.hasChunkAt(targetPos)) {
                continue;
            }
            IAuraContainer target = serverLevel.getCapability(
                    NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                    targetPos, side.getOpposite());
            if (target == null) {
                continue;
            }
            int offered;
            try {
                if (!target.isAcceptableType(type)) {
                    continue;
                }
                offered = Math.max(0, Math.min(available,
                        target.storeAura(available, true)));
            } catch (RuntimeException failure) {
                MekanismMagic.LOGGER.warn(
                        "Aura output target {} rejected simulation at {}",
                        targetPos, worldPosition, failure);
                continue;
            }
            if (!getAuraSideMode(side).allowsOutput()
                    || !refreshAuraTypeState()) {
                continue;
            }
            // Reserve before invoking external code. A reentrant capability
            // cannot extract the same Aura while receiving this transfer.
            int reserved = budgetedDrain(offered, false);
            if (reserved <= 0) {
                continue;
            }
            int accepted;
            try {
                accepted = Math.max(0, Math.min(reserved,
                        target.storeAura(reserved, false)));
            } catch (RuntimeException failure) {
                // A throwing target may already have stored its request.
                // Refunding an unknown commit would duplicate that Aura.
                MekanismMagic.LOGGER.warn(
                        "Aura output target {} threw after reservation at {}",
                        targetPos, worldPosition, failure);
                changed = true;
                continue;
            }
            int refund = reserved - accepted;
            if (refund > 0) {
                // Usually all reserved space is still free. A reentrant
                // target that refills us cannot force storage past capacity.
                storeAuraExact(Math.min(refund,
                        getAuraCapacity() - getStoredAura()), false);
            }
            changed |= accepted > 0;
        }
        return changed;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(this::getSelectedAuraTypeMode,
                value -> selectedAuraTypeMode = validMode(value)));
        container.track(SyncableInt.create(this::getLastGeneratedAura,
                value -> lastGeneratedAura = Math.max(0, value)));
    }

    @Override
    protected void saveNatureAuraMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveNatureAuraMachineData(tag, registries);
        tag.putInt(AURA_TYPE_MODE, selectedAuraTypeMode);
    }

    @Override
    protected void loadNatureAuraMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadNatureAuraMachineData(tag, registries);
        selectedAuraTypeMode = validMode(tag.getInt(AURA_TYPE_MODE));
        refreshAuraTypeState();
    }

    private static int validMode(int mode) {
        return mode >= 0 && mode <= 3 ? mode : 0;
    }
}
