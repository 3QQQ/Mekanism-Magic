package com.example.mekanismmagic.client;

import com.example.mekanismmagic.client.gui.MagicGuiTheme;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Packaged title translations and the shared header contract, without creating a client/GL context. */
public final class MachineTitlesSelfTest {
    private static final String THEMED = "com/example/mekanismmagic/client/gui/MagicThemedScreen";
    private static final String BASE = "com/example/mekanismmagic/client/screen/NativeMagicMachineScreen";
    private MachineTitlesSelfTest() { }

    public static void main(String[] args) throws IOException {
        int machines = translationsMatch();
        int routes = allHeadersUseTheSharedRenderer();
        titleNeverOverlapsStatus();
        System.out.println("MachineTitlesSelfTest: " + machines + " bilingual machine/title pairs, "
                + routes + " shared headers and long-title/status spacing passed");
    }

    private static int translationsMatch() throws IOException {
        JsonObject catalog = JsonParser.parseString(Files.readString(Path.of("config/machine-contracts.json"))).getAsJsonObject();
        int count = 0;
        Map<String, JsonObject> translations = new HashMap<>();
        for (String locale : new String[]{"zh_cn", "en_us"}) {
            String resource = "/assets/mekanism_magic/lang/" + locale + ".json";
            try (var input = MachineTitlesSelfTest.class.getResourceAsStream(resource)) {
                require(input != null, "Missing merged language resource " + resource);
                translations.put(locale, JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
            }
        }
        for (var value : catalog.getAsJsonArray("entries")) {
            var entry = value.getAsJsonObject();
            if (!entry.get("profile").getAsString().equals("machine")) continue;
            count++;
            String id = entry.get("id").getAsString();
            String block = "block.mekanism_magic." + id, title = "container.mekanism_magic." + id;
            for (var language : translations.entrySet()) {
                var names = language.getValue();
                require(names.has(block) && names.has(title), language.getKey() + " missing name/title for " + id);
                String name = names.get(block).getAsString();
                require(!name.isBlank() && name.equals(name.trim()) && name.equals(names.get(title).getAsString()),
                        language.getKey() + " block and container titles differ: " + id);
                require(!name.startsWith("block.") && !name.startsWith("container.")
                                && !Set.of("Machine", "Processor", "机器", "处理机").contains(name), "Placeholder title: " + id);
            }
        }
        require(count > 0, "Machine catalog was empty");
        return count;
    }

    private static int allHeadersUseTheSharedRenderer() throws IOException {
        Map<String, HeaderClass> classes = new HashMap<>();
        try (var files = Files.walk(Path.of("build/classes/java/main"))) {
            for (Path path : files.filter(file -> file.toString().endsWith(".class")).toList()) {
                HeaderClass info = new HeaderClass();
                new ClassReader(Files.readAllBytes(path)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                        info.name = name;
                        info.parent = parent;
                        // A marker extending the theme interface is not itself a screen.
                        info.themed = (access & Opcodes.ACC_INTERFACE) == 0
                                && java.util.Arrays.asList(interfaces).contains(THEMED);
                    }
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        boolean foreground = name.equals("drawForegroundText");
                        if (foreground) info.foreground = true;
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                                if (foreground && method.equals("drawMachineHeader")) info.sharedHeader = true;
                                if (foreground && opcode == Opcodes.INVOKESPECIAL && method.equals("drawForegroundText")) info.superHeader = true;
                                if (info.name.equals(THEMED) && name.equals("drawMachineHeader")) {
                                    info.headerCalls.add(owner + "." + method);
                                    require(!owner.equals("net/minecraft/network/chat/Component"),
                                            "Shared header replaced the supplied/player-custom title");
                                }
                            }
                            @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                if (foreground && opcode == Opcodes.GETFIELD && name.equals("title")) info.usesSuppliedTitle = true;
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                classes.put(info.name, info);
            }
        }
        int routes = 0;
        for (var info : classes.values()) {
            if (info.themed) {
                require(info.foreground && info.sharedHeader && info.usesSuppliedTitle,
                        "Themed machine screen does not use the common title/status header: " + info.name);
                routes++;
            } else if (info.foreground && inherits(info, BASE, classes)) {
                require(info.superHeader, "Concrete machine override skipped its inherited header: " + info.name);
            }
        }
        HeaderClass themed = classes.get(THEMED);
        require(themed != null && themed.headerCalls.stream().anyMatch(call -> call.endsWith(".drawScrollingString"))
                        && themed.headerCalls.contains("com/example/mekanismmagic/client/gui/MagicGuiTheme.renderStatus"),
                "Common header lost scrolling or the live status label");
        require(routes > 0, "No themed machine headers were inspected");
        return routes;
    }

    private static boolean inherits(HeaderClass info, String target, Map<String, HeaderClass> classes) {
        Set<String> seen = new HashSet<>();
        while (info != null && info.parent != null && seen.add(info.parent)) {
            if (info.parent.equals(target)) return true;
            info = classes.get(info.parent);
        }
        return false;
    }

    private static void titleNeverOverlapsStatus() {
        require(MagicGuiTheme.MACHINE_TITLE_X == 10 && MagicGuiTheme.MACHINE_TITLE_Y == 4
                        && MagicGuiTheme.MACHINE_TITLE_Y + 9 < 15, "Default title no longer fits the flat header band");
        for (int width : new int[]{176, 210, 246, 302, 442}) {
            for (int statusWidth : new int[]{0, 16, 24, 32, 48, 64}) {
                int titleWidth = MagicGuiTheme.availableTitleWidth(width, statusWidth);
                int indicatorLeft = MagicGuiTheme.statusStart(width, statusWidth) - 7;
                require(titleWidth >= 28 && MagicGuiTheme.MACHINE_TITLE_X + titleWidth <= indicatorLeft - 4,
                        "Long title touches the status marker at width " + width + " / status " + statusWidth);
                require(MagicGuiTheme.statusStart(width, statusWidth) + statusWidth
                                <= width - MagicGuiTheme.HEADER_GAUGE_RESERVE
                                && width - 12 - (MagicGuiTheme.statusStart(width, statusWidth)
                                + statusWidth) >= 18,
                        "Status label enters the native power-gauge header area");
            }
        }
    }

    private static final class HeaderClass {
        String name, parent;
        boolean themed, foreground, sharedHeader, superHeader, usesSuppliedTitle;
        final Set<String> headerCalls = new HashSet<>();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
