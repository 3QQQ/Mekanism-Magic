package com.example.mekanismmagic.integration.naturesaura;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public final class AnimalSpawnerProcessorContainer extends MekanismTileContainer<AnimalSpawnerProcessorBlockEntity> {
    public static final int SELECT_NEXT_RECIPE = 100;
    public static final int SIDE_MODE_START = AuraSideConfigControls.FORWARD_BUTTON_START;

    public AnimalSpawnerProcessorContainer(int id, Inventory inventory, AnimalSpawnerProcessorBlockEntity tile) {
        super(NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_CONTAINER, id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return NatureAuraScreenLayout.ANIMAL_INVENTORY_Y;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == SELECT_NEXT_RECIPE) {
            if (!AuraSideConfigControls.canConfigure(this, player)) {
                return false;
            }
            tile.cycleMatchingRecipe();
            return true;
        }
        if (AuraSideConfigControls.isAuraSideButton(buttonId)) {
            return AuraSideConfigControls.handleButton(this, player, buttonId);
        }
        return super.clickMenuButton(player, buttonId);
    }
}
