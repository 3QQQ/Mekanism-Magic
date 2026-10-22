package com.example.mekanismmagic.integration.naturesaura;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * Nature altar menu with an explicit server-authoritative Aura side control.
 */
public final class NatureAltarProcessorContainer extends
        MekanismTileContainer<NatureAltarProcessorBlockEntity> {
    public static final int SELECT_NEXT_RECIPE = 100;
    public static final int PREVIOUS_CATALYST_PAGE = 220;
    public static final int NEXT_CATALYST_PAGE = 221;
    public static final int SIDE_MODE_BUTTON_START =
            AuraSideConfigControls.FORWARD_BUTTON_START;

    public NatureAltarProcessorContainer(
            int id, Inventory inventory,
            NatureAltarProcessorBlockEntity tile) {
        super(NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_CONTAINER,
                id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return NatureAuraScreenLayout.COMPACT_INVENTORY_Y;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == PREVIOUS_CATALYST_PAGE
                || buttonId == NEXT_CATALYST_PAGE) {
            if (!AuraSideConfigControls.canConfigure(this, player)) {
                return false;
            }
            getTileEntity().cycleCatalystPage(
                    buttonId == NEXT_CATALYST_PAGE ? 1 : -1);
            return true;
        }
        if (buttonId == SELECT_NEXT_RECIPE) {
            if (!AuraSideConfigControls.canConfigure(this, player)) {
                return false;
            }
            getTileEntity().cycleMatchingRecipe();
            return true;
        }
        if (AuraSideConfigControls.isAuraSideButton(buttonId)) {
            return AuraSideConfigControls.handleButton(this, player, buttonId);
        }
        return super.clickMenuButton(player, buttonId);
    }
}
