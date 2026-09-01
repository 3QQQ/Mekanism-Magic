package com.example.mekanismmagic.inventory;

import com.example.mekanismmagic.MekanismMagic;
import mekanism.api.IContentsListener;
import mekanism.api.functions.ConstantPredicates;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.SelectedWindowData.WindowType;
import mekanism.common.inventory.container.slot.SlotOverlay;
import mekanism.common.inventory.container.slot.VirtualInventoryContainerSlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Save-compatibility slot for builds that stored the creative-magic item
 * outside Mekanism's upgrade component. It is intentionally never rendered;
 * loaded contents are migrated into the native component on the server.
 */
public final class CreativeMagicUpgradeSlot extends BasicInventorySlot {
    private CreativeMagicUpgradeSlot(@Nullable IContentsListener listener) {
        super(1, ConstantPredicates.manualOnly(),
                ConstantPredicates.alwaysFalseBi(),
                stack -> stack.is(MekanismMagic.CREATIVE_MAGIC_UPGRADE),
                listener, 0, 0);
        setSlotOverlay(SlotOverlay.UPGRADE);
    }

    public static CreativeMagicUpgradeSlot create(
            @Nullable IContentsListener listener) {
        return new CreativeMagicUpgradeSlot(listener);
    }

    @NotNull
    @Override
    public VirtualInventoryContainerSlot createContainerSlot() {
        return new VirtualInventoryContainerSlot(this,
                new SelectedWindowData(WindowType.UPGRADE),
                getSlotOverlay(), this::setStackUnchecked);
    }
}
