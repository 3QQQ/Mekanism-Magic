package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks.ControlSnapshot;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.button.MekanismButton;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/** Server-authoritative AE controls embedded in the existing machine-settings window. */
public final class MachineAeSettingsSection implements MachineSettingsSections.Section {
    public static final int MASTER_Y = 13;
    public static final int FEATURE_Y = 30;
    public static final int ROW_HEIGHT = 16;
    public static final int BUTTON_HEIGHT = 14;
    public static final int PAGINATION_RESERVE = 16;
    private static final int[] INPUTS = {MachineAeResourceHooks.ENERGY, MachineAeResourceHooks.SOURCE,
            MachineAeResourceHooks.AURA, MachineAeResourceHooks.ESSENCE_AUREAL,
            MachineAeResourceHooks.ESSENCE_SOULS, MachineAeResourceHooks.ESSENCE_BLOOD,
            MachineAeResourceHooks.ESSENCE_EXPERIENCE};
    private static final int[] OUTPUTS = {MachineAeResourceHooks.OUTPUT_ITEMS, MachineAeResourceHooks.OUTPUT_FLUIDS,
            MachineAeResourceHooks.SOURCE, MachineAeResourceHooks.AURA, MachineAeResourceHooks.ESSENCE_AUREAL,
            MachineAeResourceHooks.ESSENCE_SOULS, MachineAeResourceHooks.ESSENCE_BLOOD,
            MachineAeResourceHooks.ESSENCE_EXPERIENCE};

    @Override public String id() { return "ae"; }
    @Override public Component title() { return Component.translatable("gui.mekanism_magic.settings.section.ae"); }
    @Override public boolean supports(TileEntityMekanism tile) { return MachineAeResourceHooks.controls(tile).supported(); }
    @Override public int preferredHeight(TileEntityMekanism tile) {
        var state = MachineAeResourceHooks.controls(tile);
        return preferredHeight(state.inputAvailableMask(), state.outputAvailableMask());
    }

    public static int preferredHeight(int inputMask, int outputMask) {
        return FEATURE_Y + ROW_HEIGHT * Math.max(Integer.bitCount(inputMask), Integer.bitCount(outputMask));
    }

    @Override public void build(MachineSettingsSections.Context context) {
        ControlSnapshot state = MachineAeResourceHooks.controls(context.tile());
        int[] inputs = Arrays.stream(INPUTS).filter(kind -> (state.inputAvailableMask() & kind) != 0).toArray();
        int[] outputs = Arrays.stream(OUTPUTS).filter(kind -> (state.outputAvailableMask() & kind) != 0).toArray();
        int rows = Math.max(inputs.length, outputs.length);
        boolean paged = FEATURE_Y + rows * ROW_HEIGHT > context.height();
        int rowsPerPage = Math.max(1, (context.height() - FEATURE_Y - (paged ? PAGINATION_RESERVE : 0)) / ROW_HEIGHT);
        int pages = Math.max(1, (rows + rowsPerPage - 1) / rowsPerPage);
        int page = Math.min(pages - 1, context.window().sectionPage(id()));
        int first = page * rowsPerPage;
        int columnWidth = (context.width() - 6) / 2;
        context.add(new NetworkStatus(context));
        context.add(new Toggle(context, context.x(), context.y() + MASTER_Y, columnWidth, true, 0));
        context.add(new Toggle(context, context.x() + columnWidth + 6, context.y() + MASTER_Y, columnWidth, false, 0));
        for (int row = 0; row < rowsPerPage; row++) {
            int index = first + row;
            int y = context.y() + FEATURE_Y + row * ROW_HEIGHT;
            if (index < inputs.length) context.add(new Toggle(context, context.x(), y, columnWidth, true, inputs[index]));
            if (index < outputs.length) context.add(new Toggle(context, context.x() + columnWidth + 6, y, columnWidth, false, outputs[index]));
        }
        if (paged) {
            int y = context.y() + context.height() - BUTTON_HEIGHT;
            var previous = new MekanismButton(context.gui(), context.x(), y, 24, BUTTON_HEIGHT, Component.literal("‹"),
                    (element, mouseX, mouseY) -> {
                        context.window().setSectionPage(id(), page - 1);
                        return true;
                    }, null);
            previous.active = page > 0;
            context.add(previous);
            var next = new MekanismButton(context.gui(), context.x() + context.width() - 24, y, 24, BUTTON_HEIGHT,
                    Component.literal("›"), (element, mouseX, mouseY) -> {
                        context.window().setSectionPage(id(), page + 1);
                        return true;
                    }, null);
            next.active = page + 1 < pages;
            context.add(next);
            context.add(new GuiElement(context.gui(), context.x() + 28, y, context.width() - 56, BUTTON_HEIGHT) {
                { active = false; }
                @Override public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY) {
                    Component label = Component.literal((page + 1) + " / " + pages);
                    graphics.drawString(font(), label, relativeX + (width - font().width(label)) / 2,
                            relativeY + 3, MagicGuiTheme.textMuted(), false);
                }
            });
        }
    }

    private static final class NetworkStatus extends GuiElement {
        private final TileEntityMekanism tile;

        private NetworkStatus(MachineSettingsSections.Context context) {
            super(context.gui(), context.x(), context.y(), context.width(), 12);
            tile = context.tile();
            active = false;
        }

        @Override public void renderForeground(GuiGraphics graphics, int mouseX, int mouseY) {
            var status = MachineAeResourceHooks.snapshot(tile);
            Component state = Component.translatable("gui.mekanism_magic.ae_config.network",
                    Component.translatable("gui.mekanism_magic.ae_config." +
                            (!status.connected() ? "offline" : status.active() ? "online" : "not_ready")));
            graphics.drawString(font(), state, relativeX, relativeY + 2, MagicGuiTheme.textMuted(), false);
        }

        @Override public void updateTooltip(int mouseX, int mouseY) {
            setTooltip(Tooltip.create(MachineAeTooltips.tooltip(MachineAeResourceHooks.snapshot(tile))));
        }

        @Override public boolean isMouseOver(double mouseX, double mouseY) {
            return visible && mouseX >= getX() && mouseX < getRight()
                    && mouseY >= getY() && mouseY < getBottom();
        }
    }

    /** Fields intentionally describe direction and resource without giving the client write access to settings. */
    public static final class Toggle extends MekanismButton {
        private final TileEntityMekanism tile;
        private final boolean input;
        private final int kind;

        private Toggle(MachineSettingsSections.Context context, int x, int y, int buttonWidth,
                       boolean input, int kind) {
            super(context.gui(), x, y, buttonWidth, BUTTON_HEIGHT, Component.empty(),
                    (element, mouseX, mouseY) -> send(context, input, kind), null);
            tile = context.tile();
            this.input = input;
            this.kind = kind;
            setTooltip(Tooltip.create(Component.translatable("gui.mekanism_magic.ae_config.note")));
            refresh();
        }

        public boolean input() { return input; }
        public int kind() { return kind; }

        private void refresh() {
            ControlSnapshot controls = MachineAeResourceHooks.controls(tile);
            int available = input ? controls.inputAvailableMask() : controls.outputAvailableMask();
            boolean master = input ? controls.inputMaster() : controls.outputMaster();
            int configured = input ? controls.configuredInputMask() : controls.configuredOutputMask();
            boolean supported = kind == 0 ? available != 0 : (available & kind) != 0;
            active = supported && (kind == 0 || master);
            boolean enabled = kind == 0 ? master : (configured & kind) != 0;
            Component label = Component.translatable(kind == 0
                    ? "gui.mekanism_magic.ae_config." + (input ? "input" : "output")
                    : "gui.mekanism_magic.ae_config.feature." + feature(kind));
            setMessage(Component.translatable("gui.mekanism_magic.ae_config.entry", label,
                    Component.translatable("gui.mekanism_magic.ae_config." +
                            (!supported ? "unavailable" : enabled ? "on" : "off"))));
        }

        @Override public void tick() { super.tick(); refresh(); }

        @Override public boolean isMouseOver(double mouseX, double mouseY) {
            return visible && mouseX >= getX() && mouseX < getRight()
                    && mouseY >= getY() && mouseY < getBottom();
        }
    }

    private static boolean send(MachineSettingsSections.Context context, boolean input, int kind) {
        int id = kind == 0 ? input ? MachineAeResourceHooks.TOGGLE_BUTTON_ID
                : MachineAeResourceHooks.OUTPUT_MASTER_BUTTON_ID
                : input ? MachineAeResourceHooks.buttonIdForInput(kind) : MachineAeResourceHooks.buttonIdForOutput(kind);
        if (id < 0 || Minecraft.getInstance().gameMode == null) return false;
        Minecraft.getInstance().gameMode.handleInventoryButtonClick(context.gui().getMenu().containerId, id);
        return true;
    }

    private static String feature(int kind) {
        return switch (kind) {
            case MachineAeResourceHooks.ENERGY -> "energy";
            case MachineAeResourceHooks.SOURCE -> "source";
            case MachineAeResourceHooks.AURA -> "aura";
            case MachineAeResourceHooks.ESSENCE_AUREAL -> "aureal";
            case MachineAeResourceHooks.ESSENCE_SOULS -> "souls";
            case MachineAeResourceHooks.ESSENCE_BLOOD -> "blood";
            case MachineAeResourceHooks.ESSENCE_EXPERIENCE -> "experience";
            case MachineAeResourceHooks.OUTPUT_ITEMS -> "items";
            case MachineAeResourceHooks.OUTPUT_FLUIDS -> "fluids";
            default -> throw new IllegalArgumentException("Unknown AE function " + kind);
        };
    }
}
