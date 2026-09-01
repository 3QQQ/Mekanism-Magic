package com.example.mekanismmagic.container;

import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryLayout;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;

public final class ImbuementFactoryContainer
        extends MekanismTileContainer<ImbuementFactoryBlockEntity> {
    public ImbuementFactoryContainer(int id, Inventory inventory,
                                     ImbuementFactoryBlockEntity tile) {
        super(com.example.mekanismmagic.integration.arsnouveau
                        .ArsNouveauRegistries.IMBUEMENT_FACTORY_CONTAINER,
                id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        // Match Mekanism's FactoryContainer exactly.
        return 85;
    }

    @Override
    protected int getInventoryXOffset() {
        return ImbuementFactoryLayout.standardInventoryX(tile.tier);
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
        if (buttonId >= 300
                && buttonId < 300
                + com.example.mekanismmagic.blockentity
                .NativeMagicMachineBlockEntity
                .CATALYST_LIBRARY_SLOT_COUNT) {
            tile.selectCatalystIdentifier(buttonId - 300);
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
