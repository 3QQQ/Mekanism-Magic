package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NativeRitualEngineBlockEntity extends NativeMagicMachineBlockEntity {
    private DictionaryInventorySlot dictionarySlot;
    private InputInventorySlot ritualSlot;
    private InputInventorySlot activationSlot;
    private InputInventorySlot sacrificeSlot;
    private OutputInventorySlot sacrificeRemainderSlot;
    private boolean dictionaryModuleOpen;

    public NativeRitualEngineBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.RITUAL_BLOCK.get().builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper, IContentsListener listener) {
        List<mekanism.api.inventory.IInventorySlot> inputs = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                int index = row * 4 + column;
                inputs.add(registerLogicalSlot(helper, index,
                        InputInventorySlot.at(listener, 69 + column * 18, 31 + row * 18)));
            }
        }
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 176, 58));
        sacrificeRemainderSlot = registerLogicalSlot(helper,
                RITUAL_REMAINDER_SLOT,
                OutputInventorySlot.at(listener, 176, 80));
        ritualSlot = registerLogicalSlot(helper, RITUAL_SLOT,
                InputInventorySlot.at(OccultismRecipeBridge::isRitualSelector, listener, 20, 39));
        activationSlot = registerLogicalSlot(helper, ACTIVATION_SLOT,
                InputInventorySlot.at(OccultismRecipeBridge::isActivationItem,
                        listener, 20, 62));
        // Shared sacrifice slot: spawn eggs are consumed; supported filled
        // containers return empty through the dedicated remainder output.
        sacrificeSlot = registerLogicalSlot(helper, SACRIFICE_SLOT,
                InputInventorySlot.at(OccultismRecipeBridge::isSacrificeItem,
                        listener, 20, 85));
        dictionarySlot = registerLogicalSlot(helper, DICTIONARY_SLOT,
                new DictionaryInventorySlot(this, listener, 240, 104));
        var itemConfig = setupNativeItemIO(
                inputs, List.of(outputSlot, sacrificeRemainderSlot),
                List.of());
        addNativeItemSlotInfo(itemConfig, DataType.EXTRA,
                true, false, List.of(activationSlot));
        addNativeItemSlotInfo(itemConfig, DataType.INPUT_2,
                true, false, List.of(sacrificeSlot));
    }

    @Override
    protected int energySlotX() {
        return 20;
    }

    @Override
    protected int energySlotY() {
        return 16;
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        return OccultismRecipeBridge.findRitualRecipe(level, inventory,
                inventory.getStackInSlot(RITUAL_SLOT),
                inventory.getStackInSlot(ACTIVATION_SLOT),
                inventory.getStackInSlot(SACRIFICE_SLOT),
                !inventory.getStackInSlot(DICTIONARY_SLOT).isEmpty());
    }

    /**
     * A ritual selector by itself is not enough to start a useful lookup.
     * Require both the selected miniature ritual and at least one of the
     * sixteen material inputs before walking Occultism's ritual recipe list.
     * This keeps a configured but empty ritual engine nearly idle.
     */
    @Override
    protected boolean hasAnyRecipeInput() {
        if (ritualSlot == null || ritualSlot.getStack().isEmpty()) {
            return false;
        }
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

    @Override
    protected int baseEnergyPerTick() {
        return 1_200;
    }

    @Override
    protected long recipeLookupRevision() {
        return OccultismRecipeBridge.recipeRevision();
    }

    @Override
    public List<mekanism.api.inventory.IInventorySlot>
    mekanismMagicPatternInputs() {
        List<mekanism.api.inventory.IInventorySlot> slots =
                new ArrayList<>();
        // Put constrained ports before the sixteen generic material slots so
        // a bounded AE route cannot park an activation or sacrifice item in
        // an otherwise-valid generic slot while leaving its dedicated slot
        // empty.
        if (activationSlot != null) {
            slots.add(activationSlot);
        }
        if (sacrificeSlot != null) {
            slots.add(sacrificeSlot);
        }
        slots.addAll(super.mekanismMagicPatternInputs());
        return List.copyOf(slots);
    }

    @Override
    public List<mekanism.api.inventory.IInventorySlot>
    mekanismMagicManualOnlySlots() {
        List<mekanism.api.inventory.IInventorySlot> slots =
                new ArrayList<>(2);
        if (ritualSlot != null) {
            slots.add(ritualSlot);
        }
        if (dictionarySlot != null) {
            slots.add(dictionarySlot);
        }
        return List.copyOf(slots);
    }

    public Optional<OccultismRecipeBridge.RitualAutomationPlan>
    mekanismMagicPlanRitualAutomation(
            List<OccultismRecipeBridge.RitualAutomationInput> inputs) {
        if (level == null || ritualSlot == null) {
            return Optional.empty();
        }
        return OccultismRecipeBridge.planRitualAutomation(
                level, ritualSlot.getStack(), inputs,
                dictionarySlot != null
                        && !dictionarySlot.getStack().isEmpty());
    }

    public void setDictionaryModuleOpen(boolean open) {
        dictionaryModuleOpen = open;
    }

    private boolean isDictionaryContainerSlotActive() {
        return level == null || !level.isClientSide() || dictionaryModuleOpen;
    }

    private static final class DictionaryInventorySlot extends BasicInventorySlot {
        private final NativeRitualEngineBlockEntity tile;
        private final int x;
        private final int y;

        private DictionaryInventorySlot(NativeRitualEngineBlockEntity tile,
                                        IContentsListener listener, int x, int y) {
            super((stack, automation) -> true, (stack, automation) -> true,
                    OccultismRecipeBridge::isDictionaryOfSpirits, listener, x, y);
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
                    return tile.isDictionaryContainerSlotActive();
                }
            };
        }
    }
}
