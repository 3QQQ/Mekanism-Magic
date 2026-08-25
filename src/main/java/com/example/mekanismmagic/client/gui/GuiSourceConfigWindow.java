package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.integration.arsnouveau.ArsSourceMachineBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ArsSourceModeHost;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.client.gui.element.window.GuiWindow;
import mekanism.common.inventory.container.SelectedWindowData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * Compact six-side Source mode window. Each button cycles None/Input/Output/
 * Input+Output just like Mekanism's other transmission configuration modes.
 */
public final class GuiSourceConfigWindow extends GuiWindow {
    private final ArsSourceModeHost tile;
    private final ModeButton[] buttons = new ModeButton[6];

    public GuiSourceConfigWindow(IGuiWrapper gui,
                                 ArsSourceModeHost tile,
                                 IntConsumer click) {
        super(gui, 0, 0, 150, 145,
                SelectedWindowData.WindowType.SIDE_CONFIG);
        this.tile = tile;
        addCloseButton();
        Direction[] directions = Direction.values();
        for (int index = 0; index < directions.length; index++) {
            int buttonIndex = index;
            int x = 8 + (index % 2) * 70;
            int y = 22 + (index / 2) * 30;
            buttons[index] = addChild(new ModeButton(gui, x, y,
                    directions[index], (element, mouseX, mouseY) -> {
                        click.accept(buttonIndex);
                        return true;
                    }));
        }
    }

    @Override
    public void tick() {
        super.tick();
        for (int index = 0; index < buttons.length; index++) {
            buttons[index].refresh(tile.getSourceMode(Direction.values()[index]));
        }
    }

    private static final class ModeButton extends MekanismButton {
        private final Direction direction;

        private ModeButton(IGuiWrapper gui, int x, int y,
                           Direction direction, GuiElement.IClickable click) {
            super(gui, x, y, 62, 20,
                    Component.literal(direction.getName()), click);
            this.direction = direction;
        }

        private void refresh(ArsSourceMachineBlockEntity.SourceMode mode) {
            setMessage(Component.literal(direction.getName() + ": "
                    + mode.name()));
        }
    }
}
