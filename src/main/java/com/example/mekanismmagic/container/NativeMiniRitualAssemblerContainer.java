package com.example.mekanismmagic.container;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.blockentity.NativeMiniRitualAssemblerBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public final class NativeMiniRitualAssemblerContainer
        extends MekanismTileContainer<NativeMiniRitualAssemblerBlockEntity> {
    public NativeMiniRitualAssemblerContainer(int id, Inventory inventory,
                                               NativeMiniRitualAssemblerBlockEntity tile) {
        super(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_CONTAINER,
                id, inventory, tile);
    }

    @Override
    protected int getInventoryXOffset() {
        return 25;
    }

    @Override
    protected int getInventoryYOffset() {
        return 126;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == 99) {
            getTileEntity().clearPentaclePreference();
            return true;
        }
        if (buttonId >= 100) {
            getTileEntity().lockPentacle(buttonId - 100);
            return true;
        }
        return super.clickMenuButton(player, buttonId);
    }
}
