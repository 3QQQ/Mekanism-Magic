package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;

import com.example.mekanismmagic.NativeMekanismRegistries;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

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
    private List<OccultismRecipeBridge.RecipeResult> cachedPreviewCandidates;

    public NativeMiniRitualAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK, pos, state);
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
                chalkSlots.add(registerLogicalSlot(helper,
                        CHALK_SLOT_START + index,
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
    protected Optional<OccultismRecipeBridge.RecipeResult> findRecipe(
            ItemStackHandler inventory) {
        if (inputSettleTicks > 0 && progress == 0) {
            inputSettleTicks--;
            return Optional.empty();
        }
        List<OccultismRecipeBridge.RecipeResult> candidates =
                previewCandidates(inventory);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        if (progress > 0 && !activeRecipe.isEmpty()) {
            Optional<OccultismRecipeBridge.RecipeResult> running =
                    candidates.stream()
                            .filter(candidate -> activeRecipe.startsWith(
                                    candidate.id() + "|"))
                            .findFirst();
            if (running.isPresent()) {
                return running;
            }
        }
        return candidates.stream()
                .min(Comparator
                        .comparingInt((OccultismRecipeBridge.RecipeResult candidate) ->
                                unmatchedItemCount(candidate, inventory))
                        .thenComparingInt(candidate ->
                                unmatchedSlotCount(candidate, inventory))
                        .thenComparingInt(candidate ->
                                requestedPentacle.equals(
                                        candidatePentacle(candidate)) ? 0 : 1)
                        .thenComparing(candidate ->
                                candidate.id().toString()));
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

    private List<OccultismRecipeBridge.RecipeResult> previewCandidates(
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
        return level == null ? List.of()
                : OccultismRecipeBridge.miniRitualPentacleIds(level);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> previewIndex,
                this::setPreviewIndex));
        container.track(SyncableBoolean.create(() -> craftRequested,
                value -> craftRequested = value));
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
        return target == null ? ItemStack.EMPTY
                : OccultismRecipeBridge.createPentacleMiniRitual(target);
    }

    private ResourceLocation previewTarget() {
        List<ResourceLocation> targets = previewTargets();
        return targets.isEmpty() ? null
                : targets.get(Math.floorMod(previewIndex, targets.size()));
    }

    private void setPreviewIndex(int value) {
        int normalized = Math.max(0, value);
        if (previewIndex != normalized) {
            previewIndex = normalized;
        }
    }

    private static String candidatePentacle(
            OccultismRecipeBridge.RecipeResult candidate) {
        net.minecraft.nbt.CompoundTag data = candidate.output().getTag();
        return data == null ? "" : data.getString("pentacle");
    }

    private static int unmatchedItemCount(
            OccultismRecipeBridge.RecipeResult candidate,
            ItemStackHandler inventory) {
        int total = 0;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            total += inventory.getStackInSlot(slot).getCount();
        }
        int matched = candidate.inputs().stream()
                .mapToInt(OccultismRecipeBridge.InputUse::count)
                .sum();
        return Math.max(0, total - matched);
    }

    private static int unmatchedSlotCount(
            OccultismRecipeBridge.RecipeResult candidate,
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
    protected void onRecipeFinished(
            OccultismRecipeBridge.RecipeResult recipe) {
        // Keep the selected target armed so the next supplied batch starts
        // automatically without another GUI interaction.
    }

    @Override
    public void saveAdditional(net.minecraft.nbt.CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("mini_ritual_preview", previewIndex);
        tag.putBoolean("mini_ritual_craft_requested", craftRequested);
        tag.putString("mini_ritual_requested_pentacle",
                requestedPentacle);
    }

    @Override
    public void load(net.minecraft.nbt.CompoundTag tag) {
        super.load(tag);
        previewIndex = Math.max(0, tag.getInt("mini_ritual_preview"));
        craftRequested = tag.getBoolean(
                "mini_ritual_craft_requested");
        requestedPentacle = tag.getString(
                "mini_ritual_requested_pentacle");
        if (!requestedPentacle.isEmpty()) {
            craftRequested = true;
        }
        cachedPreviewCandidates = null;
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
        // The server must always accept the slot. On the client it follows
        // the collapsible module so hidden stacks and hit boxes disappear.
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

