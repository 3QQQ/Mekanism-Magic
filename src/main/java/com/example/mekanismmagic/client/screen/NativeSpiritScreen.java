package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeSpiritProcessorBlockEntity;
import com.example.mekanismmagic.client.OccultismRecipeViewerTypes;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class NativeSpiritScreen extends NativeMagicMachineScreen<
        NativeSpiritProcessorBlockEntity,
        MekanismTileContainer<NativeSpiritProcessorBlockEntity>> {
    public NativeSpiritScreen(MekanismTileContainer<NativeSpiritProcessorBlockEntity> container,
                              Inventory inventory, Component title) {
        super(container, inventory, title, 166);
    }

    @Override
    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{
                OccultismRecipeViewerTypes.SPIRIT};
    }
}
