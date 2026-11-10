package com.example.mekanismmagic.build;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/** Build-only scanner. Only symbol names are shipped; no dependency bytecode is copied. */
public final class CompatibilityAbiManifestGenerator {
    public static final String RESOURCE = "META-INF/mekanism_magic/compatibility-abi.json";
    private static final String OWN_PREFIX = "com/example/mekanismmagic/";
    private static final List<String> INTEGRATIONS = List.of("forbidden_arcanus");
    private final Map<String, Requirements> groups = new LinkedHashMap<>();
    private final Map<String, byte[]> ownClasses = new TreeMap<>();
    private final Map<String, OwnType> ownTypes = new TreeMap<>();
    private final Map<String, OwnType> dependencyTypes = new java.util.HashMap<>();

    public CompatibilityAbiManifestGenerator() {
        for (String integration : INTEGRATIONS) groups.put(integration, new Requirements());
    }

    public static void main(String[] args) throws IOException {
        Path output = null;
        List<Path> roots = new ArrayList<>();
        Map<String, String> baseline = new TreeMap<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--output" -> output = Path.of(requireArgument(args, ++i));
                case "--classes" -> roots.add(Path.of(requireArgument(args, ++i)));
                case "--baseline" -> {
                    String[] pair = requireArgument(args, ++i).split("=", 2);
                    if (pair.length != 2 || pair[0].isBlank() || pair[1].isBlank()) throw new IllegalArgumentException("Expected mod=version baseline");
                    baseline.put(pair[0], pair[1]);
                }
                default -> throw new IllegalArgumentException("Unknown generator argument: " + args[i]);
            }
        }
        if (output == null || roots.isEmpty()) throw new IllegalArgumentException("Expected --output and at least one --classes directory");
        var generator = new CompatibilityAbiManifestGenerator();
        int classes = 0;
        for (Path root : roots) {
            if (!Files.isDirectory(root)) continue;
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                    generator.scan(Files.readAllBytes(file)); classes++;
                }
            }
        }
        if (classes == 0) throw new IllegalStateException("No compiled main classes available for ABI extraction");
        JsonObject manifest = generator.manifest(baseline);
        for (String integration : INTEGRATIONS) {
            JsonObject requirement = manifest.getAsJsonObject("integrations").getAsJsonObject(integration);
            if (requirement.getAsJsonArray("classes").isEmpty() || requirement.getAsJsonArray("methods").isEmpty()) {
                throw new IllegalStateException("ABI extraction unexpectedly omitted " + integration);
            }
        }
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(manifest) + "\n", StandardCharsets.UTF_8);
        System.out.println("Generated compatibility ABI requirements from " + classes + " addon classes: " + output);
    }

    private static String requireArgument(String[] args, int index) {
        if (index >= args.length) throw new IllegalArgumentException("Missing generator argument");
        return args[index];
    }

    public void scan(byte[] bytes) {
        ClassReader reader = new ClassReader(bytes);
        if (!reader.getClassName().startsWith(OWN_PREFIX)) {
            dependencyTypes.put(reader.getClassName(), readType(reader)); return;
        }
        ownClasses.put(reader.getClassName(), bytes.clone());
        ownTypes.put(reader.getClassName(), readType(reader));
    }

    private static OwnType readType(ClassReader reader) {
        OwnType info = new OwnType(reader.getSuperName(), List.of(reader.getInterfaces()), new HashSet<>(), new HashSet<>());
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                info.fields.add(name + descriptor); return null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                info.methods.add(name + descriptor); return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return info;
    }

    public JsonObject manifest(Map<String, String> baseline) {
        groups.values().forEach(group -> { group.classes.clear(); group.fields.clear(); group.methods.clear(); });
        // A complete own-class index is necessary for compiler references to inherited API through an addon subclass.
        ownClasses.forEach((name, bytes) -> new ClassReader(bytes).accept(new AddonClassVisitor(name), ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES));
        JsonObject manifest = new JsonObject();
        manifest.addProperty("schemaVersion", 1);
        JsonObject integrations = new JsonObject();
        groups.forEach((id, requirements) -> {
            JsonObject entry = new JsonObject();
            JsonObject compiledAgainst = new JsonObject();
            for (String dependency : switch (id) {
                case "forbidden_arcanus" -> List.of("forbidden_arcanus", "valhelsia_core");
                default -> List.of(id);
            }) if (baseline.containsKey(dependency)) compiledAgainst.addProperty(dependency, baseline.get(dependency));
            entry.add("compiledAgainst", compiledAgainst);
            entry.add("classes", array(requirements.classes));
            entry.add("fields", array(requirements.fields));
            entry.add("methods", array(requirements.methods));
            integrations.add(id, entry);
        });
        manifest.add("integrations", integrations);
        return manifest;
    }

    private static JsonArray array(Map<String, JsonObject> values) {
        JsonArray array = new JsonArray(); values.values().forEach(array::add); return array;
    }

    private static final class Requirements {
        private final Map<String, JsonObject> classes = new TreeMap<>();
        private final Map<String, JsonObject> fields = new TreeMap<>();
        private final Map<String, JsonObject> methods = new TreeMap<>();
    }

    private record OwnType(String parent, List<String> interfaces, Set<String> fields, Set<String> methods) { }

    private Set<String> memberIntegrations(String owner, String member, boolean method, String context) {
        String direct = integrationFor(owner, context);
        if (direct != null) return Set.of(direct);
        if (!ownTypes.containsKey(owner)) return Set.of();
        Set<String> integrations = new java.util.TreeSet<>();
        String activeContext = context == null ? callerIntegration(owner) : context;
        for (String declaration : declarations(owner, member, method, new HashSet<>())) {
            String integration = integrationFor(declaration, activeContext);
            if (integration != null) integrations.add(integration);
        }
        return integrations;
    }

    private OwnType typeInfo(String name) {
        if (ownTypes.containsKey(name)) return ownTypes.get(name);
        if (dependencyTypes.containsKey(name)) return dependencyTypes.get(name);
        try (var input = CompatibilityAbiManifestGenerator.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new IllegalStateException("Missing compile dependency class bytes required to extract inherited ABI: " + name);
            OwnType info = readType(new ClassReader(input));
            dependencyTypes.put(name, info); return info;
        } catch (IOException invalid) { throw new IllegalStateException("Cannot read compile dependency " + name, invalid); }
    }

    private Set<String> declarations(String owner, String member, boolean method, Set<String> visited) {
        if (owner == null || !visited.add(owner)) return Set.of();
        OwnType type = typeInfo(owner);
        if (type == null) return Set.of();
        if ((method ? type.methods : type.fields).contains(member)) return Set.of(owner);
        if (method && member.startsWith("<init>")) return Set.of();
        // Methods search the superclass chain before interface defaults; field resolution searches interfaces first.
        if (method) {
            Set<String> parent = declarations(type.parent, member, true, visited);
            if (!parent.isEmpty()) return parent;
        }
        Set<String> interfaces = new java.util.TreeSet<>();
        for (String implemented : type.interfaces) interfaces.addAll(declarations(implemented, member, method, visited));
        if (!interfaces.isEmpty()) return interfaces;
        return method ? Set.of() : declarations(type.parent, member, false, visited);
    }

    private static String primaryIntegration(String name) {
        if (name.startsWith("com/stal111/forbidden_arcanus/")) return "forbidden_arcanus";
        return null;
    }

    private static String callerIntegration(String caller) {
        String base = OWN_PREFIX + "integration/";
        if (caller.startsWith(base + "forbiddenarcanus/")) return "forbidden_arcanus";
        return null;
    }

    private static String integrationFor(String name, String context) {
        String primary = primaryIntegration(name);
        if (primary != null) return primary;
        if ("forbidden_arcanus".equals(context) && name.startsWith("net/valhelsia/valhelsia_core/")) return context;
        return null;
    }

    private final class AddonClassVisitor extends ClassVisitor {
        private final String caller;
        private final String context;

        private AddonClassVisitor(String caller) { super(Opcodes.ASM9); this.caller = caller; context = callerIntegration(caller); }

        private void classReference(String name, String activeContext, Boolean expectedInterface, boolean extensible) {
            classReference(name, activeContext, expectedInterface, extensible, false);
        }

        private void classReference(String name, String activeContext, Boolean expectedInterface, boolean extensible, boolean accessRequired) {
            if (name == null) return;
            if (name.startsWith("[")) { type(Type.getType(name), activeContext, accessRequired); return; }
            String integration = integrationFor(name, activeContext);
            if (integration == null) return;
            JsonObject entry = groups.get(integration).classes.computeIfAbsent(name + "\0" + caller, ignored -> {
                JsonObject result = new JsonObject(); result.addProperty("name", name); result.addProperty("caller", caller); return result;
            });
            entry.addProperty("clientOnly", clientOnly(name));
            if (accessRequired) entry.addProperty("accessRequired", true);
            if (expectedInterface != null) {
                if (entry.has("interfaceExpected") && entry.get("interfaceExpected").getAsBoolean() != expectedInterface) {
                    throw new IllegalStateException("Inconsistent class/interface use: " + name + " by " + caller);
                }
                entry.addProperty("interfaceExpected", expectedInterface);
            }
            if (extensible) entry.addProperty("extensibleExpected", true);
        }

        private void descriptor(String descriptor, String activeContext) {
            if (descriptor == null) return;
            type(Type.getType(descriptor), activeContext);
        }

        private void type(Type type, String activeContext) {
            type(type, activeContext, false);
        }

        private void type(Type type, String activeContext, boolean accessRequired) {
            switch (type.getSort()) {
                case Type.OBJECT -> classReference(type.getInternalName(), activeContext, null, false, accessRequired);
                case Type.ARRAY -> type(type.getElementType(), activeContext, accessRequired);
                case Type.METHOD -> { for (Type argument : type.getArgumentTypes()) type(argument, activeContext, accessRequired); type(type.getReturnType(), activeContext, accessRequired); }
                default -> { }
            }
        }

        private void signature(String signature) {
            if (signature == null) return;
            new SignatureReader(signature).accept(signatureVisitor());
        }

        private SignatureVisitor signatureVisitor() {
            return new SignatureVisitor(Opcodes.ASM9) {
                private String current;
                @Override public void visitClassType(String name) { current = name; classReference(name, context, null, false); }
                @Override public void visitInnerClassType(String name) { current += "$" + name; classReference(current, context, null, false); }
                @Override public SignatureVisitor visitClassBound() { return signatureVisitor(); }
                @Override public SignatureVisitor visitInterfaceBound() { return signatureVisitor(); }
                @Override public SignatureVisitor visitSuperclass() { return signatureVisitor(); }
                @Override public SignatureVisitor visitInterface() { return signatureVisitor(); }
                @Override public SignatureVisitor visitParameterType() { return signatureVisitor(); }
                @Override public SignatureVisitor visitReturnType() { return signatureVisitor(); }
                @Override public SignatureVisitor visitExceptionType() { return signatureVisitor(); }
                @Override public SignatureVisitor visitArrayType() { return signatureVisitor(); }
                @Override public SignatureVisitor visitTypeArgument(char wildcard) { return signatureVisitor(); }
            };
        }

        private AnnotationVisitor annotation(String descriptor) {
            descriptor(descriptor, context);
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override public void visit(String name, Object value) { constant(value, context); }
                @Override public void visitEnum(String name, String descriptor, String value) { descriptor(descriptor, context); }
                @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) { return annotation(descriptor); }
                @Override public AnnotationVisitor visitArray(String name) { return this; }
            };
        }

        private void fieldReference(String owner, String name, String descriptor, boolean isStatic, boolean write) {
            Set<String> integrations = memberIntegrations(owner, name + descriptor, false, context);
            String activeContext = integrations.size() == 1 ? integrations.iterator().next() : context;
            classReference(owner, activeContext, null, false); descriptor(descriptor, activeContext);
            String key = owner + "\0" + name + "\0" + descriptor + "\0" + isStatic + "\0" + caller;
            for (String integration : integrations) {
                JsonObject entry = groups.get(integration).fields.computeIfAbsent(key, ignored -> member(owner, name, descriptor, isStatic));
                if (write) entry.addProperty("writeExpected", true);
            }
        }

        private void methodReference(String owner, String name, String descriptor, boolean isStatic, boolean isInterface) {
            Set<String> integrations = memberIntegrations(owner, name + descriptor, true, context);
            String activeContext = integrations.size() == 1 ? integrations.iterator().next() : context;
            classReference(owner, activeContext, isInterface, false); descriptor(descriptor, activeContext);
            String key = owner + "\0" + name + "\0" + descriptor + "\0" + isStatic + "\0" + isInterface + "\0" + caller;
            JsonObject entry = member(owner, name, descriptor, isStatic); entry.addProperty("interfaceOwnerExpected", isInterface);
            for (String integration : integrations) groups.get(integration).methods.putIfAbsent(key, entry);
        }

        private JsonObject member(String owner, String name, String descriptor, boolean isStatic) {
            JsonObject result = new JsonObject(); result.addProperty("owner", owner); result.addProperty("name", name);
            result.addProperty("descriptor", descriptor); result.addProperty("staticExpected", isStatic); result.addProperty("caller", caller);
            result.addProperty("clientOnly", clientOnly(owner)); return result;
        }

        private boolean clientOnly(String target) { return caller.contains("/client/") || target.contains("/client/"); }

        private void handle(Handle handle, String activeContext) {
            // The owner determines the provider even for references used by cross-mod AE/client helpers.
            switch (handle.getTag()) {
                case Opcodes.H_GETFIELD -> fieldReference(handle.getOwner(), handle.getName(), handle.getDesc(), false, false);
                case Opcodes.H_GETSTATIC -> fieldReference(handle.getOwner(), handle.getName(), handle.getDesc(), true, false);
                case Opcodes.H_PUTFIELD -> fieldReference(handle.getOwner(), handle.getName(), handle.getDesc(), false, true);
                case Opcodes.H_PUTSTATIC -> fieldReference(handle.getOwner(), handle.getName(), handle.getDesc(), true, true);
                default -> methodReference(handle.getOwner(), handle.getName(), handle.getDesc(), handle.getTag() == Opcodes.H_INVOKESTATIC, handle.isInterface());
            }
            descriptor(handle.getDesc(), activeContext);
        }

        private void constant(Object value, String activeContext) {
            if (value instanceof Type type) type(type, activeContext, true);
            else if (value instanceof Handle handle) handle(handle, activeContext);
            else if (value instanceof ConstantDynamic dynamic) {
                descriptor(dynamic.getDescriptor(), activeContext); handle(dynamic.getBootstrapMethod(), activeContext);
                for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) constant(dynamic.getBootstrapMethodArgument(i), activeContext);
            }
        }

        @Override public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            classReference(superName, context, false, true, true);
            for (String implemented : interfaces) classReference(implemented, context, true, false, true);
            signature(signature);
        }

        @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) { return annotation(descriptor); }
        @Override public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) { return annotation(descriptor); }
        @Override public void visitOuterClass(String owner, String name, String descriptor) { classReference(owner, context, null, false); descriptor(descriptor, context); }
        @Override public void visitNestHost(String nestHost) { classReference(nestHost, context, null, false); }
        @Override public void visitNestMember(String nestMember) { classReference(nestMember, context, null, false); }
        @Override public void visitPermittedSubclass(String permittedSubclass) { classReference(permittedSubclass, context, null, false); }
        @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            descriptor(descriptor, context); signature(signature); constant(value, context);
            return new FieldVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) { return annotation(descriptor); }
                @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String descriptor, boolean visible) { return annotation(descriptor); }
            };
        }
        @Override public RecordComponentVisitor visitRecordComponent(String name, String descriptor, String signature) {
            descriptor(descriptor, context); signature(signature);
            return new RecordComponentVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) { return annotation(descriptor); }
                @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String descriptor, boolean visible) { return annotation(descriptor); }
            };
        }
        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            descriptor(descriptor, context); signature(signature);
            if (exceptions != null) for (String exception : exceptions) classReference(exception, context, null, false);
            return new MethodVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) { return annotation(descriptor); }
                @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String descriptor, boolean visible) { return annotation(descriptor); }
                @Override public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) { return annotation(descriptor); }
                @Override public AnnotationVisitor visitAnnotationDefault() { return annotation("Ljava/lang/annotation/Annotation;"); }
                @Override public void visitTypeInsn(int opcode, String type) { classReference(type, context, opcode == Opcodes.NEW ? false : null, false, true); }
                @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                    fieldReference(owner, name, descriptor, opcode == Opcodes.GETSTATIC || opcode == Opcodes.PUTSTATIC,
                            opcode == Opcodes.PUTFIELD || opcode == Opcodes.PUTSTATIC);
                }
                @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                    methodReference(owner, name, descriptor, opcode == Opcodes.INVOKESTATIC, isInterface);
                }
                @Override public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... arguments) {
                    descriptor(descriptor, context); handle(bootstrap, context);
                    for (Object argument : arguments) constant(argument, context);
                }
                @Override public void visitLdcInsn(Object value) { constant(value, context); }
                @Override public void visitMultiANewArrayInsn(String descriptor, int dimensions) { type(Type.getType(descriptor), context, true); }
                @Override public void visitTryCatchBlock(Label start, Label end, Label handler, String type) { classReference(type, context, null, false, true); }
                @Override public AnnotationVisitor visitInsnAnnotation(int ref, TypePath path, String descriptor, boolean visible) { return annotation(descriptor); }
                @Override public AnnotationVisitor visitTryCatchAnnotation(int ref, TypePath path, String descriptor, boolean visible) { return annotation(descriptor); }
            };
        }
    }
}
