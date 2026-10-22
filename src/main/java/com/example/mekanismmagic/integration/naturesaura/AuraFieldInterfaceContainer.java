package com.example.mekanismmagic.integration.naturesaura;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** Minimal mode-control menu for the Aura field interface. */
public final class AuraFieldInterfaceContainer
        extends MekanismTileContainer<AuraFieldInterfaceBlockEntity> {
    public static final int CYCLE_MODE_BUTTON = 500;
    public static final int SIDE_MODE_BUTTON_START =
            AuraSideConfigControls.FORWARD_BUTTON_START;

    public AuraFieldInterfaceContainer(int id, Inventory inventory,
                                       AuraFieldInterfaceBlockEntity tile) {
        super(NaturesAuraRegistries.AURA_FIELD_INTERFACE_CONTAINER,
                id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return NatureAuraScreenLayout.COMPACT_INVENTORY_Y;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == CYCLE_MODE_BUTTON) {
            if (!AuraSideConfigControls.canConfigure(this, player)) {
                return false;
            }
            tile.cycleFieldMode();
            return true;
        }
        if (AuraSideConfigControls.isAuraSideButton(buttonId)) {
            return AuraSideConfigControls.handleButton(this, player, buttonId);
        }
        return super.clickMenuButton(player, buttonId);
    }
}
