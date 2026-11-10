package com.example.mekanismmagic.integration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Symbolic fixtures are generated in memory; no optional mod or Minecraft process is required. */
public final class IntegrationAbiVerifierSelfTest {
    private static int checks;
    private record Spec(String name, String descriptor, int access) { }

    private IntegrationAbiVerifierSelfTest() { }

    public static void main(String[] args) {
        var resources = baseline();
        var requirements = empty();
        addClass(requirements, "fixture/Api", false, false);
        addField(requirements, "fixture/Api", "capacity", "I", false, false, "addon/Machine");
        addMethod(requirements, "fixture/Api", "process", "(I)Ljava/lang/String;", false, false, "addon/Machine");
        pass(requirements, resources, "baseline signatures");
        // Version labels are diagnostic metadata, never part of the symbol comparison.
        var compiledAgainst = new JsonObject(); compiledAgainst.addProperty("mod", "1.0.0");
        requirements.add("compiledAgainst", compiledAgainst);
        resources.versions.put("mod", "99.0.0-beta+future");
        pass(requirements, resources, "new version with unchanged ABI");
        require(resources.classLoads == 0, "ABI checking loaded/initialized a fixture class");

        var missing = baseline(); missing.bytes.remove("fixture/Api.class");
        fail(requirements, missing, "Missing class fixture/Api");
        var changedMethod = baseline();
        changedMethod.bytes.put("fixture/Api.class", type("fixture/Api", "java/lang/Object", Opcodes.ACC_PUBLIC, List.of(),
                List.of(field()), List.of(new Spec("process", "(J)Ljava/lang/String;", Opcodes.ACC_PUBLIC))));
        fail(requirements, changedMethod, "Missing method fixture/Api.process(I)Ljava/lang/String;");
        var changedField = baseline();
        changedField.bytes.put("fixture/Api.class", type("fixture/Api", "java/lang/Object", Opcodes.ACC_PUBLIC, List.of(),
                List.of(new Spec("capacity", "J", Opcodes.ACC_PUBLIC)), List.of(method())));
        fail(requirements, changedField, "Missing field fixture/Api.capacityI");
        var staticMethod = baseline();
        staticMethod.bytes.put("fixture/Api.class", type("fixture/Api", "java/lang/Object", Opcodes.ACC_PUBLIC, List.of(),
                List.of(field()), List.of(new Spec("process", method().descriptor, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))));
        fail(requirements, staticMethod, "Static/instance kind changed");
        var staticField = baseline();
        staticField.bytes.put("fixture/Api.class", type("fixture/Api", "java/lang/Object", Opcodes.ACC_PUBLIC, List.of(),
                List.of(new Spec("capacity", "I", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)), List.of(method())));
        fail(requirements, staticField, "Static/instance kind changed");
        var privateMethod = baseline();
        privateMethod.bytes.put("fixture/Api.class", type("fixture/Api", "java/lang/Object", Opcodes.ACC_PUBLIC, List.of(),
                List.of(field()), List.of(new Spec("process", method().descriptor, Opcodes.ACC_PRIVATE))));
        fail(requirements, privateMethod, "Member is no longer accessible");
        var changedKind = baseline();
        changedKind.bytes.put("fixture/Api.class", type("fixture/Api", "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, List.of(), List.of(), List.of()));
        fail(requirements, changedKind, "Class/interface kind changed");

        inheritance();
        interfaceResolution();
        protectedMembers();
        classVisibility();
        clientIsolation();
        finalChanges();
        malformedAndEmpty();
        require(resources.classLoads == 0, "Class initialization occurred while reading the ABI");
        System.out.println("IntegrationAbiVerifierSelfTest: " + checks + " symbolic ABI checks passed; future version labels, inheritance, interfaces, access, static/final changes and no class initialization");
    }

    private static void inheritance() {
        var resources = baseline();
        resources.bytes.put("fixture/Child.class", type("fixture/Child", "fixture/Api", Opcodes.ACC_PUBLIC, List.of(), List.of(), List.of()));
        var requirements = empty(); addClass(requirements, "fixture/Child", false, true);
        addMethod(requirements, "fixture/Child", "process", method().descriptor, false, false, "addon/Machine");
        addField(requirements, "fixture/Child", "capacity", "I", false, false, "addon/Machine");
        pass(requirements, resources, "inherited methods and fields");
        var missingParent = baseline();
        missingParent.bytes.put("fixture/Child.class", resources.bytes.get("fixture/Child.class"));
        missingParent.bytes.remove("fixture/Api.class");
        fail(requirements, missingParent, "Missing superclass fixture/Api");
        addMethod(requirements, "fixture/Child", "<init>", "()V", false, false, "addon/Machine");
        fail(requirements, resources, "Missing method fixture/Child.<init>()V");
    }

    private static void interfaceResolution() {
        var resources = baseline();
        resources.bytes.put("fixture/Contract.class", type("fixture/Contract", "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, List.of(), List.of(),
                List.of(new Spec("apply", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT),
                        new Spec("create", "()V", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))));
        resources.bytes.put("fixture/SubContract.class", type("fixture/SubContract", "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT, List.of("fixture/Contract"), List.of(), List.of()));
        resources.bytes.put("fixture/Concrete.class", type("fixture/Concrete", "java/lang/Object", Opcodes.ACC_PUBLIC,
                List.of("fixture/Contract"), List.of(), List.of()));
        var requirements = empty();
        addMethod(requirements, "fixture/SubContract", "apply", "()V", false, true, "addon/Machine");
        addMethod(requirements, "fixture/Concrete", "apply", "()V", false, false, "addon/Machine");
        addMethod(requirements, "fixture/SubContract", "toString", "()Ljava/lang/String;", false, true, "addon/Machine");
        pass(requirements, resources, "inherited interface and public Object methods");
        addMethod(requirements, "fixture/SubContract", "create", "()V", true, true, "addon/Machine");
        fail(requirements, resources, "Missing method fixture/SubContract.create()V");
    }

    private static void protectedMembers() {
        var resources = baseline();
        resources.bytes.put("fixture/Api.class", type("fixture/Api", "java/lang/Object", Opcodes.ACC_PUBLIC, List.of(), List.of(),
                List.of(new Spec("work", "()V", Opcodes.ACC_PROTECTED))));
        resources.bytes.put("addon/Child.class", type("addon/Child", "fixture/Api", Opcodes.ACC_PUBLIC, List.of(), List.of(), List.of()));
        var requirements = empty(); addMethod(requirements, "fixture/Api", "work", "()V", false, false, "addon/Child");
        pass(requirements, resources, "protected inherited member");
        var inaccessible = empty(); addMethod(inaccessible, "fixture/Api", "work", "()V", false, false, "addon/Unrelated");
        fail(inaccessible, resources, "Member is no longer accessible");
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "addon/Child$Nested", null, "java/lang/Object", null);
        writer.visitNestHost("addon/Child"); writer.visitEnd();
        resources.bytes.put("addon/Child$Nested.class", writer.toByteArray());
        var nested = empty(); addMethod(nested, "fixture/Api", "work", "()V", false, false, "addon/Child$Nested");
        pass(nested, resources, "nested subclass access");
    }

    private static void finalChanges() {
        var resources = baseline();
        resources.bytes.put("fixture/Api.class", type("fixture/Api", "java/lang/Object", Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                List.of(), List.of(new Spec("capacity", "I", Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL)), List.of(method())));
        var requirements = empty(); addClass(requirements, "fixture/Api", false, true);
        fail(requirements, resources, "Superclass became final");
        var write = empty(); addField(write, "fixture/Api", "capacity", "I", false, true, "addon/Machine");
        fail(write, resources, "Written field became final");
        var read = empty(); addField(read, "fixture/Api", "capacity", "I", false, false, "addon/Machine");
        pass(read, resources, "read-only final fields remain usable");
    }

    private static void classVisibility() {
        var resources = baseline();
        resources.bytes.put("fixture/Api.class", type("fixture/Api", "java/lang/Object", 0, List.of(), List.of(field()), List.of(method())));
        var explicit = empty(); addClass(explicit, "fixture/Api", false, false);
        explicit.getAsJsonArray("classes").get(0).getAsJsonObject().addProperty("accessRequired", true);
        explicit.getAsJsonArray("classes").get(0).getAsJsonObject().addProperty("caller", "addon/Machine");
        fail(explicit, resources, "Class is no longer accessible");
        var descriptor = empty(); addClass(descriptor, "fixture/Api", false, false);
        pass(descriptor, resources, "an inaccessible return type can legally be assigned to Object");
        var member = empty(); addMethod(member, "fixture/Api", "process", method().descriptor, false, false, "addon/Machine");
        fail(member, resources, "Member owner class is no longer accessible");
        var inner = new ClassWriter(0);
        inner.visit(Opcodes.V21, 0, "addon/Owner$Private", null, "java/lang/Object", null);
        inner.visitNestHost("addon/Owner");
        inner.visitInnerClass("addon/Owner$Private", "addon/Owner", "Private", Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC);
        inner.visitEnd();
        var peer = new ClassWriter(0);
        peer.visit(Opcodes.V21, 0, "addon/Owner$Peer", null, "java/lang/Object", null);
        peer.visitNestHost("addon/Owner"); peer.visitEnd();
        resources.bytes.put("addon/Owner$Private.class", inner.toByteArray());
        resources.bytes.put("addon/Owner$Peer.class", peer.toByteArray());
        var nest = empty(); addClass(nest, "addon/Owner$Private", false, false);
        nest.getAsJsonArray("classes").get(0).getAsJsonObject().addProperty("accessRequired", true);
        nest.getAsJsonArray("classes").get(0).getAsJsonObject().addProperty("caller", "addon/Owner$Peer");
        pass(nest, resources, "private addon nesting stays accessible within its nest");
    }

    private static void clientIsolation() {
        var requirements = empty(); addClass(requirements, "fixture/client/Api", false, false);
        requirements.getAsJsonArray("classes").get(0).getAsJsonObject().addProperty("clientOnly", true);
        addMethod(requirements, "fixture/client/Api", "draw", "()V", false, false, "addon/client/Screen");
        requirements.getAsJsonArray("methods").get(0).getAsJsonObject().addProperty("clientOnly", true);
        var resources = new Resources();
        require(IntegrationAbiVerifier.inspectRequirements(requirements, resources, false).isEmpty()
                && resources.reads == 0, "Dedicated servers inspected client implementation classes"); checks++;
        fail(requirements, resources, "Missing class fixture/client/Api");
    }

    private static void malformedAndEmpty() {
        var resources = baseline();
        pass(empty(), resources, "absent integration does not inspect optional resources");
        require(resources.reads == 0, "An empty/absent target read optional implementation bytes");
        var malformed = new JsonObject();
        try { IntegrationAbiVerifier.inspectRequirements(malformed, resources); throw new AssertionError("Malformed requirements accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        resources.bytes.put("fixture/Api.class", new byte[]{0, 1, 2});
        var requirements = empty(); addClass(requirements, "fixture/Api", false, false);
        fail(requirements, resources, "Cannot read class fixture/Api");
        resources.bytes.put("fixture/Api.class", type("fixture/Different", "java/lang/Object", Opcodes.ACC_PUBLIC, List.of(), List.of(), List.of()));
        fail(requirements, resources, "Cannot read class fixture/Api");
    }

    private static Resources baseline() {
        var resources = new Resources();
        resources.bytes.put("fixture/Api.class", type("fixture/Api", "java/lang/Object", Opcodes.ACC_PUBLIC,
                List.of(), List.of(field()), List.of(method(), new Spec("<init>", "()V", Opcodes.ACC_PUBLIC))));
        resources.bytes.put("java/lang/Object.class", type("java/lang/Object", null, Opcodes.ACC_PUBLIC, List.of(), List.of(),
                List.of(new Spec("toString", "()Ljava/lang/String;", Opcodes.ACC_PUBLIC))));
        return resources;
    }
    private static Spec field() { return new Spec("capacity", "I", Opcodes.ACC_PUBLIC); }
    private static Spec method() { return new Spec("process", "(I)Ljava/lang/String;", Opcodes.ACC_PUBLIC); }
    private static byte[] type(String name, String parent, int access, List<String> interfaces, List<Spec> fields, List<Spec> methods) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V21, access, name, null, parent, interfaces.toArray(String[]::new));
        for (Spec field : fields) writer.visitField(field.access, field.name, field.descriptor, null, null).visitEnd();
        for (Spec method : methods) {
            var visitor = writer.visitMethod(method.access, method.name, method.descriptor, null, null);
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                visitor.visitCode();
                if (method.name.equals("<init>")) {
                    visitor.visitVarInsn(Opcodes.ALOAD, 0);
                    visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, parent, "<init>", "()V", false);
                    visitor.visitInsn(Opcodes.RETURN);
                } else {
                    int kind = Type.getReturnType(method.descriptor).getSort();
                    switch (kind) {
                        case Type.VOID -> visitor.visitInsn(Opcodes.RETURN);
                        case Type.OBJECT, Type.ARRAY -> { visitor.visitInsn(Opcodes.ACONST_NULL); visitor.visitInsn(Opcodes.ARETURN); }
                        case Type.LONG -> { visitor.visitInsn(Opcodes.LCONST_0); visitor.visitInsn(Opcodes.LRETURN); }
                        case Type.DOUBLE -> { visitor.visitInsn(Opcodes.DCONST_0); visitor.visitInsn(Opcodes.DRETURN); }
                        case Type.FLOAT -> { visitor.visitInsn(Opcodes.FCONST_0); visitor.visitInsn(Opcodes.FRETURN); }
                        default -> { visitor.visitInsn(Opcodes.ICONST_0); visitor.visitInsn(Opcodes.IRETURN); }
                    }
                }
                int locals = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
                for (Type argument : Type.getArgumentTypes(method.descriptor)) locals += argument.getSize();
                visitor.visitMaxs(2, locals);
            }
            visitor.visitEnd();
        }
        // Reading this bytecode is harmless; defining it would execute a deliberately throwing initializer.
        var clinit = writer.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        clinit.visitCode(); clinit.visitInsn(Opcodes.ACONST_NULL); clinit.visitInsn(Opcodes.ATHROW);
        clinit.visitMaxs(1, 0); clinit.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
    }

    private static JsonObject empty() {
        var result = new JsonObject();
        for (String collection : List.of("classes", "fields", "methods")) result.add(collection, new JsonArray());
        return result;
    }
    private static void addClass(JsonObject requirements, String name, boolean isInterface, boolean extensible) {
        var entry = new JsonObject(); entry.addProperty("name", name); entry.addProperty("interfaceExpected", isInterface);
        entry.addProperty("extensibleExpected", extensible); requirements.getAsJsonArray("classes").add(entry);
    }
    private static void addField(JsonObject requirements, String owner, String name, String descriptor,
                                 boolean isStatic, boolean write, String caller) {
        var entry = member(owner, name, descriptor, isStatic, caller); entry.addProperty("writeExpected", write);
        requirements.getAsJsonArray("fields").add(entry);
    }
    private static void addMethod(JsonObject requirements, String owner, String name, String descriptor,
                                  boolean isStatic, boolean isInterface, String caller) {
        var entry = member(owner, name, descriptor, isStatic, caller); entry.addProperty("interfaceOwnerExpected", isInterface);
        requirements.getAsJsonArray("methods").add(entry);
    }
    private static JsonObject member(String owner, String name, String descriptor, boolean isStatic, String caller) {
        var entry = new JsonObject(); entry.addProperty("owner", owner); entry.addProperty("name", name);
        entry.addProperty("descriptor", descriptor); entry.addProperty("staticExpected", isStatic); entry.addProperty("caller", caller);
        return entry;
    }
    private static void pass(JsonObject requirements, Resources resources, String scenario) {
        var failures = IntegrationAbiVerifier.inspectRequirements(requirements, resources);
        require(failures.isEmpty(), scenario + ": " + failures); checks++;
    }
    private static void fail(JsonObject requirements, Resources resources, String message) {
        var failures = IntegrationAbiVerifier.inspectRequirements(requirements, resources);
        require(failures.stream().anyMatch(value -> value.contains(message)), "Missing diagnostic " + message + ": " + failures); checks++;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class Resources extends ClassLoader implements IntegrationAbiVerifier.ResourceLookup {
        final Map<String, byte[]> bytes = new HashMap<>();
        final Map<String, String> versions = new HashMap<>();
        int reads, classLoads;
        @Override public InputStream open(String path) {
            reads++; byte[] value = bytes.get(path); return value == null ? null : new ByteArrayInputStream(value);
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            classLoads++; throw new ClassNotFoundException("Fixture classes must never be initialized: " + name);
        }
    }
}
