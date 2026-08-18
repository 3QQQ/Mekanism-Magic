package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeSpiritProcessorBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class NativeSpiritScreen extends NativeMagicMachineScreen<
        NativeSpiritProcessorBlockEntity> {
    public NativeSpiritScreen(MekanismTileContainer<NativeSpiritProcessorBlockEntity> container,
                              Inventory inventory, Component title) {
        super(container, inventory, title, 166);
    }

}
