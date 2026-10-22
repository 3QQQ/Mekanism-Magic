package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.container.RetainedSlotAccess;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraScreenLayout;
import com.example.mekanismmagic.integration.naturesaura.TreeRitualProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.TreeRitualProcessorContainer;
import com.example.mekanismmagic.integration.naturesaura.TreeRitualRecipeBridge;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.gui.element.progress.ProgressType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;

public final class TreeRitualProcessorScreen extends NativeMagicMachineScreen<TreeRitualProcessorBlockEntity,
        TreeRitualProcessorContainer> {
    private InventoryContainerSlot chargingSlot;

    public TreeRitualProcessorScreen(TreeRitualProcessorContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title, NatureAuraScreenLayout.TREE_HEIGHT);
    }

    @Override protected boolean showUpArrow() { return false; }
    @Override protected int workProgressX() { return NatureAuraScreenLayout.TREE_PROGRESS_X; }
    @Override protected int workProgressY() { return NatureAuraScreenLayout.TREE_PROGRESS_Y; }
    @Override protected ProgressType progressType() { return ProgressType.SMALL_RIGHT; }
    @Override protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{NaturesAuraRecipeViewerTypes.TREE_RITUAL};
    }

    @Override protected void prepareMachineGuiElements() {
        restoreChargingSlot();
        var matches = menu.getInventoryContainerSlots().stream()
                .filter(slot -> slot.getInventorySlot() == getTileEntity().getNativeEnergySlot()).toList();
        if (matches.size() != 1 || !(matches.getFirst() instanceof RetainedSlotAccess)) {
            throw new IllegalStateException("Tree ritual needs its original FE charging slot");
        }
        chargingSlot = matches.getFirst();
        ((RetainedSlotAccess) chargingSlot).mekanismMagic$positionRetainedSlot(
                MachinePanelLayout.TREE_ENERGY.x(), MachinePanelLayout.TREE_ENERGY.y(), true);
    }

    private void restoreChargingSlot() {
        if (chargingSlot == null) return;
        ((RetainedSlotAccess) chargingSlot).mekanismMagic$restoreRetainedSlot();
        chargingSlot = null;
    }

    @Override public void removed() {
        restoreChargingSlot();
        super.removed();
    }

    @Override protected void addSlots() {
        super.addSlots();
        var tile = getTileEntity();
        for (var child : children()) {
            if (!(child instanceof GuiSlot slot)) continue;
            for (int index = 0; index < TreeRitualRecipeBridge.MATERIAL_SLOTS; index++) {
                emptyTooltip(slot, tile.getLogicalSlot(index), "material_slot");
            }
            emptyTooltip(slot, tile.getLogicalSlot(TreeRitualProcessorBlockEntity.SAPLING_SLOT), "sapling_slot");
            emptyTooltip(slot, tile.getLogicalSlot(TreeRitualProcessorBlockEntity.GOLD_POWDER_SLOT), "gold_powder_slot");
            emptyTooltip(slot, tile.getNativeOutputSlot(), "output_slot");
        }
    }

    private void emptyTooltip(GuiSlot visual, IInventorySlot inventory, String key) {
        if (inventory != null && isGuiSlotForInventory(visual, inventory)) {
            visual.hover(ignored -> inventory.isEmpty()
                    ? List.of(Component.translatable("tooltip.mekanism_magic.tree_ritual." + key)) : List.of());
            visual.active = false;
        }
    }
}
