package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;

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

    public NativeMiniRitualAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        List<mekanism.api.inventory.IInventorySlot> inputs = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                int index = row * 4 + column;
                InputInventorySlot slot = registerLogicalSlot(helper, index,
                        InputInventorySlot.at(listener,
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
                                listener, 240 + column * 18, 104 + row * 18)));
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
        List<OccultismRecipeBridge.RecipeResult> candidates =
                OccultismRecipeBridge.findMiniRitualCandidates(level, inventory);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream()
                .filter(candidate -> requestedPentacle.isEmpty()
                        || candidate.output().getItem() == com.example.mekanismmagic.MekanismMagic.MINI_RITUAL.get())
                .filter(candidate -> requestedPentacle.isEmpty()
                        || requestedPentacle.equals(candidatePentacle(candidate)))
                .findFirst();
    }

    public void cyclePreview(int direction) {
        List<OccultismRecipeBridge.RecipeResult> candidates =
                OccultismRecipeBridge.findMiniRitualCandidates(level,
                        snapshotForPreview());
        if (!candidates.isEmpty()) {
            previewIndex = Math.floorMod(previewIndex + direction,
                    candidates.size());
            setChanged();
        }
    }

    public void requestCraft() {
        List<OccultismRecipeBridge.RecipeResult> candidates =
                OccultismRecipeBridge.findMiniRitualCandidates(level,
                        snapshotForPreview());
        if (candidates.isEmpty()) {
            return;
        }
        previewIndex = Math.floorMod(previewIndex, candidates.size());
        requestedPentacle = candidatePentacle(
                candidates.get(previewIndex));
        craftRequested = true;
        setChanged();
    }

    private ItemStackHandler snapshotForPreview() {
        ItemStackHandler inventory = new ItemStackHandler(MACHINE_INVENTORY_SIZE);
        if (logicalSlots() != null) {
            logicalSlots().forEach((index, slot) ->
                    inventory.setStackInSlot(index, slot.getStack().copy()));
        }
        return inventory;
    }

    public int getPreviewIndex() {
        return previewIndex;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(() -> previewIndex,
                value -> previewIndex = Math.max(0, value)));
    }

    public Component getPreviewLabel() {
        List<OccultismRecipeBridge.RecipeResult> candidates =
                OccultismRecipeBridge.findMiniRitualCandidates(level,
                        snapshotForPreview());
        if (candidates.isEmpty()) {
            return Component.translatable("gui.mekanism_magic.mini_ritual.none");
        }
        return OccultismRecipeBridge.pentacleDisplayName(
                ResourceLocation.tryParse(candidatePentacle(
                        candidates.get(Math.floorMod(previewIndex,
                                candidates.size())))));
    }

    private static String candidatePentacle(
            OccultismRecipeBridge.RecipeResult candidate) {
        net.minecraft.world.item.component.CustomData data =
                candidate.output().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString("pentacle");
    }

    @Override
    protected void onNativeUpgradeChanged(mekanism.api.Upgrade upgrade) {
        previewIndex = 0;
    }

    @Override
    protected void onRecipeFinished(
            OccultismRecipeBridge.RecipeResult recipe) {
        craftRequested = false;
        requestedPentacle = "";
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
