package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierAssemblerBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ArsThreeByThreeMachineLayout;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class CatalystIdentifierAssemblerScreen extends NativeMagicMachineScreen<
        CatalystIdentifierAssemblerBlockEntity,
        MekanismTileContainer<CatalystIdentifierAssemblerBlockEntity>> {
    public CatalystIdentifierAssemblerScreen(
            MekanismTileContainer<CatalystIdentifierAssemblerBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 208);
        imageWidth = ArsThreeByThreeMachineLayout.IMAGE_WIDTH;
        inventoryLabelX = ArsThreeByThreeMachineLayout.INVENTORY_LABEL_X;
    }

    @Override
    protected void addWorkGuiElements() {
        addMekanismWorkGuiElements(true);
    }

    @Override
    protected int workArrowX() {
        return ArsThreeByThreeMachineLayout.STANDARD_PROGRESS_X;
    }

    @Override
    protected int workArrowY() {
        return ArsThreeByThreeMachineLayout.PROGRESS_Y;
    }

    @Override
    protected int workProgressX() {
        return ArsThreeByThreeMachineLayout.STANDARD_PROGRESS_X;
    }

    @Override
    protected int workProgressY() {
        return ArsThreeByThreeMachineLayout.PROGRESS_Y;
    }

    @Override
    protected boolean showUpArrow() {
        return false;
    }

    @Override
    protected int energyBarHeight() {
        return ArsThreeByThreeMachineLayout.RESOURCE_BAR_HEIGHT;
    }

    @Override
    protected mekanism.client.gui.element.progress.ProgressType progressType() {
        return mekanism.client.gui.element.progress.ProgressType.SMALL_RIGHT;
    }

    @Override
    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{
                ArsRecipeViewerTypes.CATALYST_IDENTIFIER};
    }
}
