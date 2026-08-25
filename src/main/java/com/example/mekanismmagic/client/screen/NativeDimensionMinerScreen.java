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
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                       float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        for (Slot slot : getMenu().slots) {
            if (!(slot instanceof InventoryContainerSlot inventorySlot)
                    || !getTileEntity().isMinerOutputSlot(
                    inventorySlot.getInventorySlot())) {
                continue;
            }
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            if (stack.isEmpty() || stack.getCount() < 100) {
                continue;
            }
            String text = formatCompactCount(stack.getCount());
            int width = font.width(text);
            int x = leftPos + slot.x + 17 - width;
            int y = topPos + slot.y + 8;
            graphics.fill(x - 1, y - 1, x + width + 1, y + 8,
                    0xB0000000);
            graphics.drawString(font, text, x, y, 0xFFFFFF, true);
        }
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
        double scaled = Math.floor(count / unit * 10D) / 10D;
        return String.format(Locale.ROOT, "%.1f%s", scaled, suffix)
                .replace(".0", "");
    }

    @Override
    protected int workArrowX() {
        return 38;
    }

    @Override
    protected int workArrowY() {
        return 38;
    }

    @Override
    protected int workProgressX() {
        return 38;
    }

    @Override
    protected int workProgressY() {
        return 38;
    }

}
