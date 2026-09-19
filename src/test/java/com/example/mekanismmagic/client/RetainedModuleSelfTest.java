package com.example.mekanismmagic.client;

import com.example.mekanismmagic.container.RetainedModuleLayout;
import com.example.mekanismmagic.container.DockedPanelLayout;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure geometry and compiled client wiring; actual transformed slots are exercised separately by GameTests. */
public final class RetainedModuleSelfTest {
    private static final String ROOT = "com/example/mekanismmagic/";
    private static final String MODULE = ROOT + "client/gui/RetainedItemModule";
    private static final Path CLASSES = Path.of("build/classes/java/main");
    private RetainedModuleSelfTest() { }

    public static void main(String[] args) throws IOException {
        for (int width = 176; width <= 608; width += 2) {
            for (int count = 1; count <= 16; count++) {
                for (boolean dictionary : new boolean[]{false, true}) {
                    var layout = RetainedModuleLayout.of(width, count, dictionary);
                    require(layout.panelX() >= width + 26 && layout.tabY() >= 90, "Module overlaps machine/pattern tab");
                    require(!dictionary || layout.tabY() >= 106, "Module tab overlaps dictionary tab");
                    require(layout.height() <= 96 && layout.width() <= 80, "Module grew beyond its compact grid");
                    centeredGrid(layout.panel(), count);
                    for (int slot = 0; slot < count; slot++) {
                        int x = layout.slotX(slot), y = layout.slotY(slot);
                        require(layout.contains(x - 1, y - 1) && layout.contains(x + 16, y + 16), "Slot border outside sidebar");
                        require(y - 1 >= layout.panelY() + 14, "Slot overlaps caption");
                        for (int other = 0; other < slot; other++) require(Math.abs(x - layout.slotX(other)) >= 18
                                || Math.abs(y - layout.slotY(other)) >= 18, "Retained slot borders overlap");
                    }
                    require(!layout.contains(layout.panelX() - 1, layout.panelY())
                            && !layout.contains(layout.panelX() + layout.width(), layout.panelY())
                            && !layout.contains(layout.panelX(), layout.panelY() + layout.height()), "Outside-click boundary is inclusive");
                }
            }
            int x = CatalystLibraryLayout.activeSlotX(width), y = CatalystLibraryLayout.ACTIVE_SLOT_Y;
            var library = CatalystLibraryLayout.panel(width);
            uniformFrame(library);
            require(x - 1 >= CatalystLibraryLayout.panelLeft(width) && x + 17 <= CatalystLibraryLayout.panelLeft(width) + CatalystLibraryLayout.PANEL_WIDTH,
                    "Catalyst selector outside frame");
            require(y - 1 >= CatalystLibraryLayout.FRAME_TOP && y + 17 < CatalystLibraryLayout.SLOT_TOP - 1,
                    "Catalyst selector overlaps first library row");
            require(CatalystLibraryLayout.containsActiveSlot(width, x, y)
                    && !CatalystLibraryLayout.containsActiveSlot(width, x + 17, y), "Selector right-click hitbox differs from visual border");
            require(y - 1 - library.y() == 4 && library.y() + library.headerHeight() - (y + 17) == 4
                    && library.x() + library.width() - (x + 17) == 4, "Active selector has uneven header margins");
            require(CatalystLibraryLayout.slotLeft(width) - 1 - library.x()
                    == library.x() + library.width() - (CatalystLibraryLayout.slotX(width, 3) + 17), "Catalyst page grid is not centered");
            require(CatalystLibraryLayout.SLOT_TOP - 1 - library.y() - library.headerHeight() == 4
                    && CatalystLibraryLayout.FOOTER_TOP - (CatalystLibraryLayout.slotY(15) + 17) == 4
                    && library.y() + library.height() - (CatalystLibraryLayout.FOOTER_TOP + 18) == 4, "Catalyst page/footer margins differ");
            for (int slot = 0; slot < 16; slot++) {
                int slotX = CatalystLibraryLayout.slotX(width, slot), slotY = CatalystLibraryLayout.slotY(slot);
                for (int dx : new int[]{-1, 0, 15, 16}) for (int dy : new int[]{-1, 0, 15, 16}) {
                    require(CatalystLibraryLayout.pageSlotAt(width, slotX + dx, slotY + dy) == slot, "Page hitbox excludes or targets the wrong slot border");
                }
            }
            require(CatalystLibraryLayout.pageSlotAt(width, CatalystLibraryLayout.slotLeft(width) - 1.1, CatalystLibraryLayout.SLOT_TOP) == -1,
                    "Fractional click outside the grid was truncated into its first slot");
        }
        require(CatalystLibraryLayout.FRAME_TOP + CatalystLibraryLayout.FRAME_HEIGHT
                == CatalystLibraryLayout.PANEL_TOP + CatalystLibraryLayout.PANEL_HEIGHT, "Selector enlarged the library below the old bottom edge");
        centeredGrid(DockedPanelLayout.CHALK, 16);
        centeredGrid(DockedPanelLayout.DICTIONARY, 1);
        require(DockedPanelLayout.CHALK.x() == DockedPanelLayout.DICTIONARY.x()
                && DockedPanelLayout.CHALK.x() == CatalystLibraryLayout.panelLeft(210), "Legacy sidebars use a different docking gap");
        for (int invalid : new int[]{0, 17}) {
            boolean rejected = false;
            try { RetainedModuleLayout.of(176, invalid, false); } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "Invalid retained slot count was accepted");
        }
        clientWiring();
        System.out.println("RetainedModuleSelfTest: 6,944 centered grids, equal 2px rims, chalk/dictionary margins, 55,552 library border hit-tests and all sidebar GUI routes passed");
    }

    private static void uniformFrame(DockedPanelLayout panel) {
        var interior = panel.interior();
        require(interior.x() - panel.x() == 2 && interior.y() - panel.y() == 2
                && panel.x() + panel.width() - interior.right() == 2
                && panel.y() + panel.height() - interior.bottom() == 2, "Sidebar rim thickness differs by side");
        require(panel.header().x() == interior.x() && panel.header().right() == interior.right()
                && panel.header().y() == interior.y() && panel.header().bottom() == panel.body().y()
                && panel.body().x() == interior.x() && panel.body().right() == interior.right()
                && panel.body().bottom() == interior.bottom(), "Actual header/body rectangles expose an uneven frame");
    }

    private static void centeredGrid(DockedPanelLayout panel, int count) {
        uniformFrame(panel);
        int columns = Math.min(4, count);
        for (int row = 0; row < Math.ceilDiv(count, columns); row++) {
            int first = row * columns, last = Math.min(count, first + columns) - 1;
            int leftMargin = panel.slotX(first, count) - 1 - panel.x();
            int rightMargin = panel.x() + panel.width() - (panel.slotX(last, count) + 17);
            require(leftMargin == rightMargin && leftMargin >= 4, "Single/partial/full slot row has asymmetric margins");
        }
        require(panel.slotY(0, count) - 1 - (panel.y() + panel.headerHeight()) == 4
                && panel.y() + panel.height() - (panel.slotY(count - 1, count) + 17) == 4, "Grid top/bottom padding differs");
    }

    private static void clientWiring() throws IOException {
        for (String screen : List.of("client/screen/NativeMagicMachineScreen", "client/screen/NativeSpiritFactoryScreen",
                "integration/mekextras/client/ExtraSpiritFactoryScreen")) {
            Path file = CLASSES.resolve(ROOT + screen + ".class");
            if (!Files.exists(file) && screen.contains("mekextras")) continue;
            var methods = calls(file);
            var setup = methods.get("addGuiElements");
            int prepare = index(setup, MODULE + ".prepare");
            int inherited = index(setup, ".addGuiElements");
            require(prepare >= 0 && inherited > prepare, "Sidebar must reposition original slots BEFORE Mekanism builds visual overlays: " + screen);
            for (var entry : Map.of("addSlots", ".captureOverlays", "renderBg", ".renderBackground",
                    "drawForegroundText", ".renderCaption", "hasClickedOutside", ".contains", "removed", ".dispose").entrySet()) {
                require(index(methods.get(entry.getKey()), MODULE + entry.getValue()) >= 0, "Missing retained hook " + entry + " on " + screen);
            }
        }
        var moduleCalls = calls(CLASSES.resolve(MODULE + ".class"));
        require(moduleCalls.values().stream().flatMap(List::stream).anyMatch(call -> call.contains(".getInventorySlot")), "Module stopped matching backing inventory identity");
        require(index(moduleCalls.get("contains"), ".contains") >= 0, "Outside-click guard disappeared");
        for (var method : moduleCalls.values()) for (String call : method) require(!call.contains(".setStack(")
                && !call.contains(".insertItem(") && !call.contains(".extractItem("), "Client presentation mutates item storage");
        for (String screen : List.of("integration/arsnouveau/client/ImbuementProcessorScreen",
                "integration/arsnouveau/client/ImbuementFactoryScreen", "integration/mekextras/client/ExtraImbuementFactoryScreen")) {
            Path file = CLASSES.resolve(ROOT + screen + ".class");
            if (!Files.exists(file) && screen.contains("mekextras")) continue;
            var methods = calls(file);
            require(methods.values().stream().flatMap(List::stream).anyMatch(call -> call.contains(".activeSlotX")), "Ghost selector stayed in the main frame: " + screen);
            require(index(methods.get("mouseClicked"), ".containsActiveSlot") >= 0, "Ghost selector right-click uses old coordinates: " + screen);
            require(index(methods.get("mouseClicked"), ".pageSlotAt") >= 0, "Library clicks do not use the shared full-border hitbox: " + screen);
            require(methods.values().stream().flatMap(List::stream).anyMatch(call -> call.contains(".updateMachineSlot") && call.contains(";Z)Z")),
                    "Ghost selector visibility ignores collapsed library: " + screen);
            int guardedGhostMethods = 0;
            try (var siblings = Files.list(file.getParent())) {
                String prefix = file.getFileName().toString().replace(".class", "$");
                for (Path sibling : siblings.filter(path -> path.getFileName().toString().startsWith(prefix) && path.toString().endsWith(".class")).toList()) {
                    var inner = calls(sibling);
                    for (String method : List.of("supportedTarget", "accept")) if (index(inner.get(method), ".isPatternWindowOpen") >= 0) guardedGhostMethods++;
                }
            }
            require(guardedGhostMethods >= 2, "JEI ghost target/accept bypasses modal guard: " + screen);
        }
        var mixins = JsonParser.parseString(Files.readString(Path.of("src/main/resources/mekanism_magic.mixins.json"))).getAsJsonObject().getAsJsonArray("mixins");
        require(mixins.asList().stream().anyMatch(value -> value.getAsString().equals("RetainedSlotMixin")), "Required Slot presentation hook is not configured");
        for (String machine : List.of("NativeMiniRitualAssembler", "NativeRitualEngine")) {
            String screen = machine.equals("NativeRitualEngine") ? "NativeRitualScreen" : machine + "Screen";
            var screenCalls = calls(CLASSES.resolve(ROOT + "client/screen/" + screen + ".class"));
            require(index(screenCalls.get("hasClickedOutside"), "DockedPanelLayout.contains") >= 0,
                    "Legacy sidebar outside-click area still uses an old hardcoded rectangle");
            var tileCalls = calls(CLASSES.resolve(ROOT + "blockentity/" + machine + "BlockEntity.class"));
            require(index(tileCalls.get("createMachineSlots"), "DockedPanelLayout.slotX") >= 0
                    && index(tileCalls.get("createMachineSlots"), "DockedPanelLayout.slotY") >= 0, "Legacy real slots do not follow the shared centered geometry");
        }
        var themeCalls = calls(CLASSES.resolve(ROOT + "client/gui/MagicGuiTheme.class"));
        require(index(themeCalls.get("renderDockedPanel"), "DockedPanelLayout.header") >= 0
                && index(themeCalls.get("renderDockedPanel"), "DockedPanelLayout.body") >= 0, "Theme does not render the verified symmetric frame geometry");
    }

    private static Map<String, List<String>> calls(Path file) throws IOException {
        Map<String, List<String>> result = new HashMap<>();
        new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                List<String> calls = result.computeIfAbsent(name, ignored -> new ArrayList<>());
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                        calls.add(owner + "." + method + descriptor);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return result;
    }
    private static int index(List<String> calls, String fragment) {
        if (calls != null) for (int index = 0; index < calls.size(); index++) if (calls.get(index).contains(fragment)) return index;
        return -1;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
