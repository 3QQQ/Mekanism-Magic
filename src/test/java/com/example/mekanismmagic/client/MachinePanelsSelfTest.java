package com.example.mekanismmagic.client;

import com.example.mekanismmagic.client.gui.GuiMachineReadout;
import com.example.mekanismmagic.client.gui.GuiMachineInstrument;
import com.example.mekanismmagic.client.gui.GuiMachineProgress;
import com.example.mekanismmagic.client.gui.MagicGuiTextures;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.example.mekanismmagic.config.MagicClientConfig;
import com.example.mekanismmagic.container.MachinePanelLayout;
import com.example.mekanismmagic.container.MachineFrameLayout;
import com.example.mekanismmagic.container.MachineArrowLayout;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryLayout;
import com.example.mekanismmagic.integration.mekextras.ExtraSpiritFactoryLayout;
import com.example.mekanismmagic.integration.mekextras.ExtraNatureAltarFactoryLayout;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarFactoryLayout;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraScreenLayout;
import com.example.mekanismmagic.integration.naturesaura.client.GuiAuraBar;
import com.example.mekanismmagic.integration.naturesaura.client.NatureMachineLayout;
import com.example.mekanismmagic.integration.naturesaura.client.NatureMachineSettingsSection;
import com.example.mekanismmagic.integration.arsnouveau.client.GuiSourceBar;
import com.electronwill.nightconfig.core.CommentedConfig;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.bar.GuiBar;
import mekanism.client.gui.element.bar.GuiVerticalPowerBar;
import mekanism.client.gui.element.progress.GuiProgress;
import mekanism.client.gui.element.progress.IProgressInfoHandler;
import mekanism.client.gui.element.progress.ProgressType;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.common.tier.FactoryTier;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Calls the actual new card renderer off-screen with conservative glyph metrics; no Minecraft instance or GL context. */
public final class MachinePanelsSelfTest {
    private static final Set<String> INSTRUMENT_SAMPLES = Set.of(
            "com/example/mekanismmagic/integration/naturesaura/client/NatureAltarProcessorScreen",
            "com/example/mekanismmagic/integration/naturesaura/client/NatureAltarFactoryScreen",
            "com/example/mekanismmagic/integration/mekextras/client/ExtraNatureAltarFactoryScreen",
            "com/example/mekanismmagic/integration/naturesaura/client/OfferingProcessorScreen",
            "com/example/mekanismmagic/integration/naturesaura/client/AnimalSpawnerProcessorScreen",
            "com/example/mekanismmagic/integration/naturesaura/client/AuraFuelGeneratorScreen",
            "com/example/mekanismmagic/integration/naturesaura/client/AuraFieldInterfaceScreen",
            "com/example/mekanismmagic/integration/naturesaura/client/FeAuraConverterScreen",
            "com/example/mekanismmagic/integration/forbiddenarcanus/client/ClibanoProcessorScreen",
            "com/example/mekanismmagic/integration/forbiddenarcanus/client/EssenceCondenserScreen",
            "com/example/mekanismmagic/integration/forbiddenarcanus/client/HephaestusForgeProcessorScreen");
    private MachinePanelsSelfTest() { }
    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        wiring();
        fullScreenGeometry();
        compactArrowRendering();
        try {
            Map<String, List<Integer>> instrumentRenders = new java.util.LinkedHashMap<>();
            for (String theme : List.of("DARK", "LIGHT")) {
                memoryTheme(theme);
                render(theme);
                defaultSettingsButtonContrast(theme);
                instrumentRenders.put("default-" + theme, instrumentReadouts());
            }
            com.example.mekanismmagic.client.gui.GuiResourcePackSelfTest.reload(
                    Files.readString(Path.of("resourcepacks/ae-terminal/style.json")));
            for (String theme : List.of("DARK", "LIGHT")) {
                memoryTheme(theme);
                render(theme);
                terminalSettingsButtonContrast(theme);
                instrumentRenders.put("terminal-" + theme, instrumentReadouts());
            }
            require(!instrumentRenders.get("default-DARK").equals(instrumentRenders.get("default-LIGHT"))
                            && !instrumentRenders.get("terminal-DARK").equals(instrumentRenders.get("terminal-LIGHT"))
                            && !instrumentRenders.get("default-DARK").equals(instrumentRenders.get("terminal-DARK"))
                            && !instrumentRenders.get("default-LIGHT").equals(instrumentRenders.get("terminal-LIGHT")),
                    "Machine instruments stopped following the active light/dark or resource-pack palette");
        } finally {
            com.example.mekanismmagic.client.gui.GuiResourcePackSelfTest.reload(null);
            MagicClientConfig.SPEC.acceptConfig(null);
        }
        System.out.println("MachinePanelsSelfTest: unified Nature/Forbidden screens and grouped bounds, shared settings route, bilingual labels/values/tooltips and default/terminal two-theme drawing passed");
    }

    /** Tests complete screen decks with real Mekanism widget sizes and menu-slot coordinates. */
    private static void fullScreenGeometry() {
        paddedMachineCase();
        inventoryDeck("tree ritual", 176, MachinePanelLayout.TREE_HEIGHT, 8,
                MachinePanelLayout.inventoryY(MachinePanelLayout.TREE_HEIGHT),
                MachinePanelLayout.inventoryLabelY(MachinePanelLayout.TREE_HEIGHT));
        treeRitualWorkDeck();
        forgeInternalTierDeck();
        essenceCondenserDeck();
        inventoryDeck("mini ritual", MachinePanelLayout.MINI_RITUAL_WIDTH,
                MachinePanelLayout.MINI_RITUAL_HEIGHT,
                MachinePanelLayout.inventoryX(MachinePanelLayout.MINI_RITUAL_WIDTH),
                MachinePanelLayout.inventoryY(MachinePanelLayout.MINI_RITUAL_HEIGHT),
                MachinePanelLayout.inventoryLabelY(MachinePanelLayout.MINI_RITUAL_HEIGHT));
        miniRitualWorkDeck();
        inventoryDeck("ritual engine", 210, MachinePanelLayout.RITUAL_HEIGHT,
                MachinePanelLayout.inventoryX(210), MachinePanelLayout.inventoryY(MachinePanelLayout.RITUAL_HEIGHT),
                MachinePanelLayout.inventoryLabelY(MachinePanelLayout.RITUAL_HEIGHT));
        inventoryDeck("dimensional miner", 210, MachinePanelLayout.MINER_HEIGHT,
                MachinePanelLayout.inventoryX(210), MachinePanelLayout.inventoryY(MachinePanelLayout.MINER_HEIGHT),
                MachinePanelLayout.inventoryLabelY(MachinePanelLayout.MINER_HEIGHT));
        inventoryDeck("Ars three-by-three", 210, MachinePanelLayout.ARS_THREE_BY_THREE_HEIGHT,
                MachinePanelLayout.inventoryX(210), MachinePanelLayout.inventoryY(MachinePanelLayout.ARS_THREE_BY_THREE_HEIGHT),
                MachinePanelLayout.inventoryLabelY(MachinePanelLayout.ARS_THREE_BY_THREE_HEIGHT));
        inventoryDeck("Drygmy", 210, MachinePanelLayout.DRYGMY_HEIGHT,
                MachinePanelLayout.inventoryX(210), MachinePanelLayout.inventoryY(MachinePanelLayout.DRYGMY_HEIGHT),
                MachinePanelLayout.inventoryLabelY(MachinePanelLayout.DRYGMY_HEIGHT));
        drygmyWorkDeck();
        generatorModeControls();

        for (FactoryTier tier : FactoryTier.values()) {
            int width = tier == FactoryTier.ULTIMATE ? 210 : 176;
            factoryDeck("spirit " + tier, width, MachinePanelLayout.FACTORY_HEIGHT,
                    MachinePanelLayout.inventoryX(width), MachinePanelLayout.inventoryY(MachinePanelLayout.FACTORY_HEIGHT),
                    MachinePanelLayout.inventoryLabelY(MachinePanelLayout.FACTORY_HEIGHT),
                    tier.processes, switch (tier) {
                        case BASIC -> 55;
                        case ADVANCED -> 35;
                        case ELITE -> 29;
                        case ULTIMATE -> 27;
                    }, switch (tier) {
                        case BASIC -> 38;
                        case ADVANCED -> 26;
                        case ELITE, ULTIMATE -> 19;
                    }, false);
            int natureWidth = NatureAltarFactoryLayout.imageWidth(tier);
            factoryDeck("nature " + tier, natureWidth, NatureAltarFactoryLayout.IMAGE_HEIGHT,
                    NatureAltarFactoryLayout.inventoryX(tier), NatureAltarFactoryLayout.INVENTORY_Y,
                    NatureAltarFactoryLayout.INVENTORY_LABEL_Y, tier.processes,
                    NatureAltarFactoryLayout.firstProcessX(tier),
                    NatureAltarFactoryLayout.processSpacing(tier), true);
            int imbuementWidth = ImbuementFactoryLayout.standardImageWidth(tier);
            factoryDeck("imbuement " + tier, imbuementWidth, MachinePanelLayout.FACTORY_HEIGHT,
                    ImbuementFactoryLayout.standardInventoryX(tier), MachinePanelLayout.inventoryY(MachinePanelLayout.FACTORY_HEIGHT),
                    MachinePanelLayout.inventoryLabelY(MachinePanelLayout.FACTORY_HEIGHT),
                    tier.processes, ImbuementFactoryLayout.firstProcessX(tier),
                    ImbuementFactoryLayout.processSpacing(tier), true);
        }
        // Mekanism Extras 1.4.1's four factory tiers have 11/13/15/17 lanes.
        int[] extraProcesses = {11, 13, 15, 17};
        for (int tier = 0; tier < extraProcesses.length; tier++) {
            int spiritWidth = ExtraSpiritFactoryLayout.imageWidth(tier);
            factoryDeck("extra spirit " + tier, spiritWidth, MachinePanelLayout.FACTORY_HEIGHT,
                    ExtraSpiritFactoryLayout.inventoryX(tier), MachinePanelLayout.inventoryY(MachinePanelLayout.FACTORY_HEIGHT),
                    MachinePanelLayout.inventoryLabelY(MachinePanelLayout.FACTORY_HEIGHT),
                    extraProcesses[tier], 27, 19, false);
            int natureWidth = ExtraNatureAltarFactoryLayout.imageWidth(tier);
            factoryDeck("extra nature " + tier, natureWidth,
                    ExtraNatureAltarFactoryLayout.IMAGE_HEIGHT,
                    ExtraNatureAltarFactoryLayout.inventoryX(tier),
                    ExtraNatureAltarFactoryLayout.INVENTORY_Y,
                    ExtraNatureAltarFactoryLayout.INVENTORY_LABEL_Y,
                    extraProcesses[tier], 27, 19, true);
            int imbuementWidth = ImbuementFactoryLayout.extraImageWidth(tier);
            factoryDeck("extra imbuement " + tier, imbuementWidth, MachinePanelLayout.FACTORY_HEIGHT,
                    ImbuementFactoryLayout.extraInventoryX(tier), MachinePanelLayout.inventoryY(MachinePanelLayout.FACTORY_HEIGHT),
                    MachinePanelLayout.inventoryLabelY(MachinePanelLayout.FACTORY_HEIGHT),
                    extraProcesses[tier], 27, 19, true);
        }
    }

    private static void paddedMachineCase() {
        require(MachineFrameLayout.CASE_PADDING == 3 && MachineFrameLayout.WORK_INVENTORY_GAP == 3,
                "Shared machine breathing room lost its explicit physical geometry contract");
        for (int width : new int[]{176, 194, 210, 256, 370, 608}) {
            for (int height : new int[]{166, 171, 176, 183, 185, 190, 194, 198, 200, 202}) {
                int left = 41, top = 23;
                var outer = MachineFrameLayout.outerBounds(left, top, width, height);
                require(outer.equals(new MachinePanelLayout.Box(left - 3, top - 3, width + 6, height + 6)),
                        "Painted machine shell is not symmetric around its unchanged logical content");
                for (double[] inside : new double[][]{{outer.x(), outer.y()}, {outer.right() - 0.1, outer.bottom() - 0.1},
                        {left - 1.5, top + height / 2.0}, {left + width + 1.5, top + height / 2.0},
                        {left + width / 2.0, top - 1.5}, {left + width / 2.0, top + height + 1.5}}) {
                    require(MachineFrameLayout.containsFrame(inside[0], inside[1], left, top, width, height),
                            "Visible padded case edge is treated as outside inventory");
                }
                for (double[] outside : new double[][]{{outer.x() - 0.1, top}, {outer.right(), top},
                        {left, outer.y() - 0.1}, {left, outer.bottom()}}) {
                    require(!MachineFrameLayout.containsFrame(outside[0], outside[1], left, top, width, height),
                            "Case hitbox extends past the visible symmetric border");
                }
                require(MachineFrameLayout.sideTabX(-26, width) == -29
                                && MachineFrameLayout.sideTabX(width, width) == width + 3
                                && MachineFrameLayout.sideTabX(8, width) == 8,
                        "Padded case moved an internal element or left its external tabs behind");
            }
        }
    }

    private static void drygmyWorkDeck() {
        require(MachinePanelLayout.DRYGMY_HEIGHT == 198 && MachinePanelLayout.DRYGMY_GRID_SHIFT_X == 4
                        && MachinePanelLayout.DRYGMY_ENERGY.equals(new MachinePanelLayout.Box(8, 44, 16, 16)),
                "Drygmy compact grid/charging-slot presentation changed");
        IGuiWrapper gui = layoutWrapper(210, MachinePanelLayout.DRYGMY_HEIGHT);
        List<MachinePanelLayout.Box> boxes = new java.util.ArrayList<>();
        boxes.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui, 7, 43)));
        for (int index = 0; index < 36; index++) {
            int x = 24 + MachinePanelLayout.DRYGMY_GRID_SHIFT_X + index % 9 * 18;
            int y = index < 9 ? 16 : 46 + (index - 9) / 9 * 18;
            boxes.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui, x - 1, y - 1)));
        }
        boxes.add(widgetBox(new GuiVerticalPowerBar(gui, new GuiBar.IBarInfoHandler() {
            @Override public Component getTooltip() { return Component.empty(); }
            @Override public double getLevel() { return 0.5; }
        }, 198, 16, 76)));
        boxes.add(widgetBox(new GuiSourceBar(gui, null, 190, 16, 76)));
        for (int first = 0; first < boxes.size(); first++) {
            workWidgetInside("Drygmy compact physical role", boxes.get(first), 210,
                    MachinePanelLayout.inventoryLabelY(MachinePanelLayout.DRYGMY_HEIGHT));
            for (int second = first + 1; second < boxes.size(); second++) {
                require(!boxes.get(first).overlaps(boxes.get(second)),
                        "Drygmy sample/output/charging/gauge visual bounds collide");
            }
        }
    }

    private static void miniRitualWorkDeck() {
        int width = MachinePanelLayout.MINI_RITUAL_WIDTH;
        int labelY = MachinePanelLayout.inventoryLabelY(
                MachinePanelLayout.MINI_RITUAL_HEIGHT);
        IGuiWrapper gui = layoutWrapper(width, MachinePanelLayout.MINI_RITUAL_HEIGHT);
        List<MachinePanelLayout.Box> inputs = new java.util.ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                GuiSlot slot = new GuiSlot(SlotType.NORMAL, gui,
                        MachinePanelLayout.MINI_RITUAL_INPUT_X + column * 18 - 1,
                        MachinePanelLayout.MINI_RITUAL_INPUT_Y + row * 18 - 1);
                MachinePanelLayout.Box box = widgetBox(slot);
                workWidgetInside("mini input", box, width, labelY);
                inputs.add(box);
            }
        }
        GuiSlot output = new GuiSlot(SlotType.NORMAL, gui,
                MachinePanelLayout.MINI_RITUAL_OUTPUT_X - 1,
                MachinePanelLayout.MINI_RITUAL_OUTPUT_Y - 1);
        GuiProgress progress = new GuiProgress((IProgressInfoHandler) () -> 0.5,
                ProgressType.SMALL_RIGHT, gui,
                MachinePanelLayout.MINI_RITUAL_PROGRESS_X,
                MachinePanelLayout.MINI_RITUAL_PROGRESS_Y);
        GuiVerticalPowerBar power = new GuiVerticalPowerBar(gui,
                new GuiBar.IBarInfoHandler() {
                    @Override public Component getTooltip() { return Component.empty(); }
                    @Override public double getLevel() { return 0.5; }
                }, width - 12, 16, 75);
        for (GuiElement element : List.of(output, progress, power)) {
            workWidgetInside("mini work element", widgetBox(element), width, labelY);
        }
        MachinePanelLayout.Box outputBox = widgetBox(output);
        MachinePanelLayout.Box progressBox = widgetBox(progress);
        MachinePanelLayout.Box powerBox = widgetBox(power);
        require(!outputBox.overlaps(progressBox) && !outputBox.overlaps(powerBox)
                        && !progressBox.overlaps(powerBox)
                        && inputs.stream().noneMatch(box -> box.overlaps(outputBox)
                        || box.overlaps(progressBox) || box.overlaps(powerBox)),
                "Mini ritual input/output/progress/power visual bounds collide");
    }

    private static void treeRitualWorkDeck() {
        int width = 176;
        int labelY = MachinePanelLayout.inventoryLabelY(MachinePanelLayout.TREE_HEIGHT);
        IGuiWrapper gui = layoutWrapper(width, MachinePanelLayout.TREE_HEIGHT);
        List<MachinePanelLayout.Box> inputs = new java.util.ArrayList<>();
        require(MachinePanelLayout.TREE_ENERGY.equals(new MachinePanelLayout.Box(8, 25, 16, 16)),
                "Tree ritual FE charging role lost its compact original-slot presentation");
        inputs.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                MachinePanelLayout.TREE_ENERGY.x() - 1, MachinePanelLayout.TREE_ENERGY.y() - 1)));
        for (int slot = 0; slot < 8; slot++) {
            GuiSlot visual = new GuiSlot(SlotType.NORMAL, gui,
                    NatureAuraScreenLayout.TREE_MATERIAL_X + slot % 4 * 18 - 1,
                    NatureAuraScreenLayout.TREE_MATERIAL_Y + slot / 4 * 18 - 1);
            inputs.add(widgetBox(visual));
        }
        for (int x : new int[]{NatureAuraScreenLayout.TREE_SAPLING_X,
                NatureAuraScreenLayout.TREE_GOLD_X}) {
            inputs.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                    x - 1, NatureAuraScreenLayout.TREE_MATERIAL_Y - 1)));
        }
        GuiSlot output = new GuiSlot(SlotType.NORMAL, gui,
                NatureAuraScreenLayout.TREE_OUTPUT_X - 1,
                NatureAuraScreenLayout.TREE_OUTPUT_Y - 1);
        GuiProgress progress = new GuiProgress((IProgressInfoHandler) () -> 0.5,
                ProgressType.SMALL_RIGHT, gui,
                NatureAuraScreenLayout.TREE_PROGRESS_X,
                NatureAuraScreenLayout.TREE_PROGRESS_Y);
        GuiVerticalPowerBar power = new GuiVerticalPowerBar(gui,
                new GuiBar.IBarInfoHandler() {
                    @Override public Component getTooltip() { return Component.empty(); }
                    @Override public double getLevel() { return 0.5; }
                }, width - 12, 16, 52);
        MachinePanelLayout.Box outputBox = widgetBox(output);
        MachinePanelLayout.Box progressBox = widgetBox(progress);
        MachinePanelLayout.Box powerBox = widgetBox(power);
        for (MachinePanelLayout.Box box : inputs) {
            workWidgetInside("tree input", box, width, labelY);
            require(!box.overlaps(progressBox) && !box.overlaps(outputBox)
                            && !box.overlaps(powerBox),
                    "Tree ritual ingredient border collides with work widget");
        }
        for (MachinePanelLayout.Box box : List.of(outputBox, progressBox, powerBox)) {
            workWidgetInside("tree output/progress/power", box, width, labelY);
        }
        require(!outputBox.overlaps(progressBox) && !outputBox.overlaps(powerBox)
                        && !progressBox.overlaps(powerBox),
                "Tree ritual arrow overlaps output slot or power gauge");
    }

    private static void forgeInternalTierDeck() {
        int width = 176;
        int height = MachinePanelLayout.FORGE_HEIGHT;
        IGuiWrapper gui = layoutWrapper(width, height);
        require(width == 176 && height == 202, "Forge deck lost its compact size and inventory-caption breathing room");
        GuiMachineInstrument tier = GuiMachineInstrument.stacked(gui,
                MachinePanelLayout.FORGE_TIER,
                () -> Component.literal("Machine tier"),
                () -> Component.literal("1 → 2"), MagicGuiTheme::accentRitual,
                () -> List.of(Component.literal("Internal forge tier")), null);
        MachinePanelLayout.Box card = widgetBox(tier);
        workWidgetInside("forge internal tier", card, width,
                MachinePanelLayout.inventoryLabelY(height));
        List<MachinePanelLayout.Box> instruments = new java.util.ArrayList<>();
        instruments.add(card);
        for (int channel = 0; channel < 4; channel++) {
            GuiMachineInstrument bank = GuiMachineInstrument.stacked(gui,
                    MachinePanelLayout.forgeEssence(channel), () -> Component.literal("Essence"),
                    () -> Component.literal("1.2k"), () -> 0xFF68C5D4,
                    () -> List.of(Component.literal("Exact stored essence")), () -> 0.5);
            instruments.add(widgetBox(bank));
            workWidgetInside("forge essence bank", widgetBox(bank), width, MachinePanelLayout.inventoryLabelY(height));
        }
        for (int first = 0; first < instruments.size(); first++) {
            for (int second = first + 1; second < instruments.size(); second++) {
                require(!instruments.get(first).overlaps(instruments.get(second)),
                        "Forge information instruments overlap one another");
            }
        }
        // Keep the authoritative/server coordinates in this independent
        // fixture. Enhancers are relocated into the client retained sidebar.
        List<int[]> itemSlots = new java.util.ArrayList<>();
        itemSlots.add(new int[]{8, 25}); // Existing FE charging slot.
        itemSlots.add(new int[]{80, 25}); // Main material.
        for (int row = 0; row < 2; row++) {
            for (int column = 0; column < 4; column++) {
                itemSlots.add(new int[]{26 + column * 18, 51 + row * 18});
            }
        }
        itemSlots.add(new int[]{134, 51});
        for (int column = 0; column < 4; column++) {
            itemSlots.add(new int[]{26 + column * 18, 98});
        }
        for (int x : new int[]{116, 134, 152}) itemSlots.add(new int[]{x, 98});
        require(itemSlots.size() == 18, "Authoritative Forge fixture lost a stored work/enhancer role");
        require(itemSlots.stream().noneMatch(item -> item[0] == 26 && item[1] == 25
                        || item[0] == 44 && item[1] == 25),
                "Retired core or hammer was restored to the forge work deck");

        List<MachinePanelLayout.Box> clientSlots = new java.util.ArrayList<>();
        clientSlots.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui, 7, 24))); // Existing FE charging slot.
        clientSlots.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                MachinePanelLayout.FORGE_MAIN.x() - 1, MachinePanelLayout.FORGE_MAIN.y() - 1)));
        for (int index = 0; index < 8; index++) clientSlots.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                MachinePanelLayout.forgeAuxiliaryX(index) - 1, MachinePanelLayout.forgeAuxiliaryY(index) - 1)));
        clientSlots.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                MachinePanelLayout.FORGE_OUTPUT.x() - 1, MachinePanelLayout.FORGE_OUTPUT.y() - 1)));
        require(MachinePanelLayout.FORGE_MAIN.equals(new MachinePanelLayout.Box(86, 25, 16, 16))
                        && MachinePanelLayout.FORGE_OUTPUT.equals(new MachinePanelLayout.Box(142, 53, 16, 16))
                        && MachinePanelLayout.FORGE_TIER.equals(new MachinePanelLayout.Box(28, 19, 44, 24)),
                "Forge material/output center line or compact tier information changed");
        for (int index = 0; index < 3; index++) {
            require(MachinePanelLayout.forgeVesselX(index) == 8
                            && MachinePanelLayout.forgeVesselY(index) == 44 + index * 18,
                    "Forge vessel role escaped its compact left-side vertical lane");
            clientSlots.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                    MachinePanelLayout.forgeVesselX(index) - 1, MachinePanelLayout.forgeVesselY(index) - 1)));
        }
        for (int channel = 0; channel < 4; channel++) {
            require(MachinePanelLayout.forgeEssence(channel).equals(new MachinePanelLayout.Box(
                            25 + channel * 36, 79, 36, 26)),
                    "Forge essence readings no longer form a compact four-column bank");
        }
        require(clientSlots.size() == 14, "Client Forge deck restored hidden server enhancers or omitted a vessel slot");
        MachinePanelLayout.Box progress = widgetBox(new GuiProgress((IProgressInfoHandler) () -> 0.5,
                ProgressType.BAR, gui, MachinePanelLayout.FORGE_PROGRESS_X, MachinePanelLayout.FORGE_PROGRESS_Y));
        for (int first = 0; first < clientSlots.size(); first++) {
            MachinePanelLayout.Box slot = clientSlots.get(first);
            workWidgetInside("Forge visible client slot", slot, width, MachinePanelLayout.inventoryLabelY(height));
            require(!slot.overlaps(progress) && instruments.stream().noneMatch(slot::overlaps),
                    "Grouped Forge vessel/material/output slot covers progress or an instrument");
            for (int second = first + 1; second < clientSlots.size(); second++) {
                require(!slot.overlaps(clientSlots.get(second)), "Visible client Forge slots overlap one another");
            }
        }
    }

    private static void essenceCondenserDeck() {
        int width = MachinePanelLayout.ESSENCE_CONDENSER_WIDTH, height = MachinePanelLayout.ESSENCE_CONDENSER_HEIGHT;
        require(width == 176 && height == 200, "Essence condenser lost its compact size and inventory-caption breathing room");
        IGuiWrapper gui = layoutWrapper(width, height);
        List<MachinePanelLayout.Box> boxes = new java.util.ArrayList<>();
        require(MachinePanelLayout.ESSENCE_CONDENSER_ENERGY.equals(new MachinePanelLayout.Box(8, 25, 16, 16)),
                "Condenser FE charging presentation was not moved out of the balance row");
        boxes.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui, MachinePanelLayout.ESSENCE_CONDENSER_ENERGY.x() - 1,
                MachinePanelLayout.ESSENCE_CONDENSER_ENERGY.y() - 1)));
        boxes.add(widgetBox(new GuiVerticalPowerBar(gui, new GuiBar.IBarInfoHandler() {
            @Override public Component getTooltip() { return Component.empty(); }
            @Override public double getLevel() { return 0.5; }
        }, width - 12, 16, 32)));
        for (int channel = 0; channel < 4; channel++) {
            require(MachinePanelLayout.essenceCondenserBank(channel).equals(new MachinePanelLayout.Box(23 + 36 * channel, 54, 36, 26))
                            && MachinePanelLayout.essenceCondenserSampleX(channel) == 32 + 36 * channel
                            && MachinePanelLayout.essenceCondenserSampleY(channel) == 28
                            && MachinePanelLayout.essenceCondenserVesselInputX(channel) == 24 + 36 * channel
                            && MachinePanelLayout.essenceCondenserVesselInputY(channel) == 86
                            && MachinePanelLayout.essenceCondenserVesselOutputX(channel) == 42 + 36 * channel
                            && MachinePanelLayout.essenceCondenserVesselOutputY(channel) == 86
                            && MachinePanelLayout.essenceCondenserProgress(channel).equals(new MachinePanelLayout.Box(27 + 36 * channel, 49, 28, 2)),
                    "Condenser sample/balance/progress/vessel lane lost the compact shared layout");
            boxes.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                    MachinePanelLayout.essenceCondenserSampleX(channel) - 1, MachinePanelLayout.essenceCondenserSampleY(channel) - 1)));
            boxes.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                    MachinePanelLayout.essenceCondenserVesselInputX(channel) - 1, MachinePanelLayout.essenceCondenserVesselInputY(channel) - 1)));
            boxes.add(widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                    MachinePanelLayout.essenceCondenserVesselOutputX(channel) - 1, MachinePanelLayout.essenceCondenserVesselOutputY(channel) - 1)));
            boxes.add(MachinePanelLayout.essenceCondenserBank(channel));
            boxes.add(MachinePanelLayout.essenceCondenserProgress(channel));
        }
        for (int first = 0; first < boxes.size(); first++) {
            workWidgetInside("Condenser lane", boxes.get(first), width, MachinePanelLayout.inventoryLabelY(height));
            for (int second = first + 1; second < boxes.size(); second++)
                require(!boxes.get(first).overlaps(boxes.get(second)), "Compact condenser lanes overlap slots, glyphs, progress or energy");
        }
    }

    private static void generatorModeControls() {
        int width = 176;
        int height = NatureAuraScreenLayout.COMPACT_HEIGHT;
        int labelY = NatureAuraScreenLayout.COMPACT_LABEL_Y;
        IGuiWrapper gui = layoutWrapper(width, height);
        MachinePanelLayout.Box fuelReadout = MachinePanelLayout.FUEL_AMOUNT;
        MachinePanelLayout.Box fuelProgress = widgetBox(new GuiProgress(
                (IProgressInfoHandler) () -> 0.5, ProgressType.BAR, gui, 86, 38));
        workWidgetInside("fuel Aura amount", fuelReadout, width, labelY);
        workWidgetInside("fuel mode information", NatureMachineLayout.FUEL_FOOTER, width, labelY);
        require(!fuelReadout.overlaps(fuelProgress) && !fuelReadout.overlaps(NatureMachineLayout.FUEL_FOOTER),
                "Fuel generator amount/mode information covers progress or one another");
        MachinePanelLayout.Box converterEnergy = widgetBox(new GuiSlot(
                SlotType.NORMAL, gui, 27, 34));
        List<MachinePanelLayout.Box> converterInstruments = List.of(
                widgetBox(GuiMachineInstrument.line(gui, MachinePanelLayout.CONVERSION_RATE,
                        () -> text("FE/Aura"), () -> text("200"), () -> 0xFF68C5D4, List::of)),
                widgetBox(GuiMachineInstrument.line(gui, MachinePanelLayout.FIELD_AMOUNT,
                        () -> text("Aura/t"), () -> text("1.0k"), () -> 0xFF68C5D4, List::of)),
                widgetBox(GuiMachineInstrument.line(gui, NatureMachineLayout.COMPACT_FOOTER,
                        () -> text("Mode"), () -> text("Auto"), () -> 0xFF68C5D4, List::of)));
        require(!converterInstruments.getFirst().overlaps(converterInstruments.get(1)),
                "Native converter instruments overlap one another");
        for (MachinePanelLayout.Box instrument : converterInstruments) {
            workWidgetInside("converter information instrument", instrument, width, labelY);
            require(!instrument.overlaps(converterEnergy),
                    "Native converter instrument covers its unchanged energy slot");
        }
        require(NatureMachineLayout.COMPACT_UPPER.equals(MachinePanelLayout.CONVERSION_RATE)
                        && NatureMachineLayout.COMPACT_LOWER.equals(MachinePanelLayout.FIELD_AMOUNT)
                        && NatureMachineLayout.COMPACT_FOOTER.equals(MachinePanelLayout.NATURE_COMPACT_FOOTER)
                        && NatureMachineLayout.FUEL_FOOTER.equals(MachinePanelLayout.FUEL_FOOTER)
                        && NatureMachineLayout.ANIMAL_AURA.equals(MachinePanelLayout.ANIMAL_AURA),
                "Nature client grouping drifted from the canonical shared machine-layout contracts");
        for (int first = 0; first < converterInstruments.size(); first++) for (int second = first + 1;
                second < converterInstruments.size(); second++) require(!converterInstruments.get(first).overlaps(converterInstruments.get(second)),
                "Converter rate/Aura/mode information groups overlap");
        for (int mode = 0; mode < 4; mode++) {
            Component label = NatureMachineSettingsSection.auraTypeLabel(mode);
            require(label.getString().equals("gui.mekanism_magic.aura_mode."
                            + new String[]{"auto", "overworld", "nether", "end"}[mode]),
                    "Generator type control lost a synchronized mode label");
        }
    }

    private static void factoryDeck(String name, int width, int height,
                                    int inventoryX, int inventoryY, int labelY,
                                    int processes, int firstX, int spacing,
                                    boolean hasSecondaryBar) {
        inventoryDeck(name, width, height, inventoryX, inventoryY, labelY);
        IGuiWrapper gui = layoutWrapper(width, height);
        GuiVerticalPowerBar power = new GuiVerticalPowerBar(gui,
                new GuiBar.IBarInfoHandler() {
                    @Override public Component getTooltip() { return Component.empty(); }
                    @Override public double getLevel() { return 0.5; }
                }, width - 12, 16, 52);
        MachinePanelLayout.Box powerBox = widgetBox(power);
        workWidgetInside(name + " power", powerBox, width, labelY);
        if (hasSecondaryBar) {
            GuiElement secondary = name.contains("nature")
                    ? new GuiAuraBar(gui, null, width - 20, 16, 52)
                    : new GuiSourceBar(gui, null, width - 20, 16, 52);
            MachinePanelLayout.Box secondaryBox = widgetBox(secondary);
            workWidgetInside(name + " resource", secondaryBox, width, labelY);
            require(!secondaryBox.overlaps(powerBox),
                    name + " resource gauge overlaps the native power gauge");
        }
        for (int process = 0; process < processes; process++) {
            int x = 4 + firstX + process * spacing;
            GuiMachineProgress progress = GuiMachineProgress.factoryDown(() -> 0.5, gui, x);
            MachinePanelLayout.Box box = widgetBox(progress);
            require(box.equals(new MachinePanelLayout.Box(x, 36, 8, 17))
                            && box.equals(progress.displayBounds()) && box.equals(MachineArrowLayout.factoryDown(x)),
                    name + " lost the short native factory arrow and matching hitbox");
            workWidgetInside(name + " lane " + process, box, width, labelY);
            require(!box.overlaps(powerBox), name + " progress reaches the power gauge");
            var input = widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                    firstX + process * spacing - 1, MachinePanelLayout.FACTORY_INPUT_Y - 1));
            var output = widgetBox(new GuiSlot(SlotType.NORMAL, gui,
                    firstX + process * spacing - 1, 57 - 1));
            require(MachinePanelLayout.FACTORY_INPUT_Y == 16,
                    "Factory input/charging row again covers the shared machine title band");
            workWidgetInside(name + " original factory input", input, width, labelY);
            workWidgetInside(name + " original factory output", output, width, labelY);
            require(!input.overlaps(box) && !output.overlaps(box)
                            && !input.overlaps(powerBox) && !output.overlaps(powerBox),
                    "Factory physical slot borders collide with their native progress or power gauge");
            require(box.y() - input.bottom() == 3 && output.y() - box.bottom() == 3
                            && box.x() + box.width() / 2 == input.x() + input.width() / 2
                            && box.x() + box.width() / 2 == output.x() + output.width() / 2,
                    "Factory arrow is not centered between complete 18px slot borders with equal 3px end gaps");
        }
        if (name.contains("nature")) {
            var footer = NatureMachineLayout.factoryFooter(width);
            require(footer.equals(new MachinePanelLayout.Box(28, 74, width - 54, 12))
                            && footer.equals(MachinePanelLayout.natureFactoryFooter(width)),
                    "Nature factory recipe footer drifted from the canonical inset and height");
            workWidgetInside(name + " recipe footer", footer, width, labelY);
            require(!footer.overlaps(powerBox), "Nature factory recipe footer covers its energy bar");
        }
    }

    private static void inventoryDeck(String name, int width, int height,
                                      int inventoryX, int inventoryY,
                                      int labelY) {
        IGuiWrapper gui = layoutWrapper(width, height);
        require(inventoryX == MachinePanelLayout.inventoryX(width),
                name + " inventory is not centered between frame sides");
        require(inventoryY + 81 == height,
                name + " inventory/hotbar is not anchored to the lower frame");
        require(labelY + 9 + MachineFrameLayout.WORK_INVENTORY_GAP <= inventoryY - 1,
                name + " inventory caption leaves no breathing room before the first 18px slot border");
        for (int slot = 0; slot < 36; slot++) {
            int x = inventoryX + slot % 9 * 18;
            int y = inventoryY + (slot < 27 ? slot / 9 * 18 : 58);
            GuiSlot visual = new GuiSlot(SlotType.NORMAL, gui, x - 1, y - 1);
            MachinePanelLayout.Box box = widgetBox(visual);
            require(box.x() >= 7 && box.right() <= width - 7
                            && box.y() > labelY + 8 && box.bottom() <= height - 6
                            && visual.getX() == 41 + box.x()
                            && visual.getY() == 23 + box.y(),
                    name + " real GuiSlot boundary differs from menu slot/frame: " + slot);
        }
    }

    private static IGuiWrapper layoutWrapper(int width, int height) {
        return (IGuiWrapper) Proxy.newProxyInstance(IGuiWrapper.class.getClassLoader(),
                new Class<?>[]{IGuiWrapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getXSize" -> width;
                    case "getYSize" -> height;
                    case "getGuiLeft" -> 41;
                    case "getGuiTop" -> 23;
                    default -> method.getReturnType() == boolean.class ? false
                            : method.getReturnType() == int.class ? 0 : null;
                });
    }

    /** Run the real native renderer through its shortened shaft, without a GL context or fake tile state. */
    private static void compactArrowRendering() throws Exception {
        horizontalArrowGaps();
        IGuiWrapper gui = (IGuiWrapper) Proxy.newProxyInstance(IGuiWrapper.class.getClassLoader(),
                new Class<?>[]{IGuiWrapper.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getGuiLeft" -> 41;
                    case "getGuiTop" -> 23;
                    case "getXSize" -> 176;
                    case "getYSize" -> 171;
                    case "trackWarning" -> args[1];
                    default -> method.getReturnType() == boolean.class ? false
                            : method.getReturnType() == int.class ? 0 : null;
                });
        double[] amount = {0};
        boolean[] active = {true}, warning = {false};
        IProgressInfoHandler handler = new IProgressInfoHandler() {
            @Override public double getProgress() { return amount[0]; }
            @Override public boolean isActive() { return active[0]; }
        };
        var progress = GuiMachineProgress.factoryDown(handler, gui, 59);
        progress.warning(WarningType.INPUT_DOESNT_PRODUCE_OUTPUT, () -> warning[0]);
        IRecipeViewerRecipeType<?> category = (IRecipeViewerRecipeType<?>) Proxy.newProxyInstance(
                IRecipeViewerRecipeType.class.getClassLoader(), new Class<?>[]{IRecipeViewerRecipeType.class},
                (proxy, method, args) -> null);
        IRecipeViewerRecipeType<?>[] categories = {category};
        require(progress.recipeViewerCategories(categories) == progress && progress.getRecipeCategories() == categories,
                "Compact arrows replaced the native recipe-viewer area/category contract");
        var nativeHandler = GuiProgress.class.getDeclaredField("handler");
        var nativeWarning = GuiProgress.class.getDeclaredField("warningSupplier");
        nativeHandler.setAccessible(true); nativeWarning.setAccessible(true);
        require(nativeHandler.get(progress) == handler && nativeWarning.get(progress) != null,
                "Compact arrows detached the native progress provider or warning tracker");
        var expected = new MachinePanelLayout.Box(100, 59, 8, 17);
        var graphics = ProgressProbe.recording();
        graphics.pose().translate(41, 23, 0);
        for (double value : new double[]{-1, 0, 0.01, 0.25, 0.5, 0.75, 0.99, 1, 2,
                Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            amount[0] = value;
            graphics.draws.clear();
            progress.drawBackground(graphics, -1, -1, 0);
            int fill = Double.isNaN(value) || value <= 0 ? 0 : value >= 1 ? 17 : (int) (value * 17);
            assertProgressPixels(graphics, expected, 0, 17);
            assertProgressPixels(graphics, expected, 1, fill);
            assertProgressPixels(graphics, expected, 2, 0);
            require(progress.displayBounds().equals(new MachinePanelLayout.Box(59, 36, 8, 17))
                            && progress.getWidth() == 8 && progress.getHeight() == 17 && graphics.scissors.isEmpty()
                            && graphics.pose().last().pose().m31() == 23 && progress.isRecipeViewerAreaActive(),
                    "Rendering leaked native dimensions, scissor, transform or recipe-area activity");
        }
        amount[0] = 0.1;
        warning[0] = true;
        graphics.draws.clear();
        progress.drawBackground(graphics, -1, -1, 0);
        assertProgressPixels(graphics, expected, 0, 17);
        assertProgressPixels(graphics, expected, 1, 0);
        assertProgressPixels(graphics, expected, 2, 17);
        active[0] = false;
        graphics.draws.clear();
        progress.drawBackground(graphics, -1, -1, 0);
        require(graphics.draws.isEmpty() && !progress.isRecipeViewerAreaActive(),
                "Inactive compact arrows retained an active native recipe area or painted progress");
    }

    private static void horizontalArrowGaps() {
        IGuiWrapper gui = layoutWrapper(210, 202);
        balancedHorizontal("mini ritual", gui, ProgressType.SMALL_RIGHT, MachinePanelLayout.MINI_RITUAL_PROGRESS_X,
                MachinePanelLayout.MINI_RITUAL_PROGRESS_Y, 122, new MachinePanelLayout.Box(157, 47, 18, 18));
        balancedHorizontal("ritual", gui, ProgressType.SMALL_RIGHT, 144, 62,
                140, new MachinePanelLayout.Box(175, 57, 18, 18));
        balancedHorizontal("catalyst assembler", gui, ProgressType.SMALL_RIGHT, 139, 62,
                131, new MachinePanelLayout.Box(175, 57, 18, 18));
        balancedHorizontal("enchanting apparatus", gui, ProgressType.SMALL_RIGHT, 134, 62,
                131, new MachinePanelLayout.Box(165, 57, 18, 18));
        balancedHorizontal("animal spawner", gui, ProgressType.SMALL_RIGHT, 98, 48,
                91, new MachinePanelLayout.Box(133, 43, 18, 18));
        balancedHorizontal("tree ritual", gui, ProgressType.SMALL_RIGHT, NatureAuraScreenLayout.TREE_PROGRESS_X,
                NatureAuraScreenLayout.TREE_PROGRESS_Y, 97, new MachinePanelLayout.Box(143, 52, 18, 18));
        balancedHorizontal("nature altar", gui, ProgressType.BAR, 77, 38,
                63, new MachinePanelLayout.Box(115, 34, 18, 18));
        balancedHorizontal("forge", gui, ProgressType.BAR, MachinePanelLayout.FORGE_PROGRESS_X,
                MachinePanelLayout.FORGE_PROGRESS_Y, 103, new MachinePanelLayout.Box(141, 52, 18, 18));
    }

    private static void balancedHorizontal(String name, IGuiWrapper gui, ProgressType type, int x, int y,
                                            int inputBorderRight, MachinePanelLayout.Box output) {
        var arrow = widgetBox(new GuiProgress((IProgressInfoHandler) () -> 0.5, type, gui, x, y));
        int before = arrow.x() - inputBorderRight, after = output.x() - arrow.right();
        require(before >= 3 && after >= 3 && Math.abs(before - after) <= 1,
                name + " arrow is not centered with breathing room between complete input/output borders");
        require(Math.abs((arrow.y() + arrow.height() / 2D) - (output.y() + output.height() / 2D)) <= 0.5,
                name + " arrow does not meet the output slot's centerline");
    }

    private static void assertProgressPixels(ProgressProbe graphics, MachinePanelLayout.Box expected,
                                             int layer, int filledRows) {
        int[] coverage = new int[17];
        for (var draw : graphics.draws) {
            require(draw.texture().equals(ProgressType.DOWN.getTexture())
                            && draw.bounds().x() == expected.x() && draw.bounds().width() == expected.width()
                            && draw.bounds().y() >= expected.y() && draw.bounds().bottom() <= expected.bottom(),
                    "Short arrow renderer escaped the real display rectangle or changed the native sprite");
            require(draw.sourceTop() + draw.bounds().height() <= 8 || draw.sourceTop() >= 11,
                    "Short arrow kept removed shaft pixels or cropped/remapped its native head");
            if (draw.layer() != layer) continue;
            for (int row = draw.bounds().y() - expected.y(); row < draw.bounds().bottom() - expected.y(); row++) coverage[row]++;
        }
        for (int row = 0; row < coverage.length; row++) require(coverage[row] == (row < filledRows ? 1 : 0),
                "Native arrow fill/warning layer is discontinuous, doubled, reversed or not proportional: layer="
                        + layer + " row=" + row + " fill=" + filledRows);
    }

    private record ProgressDraw(ResourceLocation texture, MachinePanelLayout.Box bounds, int layer, int sourceTop) { }

    private static final class ProgressProbe extends GuiGraphics {
        private List<ProgressDraw> draws;
        private List<MachinePanelLayout.Box> scissors;
        private ProgressProbe() { super(null, null); }
        private static ProgressProbe recording() throws ReflectiveOperationException {
            // As with Probe below, do not invoke GuiGraphics' Minecraft/GL-backed constructor.
            var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            var unsafe = (sun.misc.Unsafe) unsafeField.get(null);
            var graphics = (ProgressProbe) unsafe.allocateInstance(ProgressProbe.class);
            graphics.draws = new java.util.ArrayList<>();
            graphics.scissors = new java.util.ArrayList<>();
            var poseField = GuiGraphics.class.getDeclaredField("pose");
            unsafe.putObject(graphics, unsafe.objectFieldOffset(poseField), new com.mojang.blaze3d.vertex.PoseStack());
            return graphics;
        }
        @Override public void enableScissor(int left, int top, int right, int bottom) {
            scissors.add(new MachinePanelLayout.Box(left, top, right - left, bottom - top));
        }
        @Override public void disableScissor() {
            require(!scissors.isEmpty(), "Arrow renderer popped an absent clip");
            scissors.removeLast();
        }
        @Override public void blit(ResourceLocation texture, int x, int y, float u, float v,
                                    int width, int height, int textureWidth, int textureHeight) {
            var matrix = pose().last().pose();
            int left = Math.round(x + matrix.m30()), top = Math.round(y + matrix.m31());
            int right = left + width, bottom = top + height;
            int rawTop = top;
            for (var clip : scissors) {
                left = Math.max(left, clip.x()); top = Math.max(top, clip.y());
                right = Math.min(right, clip.right()); bottom = Math.min(bottom, clip.bottom());
            }
            if (right > left && bottom > top) draws.add(new ProgressDraw(texture,
                    new MachinePanelLayout.Box(left, top, right - left, bottom - top), Math.round(u) / 8,
                    Math.round(v) + top - rawTop));
        }
    }

    private static MachinePanelLayout.Box widgetBox(GuiElement element) {
        return new MachinePanelLayout.Box(element.getRelativeX(), element.getRelativeY(),
                element.getWidth(), element.getHeight());
    }

    private static void workWidgetInside(String name, MachinePanelLayout.Box box,
                                         int width, int labelY) {
        require(box.x() >= 6 && box.right() <= width - 6
                        && box.y() >= 15 && box.bottom() <= labelY - MachineFrameLayout.WORK_INVENTORY_GAP,
                name + " overlaps header, inventory caption or frame");
    }

    private static void wiring() throws Exception {
        String root = "com/example/mekanismmagic/";
        roleCaptionGuardContracts();
        for (String screen : List.of("naturesaura/client/NatureAltarProcessorScreen", "naturesaura/client/TreeRitualProcessorScreen",
                "naturesaura/client/OfferingProcessorScreen", "naturesaura/client/AnimalSpawnerProcessorScreen",
                "naturesaura/client/FeAuraConverterScreen", "naturesaura/client/AuraFuelGeneratorScreen", "naturesaura/client/AuraFieldInterfaceScreen",
                "naturesaura/client/NatureAltarFactoryScreen", "mekextras/client/ExtraNatureAltarFactoryScreen",
                "forbiddenarcanus/client/ClibanoProcessorScreen", "forbiddenarcanus/client/HephaestusForgeProcessorScreen",
                "forbiddenarcanus/client/EssenceCondenserScreen")) {
            Path file = Path.of("build/classes/java/main", root + "integration/" + screen + ".class");
            if (!Files.exists(file) && screen.contains("mekextras")) continue;
            boolean instrumentSample = INSTRUMENT_SAMPLES.contains(root + "integration/" + screen);
            boolean[] card = {false};
            boolean[] instrumentReadout = {false};
            new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if (screen.endsWith("TreeRitualProcessorScreen")) {
                        require(!name.equals("drawForegroundText"), "Removed work-area description labels were restored: " + screen);
                    }
                    return new RoleCaptionGuard(root + "integration/" + screen, name, new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                            require(!(owner.equals("com/mojang/blaze3d/vertex/PoseStack") && method.equals("scale")), "A redesigned screen still shrinks its information text: " + screen);
                            if (owner.equals(root + "client/gui/GuiMachineReadout")) {
                                require(!method.equals("status"), "A removed reminder control was registered: " + screen);
                                if (method.equals("line") || method.equals("stacked")) card[0] = true;
                            }
                            if (owner.equals(root + "client/gui/GuiMachineInstrument")
                                    && (method.equals("line") || method.equals("stacked"))) {
                                instrumentReadout[0] = true;
                            }
                            require(!(owner.equals(root + "container/MachinePanelLayout") && method.equals("status")),
                                    "A removed reminder hitbox was requested: " + screen);
                        }
                    });
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            boolean needsReadout = !screen.endsWith("TreeRitualProcessorScreen");
            require((card[0] || instrumentReadout[0]) == needsReadout,
                    "Essential readouts were removed or a reminder-only screen retained a placeholder: " + screen);
            require(instrumentReadout[0] == instrumentSample && (!instrumentSample || !card[0]),
                    "A Nature/Forbidden screen lost its grouped instrument or retained the old framed readout: " + screen);
        }
        instrumentScreenWiring(root);
        natureCatalystScreenWiring(root);
        for (int color : new int[]{0xFFEAD074, 0xFF68C5D4, 0xFFCC5263, 0xFF97CA63}) {
            require(GuiMachineReadout.textAccent(color, true) != color && GuiMachineReadout.textAccent(color, false) == color,
                    "Bright resource text did not adapt to the light palette");
        }
        for (int width : new int[]{176, 184, 218, 256, 294, 332, 370}) {
            for (String id : List.of("tree_ritual_processor")) {
                require(MachinePanelLayout.information(id, width).isEmpty(), "Reminder-only screen still reserves an invisible information region");
            }
            require(MachinePanelLayout.information("basic_nature_altar_factory", width)
                            .equals(List.of(MachinePanelLayout.natureFactoryFooter(width))),
                    "Nature factory lost its actual selected-recipe information contract");
            for (String id : List.of("nature_altar_processor", "clibano_processor", "hephaestus_forge_processor", "essence_condenser",
                    "aura_field_interface", "aura_fuel_generator", "fe_aura_converter", "animal_spawner_processor")) {
                require(MachinePanelLayout.information(id, width).stream().noneMatch(box -> box.width() == 14 && box.height() == 14),
                        "Removed reminder geometry remained in " + id);
            }
        }
        new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main", root + "client/gui/GuiMachineReadout.class")))
                .accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        require(!name.equals("status"), "Removed reminder factory remains callable");
                        if (!name.equals("renderForeground")) return null;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                                require(!field.equals("label"), "Descriptions must not be fetched by the permanent foreground renderer");
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    /** Ban permanent role captions only at caption/drawing sinks, not merely wherever a tooltip key is loaded. */
    private static final class RoleCaptionGuard extends MethodVisitor {
        private static final Set<String> ROLE_KEYS = Set.of(
                "gui.mekanism_magic.panel.inputs", "gui.mekanism_magic.panel.auxiliary", "gui.mekanism_magic.panel.output",
                "gui.mekanism_magic.hephaestus.section.main", "gui.mekanism_magic.hephaestus.section.auxiliary",
                "gui.mekanism_magic.hephaestus.section.output");
        private static final Set<String> FORGE_ROLES = Set.of("main", "auxiliary", "output");
        private final String screen;
        private final boolean permanentScope;
        private String roleKey, forgeRole;

        private RoleCaptionGuard(String screen, String method, MethodVisitor delegate) {
            super(Opcodes.ASM9, delegate);
            this.screen = screen;
            permanentScope = method.equals("addMachineGuiElements") || method.equals("addGuiElements")
                    || method.equals("drawForegroundText");
        }

        @Override public void visitLdcInsn(Object value) {
            if (permanentScope && value instanceof String text) {
                roleKey = ROLE_KEYS.contains(text) ? text : null;
                forgeRole = FORGE_ROLES.contains(text) ? text : null;
            }
            super.visitLdcInsn(value);
        }

        @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
            if (permanentScope) {
                boolean tooltip = method.equals("setTooltip") || method.equals("hover")
                        || owner.equals("net/minecraft/client/gui/components/Tooltip") && method.equals("create")
                        || owner.equals("mekanism/client/gui/tooltip/TooltipUtils") && method.equals("create");
                if (tooltip) {
                    roleKey = null;
                    forgeRole = null;
                }
                boolean groupedCaption = owner.equals("com/example/mekanismmagic/integration/naturesaura/client/NatureMachineGroups")
                        && method.equals("caption");
                boolean forgeCaption = owner.equals(screen) && screen.endsWith("HephaestusForgeProcessorScreen")
                        && method.equals("caption");
                boolean drawing = Set.of("drawString", "drawCenteredText", "drawScrollingString", "drawScaledScrollingString")
                        .contains(method);
                require(!(roleKey != null && (groupedCaption || forgeCaption || drawing)
                                || forgeRole != null && forgeCaption),
                        "Machine restored a permanent input/material/output role caption: " + screen + " key="
                                + (roleKey != null ? roleKey : forgeRole));
                if (groupedCaption || forgeCaption || drawing) {
                    roleKey = null;
                    forgeRole = null;
                }
            }
            super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
        }
    }

    private static void roleCaptionGuardContracts() {
        String forge = "com/example/mekanismmagic/integration/forbiddenarcanus/client/HephaestusForgeProcessorScreen";
        for (String method : List.of("addMachineGuiElements", "addGuiElements", "drawForegroundText")) {
            boolean groupedRejected = false, forgeRejected = false, directRejected = false;
            try {
                var guard = new RoleCaptionGuard(forge, method, null);
                guard.visitLdcInsn("gui.mekanism_magic.panel.inputs");
                guard.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "com/example/mekanismmagic/integration/naturesaura/client/NatureMachineGroups", "caption", "()V", false);
            } catch (AssertionError expected) { groupedRejected = true; }
            try {
                var guard = new RoleCaptionGuard(forge, method, null);
                guard.visitLdcInsn("auxiliary");
                guard.visitMethodInsn(Opcodes.INVOKEVIRTUAL, forge, "caption", "()V", false);
            } catch (AssertionError expected) { forgeRejected = true; }
            try {
                var guard = new RoleCaptionGuard(forge, method, null);
                guard.visitLdcInsn("gui.mekanism_magic.hephaestus.section.output");
                guard.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/network/chat/Component", "translatable", "()V", true);
                guard.visitMethodInsn(Opcodes.INVOKESTATIC, "com/example/mekanismmagic/client/gui/MagicGuiTheme", "drawCenteredText", "()V", false);
            } catch (AssertionError expected) { directRejected = true; }
            require(groupedRejected && forgeRejected && directRejected,
                    "Permanent role-caption guard missed a grouped, Forge-short-role or direct drawing path");
            var tooltip = new RoleCaptionGuard(forge, method, null);
            tooltip.visitLdcInsn("gui.mekanism_magic.panel.output");
            tooltip.visitMethodInsn(Opcodes.INVOKESTATIC, "net/minecraft/client/gui/components/Tooltip", "create", "()V", false);
            tooltip.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/gui/GuiGraphics", "drawString", "()V", false);
            for (String allowed : List.of("vessel_input", "vessel_working", "vessel_return", "gui.mekanism_magic.panel.essence.aureal")) {
                var resource = new RoleCaptionGuard(forge, method, null);
                resource.visitLdcInsn(allowed);
                resource.visitMethodInsn(Opcodes.INVOKEVIRTUAL, forge, "caption", "()V", false);
            }
        }
        // JEI and hover/slot construction are intentionally outside this permanent-rendering scope.
        var hover = new RoleCaptionGuard(forge, "addSlots", null);
        hover.visitLdcInsn("gui.mekanism_magic.panel.inputs");
        hover.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "net/minecraft/client/gui/GuiGraphics", "drawString", "()V", false);
    }

    private static void instrumentScreenWiring(String root) throws Exception {
        Path classes = Path.of("build/classes/java/main");
        Set<String> retired = Set.of(root + "client/gui/MekIndustrialScreen", root + "client/gui/GuiMekMachineReadout");
        for (String type : retired) {
            require(!Files.exists(classes.resolve(type + ".class"))
                            && !Files.exists(Path.of("src/main/java").resolve(type + ".java")),
                    "Retired native-gray GUI implementation remains executable: " + type);
        }
        Set<String> instrumentScreens = new java.util.HashSet<>();
        try (var files = Files.walk(classes.resolve(root))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                ClassReader reader = new ClassReader(Files.readAllBytes(file));
                require(Arrays.stream(reader.getInterfaces()).noneMatch(retired::contains),
                        "A screen still opts out of the shared Magic theme: " + reader.getClassName());
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        require(!name.equals("drawIndustrialMachineHeader"), "Retired native header renderer remains callable");
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitTypeInsn(int opcode, String type) {
                                require(!retired.contains(type), "A compiled screen still references the retired native skin");
                            }
                            @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                                  String descriptor, boolean isInterface) {
                                require(!retired.contains(owner) && !called.equals("drawIndustrialMachineHeader"),
                                        "A compiled screen still calls the retired native skin/header");
                                if (owner.equals(root + "client/gui/GuiMachineInstrument")
                                        && (called.equals("line") || called.equals("stacked"))) {
                                    instrumentScreens.add(reader.getClassName());
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        require(instrumentScreens.equals(INSTRUMENT_SAMPLES),
                "Information instruments do not match the explicit machine screen catalog: " + instrumentScreens);

        Object ordinary = Proxy.newProxyInstance(MagicThemedScreen.class.getClassLoader(),
                new Class<?>[]{MagicThemedScreen.class}, (proxy, method, args) -> null);
        Object multiInterface = Proxy.newProxyInstance(MagicThemedScreen.class.getClassLoader(),
                new Class<?>[]{MagicThemedScreen.class, IGuiWrapper.class}, (proxy, method, args) -> null);
        require(MagicGuiTextures.isThemeActive(ordinary) && MagicGuiTextures.isThemeActive(multiInterface)
                        && !MagicGuiTextures.isThemeActive(new Object()) && !MagicGuiTextures.isThemeActive(null),
                "A MagicThemedScreen was excluded from the unified skin, or an unrelated screen was included");
        new ClassReader(Files.readAllBytes(classes.resolve(root + "client/gui/MagicGuiTextures.class")))
                .accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        if (!name.equals("isThemeActive") || !descriptor.equals("(Ljava/lang/Object;)Z")) return null;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitTypeInsn(int opcode, String type) {
                                if (opcode == Opcodes.INSTANCEOF) require(type.equals(root + "client/gui/MagicThemedScreen"),
                                        "Theme eligibility added another sample-specific native-skin exclusion");
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        Set<String> retainedCalls = new java.util.HashSet<>();
        new ClassReader(Files.readAllBytes(classes.resolve(root + "client/screen/NativeMagicMachineScreen.class")))
                .accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitTypeInsn(int opcode, String type) {
                                require(!type.equals(root + "client/gui/GuiThemeToggleTab"),
                                        "The retired standalone theme tab was restored beside shared settings");
                            }
                            @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                                  String descriptor, boolean isInterface) {
                                if (name.equals("drawForegroundText") && called.equals("drawMachineHeader")) {
                                    retainedCalls.add("shared-header");
                                }
                                if (name.equals("addGuiElements") && called.equals("addMachineGuiElements")) {
                                    retainedCalls.add("machine-controls");
                                }
                                if (name.equals("renderBg") && owner.equals("mekanism/client/gui/GuiConfigurableTile")
                                        && called.equals("renderBg")) retainedCalls.add("native-background");
                                if (name.equals("renderBg") && owner.equals(root + "client/gui/MagicGuiTheme")
                                        && called.equals("renderMachineFrame")) retainedCalls.add("shared-frame");
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        require(retainedCalls.containsAll(Set.of("shared-header", "machine-controls", "native-background", "shared-frame")),
                "Grouped machine screens removed the shared header/frame or normal machine controls");
        boolean[] sharedSettings = {false}, sharedTheme = {false};
        new ClassReader(Files.readAllBytes(classes.resolve(root + "mixin/client/GuiMachineSettingsMixin.class")))
                .accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitTypeInsn(int opcode, String type) {
                                if (opcode == Opcodes.NEW && type.equals(root + "client/gui/GuiMachineSettingsTab")) sharedSettings[0] = true;
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        new ClassReader(Files.readAllBytes(classes.resolve(root + "client/gui/MachineSettingsSections$ThemeToggle.class")))
                .accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                                 String descriptor, boolean isInterface) {
                                if (owner.equals(root + "config/MagicClientConfig") && called.equals("toggleTheme")) sharedTheme[0] = true;
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        require(sharedSettings[0] && sharedTheme[0], "Shared machine settings lost its entry or real appearance-toggle action");
        settingsButtonThemeWiring(classes, root);

        ClassReader instrument = new ClassReader(Files.readAllBytes(classes.resolve(root + "client/gui/GuiMachineInstrument.class")));
        require(instrument.getSuperName().equals("mekanism/client/gui/element/GuiElement"),
                "Information instruments no longer use ordinary GUI elements");
        Set<String> paletteCalls = new java.util.HashSet<>();
        instrument.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                       String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                          String descriptor, boolean isInterface) {
                        require(!(owner.equals("com/mojang/blaze3d/vertex/PoseStack") && called.equals("scale")),
                                "Information instrument shrank its text instead of bounding it");
                        if (owner.equals(root + "client/gui/MagicGuiTheme")) paletteCalls.add(called);
                    }
                    @Override public void visitLdcInsn(Object value) {
                        if (value instanceof Integer color) require(!Set.of(0xFF30363A, 0xFF666666, 0xFFF0F0F0,
                                        0xFFB5BDBF, 0xFFF1F4F3).contains(color),
                                "The information instrument retained hardcoded native LCD colors");
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        require(paletteCalls.containsAll(Set.of("textPrimary", "textMuted", "readoutTrack")),
                "Information instruments do not reuse the shared text/track palette");
    }

    /** A no-pack skin must change the actual sliced button texture, not only its text palette. */
    private static void settingsButtonThemeWiring(Path classes, String root) throws Exception {
        Map<String, Set<String>> calls = new java.util.HashMap<>();
        for (String type : List.of("mixin/client/GuiUtilsMixin", "mixin/client/GuiElementThemeMixin",
                "client/gui/MagicGuiTextures")) {
            new ClassReader(Files.readAllBytes(classes.resolve(root + type + ".class")))
                    .accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                                   String signature, String[] exceptions) {
                            if (!Set.of("mekanismMagic$themeSlicedTexture", "mekanismMagic$flatButtonLabel", "resolve").contains(name)) return null;
                            var methods = calls.computeIfAbsent(name, ignored -> new java.util.HashSet<>());
                            return new MethodVisitor(Opcodes.ASM9) {
                                @Override public void visitMethodInsn(int opcode, String owner, String name,
                                                                      String descriptor, boolean isInterface) {
                                    methods.add(owner + "." + name);
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        require(calls.getOrDefault("mekanismMagic$themeSlicedTexture", Set.of()).contains(root + "client/gui/MagicGuiTextures.resolve")
                        && calls.getOrDefault("mekanismMagic$themeSlicedTexture", Set.of()).stream().noneMatch(call -> call.endsWith("MagicGuiStyle.isTerminal")),
                "Default no-pack buttons keep a native dark texture under the addon's light text palette");
        require(calls.getOrDefault("mekanismMagic$flatButtonLabel", Set.of()).contains(root + "client/gui/MagicGuiTextures.isThemeActive")
                        && calls.getOrDefault("mekanismMagic$flatButtonLabel", Set.of()).stream().noneMatch(call -> call.endsWith("MagicGuiStyle.isTerminal")),
                "No-pack themed button labels retain an incompatible native text shadow");
        require(calls.getOrDefault("resolve", Set.of()).stream().anyMatch(call -> call.endsWith(".getResource"))
                        && calls.getOrDefault("resolve", Set.of()).stream().noneMatch(call -> call.endsWith("MagicGuiStyle.isTerminal")),
                "Unknown addon glyphs only fall back safely with the external resource pack installed");
    }

    private static void defaultSettingsButtonContrast(String theme) throws Exception {
        String path = "/assets/mekanism_magic/" + (theme.equals("LIGHT") ? "gui_theme_light" : "gui_theme") + "/button.png";
        try (var input = MachinePanelsSelfTest.class.getResourceAsStream(path)) {
            require(input != null, "Missing bundled no-pack settings button texture: " + path);
            var texture = ImageIO.read(input);
            require(texture != null && texture.getWidth() == 200 && texture.getHeight() == 60,
                    "Settings button no longer uses Mekanism's three-state sliced texture");
            require(contrast(MagicGuiTheme.activeButtonText(), texture.getRGB(100, 30)) >= 4.5
                            && contrast(MagicGuiTheme.hoveredButtonText(), texture.getRGB(100, 50)) >= 4.5
                            && contrast(MagicGuiTheme.inactiveButtonText(), texture.getRGB(100, 10)) >= 3,
                    "Bundled " + theme + " normal/hover/disabled settings labels lack readable contrast");
        }
    }

    /** Pack 2.4 uses the same three strips in both themed button PNGs; keep mod checks independent of its ZIP build. */
    private static void terminalSettingsButtonContrast(String theme) throws Exception {
        require(com.example.mekanismmagic.client.gui.MagicGuiStyle.isTerminal(),
                "Terminal contrast guard did not load the actual source style contract");
        // Golden center pixels verified against both assets/mekanism_magic/gui_theme{,_light}/button.png entries
        // in AE-Terminal-Complete-Light-2.4.zip. The current source pack has style.json but no corresponding source PNGs.
        int disabled = 0xFF5C5E70, normal = 0xFFB0B2C4, hover = 0xFF9CD3FF;
        Path source = Path.of("resourcepacks/ae-terminal/assets/mekanism_magic",
                theme.equals("LIGHT") ? "gui_theme_light" : "gui_theme", "button.png");
        if (Files.isRegularFile(source)) {
            var texture = ImageIO.read(source.toFile());
            require(texture != null && texture.getWidth() == 200 && texture.getHeight() == 60
                            && texture.getRGB(100, 10) == disabled && texture.getRGB(100, 30) == normal && texture.getRGB(100, 50) == hover,
                    "Terminal source button PNG diverged from the independently verified Pack 2.4 strip contract");
            disabled = texture.getRGB(100, 10); normal = texture.getRGB(100, 30); hover = texture.getRGB(100, 50);
        }
        require(contrast(MagicGuiTheme.activeButtonText(), normal) >= 4.5
                        && contrast(MagicGuiTheme.hoveredButtonText(), hover) >= 4.5
                        && contrast(MagicGuiTheme.inactiveButtonText(), disabled) >= 3,
                "Terminal " + theme + " normal/hover/disabled settings labels lack readable contrast against the actual Pack 2.4 strips");
    }

    private static void natureCatalystScreenWiring(String root)
            throws Exception {
        String panel = root + "integration/naturesaura/client/"
                + "NatureAltarCatalystPanel";
        Set<String> retainedMethods = new java.util.HashSet<>();
        Set<String> renderMethods = new java.util.HashSet<>();
        new ClassReader(Files.readAllBytes(Path.of(
                "build/classes/java/main", panel + ".class")))
                .accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access,
                                                     String name,
                                                     String descriptor,
                                                     String signature,
                                                     String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode,
                                    String owner, String method,
                                    String calledDescriptor,
                                    boolean isInterface) {
                                if (owner.equals(root + "container/"
                                        + "RetainedSlotAccess")) {
                                    retainedMethods.add(method);
                                }
                                if (owner.equals(root + "client/gui/"
                                        + "MagicGuiTheme")) {
                                    renderMethods.add(method);
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        require(retainedMethods.containsAll(Set.of(
                        "mekanismMagic$positionRetainedSlot",
                        "mekanismMagic$setRetainedVisible",
                        "mekanismMagic$restoreRetainedSlot"))
                        && renderMethods.containsAll(Set.of(
                        "renderDockedPanel", "renderPanelCaption",
                        "renderPageNumber")),
                "Nature's shared panel did not move both original slots,"
                        + " restore them, or draw its paged frame");
        Set<String> required = Set.of("<init>", "tab",
                "previousButton", "nextButton", "captureOverlays",
                "renderBackground", "renderCaption", "containerTick",
                "dispose", "contains");
        for (String screen : List.of(
                "naturesaura/client/NatureAltarProcessorScreen",
                "naturesaura/client/NatureAltarFactoryScreen",
                "mekextras/client/ExtraNatureAltarFactoryScreen")) {
            Path file = Path.of("build/classes/java/main", root
                    + "integration/" + screen + ".class");
            if (!Files.exists(file)) {
                require(screen.contains("mekextras"),
                        "Nature catalyst client screen is missing: " + screen);
                continue;
            }
            Set<String> calls = new java.util.HashSet<>();
            boolean[] genericRetained = {false};
            boolean[] disablesRetained = {false};
            boolean[] legacyRecipeSelector = {false};
            boolean[] extraSettingsAdapter = {false};
            new ClassReader(Files.readAllBytes(file)).accept(
                    new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access,
                                                         String name,
                                                         String descriptor,
                                                         String signature,
                                                         String[] exceptions) {
                            return new MethodVisitor(Opcodes.ASM9) {
                                private boolean returnedFalse;

                                @Override
                                public void visitInsn(int opcode) {
                                    if (name.equals("usesRetainedModule")) {
                                        if (opcode == Opcodes.ICONST_0) {
                                            returnedFalse = true;
                                        } else if (opcode == Opcodes.IRETURN
                                                && returnedFalse) {
                                            disablesRetained[0] = true;
                                        }
                                    }
                                }

                                @Override
                                public void visitMethodInsn(int opcode,
                                        String owner, String method,
                                        String calledDescriptor,
                                        boolean isInterface) {
                                    if (owner.equals(panel)) {
                                        calls.add(method);
                                    }
                                    if (owner.equals(root + "integration/"
                                            + "naturesaura/client/"
                                            + "NatureAltarRecipeButton")
                                            && method.equals("<init>")) {
                                        legacyRecipeSelector[0] = true;
                                    }
                                    if (owner.equals(root + "integration/naturesaura/client/NatureMachineSettingsSection")
                                            && method.equals("registerAltar")) {
                                        extraSettingsAdapter[0] = true;
                                    }
                                    if (method.equals(
                                            "prepareRetainedModule")) {
                                        genericRetained[0] = true;
                                    }
                                }
                            };
                        }
                    }, ClassReader.SKIP_DEBUG
                            | ClassReader.SKIP_FRAMES);
            require(calls.containsAll(required),
                    "Nature client screen did not render and operate its one catalyst panel: "
                            + screen + ", missing=" + required.stream()
                            .filter(method -> !calls.contains(method))
                            .toList());
            require(!genericRetained[0]
                            && (!screen.endsWith("NatureAltarProcessorScreen")
                            || disablesRetained[0]),
                    "Nature screen also enabled the old separate retained module: "
                            + screen);
            require(!legacyRecipeSelector[0],
                    "Nature screen restored a second recipe-selector sidebar outside shared settings: " + screen);
            if (screen.contains("mekextras")) require(extraSettingsAdapter[0],
                    "Extra Nature factory lost its optional shared-work-page recipe adapter");
        }
        natureWorkSelectorWiring(root);
    }

    /** Recipe discovery, the optional Extras adapter and the original C2S action survive the UI move. */
    private static void natureWorkSelectorWiring(String root) throws Exception {
        String section = root + "integration/naturesaura/client/NatureMachineSettingsSection";
        Set<String> registeredAltars = new java.util.HashSet<>();
        boolean[] workPage = {false}, buildsControl = {false}, registeredSection = {false}, existingPacketRoute = {false};
        new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main", section + ".class")))
                .accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitLdcInsn(Object value) {
                                if (name.equals("register") && value instanceof org.objectweb.asm.Type type)
                                    registeredAltars.add(type.getInternalName());
                                if (name.equals("id") && "work".equals(value)) workPage[0] = true;
                            }
                            @Override public void visitTypeInsn(int opcode, String type) {
                                if (name.equals("build") && opcode == Opcodes.NEW && type.equals(section + "$ControlButton"))
                                    buildsControl[0] = true;
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main", root
                + "integration/naturesaura/client/NaturesAuraClient.class")))
                .accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                                 String descriptor, boolean isInterface) {
                                if (owner.equals(section) && called.equals("register")) registeredSection[0] = true;
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        new ClassReader(Files.readAllBytes(Path.of("build/classes/java/main", section + "$ControlButton.class")))
                .accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                               String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                                 String descriptor, boolean isInterface) {
                                if (owner.equals("net/minecraft/client/multiplayer/MultiPlayerGameMode")
                                        && called.equals("handleInventoryButtonClick")) existingPacketRoute[0] = true;
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        require(registeredAltars.containsAll(Set.of(root + "integration/naturesaura/NatureAltarProcessorBlockEntity",
                        root + "integration/naturesaura/NatureAltarFactoryBlockEntity"))
                        && workPage[0] && buildsControl[0] && registeredSection[0] && existingPacketRoute[0],
                "Shared Nature work settings lost its registered altar selectors or existing authenticated menu action");
        for (String retired : List.of("NatureAltarRecipeButton", "GuiAuraTypeModeButton")) {
            String path = root + "integration/naturesaura/client/" + retired;
            require(!Files.exists(Path.of("src/main/java", path + ".java"))
                            && !Files.exists(Path.of("build/classes/java/main", path + ".class")),
                    "Retired standalone Nature selector remains executable beside shared settings: " + retired);
        }
    }

    private static void memoryTheme(String theme) throws Exception {
        CommentedConfig config = CommentedConfig.inMemory();
        config.set("guiTheme", theme);
        MagicClientConfig.SPEC.correct(config);
        var accept = Arrays.stream(MagicClientConfig.SPEC.getClass().getMethods()).filter(method -> method.getName().equals("acceptConfig")).findFirst().orElseThrow();
        Class<?> loaded = accept.getParameterTypes()[0];
        Class<?> implementation = loaded.getPermittedSubclasses()[0];
        var constructor = implementation.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object[] arguments = Arrays.stream(constructor.getParameterTypes())
                .map(type -> CommentedConfig.class.isAssignableFrom(type) ? config : null).toArray();
        Object memory = constructor.newInstance(arguments);
        accept.invoke(MagicClientConfig.SPEC, memory);
        require(MagicClientConfig.isLight() == theme.equals("LIGHT"), "In-memory theme did not refresh");
    }

    /** Themed instruments keep labels/units visible while long localized values stay inside their original rectangles. */
    private static List<Integer> instrumentReadouts() throws Exception {
        MetricFont font = new MetricFont();
        IGuiWrapper gui = (IGuiWrapper) Proxy.newProxyInstance(IGuiWrapper.class.getClassLoader(),
                new Class<?>[]{IGuiWrapper.class}, (proxy, method, args) -> {
                    if (method.getName().equals("font")) return font;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == boolean.class) return false;
                    return null;
                });
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Probe probe = (Probe) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(Probe.class);
        probe.image = new BufferedImage((176 + 2 * MachineFrameLayout.CASE_PADDING) * 3,
                (MachinePanelLayout.FORGE_HEIGHT + 2 * MachineFrameLayout.CASE_PADDING) * 3, BufferedImage.TYPE_INT_ARGB);
        probe.offsetX = probe.offsetY = MachineFrameLayout.CASE_PADDING;
        probe.canvas = probe.image.createGraphics();
        probe.canvas.scale(3, 3);
        probe.canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        probe.canvas.setFont(new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, 9));
        probe.textCalls = new java.util.ArrayList<>();
        probe.textBounds = new java.util.ArrayList<>();
        List<Integer> fingerprints = new java.util.ArrayList<>();
        try {
            for (String locale : List.of("en_us", "zh_cn")) {
                var language = com.google.gson.JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/assets/mekanism_magic/lang/" + locale + ".json"))).getAsJsonObject();
                String efficiency = language.get("gui.mekanism_magic.panel.efficiency").getAsString();
                String generated = language.get("gui.mekanism_magic.panel.generated").getAsString();
                require(efficiency.contains("FE/") && generated.endsWith("/t"),
                        "Converter's permanently visible instrument labels lost their units in " + locale);
                List<InstrumentCase> cases = new java.util.ArrayList<>();
                cases.add(new InstrumentCase(MachinePanelLayout.CONVERSION_RATE, efficiency, "200", false, true));
                cases.add(new InstrumentCase(MachinePanelLayout.FIELD_AMOUNT, generated, "1.0k", false, true));
                boolean chinese = locale.equals("zh_cn");
                String aura = chinese ? "灵气" : "Aura", mode = chinese ? "类型" : "Mode";
                cases.add(new InstrumentCase(MachinePanelLayout.AURA_AMOUNT, aura, "1.0k", false, true));
                cases.add(new InstrumentCase(MachinePanelLayout.NATURE_COMPACT_FOOTER, mode, chinese ? "自动" : "Auto", false, true));
                cases.add(new InstrumentCase(MachinePanelLayout.FUEL_AMOUNT, aura, "1.0k", false, true));
                cases.add(new InstrumentCase(MachinePanelLayout.FUEL_FOOTER, mode, chinese ? "自动" : "Auto", false, true));
                cases.add(new InstrumentCase(MachinePanelLayout.ANIMAL_TARGET, chinese ? "目标" : "Target", "Cow", false, true));
                cases.add(new InstrumentCase(MachinePanelLayout.ANIMAL_AURA, aura, "1.0k", false, true));
                cases.add(new InstrumentCase(MachinePanelLayout.OFFERING_BONUS, chinese ? "加成" : "Bonus", "+20%", false, true));
                cases.add(new InstrumentCase(MachinePanelLayout.CLIBANO_FIRE, chinese ? "灵魂火" : "Soul Fire", "120s", false, true));
                cases.add(new InstrumentCase(MachinePanelLayout.natureFactoryFooter(176), chinese ? "配方" : "Recipe",
                        chinese ? "无" : "None", false, true));
                cases.add(new InstrumentCase(MachinePanelLayout.essenceCondenserBank(0),
                        language.get("gui.mekanism_magic.panel.essence.aureal").getAsString(), "1.2k", true, false));
                cases.add(new InstrumentCase(MachinePanelLayout.FORGE_TIER,
                        language.get("gui.mekanism_magic.hephaestus.instrument.tier").getAsString(), "1→2", true, true));
                for (int channel = 0; channel < 4; channel++) {
                    String key = "gui.mekanism_magic.panel.essence."
                            + new String[]{"aureal", "souls", "blood", "experience"}[channel];
                    cases.add(new InstrumentCase(MachinePanelLayout.forgeEssence(channel),
                            language.get(key).getAsString(), "1.2k", true, false));
                }
                for (InstrumentCase sample : cases) {
                    var label = new java.util.concurrent.atomic.AtomicReference<>(text(sample.label()));
                    var value = new java.util.concurrent.atomic.AtomicReference<>(text(sample.value()));
                    var ratio = new java.util.concurrent.atomic.AtomicReference<>(0.5D);
                    java.util.function.Supplier<List<Component>> details = () -> {
                        Component complete = label.get().copy().append(": ").append(value.get());
                        Component detail = text("Exact retained amount: 9223372036854775807 / 9223372036854775807");
                        return List.of(complete, detail, detail.copy());
                    };
                    GuiMachineInstrument widget = sample.stacked()
                            ? GuiMachineInstrument.stacked(gui, sample.box(), label::get, value::get,
                            () -> 0xFF68C5D4, details, sample.box().equals(MachinePanelLayout.FORGE_TIER) ? null : ratio::get)
                            : GuiMachineInstrument.line(gui, sample.box(), label::get, value::get,
                            () -> 0xFF68C5D4, details);
                    for (int variant = 0; variant < 3; variant++) {
                        label.set(text(variant == 2 ? sample.label() + " / 持续运行的完整设备状态说明" : sample.label()));
                        value.set(text(variant == 0 ? sample.value()
                                : variant == 1 ? "9223372036854775807" : "-9223372036854775808 Aura/t"));
                        for (double fill : new double[]{-2, 0, 0.75, 1, 2, Double.NaN, Double.POSITIVE_INFINITY}) {
                            ratio.set(fill);
                            probe.canvas.setColor(new Color(0xFF808080, true));
                            probe.canvas.fillRect(0, 0, 176, MachinePanelLayout.FORGE_HEIGHT);
                            probe.bounds = MachineFrameLayout.outerBounds(0, 0, 176, MachinePanelLayout.FORGE_HEIGHT);
                            MagicGuiTheme.renderMachineFrame(probe, 0, 0, 176, MachinePanelLayout.FORGE_HEIGHT,
                                    MachinePanelLayout.inventoryLabelY(MachinePanelLayout.FORGE_HEIGHT));
                            probe.bounds = sample.box();
                            instrumentCard(probe, widget, sample.box(), variant == 0,
                                    variant == 0 && sample.requireFullLabel());
                            fingerprints.add(Arrays.hashCode(probe.image.getRGB((sample.box().x() + probe.offsetX) * 3,
                                    (sample.box().y() + probe.offsetY) * 3, sample.box().width() * 3, sample.box().height() * 3,
                                    null, 0, sample.box().width() * 3)));
                        }
                    }
                }
            }
        } finally {
            probe.canvas.dispose();
        }
        return List.copyOf(fingerprints);
    }

    private record InstrumentCase(MachinePanelLayout.Box box, String label, String value,
                                  boolean stacked, boolean requireFullLabel) { }

    private static void instrumentCard(Probe probe, GuiMachineInstrument widget, MachinePanelLayout.Box bounds,
                                boolean requireFullValue, boolean requireFullLabel) {
        MachinePanelLayout.Box panelBounds = probe.bounds;
        probe.bounds = bounds;
        probe.textCalls.clear();
        if (probe.textBounds == null) probe.textBounds = new java.util.ArrayList<>();
        if (probe.textColors == null) probe.textColors = new java.util.ArrayList<>();
        probe.textBounds.clear();
        probe.textColors.clear();
        widget.drawBackground(probe, -1, -1, 0);
        int backgroundWidth = bounds.width() * 3;
        int[] background = probe.image.getRGB((probe.offsetX + bounds.x()) * 3,
                (probe.offsetY + bounds.y()) * 3, backgroundWidth, bounds.height() * 3,
                null, 0, backgroundWidth);
        widget.renderForeground(probe, -1, -1);
        widget.updateTooltip(widget.getX() + 1, widget.getY() + 1);
        try {
            var label = GuiMachineInstrument.class.getDeclaredField("label");
            var value = GuiMachineInstrument.class.getDeclaredField("value");
            var details = GuiMachineInstrument.class.getDeclaredField("details");
            var tooltip = GuiMachineInstrument.class.getDeclaredField("previousTooltip");
            label.setAccessible(true); value.setAccessible(true); details.setAccessible(true); tooltip.setAccessible(true);
            String description = ((Component) ((java.util.function.Supplier<?>) label.get(widget)).get()).getString();
            String currentValue = ((Component) ((java.util.function.Supplier<?>) value.get(widget)).get()).getString();
            require(probe.textCalls.size() == 2 && probe.textBounds.size() == 2,
                    "The information instrument must show its label/unit and value without extra reminder text");
            require(probe.textCalls.stream().allMatch(line -> line.codePoints().anyMatch(Character::isLetterOrDigit)),
                    "A narrow instrument hid its label or value behind only an ellipsis");
            require(!probe.textBounds.getFirst().overlaps(probe.textBounds.get(1)),
                    "Instrument label/unit and value overlap at the normal font size");
            require(probe.textColors.equals(List.of(MagicGuiTheme.textMuted() | 0xFF000000,
                            MagicGuiTheme.textPrimary() | 0xFF000000)),
                    "Instrument label/value stopped using the active shared text palette");
            for (int line = 0; line < probe.textBounds.size(); line++) {
                MachinePanelLayout.Box glyphs = probe.textBounds.get(line);
                int x = (glyphs.x() - bounds.x() + glyphs.width() / 2) * 3;
                int y = (glyphs.y() - bounds.y() + glyphs.height() / 2) * 3;
                require(contrast(probe.textColors.get(line), background[y * backgroundWidth + x]) >= 3,
                        "Instrument text is not readable on the current default/terminal work surface");
            }
            if (requireFullValue) require(probe.textCalls.get(1).equals(currentValue),
                    "A normal instrument value was unnecessarily truncated: " + currentValue);
            if (requireFullLabel) require(probe.textCalls.getFirst().equals(description),
                    "A normal instrument label or unit was hidden: " + description);
            var lines = (List<?>) tooltip.get(widget);
            var suppliedDetails = (List<?>) ((java.util.function.Supplier<?>) details.get(widget)).get();
            var expected = new java.util.HashSet<String>();
            expected.add(description + ": " + currentValue);
            for (Object detail : suppliedDetails) expected.add(((Component) detail).getString());
            require(lines.size() == expected.size()
                            && lines.stream().anyMatch(line -> ((Component) line).getString().contains(description))
                            && lines.stream().anyMatch(line -> ((Component) line).getString().contains(currentValue))
                            && expected.stream().allMatch(detail -> lines.stream()
                            .anyMatch(line -> ((Component) line).getString().equals(detail))),
                    "Instrument tooltip lost the full localized label, exact value/details, or duplicated entries");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        require(widgetBox(widget).equals(bounds), "Information instrument changed the shared machine panel rectangle");
        require(!widget.active && !widget.mouseClicked(widget.getX() + 1, widget.getY() + 1, 0),
                "Information instrument intercepts a slot/control click");
        require(widget.isMouseOver(widget.getX() + 1, widget.getY() + 1)
                        && !widget.isMouseOver(widget.getX() + widget.getWidth(), widget.getY() + 1),
                "Instrument hover details lost their exact rectangle");
        widget.visible = false;
        require(!widget.isMouseOver(widget.getX() + 1, widget.getY() + 1),
                "Hidden information instrument still intercepts hover");
        widget.visible = true;
        probe.bounds = panelBounds;
    }

    private static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int color) {
        double red = linear((color >> 16) & 255);
        double green = linear((color >> 8) & 255);
        double blue = linear(color & 255);
        return red * 0.2126 + green * 0.7152 + blue * 0.0722;
    }

    private static double linear(int value) {
        double channel = value / 255D;
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }

    private static void render(String theme) throws Exception {
        require(MagicGuiTheme.MachineStatus.IDLE.color()
                        == MagicGuiTheme.textPrimary(),
                "Idle header lost the title's readable color in " + theme);
        MetricFont font = new MetricFont();
        IGuiWrapper gui = (IGuiWrapper) Proxy.newProxyInstance(IGuiWrapper.class.getClassLoader(), new Class<?>[]{IGuiWrapper.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("font")) return font;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == double.class) return 0D;
                    return null;
                });
        // GuiGraphics' real constructor requires a running Minecraft/GL context. This test subclass overrides every used operation.
        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Probe probe = (Probe) ((sun.misc.Unsafe) unsafeField.get(null)).allocateInstance(Probe.class);
        probe.image = new BufferedImage(700 * 3, 300 * 3, BufferedImage.TYPE_INT_ARGB);
        probe.textCalls = new java.util.ArrayList<>();
        probe.canvas = probe.image.createGraphics();
        probe.canvas.scale(3, 3);
        probe.canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        probe.canvas.setFont(new java.awt.Font("Microsoft YaHei", java.awt.Font.PLAIN, 9));
        probe.canvas.setColor(new Color(theme.equals("LIGHT") ? 0xD9E1EB : 0x070B12));
        probe.canvas.fillRect(0, 0, 700, 300);
        for (int panel = 0; panel < 3; panel++) {
            probe.offsetX = 12 + panel * 194; probe.offsetY = 18;
            int width = panel == 2 ? MachinePanelLayout.ESSENCE_CONDENSER_WIDTH : 176;
            int height = panel == 0 ? MachinePanelLayout.height("nature_altar_processor")
                    : panel == 1 ? MachinePanelLayout.FORGE_HEIGHT : MachinePanelLayout.ESSENCE_CONDENSER_HEIGHT;
            int label = panel == 0 ? MachinePanelLayout.labelY("nature_altar_processor")
                    : MachinePanelLayout.inventoryLabelY(height);
            probe.bounds = MachineFrameLayout.outerBounds(0, 0, width, height);
            MagicGuiTheme.renderMachineFrame(probe, 0, 0, width, height, label);
            probe.drawString(font, panel == 0 ? "自然灌注祭坛" : panel == 1 ? "赫菲斯托斯锻造台" : "四相精华凝聚器", 10, 4, MagicGuiTheme.textPrimary(), false);
            probe.drawString(font, "物品栏", MachinePanelLayout.inventoryX(width), label, MagicGuiTheme.textMuted(), false);
            for (int slot = 0; slot < 36; slot++) slot(probe, MachinePanelLayout.inventoryX(width) + slot % 9 * 18,
                    MachinePanelLayout.inventoryY(height) + (slot < 27 ? slot / 9 * 18 : 58));
            if (panel == 0) {
                slot(probe, 28, 53); slot(probe, 46, 35); slot(probe, 116, 35);
                instrumentCard(probe, GuiMachineInstrument.line(gui, MachinePanelLayout.AURA_AMOUNT, () -> text("灵气"), () -> text("10.0k"),
                        MagicGuiTheme::accentSource, () -> List.of(text("灵气 10000 / 100000"))), MachinePanelLayout.AURA_AMOUNT, true, true);
            } else if (panel == 1) {
                slot(probe, 8, 25);
                slot(probe, MachinePanelLayout.FORGE_MAIN.x(), MachinePanelLayout.FORGE_MAIN.y());
                slot(probe, MachinePanelLayout.FORGE_OUTPUT.x(), MachinePanelLayout.FORGE_OUTPUT.y());
                for (int index = 0; index < 8; index++) slot(probe,
                        MachinePanelLayout.forgeAuxiliaryX(index), MachinePanelLayout.forgeAuxiliaryY(index));
                for (int index = 0; index < 3; index++) slot(probe,
                        MachinePanelLayout.forgeVesselX(index), MachinePanelLayout.forgeVesselY(index));
                instrumentCard(probe, GuiMachineInstrument.stacked(gui, MachinePanelLayout.FORGE_TIER, () -> text("等级"), () -> text("3"),
                        MagicGuiTheme::accentRitual, () -> List.of(text("核心等级3"), text("核心等级3")), null), MachinePanelLayout.FORGE_TIER,
                        true, true);
                String[] names = {"耀金", "灵魂", "血液", "经验"};
                int[] colors = {0xFFEAD074, 0xFF68C5D4, 0xFFCC5263, 0xFF97CA63};
                for (int channel = 0; channel < 4; channel++) {
                    int index = channel;
                    instrumentCard(probe, GuiMachineInstrument.stacked(gui, MachinePanelLayout.forgeEssence(index), () -> text(names[index]), () -> text("1.2k"),
                            () -> colors[index], () -> List.of(text(names[index] + " 9223372036854775807 / 2000")), () -> 0.6),
                            MachinePanelLayout.forgeEssence(index), true, true);
                }
            } else {
                slot(probe, MachinePanelLayout.ESSENCE_CONDENSER_ENERGY.x(), MachinePanelLayout.ESSENCE_CONDENSER_ENERGY.y());
                String[] names = {"耀金", "灵魂", "血液", "经验"};
                int[] colors = {0xFFE8BF5B, 0xFFB4A4DF, 0xFFCC5263, 0xFF91C56B};
                for (int channel = 0; channel < 4; channel++) {
                    int index = channel;
                    slot(probe, MachinePanelLayout.essenceCondenserSampleX(channel), MachinePanelLayout.essenceCondenserSampleY(channel));
                    instrumentCard(probe, GuiMachineInstrument.stacked(gui, MachinePanelLayout.essenceCondenserBank(index),
                            () -> text(names[index]), () -> text("1.2k"), () -> colors[index],
                            () -> List.of(text(names[index] + " 1200 / 10000")), () -> 0.6),
                            MachinePanelLayout.essenceCondenserBank(index), true, true);
                    slot(probe, MachinePanelLayout.essenceCondenserVesselInputX(channel), MachinePanelLayout.essenceCondenserVesselInputY(channel));
                    slot(probe, MachinePanelLayout.essenceCondenserVesselOutputX(channel), MachinePanelLayout.essenceCondenserVesselOutputY(channel));
                }
            }
        }
        probe.canvas.dispose();
        Files.createDirectories(Path.of("build/reports/machine-panels"));
        String prefix = com.example.mekanismmagic.client.gui.MagicGuiStyle.isTerminal() ? "terminal-" : "";
        ImageIO.write(probe.image, "png", Path.of("build/reports/machine-panels/" + prefix + theme.toLowerCase() + ".png").toFile());
    }

    private static Component text(String value) { return Component.literal(value); }
    private static void card(Probe probe, GuiMachineReadout widget, MachinePanelLayout.Box bounds) {
        MachinePanelLayout.Box panelBounds = probe.bounds;
        probe.bounds = bounds;
        probe.textCalls.clear();
        widget.drawBackground(probe, -1, -1, 0);
        widget.renderForeground(probe, -1, -1);
        widget.updateTooltip(bounds.x() + 1, bounds.y() + 1);
        try {
            var label = GuiMachineReadout.class.getDeclaredField("label");
            var value = GuiMachineReadout.class.getDeclaredField("value");
            var tooltip = GuiMachineReadout.class.getDeclaredField("previousTooltip");
            label.setAccessible(true); value.setAccessible(true); tooltip.setAccessible(true);
            String description = ((Component) ((java.util.function.Supplier<?>) label.get(widget)).get()).getString();
            String currentValue = ((Component) ((java.util.function.Supplier<?>) value.get(widget)).get()).getString();
            require(probe.textCalls.size() == 1, "Readout still draws an explanation or hides its value");
            require(probe.textCalls.getFirst().equals(currentValue), "The essential value was removed with its caption");
            var lines = (List<?>) tooltip.get(widget);
            require(lines.stream().anyMatch(line -> ((Component) line).getString().contains(description)),
                    "Removed description is not available in the tooltip");
            require(lines.stream().anyMatch(line -> ((Component) line).getString().contains(currentValue)), "Tooltip lost the displayed value");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        require(!widget.active && !widget.mouseClicked(bounds.x() + 1, bounds.y() + 1, 0), "Readout intercepts clicks");
        require(widget.isMouseOver(bounds.x() + 1, bounds.y() + 1), "Readout lost its detailed hover text");
        // Keep the narrow bounds during every widget draw/assertion above, then
        // restore the full panel for the next preview slot or frame element.
        probe.bounds = panelBounds;
    }
    private static void slot(Probe probe, int x, int y) {
        probe.fill(x - 1, y - 1, x + 17, y + 17, MagicGuiTheme.isLight() ? 0xFF8C9CB0 : 0xFF34465E);
        probe.fill(x, y, x + 16, y + 16, MagicGuiTheme.isLight() ? 0xFFE7EDF4 : 0xFF0B111C);
    }

    private static final class MetricFont extends Font {
        private MetricFont() { super(id -> null, false); }
        @Override public int width(String text) { return text.codePoints().map(c -> c == 32 ? 4 : c > 255 ? 9 : 6).sum(); }
        @Override public int width(FormattedText text) { return width(text.getString()); }
        @Override public String plainSubstrByWidth(String text, int width) {
            StringBuilder result = new StringBuilder();
            for (int c : text.codePoints().toArray()) {
                String value = new String(Character.toChars(c));
                if (width(result + value) > width) break;
                result.append(value);
            }
            return result.toString();
        }
    }
    private static final class Probe extends GuiGraphics {
        private BufferedImage image;
        private java.awt.Graphics2D canvas;
        private int offsetX, offsetY;
        private MachinePanelLayout.Box bounds;
        private List<String> textCalls;
        private List<MachinePanelLayout.Box> textBounds;
        private List<Integer> textColors;
        private Probe() { super(null, null); }
        @Override public void fill(int x1, int y1, int x2, int y2, int color) {
            require(x1 >= bounds.x() && y1 >= bounds.y() && x2 <= bounds.right() && y2 <= bounds.bottom(), "Renderer drew outside its information card");
            canvas.setColor(new Color(color, true));
            canvas.fillRect(offsetX + x1, offsetY + y1, x2 - x1, y2 - y1);
        }
        @Override public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
            textCalls.add(text);
            if (textBounds != null) textBounds.add(new MachinePanelLayout.Box(x, y, font.width(text), 9));
            if (textColors != null) textColors.add(color | 0xFF000000);
            require(x >= bounds.x() && x + font.width(text) <= bounds.right() && y >= bounds.y() && y + 9 <= bounds.bottom(),
                    "Unscaled readout text escaped its allocated card: " + text);
            canvas.setColor(new Color(color | 0xFF000000, true));
            canvas.drawString(text, offsetX + x, offsetY + y + 8);
            return x + font.width(text);
        }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
