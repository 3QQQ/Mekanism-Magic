package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.GuiMachineReadout;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.client.screen.NativeMagicMachineScreen;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraScreenLayout;
import com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerProcessorContainer;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class AnimalSpawnerProcessorScreen extends NativeMagicMachineScreen<AnimalSpawnerProcessorBlockEntity,
        AnimalSpawnerProcessorContainer> {

    public AnimalSpawnerProcessorScreen(AnimalSpawnerProcessorContainer menu, Inventory inventory, Component title) {
        super(menu, inventory, title, NatureAuraScreenLayout.ANIMAL_HEIGHT);
        inventoryLabelY = NatureAuraScreenLayout.ANIMAL_LABEL_Y;
    }

    @Override protected boolean showUpArrow() { return false; }
    @Override protected int workProgressX() { return 98; }
    @Override protected int workProgressY() { return 48; }
    @Override protected ProgressType progressType() { return ProgressType.SMALL_RIGHT; }
    @Override protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[]{NaturesAuraRecipeViewerTypes.ANIMAL_SPAWNER};
    }

    @Override
    protected void addMachineGuiElements() {
        AuraIntegratedSideConfig.install(this, getTileEntity(), children(),
                this::addRenderableWidget, this::removeWidget);
        addRenderableWidget(new GuiAuraBar(this, getTileEntity(), imageWidth - 20, 16, 52));
        addRenderableWidget(GuiMachineInstrument.line(this, MachinePanelLayout.ANIMAL_TARGET,
                () -> Component.translatable("gui.mekanism_magic.panel.target"), this::targetName,
                MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.animal_spawner.target", targetName()))));
        addRenderableWidget(GuiMachineInstrument.line(this, NatureMachineLayout.ANIMAL_AURA,
                () -> Component.translatable("gui.mekanism_magic.panel.aura"),
                () -> Component.literal(GuiMachineReadout.compact(getTileEntity().getStoredAura())),
                MagicGuiTheme::accentSource,
                () -> java.util.List.of(Component.translatable("gui.mekanism_magic.aura_amount",
                        getTileEntity().getStoredAura(), getTileEntity().getAuraCapacity()))));
    }

    @Override protected void addSlots() {
        super.addSlots();
        NatureMachineGroups.slotHints(menu, children());
    }

    private Component targetName() {
        var entityId = getTileEntity().getSelectedEntityId();
        if (entityId != null) {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(entityId).map(type -> type.getDescription())
                    .orElseGet(() -> Component.literal(entityId.toString()));
        }
        return Component.translatable("gui.mekanism_magic.panel.none");
    }

}
