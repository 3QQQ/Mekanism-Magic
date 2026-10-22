package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.api.IMekanismMagicAutomation.ItemInputGroup;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.integration.forbiddenarcanus.ClibanoRecipeSelector.Selection;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ClibanoRecipeView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.Snapshot;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** One native Clibano operation with durable output, typed residue and raw XP. */
public final class ClibanoProcessorBlockEntity extends NativeMagicMachineBlockEntity {
    public static final int BASE_ENERGY_PER_TICK = MagicMachineEnergyProfiles.MATERIAL_PROCESSING;
    public static final long ENERGY_CAPACITY = 1_000_000L;
    public static final int FIRE_SLOT = 10;
    public static final int ENHANCER_SLOT = 11;
    public static final int RESIDUE_OUTPUT_SLOT = 17;
    public static final int RESIDUE_CAPACITY = 64;
    public static final int MAX_EXPERIENCE_PER_CLAIM = 10_000;
    private static final double EXPERIENCE_CAPACITY = 1_000_000_000D;
    private static final int OUTPUT_RETRY_TICKS = 5;
    static final String STATE_TAG = "clibano_machine";

    private InputInventorySlot secondInputSlot;
    private BasicInventorySlot fireSlot;
    private BasicInventorySlot enhancerSlot;
    private OutputInventorySlot residueOutputSlot;
    private final ClibanoRecipeSelector selector = new ClibanoRecipeSelector();
    private final Map<ResourceLocation, Integer> residues = new LinkedHashMap<>();
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private ItemStack pendingResidueOutput = ItemStack.EMPTY;
    private FireType fireType = FireType.FIRE;
    private int soulTicks;
    private int soulDuration;
    private double storedExperience;
    private int syncedResidueCount;
    private boolean outputBlocked;
    private int outputRetryTicks;
    private long lastCatalogRevision = -1;

    public ClibanoProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper, IContentsListener listener) {
        // Ingredient-only validation routes the shared top port's context items
        // past the ordinary input slots; full recipe qualification happens later.
        inputSlot = registerLogicalSlot(helper, 0, InputInventorySlot.at(this::isClibanoInput, listener, 46, 35));
        secondInputSlot = registerLogicalSlot(helper, 1, InputInventorySlot.at(this::isClibanoInput, listener, 64, 35));
        fireSlot = registerLogicalSlot(helper, FIRE_SLOT, BasicInventorySlot.at(
                (stack, automation) -> automation != AutomationType.EXTERNAL,
                (stack, automation) -> true,
                stack -> ForbiddenArcanus261Bridge.fireType(stack) != FireType.FIRE, listener, 46, 63));
        enhancerSlot = registerLogicalSlot(helper, ENHANCER_SLOT, BasicInventorySlot.at(
                (stack, automation) -> automation != AutomationType.EXTERNAL,
                (stack, automation) -> true,
                stack -> level != null && ForbiddenArcanus261Bridge.isEnhancerItem(
                        level.registryAccess(), stack), listener, 64, 63));
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT, OutputInventorySlot.at(listener, 116, 35));
        residueOutputSlot = registerLogicalSlot(helper, RESIDUE_OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 134, 63));
        var config = setupNativeItemIO(List.of(inputSlot, secondInputSlot),
                List.of(outputSlot, residueOutputSlot), List.of(fireSlot, enhancerSlot));
        addNativeItemSlotInfo(config, DataType.OUTPUT_1, false, true, List.of(outputSlot));
        addNativeItemSlotInfo(config, DataType.OUTPUT_2, false, true, List.of(residueOutputSlot));
    }

    private boolean isClibanoInput(ItemStack stack) {
        if (level instanceof ServerLevel server) {
            return ForbiddenArcanusRecipeCatalog.isClibanoIngredient(server.getServer(), stack);
        }
        // Client and detached construction do not load or query a server catalog.
        return true;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean changed = nativeBaseUpdate();
        clearNativeRecipeWarnings();
        outputBlocked = false;
        if (!(level instanceof ServerLevel server)) {
            return finishServerUpdate(changed, false);
        }
        // Native souls burn in wall-clock ticks even with redstone disabled,
        // no FE or a blocked output. Expiry cannot refill until the next tick.
        boolean expired = false;
        if (soulTicks > 0) {
            soulTicks--;
            changed = true;
            if (soulTicks == 0) {
                fireType = FireType.FIRE;
                expired = true;
                changed |= resetWork();
            }
            setChanged();
        }
        Snapshot catalog = ForbiddenArcanusRecipeCatalog.snapshot(server.getServer());
        if (lastCatalogRevision >= 0 && lastCatalogRevision != catalog.revision()) {
            changed |= resetWork();
            selector.invalidate();
        }
        lastCatalogRevision = catalog.revision();
        if (hasPendingOutput()) {
            if (outputRetryTicks-- <= 0) {
                changed |= flushPendingOutputs();
                outputRetryTicks = hasPendingOutput() ? OUTPUT_RETRY_TICKS : 0;
            }
            outputBlocked = hasPendingOutput();
            // A finished transaction may drain while redstone is off, but
            // draining the final stack never starts another recipe this tick.
            // Retain an already-running animation across this delivery tick
            // only when the following operation can actually run. Mekanism's
            // delayed client update does not smooth the server active flag.
            return finishServerUpdate(changed, !outputBlocked
                    && canContinueAfterOutputDrain(server, catalog, expired));
        }
        if (queueCombinedResidue(catalog)) {
            return finishServerUpdate(true, false);
        }
        if (!canFunction()) {
            return finishServerUpdate(changed, false);
        }
        FireType candidateFire = soulTicks > 0 ? fireType : expired ? FireType.FIRE
                : ForbiddenArcanus261Bridge.fireType(fireSlot.getStack());
        Optional<Selection> selected = selector.select(server, inputSlot.getStack(),
                secondInputSlot.getStack(), candidateFire, enhancerSlot.getStack(), fireSlot.getStack());
        if (selected.isEmpty()) {
            changed |= resetWork();
            setInputDoesntProduceOutputWarning(!inputSlot.isEmpty() || !secondInputSlot.isEmpty());
            return finishServerUpdate(changed, false);
        }
        Selection selection = selected.get();
        ClibanoRecipeView recipe = selection.view();
        if (!selection.signature().equals(activeRecipe)) {
            activeRecipe = selection.signature();
            progress = 0;
            changed = true;
        }
        progressRequired = Math.max(1, MekanismUtils.getTicks(this, recipe.cookingTimes().forFire(candidateFire)));
        if (!canStartOutput(recipe, candidateFire, catalog)) {
            outputBlocked = true;
            return finishServerUpdate(changed, false);
        }
        long energy = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
        if (energyContainer == null || energyContainer.getEnergy() < energy) {
            setNotEnoughEnergyWarning(true);
            return finishServerUpdate(changed, false);
        }
        if (soulTicks == 0 && candidateFire != FireType.FIRE) {
            int duration = ForbiddenArcanus261Bridge.soulDuration(server.registryAccess(), enhancerSlot.getStack());
            if (duration <= 0 || fireSlot.isEmpty()
                    || ForbiddenArcanus261Bridge.fireType(fireSlot.getStack()) != candidateFire) {
                return finishServerUpdate(changed, false);
            }
            soulDuration = duration;
            soulTicks = duration;
            fireType = candidateFire;
            fireSlot.shrinkStack(1, Action.EXECUTE);
            changed = true;
        }
        changed |= mekanismMagicRecipeItemDisplay().update(List.of(
                new RecipeItemDisplayState.Entry(0,
                        RecipeItemDisplayState.displayCopy(getInputSlot(selection.firstInputSlot()).getStack()),
                        RecipeItemDisplayState.displayCopy(recipe.output()))));
        energyContainer.extract(energy, Action.EXECUTE, AutomationType.INTERNAL);
        progress++;
        changed = true;
        if (progress >= progressRequired) {
            if (!selector.stillMatches(server, selection, inputSlot.getStack(), secondInputSlot.getStack(),
                    fireType, enhancerSlot.getStack()) || !canStartOutput(recipe, fireType, catalog)) {
                changed |= resetWork();
                return finishServerUpdate(changed, false);
            }
            ItemStack first = getInputSlot(selection.firstInputSlot()).getStack();
            ItemStack second = selection.secondInputSlot() < 0 ? ItemStack.EMPTY
                    : getInputSlot(selection.secondInputSlot()).getStack();
            Optional<ItemStack> assembled = ForbiddenArcanusRecipeCatalog.assembleClibano(server,
                    selection.id(), first, second, fireType, enhancerSlot.getStack());
            if (assembled.isEmpty() || assembled.get().isEmpty()) {
                changed |= resetWork();
                return finishServerUpdate(changed, false);
            }
            // Native assemble and random residue each run once, after all
            // qualification checks. Owned-slot changes cannot interleave with
            // another capability call on the server thread.
            pendingOutput = assembled.get().copy();
            if (recipe.canProduceResidue(fireType)) {
                var chance = recipe.residue().orElseThrow();
                if (server.random.nextDouble() < chance.chance()) {
                    residues.merge(chance.type(), 1, Integer::sum);
                }
            }
            storedExperience += recipe.experience();
            queueCombinedResidue(catalog);
            getInputSlot(selection.firstInputSlot()).shrinkStack(selection.firstAmount(), Action.EXECUTE);
            if (selection.secondInputSlot() >= 0) {
                getInputSlot(selection.secondInputSlot()).shrinkStack(selection.secondAmount(), Action.EXECUTE);
            }
            progress = 0;
            activeRecipe = "";
            outputRetryTicks = 0;
            selector.invalidate();
            setChanged();
        }
        return finishServerUpdate(changed, true);
    }

    private boolean canContinueAfterOutputDrain(ServerLevel server, Snapshot catalog, boolean expired) {
        if (!getActive() || !canFunction() || !hasAnyRecipeInput() || energyContainer == null
                || energyContainer.getEnergy() < MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK)) {
            return false;
        }
        FireType nextFire = soulTicks > 0 ? fireType : expired ? FireType.FIRE
                : ForbiddenArcanus261Bridge.fireType(fireSlot.getStack());
        Optional<Selection> next = selector.select(server, inputSlot.getStack(), secondInputSlot.getStack(),
                nextFire, enhancerSlot.getStack(), fireSlot.getStack());
        if (next.isEmpty()) {
            return false;
        }
        outputBlocked = !canStartOutput(next.get().view(), nextFire, catalog);
        return !outputBlocked && (nextFire == FireType.FIRE || soulTicks > 0
                || ForbiddenArcanus261Bridge.soulDuration(server.registryAccess(), enhancerSlot.getStack()) > 0);
    }

    private boolean canStartOutput(ClibanoRecipeView recipe, FireType fire, Snapshot catalog) {
        double experience = recipe.experience();
        if (!Double.isFinite(experience) || experience < 0
                || storedExperience + experience > EXPERIENCE_CAPACITY
                || !canFit(outputSlot, recipe.output())) {
            return false;
        }
        if (recipe.canProduceResidue(fire) && recipe.residue().orElseThrow().chance() > 0) {
            if (getStoredResidueCount() >= RESIDUE_CAPACITY) {
                return false;
            }
            var definition = catalog.residuesById().get(recipe.residue().orElseThrow().type());
            // A blocked combined output must not keep accumulating residues.
            return definition == null || canFit(residueOutputSlot, definition.output());
        }
        return true;
    }

    private boolean queueCombinedResidue(Snapshot catalog) {
        if (!pendingResidueOutput.isEmpty()) {
            return false;
        }
        for (var entry : residues.entrySet()) {
            var definition = catalog.residuesById().get(entry.getKey());
            // Unknown registry IDs survive reload unchanged and continue to
            // occupy capacity until their data-pack definition returns.
            if (definition == null || definition.requiredAmount() <= 0
                    || entry.getValue() < definition.requiredAmount()
                    || definition.output().isEmpty()) {
                continue;
            }
            pendingResidueOutput = definition.output();
            entry.setValue(entry.getValue() - definition.requiredAmount());
            setChanged();
            return true;
        }
        return false;
    }

    private boolean flushPendingOutputs() {
        boolean changed = false;
        if (!pendingOutput.isEmpty()) {
            int moved = insertPending(outputSlot, pendingOutput);
            pendingOutput.shrink(moved);
            changed |= moved > 0;
        }
        if (!pendingResidueOutput.isEmpty()) {
            int moved = insertPending(residueOutputSlot, pendingResidueOutput);
            pendingResidueOutput.shrink(moved);
            changed |= moved > 0;
        }
        if (changed) {
            setChanged();
        }
        return changed;
    }

    private static boolean canFit(IInventorySlot slot, ItemStack result) {
        ItemStack current = slot.getStack();
        return result.isEmpty() || current.isEmpty() || ItemStack.isSameItemSameComponents(current, result)
                && (long) current.getCount() + result.getCount() <= Math.min(current.getMaxStackSize(), slot.getLimit(current));
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

    private boolean resetWork() {
        boolean changed = progress != 0 || !activeRecipe.isEmpty();
        progress = 0;
        progressRequired = 1;
        activeRecipe = "";
        return mekanismMagicRecipeItemDisplay().clear() || changed;
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(ItemStackHandler inventory) {
        // This machine commits a multi-part transaction in its own loop.
        return Optional.empty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return BASE_ENERGY_PER_TICK;
    }

    @Override
    protected int energySlotX() {
        return 28;
    }

    @Override
    protected int energySlotY() {
        return 63;
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        return false;
    }

    @Override
    public Optional<List<ItemInputGroup>> mekanismMagicPlanExternalItemInputs(
            List<PatternStack> inputs, List<PatternStack> outputs) {
        // A first-fit item handler packs two identical ingredients into the
        // first slot, but native double-input recipes require both slots.
        // Accept one exact operation and reserve its physical roles explicitly.
        if (!(level instanceof ServerLevel server) || hasPendingOutput()
                || !inputSlot.isEmpty() || !secondInputSlot.isEmpty()) {
            return Optional.empty();
        }
        Optional<List<PatternStack>> normalized =
                ForbiddenExternalPatternInputs.normalize(inputs);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        List<PatternStack> keys = normalized.get();
        if (keys.size() == 1) {
            PatternStack key = keys.getFirst();
            if (key.amount() == 1) {
                Optional<List<ItemInputGroup>> first = planClibanoCandidate(
                        server, key.stack(), ItemStack.EMPTY, outputs);
                return first.isPresent() ? first : planClibanoCandidate(
                        server, ItemStack.EMPTY, key.stack(), outputs);
            }
            if (key.amount() == 2) {
                return planClibanoCandidate(server, key.stack(),
                        key.stack(), outputs);
            }
        } else if (keys.size() == 2 && keys.get(0).amount() == 1
                && keys.get(1).amount() == 1) {
            Optional<List<ItemInputGroup>> first = planClibanoCandidate(
                    server, keys.get(0).stack(), keys.get(1).stack(),
                    outputs);
            return first.isPresent() ? first : planClibanoCandidate(
                    server, keys.get(1).stack(), keys.get(0).stack(),
                    outputs);
        }
        return Optional.empty();
    }

    private Optional<List<ItemInputGroup>> planClibanoCandidate(
            ServerLevel server, ItemStack first, ItemStack second,
            List<PatternStack> outputs) {
        FireType candidateFire = soulTicks > 0 ? fireType
                : ForbiddenArcanus261Bridge.fireType(fireSlot.getStack());
        Optional<ClibanoRecipeSelector.Selection> selected =
                new ClibanoRecipeSelector().select(server, first, second,
                        candidateFire, enhancerSlot.getStack(),
                        fireSlot.getStack());
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        var selection = selected.get();
        int firstUsed = (selection.firstInputSlot() == 0 ?
                selection.firstAmount() : 0)
                + (selection.secondInputSlot() == 0 ?
                selection.secondAmount() : 0);
        int secondUsed = (selection.firstInputSlot() == 1 ?
                selection.firstAmount() : 0)
                + (selection.secondInputSlot() == 1 ?
                selection.secondAmount() : 0);
        if (firstUsed != first.getCount() || secondUsed != second.getCount()) {
            // Native selection may ignore an extra stack. Never charge AE for
            // an item that this single operation will not consume.
            return Optional.empty();
        }
        ItemStack recipeFirst = selection.firstInputSlot() == 0 ?
                first : second;
        ItemStack recipeSecond = selection.secondInputSlot() < 0 ?
                ItemStack.EMPTY : selection.secondInputSlot() == 0 ?
                first : second;
        Optional<ItemStack> assembled = ForbiddenArcanusRecipeCatalog
                .assembleClibano(server, selection.id(), recipeFirst,
                        recipeSecond, candidateFire, enhancerSlot.getStack());
        if (assembled.isEmpty()
                || !ForbiddenExternalPatternInputs.matchesPrimaryOutput(
                outputs, assembled.get())
                || !canStartOutput(selection.view(), candidateFire,
                ForbiddenArcanusRecipeCatalog.snapshot(server.getServer()))
                || !canFit(outputSlot, assembled.get())) {
            return Optional.empty();
        }
        List<ItemInputGroup> groups = new ArrayList<>(2);
        if (!first.isEmpty()) {
            groups.add(new ItemInputGroup(
                    List.of(new PatternStack(first, 1)),
                    List.of(inputSlot)));
        }
        if (!second.isEmpty()) {
            groups.add(new ItemInputGroup(
                    List.of(new PatternStack(second, 1)),
                    List.of(secondInputSlot)));
        }
        return Optional.of(List.copyOf(groups));
    }

    @Override
    public boolean mekanismMagicIsBusy() {
        return super.mekanismMagicIsBusy() || hasPendingOutput();
    }

    public IInventorySlot getInputSlot(int index) {
        if (index < 0 || index > 1) {
            throw new IllegalArgumentException("Clibano input slot must be 0 or 1");
        }
        return index == 0 ? inputSlot : secondInputSlot;
    }

    public IInventorySlot getFireSlot() {
        return fireSlot;
    }

    public IInventorySlot getEnhancerSlot() {
        return enhancerSlot;
    }

    public IInventorySlot getResidueOutputSlot() {
        return residueOutputSlot;
    }

    public FireType getFireType() {
        return fireType;
    }

    public int getSoulTicks() {
        return soulTicks;
    }

    public int getSoulDuration() {
        return soulDuration;
    }

    public double getStoredExperience() {
        return storedExperience;
    }

    public Map<ResourceLocation, Integer> getResidueCounts() {
        return Map.copyOf(residues);
    }

    public int getStoredResidueCount() {
        return level != null && level.isClientSide ? syncedResidueCount
                : (int) Math.min(Integer.MAX_VALUE, residues.values().stream().mapToLong(Integer::longValue).sum());
    }

    public boolean hasPendingOutput() {
        return !pendingOutput.isEmpty() || !pendingResidueOutput.isEmpty();
    }

    public List<ItemStack> getPendingOutputs() {
        return List.of(pendingOutput.copy(), pendingResidueOutput.copy());
    }

    public boolean hasOutputBlocked() {
        return outputBlocked;
    }

    /** A GUI claim is bounded, and raw fractional XP is rounded exactly once. */
    public int takeExperience(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || player.level() != level || isRemoved()
                || player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 64 || storedExperience <= 0) {
            return 0;
        }
        double claimed = Math.min(MAX_EXPERIENCE_PER_CLAIM, storedExperience);
        storedExperience -= claimed;
        setChanged();
        int amount = (int) Math.floor(claimed);
        double fraction = claimed - amount;
        if (fraction > 0 && server.random.nextDouble() < fraction) {
            amount++;
        }
        if (amount > 0) {
            ExperienceOrb.award(server, player.position(), amount);
        }
        return amount;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> fireType.ordinal(),
                value -> fireType = FireType.values()[Math.max(0, Math.min(FireType.values().length - 1, value))]));
        container.track(SyncableInt.create(() -> soulTicks, value -> soulTicks = Math.max(0, value)));
        container.track(SyncableInt.create(() -> soulDuration, value -> soulDuration = Math.max(0, value)));
        container.track(SyncableInt.create(this::getStoredResidueCount, value -> syncedResidueCount = Math.max(0, value)));
        container.track(SyncableDouble.create(() -> storedExperience, value -> storedExperience = Math.max(0, value)));
        container.track(SyncableBoolean.create(() -> outputBlocked, value -> outputBlocked = value));
    }

    @Override
    protected void saveNativeMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag data = new CompoundTag();
        data.put("pending_output", saveCountedItemStack(pendingOutput, registries));
        data.put("pending_residue_output", saveCountedItemStack(pendingResidueOutput, registries));
        data.putDouble("experience", storedExperience);
        data.putString("fire_type", fireType.name());
        data.putInt("soul_ticks", soulTicks);
        data.putInt("soul_duration", soulDuration);
        ListTag list = new ListTag();
        residues.forEach((id, count) -> {
            if (count > 0) {
                CompoundTag residue = new CompoundTag();
                residue.putString("id", id.toString());
                residue.putInt("count", count);
                list.add(residue);
            }
        });
        data.put("residues", list);
        tag.put(STATE_TAG, data);
    }

    @Override
    protected void loadNativeMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag data = tag.getCompound(STATE_TAG);
        pendingOutput = loadCountedItemStack(data.getCompound("pending_output"), registries);
        pendingResidueOutput = loadCountedItemStack(data.getCompound("pending_residue_output"), registries);
        double experience = data.getDouble("experience");
        storedExperience = Double.isFinite(experience) ? Math.max(0, experience) : 0;
        soulTicks = Math.max(0, data.getInt("soul_ticks"));
        soulDuration = Math.max(soulTicks, data.getInt("soul_duration"));
        try {
            fireType = soulTicks > 0 ? FireType.valueOf(data.getString("fire_type")) : FireType.FIRE;
        } catch (IllegalArgumentException malformedFire) {
            fireType = FireType.FIRE;
        }
        if (fireType == FireType.FIRE) {
            soulTicks = 0;
        }
        residues.clear();
        for (Tag entry : data.getList("residues", Tag.TAG_COMPOUND)) {
            CompoundTag residue = (CompoundTag) entry;
            ResourceLocation id = ResourceLocation.tryParse(residue.getString("id"));
            int count = Math.max(0, residue.getInt("count"));
            if (id != null && count > 0) {
                residues.merge(id, count, (first, second) -> (int) Math.min(Integer.MAX_VALUE, (long) first + second));
            }
        }
        outputRetryTicks = 0;
        outputBlocked = false;
        lastCatalogRevision = -1;
        selector.invalidate();
    }
}
