package com.example.mekanismmagic.integration.mekextras;

import com.example.mekanismmagic.integration.naturesaura.AuraSideConfigControls;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** One menu for all four extended altar factory tiers. */
public final class ExtraNatureAltarFactoryContainer extends
        MekanismTileContainer<ExtraNatureAltarFactoryBlockEntity> {
    public static final int SELECT_NEXT_RECIPE = 100;
    public static final int PREVIOUS_CATALYST_PAGE = 220;
    public static final int NEXT_CATALYST_PAGE = 221;
    public ExtraNatureAltarFactoryContainer(int id, Inventory inventory,
                                           ExtraNatureAltarFactoryBlockEntity tile) {
        super(MekanismExtrasNatureAltarFactories.NATURE_ALTAR_FACTORY_CONTAINER,
                id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return ExtraNatureAltarFactoryLayout.INVENTORY_Y;
    }

    @Override
    protected int getInventoryXOffset() {
        return ExtraNatureAltarFactoryLayout.inventoryX(tile.tier.ordinal());
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
