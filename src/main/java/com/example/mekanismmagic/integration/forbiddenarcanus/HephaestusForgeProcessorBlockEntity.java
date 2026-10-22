package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.ItemInputGroup;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.EnumSet;

/** A self-contained native-tier forge; rituals pay once and upgrade this machine. */
public final class HephaestusForgeProcessorBlockEntity extends NativeMagicMachineBlockEntity
        implements EssenceFluidBankHost {
    public static final int BASE_ENERGY_PER_TICK = MagicMachineEnergyProfiles.ADVANCED_RITUAL;
    public static final long ENERGY_CAPACITY = 2_000_000L;
    public static final int AUXILIARY_SLOTS = 8;
    public static final int ESSENCE_INPUT_SLOT = 9;
    public static final int LEGACY_CORE_SLOT = 10;
    public static final int LEGACY_STARTER_SLOT = 11;
    public static final int RETURN_SLOT = 17;
    public static final int ENHANCER_SLOT_START = 24;
    public static final int ENHANCER_SLOTS = 4;
    public static final int WORKING_ESSENCE_SLOT = 28;
    static final String STATE_TAG = "hephaestus_forge_machine";
    private static final int OUTPUT_RETRY_TICKS = 5;

    // Inventory construction is invoked by the superclass constructor.
    private List<InputInventorySlot> auxiliarySlots;
    private List<BasicInventorySlot> enhancerSlots;
    private BasicInventorySlot essenceInputSlot;
    private BasicInventorySlot legacyCoreSlot;
    private BasicInventorySlot legacyStarterSlot;
    private BasicInventorySlot workingEssenceSlot;
    private OutputInventorySlot returnSlot;
    private final HephaestusRecipeSelector selector = new HephaestusRecipeSelector();
    /** Stable channel order: Aureal, Souls, Blood, Experience. */
    private final int[] essences = new int[4];
    private final int[] capacities = new int[4];
    private final List<ItemStack> pendingReturns = new ArrayList<>();
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private ItemStack paidOutput = ItemStack.EMPTY;
    private int pendingUpgradeTier;
    private int paidUpgradeTier;
    private int paidSourceTier;
    private boolean migrateLegacyPaidContext;
    private boolean migrateLegacyTier;
    private List<ItemStack> paidEnhancers = List.of();
    private String paidContext = "";
    private String paidRecipe = "";
    private String paidCircle = "";
    private int paidDuration = 1;
    private int forgeTier = 1;
    private int outputRetryTicks;
    private long lastCatalogRevision = -1;
    private long contextCatalogRevision = Long.MIN_VALUE;
    private int contextTier;
    private List<ItemStack> contextEnhancers = List.of();
    private String resolvedContext = "";
    private long contextResolutions;
    private boolean outputBlocked;
    private boolean contextMismatch;
    private boolean syncedPaid;
    private boolean syncedPaused;
    private int syncedUpgradeTarget;
    private boolean lastVisualPaid;
    private boolean lastVisualPaused;
    private String lastVisualCircle = "";
    private Status status = Status.IDLE;

    public HephaestusForgeProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected EnumSet<TransmissionType> nativeTransmissions() {
        EnumSet<TransmissionType> types = super.nativeTransmissions();
        if (EssenceFluidPorts.available()) types.add(TransmissionType.FLUID);
        return types;
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return EssenceFluidPorts.input(this, listener, 0, 1, 2, 3);
    }

    @Override
    public int getEssenceFluidStored(int channel) {
        return essences == null || channel < 0 || channel >= essences.length
                ? 0 : essences[channel];
    }

    @Override
    public int getEssenceFluidCapacity(int channel) {
        return channel < 0 || channel >= 4 ? 0
                : HephaestusForge261Bridge.capacities(getForgeTier())[channel];
    }

    @Override
    public int insertEssenceFluid(int channel, int requested, Action action) {
        if (!(level instanceof ServerLevel) || channel < 0 || channel >= 4
                || requested <= 0 || isCreativeMagic() || essences == null
                || action == null || isRemoved()) return 0;
        // SIMULATE is strictly read-only. In particular, do not call
        // refreshForgeState here: it mutates tier/capacity presentation state.
        int room = Math.max(0, getEssenceFluidCapacity(channel)
                - essences[channel]);
        int accepted = Math.min(requested, room);
        if (accepted > 0 && action.execute()) {
            essences[channel] += accepted;
            setChanged();
        }
        return accepted;
    }

    @Override
    public int extractEssenceFluid(int channel, int requested, Action action) {
        // The forge is an input consumer, never an essence fluid source.
        return 0;
    }

    @Override
    public boolean canInputEssence(int channel) {
        return channel >= 0 && channel < essences.length && !isRemoved() && !isCreativeMagic();
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper, IContentsListener listener) {
        inputSlot = registerLogicalSlot(helper, 0, InputInventorySlot.at(
                stack -> isMaterial(stack, true), listener, 80, 25));
        auxiliarySlots = new ArrayList<>(AUXILIARY_SLOTS);
        for (int index = 0; index < AUXILIARY_SLOTS; index++) {
            auxiliarySlots.add(registerLogicalSlot(helper, index + 1, InputInventorySlot.at(
                    stack -> isMaterial(stack, false), listener, 26 + index % 4 * 18, 51 + index / 4 * 18)));
        }
        essenceInputSlot = registerLogicalSlot(helper, ESSENCE_INPUT_SLOT, BasicInventorySlot.at(
                (stack, automation) -> automation != AutomationType.EXTERNAL,
                (stack, automation) -> true, HephaestusForge261Bridge::isEssenceInput, listener, 116, 98));
        // Preserve old physical storage indices only. These retired inputs
        // have no menu slot, insertion path or crafting role.
        legacyCoreSlot = registerLogicalSlot(helper, LEGACY_CORE_SLOT, new LegacyRecoverySlot(listener));
        legacyStarterSlot = registerLogicalSlot(helper, LEGACY_STARTER_SLOT, new LegacyRecoverySlot(listener));
        enhancerSlots = new ArrayList<>(ENHANCER_SLOTS);
        for (int index = 0; index < ENHANCER_SLOTS; index++) {
            enhancerSlots.add(registerLogicalSlot(helper, ENHANCER_SLOT_START + index, BasicInventorySlot.at(
                    (stack, automation) -> automation != AutomationType.EXTERNAL,
                    (stack, automation) -> true, stack -> level != null && ForbiddenArcanus261Bridge.isEnhancerItem(
                            level.registryAccess(), stack), listener, 26 + index * 18, 98)));
        }
        // Only the program may insert the separated single container. A player
        // may recover it, but no capability or automatic ejector may take it.
        workingEssenceSlot = registerLogicalSlot(helper, WORKING_ESSENCE_SLOT, BasicInventorySlot.at(
                (stack, automation) -> automation != AutomationType.EXTERNAL,
                (stack, automation) -> automation == AutomationType.INTERNAL,
                stack -> true, listener, 134, 98));
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT, OutputInventorySlot.at(listener, 134, 51));
        returnSlot = registerLogicalSlot(helper, RETURN_SLOT, OutputInventorySlot.at(listener, 152, 98));
        List<IInventorySlot> materials = new ArrayList<>();
        materials.add(inputSlot);
        materials.addAll(auxiliarySlots);
        List<IInventorySlot> extras = new ArrayList<>(List.of(essenceInputSlot));
        extras.addAll(enhancerSlots);
        var config = setupNativeItemIO(materials, List.of(outputSlot, returnSlot), extras);
        addNativeItemSlotInfo(config, DataType.INPUT_1, true, false, List.of(inputSlot));
        addNativeItemSlotInfo(config, DataType.INPUT_2, true, false, auxiliarySlots);
        addNativeItemSlotInfo(config, DataType.OUTPUT_1, false, true, List.of(outputSlot));
        addNativeItemSlotInfo(config, DataType.OUTPUT_2, false, true, List.of(returnSlot));
    }

    private boolean isMaterial(ItemStack stack, boolean main) {
        if (!(level instanceof ServerLevel server)) {
            return true;
        }
        return main ? HephaestusForge261Bridge.isRitualMainInput(server.getServer(), stack)
                : HephaestusForge261Bridge.isRitualAuxInput(server.getServer(), stack);
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = nativeBaseUpdate();
        clearNativeRecipeWarnings();
        outputBlocked = false;
        contextMismatch = false;
        if (!(level instanceof ServerLevel server)) {
            return finishForgeUpdate(changed, false);
        }
        changed |= recoverLegacyInputs();
        refreshForgeState();
        if (migrateLegacyPaidContext && hasPaidJob()) {
            paidContext = HephaestusForge261Bridge.currentContextSignature(server,
                    paidSourceTier, paidEnhancers.isEmpty() ? enhancerStacks() : paddedEnhancers(paidEnhancers));
            migrateLegacyPaidContext = false;
            changed = true;
        }
        long revision = ForbiddenArcanusRecipeCatalog.snapshot(server.getServer()).revision();
        if (lastCatalogRevision != revision) {
            selector.invalidate();
            if (!hasPaidJob()) {
                changed |= resetUnpaidWork();
            }
            lastCatalogRevision = revision;
        }
        if (hasPendingOutput()) {
            if (outputRetryTicks-- <= 0) {
                changed |= flushPending();
                outputRetryTicks = hasPendingOutput() ? OUTPUT_RETRY_TICKS : 0;
            }
            outputBlocked = hasPendingOutput();
            status = outputBlocked ? Status.OUTPUT_BLOCKED : Status.IDLE;
            // A delivery tick never begins or pays for another ritual.
            return finishForgeUpdate(changed, !outputBlocked && canKeepActive(server));
        }
        if (!canFunction()) {
            status = Status.IDLE;
            return finishForgeUpdate(changed, false);
        }
        if (hasPaidJob()) {
            return advancePaidJob(server, changed, true);
        }
        // Loading explicit essence items is independent of ritual payment.
        // Creative mode never imports resources into these independent banks.
        if (!isCreativeMagic()) {
            changed |= inputEssence(server);
            if (hasPendingOutput()) {
                status = Status.OUTPUT_BLOCKED;
                return finishForgeUpdate(changed, false);
            }
        }
        Optional<HephaestusRecipeSelector.Selection> found = select(server);
        if (found.isEmpty()) {
            status = hasAnyRecipeInput() ? Status.NO_RECIPE : Status.IDLE;
            setInputDoesntProduceOutputWarning(hasAnyRecipeInput());
            changed |= resetUnpaidWork();
            return finishForgeUpdate(changed, false);
        }
        var selection = found.get();
        activeRecipe = selection.signature();
        if (!selection.upgradesForge() && !canFit(outputSlot, selection.resultPreview())) {
            outputBlocked = true;
            status = Status.OUTPUT_BLOCKED;
            return finishForgeUpdate(changed, false);
        }
        if (!hasProcessingEnergy()) {
            setNotEnoughEnergyWarning(true);
            status = Status.NO_ENERGY;
            return finishForgeUpdate(changed, false);
        }
        if (!HephaestusForge261Bridge.canPay(selection, essences, isCreativeMagic())) {
            status = Status.MISSING_ESSENCE;
            return finishForgeUpdate(changed, false);
        }
        if (!hasAllMaterialAmounts(selection.inputs())) {
            selector.invalidate();
            status = Status.NO_RECIPE;
            return finishForgeUpdate(changed, false);
        }
        var prepared = HephaestusForge261Bridge.prepareJob(server, selection, inputSlot.getStack(),
                auxiliaryStacks(), forgeTier, enhancerStacks(),
                essences.clone(), isCreativeMagic());
        if (prepared.isEmpty()) {
            selector.invalidate();
            status = Status.NO_RECIPE;
            return finishForgeUpdate(changed, false);
        }
        var job = prepared.get();
        int[] debit = job.debit();
        ItemStack result = job.result();
        if ((!job.upgradesForge() && result.isEmpty()) || debit.length != essences.length || !validDebit(debit)
                || job.upgradeTier() != selection.upgradeTier()
                || job.upgradesForge() && (!result.isEmpty()
                || job.upgradeTier() != forgeTier + 1 || job.upgradeTier() > 5)) {
            throw new IllegalStateException("Native Hephaestus payment returned an invalid transaction");
        }
        // Preparation changes copies only. All owned slots and four balances
        // are committed together on the server thread after full validation.
        paidOutput = result.copy();
        paidUpgradeTier = job.upgradeTier();
        paidSourceTier = forgeTier;
        paidDuration = Math.max(1, job.duration());
        paidContext = job.contextSignature();
        paidRecipe = selection.id().toString();
        paidCircle = selection.view().magicCircle().toString();
        paidEnhancers = enhancerStacks().subList(0, HephaestusForge261Bridge.allowedEnhancerCount(forgeTier))
                .stream().map(stack -> stack.copyWithCount(1)).toList();
        for (InputUse use : selection.inputs()) {
            consumeOwnedItems(materialSlot(use.slot()), use.count());
        }
        for (int channel = 0; channel < essences.length; channel++) {
            essences[channel] -= debit[channel];
        }
        progress = 0;
        progressRequired = Math.max(1, MekanismUtils.getTicks(this, paidDuration));
        activeRecipe = paidRecipe;
        selector.invalidate();
        setChanged();
        changed = true;
        if (hasPendingOutput()) {
            status = Status.OUTPUT_BLOCKED;
            return finishForgeUpdate(changed, false);
        }
        // This tick already attempted its one native essence transfer before
        // payment; a newly paid job must not drain a storage item twice.
        return advancePaidJob(server, changed, false);
    }

    private boolean advancePaidJob(ServerLevel server, boolean changed, boolean allowEssenceInput) {
        if (forgeTier != paidSourceTier || !paidContext.equals(resolvePaidContext(server))) {
            contextMismatch = true;
            status = Status.CONTEXT_CHANGED;
            return finishForgeUpdate(changed, false);
        }
        if (paidUpgradeTier == 0 && !canFit(outputSlot, paidOutput)) {
            outputBlocked = true;
            status = Status.OUTPUT_BLOCKED;
            return finishForgeUpdate(changed, false);
        }
        if (!hasProcessingEnergy()) {
            setNotEnoughEnergyWarning(true);
            status = Status.NO_ENERGY;
            return finishForgeUpdate(changed, false);
        }
        if (allowEssenceInput && !isCreativeMagic()) {
            changed |= inputEssence(server);
            if (hasPendingOutput()) {
                outputBlocked = true;
                status = Status.OUTPUT_BLOCKED;
                return finishForgeUpdate(changed, false);
            }
        }
        // The job's four-channel debit, result and upgrade target were fixed
        // at startup. Later input only refills the bank for future payments.
        energyContainer.extract(processingEnergy(), Action.EXECUTE, AutomationType.INTERNAL);
        progressRequired = Math.max(1, MekanismUtils.getTicks(this, paidDuration));
        progress++;
        status = paidUpgradeTier > 0 ? Status.UPGRADING : Status.PAID;
        mekanismMagicRecipeItemDisplay().update(List.of(new RecipeItemDisplayState.Entry(
                0, ItemStack.EMPTY, RecipeItemDisplayState.displayCopy(paidOutput))));
        if (progress >= progressRequired) {
            if (paidUpgradeTier > 0) {
                pendingUpgradeTier = paidUpgradeTier;
            } else {
                pendingOutput = paidOutput;
            }
            clearPaidJob();
            applyPendingUpgrade();
            progress = 0;
            activeRecipe = "";
            outputRetryTicks = 0;
        }
        setChanged();
        return finishForgeUpdate(true, true);
    }

    private boolean inputEssence(ServerLevel server) {
        IInventorySlot source = workingEssenceSlot.isEmpty() ? essenceInputSlot : workingEssenceSlot;
        if (source.isEmpty() || source == workingEssenceSlot && source.getCount() != 1) {
            return false;
        }
        int[] room = new int[4];
        for (int channel = 0; channel < room.length; channel++) {
            // Downgrades preserve balances; a full channel refuses further
            // input but qualifying rituals may still consume that balance.
            room[channel] = Math.max(0, capacities[channel] - essences[channel]);
        }
        var prepared = HephaestusForge261Bridge.prepareEssenceInput(server, source.getStack().copyWithCount(1), room);
        if (prepared.isEmpty()) {
            return false;
        }
        var transfer = prepared.get();
        int channel = transfer.channel();
        if (channel < 0 || channel >= essences.length || transfer.amount() <= 0 || transfer.amount() > room[channel]) {
            throw new IllegalStateException("Native essence transfer exceeded the offered channel capacity");
        }
        ItemStack remainder = transfer.remainder();
        consumeOwnedItems(source, 1);
        essences[channel] += transfer.amount();
        if (HephaestusForge261Bridge.isEssenceInput(remainder)) {
            workingEssenceSlot.setStack(remainder.copy());
        } else {
            workingEssenceSlot.setEmpty();
            enqueueReturn(remainder);
        }
        setChanged();
        return true;
    }

    private void refreshForgeState() {
        forgeTier = validTier(forgeTier);
        int[] actual = HephaestusForge261Bridge.capacities(forgeTier);
        for (int channel = 0; channel < capacities.length; channel++) {
            capacities[channel] = channel < actual.length ? Math.max(0, actual[channel]) : 0;
        }
    }

    private boolean finishForgeUpdate(boolean changed, boolean active) {
        boolean result = finishServerUpdate(changed, active);
        if (level instanceof ServerLevel) {
            boolean paid = hasPaidJob();
            boolean paused = paid && !active;
            String circle = paid ? paidCircle : "";
            if (paid != lastVisualPaid || paused != lastVisualPaused || !circle.equals(lastVisualCircle)) {
                lastVisualPaid = paid;
                lastVisualPaused = paused;
                lastVisualCircle = circle;
                sendUpdatePacket();
            }
        }
        return result;
    }

    private boolean hasAllMaterialAmounts(List<InputUse> uses) {
        long[] total = new long[AUXILIARY_SLOTS + 1];
        for (InputUse use : uses) {
            if (use.slot() < 0 || use.slot() > AUXILIARY_SLOTS) {
                return false;
            }
            total[use.slot()] += use.count();
        }
        for (int slot = 0; slot < total.length; slot++) {
            if (total[slot] > materialSlot(slot).getCount()) {
                return false;
            }
        }
        return total[0] == 1;
    }

    private static void consumeOwnedItems(IInventorySlot slot, int amount) {
        if (amount <= 0 || amount > slot.getCount()) {
            throw new IllegalStateException("Forge payment exceeds its reserved input");
        }
        // Mekanism shrinkStack -> setStackSize clamps the remainder to the
        // CURRENT item limit. Its persistence deliberately supports oversized
        // stacks, so an old/modded count must not be truncated during payment.
        // This does not loosen external insertion limits; it only conserves
        // already-owned inventory while consuming the exact reserved quantity.
        ItemStack remaining = slot.getStack().copy();
        remaining.shrink(amount);
        slot.setStack(remaining);
    }

    private boolean validDebit(int[] debit) {
        for (int channel = 0; channel < debit.length; channel++) {
            if (debit[channel] < 0 || debit[channel] > essences[channel]) {
                return false;
            }
        }
        return true;
    }

    private boolean canKeepActive(ServerLevel server) {
        if (!getActive() || !canFunction() || !hasProcessingEnergy()) {
            return false;
        }
        if (hasPaidJob()) {
            return paidSourceTier == forgeTier && paidContext.equals(resolvePaidContext(server))
                    && (paidUpgradeTier > 0 || canFit(outputSlot, paidOutput));
        }
        var next = select(server);
        return next.isPresent() && (next.get().upgradesForge() || canFit(outputSlot, next.get().resultPreview()))
                && HephaestusForge261Bridge.canPay(next.get(), essences, isCreativeMagic());
    }

    private Optional<HephaestusRecipeSelector.Selection> select(ServerLevel server) {
        return selector.select(server, inputSlot.getStack(), auxiliaryStacks(), forgeTier,
                enhancerStacks(), isCreativeMagic());
    }

    /** Native holder resolution and JSON hashing only run when qualification changes. */
    private String resolvePaidContext(ServerLevel server) {
        long revision = ForbiddenArcanusRecipeCatalog.snapshot(server.getServer()).revision();
        int enabled = HephaestusForge261Bridge.allowedEnhancerCount(forgeTier);
        boolean unchanged = contextCatalogRevision == revision
                && contextTier == forgeTier
                && contextEnhancers.size() == enabled;
        for (int slot = 0; unchanged && slot < enabled; slot++) {
            unchanged = ItemStack.isSameItemSameComponents(contextEnhancers.get(slot), enhancerSlots.get(slot).getStack());
        }
        if (unchanged) {
            return resolvedContext;
        }
        resolvedContext = HephaestusForge261Bridge.currentContextSignature(server, forgeTier, enhancerStacks());
        contextCatalogRevision = revision;
        contextTier = forgeTier;
        List<ItemStack> snapshots = new ArrayList<>(enabled);
        for (int slot = 0; slot < enabled; slot++) {
            snapshots.add(enhancerSlots.get(slot).getStack().copyWithCount(1));
        }
        contextEnhancers = List.copyOf(snapshots);
        contextResolutions++;
        return resolvedContext;
    }

    private void invalidatePaidContext() {
        contextCatalogRevision = Long.MIN_VALUE;
        contextTier = 0;
        contextEnhancers = List.of();
        resolvedContext = "";
    }

    long contextResolutionCount() {
        return contextResolutions;
    }

    @Override
    protected void onNativeMachineLoaded() {
        super.onNativeMachineLoaded();
        invalidatePaidContext();
    }

    @Override
    protected void onNativeMachineRemoved() {
        super.onNativeMachineRemoved();
        invalidatePaidContext();
    }

    @Override
    protected void onNativeMachineRevived() {
        super.onNativeMachineRevived();
        invalidatePaidContext();
    }

    private long processingEnergy() {
        return MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
    }

    private boolean hasProcessingEnergy() {
        return energyContainer != null && energyContainer.getEnergy() >= processingEnergy();
    }

    private IInventorySlot materialSlot(int logicalSlot) {
        return logicalSlot == 0 ? inputSlot : auxiliarySlots.get(logicalSlot - 1);
    }

    private List<ItemStack> auxiliaryStacks() {
        return auxiliarySlots.stream().map(IInventorySlot::getStack).toList();
    }

    private List<ItemStack> enhancerStacks() {
        return enhancerSlots.stream().map(IInventorySlot::getStack).toList();
    }

    private void enqueueReturn(ItemStack stack) {
        if (!stack.isEmpty()) {
            pendingReturns.add(stack.copy());
            outputRetryTicks = 0;
        }
    }

    private boolean flushPending() {
        boolean changed = applyPendingUpgrade();
        if (!pendingOutput.isEmpty()) {
            int moved = insertPending(outputSlot, pendingOutput);
            pendingOutput.shrink(moved);
            changed |= moved > 0;
        }
        if (!pendingReturns.isEmpty()) {
            ItemStack stack = pendingReturns.getFirst();
            int moved = insertPending(returnSlot, stack);
            stack.shrink(moved);
            if (stack.isEmpty()) {
                pendingReturns.removeFirst();
            }
            changed |= moved > 0;
        }
        if (changed) {
            setChanged();
        }
        return changed;
    }

    private static boolean canFit(IInventorySlot slot, ItemStack result) {
        ItemStack current = slot.getStack();
        return !result.isEmpty() && (current.isEmpty() || ItemStack.isSameItemSameComponents(current, result)
                && (long) current.getCount() + result.getCount() <= Math.min(current.getMaxStackSize(), slot.getLimit(current)));
    }

    private static int insertPending(IInventorySlot slot, ItemStack pending) {
        ItemStack current = slot.getStack();
        if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, pending)) {
            return 0;
        }
        int moved = Math.min(pending.getCount(), Math.max(0,
                Math.min(slot.getLimit(pending), pending.getMaxStackSize()) - current.getCount()));
        if (moved > 0) {
            slot.setStack(pending.copyWithCount(current.getCount() + moved));
        }
        return moved;
    }

    private boolean resetUnpaidWork() {
        boolean changed = progress != 0 || !activeRecipe.isEmpty();
        progress = 0;
        progressRequired = 1;
        activeRecipe = "";
        return mekanismMagicRecipeItemDisplay().clear() || changed;
    }

    private void clearPaidJob() {
        paidOutput = ItemStack.EMPTY;
        paidUpgradeTier = 0;
        paidSourceTier = 0;
        migrateLegacyPaidContext = false;
        paidEnhancers = List.of();
        paidDuration = 1;
        paidContext = "";
        paidRecipe = "";
        paidCircle = "";
    }

    private static int validTier(int tier) {
        return Math.max(1, Math.min(5, tier));
    }

    private static int legacyTier(ItemStack core) {
        int tier = HephaestusForge261Bridge.forgeTier(core);
        return tier >= 1 && tier <= 5 ? tier : 0;
    }

    private static List<ItemStack> paddedEnhancers(List<ItemStack> saved) {
        List<ItemStack> result = new ArrayList<>(ENHANCER_SLOTS);
        for (int index = 0; index < ENHANCER_SLOTS; index++) {
            result.add(index < saved.size() ? saved.get(index) : ItemStack.EMPTY);
        }
        return result;
    }

    private boolean applyPendingUpgrade() {
        if (pendingUpgradeTier == 0) return false;
        if (pendingUpgradeTier == forgeTier + 1 && pendingUpgradeTier <= 5) {
            forgeTier = pendingUpgradeTier;
            pendingUpgradeTier = 0;
            refreshForgeState();
            selector.invalidate();
            invalidatePaidContext();
            setChanged();
            return true;
        }
        if (pendingUpgradeTier <= forgeTier) {
            // A replayed completion receipt can never increment twice.
            pendingUpgradeTier = 0;
            setChanged();
            return true;
        }
        return false;
    }

    /** One-way recovery only; old physical inputs are not runtime machine roles. */
    private boolean recoverLegacyInputs() {
        boolean changed = false;
        if (legacyCoreSlot != null && !legacyCoreSlot.isEmpty()) {
            if (migrateLegacyTier && !hasPaidJob()) {
                forgeTier = Math.max(getForgeTier(),
                        legacyTier(legacyCoreSlot.getStack()));
            }
            enqueueReturn(legacyCoreSlot.getStack());
            legacyCoreSlot.setEmpty();
            changed = true;
        }
        if (legacyStarterSlot != null && !legacyStarterSlot.isEmpty()) {
            enqueueReturn(legacyStarterSlot.getStack());
            legacyStarterSlot.setEmpty();
            changed = true;
        }
        migrateLegacyTier = false;
        if (changed) setChanged();
        return changed;
    }

    private static final class LegacyRecoverySlot extends BasicInventorySlot {
        LegacyRecoverySlot(IContentsListener listener) {
            super((ItemStack stack, AutomationType automation) -> false,
                    (ItemStack stack, AutomationType automation) -> false,
                    stack -> false, listener, 0, 0);
        }

        @Override
        public mekanism.common.inventory.container.slot.InventoryContainerSlot createContainerSlot() {
            return null;
        }
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(ItemStackHandler inventory) {
        return Optional.empty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return BASE_ENERGY_PER_TICK;
    }

    @Override
    protected int energySlotX() {
        return 8;
    }

    @Override
    protected int energySlotY() {
        return 25;
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    @Override
    public Optional<List<ItemInputGroup>> mekanismMagicPlanExternalItemInputs(
            List<PatternStack> inputs, List<PatternStack> outputs) {
        // A plain AE provider has no way to route main and auxiliary items by
        // their intended role. Plan a single complete ritual against copies,
        // then give each role its own physical destination. Existing materials
        // are deliberately not used as part of somebody else's AE order.
        if (!(level instanceof ServerLevel server) || hasPaidJob()
                || hasPendingOutput() || !inputSlot.isEmpty()
                || auxiliarySlots.stream().anyMatch(slot -> !slot.isEmpty())) {
            return Optional.empty();
        }
        Optional<List<PatternStack>> normalized =
                ForbiddenExternalPatternInputs.normalize(inputs);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        List<PatternStack> keys = normalized.get();
        long total = 0;
        for (PatternStack key : keys) {
            total += key.amount();
            if (total > AUXILIARY_SLOTS + 1L) {
                // One machine operation at a time, with no hidden input buffer.
                return Optional.empty();
            }
        }
        for (int mainIndex = 0; mainIndex < keys.size(); mainIndex++) {
            PatternStack main = keys.get(mainIndex);
            if (!isMaterial(main.stack(), true)) {
                continue;
            }
            List<ItemStack> plannedAuxiliary = new ArrayList<>(AUXILIARY_SLOTS);
            for (int index = 0; index < AUXILIARY_SLOTS; index++) {
                plannedAuxiliary.add(ItemStack.EMPTY);
            }
            List<ItemInputGroup> groups = new ArrayList<>();
            groups.add(new ItemInputGroup(
                    List.of(new PatternStack(main.stack(), 1)), List.of(inputSlot)));
            int auxiliaryIndex = 0;
            boolean valid = true;
            for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
                PatternStack key = keys.get(keyIndex);
                long amount = key.amount() - (keyIndex == mainIndex ? 1 : 0);
                if (amount == 0) {
                    continue;
                }
                if (auxiliaryIndex >= AUXILIARY_SLOTS
                        || !isMaterial(key.stack(), false)
                        || amount > auxiliarySlots.get(auxiliaryIndex)
                        .getLimit(key.stack())) {
                    valid = false;
                    break;
                }
                IInventorySlot destination = auxiliarySlots.get(auxiliaryIndex);
                plannedAuxiliary.set(auxiliaryIndex,
                        key.stack().copyWithCount((int) amount));
                groups.add(new ItemInputGroup(
                        List.of(new PatternStack(key.stack(), amount)),
                        List.of(destination)));
                auxiliaryIndex++;
            }
            if (!valid) {
                continue;
            }
            Optional<HephaestusRecipeSelector.Selection> selected =
                    new HephaestusRecipeSelector().select(server,
                            main.stack().copyWithCount(1), plannedAuxiliary,
                            forgeTier, enhancerStacks(), isCreativeMagic());
            if (selected.isEmpty() || selected.get().upgradesForge()) {
                // Upgrades change this machine's level; no item returns to AE.
                continue;
            }
            var selection = selected.get();
            ItemStack result = selection.resultPreview();
            if (!ForbiddenExternalPatternInputs.matchesPrimaryOutput(
                    outputs, result) || !canFit(outputSlot, result)
                    || !usesEveryPlannedForgeInput(selection.inputs(),
                    plannedAuxiliary)) {
                continue;
            }
            return Optional.of(List.copyOf(groups));
        }
        return Optional.empty();
    }

    private static boolean usesEveryPlannedForgeInput(
            List<InputUse> uses, List<ItemStack> auxiliary) {
        int[] used = new int[AUXILIARY_SLOTS + 1];
        for (InputUse use : uses) {
            if (use.slot() < 0 || use.slot() >= used.length
                    || use.count() <= 0
                    || used[use.slot()] > Integer.MAX_VALUE - use.count()) {
                return false;
            }
            used[use.slot()] += use.count();
        }
        if (used[0] != 1) {
            return false;
        }
        for (int index = 0; index < AUXILIARY_SLOTS; index++) {
            if (used[index + 1] != auxiliary.get(index).getCount()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mekanismMagicIsBusy() {
        return hasPaidJob() || hasPendingOutput();
    }

    public IInventorySlot getMainInputSlot() { return inputSlot; }
    public IInventorySlot getAuxInputSlot(int index) { return auxiliarySlots.get(index); }
    public IInventorySlot getEssenceInputSlot() { return essenceInputSlot; }
    public IInventorySlot getWorkingEssenceSlot() { return workingEssenceSlot; }
    public IInventorySlot getEnhancerSlot(int index) { return enhancerSlots.get(index); }
    public IInventorySlot getReturnSlot() { return returnSlot; }
    public int getStoredEssence(int channel) { return essences[channel]; }
    public int getEssenceCapacity(int channel) { return getEssenceFluidCapacity(channel); }
    public int getForgeTier() { return validTier(forgeTier); }
    public boolean hasOutputBlocked() { return outputBlocked; }
    public boolean hasContextMismatch() { return contextMismatch; }
    public String getStatusKey() { return "gui.mekanism_magic.hephaestus." + status.name().toLowerCase(java.util.Locale.ROOT); }

    public boolean isCreativeMagic() {
        return upgradeComponent != null && upgradeComponent.getUpgrades(MagicUpgrades.creativeMagic()) > 0;
    }

    public boolean hasPaidJob() {
        return level != null && level.isClientSide ? syncedPaid : !paidOutput.isEmpty() || paidUpgradeTier > 0;
    }

    public boolean hasPendingOutput() {
        return !pendingOutput.isEmpty() || pendingUpgradeTier > 0 || !pendingReturns.isEmpty();
    }

    public int getUpgradeTargetTier() {
        return level != null && level.isClientSide ? syncedUpgradeTarget
                : paidUpgradeTier > 0 ? paidUpgradeTier : pendingUpgradeTier;
    }

    public int getPaidUpgradeTier() { return paidUpgradeTier; }
    public int getPendingUpgradeTier() { return pendingUpgradeTier; }

    public ItemStack getPaidOutput() { return paidOutput.copy(); }
    public String getPaidRecipeId() { return paidRecipe; }
    public String getPaidMagicCircleId() { return paidCircle; }

    public ResourceLocation getActiveMagicCircleId() {
        return hasPaidJob() ? ResourceLocation.tryParse(paidCircle) : null;
    }

    public boolean isPaidJobPaused() {
        return level != null && level.isClientSide ? syncedPaused : hasPaidJob() && !getActive();
    }

    public List<ItemStack> getPendingOutputs() {
        List<ItemStack> result = new ArrayList<>();
        result.add(pendingOutput.copy());
        pendingReturns.forEach(stack -> result.add(stack.copy()));
        return List.copyOf(result);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        for (int channel = 0; channel < essences.length; channel++) {
            container.track(SyncableInt.create(essences, channel));
            container.track(SyncableInt.create(capacities, channel));
        }
        container.track(SyncableInt.create(() -> forgeTier, value -> forgeTier = value));
        container.track(SyncableInt.create(() -> status.ordinal(),
                value -> status = Status.values()[Math.max(0, Math.min(Status.values().length - 1, value))]));
        container.track(SyncableBoolean.create(this::hasPaidJob, value -> syncedPaid = value));
        container.track(SyncableBoolean.create(() -> outputBlocked, value -> outputBlocked = value));
        container.track(SyncableBoolean.create(() -> contextMismatch, value -> contextMismatch = value));
        container.track(SyncableInt.create(this::getUpgradeTargetTier, value -> syncedUpgradeTarget = Math.max(0, value)));
    }

    @Override
    public CompoundTag getReducedUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getReducedUpdateTag(registries);
        // World renderers need only the circle identity and paid/pause flags.
        // The complete prepared result remains in persistence, not this packet.
        tag.putBoolean("hephaestus_paid", hasPaidJob());
        tag.putBoolean("hephaestus_paused", isPaidJobPaused());
        tag.putString("hephaestus_circle", hasPaidJob() ? paidCircle : "");
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        syncedPaid = tag.getBoolean("hephaestus_paid");
        syncedPaused = tag.getBoolean("hephaestus_paused");
        paidCircle = tag.getString("hephaestus_circle");
    }

    @Override
    protected void saveNativeMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.putInt("internal_tier", getForgeTier());
        data.putIntArray("essences", essences.clone());
        data.put("pending_output", saveCountedItemStack(pendingOutput, registries));
        data.putInt("pending_upgrade_tier", pendingUpgradeTier);
        ListTag returns = new ListTag();
        pendingReturns.forEach(stack -> returns.add(saveCountedItemStack(stack, registries)));
        data.put("pending_returns", returns);
        if (hasPaidJob()) {
            CompoundTag paid = new CompoundTag();
            paid.put("output", saveCountedItemStack(paidOutput, registries));
            paid.putInt("upgrade_tier", paidUpgradeTier);
            paid.putInt("source_tier", paidSourceTier);
            if (migrateLegacyPaidContext) paid.putBoolean("legacy_context", true);
            paid.putInt("duration", paidDuration);
            paid.putString("context", paidContext);
            paid.putString("recipe", paidRecipe);
            paid.putString("magic_circle", paidCircle);
            ListTag enhancers = new ListTag();
            paidEnhancers.forEach(stack -> enhancers.add(saveCountedItemStack(stack, registries)));
            paid.put("enhancers", enhancers);
            data.put("paid_job", paid);
        }
        tag.put(STATE_TAG, data);
    }

    @Override
    protected void loadNativeMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag data = tag.getCompound(STATE_TAG);
        migrateLegacyTier = !data.contains("internal_tier", Tag.TAG_ANY_NUMERIC);
        forgeTier = validTier(data.getInt("internal_tier"));
        if (migrateLegacyTier && legacyCoreSlot != null) {
            forgeTier = Math.max(forgeTier,
                    legacyTier(legacyCoreSlot.getStack()));
        }
        int[] savedEssences = data.getIntArray("essences");
        for (int channel = 0; channel < essences.length; channel++) {
            essences[channel] = channel < savedEssences.length ? Math.max(0, savedEssences[channel]) : 0;
        }
        pendingOutput = loadCountedItemStack(data.getCompound("pending_output"), registries);
        pendingUpgradeTier = data.getInt("pending_upgrade_tier");
        if (pendingUpgradeTier < 2 || pendingUpgradeTier > 5) pendingUpgradeTier = 0;
        pendingReturns.clear();
        for (Tag entry : data.getList("pending_returns", Tag.TAG_COMPOUND)) {
            ItemStack stack = loadCountedItemStack((CompoundTag) entry, registries);
            if (!stack.isEmpty()) {
                pendingReturns.add(stack);
            }
        }
        ItemStack legacyCompletedCore = loadCountedItemStack(data.getCompound("pending_core"), registries);
        if (!legacyCompletedCore.isEmpty()) {
            if (migrateLegacyTier) {
                forgeTier = Math.max(forgeTier, legacyTier(legacyCompletedCore));
            }
            enqueueReturn(legacyCompletedCore);
        }
        clearPaidJob();
        if (data.contains("paid_job", Tag.TAG_COMPOUND)) {
            CompoundTag paid = data.getCompound("paid_job");
            paidOutput = loadCountedItemStack(paid.getCompound("output"), registries);
            boolean legacyReceipt = !paid.contains("source_tier", Tag.TAG_ANY_NUMERIC);
            ItemStack legacyPaidCore = legacyReceipt
                    ? loadCountedItemStack(paid.getCompound("core"), registries) : ItemStack.EMPTY;
            int oldSourceTier = legacyTier(legacyPaidCore);
            paidSourceTier = legacyReceipt ? oldSourceTier > 0 ? oldSourceTier : getForgeTier()
                    : validTier(paid.getInt("source_tier"));
            if (legacyReceipt && migrateLegacyTier) forgeTier = paidSourceTier;
            paidUpgradeTier = paid.getInt("upgrade_tier");
            if (legacyReceipt && paid.getBoolean("core_upgrade")) {
                paidUpgradeTier = legacyTier(paidOutput);
                // This old output was a receipt for one transforming core,
                // not another independent item to mint after migration.
                if (paidUpgradeTier >= 2 && paidUpgradeTier <= 5) paidOutput = ItemStack.EMPTY;
                enqueueReturn(legacyPaidCore);
            }
            if (paidUpgradeTier < 2 || paidUpgradeTier > 5) paidUpgradeTier = 0;
            paidDuration = Math.max(1, paid.getInt("duration"));
            paidContext = paid.getString("context");
            paidRecipe = paid.getString("recipe");
            paidCircle = paid.getString("magic_circle");
            List<ItemStack> snapshots = new ArrayList<>();
            for (Tag entry : paid.getList("enhancers", Tag.TAG_COMPOUND)) {
                snapshots.add(loadCountedItemStack((CompoundTag) entry, registries));
            }
            paidEnhancers = List.copyOf(snapshots);
            migrateLegacyPaidContext = legacyReceipt || paid.getBoolean("legacy_context");
        }
        refreshForgeState();
        status = Status.IDLE;
        outputBlocked = false;
        contextMismatch = false;
        outputRetryTicks = 0;
        lastCatalogRevision = -1;
        invalidatePaidContext();
        selector.invalidate();
    }

    private enum Status {
        IDLE, CONTEXT_CHANGED, NO_RECIPE, MISSING_ESSENCE,
        OUTPUT_BLOCKED, NO_ENERGY, PAID, UPGRADING
    }
}
