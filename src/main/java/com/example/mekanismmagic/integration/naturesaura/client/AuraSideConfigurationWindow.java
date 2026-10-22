package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.integration.naturesaura.NatureAuraMachineBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.AuraSideModeHost;
import com.example.mekanismmagic.integration.naturesaura.AuraSideConfigControls;
import mekanism.api.RelativeSide;
import mekanism.api.text.EnumColor;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.GuiInnerScreen;
import mekanism.client.gui.element.GuiInsetElement;
import mekanism.client.gui.element.button.SideDataButton;
import mekanism.client.gui.element.button.TooltipToggleButton;
import mekanism.client.gui.element.tab.GuiConfigTypeTab;
import mekanism.client.gui.element.window.GuiSideConfiguration;
import mekanism.client.gui.tooltip.TooltipUtils;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.client.render.MekanismRenderer;
import mekanism.common.MekanismLang;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Mekanism's side configuration window with Aura as a native-style page. */
public final class AuraSideConfigurationWindow<
        TILE extends mekanism.common.tile.base.TileEntityMekanism
        & mekanism.common.tile.interfaces.ISideConfiguration
        & AuraSideModeHost>
        extends GuiSideConfiguration<TILE> {
    static final int WINDOW_WIDTH = 156;
    private final TILE tile;
    private List<GuiElement> nativePageElements;
    private List<GuiConfigTypeTab> nativeTypeTabs;
    private AuraButton[] auraButtons;
    private GuiInnerScreen auraStatus;
    private TooltipToggleButton auraBatchButton;
    private AuraConfigTypeTab auraTab;
    private boolean auraPage;

    public AuraSideConfigurationWindow(
            IGuiWrapper gui, int x, int y, TILE tile,
            SelectedWindowData data) {
        super(gui, x, y, tile, data);
        this.tile = tile;
        nativePageElements = new ArrayList<>();
        nativeTypeTabs = new ArrayList<>();
        auraButtons = new AuraButton[6];

        for (GuiElement child : List.copyOf(children())) {
            if (child instanceof GuiConfigTypeTab tab) {
                nativeTypeTabs.add(tab);
            } else if (child instanceof SideDataButton
                    || child instanceof GuiInnerScreen
                    || child instanceof TooltipToggleButton
                    || isNativeEjectButton(child)) {
                nativePageElements.add(child);
            }
        }

        auraStatus = addChild(new GuiInnerScreen(gui,
                relativeX + 38, relativeY + 25, 80, 12,
                () -> List.of(MekanismLang.NO_EJECT.translate()))
                .tooltip(() -> List.of(
                        MekanismLang.CANT_EJECT_TOOLTIP.translate())));

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
            auraButtons[side.ordinal()] = addChild(new AuraButton(
                    gui, relativeX + buttonX, relativeY + buttonY,
                    side, tile));
        }

        auraBatchButton = addChild(new TooltipToggleButton(gui,
                relativeX + 136, relativeY + 95, 14,
                getButtonLocation("clear_sides"),
                () -> AuraSideConfigControls.nextAllMode(tile)
                        == NatureAuraMachineBlockEntity.AuraSideMode.NONE,
                (element, mouseX, mouseY) -> sendButton(AuraSideConfigControls.DISABLE_ALL_BUTTON),
                (element, mouseX, mouseY) -> sendButton(AuraSideConfigControls.NEXT_ALL_BUTTON),
                TooltipUtils.create(MekanismLang.SIDE_CONFIG_CLEAR,
                        MekanismLang.SIDE_CONFIG_CLEAR_ALL),
                TooltipUtils.create(MekanismLang.SIDE_CONFIG_INCREMENT)));

        int tabIndex = nativeTypeTabs.size();
        boolean left = tabIndex < 4;
        auraTab = addChild(new AuraConfigTypeTab(gui,
                relativeX + (left ? -26 : WINDOW_WIDTH),
                relativeY + 2 + 28 * (tabIndex % 4), left, this));
        updateTabs();
    }

    @Override
    public void setCurrentType(TransmissionType type) {
        auraPage = false;
        super.setCurrentType(type);
    }

    private void showAuraPage() {
        auraPage = true;
        updateTabs();
    }

    @Override
    public void updateTabs() {
        super.updateTabs();
        // GuiSideConfiguration calls this from its constructor before this
        // subclass has initialized its page controls.
        if (nativePageElements == null) {
            return;
        }
        for (GuiElement element : nativePageElements) {
            boolean ejectButton = isNativeEjectButton(element);
            element.visible = !auraPage || ejectButton;
            if (auraPage) {
                element.active = false;
            } else if (!(element instanceof SideDataButton)
                    && !ejectButton) {
                element.active = true;
            }
        }
        for (GuiConfigTypeTab tab : nativeTypeTabs) {
            if (auraPage) {
                tab.visible = true;
            }
            tab.active = true;
        }
        for (AuraButton button : auraButtons) {
            button.visible = auraPage;
            button.active = auraPage;
            button.refresh(tile.getAuraSideMode(button.absoluteDirection()));
        }
        auraStatus.visible = auraPage;
        auraStatus.active = auraPage;
        auraBatchButton.visible = auraPage;
        auraBatchButton.active = auraPage;
        auraTab.visible = !auraPage;
        auraTab.active = !auraPage;
    }

    @Override
    public void tick() {
        super.tick();
        if (auraPage) {
            for (AuraButton button : auraButtons) {
                button.refresh(tile.getAuraSideMode(
                        button.absoluteDirection()));
            }
        }
    }

    @Override
    public void renderForeground(
            GuiGraphics graphics, int mouseX, int mouseY) {
        if (!auraPage) {
            super.renderForeground(graphics, mouseX, mouseY);
            return;
        }
        drawTitleText(graphics, MekanismLang.CONFIG_TYPE.translate(
                Component.translatable("gui.mekanism_magic.aura")), 5);
        drawScrollingString(graphics, MekanismLang.SLOTS.translate(),
                0, 120, TextAlignment.CENTER,
                subheadingTextColor(), 4, false);
    }

    private boolean isNativeEjectButton(GuiElement element) {
        return element.getRelativeX() == relativeX + 136
                && element.getRelativeY() == relativeY + 6;
    }

    private boolean sendButton(int id) {
        if (GuiElement.minecraft.gameMode == null
                || !(gui() instanceof mekanism.client.gui.GuiMekanism<?> screen)) {
            return false;
        }
        GuiElement.minecraft.gameMode.handleInventoryButtonClick(
                screen.getMenu().containerId, id);
        return true;
    }

    private static final class AuraConfigTypeTab
            extends GuiInsetElement<Void> {
        private static final ResourceLocation ICON =
                ResourceLocation.fromNamespaceAndPath(
                        "naturesaura", "textures/item/aura_bottle.png");
        private final AuraSideConfigurationWindow<?> window;

        private AuraConfigTypeTab(
                IGuiWrapper gui, int x, int y, boolean left,
                AuraSideConfigurationWindow<?> window) {
            super(ICON, gui, null, x, y, 26, 18, left);
            this.window = window;
            setTooltip(Tooltip.create(Component.translatable(
                    "gui.mekanism_magic.aura")));
        }

        @Override
        protected void colorTab(GuiGraphics graphics) {
            MekanismRenderer.color(graphics, EnumColor.BRIGHT_GREEN);
        }

        @Override
        public void onClick(double mouseX, double mouseY, int button) {
            window.showAuraPage();
        }
    }

    private static final class AuraButton extends SideDataButton {
        private final IGuiWrapper gui;
        private final mekanism.common.tile.base.TileEntityMekanism tile;
        private final RelativeSide side;
        private final AuraState state;

        private AuraButton(IGuiWrapper gui, int x, int y,
                             RelativeSide side,
                             mekanism.common.tile.base.TileEntityMekanism tile) {
            this(gui, x, y, side, tile, new AuraState());
        }

        private AuraButton(IGuiWrapper gui, int x, int y,
                             RelativeSide side,
                             mekanism.common.tile.base.TileEntityMekanism tile,
                             AuraState state) {
            super(gui, x, y, side, state::dataType, state::color, tile,
                    (pos, click, relativeSide) -> null, true);
            this.gui = gui;
            this.tile = tile;
            this.side = side;
            this.state = state;
        }

        private void refresh(NatureAuraMachineBlockEntity.AuraSideMode mode) {
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
                        (button == 0 ? AuraSideConfigControls.FORWARD_BUTTON_START
                                : AuraSideConfigControls.REVERSE_BUTTON_START)
                                + absoluteDirection().ordinal());
                return true;
            }
            return false;
        }

        private static final class AuraState {
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
