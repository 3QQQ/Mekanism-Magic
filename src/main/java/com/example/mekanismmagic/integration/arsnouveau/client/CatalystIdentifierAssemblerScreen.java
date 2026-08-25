package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierAssemblerBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CatalystIdentifierAssemblerScreen extends NativeMagicMachineScreen<
        CatalystIdentifierAssemblerBlockEntity,
        MekanismTileContainer<CatalystIdentifierAssemblerBlockEntity>> {
    public CatalystIdentifierAssemblerScreen(
            MekanismTileContainer<CatalystIdentifierAssemblerBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 208);
        imageWidth = 210;
        inventoryLabelX = 26;
    }

    @Override
    protected int workArrowX() {
        return 145;
    }

    @Override
    protected int workArrowY() {
        return 62;
    }

    @Override
    protected int workProgressX() {
        return 145;
    }

    @Override
    protected int workProgressY() {
        return 62;
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected mekanism.client.gui.element.progress.ProgressType progressType() {
        return mekanism.client.gui.element.progress.ProgressType.SMALL_RIGHT;
    }
}
