package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.blockentity.NativeRitualEngineBlockEntity;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.tab.GuiInsetToggleElement;
import mekanism.client.render.MekanismRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BooleanSupplier;

/**
 * Mekanism-style side tab for the ritual engine's non-consumable
 * Dictionary of Spirits slot.
 */
public final class GuiDictionaryModuleTab
        extends GuiInsetToggleElement<NativeRitualEngineBlockEntity> {
    private static final ResourceLocation ICON =
            ResourceLocation.fromNamespaceAndPath(
                    "occultism", "textures/item/dictionary_of_spirits.png");

    private final Runnable toggle;

    public GuiDictionaryModuleTab(IGuiWrapper gui,
                                  NativeRitualEngineBlockEntity tile,
                                  BooleanSupplier open, Runnable toggle) {
        super(gui, tile, 210, 80, 26, 18, false,
                ICON, ICON, open);
        this.toggle = toggle;
        setTooltip(Tooltip.create(Component.translatable(
                "gui.mekanism_magic.dictionary_module")));
    }

    @Override
    protected void colorTab(GuiGraphics graphics) {
        MekanismRenderer.color(graphics, 0xFFB879D6);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        toggle.run();
    }
}
