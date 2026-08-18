package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeDimensionMinerBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class NativeDimensionMinerScreen extends NativeMagicMachineScreen<
        NativeDimensionMinerBlockEntity> {
    public NativeDimensionMinerScreen(
            MekanismTileContainer<NativeDimensionMinerBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 166);
        imageWidth = 210;
        inventoryLabelX = 26;
    }

    @Override
    protected int workArrowX() {
        return 38;
    }

    @Override
    protected int workArrowY() {
        return 38;
    }

    @Override
    protected int workProgressX() {
        return 38;
    }

    @Override
    protected int workProgressY() {
        return 38;
    }

    @Override
    protected int energyBarX() {
        return 4;
    }
}
