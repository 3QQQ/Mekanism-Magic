package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.GuiMachineReadout;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.container.RetainedSlotAccess;
import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusForgeProcessorBlockEntity;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusForgeProcessorContainer;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.client.gui.element.slot.GuiSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.ArrayList;

/** Internal forge tier and four independent essence banks; no physical core or hammer control. */
public final class HephaestusForgeProcessorScreen extends NativeMagicMachineScreen<HephaestusForgeProcessorBlockEntity, HephaestusForgeProcessorContainer> {
    private static final int[] COLORS = {0xFFE8BF5B, 0xFFB4A4DF, 0xFFCC5263, 0xFF91C56B};
    private static final String[] CHANNELS = {"aureal", "souls", "blood", "experience"};
    private List<InventoryContainerSlot> workSlots = List.of();

    public HephaestusForgeProcessorScreen(HephaestusForgeProcessorContainer container, Inventory inventory, Component title) {
        super(container, inventory, title, MachinePanelLayout.FORGE_HEIGHT);
    }

    @Override
    protected void prepareMachineGuiElements() {
        restoreWorkSlots();
        var tile = getTileEntity();
        List<IInventorySlot> inventories = new ArrayList<>();
        List<MachinePanelLayout.Box> positions = new ArrayList<>();
        inventories.add(tile.getMainInputSlot());
        positions.add(MachinePanelLayout.FORGE_MAIN);
        for (int index = 0; index < 8; index++) {
            inventories.add(tile.getAuxInputSlot(index));
            positions.add(new MachinePanelLayout.Box(MachinePanelLayout.forgeAuxiliaryX(index),
                    MachinePanelLayout.forgeAuxiliaryY(index), 16, 16));
        }
        inventories.add(tile.getNativeOutputSlot());
        positions.add(MachinePanelLayout.FORGE_OUTPUT);
        inventories.addAll(List.of(tile.getEssenceInputSlot(), tile.getWorkingEssenceSlot(), tile.getReturnSlot()));
        for (int index = 0; index < 3; index++) positions.add(new MachinePanelLayout.Box(
                MachinePanelLayout.forgeVesselX(index), MachinePanelLayout.forgeVesselY(index), 16, 16));
        List<InventoryContainerSlot> bindings = new ArrayList<>(inventories.size());
        for (IInventorySlot inventory : inventories) {
            var matches = menu.getInventoryContainerSlots().stream()
                    .filter(slot -> slot.getInventorySlot() == inventory).toList();
            if (matches.size() != 1 || !(matches.getFirst() instanceof RetainedSlotAccess)) {
                throw new IllegalStateException("Forge work deck needs one original menu slot per role");
            }
            bindings.add(matches.getFirst());
        }
        workSlots = List.copyOf(bindings);
        for (int index = 0; index < workSlots.size(); index++) {
            var position = positions.get(index);
            ((RetainedSlotAccess) workSlots.get(index)).mekanismMagic$positionRetainedSlot(
                    position.x(), position.y(), true);
        }
    }

    private void restoreWorkSlots() {
        for (InventoryContainerSlot slot : workSlots) {
            ((RetainedSlotAccess) slot).mekanismMagic$restoreRetainedSlot();
        }
        workSlots = List.of();
    }

    @Override
    public void removed() {
        restoreWorkSlots();
        super.removed();
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected int workProgressX() {
        return MachinePanelLayout.FORGE_PROGRESS_X;
    }

    @Override
    protected int workProgressY() {
        return MachinePanelLayout.FORGE_PROGRESS_Y;
    }

    @Override
    protected void addMachineGuiElements() {
        addRenderableWidget(GuiMachineInstrument.stacked(this, MachinePanelLayout.FORGE_TIER,
                () -> Component.translatable("gui.mekanism_magic.hephaestus.instrument.tier"),
                () -> Component.literal(getTileEntity().getUpgradeTargetTier() > 0
                        ? getTileEntity().getForgeTier() + " → " + getTileEntity().getUpgradeTargetTier()
                        : Integer.toString(getTileEntity().getForgeTier())),
                MagicGuiTheme::accentRitual,
                () -> List.of(Component.translatable("gui.mekanism_magic.hephaestus.tier", getTileEntity().getForgeTier()),
                        Component.translatable("tooltip.mekanism_magic.hephaestus.internal_tier")), null));
        for (int channel = 0; channel < CHANNELS.length; channel++) {
            final int index = channel;
            addRenderableWidget(GuiMachineInstrument.stacked(this, MachinePanelLayout.forgeEssence(index),
                    () -> Component.translatable("gui.mekanism_magic.panel.essence." + CHANNELS[index]),
                    () -> Component.literal(GuiMachineReadout.compact(getTileEntity().getStoredEssence(index))),
                    () -> COLORS[index], () -> List.of(
                            Component.translatable("tooltip.mekanism_magic.hephaestus.essence." + CHANNELS[index]),
                            Component.translatable("tooltip.mekanism_magic.hephaestus.stored", getTileEntity().getStoredEssence(index)),
                            Component.translatable("tooltip.mekanism_magic.hephaestus.capacity", getTileEntity().getEssenceCapacity(index))),
                    () -> getTileEntity().getEssenceCapacity(index) <= 0 ? 0
                            : getTileEntity().getStoredEssence(index) / (double) getTileEntity().getEssenceCapacity(index)));
        }
    }

    @Override
    protected void addSlots() {
        super.addSlots();
        var tile = getTileEntity();
        for (var child : children()) {
            if (!(child instanceof GuiSlot slot)) continue;
            if (isGuiSlotForInventory(slot, tile.getMainInputSlot())) {
                emptyTooltip(slot, tile.getMainInputSlot(), "main");
            } else if (isGuiSlotForInventory(slot, tile.getNativeOutputSlot())) {
                emptyTooltip(slot, tile.getNativeOutputSlot(), "output");
            } else if (isGuiSlotForInventory(slot, tile.getEssenceInputSlot())) {
                emptyTooltip(slot, tile.getEssenceInputSlot(), "essence_input");
            } else if (isGuiSlotForInventory(slot, tile.getWorkingEssenceSlot())) {
                emptyTooltip(slot, tile.getWorkingEssenceSlot(), "working");
            } else if (isGuiSlotForInventory(slot, tile.getReturnSlot())) {
                emptyTooltip(slot, tile.getReturnSlot(), "return");
            } else if (isRetainedGuiSlot(slot)) {
                int index = java.util.stream.IntStream.range(0, 4)
                        .filter(candidate -> isGuiSlotForInventory(slot, tile.getEnhancerSlot(candidate))).findFirst().orElse(-1);
                if (index < 0) continue;
                slot.hover(ignored -> {
                    if (!tile.getEnhancerSlot(index).isEmpty()) return List.of();
                    int enabled = Math.min(4, Math.max(0, tile.getForgeTier()));
                    return List.of(Component.translatable("tooltip.mekanism_magic.hephaestus.enhancer_"
                                    + (index < enabled ? "active" : "locked"), index + 1),
                            Component.translatable("tooltip.mekanism_magic.hephaestus.enhancer_rule", enabled));
                });
                slot.active = false;
            } else {
                for (int index = 0; index < 8; index++) {
                    if (isGuiSlotForInventory(slot, tile.getAuxInputSlot(index))) {
                        emptyTooltip(slot, tile.getAuxInputSlot(index), "auxiliary");
                        break;
                    }
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        int rule = MagicGuiTheme.readoutTrack();
        graphics.fill(leftPos + 28, topPos + 44, leftPos + 29, topPos + 78, rule);
    }

    private static void emptyTooltip(GuiSlot slot, IInventorySlot inventory, String key) {
        // Reuse the native visual slot. Occupied slots retain item/configurator
        // tooltips, and the actual container slot keeps all click handling.
        slot.hover(ignored -> inventory.isEmpty()
                ? List.of(Component.translatable("tooltip.mekanism_magic.hephaestus.slot." + key)) : List.of());
        slot.active = false;
    }

}
