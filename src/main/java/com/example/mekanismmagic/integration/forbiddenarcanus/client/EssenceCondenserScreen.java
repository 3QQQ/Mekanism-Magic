package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.client.gui.GuiMachineReadout;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.container.RetainedSlotAccess;
import com.example.mekanismmagic.integration.forbiddenarcanus.EssenceCondenserBlockEntity;
import com.example.mekanismmagic.integration.forbiddenarcanus.EssenceCondenserContainer;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusForge261Bridge;
import mekanism.api.inventory.IInventorySlot;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/** Four independent sample lanes, with all balances and timings read from the synced tile. */
public final class EssenceCondenserScreen extends NativeMagicMachineScreen<EssenceCondenserBlockEntity, EssenceCondenserContainer> {
    private static final String[] NAMES = {"aureal", "souls", "blood", "experience"};
    private static final int[] COLORS = {0xFFE8BF5B, 0xFFB4A4DF, 0xFFCC5263, 0xFF91C56B};
    private List<InventoryContainerSlot> workSlots = List.of();

    public EssenceCondenserScreen(EssenceCondenserContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MachinePanelLayout.ESSENCE_CONDENSER_HEIGHT);
        imageWidth = MachinePanelLayout.ESSENCE_CONDENSER_WIDTH;
    }

    @Override protected boolean showUpArrow() { return false; }
    @Override protected boolean showProgress() { return false; }
    @Override protected boolean usesRetainedModule() { return false; }
    @Override protected int energyBarHeight() { return 32; }

    @Override protected void prepareMachineGuiElements() {
        restoreWorkSlots();
        var tile = getTileEntity();
        List<IInventorySlot> inventories = new ArrayList<>();
        List<MachinePanelLayout.Box> positions = new ArrayList<>();
        inventories.add(tile.getNativeEnergySlot());
        positions.add(MachinePanelLayout.ESSENCE_CONDENSER_ENERGY);
        for (int channel = 0; channel < EssenceCondenserBlockEntity.CHANNEL_COUNT; channel++) {
            inventories.add(tile.getSampleSlot(channel));
            positions.add(new MachinePanelLayout.Box(MachinePanelLayout.essenceCondenserSampleX(channel),
                    MachinePanelLayout.essenceCondenserSampleY(channel), 16, 16));
            inventories.add(tile.getVesselInputSlot(channel));
            positions.add(new MachinePanelLayout.Box(MachinePanelLayout.essenceCondenserVesselInputX(channel),
                    MachinePanelLayout.essenceCondenserVesselInputY(channel), 16, 16));
            inventories.add(tile.getVesselOutputSlot(channel));
            positions.add(new MachinePanelLayout.Box(MachinePanelLayout.essenceCondenserVesselOutputX(channel),
                    MachinePanelLayout.essenceCondenserVesselOutputY(channel), 16, 16));
        }
        List<InventoryContainerSlot> bindings = new ArrayList<>(inventories.size());
        for (IInventorySlot inventory : inventories) {
            var matches = menu.getInventoryContainerSlots().stream()
                    .filter(slot -> slot.getInventorySlot() == inventory).toList();
            if (matches.size() != 1 || !(matches.getFirst() instanceof RetainedSlotAccess)) {
                throw new IllegalStateException("Essence condenser deck needs one original menu slot per role");
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

    @Override public void removed() {
        restoreWorkSlots();
        super.removed();
    }

    @Override protected void addMachineGuiElements() {
        for (int channel = 0; channel < EssenceCondenserBlockEntity.CHANNEL_COUNT; channel++) {
            final int fixed = channel;
            addRenderableWidget(GuiMachineInstrument.stacked(this, MachinePanelLayout.essenceCondenserBank(channel),
                    () -> Component.translatable("gui.mekanism_magic.panel.essence." + NAMES[fixed]),
                    () -> Component.literal(GuiMachineReadout.compact(getTileEntity().getStoredEssence(fixed))),
                    () -> COLORS[fixed], () -> bankDetails(fixed),
                    () -> getTileEntity().getStoredEssence(fixed) / (double) Math.max(1, getTileEntity().getEssenceCapacity(fixed))));
        }
    }

    private List<Component> bankDetails(int channel) {
        var tile = getTileEntity();
        var sample = tile.getSampleSlot(channel).getStack();
        int yield = HephaestusForge261Bridge.previewRetainedEssenceSample(sample)
                .filter(value -> value.channel() == channel).map(HephaestusForge261Bridge.EssenceSample::amount).orElse(0);
        return List.of(
                Component.translatable("gui.mekanism_magic.essence_condenser.bank." + NAMES[channel]),
                Component.translatable("gui.mekanism_magic.essence_condenser.balance",
                        tile.getStoredEssence(channel), tile.getEssenceCapacity(channel)),
                Component.translatable(tile.getLaneStatusKey(channel)),
                Component.translatable("gui.mekanism_magic.essence_condenser.progress",
                        tile.getLaneProgress(channel), tile.getLaneDuration(channel)),
                Component.translatable("gui.mekanism_magic.essence_condenser.energy", tile.getLaneEnergyPerTick(channel)),
                Component.translatable("gui.mekanism_magic.essence_condenser.yield", yield));
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        for (int channel = 0; channel < EssenceCondenserBlockEntity.CHANNEL_COUNT; channel++) {
            var progress = MachinePanelLayout.essenceCondenserProgress(channel);
            int x = leftPos + progress.x();
            int y = topPos + progress.y();
            graphics.fill(x, y, x + progress.width(), y + progress.height(), MagicGuiTheme.readoutTrack());
            int width = (int) (progress.width() * Math.clamp(getTileEntity().getLaneProgress(channel)
                    / (double) Math.max(1, getTileEntity().getLaneDuration(channel)), 0, 1));
            if (width > 0) graphics.fill(x, y, x + width, y + progress.height(), COLORS[channel]);
            if (channel < EssenceCondenserBlockEntity.CHANNEL_COUNT - 1) {
                int separatorX = leftPos + MachinePanelLayout.essenceCondenserBank(channel).right();
                graphics.fill(separatorX, topPos + 27, separatorX + 1, topPos + 81, MagicGuiTheme.readoutTrack());
            }
        }
    }

    @Override protected void addSlots() {
        super.addSlots();
        for (var child : children()) {
            if (!(child instanceof GuiSlot slot)) continue;
            for (int channel = 0; channel < EssenceCondenserBlockEntity.CHANNEL_COUNT; channel++) {
                if (!isGuiSlotForInventory(slot, getTileEntity().getSampleSlot(channel))) continue;
                final int fixed = channel;
                slot.hover(ignored -> List.of(Component.translatable("tooltip.mekanism_magic.essence_condenser.sample",
                        Component.translatable("gui.mekanism_magic.essence_condenser.bank." + NAMES[fixed]))));
                slot.active = false;
            }
            for (int channel = 0; channel < EssenceCondenserBlockEntity.CHANNEL_COUNT; channel++) {
                final int fixed = channel;
                if (isGuiSlotForInventory(slot, getTileEntity().getVesselInputSlot(channel))) {
                    slot.hover(ignored -> List.of(Component.translatable(
                            "tooltip.mekanism_magic.essence_condenser.vessel_input",
                            Component.translatable("gui.mekanism_magic.essence_condenser.bank." + NAMES[fixed]))));
                    slot.active = false;
                } else if (isGuiSlotForInventory(slot, getTileEntity().getVesselOutputSlot(channel))) {
                    slot.hover(ignored -> List.of(Component.translatable(
                            "tooltip.mekanism_magic.essence_condenser.vessel_output",
                            Component.translatable("gui.mekanism_magic.essence_condenser.bank." + NAMES[fixed]))));
                    slot.active = false;
                }
            }
        }
    }

}
