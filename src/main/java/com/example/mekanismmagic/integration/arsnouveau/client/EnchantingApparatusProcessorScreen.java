package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.EnchantingApparatusProcessorBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ArsThreeByThreeMachineLayout;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class EnchantingApparatusProcessorScreen
        extends ArsSourceMachineScreen<
        EnchantingApparatusProcessorBlockEntity> {
    public EnchantingApparatusProcessorScreen(
            MekanismTileContainer<
                    EnchantingApparatusProcessorBlockEntity> container,
            Inventory inventory, Component title) {
        super(container, inventory, title, 208);
        imageWidth = ArsThreeByThreeMachineLayout.IMAGE_WIDTH;
        inventoryLabelX = ArsThreeByThreeMachineLayout.INVENTORY_LABEL_X;
    }

    @Override
    protected int workArrowX() {
        return ArsThreeByThreeMachineLayout.SOURCE_SAFE_PROGRESS_X;
    }

    @Override
    protected int workProgressX() {
        return ArsThreeByThreeMachineLayout.SOURCE_SAFE_PROGRESS_X;
    }

    @Override
    protected int workArrowY() {
        return ArsThreeByThreeMachineLayout.PROGRESS_Y;
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
    protected int sourceBarHeight() {
        return ArsThreeByThreeMachineLayout.RESOURCE_BAR_HEIGHT;
    }

    @Override
    protected mekanism.client.gui.element.progress.ProgressType progressType() {
        return mekanism.client.gui.element.progress.ProgressType.SMALL_RIGHT;
    }

    @Override
    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{
                ArsRecipeViewerTypes.ENCHANTING_APPARATUS,
                ArsRecipeViewerTypes.ENCHANTMENT,
                ArsRecipeViewerTypes.ARMOR_UPGRADE};
    }
}
