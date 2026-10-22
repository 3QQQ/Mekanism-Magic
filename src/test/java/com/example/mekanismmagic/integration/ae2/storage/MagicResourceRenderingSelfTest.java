package com.example.mekanismmagic.integration.ae2.storage;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** No Minecraft client or GL calls: visual descriptors, real transformed vertices and API routing. */
public final class MagicResourceRenderingSelfTest {
    private MagicResourceRenderingSelfTest() { }

    public static void main(String[] args) throws IOException {
        descriptorsUseNativeResourceImagery();
        monitorQuadPreservesGeometryColorLightAndPose();
        renderFailureRestoresThePose();
        actualGuiAndClientRegistrationRoutes();
        System.out.println("MagicResourceRenderingSelfTest: native Source/Aura styles, 4 monitor rotations, failure cleanup and GUI routing passed");
    }

    private static void descriptorsUseNativeResourceImagery() {
        var source = MagicResourceKeyRenderHandler.SOURCE;
        require(source.texture().equals(ResourceLocation.parse("ars_nouveau:block/mana_still"))
                && source.atlas() && !source.blending() && source.color() == -1, "Source must match ArsEng's animated atlas sprite");
        for (int nativeColor : new int[]{0x00FF00, 0xF08A35, 0xA65DE0, 0xFFFFFF, 0x80010203}) {
            var aura = MagicResourceKeyRenderHandler.aura(nativeColor);
            require(aura.texture().equals(ResourceLocation.parse("naturesaura:textures/particles/magic_round.png"))
                    && !aura.atlas() && aura.blending() && aura.color() == (0xFF000000 | (nativeColor & 0xFFFFFF)),
                    "Aura must use native energy particles and its own type's color, not a bottle");
        }
        require(close(MagicResourceKeyRenderHandler.faceSize(1), 0.95F), "Native monitor inset changed");
        for (float scale : new float[]{0, -1, 0.01F, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            require(MagicResourceKeyRenderHandler.faceSize(scale) == 0, "Invalid scale generated corrupt vertices");
        }
    }

    private static void monitorQuadPreservesGeometryColorLightAndPose() {
        for (int rotation : new int[]{0, 90, 180, 270}) {
            PoseStack poses = new PoseStack();
            poses.translate(2, 3, 4);
            poses.mulPose(Axis.YP.rotationDegrees(rotation));
            var original = poses.last();
            Matrix4f before = new Matrix4f(original.pose());
            Recorder recorder = new Recorder();
            MagicResourceKeyRenderHandler.emitFace(poses, recorder.consumer, 0.8F,
                    0.2F, 0.7F, 0.1F, 0.9F, 0xFF42A6BC, 0x00F000D0);
            require(recorder.vertices.size() == 4, "Monitor resource is not one quad");
            require(poses.last() == original && before.equals(poses.last().pose()), "Rendering leaked a pose transform");
            float[][] expected = {{-0.4F, -0.4F, 0.2F, 0.9F}, {0.4F, -0.4F, 0.7F, 0.9F},
                    {0.4F, 0.4F, 0.7F, 0.1F}, {-0.4F, 0.4F, 0.2F, 0.1F}};
            for (int index = 0; index < 4; index++) {
                Vertex vertex = recorder.vertices.get(index);
                var position = before.transformPosition(expected[index][0], expected[index][1], 0.01F, new org.joml.Vector3f());
                var normal = new org.joml.Vector3f(0, 0, 1).rotateY((float) Math.toRadians(rotation));
                require(close(vertex.x, position.x) && close(vertex.y, position.y) && close(vertex.z, position.z), "Incorrect rotated monitor position");
                require(close(vertex.u, expected[index][2]) && close(vertex.v, expected[index][3]), "Mirrored or invalid UVs");
                require(vertex.color == 0xFF42A6BC && vertex.light == 0x00F000D0 && vertex.overlay == 0x000A0000, "Lost resource tint/light/overlay");
                require(close(vertex.nx, normal.x) && close(vertex.ny, normal.y) && close(vertex.nz, normal.z), "Monitor normal did not follow block rotation");
            }
        }
    }

    private static void renderFailureRestoresThePose() {
        PoseStack poses = new PoseStack();
        var original = poses.last();
        Recorder recorder = new Recorder();
        recorder.throwAfter = 2;
        try {
            MagicResourceKeyRenderHandler.emitFace(poses, recorder.consumer, 1, 0, 1, 0, 1, -1, 0);
            throw new AssertionError("Vertex failure fixture did not execute");
        } catch (FixtureFailure expected) {
            require(poses.last() == original, "Failed monitor draw leaked a pose");
        }
        recorder.throwAfter = Integer.MAX_VALUE;
        recorder.vertices.clear();
        for (float size : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY}) {
            MagicResourceKeyRenderHandler.emitFace(poses, recorder.consumer, size, 0, 1, 0, 1, -1, 0);
        }
        require(recorder.vertices.isEmpty() && poses.last() == original, "Empty resource quad changed render state");
    }

    private static void actualGuiAndClientRegistrationRoutes() throws IOException {
        Set<String> calls = new HashSet<>();
        for (String simpleName : List.of("MagicResourceKeyRenderHandler", "MagicStorageCellClient")) {
            try (var stream = MagicResourceRenderingSelfTest.class.getResourceAsStream(simpleName + ".class")) {
                require(stream != null, "Missing renderer class");
                new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        boolean resourceBlit = name.equals("drawInGui") && descriptor.contains("$Visual;");
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                                require(!owner.startsWith("gripe/_90/arseng/"), "Fallback Source display gained a hard ArsEng dependency");
                                calls.add(owner + "." + method);
                                if (resourceBlit) {
                                    require(!owner.equals("appeng/api/stacks/AEItemKey"), "Built-in resource visual reverted to an item");
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        for (String required : List.of("appeng/client/gui/style/Blitter.sprite", "appeng/client/gui/style/Blitter.texture",
                "appeng/client/gui/style/Blitter.colorArgb", "appeng/client/gui/style/Blitter.dest", "appeng/client/gui/style/Blitter.blit",
                "net/minecraft/client/resources/model/Material.sprite", "appeng/api/client/AEKeyRendering.register",
                "com/example/mekanismmagic/integration/ae2/storage/MagicResourceKeyRenderHandler.<init>")) {
            require(calls.contains(required), "Missing live render/registration route: " + required);
        }
    }

    private static final class Recorder {
        final List<Vertex> vertices = new ArrayList<>();
        int throwAfter = Integer.MAX_VALUE;
        final VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
                new Class<?>[]{VertexConsumer.class}, (object, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(object, method, args);
                    if (method.getName().equals("addVertex")) {
                        if (vertices.size() == throwAfter) throw new FixtureFailure();
                        Vertex vertex = new Vertex(); vertex.x = (float) args[0]; vertex.y = (float) args[1]; vertex.z = (float) args[2]; vertices.add(vertex);
                    } else {
                        Vertex vertex = vertices.getLast();
                        switch (method.getName()) {
                            case "setColor" -> vertex.color = ((int) args[3] << 24) | ((int) args[0] << 16) | ((int) args[1] << 8) | (int) args[2];
                            case "setUv" -> { vertex.u = (float) args[0]; vertex.v = (float) args[1]; }
                            case "setUv1" -> vertex.overlay = (int) args[0] | ((int) args[1] << 16);
                            case "setUv2" -> vertex.light = (int) args[0] | ((int) args[1] << 16);
                            case "setNormal" -> { vertex.nx = (float) args[0]; vertex.ny = (float) args[1]; vertex.nz = (float) args[2]; }
                            default -> throw new AssertionError("Unexpected vertex method: " + method);
                        }
                    }
                    return object;
                });
    }
    private static final class Vertex { float x, y, z, u, v, nx, ny, nz; int color, light, overlay; }
    private static final class FixtureFailure extends RuntimeException { }
    private static boolean close(float actual, float expected) { return Math.abs(actual - expected) < 0.00001F; }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
