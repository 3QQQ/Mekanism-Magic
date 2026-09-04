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
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

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
    private static final int INPUT_SETTLE_TICKS = 5;
    private List<mekanism.api.inventory.IInventorySlot> chalkSlots;
    private boolean chalkModuleOpen;
    private int inputSettleTicks;
    private List<MachineRecipeResult> cachedPreviewCandidates;
    private long cachedPreviewRevision = Long.MIN_VALUE;

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
        // Every generated recipe now has a unique dye-set signature. A valid
        // batch must therefore resolve to exactly one pentacle; fail closed
        // instead of selecting an arbitrary candidate if a malformed input
        // deliberately contains multiple marker combinations.
        return candidates.size() == 1
                ? Optional.of(candidates.getFirst()) : Optional.empty();
    }

    /** A complete material-and-dye signature starts automatically. */
    @Override
    protected boolean hasAnyRecipeInput() {
        return hasMaterialInput();
    }

    private boolean hasMaterialInput() {
        for (int index = 0; index < INPUT_SLOTS; index++) {
            mekanism.api.inventory.IInventorySlot slot =
                    logicalSlots().get(index);
            if (slot != null && !slot.getStack().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mekanismMagicIsBusy() {
        return super.mekanismMagicIsBusy() || hasMaterialInput()
                || outputSlot != null && !outputSlot.getStack().isEmpty();
    }

    private List<MachineRecipeResult> previewCandidates(
            ItemStackHandler inventory) {
        if (level == null) {
            return List.of();
        }
        long revision = OccultismRecipeBridge.recipeRevision();
        if (cachedPreviewCandidates == null
                || cachedPreviewRevision != revision) {
            cachedPreviewCandidates =
                    OccultismRecipeBridge.findMiniRitualCandidates(
                            level, inventory);
            cachedPreviewRevision = revision;
        }
        return cachedPreviewCandidates;
    }

    @Override
    protected int baseEnergyPerTick() {
        return 300;
    }

    @Override
    protected long recipeLookupRevision() {
        return OccultismRecipeBridge.recipeRevision();
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
