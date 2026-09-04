package com.example.mekanismmagic.container;

import com.example.mekanismmagic.integration.arsnouveau.ArsSourceMachineBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryStorage;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * Common Ars machine container with Mekanism-style side-mode buttons for the
 * Source capability.
 */
public final class ArsSourceMachineContainer<
        TILE extends ArsSourceMachineBlockEntity>
        extends MekanismTileContainer<TILE> {
    public ArsSourceMachineContainer(
            int id,
            Inventory inventory, TILE tile) {
        super(com.example.mekanismmagic.integration.arsnouveau
                        .ArsNouveauRegistries.containerFor(tile),
                id, inventory, tile);
    }

    @Override
    protected int getInventoryXOffset() {
        return tile instanceof com.example.mekanismmagic.integration.arsnouveau
                .DrygmySimulatorBlockEntity
                || tile instanceof com.example.mekanismmagic.integration.arsnouveau
                .EnchantingApparatusProcessorBlockEntity ? 25 : 8;
    }

    @Override
    protected int getInventoryYOffset() {
        if (tile instanceof com.example.mekanismmagic.integration.arsnouveau
                .DrygmySimulatorBlockEntity) {
            return 126;
        }
        if (tile instanceof com.example.mekanismmagic.integration.arsnouveau
                .EnchantingApparatusProcessorBlockEntity) {
            return 126;
        }
        return 84;
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
        if (tile instanceof com.example.mekanismmagic.integration.arsnouveau
                .ImbuementProcessorBlockEntity imbuement) {
            if (buttonId == 220) {
                imbuement.cycleCatalystPage(-1);
                return true;
            }
            if (buttonId == 221) {
                imbuement.cycleCatalystPage(1);
                return true;
            }
            int windowSlot = buttonId - 300;
            if (windowSlot >= 0
                    && windowSlot < CatalystLibraryStorage.PAGE_SIZE) {
                int catalystIndex = CatalystLibraryStorage.absoluteIndex(
                        imbuement.catalystPage(), windowSlot);
                if (catalystIndex
                        < imbuement.catalystVisibleSlotCount()) {
                    imbuement.selectCatalystIdentifier(catalystIndex);
                }
                return true;
            }
            if (buttonId == 399) {
                imbuement.clearCatalystIdentifierSelection();
                return true;
            }
            if (buttonId >= 400
                    && buttonId < 400
                    + imbuement.catalystIdentifierRecipeCount()) {
                imbuement.selectCatalystIdentifierRecipe(buttonId - 400);
                return true;
            }
        }
        return super.clickMenuButton(player, buttonId);
    }
}
