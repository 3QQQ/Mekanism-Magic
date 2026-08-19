package com.example.mekanismmagic.container;

import com.example.mekanismmagic.blockentity.NativeMiniRitualAssemblerBlockEntity;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import net.minecraft.world.entity.player.Inventory;

public final class NativeMiniRitualAssemblerContainer
        extends MekanismTileContainer<NativeMiniRitualAssemblerBlockEntity> {
    public NativeMiniRitualAssemblerContainer(
            int id, Inventory inventory,
            NativeMiniRitualAssemblerBlockEntity tile) {
        super(com.example.mekanismmagic.NativeMekanismRegistries
                        .MINI_RITUAL_ASSEMBLER_CONTAINER,
                id, inventory, tile);
    }

    @Override
    public boolean clickMenuButton(net.minecraft.world.entity.player.Player player,
                                   int buttonId) {
        if (buttonId >= 0 && buttonId <= 2) {
            if (buttonId == 1) {
            getTileEntity().requestCraft();
            } else {
                getTileEntity().cyclePreview(buttonId == 2 ? 1 : -1);
            }
            return true;
        }
        return super.clickMenuButton(player, buttonId);
    }
}
