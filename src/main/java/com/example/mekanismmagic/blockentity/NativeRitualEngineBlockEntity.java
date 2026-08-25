package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;

import com.example.mekanismmagic.NativeMekanismRegistries;
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
import net.minecraftforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class NativeRitualEngineBlockEntity extends NativeMagicMachineBlockEntity {
    private DictionaryInventorySlot dictionarySlot;
    private InputInventorySlot ritualSlot;
    private BasicInventorySlot activationSlot;
    private BasicInventorySlot sacrificeSlot;
    private boolean dictionaryModuleOpen;

    public NativeRitualEngineBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.RITUAL_BLOCK, pos, state);
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
        ritualSlot = registerLogicalSlot(helper, RITUAL_SLOT,
                InputInventorySlot.at(OccultismRecipeBridge::isRitualSelector, listener, 20, 39));
        activationSlot = registerLogicalSlot(helper, ACTIVATION_SLOT,
                BasicInventorySlot.at(OccultismRecipeBridge::isActivationItem, listener, 20, 62));
        // Shared sacrifice slot: spawn eggs are consumed; supported filled
        // containment items remain and are emptied in place.
        sacrificeSlot = registerLogicalSlot(helper, SACRIFICE_SLOT,
                BasicInventorySlot.at(OccultismRecipeBridge::isSacrificeItem, listener, 20, 85));
        dictionarySlot = registerLogicalSlot(helper, DICTIONARY_SLOT,
                new DictionaryInventorySlot(this, listener, 240, 104));
        var itemConfig = setupNativeItemIO(
                inputs, List.of(outputSlot), List.of());
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
    protected Optional<OccultismRecipeBridge.RecipeResult> findRecipe(ItemStackHandler inventory) {
        return OccultismRecipeBridge.findRitualRecipe(level, inventory,
                inventory.getStackInSlot(RITUAL_SLOT),
                inventory.getStackInSlot(ACTIVATION_SLOT),
                inventory.getStackInSlot(SACRIFICE_SLOT),
                !inventory.getStackInSlot(DICTIONARY_SLOT).isEmpty());
    }

    @Override
    protected boolean hasAnyRecipeInput() {
        if (ritualSlot == null || ritualSlot.getStack().isEmpty()) {
            return false;
        }
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
    protected int baseEnergyPerTick() {
        return 1_200;
    }

    @Override
    public List<mekanism.api.inventory.IInventorySlot>
    mekanismMagicPatternInputs() {
        List<mekanism.api.inventory.IInventorySlot> slots =
                new ArrayList<>(super.mekanismMagicPatternInputs());
        if (activationSlot != null) {
            slots.add(activationSlot);
        }
        if (sacrificeSlot != null) {
            slots.add(sacrificeSlot);
        }
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

    public void setDictionaryModuleOpen(boolean open) {
        dictionaryModuleOpen = open;
    }

    private boolean isDictionaryContainerSlotActive() {
        // Keep server-side slot validation available while hiding the client
        // slot and item stack with the collapsible module.
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

