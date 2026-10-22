package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Four independent, non-consuming native essence samples; only FE is spent. */
public final class EssenceCondenserBlockEntity extends NativeMagicMachineBlockEntity implements EssenceFluidBankHost {
    public static final int CHANNEL_COUNT = 4;
    public static final int AUREAL = 0;
    public static final int SOULS = 1;
    public static final int BLOOD = 2;
    public static final int EXPERIENCE = 3;
    public static final int BASE_ENERGY_PER_TICK = MagicMachineEnergyProfiles.MATERIAL_PROCESSING;
    public static final long ENERGY_CAPACITY = 2_000_000L;
    public static final int[] BASE_TICKS = {20, 35, 200, 40};
    public static final int[] ENERGY_PER_TICK = {50, 50, 1_000, 100};
    public static final int[] BASE_CAPACITY = {100_000, 10_000, 30_000, 1_000_000};
    public static final String STATE_TAG = "essence_condenser_machine";
    private static final int SAMPLE_FIRST = 1;
    private static final int[] VESSEL_INPUT_LOGICAL = {8, 9, 12, 13};
    private static final int VESSEL_OUTPUT_FIRST = 18;

    private List<BasicInventorySlot> sampleSlots;
    private List<BasicInventorySlot> vesselInputSlots;
    private List<BasicInventorySlot> vesselOutputSlots;
    private int[] banks;
    private int[] laneProgress;
    private int[] syncedLaneDuration;
    private long[] syncedLaneEnergy;
    private LaneStatus[] laneStatus;
    private ItemStack[] progressSamples;
    private ItemStack[] parsedSamples;
    private HephaestusForge261Bridge.EssenceSample[] parsedValues;
    private long parsedRevision = Long.MIN_VALUE;

    public EssenceCondenserBlockEntity(BlockPos pos, BlockState state) {
        super(ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get().builtInRegistryHolder(), pos, state);
        ensureState();
        if (EssenceFluidPorts.available()) {
            getEjector().setOutputData(getConfig(), TransmissionType.ITEM, TransmissionType.FLUID);
        }
    }

    @Override protected EnumSet<TransmissionType> nativeTransmissions() {
        EnumSet<TransmissionType> types = super.nativeTransmissions();
        if (EssenceFluidPorts.available()) types.add(TransmissionType.FLUID);
        return types;
    }

    @Override protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        ensureState();
        return EssenceFluidPorts.output(this, listener, AUREAL, SOULS, BLOOD, EXPERIENCE);
    }

    @Override protected void createMachineSlots(InventorySlotHelper helper, IContentsListener listener) {
        ensureState();
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            final int fixed = channel;
            sampleSlots.add(registerLogicalSlot(helper, SAMPLE_FIRST + channel, BasicInventorySlot.at(
                    (stack, automation) -> automation == AutomationType.MANUAL,
                    (stack, automation) -> true,
                    stack -> HephaestusForge261Bridge.previewRetainedEssenceSample(stack)
                            .map(sample -> sample.channel() == fixed).orElse(false),
                    listener, 31 + channel * 50, 39)));
        }
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            final int fixed = channel;
            vesselInputSlots.add(registerLogicalSlot(helper, VESSEL_INPUT_LOGICAL[channel], BasicInventorySlot.at(
                    (stack, automation) -> automation == AutomationType.MANUAL,
                    (stack, automation) -> true,
                    stack -> NativeEssenceVessel261Bridge.accepts(stack, fixed),
                    listener, 31 + channel * 50, 112)));
        }
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            final int fixed = channel;
            vesselOutputSlots.add(registerLogicalSlot(helper, VESSEL_OUTPUT_FIRST + channel,
                    BasicInventorySlot.at(
                            (stack, automation) -> automation == AutomationType.MANUAL
                                    || NativeEssenceVessel261Bridge.isFullVesselForChannel(stack, fixed),
                            (stack, automation) -> false, stack -> true,
                            listener, 31 + channel * 50, 132)));
        }
        List<IInventorySlot> inputs = new ArrayList<>(sampleSlots);
        inputs.addAll(vesselInputSlots);
        List<IInventorySlot> outputs = new ArrayList<>(vesselOutputSlots);
        setupNativeItemIO(inputs, outputs, List.of());
    }

    @Override public int getEssenceFluidStored(int channel) { return getStoredEssence(channel); }
    @Override public int getEssenceFluidCapacity(int channel) { return getEssenceCapacity(channel); }
    @Override public int insertEssenceFluid(int channel, int requested, Action action) { return 0; }

    @Override public boolean canOutputEssence(int channel) {
        return validChannel(channel) && !isRemoved();
    }

    @Override public int extractEssenceFluid(int channel, int requested, Action action) {
        ensureState();
        if (!validChannel(channel) || requested <= 0 || action == null) return 0;
        int moved = Math.min(requested, Math.max(0, banks[channel]));
        if (action.execute()) {
            if (!(level instanceof ServerLevel) || isRemoved()) return 0;
            banks[channel] -= moved;
            if (moved > 0) setChanged();
        }
        return moved;
    }

    @Override public int refundExtractedEssence(int channel, int requested, Action action) {
        ensureState();
        if (!validChannel(channel) || requested <= 0 || action == null
                || !(level instanceof ServerLevel) || isRemoved()) return 0;
        int restored = Math.min(requested, Math.max(0,
                getEssenceCapacity(channel) - banks[channel]));
        if (action.execute() && restored > 0) {
            banks[channel] += restored;
            setChanged();
        }
        return restored;
    }

    public IInventorySlot getSampleSlot(int channel) {
        ensureState();
        return validChannel(channel) && sampleSlots.size() == CHANNEL_COUNT
                ? sampleSlots.get(channel) : null;
    }
    public IInventorySlot getVesselInputSlot(int channel) {
        ensureState();
        return validChannel(channel) && vesselInputSlots.size() == CHANNEL_COUNT
                ? vesselInputSlots.get(channel) : null;
    }
    public IInventorySlot getVesselOutputSlot(int channel) {
        ensureState();
        return validChannel(channel) && vesselOutputSlots.size() == CHANNEL_COUNT
                ? vesselOutputSlots.get(channel) : null;
    }
    public int getStoredEssence(int channel) {
        ensureState();
        return validChannel(channel) ? Math.max(0, banks[channel]) : 0;
    }
    public int getEssenceCapacity(int channel) {
        return validChannel(channel) ? Math.max(BASE_CAPACITY[channel], getStoredEssence(channel)) : 0;
    }
    public int getLaneProgress(int channel) {
        ensureState();
        return validChannel(channel) ? laneProgress[channel] : 0;
    }
    public int getLaneDuration(int channel) {
        ensureState();
        if (!validChannel(channel)) return 1;
        return level != null && level.isClientSide ? syncedLaneDuration[channel]
                : Math.max(1, MekanismUtils.getTicks(this, BASE_TICKS[channel]));
    }
    public long getLaneEnergyPerTick(int channel) {
        ensureState();
        if (!validChannel(channel)) return 0;
        return level != null && level.isClientSide ? syncedLaneEnergy[channel]
                : MekanismUtils.getEnergyPerTick(this, ENERGY_PER_TICK[channel]);
    }
    public String getLaneStatusKey(int channel) {
        ensureState();
        LaneStatus value = validChannel(channel) ? laneStatus[channel] : LaneStatus.IDLE;
        return "gui.mekanism_magic.essence_condenser." + value.name().toLowerCase(Locale.ROOT);
    }
    @Override protected boolean onUpdateServer() {
        ensureState();
        boolean changed = nativeBaseUpdate();
        clearNativeRecipeWarnings();
        if (!(level instanceof ServerLevel)) return finishServerUpdate(changed, false);
        boolean active = false;
        long revision = ForbiddenArcanusRecipeCatalog.revision();
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            ItemStack current = sampleSlots.get(channel).getStack();
            if (!ItemStack.isSameItemSameComponents(progressSamples[channel], current)) {
                progressSamples[channel] = current.copyWithCount(1);
                laneProgress[channel] = 0;
                changed = true;
            }
            boolean reparse = revision != parsedRevision
                    || !ItemStack.isSameItemSameComponents(parsedSamples[channel], current);
            if (reparse) {
                var next = HephaestusForge261Bridge.previewRetainedEssenceSample(current).orElse(null);
                if (next == null || next.channel() != channel) next = null;
                if (laneProgress[channel] != 0
                        && (next == null || parsedRevision != Long.MIN_VALUE
                        && !java.util.Objects.equals(parsedValues[channel], next))) {
                    laneProgress[channel] = 0;
                    changed = true;
                }
                parsedSamples[channel] = current.copyWithCount(1);
                parsedValues[channel] = next;
            }
            var sample = parsedValues[channel];
            if (current.isEmpty()) {
                laneStatus[channel] = LaneStatus.NO_SAMPLE;
                continue;
            }
            if (sample == null) {
                laneStatus[channel] = LaneStatus.INVALID_SAMPLE;
                continue;
            }
            if (getEssenceCapacity(channel) - (long) banks[channel] < sample.amount()) {
                laneStatus[channel] = LaneStatus.BANK_FULL;
                continue;
            }
            if (!canFunction()) {
                laneStatus[channel] = LaneStatus.PAUSED;
                continue;
            }
            long usage = MekanismUtils.getEnergyPerTick(this, ENERGY_PER_TICK[channel]);
            if (energyContainer == null || energyContainer.extract(usage, Action.SIMULATE,
                    AutomationType.INTERNAL) != usage) {
                setNotEnoughEnergyWarning(true);
                laneStatus[channel] = LaneStatus.NO_ENERGY;
                continue;
            }
            long drawn = energyContainer.extract(usage, Action.EXECUTE, AutomationType.INTERNAL);
            if (drawn != usage) {
                refundProcessingEnergy(drawn);
                setNotEnoughEnergyWarning(true);
                laneStatus[channel] = LaneStatus.NO_ENERGY;
                continue;
            }
            laneProgress[channel]++;
            laneStatus[channel] = LaneStatus.RUNNING;
            active = true;
            changed = true;
            if (laneProgress[channel] >= getLaneDuration(channel)) {
                // Admission happened before FE debit. The sample itself is
                // never reduced, damaged, or multiplied by its stack count.
                banks[channel] = Math.addExact(banks[channel], sample.amount());
                laneProgress[channel] = 0;
            }
        }
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            BasicInventorySlot output = vesselOutputSlots.get(channel);
            ItemStack working = output.getStack();
            if (!working.isEmpty()) {
                if (working.getCount() != 1) continue;
                // Continue the same owned vessel instead of producing a stream
                // of one-point jars. Partial vessels are manually recoverable;
                // only full ones are externally extractable.
                var topUp = NativeEssenceVessel261Bridge.previewFill(working, channel, banks[channel]);
                if (topUp.isEmpty()) continue;
                output.setStack(topUp.get().result());
                banks[channel] -= topUp.get().debitedPoints();
                changed = true;
                continue;
            }
            var firstFill = NativeEssenceVessel261Bridge.previewFill(
                    vesselInputSlots.get(channel).getStack(), channel, banks[channel]);
            if (firstFill.isEmpty()) continue;
            ItemStack remainder = vesselInputSlots.get(channel).getStack().copy();
            remainder.shrink(1);
            output.setStack(firstFill.get().result());
            vesselInputSlots.get(channel).setStack(remainder);
            banks[channel] -= firstFill.get().debitedPoints();
            changed = true;
        }
        parsedRevision = revision;
        progress = Arrays.stream(laneProgress).max().orElse(0);
        progressRequired = BASE_TICKS[BLOOD];
        activeRecipe = active ? "mekanism_magic:essence_condenser" : "";
        if (changed) setChanged();
        return finishServerUpdate(changed, active);
    }

    @Override public void addContainerTrackers(MekanismContainer container) {
        ensureState();
        super.addContainerTrackers(container);
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            final int fixed = channel;
            container.track(SyncableInt.create(() -> banks[fixed], value -> banks[fixed] = Math.max(0, value)));
            container.track(SyncableInt.create(() -> laneProgress[fixed],
                    value -> laneProgress[fixed] = Math.max(0, value)));
            container.track(SyncableInt.create(() -> Math.max(1,
                            MekanismUtils.getTicks(this, BASE_TICKS[fixed])),
                    value -> syncedLaneDuration[fixed] = Math.max(1, value)));
            container.track(SyncableLong.create(() -> Math.max(0L,
                            MekanismUtils.getEnergyPerTick(this, ENERGY_PER_TICK[fixed])),
                    value -> syncedLaneEnergy[fixed] = Math.max(0L, value)));
            container.track(SyncableInt.create(() -> laneStatus[fixed].ordinal(), value ->
                    laneStatus[fixed] = LaneStatus.values()[Math.clamp(value, 0, LaneStatus.values().length - 1)]));
        }
    }

    @Override protected void saveNativeMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        ensureState();
        CompoundTag data = new CompoundTag();
        data.putIntArray("banks", banks.clone());
        data.putIntArray("lane_progress", laneProgress.clone());
        ListTag samples = new ListTag();
        for (ItemStack stack : progressSamples) samples.add(saveCountedItemStack(stack, registries));
        data.put("progress_samples", samples);
        tag.put(STATE_TAG, data);
    }

    @Override protected void loadNativeMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        ensureState();
        Arrays.fill(banks, 0);
        Arrays.fill(laneProgress, 0);
        Arrays.fill(laneStatus, LaneStatus.IDLE);
        Arrays.fill(progressSamples, ItemStack.EMPTY);
        Arrays.fill(parsedSamples, ItemStack.EMPTY);
        Arrays.fill(parsedValues, null);
        parsedRevision = Long.MIN_VALUE;
        if (!tag.contains(STATE_TAG)) {
            progress = 0;
            activeRecipe = "";
            return;
        }
        CompoundTag data = tag.getCompound(STATE_TAG);
        int[] loadedBanks = data.getIntArray("banks");
        int[] loadedProgress = data.getIntArray("lane_progress");
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) {
            banks[channel] = channel < loadedBanks.length ? Math.max(0, loadedBanks[channel]) : 0;
            laneProgress[channel] = channel < loadedProgress.length ? Math.max(0, loadedProgress[channel]) : 0;
        }
        ListTag samples = data.getList("progress_samples", Tag.TAG_COMPOUND);
        for (int channel = 0; channel < Math.min(CHANNEL_COUNT, samples.size()); channel++) {
            progressSamples[channel] = loadCountedItemStack(samples.getCompound(channel), registries).copyWithCount(1);
        }
        progress = Arrays.stream(laneProgress).max().orElse(0);
        activeRecipe = "";
    }

    private static boolean validChannel(int channel) { return channel >= 0 && channel < CHANNEL_COUNT; }
    private void ensureState() {
        if (banks != null) return;
        sampleSlots = new ArrayList<>(CHANNEL_COUNT);
        vesselInputSlots = new ArrayList<>(CHANNEL_COUNT);
        vesselOutputSlots = new ArrayList<>(CHANNEL_COUNT);
        banks = new int[CHANNEL_COUNT];
        laneProgress = new int[CHANNEL_COUNT];
        syncedLaneDuration = BASE_TICKS.clone();
        syncedLaneEnergy = new long[CHANNEL_COUNT];
        for (int channel = 0; channel < CHANNEL_COUNT; channel++) syncedLaneEnergy[channel] = ENERGY_PER_TICK[channel];
        laneStatus = new LaneStatus[CHANNEL_COUNT];
        progressSamples = new ItemStack[CHANNEL_COUNT];
        parsedSamples = new ItemStack[CHANNEL_COUNT];
        parsedValues = new HephaestusForge261Bridge.EssenceSample[CHANNEL_COUNT];
        Arrays.fill(laneStatus, LaneStatus.IDLE);
        Arrays.fill(progressSamples, ItemStack.EMPTY);
        Arrays.fill(parsedSamples, ItemStack.EMPTY);
    }
    @Override protected Optional<MachineRecipeResult> findRecipe(ItemStackHandler inventory) { return Optional.empty(); }
    @Override protected int baseEnergyPerTick() { return BASE_ENERGY_PER_TICK; }
    @Override protected int energySlotX() { return 8; }
    @Override protected int energySlotY() { return 45; }
    @Override public boolean mekanismMagicSupportsPatternAutomation() { return false; }
    @Override public boolean mekanismMagicIsBusy() {
        ensureState();
        return Arrays.stream(laneProgress).anyMatch(value -> value > 0);
    }

    private enum LaneStatus { IDLE, NO_SAMPLE, INVALID_SAMPLE, BANK_FULL, NO_ENERGY, RUNNING, PAUSED }
}
