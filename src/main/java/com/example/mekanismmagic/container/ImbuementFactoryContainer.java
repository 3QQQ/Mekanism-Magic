package com.example.mekanismmagic.container;

import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tier.FactoryTier;
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
        return 84;
    }

    @Override
    protected int getInventoryXOffset() {
        return tile.tier == FactoryTier.ULTIMATE ? 26 : 8;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= 200 && buttonId < 206) {
            tile.cycleSourceMode(buttonId - 200);
            return true;
        }
        return super.clickMenuButton(player, buttonId);
    }
}
