package com.example.mekanismmagic.client;

import com.example.mekanismmagic.client.gui.GuiCatalystLibraryTab;
import com.example.mekanismmagic.client.gui.GuiChalkModuleTab;
import com.example.mekanismmagic.client.gui.GuiDictionaryModuleTab;
import com.example.mekanismmagic.client.gui.MagicThemedScreen;
import com.example.mekanismmagic.container.MachineSidebarLayout;
import com.example.mekanismmagic.container.RetainedModuleLayout;
import mekanism.client.gui.IGuiWrapper;
import mekanism.client.gui.element.GuiElement;
import mekanism.client.gui.element.tab.GuiRedstoneControlTab;
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
            for (int height : new int[]{166, 186, 198, 208, 218, 240, 300}) {
                for (int scale = 1; scale <= 4; scale++) {
                    check(width, height, scale, false);
                    cases++;
                    if (height >= 208) { check(width, height, scale, true); cases++; }
                }
            }
        }
        screenEventScope();
        dependencyContract();
        System.out.println("MachineSidebarSelfTest: " + cases + " layouts, real tab hitboxes, repeat alignment/resize, ME row and scoped event wiring passed");
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
        }
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
        for (GuiElement module : List.of(chalk, dictionary, library)) {
            require(module.getRelativeY() * scale >= (MachineSidebarLayout.PATTERN_TAB_Y + 26 + 2) * scale,
                    "Module overlaps optional ME pattern tab");
            require(module.isMouseOver(module.getX() + 13, module.getY() + 13), "Module center is not clickable");
            require(!module.isMouseOver(module.getX() + 13, 41 + 84), "Old overlapping ME area still activates module");
            module.onClick(module.getX() + 13, module.getY() + 13, 0);
        }
        require(clicks.get() == 3, "Relayout changed module toggle actions");
        if (dictionaryNeighbour) {
            require(dictionary.getRelativeBottom() + 2 <= retainedY, "Dictionary overlaps retained module");
            require(!redstone.isMouseOver(redstone.getX() + 13, 41 + retainedY + 13), "Retained module click hits redstone");
        }
        MachineSidebarEvents.alignRedstone(gui, tabs);
        require(redstone.getRelativeY() == newY && redstone.getY() == newY + 41, "Repeat init drifted the tab");
        dimensions[1] += 28;
        MachineSidebarEvents.alignRedstone(gui, tabs);
        require(redstone.getRelativeY() == newY + 28 && redstone.getY() == newY + 69, "Resize lost rendering/hitbox synchronization");
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
