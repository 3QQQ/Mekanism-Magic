package com.example.mekanismmagic.integration.mekextras;

import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryLayout;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/** Container used only by Mekanism Extras imbuement factories. */
public final class ExtraImbuementFactoryContainer
        extends MekanismTileContainer<ExtraImbuementFactoryBlockEntity> {
    public ExtraImbuementFactoryContainer(
            int id, Inventory inventory,
            ExtraImbuementFactoryBlockEntity tile) {
        super(MekanismExtrasImbuementFactories.IMBUEMENT_FACTORY_CONTAINER,
                id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return 85;
    }

    @Override
    protected int getInventoryXOffset() {
        return ImbuementFactoryLayout.extraInventoryX(tile.tier.ordinal());
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= 200 && buttonId < 206) {
            tile.cycleSourceMode(buttonId - 200);
            return true;
        }
        if (buttonId >= 210 && buttonId < 216) {
            tile.cycleSourceMode(buttonId - 210, -1);
            return true;
        }
        if (buttonId == 230 || buttonId == 231) {
            tile.shiftAllSourceModes(buttonId == 230 ? 1 : -1);
            return true;
        }
        if (buttonId == 220) {
            tile.cycleCatalystPage(-1);
            return true;
        }
        if (buttonId == 221) {
            tile.cycleCatalystPage(1);
            return true;
        }
        int catalystPageSlot = buttonId - 300;
        int catalystIndex = tile.catalystPage()
                * com.example.mekanismmagic.integration.arsnouveau
                .CatalystLibraryStorage.PAGE_SIZE + catalystPageSlot;
        if (catalystPageSlot >= 0
                && catalystPageSlot < com.example.mekanismmagic.integration
                .arsnouveau.CatalystLibraryStorage.PAGE_SIZE
                && catalystIndex < tile.catalystVisibleSlotCount()) {
            tile.selectCatalystIdentifier(catalystIndex);
            return true;
        }
        if (buttonId == 399) {
            tile.clearCatalystIdentifierSelection();
            return true;
        }
        if (buttonId >= 400
                && buttonId < 400
                + tile.catalystIdentifierRecipeCount()) {
            tile.selectCatalystIdentifierRecipe(buttonId - 400);
            return true;
        }
        return super.clickMenuButton(player, buttonId);
    }
}
