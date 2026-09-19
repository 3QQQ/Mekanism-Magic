package com.example.mekanismmagic.client.gui;

import mekanism.common.lib.transmitter.TransmissionType;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exhaustive page combinations, ownership boundaries, and non-mutating selection. */
public final class SideConfigDefaultPageSelfTest {
    private SideConfigDefaultPageSelfTest() {
    }

    public static void main(String[] args) {
        TransmissionType[] types = TransmissionType.values();
        int checked = 0;
        for (int mask = 1; mask < (1 << types.length); mask++) {
            List<TransmissionType> pages = new ArrayList<>();
            for (int index = 0; index < types.length; index++) {
                if ((mask & (1 << index)) != 0) {
                    pages.add(types[index]);
                }
            }
            for (int order = 0; order < 2; order++) {
                List<TransmissionType> before = List.copyOf(pages);
                TransmissionType original = pages.getFirst();
                TransmissionType expected = pages.contains(TransmissionType.ITEM)
                        ? TransmissionType.ITEM : original;
                require(SideConfigDefaultPage.select(id("mekanism_magic"), pages, original) == expected,
                        "Addon window chose an unavailable/non-item opening page: " + pages);
                for (String namespace : List.of("mekanism", "mekanism_extras", "another_addon")) {
                    require(SideConfigDefaultPage.select(id(namespace), pages, original) == original,
                            "Changed another mod's default page: " + namespace);
                }
                require(SideConfigDefaultPage.select(null, pages, original) == original,
                        "Missing owner unexpectedly selected the item page");
                require(pages.equals(before), "Opening-page selection reordered shared configuration pages");
                require(SideConfigDefaultPage.select(id("mekanism_magic"), before, original) == expected,
                        "Immutable page lists are not supported");
                checked++;
                Collections.reverse(pages);
            }
        }
        require(SideConfigDefaultPage.select(id("mekanism_magic"), List.of(), TransmissionType.ENERGY)
                        == TransmissionType.ENERGY,
                "Empty page set must retain its caller's fallback");
        verifyNativeConstructorHook();
        System.out.println("Side configuration opening-page self-test passed: " + checked + " page sets/orders");
    }

    /** Inspect the actual dependency without loading Minecraft's client/window classes. */
    private static void verifyNativeConstructorHook() {
        String owner = "mekanism/client/gui/element/window/GuiSideConfiguration";
        int[] calls = {0};
        try (var input = SideConfigDefaultPageSelfTest.class.getResourceAsStream("/" + owner + ".class")) {
            require(input != null, "Native side configuration bytecode is missing");
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                  String signature, String[] exceptions) {
                    if (!"<init>".equals(name)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM9) {
                        private boolean tileAssigned;

                        @Override
                        public void visitFieldInsn(int opcode, String fieldOwner, String fieldName,
                                                    String fieldDescriptor) {
                            if (opcode == Opcodes.PUTFIELD && owner.equals(fieldOwner)
                                    && "tile".equals(fieldName)
                                    && "Lmekanism/common/tile/base/TileEntityMekanism;".equals(fieldDescriptor)) {
                                tileAssigned = true;
                            }
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String methodOwner, String methodName,
                                                     String methodDescriptor, boolean isInterface) {
                            if (owner.equals(methodOwner) && "setCurrentType".equals(methodName)
                                    && "(Lmekanism/common/lib/transmitter/TransmissionType;)V".equals(methodDescriptor)) {
                                require(tileAssigned, "Opening-page hook executes before tile initialization");
                                require(opcode == Opcodes.INVOKEVIRTUAL,
                                        "Native constructor no longer uses the expected virtual page setter");
                                calls[0]++;
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (IOException failure) {
            throw new AssertionError("Could not inspect the native opening-page hook", failure);
        }
        require(calls[0] == 1, "Native initial-page injection target changed: " + calls[0]);
        System.out.println("Native side configuration constructor hook verified");
    }

    private static ResourceLocation id(String namespace) {
        return ResourceLocation.fromNamespaceAndPath(namespace, "test_machine");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
