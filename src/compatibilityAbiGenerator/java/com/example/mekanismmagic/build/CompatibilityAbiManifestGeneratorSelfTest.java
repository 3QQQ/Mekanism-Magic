package com.example.mekanismmagic.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.Map;

/** The formal-release scanner has one optional provider; no implementation class is loaded. */
public final class CompatibilityAbiManifestGeneratorSelfTest {
    private static final String API = "com/stal111/forbidden_arcanus/Api";
    private static final String PARENT = "com/stal111/forbidden_arcanus/ParentApi";
    private static final String TYPED = "com/stal111/forbidden_arcanus/TypedApi";
    private static final String BOOTSTRAP = "com/stal111/forbidden_arcanus/BootstrapApi";
    private static final String CORE = "net/valhelsia/valhelsia_core/api/SyntheticApi";
    private static final String OWN_SUPPORT = "com/example/mekanismmagic/integration/forbiddenarcanus/SupportProbe";
    private static final String CLIENT = "com/stal111/forbidden_arcanus/client/ClientApi";

    private CompatibilityAbiManifestGeneratorSelfTest() { }

    public static void main(String[] args) {
        var first = new CompatibilityAbiManifestGenerator();
        first.scan(crossContextReferences()); first.scan(supportReferences("forbiddenarcanus"));
        first.scan(externalParent()); first.scan(clientReferences());
        var second = new CompatibilityAbiManifestGenerator();
        second.scan(clientReferences()); second.scan(externalParent());
        second.scan(supportReferences("forbiddenarcanus")); second.scan(crossContextReferences());
        JsonObject manifest = first.manifest(Map.of("forbidden_arcanus", "2.6.1", "valhelsia_core", "1.1.5"));
        check(manifest.toString().equals(second.manifest(Map.of("valhelsia_core", "1.1.5", "forbidden_arcanus", "2.6.1")).toString()),
                "Manifest depends on class traversal or baseline-map ordering");
        check(manifest.get("schemaVersion").getAsInt() == 1, "Unknown ABI schema");
        JsonObject groups = manifest.getAsJsonObject("integrations");
        check(groups.size() == 1 && groups.has("forbidden_arcanus"), "A post-release integration leaked into the hotfix");
        JsonObject group = groups.getAsJsonObject("forbidden_arcanus");
        check(group.getAsJsonObject("compiledAgainst").size() == 2
                && group.getAsJsonObject("compiledAgainst").get("valhelsia_core").getAsString().equals("1.1.5"), "Baseline metadata lost");
        check(member(group.getAsJsonArray("fields"), API, "mutable").get("writeExpected").getAsBoolean(), "PUTSTATIC/H_PUTSTATIC write contract lost");
        check(member(group.getAsJsonArray("fields"), API, "mutable").get("staticExpected").getAsBoolean(), "Static field contract lost");
        check(!member(group.getAsJsonArray("methods"), API, "process").get("staticExpected").getAsBoolean(), "Instance invocation treated as static");
        check(member(group.getAsJsonArray("methods"), API, "factory").get("staticExpected").getAsBoolean(), "Bootstrap method handle omitted");
        check(member(group.getAsJsonArray("methods"), BOOTSTRAP, "constant").get("staticExpected").getAsBoolean(), "ConstantDynamic bootstrap omitted");
        check(group.getAsJsonArray("classes").asList().stream().map(value -> value.getAsJsonObject()).noneMatch(value ->
                value.get("name").getAsString().endsWith("NotAnActualReference")), "Plain strings were mistaken for class linkage");
        check(group.getAsJsonArray("classes").asList().stream().map(value -> value.getAsJsonObject()).anyMatch(value ->
                value.get("name").getAsString().equals(PARENT) && value.has("extensibleExpected") && value.get("extensibleExpected").getAsBoolean()),
                "Superclass extensibility contract lost");
        check(group.getAsJsonArray("classes").asList().stream().map(value -> value.getAsJsonObject()).anyMatch(value ->
                value.get("name").getAsString().equals(PARENT) && value.has("accessRequired") && value.get("accessRequired").getAsBoolean()),
                "Explicit superclass access requirement lost");
        check(named(group.getAsJsonArray("classes"), PARENT + "$Inner").get("caller").getAsString().contains("forbiddenarcanus/"),
                "Nested generic type lost its outer owner");
        check(!named(group.getAsJsonArray("classes"), CORE).has("accessRequired"), "Descriptor-only type acquired an overstrict access requirement");
        check(member(group.getAsJsonArray("methods"), PARENT + "Interface", "consume").get("interfaceOwnerExpected").getAsBoolean(), "INVOKEINTERFACE contract lost");
        check(member(group.getAsJsonArray("methods"), OWN_SUPPORT, "inherited").get("caller").getAsString().contains("common/"),
                "Inherited native method referenced through an addon subclass was omitted");
        check(member(group.getAsJsonArray("fields"), OWN_SUPPORT, "inheritedField") != null, "Inherited native field omitted");
        check(named(group.getAsJsonArray("classes"), CLIENT).get("clientOnly").getAsBoolean(), "Client-only class marker lost");
        check(member(group.getAsJsonArray("methods"), CLIENT, "draw").get("clientOnly").getAsBoolean(), "Client-only member marker lost");

        var unrelated = new CompatibilityAbiManifestGenerator(); unrelated.scan(supportReferences("occultism"));
        check(unrelated.manifest(Map.of()).getAsJsonObject("integrations").getAsJsonObject("forbidden_arcanus").getAsJsonArray("classes").isEmpty(),
                "An unrelated integration's support symbols became mandatory for Forbidden");
        var dependency = new CompatibilityAbiManifestGenerator(); dependency.scan(externalClass());
        check(dependency.manifest(Map.of()).getAsJsonObject("integrations").getAsJsonObject("forbidden_arcanus").getAsJsonArray("classes").isEmpty(),
                "The generator scanned and redistributed third-party class references");
        var missingParent = new CompatibilityAbiManifestGenerator(); missingParent.scan(missingInheritedDependency());
        boolean missingRejected = false;
        try { missingParent.manifest(Map.of()); }
        catch (IllegalStateException expected) {
            missingRejected = expected.getMessage().contains("Missing compile dependency class bytes")
                    && expected.getMessage().contains(PARENT + "MissingCompileParent");
        }
        check(missingRejected, "Missing compiler dependency silently removed inherited requirements");
        System.out.println("CompatibilityAbiManifestGeneratorSelfTest: one Forbidden/Core provider, cross-context references, descriptors/generics, inherited members, access/static/final, client isolation, handles/constant dynamics, fail-closed and deterministic output passed");
    }

    private static byte[] crossContextReferences() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/mekanismmagic/integration/common/CrossContextProbe", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "typed", "L" + TYPED + ";", null, null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "([L" + PARENT + ";)V", null, new String[]{API + "Exception"});
        method.visitCode(); method.visitLdcInsn("com/stal111/forbidden_arcanus/NotAnActualReference"); method.visitInsn(Opcodes.POP);
        method.visitFieldInsn(Opcodes.GETSTATIC, API, "mutable", "I"); method.visitFieldInsn(Opcodes.PUTSTATIC, API, "mutable", "I");
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, OWN_SUPPORT, "inherited", "()V", false);
        method.visitFieldInsn(Opcodes.GETFIELD, OWN_SUPPORT, "inheritedField", "I"); method.visitInsn(Opcodes.POP);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, API, "process", "()V", false);
        method.visitLdcInsn(new Handle(Opcodes.H_PUTSTATIC, API, "mutable", "I", false)); method.visitInsn(Opcodes.POP);
        var bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false);
        method.visitInvokeDynamicInsn("run", "()Ljava/lang/Runnable;", bootstrap,
                Type.getMethodType("()V"), new Handle(Opcodes.H_INVOKESTATIC, API, "factory", "()V", false), Type.getMethodType("()V"));
        method.visitInsn(Opcodes.POP);
        method.visitLdcInsn(new ConstantDynamic("value", "L" + TYPED + ";",
                new Handle(Opcodes.H_INVOKESTATIC, BOOTSTRAP, "constant", "()Ljava/lang/Object;", false), Type.getType("L" + TYPED + ";")));
        method.visitInsn(Opcodes.POP); method.visitInsn(Opcodes.RETURN); method.visitMaxs(4, 2); method.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] supportReferences(String integration) {
        ClassWriter writer = new ClassWriter(0);
        boolean forbidden = integration.equals("forbiddenarcanus");
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/mekanismmagic/integration/" + integration + "/SupportProbe", null,
                forbidden ? PARENT : "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "support", "L" + CORE + ";", null, null).visitEnd();
        if (forbidden) {
            writer.visitField(Opcodes.ACC_PRIVATE, "generic", "L" + PARENT + "$Inner;", "L" + PARENT + "<L" + TYPED + ";>.Inner;", null).visitEnd();
            var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
            method.visitCode(); method.visitMethodInsn(Opcodes.INVOKEINTERFACE, PARENT + "Interface", "consume", "()V", true);
            method.visitInsn(Opcodes.RETURN); method.visitMaxs(1, 1); method.visitEnd();
        }
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] clientReferences() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/mekanismmagic/integration/forbiddenarcanus/client/ClientProbe", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PRIVATE, "client", "L" + CLIENT + ";", null, null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "render", "()V", null, null);
        method.visitCode(); method.visitMethodInsn(Opcodes.INVOKESTATIC, CLIENT, "draw", "()V", false);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 1); method.visitEnd(); writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] externalClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "thirdparty/NotOurClass", null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "unused", "L" + API + ";", null, null).visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] externalParent() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, PARENT, null, "java/lang/Object", null);
        writer.visitField(Opcodes.ACC_PUBLIC, "inheritedField", "I", null, null).visitEnd();
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "inherited", "()V", null, null);
        method.visitCode(); method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 1); method.visitEnd();
        writer.visitEnd(); return writer.toByteArray();
    }

    private static byte[] missingInheritedDependency() {
        ClassWriter writer = new ClassWriter(0);
        String owner = "com/example/mekanismmagic/integration/forbiddenarcanus/MissingParentProbe";
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, owner, null, PARENT + "MissingCompileParent", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "probe", "()V", null, null);
        method.visitCode(); method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "inheritedMissing", "()V", false);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(1, 1); method.visitEnd(); writer.visitEnd(); return writer.toByteArray();
    }

    private static JsonObject named(JsonArray values, String name) {
        return values.asList().stream().map(value -> value.getAsJsonObject()).filter(value -> value.get("name").getAsString().equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing class requirement: " + name));
    }
    private static JsonObject member(JsonArray values, String owner, String name) {
        return values.asList().stream().map(value -> value.getAsJsonObject()).filter(value -> value.get("owner").getAsString().equals(owner)
                && value.get("name").getAsString().equals(name)).findFirst().orElseThrow(() -> new AssertionError("Missing member: " + owner + "." + name));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
