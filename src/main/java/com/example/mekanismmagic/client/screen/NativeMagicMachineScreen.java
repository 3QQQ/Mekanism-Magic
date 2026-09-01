package com.example.mekanismmagic.client.screen;

import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.client.gui.MagicGuiTheme.MachineStatus;
import com.example.mekanismmagic.client.gui.MagicItemCountRenderer;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.client.gui.GuiThemeToggleTab;
import com.example.mekanismmagic.integration.common.network
        .MachineDirectOutputHooks.DirectNetworkStatus;
import mekanism.client.gui.GuiConfigurableTile;
import mekanism.client.gui.element.GuiUpArrow;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.render.IFancyFontRenderer.TextAlignment;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Shared Mekanism work-machine screen. Concrete screens only add their
 * machine-specific slot widgets.
 */
public abstract class NativeMagicMachineScreen<
        TILE extends NativeMagicMachineBlockEntity,
        CONTAINER extends MekanismTileContainer<TILE>>
        extends GuiConfigurableTile<TILE, CONTAINER>
        implements MagicThemedScreen {

    protected NativeMagicMachineScreen(CONTAINER container,
                                       Inventory inventory, Component title,
                                       int imageHeight) {
        super(container, inventory, title);
        imageWidth = 176;
        this.imageHeight = imageHeight;
        inventoryLabelY = imageHeight - 94;
        dynamicSlots = true;
    }

    @Override
    protected final void addGuiElements() {
        super.addGuiElements();
        addRenderableWidget(new GuiThemeToggleTab<>(this, getTileEntity()));
        addWorkGuiElements();
        addMachineGuiElements();
    }

    /**
     * Adds the work-area widgets (arrow, power bar and progress indicator).
     * The default is the compact layout used by the original native
     * machines. Optional integrations can override this hook to use the
     * exact Mekanism machine variant without changing the shared Occultism
     * screens.
     */
    protected void addWorkGuiElements() {
        addMekanismWorkGuiElements(true);
    }

    /** Shared Mekanism electric-machine work area. */
    protected final void addMekanismWorkGuiElements(
            boolean includeEnergyInformationTab) {
        if (showUpArrow()) {
            addRenderableWidget(new GuiUpArrow(this, workArrowX(), workArrowY()));
        }
        addRenderableWidget(new GuiVerticalPowerBar(this,
                getTileEntity().getNativeEnergyContainer(), energyBarX(), 16,
                energyBarHeight()))
                .warning(WarningType.NOT_ENOUGH_ENERGY,
                        getTileEntity()::hasNotEnoughEnergyWarning)
                .warning(WarningType.NOT_ENOUGH_ENERGY_REDUCED_RATE,
                        getTileEntity()::hasReducedEnergyWarning);
        if (includeEnergyInformationTab) {
            addRenderableWidget(MagicLeftControlLayout.alignEnergyTab(this,
                    new GuiEnergyTab(this,
                    getTileEntity().getNativeEnergyContainer(),
                    getTileEntity()::getActive)));
        }
        if (showProgress()) {
            GuiProgress progress = new GuiProgress(
                    getTileEntity()::getProgress,
                    progressType(), this, workProgressX(), workProgressY())
                    .warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT,
                            getTileEntity()
                                    ::hasInputDoesntProduceOutputWarning);
            IRecipeViewerRecipeType<?>[] recipeTypes = recipeViewerTypes();
            if (recipeTypes.length > 0) {
                progress.recipeViewerCategories(recipeTypes);
            }
            addRenderableWidget(progress);
        } else {
            trackWarning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT,
                    getTileEntity()
                            ::hasInputDoesntProduceOutputWarning);
        }
    }

    protected void addMachineGuiElements() {
    }

    protected int workArrowX() {
        return 68;
    }

    protected int workArrowY() {
        return 38;
    }

    protected boolean showUpArrow() {
        return true;
    }

    protected boolean showProgress() {
        return true;
    }

    protected int energyBarX() {
        return imageWidth - 12;
    }

    protected int energyBarHeight() {
        return 52;
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack,
                                      Slot slot, String countText) {
        if (usesCompactSlotCount(stack, slot)) {
            // An explicit empty string suppresses vanilla's unscaled count
            // while retaining durability, cooldown and item decorations.
            super.renderSlotContents(graphics, stack, slot, "");
            MagicItemCountRenderer.render(graphics, font, stack.getCount(),
                    slot.x, slot.y);
            return;
        }
        super.renderSlotContents(graphics, stack, slot, countText);
    }

    protected boolean usesCompactSlotCount(ItemStack stack, Slot slot) {
        return false;
    }

    protected int workProgressX() {
        return 86;
    }

    protected int workProgressY() {
        return 38;
    }

    protected ProgressType progressType() {
        return ProgressType.BAR;
    }

    protected IRecipeViewerRecipeType<?>[] recipeViewerTypes() {
        return new IRecipeViewerRecipeType<?>[0];
    }

    protected void clickMenuButton(int buttonId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(
                    getMenu().containerId, buttonId);
        }
    }

    @Override
    protected void renderBg(net.minecraft.client.gui.GuiGraphics graphics,
                            float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        MagicGuiTheme.renderMachineFrame(graphics, leftPos, topPos,
                imageWidth, imageHeight, inventoryLabelY);
    }

    @Override
    public int titleTextColor() {
        return MagicGuiTheme.textPrimary();
    }

    protected MachineStatus machineStatus() {
        if (getTileEntity().getActive()) {
            return MachineStatus.RUNNING;
        }
        if (getTileEntity().hasNotEnoughEnergyWarning()
                || getTileEntity().hasReducedEnergyWarning()) {
            return MachineStatus.NO_POWER;
        }
        if (getTileEntity().hasInputDoesntProduceOutputWarning()) {
            return MachineStatus.WAITING;
        }
        return MachineStatus.IDLE;
    }

    @Override
    protected void drawForegroundText(net.minecraft.client.gui.GuiGraphics gui,
                                      int mouseX, int mouseY) {
        MachineStatus status = machineStatus();
        drawScrollingString(gui, title, 10, titleLabelY,
                TextAlignment.LEFT, MagicGuiTheme.textPrimary(),
                MagicGuiTheme.availableTitleWidth(font, status, imageWidth),
                0, false);
        MagicGuiTheme.renderStatus(gui, font, status, imageWidth,
                titleLabelY);
        renderDirectNetworkStatus(gui);
        renderInventoryText(gui);
    }

    private void renderDirectNetworkStatus(GuiGraphics graphics) {
        if (!getTileEntity().mekanismMagicSupportsDirectNetworkOutput()) {
            return;
        }
        DirectNetworkStatus status =
                getTileEntity().getDirectNetworkStatus();
        String key = switch (status) {
            case ONLINE -> "gui.mekanism_magic.ae_online";
            case BLOCKED -> "gui.mekanism_magic.ae_blocked";
            case OFFLINE -> "gui.mekanism_magic.ae_offline";
            case UNAVAILABLE -> "gui.mekanism_magic.ae_unavailable";
        };
        int color = switch (status) {
            case ONLINE -> 0x59D98E;
            case BLOCKED -> 0xE6B85C;
            case OFFLINE -> 0xE06B75;
            case UNAVAILABLE -> MagicGuiTheme.textMuted();
        };
        Component label = Component.translatable(key);
        graphics.drawString(font, label,
                imageWidth - 26 - font.width(label),
                inventoryLabelY - 11, color, false);
    }
}
