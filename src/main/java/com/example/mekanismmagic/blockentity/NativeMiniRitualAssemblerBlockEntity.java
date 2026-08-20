package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;

import com.example.mekanismmagic.NativeMekanismRegistries;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Creates one of Occultism's eighteen miniature pentacle selectors from the
 * original formation materials and the non-consumable chalk module.
 */
public final class NativeMiniRitualAssemblerBlockEntity
        extends NativeMagicMachineBlockEntity {
    private List<mekanism.api.inventory.IInventorySlot> chalkSlots;
    private boolean chalkModuleOpen;
    private int previewIndex;
    private boolean craftRequested;
    private String requestedPentacle = "";
    private List<OccultismRecipeBridge.RecipeResult> cachedPreviewCandidates;
    private Component cachedPreviewLabel;

    public NativeMiniRitualAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK, pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        IContentsListener previewListener = () -> {
            cachedPreviewCandidates = null;
            cachedPreviewLabel = null;
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
        setupNativeItemIO(inputs, List.of(outputSlot), chalkSlots);
    }

    @Override
    protected Optional<OccultismRecipeBridge.RecipeResult> findRecipe(
            ItemStackHandler inventory) {
        if (!craftRequested) {
            return Optional.empty();
        }
        return previewCandidates(inventory).stream()
                .filter(candidate -> requestedPentacle.isEmpty()
                        || requestedPentacle.equals(
                        candidatePentacle(candidate)))
                .findFirst();
    }

    public void cyclePreview(int direction) {
        List<OccultismRecipeBridge.RecipeResult> candidates =
                previewCandidates();
        if (!candidates.isEmpty()) {
            previewIndex = Math.floorMod(
                    previewIndex + direction, candidates.size());
            cachedPreviewLabel = null;
            setChanged();
        }
    }

    public void requestCraft() {
        List<OccultismRecipeBridge.RecipeResult> candidates =
                previewCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        previewIndex = Math.floorMod(previewIndex, candidates.size());
        requestedPentacle = candidatePentacle(
                candidates.get(previewIndex));
        craftRequested = true;
        setChanged();
    }

    private List<OccultismRecipeBridge.RecipeResult> previewCandidates() {
        if (level == null) {
            return List.of();
        }
        if (cachedPreviewCandidates != null) {
            return cachedPreviewCandidates;
        }
        return previewCandidates(snapshotInventory());
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

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> previewIndex,
                this::setPreviewIndex));
    }

    public Component getPreviewLabel() {
        if (cachedPreviewLabel != null) {
            return cachedPreviewLabel;
        }
        List<OccultismRecipeBridge.RecipeResult> candidates =
                previewCandidates();
        if (candidates.isEmpty()) {
            cachedPreviewLabel = Component.translatable(
                    "gui.mekanism_magic.mini_ritual.none");
            return cachedPreviewLabel;
        }
        cachedPreviewLabel = OccultismRecipeBridge.pentacleDisplayName(
                ResourceLocation.tryParse(candidatePentacle(candidates.get(
                        Math.floorMod(previewIndex, candidates.size())))));
        return cachedPreviewLabel;
    }

    private void setPreviewIndex(int value) {
        int normalized = Math.max(0, value);
        if (previewIndex != normalized) {
            previewIndex = normalized;
            cachedPreviewLabel = null;
        }
    }

    private static String candidatePentacle(
            OccultismRecipeBridge.RecipeResult candidate) {
        net.minecraft.nbt.CompoundTag data = candidate.output().getTag();
        return data == null ? "" : data.getString("pentacle");
    }

    @Override
    protected void onNativeUpgradeChanged(mekanism.api.Upgrade upgrade) {
        previewIndex = 0;
        cachedPreviewLabel = null;
    }

    @Override
    protected void onRecipeFinished(
            OccultismRecipeBridge.RecipeResult recipe) {
        craftRequested = false;
        requestedPentacle = "";
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
        cachedPreviewCandidates = null;
        cachedPreviewLabel = null;
        if (!craftRequested) {
            progress = 0;
            activeRecipe = "";
        }
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

