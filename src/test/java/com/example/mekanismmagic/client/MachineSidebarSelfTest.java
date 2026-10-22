package com.example.mekanismmagic.client;

import com.example.mekanismmagic.client.gui.GuiCatalystLibraryTab;
import com.example.mekanismmagic.client.gui.GuiChalkModuleTab;
import com.example.mekanismmagic.client.gui.GuiDictionaryModuleTab;
import com.example.mekanismmagic.client.gui.MachineAeTooltips;
import com.example.mekanismmagic.client.gui.MachineAeSettingsSection;
import com.example.mekanismmagic.client.gui.MachineSettingsSections;
import com.example.mekanismmagic.client.gui.GuiMachineSettingsWindow;
import com.example.mekanismmagic.container.MachinePanelLayout.Box;
import com.example.mekanismmagic.client.gui.GuiMachineSettingsTab;
import com.example.mekanismmagic.client.gui.MagicLeftControlLayout;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.client.gui.MagicMachineFrame;
import com.example.mekanismmagic.container.MachineFrameLayout;
import com.example.mekanismmagic.container.DockedPanelLayout;
import com.example.mekanismmagic.container.MachineSidebarLayout;
import com.example.mekanismmagic.container.RetainedModuleLayout;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.naturesaura.client.GuiNatureCatalystTab;
import com.example.mekanismmagic.integration.naturesaura.client.NatureCatalystPanelLayout;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.tab.GuiRedstoneControlTab;
import mekanism.client.gui.element.tab.GuiWarningTab;
import mekanism.client.gui.element.tab.GuiSortingTab;
import mekanism.client.gui.element.tab.GuiEnergyTab;
import mekanism.client.gui.element.slot.GuiSlot;
import mekanism.client.gui.element.slot.SlotType;
import mekanism.common.inventory.warning.WarningTracker;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Real native tab bounds/hitboxes plus dependency and event-wiring contracts, without a GL context. */
public final class MachineSidebarSelfTest {
    private MachineSidebarSelfTest() { }

    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        int cases = 0;
        for (int width : new int[]{176, 210, 244, 338, 608}) {
            for (int height : new int[]{166, 171, 176, 180, 183, 184, 185, 186, 190, 194, 196, 198, 200, 202, 208, 218, 240, 300}) {
                for (int scale = 1; scale <= 4; scale++) {
                    check(width, height, scale, false);
                    cases++;
                    if (height >= 194) { check(width, height, scale, true); cases++; }
                }
            }
        }
        screenEventScope();
        paddedCaseTabContract();
        optionalOverlayFrameScope();
        warningPlacementContract();
        dependencyContract();
        aeSupplyTooltipContract();
        settingsScreenWiring();
        aeButtonContract();
        settingsModuleContract();
        System.out.println("MachineSidebarSelfTest: " + cases + " layouts, unified settings hitboxes/registry/pages/no-AE entry, AE whitelist/resource tooltips, warning/sorting/energy separation, repeat init/resize, retained catalog and ME row contracts passed");
    }

    private static void paddedCaseTabContract() throws Exception {
        for (int width : new int[]{176, 194, 210, 256, 370}) {
            IGuiWrapper gui = (IGuiWrapper) Proxy.newProxyInstance(IGuiWrapper.class.getClassLoader(),
                    new Class<?>[]{IGuiWrapper.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getXSize" -> width;
                        case "getYSize" -> 200;
                        case "getGuiLeft" -> 41;
                        case "getGuiTop" -> 23;
                        default -> method.getReturnType() == boolean.class ? false
                                : method.getReturnType() == int.class ? 0 : null;
                    });
            var left = new GuiMachineSettingsTab(gui, null, new Box(-26, 104, 26, 26));
            var right = new GuiRedstoneControlTab(gui, null);
            var internal = new GuiSlot(SlotType.NORMAL, gui, 7, 24);
            var elements = List.of(left, right, internal);
            for (int repeat = 0; repeat < 3; repeat++) {
                MagicMachineFrame.alignSideTabs(gui, elements);
                require(left.getRelativeX() == -26 - MachineFrameLayout.CASE_PADDING
                                && right.getRelativeX() == width + MachineFrameLayout.CASE_PADDING
                                && internal.getRelativeX() == 7 && internal.getRelativeY() == 24,
                        "Frame tab alignment drifted, resized a slot, or lost the physical case edge");
                require(left.getX() == 41 + left.getRelativeX() && right.getX() == 41 + right.getRelativeX()
                                && left.isMouseOver(left.getX() + 13, left.getY() + 13)
                                && right.isMouseOver(right.getX() + 13, right.getY() + 13),
                        "Padded native tab painting and its actual mouse target differ");
            }
        }
        String root = "com/example/mekanismmagic/";
        var frame = bytecode(root + "mixin/client/GuiMachineFrameMixin");
        require(frame.contains("type:193:" + root + "client/gui/MagicThemedScreen")
                        && frame.contains("call:" + root + "container/MachineFrameLayout.containsFrame"),
                "Physical frame input expansion lost addon-only scope or its shared geometry");
        var events = bytecode(root + "client/MachineSidebarEvents");
        require(events.contains("call:" + root + "client/gui/MagicMachineFrame.alignSideTabs"),
                "Native side tabs no longer align with the padded case after init/resize");
        String mixins = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/mekanism_magic.client.mixins.json"));
        require(mixins.contains("GuiMachineFrameMixin"), "Visible machine frame has no native texture/input bridge");
    }

    private static void optionalOverlayFrameScope() throws Exception {
        String root = "com/example/mekanismmagic/";
        for (String overlay : List.of("MekEnergisticsPatternTabFrameMixin", "MekEnergisticsInterfaceTabFrameMixin")) {
            String type = root + "integration/mekenergistics/mixin/client/" + overlay;
            if (!java.nio.file.Files.exists(java.nio.file.Path.of("build/classes/java/main", type + ".class"))) continue;
            var facts = bytecode(type);
            require(facts.contains("type:193:" + root + "client/gui/MagicThemedScreen")
                            && facts.contains("call:org/spongepowered/asm/mixin/injection/invoke/arg/Args.set"),
                    "Hand-rendered ME overlay moves unrelated screens or stopped sharing its render/click bounds");
        }
    }

    private static void screenEventScope() {
        for (boolean themed : new boolean[]{false, true}) {
            WrapperScreen screen = themed ? new ThemedScreen() : new WrapperScreen();
            var redstone = new GuiRedstoneControlTab(screen, null);
            screen.addTab(redstone);
            var event = new net.neoforged.neoforge.client.event.ScreenEvent.Init.Post(
                    screen, new java.util.ArrayList<>(screen.children()), ignored -> { }, ignored -> { });
            MachineSidebarEvents.onScreenInit(event);
            require(redstone.getRelativeY() == (themed ? 179 : 137), "Post-init event changed another mod's screen or skipped ours");
            require(redstone.getRelativeX() == 210 + (themed ? MachineFrameLayout.CASE_PADDING : 0),
                    "Physical frame alignment changed another mod's tab or skipped our native tab");
        }
    }

    private static void warningPlacementContract() {
        for (int height : new int[]{166, 180, 208}) {
            IGuiWrapper gui = (IGuiWrapper) Proxy.newProxyInstance(
                    IGuiWrapper.class.getClassLoader(), new Class<?>[]{IGuiWrapper.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getXSize" -> 176;
                        case "getYSize" -> height;
                        case "getGuiLeft" -> 50;
                        case "getGuiTop" -> 30;
                        default -> method.getReturnType() == boolean.class ? false
                                : method.getReturnType() == int.class ? 0 : null;
                    });
            var warning = new GuiWarningTab(gui, new WarningTracker(), true);
            warning.visible = true;
            var settings = new GuiMachineSettingsTab(gui, null,
                    new Box(-26, MagicLeftControlLayout.settingsY(gui), 26, 26));
            var energy = MagicLeftControlLayout.alignEnergyTab(gui,
                    new GuiEnergyTab(gui, () -> List.of()));
            List<GuiElement> controls = new java.util.ArrayList<>(List.of(
                    warning, settings, energy));
            if (height == 166) controls.add(new GuiSortingTab(gui, null));
            MachineSidebarEvents.alignWarning(gui, controls);
            require(!overlaps(warning, settings) && !overlaps(warning, energy)
                            && (height != 166 || !overlaps(warning, controls.getLast()))
                            && warning.getX() == 50 + warning.getRelativeX()
                            && warning.getY() == 30 + warning.getRelativeY()
                            && warning.isMouseOver(warning.getX() + 13,
                            warning.getY() + 13),
                    "Native warning tab overlaps left controls or lost its moved hitbox: " + height);
            require(height != 180 || warning.getRelativeX() == -26
                            && warning.getRelativeY() == 62,
                    "Ordinary machine warning did not use its free upper-left row");
            require(height != 166 || warning.getRelativeX() == -52
                            && warning.getRelativeY() == 62,
                    "Factory warning did not avoid the 35px sorting row");
            int x = warning.getRelativeX(), y = warning.getRelativeY();
            warning.visible = false;
            MachineSidebarEvents.alignWarning(gui, controls);
            warning.visible = true;
            require(warning.getRelativeX() == x && warning.getRelativeY() == y
                            && !overlaps(warning, settings) && !overlaps(warning, energy),
                    "A warning becoming visible after init obscures settings or changes the stable dock");
        }
    }

    private static boolean overlaps(GuiElement first, GuiElement second) {
        return first.getRelativeX() < second.getRelativeRight()
                && first.getRelativeRight() > second.getRelativeX()
                && first.getRelativeY() < second.getRelativeBottom()
                && first.getRelativeBottom() > second.getRelativeY();
    }

    private static class WrapperScreen extends net.minecraft.client.gui.screens.Screen implements IGuiWrapper {
        private WrapperScreen() { super(net.minecraft.network.chat.Component.empty()); }
        void addTab(GuiElement tab) { addRenderableWidget(tab); }
        @Override public int getGuiLeft() { return 20; }
        @Override public int getGuiTop() { return 10; }
        @Override public int getXSize() { return 210; }
        @Override public int getYSize() { return 208; }
        @Override public net.minecraft.client.gui.Font font() { return null; }
    }

    private static final class ThemedScreen extends WrapperScreen implements MagicThemedScreen { }

    private static void check(int width, int height, int scale, boolean dictionaryNeighbour) {
        int[] dimensions = {width, height};
        IGuiWrapper gui = (IGuiWrapper) Proxy.newProxyInstance(IGuiWrapper.class.getClassLoader(), new Class<?>[]{IGuiWrapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getXSize" -> dimensions[0];
                    case "getYSize" -> dimensions[1];
                    case "getGuiLeft" -> 73;
                    case "getGuiTop" -> 41;
                    default -> method.getReturnType() == boolean.class ? false : method.getReturnType() == int.class ? 0 : null;
                });
        AtomicInteger clicks = new AtomicInteger();
        var chalk = new GuiChalkModuleTab(gui, null, () -> false, clicks::incrementAndGet);
        var dictionary = new GuiDictionaryModuleTab(gui, null, () -> false, clicks::incrementAndGet);
        var library = new GuiCatalystLibraryTab<>(gui, null, () -> false, clicks::incrementAndGet);
        var nature = new GuiNatureCatalystTab<>(gui, null,
                () -> false, clicks::incrementAndGet);
        var settings = new GuiMachineSettingsTab(gui, null,
                new Box(-26, MagicLeftControlLayout.settingsY(gui), 26, 26));
        var redstone = new GuiRedstoneControlTab(gui, null);
        List<GuiElement> tabs = List.of(chalk, dictionary, library, redstone);
        int originalRedstoneY = redstone.getRelativeY();
        require(originalRedstoneY == 137, "Native redstone anchor changed; re-evaluate dependency contract");
        MachineSidebarEvents.alignRedstone(gui, tabs);
        int newY = redstone.getRelativeY();
        require(newY + redstone.getHeight() == height - 3, "Redstone did not follow the frame bottom");
        require(redstone.getY() == 41 + newY && redstone.getX() == 73 + width, "Absolute hitbox differs from rendering coordinates");
        require(redstone.isMouseOver(redstone.getX() + 13, redstone.getY() + 13), "Moved redstone lost its click target");
        int retainedY = RetainedModuleLayout.of(width, 1, dictionaryNeighbour).tabY();
        require((retainedY + 26) * scale + 2 * scale <= newY * scale, "Retained tab overlaps redstone control");
        require(nature.getRelativeX() == width
                        && nature.getRelativeY()
                        == MachineSidebarLayout.moduleY(false)
                        && nature.getRelativeY()
                        >= MachineSidebarLayout.PATTERN_TAB_Y
                        + MachineSidebarLayout.TAB_HEIGHT
                        + MachineSidebarLayout.TAB_GAP
                        && nature.getRelativeBottom()
                        + MachineSidebarLayout.TAB_GAP <= newY,
                "Nature's single catalyst tab overlaps the ME or redstone row");
        require(nature.isMouseOver(nature.getX() + 13,
                        nature.getY() + 9)
                        && !nature.isMouseOver(nature.getX() + 13,
                        41 + MachineSidebarLayout.PATTERN_TAB_Y + 13),
                "Nature catalyst tab lost its own right-side hitbox");
        require(settings.visible && settings.active && settings.getRelativeX() == -26
                        && settings.getRelativeY() == height - 62
                        && settings.getRelativeRight() == 0
                        && settings.isMouseOver(settings.getX() + 13, settings.getY() + 13)
                        && !settings.isMouseOver(settings.getX() + 1, settings.getY() + 1)
                        && !settings.isMouseOver(settings.getX() + 27, settings.getY() + 13),
                "Unified settings lost its native inset hitbox or disappeared without AE/tile state");
        require(settings.getX() == 73 - 26 && settings.getY() == 41 + height - 62,
                "Settings rendering coordinates differ from the real click target");
        settings.onClick(settings.getX() + 13, settings.getY() + 13, 0);
        nature.onClick(nature.getX() + 13, nature.getY() + 9, 0);
        checkNaturePanelGeometry(width);
        for (GuiElement module : List.of(chalk, dictionary, library)) {
            require(module.getRelativeY() * scale >= (MachineSidebarLayout.PATTERN_TAB_Y + 26 + 2) * scale,
                    "Module overlaps optional ME pattern tab");
            require(module.isMouseOver(module.getX() + 13, module.getY() + 13), "Module center is not clickable");
            require(!module.isMouseOver(module.getX() + 13, 41 + 84), "Old overlapping ME area still activates module");
            module.onClick(module.getX() + 13, module.getY() + 13, 0);
        }
        require(clicks.get() == 4, "Relayout changed module toggle actions");
        if (dictionaryNeighbour) {
            require(dictionary.getRelativeBottom() + 2 <= retainedY, "Dictionary overlaps retained module");
            require(!redstone.isMouseOver(redstone.getX() + 13, 41 + retainedY + 13), "Retained module click hits redstone");
        }
        MachineSidebarEvents.alignRedstone(gui, tabs);
        require(redstone.getRelativeY() == newY && redstone.getY() == newY + 41, "Repeat init drifted the tab");
        dimensions[1] += 28;
        MachineSidebarEvents.alignRedstone(gui, tabs);
        require(redstone.getRelativeY() == newY + 28 && redstone.getY() == newY + 69, "Resize lost rendering/hitbox synchronization");
        settings.move(17, 9);
        require(settings.getRelativeX() == -9 && settings.getRelativeY() == height - 53
                        && settings.getX() == 64 && settings.getY() == height - 12
                        && settings.isMouseOver(settings.getX() + 13, settings.getY() + 13),
                "Moving native settings desynchronized its rendering and hitbox");
        var resized = new GuiMachineSettingsTab(gui, null,
                new Box(-26, MagicLeftControlLayout.settingsY(gui), 26, 26));
        require(resized.getRelativeY() == height - 34
                        && resized.isMouseOver(resized.getX() + 13, resized.getY() + 13),
                "Resizing stopped reconstructing settings at its shared dock");
    }

    private static void aeSupplyTooltipContract() {
        var energyOnly = new MachineAeResourceHooks.Snapshot(true, true,
                true, true, MachineAeResourceHooks.ENERGY,
                MachineAeResourceHooks.ENERGY, 400, 0, 0);
        var energyAndSource = new MachineAeResourceHooks.Snapshot(true,
                true, true, false, MachineAeResourceHooks.ENERGY
                | MachineAeResourceHooks.SOURCE, 0, 0, 0, 0);
        var allDisabled = new MachineAeResourceHooks.Snapshot(true,
                false, true, true, 7, 7, 400, 250, 80);
        int allResources = MachineAeResourceHooks.ENERGY | MachineAeResourceHooks.SOURCE
                | MachineAeResourceHooks.AURA | MachineAeResourceHooks.ESSENCE_MASK;
        var allEssences = new MachineAeResourceHooks.Snapshot(true, true,
                true, true, allResources, allResources, 400, 250, 80,
                11, 22, 33, 44);
        String energy = "gui.mekanism_magic.ae_resource.energy";
        String source = "gui.mekanism_magic.ae_resource.source";
        String aura = "gui.mekanism_magic.ae_resource.aura";
        String essence = "gui.mekanism_magic.ae_resource.essence.";
        require(topLevelTooltipKey(energyOnly, energy)
                        && !topLevelTooltipKey(energyOnly, source)
                        && !topLevelTooltipKey(energyOnly, aura)
                        && topLevelTooltipKey(energyAndSource, energy)
                        && topLevelTooltipKey(energyAndSource, source)
                        && !topLevelTooltipKey(energyAndSource, aura)
                        && !topLevelTooltipKey(energyOnly, essence + "aureal")
                        && topLevelTooltipKey(allDisabled, aura),
                "AE tooltip displayed an unsupported resource or omitted a supported one");
        for (String channel : List.of("aureal", "souls", "blood", "experience")) {
            require(topLevelTooltipKey(allEssences, essence + channel),
                    "AE tooltip omitted a typed essence channel: " + channel);
        }
        int packed = allEssences.packedFlags();
        require((packed & 15) == 15
                        && ((packed >>> 4) & 127) == allResources
                        && ((packed >>> 12) & 127) == allResources
                        && allEssences.lastEssence(0) == 11
                        && allEssences.lastEssence(1) == 22
                        && allEssences.lastEssence(2) == 33
                        && allEssences.lastEssence(3) == 44
                        && energyOnly.lastEssence(0) == 0,
                "Seven-bit AE supply status lost an essence channel or broke older snapshots");
        String transferredEssences = MachineAeTooltips.tooltipLines(allEssences).toString();
        require(transferredEssences.split("gui.mekanism_magic.ae_resource.recent_essence", -1).length - 1 == 4,
                "AE essence transfer tooltip omitted its point unit or a channel");
        String disabled = MachineAeTooltips.tooltipLines(allDisabled)
                .toString();
        require(disabled.contains("gui.mekanism_magic.ae_resource.disabled")
                        && !disabled.contains("gui.mekanism_magic.ae_resource.recent"),
                "Disabled native AE supply still claims a recent transfer");
        var partial = new MachineAeResourceHooks.Snapshot(true, true, true, true,
                allResources, allResources, 400, 250, 80, 11, 22, 33, 44,
                MachineAeResourceHooks.ENERGY | MachineAeResourceHooks.ESSENCE_BLOOD);
        require(resourceTooltipLine(partial, energy).contains("recent_joules")
                        && resourceTooltipLine(partial, essence + "blood").contains("recent_essence")
                        && resourceTooltipLine(partial, source).contains("disabled")
                        && resourceTooltipLine(partial, aura).contains("disabled")
                        && resourceTooltipLine(partial, essence + "souls").contains("disabled"),
                "Per-kind disabled resources display a recent transfer merely because the input master is on");
    }

    private static boolean topLevelTooltipKey(
            MachineAeResourceHooks.Snapshot snapshot, String key) {
        return MachineAeTooltips.tooltipLines(snapshot).stream().anyMatch(
                line -> line.getContents() instanceof net.minecraft.network
                        .chat.contents.TranslatableContents translated
                        && key.equals(translated.getKey()));
    }

    private static String resourceTooltipLine(MachineAeResourceHooks.Snapshot snapshot, String key) {
        return MachineAeTooltips.tooltipLines(snapshot).stream().filter(
                line -> line.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && key.equals(text.getKey())).findFirst().orElseThrow().toString();
    }

    private static void settingsScreenWiring() throws Exception {
        String root = "com/example/mekanismmagic/";
        for (String screen : List.of(
                "client/screen/NativeMagicMachineScreen",
                "client/screen/NativeSpiritFactoryScreen",
                "integration/arsnouveau/client/ImbuementFactoryScreen",
                "integration/naturesaura/client/NatureAltarFactoryScreen",
                "integration/mekextras/client/ExtraSpiritFactoryScreen",
                "integration/mekextras/client/ExtraImbuementFactoryScreen",
                "integration/mekextras/client/ExtraNatureAltarFactoryScreen")) {
            var resource = MachineSidebarSelfTest.class.getResourceAsStream(
                    "/" + root + screen + ".class");
            if (resource == null && screen.contains("mekextras")) {
                continue;
            }
            require(resource != null, "Missing native/factory machine screen: " + screen);
            boolean[] added = {false}, themed = {false};
            try (resource) {
                new ClassReader(resource).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public void visit(int version, int access, String name, String signature,
                                                String superName, String[] interfaces) {
                        themed[0] = List.of(interfaces).contains(root + "client/gui/MagicThemedScreen");
                    }
                    @Override public MethodVisitor visitMethod(int access,
                            String name, String descriptor,
                            String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitTypeInsn(int opcode,
                                    String type) {
                                if (opcode == Opcodes.NEW && (type.equals(root + "client/gui/GuiMachineSettingsTab")
                                        || type.equals(root + "client/gui/GuiThemeToggleTab")
                                        || type.equals(root + "client/gui/GuiAeSupplyTab")
                                        || type.equals(root + "client/gui/GuiAeConfigurationTab")
                                        || type.endsWith("/GuiAuraTypeModeButton")
                                        || type.endsWith("/NatureAltarRecipeButton"))) {
                                    added[0] = true;
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            require(themed[0] && !added[0],
                    "Screen lost shared settings scope or retained an independent adjustment entry: " + screen);
        }
        var injection = bytecode(root + "mixin/client/GuiMachineSettingsMixin");
        require(injection.contains("type:193:" + root + "client/gui/MagicThemedScreen")
                        && injection.contains("type:193:mekanism/common/inventory/container/tile/MekanismTileContainer")
                        && injection.contains("type:193:" + root + "api/IMekanismMagicAutomation")
                        && injection.contains("type:187:" + root + "client/gui/GuiMachineSettingsTab")
                        && injection.contains("call:" + root + "client/gui/MagicLeftControlLayout.settingsY")
                        && injection.contains("at:TAIL"),
                "Unified settings lost native/factory-scoped Init.TAIL creation or its fixed dock");
        AtomicInteger newEntries = new AtomicInteger();
        try (var in = MachineSidebarSelfTest.class.getResourceAsStream("/" + root + "mixin/client/GuiMachineSettingsMixin.class")) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitTypeInsn(int opcode, String type) {
                            if (opcode == Opcodes.NEW && type.equals(root + "client/gui/GuiMachineSettingsTab")) newEntries.incrementAndGet();
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        require(newEntries.get() == 1, "Shared mixin creates multiple settings tabs");
        var sideConfig = bytecode(root + "mixin/client/GuiSideConfigurationDefaultPageMixin");
        require(sideConfig.stream().noneMatch(fact -> fact.contains("GuiAe") || fact.contains("GuiMachineSettings")),
                "Side configuration retained a second settings/AE control");
        for (String removed : List.of("client/gui/GuiThemeToggleTab", "client/gui/GuiAeSideConfigControl",
                "client/gui/GuiAeConfigurationTab", "client/gui/GuiAeConfigurationWindow", "client/gui/AeConfigurationLayout",
                "mixin/client/GuiMachineAeConfigurationMixin", "integration/naturesaura/client/GuiAuraTypeModeButton",
                "integration/naturesaura/client/NatureAltarRecipeButton")) {
            require(!java.nio.file.Files.exists(java.nio.file.Path.of("src/main/java", root + removed + ".java")),
                    "Retired independent adjustment implementation remains: " + removed);
        }
        try (var config = MachineSidebarSelfTest.class.getResourceAsStream("/mekanism_magic.client.mixins.json")) {
            require(config != null, "Missing client mixin manifest");
            String json = new String(config.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            require(json.contains("GuiMachineSettingsMixin") && !json.contains("GuiMachineAeConfigurationMixin"),
                    "Client manifest lost the shared settings entry or revived the retired AE entry");
        }
    }

    private static void aeButtonContract() {
        int[] inputs = {1, 2, 4, 8, 16, 32, 64};
        int[] outputs = {2, 4, 8, 16, 32, 64, 128, 256};
        java.util.Set<Integer> buttons = new java.util.HashSet<>(List.of(9351, 9353));
        for (int index = 0; index < inputs.length; index++) {
            int id = MachineAeResourceHooks.buttonIdForInput(inputs[index]);
            require(id == 9360 + index && buttons.add(id), "Input button whitelist changed or collided");
        }
        for (int index = 0; index < outputs.length; index++) {
            int id = MachineAeResourceHooks.buttonIdForOutput(outputs[index]);
            require(id == 9370 + index && buttons.add(id), "Output button whitelist changed or collided");
        }
        for (int id = 9300; id <= 9400; id++) require(MachineAeResourceHooks.isConfigurationButton(id) == buttons.contains(id),
                "AE UI/server button whitelist accepted an unrelated ID: " + id);
        for (int invalid : new int[]{-1, 0, 3, 5, 127, 510, 512, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            require(MachineAeResourceHooks.buttonIdForInput(invalid) == -1
                            && MachineAeResourceHooks.buttonIdForOutput(invalid) == -1,
                    "AE function mapping accepted an unsupported/composite resource bit");
        }
        require(MachineAeResourceHooks.buttonIdForOutput(MachineAeResourceHooks.ENERGY) == -1
                        && MachineAeResourceHooks.buttonIdForInput(MachineAeResourceHooks.OUTPUT_ITEMS) == -1
                        && MachineAeResourceHooks.buttonIdForInput(MachineAeResourceHooks.OUTPUT_FLUIDS) == -1,
                "AE configuration offered an unsupported resource direction");
        var controls = new MachineAeResourceHooks.ControlSnapshot(true, false, true, 127, 510, 127, 510);
        require(controls.enabledInputMask() == 0 && controls.enabledOutputMask() == 510,
                "Input master OFF also disabled output-only functions");
        require(!MachineAeResourceHooks.controls(null).supported()
                        && MachineAeResourceHooks.controls(null).inputAvailableMask() == 0
                        && MachineAeResourceHooks.controls(null).outputAvailableMask() == 0,
                "Missing AE/machine context fabricated a visible configuration entry");

    }

    private static java.util.Set<String> bytecode(String type) throws Exception {
        var facts = new java.util.HashSet<String>();
        try (var in = MachineSidebarSelfTest.class.getResourceAsStream("/" + type + ".class")) {
            require(in != null, "Missing UI contract class: " + type);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitTypeInsn(int opcode, String owner) { facts.add("type:" + opcode + ":" + owner); }
                        @Override public void visitLdcInsn(Object value) { facts.add("ldc:" + value); }
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            facts.add("call:" + owner + "." + name);
                            facts.add("descriptor:" + descriptor);
                        }
                        @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            facts.add("field:" + opcode + ":" + owner + "." + name);
                            facts.add("descriptor:" + descriptor);
                        }
                        @Override public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            return annotationFacts(facts);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return facts;
    }

    private static void settingsModuleContract() throws Exception {
        String root = "com/example/mekanismmagic/";
        var tab = bytecode(root + "client/gui/GuiMachineSettingsTab");
        var window = bytecode(root + "client/gui/GuiMachineSettingsWindow");
        var sections = bytecode(root + "client/gui/MachineSettingsSections");
        var theme = bytecode(root + "client/gui/MachineSettingsSections$ThemeToggle");
        var ae = bytecode(root + "client/gui/MachineAeSettingsSection");
        var status = bytecode(root + "client/gui/MachineAeSettingsSection$NetworkStatus");
        require(tab.contains("call:" + root + "client/gui/GuiMachineSettingsWindow.open")
                        && tab.contains("ldc:mekanism") && tab.contains("ldc:gui/configuration.png")
                        && !tab.contains("ldc:AE")
                        && tab.stream().noneMatch(fact -> fact.endsWith(".controls") || fact.endsWith(".isEnabled")
                        || fact.endsWith(".toggleTheme") || fact.endsWith(".toggleFunction")
                        || fact.endsWith(".handleInventoryButtonClick")),
                "Settings entry depends on AE state or adjusts a function instead of opening the shared module");
        try (var icon = MachineSidebarSelfTest.class.getResourceAsStream("/assets/mekanism/gui/configuration.png")) {
            require(icon != null && icon.read() >= 0, "Unified settings references a missing native configuration glyph");
        }
        require(window.contains("type:193:" + root + "client/gui/GuiMachineSettingsWindow")
                        && window.contains("call:mekanism/common/lib/collection/LRU.moveUp")
                        && window.contains("call:" + root + "client/gui/GuiMachineSettingsWindow.selectSection")
                        && window.contains("call:" + root + "client/gui/MachineSettingsSections$Section.build")
                        && window.stream().noneMatch(fact -> fact.contains("GuiAeConfigurationWindow")
                        || fact.contains("GuiThemeToggleTab")),
                "Settings lost its single-window LRU/page route or revived a separate adjustment window");
        require(sections.contains("type:187:" + root + "client/gui/MachineSettingsSections$ThemeSection")
                        && sections.contains("type:187:" + root + "client/gui/MachineAeSettingsSection")
                        && theme.contains("call:" + root + "config/MagicClientConfig.toggleTheme")
                        && theme.stream().noneMatch(fact -> fact.endsWith(".handleInventoryButtonClick"))
                        && ae.contains("call:net/minecraft/client/multiplayer/MultiPlayerGameMode.handleInventoryButtonClick")
                        && ae.stream().noneMatch(fact -> fact.endsWith(".toggleFunction") || fact.endsWith(".toggleSupply")),
                "Theme is no longer client-only or AE lost the authenticated server menu route");
        require(status.contains("call:" + root + "client/gui/MachineAeTooltips.tooltip")
                        && status.contains("call:" + root + "integration/common/network/MachineAeResourceHooks.snapshot"),
                "Unified AE page lost its actual network/resource transfer tooltip");
        compactSettingsInteraction(root);
        for (String type : List.of("client/gui/GuiMachineSettingsTab", "client/gui/GuiMachineSettingsWindow",
                "client/gui/GuiMachineSettingsWindow$NavButton", "client/gui/MachineSettingsSections",
                "client/gui/MachineSettingsSections$Section", "client/gui/MachineSettingsSections$Context",
                "client/gui/MachineSettingsSections$ThemeSection",
                "client/gui/MachineSettingsSections$ThemeToggle", "client/gui/MachineAeSettingsSection$NetworkStatus",
                "client/gui/MachineAeSettingsSection", "client/gui/MachineAeSettingsSection$Toggle",
                "mixin/client/GuiMachineSettingsMixin")) {
            require(bytecode(root + type).stream().noneMatch(fact -> fact.contains("appeng/")
                            || fact.contains("de/ellpeck/") || fact.contains("integration/naturesaura/")),
                    "Shared settings directly linked an optional mod API: " + type);
        }

        var defaults = MachineSettingsSections.registered();
        require(defaults.size() >= 2 && defaults.get(0).id().equals("theme")
                        && defaults.get(1).id().equals("ae")
                        && !defaults.get(1).supports(null)
                        && MachineSettingsSections.supported(null).isEmpty(),
                "Default settings order/support filtering changed or absent AE became an applicable section");
        var themeOnly = List.of(defaults.get(0));
        var withAe = List.of(defaults.get(0), defaults.get(1));
        require(GuiMachineSettingsWindow.retainedSectionId(themeOnly, "theme").equals("theme")
                        && GuiMachineSettingsWindow.retainedSectionId(withAe, "theme").equals("theme")
                        && GuiMachineSettingsWindow.retainedSectionId(withAe, "ae").equals("ae")
                        && GuiMachineSettingsWindow.retainedSectionId(themeOnly, "ae").equals("theme")
                        && GuiMachineSettingsWindow.retainedSectionId(withAe, "unregistered").equals("theme")
                        && GuiMachineSettingsWindow.retainedSectionId(List.of(), "ae") == null,
                "Late AE support/removal lost an existing page or retained an unsupported page");
        require(!java.lang.reflect.Modifier.isFinal(GuiMachineSettingsWindow.class.getDeclaredField("sections").getModifiers()),
                "Settings pages remain an immutable opening-time snapshot");
        verifyLiveSettingsRefresh(root);
        boolean immutable = false;
        try { defaults.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        require(immutable, "Registered sections expose mutable shared registry state");
        require(GuiMachineSettingsWindow.WIDTH == 176 && GuiMachineSettingsWindow.BODY_Y == 42
                        && GuiMachineSettingsWindow.INSET == 7 && defaults.getFirst().preferredHeight(null) == 22,
                "Shared settings regressed to an oversized window or appearance page");
        AtomicInteger preferred = new AtomicInteger(22);
        var extension = settingsFixture("qa-layout", preferred);
        MachineSettingsSections.register(extension);
        MachineSettingsSections.register(extension);
        require(MachineSettingsSections.registered().stream().filter(section -> section.id().equals("qa-layout")).count() == 1
                        && MachineSettingsSections.supported(null).equals(List.of(extension)),
                "Future section registration duplicates itself or bypasses explicit supports filtering");
        boolean duplicate = false, blank = false;
        try { MachineSettingsSections.register(settingsFixture("qa-layout", preferred)); }
        catch (IllegalArgumentException expected) { duplicate = true; }
        try { MachineSettingsSections.register(settingsFixture(" ", preferred)); }
        catch (IllegalArgumentException expected) { blank = true; }
        require(duplicate && blank, "Settings registry accepted a duplicate/unstable section ID");
        var lateExtension = settingsFixture("qa-late", new AtomicInteger(0));
        var beforeRegistration = MachineSettingsSections.supported(null);
        MachineSettingsSections.register(lateExtension);
        var afterRegistration = MachineSettingsSections.supported(null);
        require(!beforeRegistration.contains(lateExtension) && afterRegistration.contains(lateExtension)
                        && GuiMachineSettingsWindow.retainedSectionId(afterRegistration, "qa-layout").equals("qa-layout")
                        && GuiMachineSettingsWindow.retainedSectionId(afterRegistration, "qa-late").equals("qa-late"),
                "A future section registered after opening cannot be discovered without replacing the selected page");
        preferred.set(200);
        require(GuiMachineSettingsWindow.windowHeight(null, 333, "qa-layout") == 247
                        && GuiMachineSettingsWindow.windowHeight(null, 333, "qa-late") == 67
                        && GuiMachineSettingsWindow.windowHeight(null, 333) == 247,
                "Settings retains its tallest section's blank body after selecting a compact page");
        preferred.set(22);

        var unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var shell = (GuiMachineSettingsWindow) ((sun.misc.Unsafe) unsafeField.get(null))
                .allocateInstance(GuiMachineSettingsWindow.class);
        var supportedField = GuiMachineSettingsWindow.class.getDeclaredField("sections");
        supportedField.setAccessible(true);
        supportedField.set(shell, List.of(defaults.getFirst()));
        var selectedField = GuiMachineSettingsWindow.class.getDeclaredField("selectedSectionId");
        selectedField.setAccessible(true);
        selectedField.set(shell, "theme");
        for (String unsupported : new String[]{null, "", "ae", "unregistered", "../unknown"}) {
            require(!shell.selectSection(unsupported) && shell.selectedSectionId().equals("theme"),
                    "Unsupported settings page changed the existing page/window: " + unsupported);
        }
        require(shell.selectSection("theme") && shell.selectedSectionId().equals("theme"),
                "Selecting the current supported section rebuilt or rejected the same page");

        for (int viewport : new int[]{166, 200, 240, 333, 500, 720}) {
            for (int extent : new int[]{102, 154, 214, 236}) {
                if (extent > viewport - 12) continue;
                for (int origin : new int[]{-40, 0, 10, 82, 199}) {
                    for (int previous : new int[]{-1000, -26, 0, 18, 1000}) {
                        int clamped = GuiMachineSettingsWindow.clampedRelativePosition(previous, origin, extent, viewport);
                        require(origin + clamped >= 6 && origin + clamped + extent <= viewport - 6
                                        && GuiMachineSettingsWindow.clampedRelativePosition(clamped, origin, extent, viewport) == clamped,
                                "Dynamic settings resizing escaped the viewport or drifted across repeat refresh");
                        if (origin + previous >= 6 && origin + previous + extent <= viewport - 6) {
                            require(clamped == previous, "Refreshing settings moved an already-valid user window position");
                        }
                    }
                }
            }
        }

        for (int inputs = 0; inputs <= MachineAeResourceHooks.ALL_INPUT_MASK; inputs++) {
            for (int outputs = 0; outputs <= MachineAeResourceHooks.ALL_OUTPUT_MASK; outputs += 2) {
                int rows = Math.max(Integer.bitCount(inputs), Integer.bitCount(outputs));
                int content = MachineAeSettingsSection.preferredHeight(inputs, outputs);
                require(content == 30 + 16 * rows, "Compact AE page preferred height drifted from its actual rows");
                preferred.set(content);
                for (int viewport : new int[]{166, 180, 200, 240, 333, 500, 720}) {
                    int height = GuiMachineSettingsWindow.windowHeight(null, viewport);
                    require(height == Math.min(47 + Math.max(20, content), viewport - 12)
                                    && height <= viewport - 12 && height >= 67,
                            "Settings frame escaped a supported GUI viewport");
                    int body = height - GuiMachineSettingsWindow.BODY_Y - 5;
                    boolean paged = content > body;
                    int perPage = Math.max(1, (body - 30 - (paged ? 16 : 0)) / 16);
                    int pages = Math.max(1, Math.ceilDiv(rows, perPage));
                    for (int page = 0; page < pages; page++) {
                        int pageRows = Math.min(perPage, rows - page * perPage);
                        int bottom = pageRows == 0 ? 27 : 30 + (pageRows - 1) * 16 + 14;
                        require(bottom <= body - (paged ? 16 : 0)
                                        && (!paged || body - 14 >= bottom),
                                "A clipped AE page overlaps its pagination footer or escapes the shared window");
                    }
                }
            }
        }
    }

    private static void compactSettingsInteraction(String root) throws Exception {
        var calls = new java.util.HashSet<String>();
        var fields = new java.util.HashSet<String>();
        try (var in = MachineSidebarSelfTest.class.getResourceAsStream("/" + root + "client/gui/GuiMachineSettingsWindow.class")) {
            require(in != null, "Missing shared settings window");
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    if (!name.equals("<init>") && !name.equals("mouseClicked")) return null;
                    String scope = name;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            fields.add(scope + ":" + opcode + ":" + name);
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            calls.add(scope + ":" + owner + "." + name);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        require(fields.contains("<init>:" + Opcodes.GETSTATIC + ":ALL")
                        && fields.contains("<init>:" + Opcodes.PUTFIELD + ":interactionStrategy")
                        && calls.contains("mouseClicked:mekanism/client/gui/element/window/GuiWindow.mouseClicked")
                        && calls.stream().anyMatch(call -> call.startsWith("mouseClicked:") && call.endsWith(".isMouseOver")),
                "Settings must retain native outside-click dispatch and consume both buttons within its own bounds");
        var theme = bytecode(root + "client/gui/MachineSettingsSections$ThemeToggle");
        require(theme.stream().anyMatch(fact -> fact.endsWith(".setTooltip"))
                        && theme.contains("ldc:gui.mekanism_magic.settings.theme.value"),
                "Compact appearance setting lost its short value label or explanatory tooltip");
    }

    private static void verifyLiveSettingsRefresh(String root) throws Exception {
        var tick = new java.util.ArrayList<String>();
        var refresh = new java.util.HashSet<String>();
        var writes = new java.util.HashSet<String>();
        try (var in = MachineSidebarSelfTest.class.getResourceAsStream("/" + root + "client/gui/GuiMachineSettingsWindow.class")) {
            require(in != null, "Missing shared settings-window class");
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    if (!name.equals("tick") && !name.equals("refreshSections")) return null;
                    boolean ticking = name.equals("tick");
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            if (ticking) tick.add(owner + "." + name);
                            else refresh.add(owner + "." + name);
                        }
                        @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            if (!ticking && opcode == Opcodes.PUTFIELD) writes.add(name);
                        }
                        @Override public void visitTypeInsn(int opcode, String type) {
                            if (!ticking && opcode == Opcodes.NEW) refresh.add("new:" + type);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        require(tick.size() == 2 && tick.getFirst().endsWith("GuiMachineSettingsWindow.refreshSections")
                        && tick.getLast().endsWith(".tick"),
                "Supported settings refresh must finish before native child tick iteration");
        require(refresh.contains(root + "client/gui/MachineSettingsSections.supported")
                        && refresh.contains(root + "client/gui/GuiMachineSettingsWindow.retainedSectionId")
                        && refresh.contains(root + "client/gui/GuiMachineSettingsWindow.rebuildPage")
                        && refresh.stream().anyMatch(call -> call.endsWith(".setHeight"))
                        && refresh.stream().anyMatch(call -> call.endsWith(".move"))
                        && writes.containsAll(List.of("sections", "preferredSectionHeights", "selectedSectionId")),
                "Live settings support/height changes do not rebuild and re-clamp the same native window");
        require(!writes.contains("sectionPages") && refresh.stream().noneMatch(call -> call.endsWith(".clear")
                        || call.endsWith(".addWindow") || call.startsWith("new:")),
                "Refreshing support recreates windows or clears retained per-section page choices");
    }

    private static MachineSettingsSections.Section settingsFixture(String id, AtomicInteger preferred) {
        return new MachineSettingsSections.Section() {
            @Override public String id() { return id; }
            @Override public net.minecraft.network.chat.Component title() {
                return net.minecraft.network.chat.Component.literal(id);
            }
            @Override public boolean supports(TileEntityMekanism tile) { return tile == null; }
            @Override public int preferredHeight(TileEntityMekanism tile) { return preferred.get(); }
            @Override public void build(MachineSettingsSections.Context context) {
                throw new AssertionError("Pure registry/geometry fixture must never construct a live GUI");
            }
        };
    }

    private static org.objectweb.asm.AnnotationVisitor annotationFacts(java.util.Set<String> facts) {
        return new org.objectweb.asm.AnnotationVisitor(Opcodes.ASM9) {
            @Override public void visit(String name, Object value) {
                if ("value".equals(name)) facts.add("at:" + value);
            }
            @Override public org.objectweb.asm.AnnotationVisitor visitAnnotation(String name, String descriptor) { return annotationFacts(facts); }
            @Override public org.objectweb.asm.AnnotationVisitor visitArray(String name) { return annotationFacts(facts); }
        };
    }

    private static void checkNaturePanelGeometry(int imageWidth) {
        var compact = NatureCatalystPanelLayout.of(imageWidth, 0, 0, 1);
        var retained = RetainedModuleLayout.of(imageWidth, 2, false);
        require(compact.x() == imageWidth + DockedPanelLayout.GAP && DockedPanelLayout.GAP == 31
                        && compact.y() == 88
                        && compact.width() == 62
                        && compact.height() == 42
                        && compact.x() == retained.panelX()
                        && compact.y() == retained.panelY()
                        && compact.width() == retained.width()
                        && compact.height() == retained.height()
                        && compact.baseSlotX(0) == retained.slotX(0)
                        && compact.baseSlotX(1) == retained.slotX(1)
                        && compact.baseY() == retained.slotY(0)
                        && !compact.overflow()
                        && !compact.navigation()
                        && compact.footerY() < 0,
                "Default Nature catalyst sidebar is not the compact two-slot panel");
        checkBaseSlots(compact);
        for (int count : new int[]{1, 4, 5, 16, 17, 32, 33}) {
            int pages = Math.ceilDiv(count, CatalystLibraryLayout.PAGE_SIZE);
            for (int page = 0; page < pages; page++) {
                var expanded = NatureCatalystPanelLayout.of(
                        imageWidth, count, page, pages);
                int slotsOnPage = Math.min(CatalystLibraryLayout.PAGE_SIZE,
                        count - page * CatalystLibraryLayout.PAGE_SIZE);
                require(expanded.x() == imageWidth + DockedPanelLayout.GAP
                                && expanded.y() == 44
                                && expanded.width() == 98
                                && expanded.overflow()
                                && expanded.navigation() == (pages > 1),
                        "Nature overflow panel does not follow the loaded catalyst catalog");
                checkBaseSlots(expanded);
                require(expanded.baseY() + 17 < CatalystLibraryLayout.SLOT_TOP,
                        "Stable catalysts overlap the paged catalyst grid");
                for (int index = 0; index < slotsOnPage; index++) {
                    int x = CatalystLibraryLayout.slotX(imageWidth, index);
                    int y = CatalystLibraryLayout.slotY(index);
                    require(contains(expanded, x - 1, y - 1)
                                    && contains(expanded, x + 16, y + 16)
                                    && CatalystLibraryLayout.pageSlotAt(
                                    imageWidth, x - 1, y - 1) == index
                                    && CatalystLibraryLayout.pageSlotAt(
                                    imageWidth, x + 16, y + 16) == index,
                            "Nature catalyst page slot border missed its visible frame or hitbox: "
                                    + index);
                }
                if (expanded.navigation()) {
                    int footer = expanded.footerY();
                    require(contains(expanded, expanded.x() + 4, footer)
                                    && contains(expanded, expanded.x() + 31, footer + 17)
                                    && contains(expanded, expanded.x() + 66, footer)
                                    && contains(expanded, expanded.x() + 93, footer + 17),
                            "Nature catalyst page buttons do not fit the dynamic footer");
                }
            }
        }
    }

    private static void checkBaseSlots(NatureCatalystPanelLayout layout) {
        for (int index = 0; index < 2; index++) {
            int x = layout.baseSlotX(index);
            int y = layout.baseY();
            require(contains(layout, x - 1, y - 1)
                            && contains(layout, x + 16, y + 16),
                    "Stable Nature catalyst slot escaped the retained panel");
        }
        require(layout.baseSlotX(1) - layout.baseSlotX(0) == 18,
                "Nature retained catalyst slots lost normal slot spacing");
    }

    private static boolean contains(NatureCatalystPanelLayout panel,
                                    int x, int y) {
        return x >= panel.x() && x < panel.x() + panel.width()
                && y >= panel.y() && y < panel.y() + panel.height();
    }

    private static void dependencyContract() throws Exception {
        boolean[] gated = {false}, moves = {false};
        try (var in = MachineSidebarSelfTest.class.getResourceAsStream("/com/example/mekanismmagic/client/MachineSidebarEvents.class")) {
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitTypeInsn(int opcode, String type) {
                            if (name.equals("onScreenInit") && opcode == Opcodes.INSTANCEOF && type.endsWith("/MagicThemedScreen")) gated[0] = true;
                        }
                        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                            if (owner.endsWith("/GuiRedstoneControlTab") && name.equals("move")) moves[0] = true;
                        }
                    };
                }
            }, 0);
        }
        require(gated[0] && moves[0], "Layout is not scoped to our screens or stopped moving real widgets");
        var subscriber = MachineSidebarEvents.class.getAnnotation(net.neoforged.fml.common.EventBusSubscriber.class);
        require(subscriber != null && subscriber.modid().equals("mekanism_magic")
                && List.of(subscriber.value()).equals(List.of(net.neoforged.api.distmarker.Dist.CLIENT)), "Client subscriber registration is missing");
        require(MachineSidebarEvents.class.getMethod("onScreenInit", net.neoforged.neoforge.client.event.ScreenEvent.Init.Post.class)
                .isAnnotationPresent(net.neoforged.bus.api.SubscribeEvent.class), "Post-init hook is not subscribed");
        // The shipped ME overlay draws outside screen.children(); retain its fixed native row.
        if (!java.nio.file.Files.isRegularFile(java.nio.file.Path.of("libs/mekenergistics-3.0.6.jar"))) return;
        try (var archive = new java.util.jar.JarFile("libs/mekenergistics-3.0.6.jar")) {
            var entry = archive.getJarEntry("com/beipuo/mekenergistics/client/overlay/MePatternWindowOverlay.class");
            boolean[] nativeRow = {false};
            new ClassReader(archive.getInputStream(entry)).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return !name.equals("buttonBounds") ? null : new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitIntInsn(int opcode, int operand) {
                            if (operand == MachineSidebarLayout.PATTERN_TAB_Y) nativeRow[0] = true;
                        }
                    };
                }
            }, 0);
            require(nativeRow[0], "ME pattern anchor changed; sidebar reservation must be revisited");
        }
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
