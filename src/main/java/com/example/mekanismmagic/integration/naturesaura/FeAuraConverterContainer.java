package com.example.mekanismmagic.integration.naturesaura;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public final class FeAuraConverterContainer extends MekanismTileContainer<FeAuraConverterBlockEntity> {
    public FeAuraConverterContainer(int id, Inventory inventory, FeAuraConverterBlockEntity tile) {
        super(NaturesAuraRegistries.FE_AURA_CONVERTER_CONTAINER, id, inventory, tile);
    }
    @Override protected int getInventoryYOffset() { return NatureAuraScreenLayout.COMPACT_INVENTORY_Y; }
    @Override public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == NatureAuraGeneratorBlockEntity.CYCLE_AURA_TYPE_BUTTON_ID) {
            return AuraSideConfigControls.canConfigure(this, player)
                    && getTileEntity().cycleAuraTypeMode();
        }
        if (AuraSideConfigControls.isAuraSideButton(buttonId)) {
            return AuraSideConfigControls.handleButton(this, player, buttonId);
        }
        return super.clickMenuButton(player, buttonId);
    }
}
