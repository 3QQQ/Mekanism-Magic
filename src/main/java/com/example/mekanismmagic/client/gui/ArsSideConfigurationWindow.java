package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.integration.arsnouveau.ArsSourceMachineBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ArsSourceModeHost;
import mekanism.api.RelativeSide;
import mekanism.api.text.EnumColor;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.gui.element.button.SideDataButton;
import mekanism.client.gui.element.window.GuiSideConfiguration;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mekanism's native side configuration window with Source as an additional
 * page. The Source page uses the same six-direction positions as the native
 * side diagram; it does not occupy or overlap the native transmission tabs.
 */
public final class ArsSideConfigurationWindow<
        TILE extends mekanism.common.tile.base.TileEntityMekanism
        & mekanism.common.tile.interfaces.ISideConfiguration
        & ArsSourceModeHost>
        extends GuiSideConfiguration<TILE> {
    private final ArsSourceModeHost sourceHost;
    private final List<SideDataButton> originalButtons = new ArrayList<>();
    private final SourceButton[] sourceButtons = new SourceButton[6];
    private final SourcePageTab sourcePageTab;
    private boolean sourcePage;

    public ArsSideConfigurationWindow(
            IGuiWrapper gui, int x, int y, TILE tile,
            SelectedWindowData data) {
        super(gui, x, y, tile, data);
        sourceHost = tile;

        for (GuiElement child : children()) {
            if (child instanceof SideDataButton button) {
                originalButtons.add(button);
            }
        }

        sourcePageTab = addChild(new SourcePageTab(gui, this));
        for (RelativeSide side : RelativeSide.values()) {
            int buttonX = switch (side) {
                case BACK, LEFT -> 44;
                case FRONT, BOTTOM, TOP -> 67;
                case RIGHT -> 90;
            };
            int buttonY = switch (side) {
                case TOP -> 46;
                case LEFT, FRONT, RIGHT -> 69;
                case BACK, BOTTOM -> 92;
            };
            sourceButtons[side.ordinal()] = addChild(new SourceButton(
                    gui, buttonX, buttonY, side, tile));
        }
        updatePageVisibility();
    }

    @Override
    public void tick() {
        super.tick();
        for (SourceButton button : sourceButtons) {
            button.refresh(sourceHost.getSourceMode(
                    button.absoluteDirection()));
        }
        if (sourcePage && originalButtons.stream()
                .anyMatch(button -> button.visible)) {
            sourcePage = false;
            updatePageVisibility();
        }
    }

    @Override
    public void renderForeground(
            GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderForeground(graphics, mouseX, mouseY);
        if (sourcePage) {
            graphics.fill(relativeX + 38, relativeY + 25,
                    relativeX + 118, relativeY + 37, 0xFF303030);
            graphics.drawString(font(), Component.translatable(
                            "gui.mekanism_magic.source_column"),
                    relativeX + 42, relativeY + 27, 0xFFFFFF);
        }
    }

    private void toggleSourcePage() {
        sourcePage = !sourcePage;
        updatePageVisibility();
    }

    private void updatePageVisibility() {
        if (sourcePage) {
            for (SideDataButton button : originalButtons) {
                button.visible = false;
            }
        } else {
            updateTabs();
        }
        for (SourceButton button : sourceButtons) {
            button.visible = sourcePage;
            button.active = sourcePage;
        }
        sourcePageTab.active = true;
    }

    private static final class SourcePageTab extends GuiInsetElement<Void> {
        private final ArsSideConfigurationWindow<?> window;

        private SourcePageTab(IGuiWrapper gui,
                              ArsSideConfigurationWindow<?> window) {
            super(MekanismUtils.getResource(
                            MekanismUtils.ResourceType.GUI,
                            "configuration.png"),
                    gui, null, 156, 2, 26, 18, false);
            this.window = window;
            setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                    Component.translatable(
                            "gui.mekanism_magic.source_column")));
        }

        @Override
        protected void colorTab(GuiGraphics graphics) {
            MekanismRenderer.color(graphics,
                    mekanism.client.SpecialColors.TAB_CONFIGURATION);
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            window.toggleSourcePage();
        }
    }

    private static final class SourceButton extends SideDataButton {
        private final IGuiWrapper gui;
        private final mekanism.common.tile.base.TileEntityMekanism tile;
        private final RelativeSide side;
        private final SourceState state;

        private SourceButton(IGuiWrapper gui, int x, int y,
                             RelativeSide side,
                             mekanism.common.tile.base.TileEntityMekanism tile) {
            this(gui, x, y, side, tile, new SourceState());
        }

        private SourceButton(IGuiWrapper gui, int x, int y,
                             RelativeSide side,
                             mekanism.common.tile.base.TileEntityMekanism tile,
                             SourceState state) {
            super(gui, x, y, side, state::dataType, state::color, tile,
                    (pos, click, relativeSide) -> null, true);
            this.gui = gui;
            this.tile = tile;
            this.side = side;
            this.state = state;
        }

        private void refresh(
                ArsSourceMachineBlockEntity.SourceMode mode) {
            state.type = switch (mode) {
                case NONE -> DataType.NONE;
                case INPUT -> DataType.INPUT;
                case OUTPUT -> DataType.OUTPUT;
                case INPUT_OUTPUT -> DataType.INPUT_OUTPUT;
            };
        }

        private Direction absoluteDirection() {
            return side.getDirection(tile.getDirection());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!visible || !active || !isMouseOver(mouseX, mouseY)) {
                return false;
            }
            if ((button == 0 || button == 1)
                    && GuiElement.minecraft.gameMode != null
                    && gui instanceof mekanism.client.gui.GuiMekanism<?> screen) {
                GuiElement.minecraft.gameMode.handleInventoryButtonClick(
                        screen.getMenu().containerId,
                        200 + absoluteDirection().ordinal());
                return true;
            }
            return false;
        }

        private static final class SourceState {
            private DataType type = DataType.NONE;

            private DataType dataType() {
                return type;
            }

            private EnumColor color() {
                return type == DataType.NONE ? null : type.getColor();
            }
        }
    }
}
