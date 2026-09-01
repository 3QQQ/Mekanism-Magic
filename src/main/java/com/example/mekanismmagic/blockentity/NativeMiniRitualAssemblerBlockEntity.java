package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Creates one of Occultism's eighteen miniature pentacle selectors from the
 * original formation materials and the non-consumable chalk module.
 */
public final class NativeMiniRitualAssemblerBlockEntity
        extends NativeMagicMachineBlockEntity {
    private static final int INPUT_SETTLE_TICKS = 5;
    private List<mekanism.api.inventory.IInventorySlot> chalkSlots;
    private boolean chalkModuleOpen;
    private int previewIndex;
    private boolean craftRequested;
    private int inputSettleTicks;
    private String requestedPentacle = "";
    private List<MachineRecipeResult> cachedPreviewCandidates;
    private RecipeManager cachedPreviewRecipeManager;
    private Object cachedPreviewRecipeState;
    private List<ResourceLocation> cachedPreviewTargets = List.of();
    private ResourceLocation cachedLockedPentacleTarget;
    private ItemStack cachedLockedPentacleStack = ItemStack.EMPTY;

    public NativeMiniRitualAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        IContentsListener previewListener = () -> {
            cachedPreviewCandidates = null;
            inputSettleTicks = INPUT_SETTLE_TICKS;
            listener.onContentsChanged();
        };
        List<mekanism.api.inventory.IInventorySlot> inputs = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                int index = row * 4 + column;
                InputInventorySlot slot = registerLogicalSlot(helper, index,
                        InputInventorySlot.at(previewListener,
                                69 + column * 18, 31 + row * 18));
                inputs.add(slot);
                if (index == 0) {
                    inputSlot = slot;
                }
            }
        }
        chalkSlots = new ArrayList<>();
        List<String> chalkColors = OccultismRecipeBridge.ritualChalkColors();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                int index = row * 4 + column;
                chalkSlots.add(registerLogicalSlot(helper, CHALK_SLOT_START + index,
                        new ChalkInventorySlot(this,
                                OccultismRecipeBridge::isAnyChalk,
                                previewListener, 240 + column * 18,
                                104 + row * 18)));
            }
        }
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 176, 58));
        setupNativeItemIO(inputs, List.of(outputSlot), List.of());
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        if (inputSettleTicks > 0 && progress == 0) {
            inputSettleTicks--;
            return Optional.empty();
        }
        List<MachineRecipeResult> candidates = previewCandidates(inventory);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (progress > 0 && !activeRecipe.isEmpty()) {
            Optional<MachineRecipeResult> running = candidates.stream()
                    .filter(candidate -> activeRecipe.startsWith(
                            candidate.id() + "|"))
                    .findFirst();
            if (running.isPresent()) {
                return running;
            }
        }
        return candidates.stream()
                .min(Comparator
                        .comparingInt((MachineRecipeResult candidate) ->
                                unmatchedItemCount(candidate, inventory))
                        .thenComparingInt(candidate ->
                                unmatchedSlotCount(candidate, inventory))
                        .thenComparingInt(candidate ->
                                requestedPentacle.equals(
                                        candidatePentacle(candidate)) ? 0 : 1)
                        .thenComparing(candidate ->
                                candidate.id().toString()));
    }

    /**
     * A complete material set may start automatically. Pentacle locking is an
     * optional preference for ambiguous inputs, not a requirement to process
     * manually inserted materials.
     */
    @Override
    protected boolean hasAnyRecipeInput() {
        for (int index = 0; index < INPUT_SLOTS; index++) {
            mekanism.api.inventory.IInventorySlot slot =
                    logicalSlots().get(index);
            if (slot != null && !slot.getStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public void lockPentacle(int targetIndex) {
        List<ResourceLocation> targets = previewTargets();
        if (targets.isEmpty()) {
            return;
        }
        previewIndex = Math.floorMod(targetIndex, targets.size());
        requestedPentacle = targets.get(previewIndex).toString();
        craftRequested = true;
        setChanged();
    }

    public void clearPentaclePreference() {
        requestedPentacle = "";
        craftRequested = false;
        setChanged();
    }

    private List<MachineRecipeResult> previewCandidates(
            ItemStackHandler inventory) {
        if (level == null) {
            return List.of();
        }
        if (cachedPreviewCandidates == null) {
            cachedPreviewCandidates =
                    OccultismRecipeBridge.findMiniRitualCandidates(
                            level, inventory);
        }
        return cachedPreviewCandidates;
    }

    private List<ResourceLocation> previewTargets() {
        if (level == null) {
            return List.of();
        }
        RecipeManager recipeManager = level.getRecipeManager();
        // RecipeManager replaces its backing recipe map on every reload. Its
        // values view therefore gives us an allocation-free reload token while
        // the GUI is rendering, instead of fingerprinting every ritual recipe
        // once per frame.
        Object recipeState = recipeManager.getRecipes();
        if (recipeManager != cachedPreviewRecipeManager
                || recipeState != cachedPreviewRecipeState) {
            cachedPreviewTargets = OccultismRecipeBridge
                    .miniRitualPentacleIds(level);
            cachedPreviewRecipeManager = recipeManager;
            cachedPreviewRecipeState = recipeState;
            if (cachedLockedPentacleTarget != null
                    && !cachedPreviewTargets.contains(
                            cachedLockedPentacleTarget)) {
                clearLockedPentaclePreview();
            }
        }
        return cachedPreviewTargets;
    }

    public int getPreviewIndex() {
        return previewIndex;
    }

    public ItemStack getLockedPentacleStack() {
        if (level == null || !craftRequested) {
            return ItemStack.EMPTY;
        }
        ResourceLocation target = level.isClientSide()
                ? previewTarget()
                : requestedPentacle.isEmpty()
                ? previewTarget()
                : ResourceLocation.tryParse(requestedPentacle);
        if (target == null) {
            clearLockedPentaclePreview();
            return ItemStack.EMPTY;
        }
        if (!target.equals(cachedLockedPentacleTarget)) {
            cachedLockedPentacleTarget = target;
            cachedLockedPentacleStack = OccultismRecipeBridge
                    .createPentacleMiniRitual(target);
        }
        return cachedLockedPentacleStack;
    }

    private void clearLockedPentaclePreview() {
        cachedLockedPentacleTarget = null;
        cachedLockedPentacleStack = ItemStack.EMPTY;
    }

    private void clearPentacleCatalogCache() {
        cachedPreviewRecipeManager = null;
        cachedPreviewRecipeState = null;
        cachedPreviewTargets = List.of();
        clearLockedPentaclePreview();
    }

    private ResourceLocation previewTarget() {
        List<ResourceLocation> targets = previewTargets();
        return targets.isEmpty() ? null
                : targets.get(Math.floorMod(previewIndex, targets.size()));
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> previewIndex,
                this::setPreviewIndex));
        container.track(SyncableBoolean.create(() -> craftRequested,
                value -> craftRequested = value));
    }

    private void setPreviewIndex(int value) {
        int normalized = Math.max(0, value);
        if (previewIndex != normalized) {
            previewIndex = normalized;
        }
    }

    private static String candidatePentacle(
            MachineRecipeResult candidate) {
        net.minecraft.world.item.component.CustomData data =
                candidate.output().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.getUnsafe().getString("pentacle");
    }

    private static int unmatchedItemCount(MachineRecipeResult candidate,
                                          ItemStackHandler inventory) {
        int total = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            total += inventory.getStackInSlot(slot).getCount();
        }
        int matched = candidate.inputs().stream()
                .mapToInt(com.example.mekanismmagic.integration.common.recipe
                        .InputUse::count)
                .sum();
        return Math.max(0, total - matched);
    }

    private static int unmatchedSlotCount(MachineRecipeResult candidate,
                                          ItemStackHandler inventory) {
        int[] matched = new int[INPUT_SLOTS];
        candidate.inputs().forEach(input -> {
            if (input.slot() >= 0 && input.slot() < matched.length) {
                matched[input.slot()] += input.count();
            }
        });
        int remainingSlots = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (inventory.getStackInSlot(slot).getCount() > matched[slot]) {
                remainingSlots++;
            }
        }
        return remainingSlots;
    }

    @Override
    protected void onNativeUpgradeChanged(mekanism.api.Upgrade upgrade) {
        previewIndex = 0;
    }

    @Override
    protected void onRecipeFinished(MachineRecipeResult recipe) {
        // Keep the selected target armed so the next supplied batch starts
        // automatically without another GUI interaction.
    }

    @Override
    protected void saveNativeMachineData(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.saveNativeMachineData(tag, registries);
        tag.putInt("mini_ritual_preview", previewIndex);
        tag.putBoolean("mini_ritual_craft_requested", craftRequested);
        tag.putString("mini_ritual_requested_pentacle", requestedPentacle);
    }

    @Override
    protected void loadNativeMachineData(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.loadNativeMachineData(tag, registries);
        previewIndex = Math.max(0, tag.getInt("mini_ritual_preview"));
        craftRequested = tag.getBoolean("mini_ritual_craft_requested");
        requestedPentacle = tag.getString("mini_ritual_requested_pentacle");
        if (!requestedPentacle.isEmpty()) {
            craftRequested = true;
        }
        cachedPreviewCandidates = null;
        clearPentacleCatalogCache();
    }

    @Override
    protected int baseEnergyPerTick() {
        return 300;
    }

    @Override
    protected int energySlotX() {
        return 30;
    }

    @Override
    protected int energySlotY() {
        return 35;
    }


    public boolean isChalkSlot(mekanism.api.inventory.IInventorySlot slot) {
        return chalkSlots != null && chalkSlots.contains(slot);
    }

    @Override
    public List<mekanism.api.inventory.IInventorySlot>
    mekanismMagicManualOnlySlots() {
        return chalkSlots == null ? List.of() : List.copyOf(chalkSlots);
    }

    public void setChalkModuleOpen(boolean open) {
        chalkModuleOpen = open;
    }

    private boolean isChalkContainerSlotActive() {
        return level == null || !level.isClientSide() || chalkModuleOpen;
    }

    private static final class ChalkInventorySlot extends BasicInventorySlot {
        private final NativeMiniRitualAssemblerBlockEntity tile;
        private final int x;
        private final int y;

        private ChalkInventorySlot(NativeMiniRitualAssemblerBlockEntity tile,
                                   Predicate<ItemStack> validator,
                                   IContentsListener listener, int x, int y) {
            super((stack, automation) -> true, (stack, automation) -> true,
                    validator, listener, x, y);
            this.tile = tile;
            this.x = x;
            this.y = y;
        }

        @Override
        public InventoryContainerSlot createContainerSlot() {
            return new InventoryContainerSlot(this, x, y, getSlotType(),
                    getSlotOverlay(), warning -> {
                }, this::setStackUnchecked) {
                @Override
                public boolean isActive() {
                    return tile.isChalkContainerSlotActive();
                }
            };
        }
    }
}
