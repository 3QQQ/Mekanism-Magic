package com.example.mekanismmagic.client;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Test-JVM equivalent of the supplied Mekanism access transformers used by readouts and multiline tooltips.
 * A plain JavaExec has no NeoForge access-transformer service. This is never in the mod JAR.
 */
public final class MachinePanelAccessAgent {
    private MachinePanelAccessAgent() { }
    public static void premain(String args, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefined,
                                               ProtectionDomain domain, byte[] bytes) {
                boolean widget = name.equals("net/minecraft/client/gui/components/AbstractWidget");
                boolean tooltip = name.equals("net/minecraft/client/gui/components/Tooltip");
                boolean screen = name.equals("net/minecraft/client/gui/screens/Screen");
                if (!widget && !tooltip && !screen) return null;
                ClassWriter writer = new ClassWriter(0);
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                        if (widget && method.equals("render") && descriptor.equals("(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")) access &= ~Opcodes.ACC_FINAL;
                        // Mekanism 10.7.19.85 META-INF/accesstransformer.cfg:
                        // public-f Screen renderWithTooltip(GuiGraphics;IIF)V
                        // Loading the real GuiMekanism type for settings-window
                        // reflection needs the same inherited-method contract.
                        if (screen && method.equals("renderWithTooltip")
                                && descriptor.equals("(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")) {
                            access = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED | Opcodes.ACC_FINAL))
                                    | Opcodes.ACC_PUBLIC;
                        }
                        if (tooltip && method.equals("<init>") && descriptor.equals("(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;)V")) {
                            access = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PUBLIC)) | Opcodes.ACC_PROTECTED;
                        }
                        return super.visitMethod(access, method, descriptor, signature, exceptions);
                    }
                    @Override public FieldVisitor visitField(int access, String field, String descriptor, String signature, Object value) {
                        if (tooltip && field.equals("cachedTooltip")) access = (access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PUBLIC)) | Opcodes.ACC_PROTECTED;
                        return super.visitField(access, field, descriptor, signature, value);
                    }
                }, 0);
                return writer.toByteArray();
            }
        });
    }
}
