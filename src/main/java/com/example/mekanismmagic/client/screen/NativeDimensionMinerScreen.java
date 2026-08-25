package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeDimensionMinerBlockEntity;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.Locale;

public final class NativeDimensionMinerScreen extends NativeMagicMachineScreen<
        NativeDimensionMinerBlockEntity,
        MekanismTileContainer<NativeDimensionMinerBlockEntity>> {
    public NativeDimensionMinerScreen(
            MekanismTileContainer<NativeDimensionMinerBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 208);
        imageWidth = 210;
        inventoryLabelX = 26;
        inventoryLabelY = 114;
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected boolean showProgress() {
        return false;
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics,
                                      net.minecraft.world.item.ItemStack stack,
                                      Slot slot,
                                      String countText) {
        if (isMinerOutputSlot(slot) && stack.getCount() >= 100) {
            countText = formatCompactCount(stack.getCount());
        }
        super.renderSlotContents(graphics, stack, slot, countText);
    }

    private boolean isMinerOutputSlot(Slot slot) {
        return slot instanceof InventoryContainerSlot inventorySlot
                && getTileEntity().isMinerOutputSlot(
                        inventorySlot.getInventorySlot());
    }

    private static String formatCompactCount(int count) {
        final double unit;
        final String suffix;
        if (count >= 100_000_000) {
            unit = 1_000_000_000D;
            suffix = "b";
        } else if (count >= 100_000) {
            unit = 1_000_000D;
            suffix = "m";
        } else {
            unit = 1_000D;
            suffix = "k";
        }
        // Truncate instead of rounding so 999,999 is shown as 999.9k
        // rather than overflowing visually to 1000k.
        double scaled = Math.floor(count / unit * 10D) / 10D;
        return String.format(Locale.ROOT, "%.1f%s", scaled, suffix)
                .replace(".0", "");
    }

}
