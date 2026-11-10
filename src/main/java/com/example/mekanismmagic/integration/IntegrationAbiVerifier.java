package com.example.mekanismmagic.integration;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Checks the bridge's compiled symbolic references without loading or initializing optional classes. */
public final class IntegrationAbiVerifier {
    public static final String MANIFEST = "META-INF/mekanism_magic/compatibility-abi.json";
    private static final Set<CheckedIntegration> CHECKED = ConcurrentHashMap.newKeySet();
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private IntegrationAbiVerifier() { }

    @FunctionalInterface
    public interface ResourceLookup {
        InputStream open(String resourcePath) throws IOException;
    }

    /** A different version number alone is never a rejection reason. Missing/broken ABI data is not ignored. */
    public static void requireCompatible(String integrationId) {
        ClassLoader loader = IntegrationAbiVerifier.class.getClassLoader();
        boolean includeClient = FMLEnvironment.dist == Dist.CLIENT;
        var checked = new CheckedIntegration(loader, integrationId, includeClient);
        if (CHECKED.contains(checked)) return;
        ResourceLookup resources = path -> {
            InputStream input = loader.getResourceAsStream(path);
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            return input != null || context == null || context == loader ? input : context.getResourceAsStream(path);
        };
        try {
            JsonObject requirements = readRequirements(integrationId, resources);
            List<String> failures = inspectRequirements(requirements, resources, includeClient);
            if (!failures.isEmpty()) {
                String summary = String.join("\n - ", failures.stream().limit(12).toList());
                throw new IllegalStateException("Incompatible installed API members (not a version-number mismatch):\n - "
                        + summary + (failures.size() > 12 ? "\n - ... " + (failures.size() - 12) + " more" : ""));
            }
            var installed = new TreeMap<String, String>();
            installed.put(integrationId, ModCompatibility.installedVersion(integrationId).orElse("absent"));
            if (requirements.has("compiledAgainst") && requirements.get("compiledAgainst").isJsonObject()) {
                for (String id : requirements.getAsJsonObject("compiledAgainst").keySet()) {
                    installed.put(id, ModCompatibility.installedVersion(id).orElse("absent"));
                }
            }
            if (CHECKED.add(checked)) {
                LOGGER.info("Verified {} integration ABI: installed={}, classes={}, fields={}, methods={}, client={}, compile baseline={} (not a version restriction)",
                        integrationId, installed, checkedCount(requirements, "classes", includeClient),
                        checkedCount(requirements, "fields", includeClient), checkedCount(requirements, "methods", includeClient),
                        includeClient, requirements.get("compiledAgainst"));
            }
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot verify Mekanism Magic's " + integrationId + " integration: "
                    + failure.getMessage() + "\nStartup stopped before omitting saved machine registrations or inventories.", failure);
        }
    }

    private static int checkedCount(JsonObject requirements, String collection, boolean includeClient) {
        int count = 0;
        for (JsonElement entry : requirements.getAsJsonArray(collection)) {
            JsonObject object = entry.getAsJsonObject();
            if (includeClient || !object.has("clientOnly") || !object.get("clientOnly").getAsBoolean()) count++;
        }
        return count;
    }

    private static JsonObject readRequirements(String integrationId, ResourceLookup resources) throws IOException {
        JsonObject manifest;
        try (InputStream input = resources.open(MANIFEST)) {
            if (input == null) throw new IllegalStateException("Missing generated compatibility ABI manifest: " + MANIFEST);
            manifest = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        if (!manifest.has("schemaVersion") || manifest.get("schemaVersion").getAsInt() != 1
                || !manifest.has("integrations") || !manifest.get("integrations").isJsonObject()) {
            throw new IllegalStateException("Unsupported compatibility ABI manifest schema");
        }
        JsonElement entry = manifest.getAsJsonObject("integrations").get(integrationId);
        if (entry == null || !entry.isJsonObject()) throw new IllegalStateException("Missing generated compatibility requirements for " + integrationId);
        JsonObject requirements = entry.getAsJsonObject();
        if (!requirements.has("classes") || !requirements.get("classes").isJsonArray()
                || requirements.getAsJsonArray("classes").isEmpty()) throw new IllegalStateException("Empty compatibility requirements for " + integrationId);
        return requirements;
    }

    /** Also used by synthetic bytecode regressions; the lookup supplies bytes, never Class.forName. */
    public static List<String> inspectRequirements(JsonObject requirements, ResourceLookup resources) {
        return inspectRequirements(requirements, resources, true);
    }

    public static List<String> inspectRequirements(JsonObject requirements, ResourceLookup resources, boolean includeClient) {
        return new Inspection(resources, includeClient).inspect(requirements);
    }

    private record CheckedIntegration(ClassLoader loader, String id, boolean includeClient) { }
    private record Member(String owner, String name, String descriptor, int access) { }
    private static final class Shape {
        String name, parent, nestHost, outer;
        int access, innerAccess = -1;
        List<String> interfaces = List.of();
        final List<Member> fields = new ArrayList<>(), methods = new ArrayList<>();
        boolean isInterface() { return (access & Opcodes.ACC_INTERFACE) != 0; }
    }

    private static final class Inspection {
        private final ResourceLookup resources;
        private final boolean includeClient;
        private final Map<String, Shape> shapes = new HashMap<>();
        private final Set<String> absent = new HashSet<>();
        private final Set<String> checkedHierarchies = new HashSet<>();
        private final Set<String> failures = new TreeSet<>();

        Inspection(ResourceLookup resources, boolean includeClient) { this.resources = resources; this.includeClient = includeClient; }

        List<String> inspect(JsonObject requirements) {
            for (String collection : List.of("classes", "fields", "methods")) {
                if (!requirements.has(collection) || !requirements.get(collection).isJsonArray()) {
                    throw new IllegalArgumentException("Malformed ABI requirements: " + collection);
                }
            }
            for (JsonElement element : requirements.getAsJsonArray("classes")) {
                JsonObject requirement = element.getAsJsonObject();
                if (!includeClient && bool(requirement, "clientOnly")) continue;
                String name = string(requirement, "name");
                Shape shape = shape(name);
                if (shape == null) failures.add("Missing class " + name);
                else {
                    requireHierarchy(name, new HashSet<>());
                    if (requirement.has("interfaceExpected") && shape.isInterface() != requirement.get("interfaceExpected").getAsBoolean())
                        failures.add("Class/interface kind changed: " + name);
                    if (bool(requirement, "extensibleExpected") && (shape.access & Opcodes.ACC_FINAL) != 0)
                        failures.add("Superclass became final: " + name);
                    if (bool(requirement, "accessRequired") && !accessibleClass(shape, string(requirement, "caller")))
                        failures.add("Class is no longer accessible: " + name + " from " + string(requirement, "caller"));
                }
            }
            for (JsonElement element : requirements.getAsJsonArray("fields")) inspectMember(element.getAsJsonObject(), false);
            for (JsonElement element : requirements.getAsJsonArray("methods")) inspectMember(element.getAsJsonObject(), true);
            return List.copyOf(failures);
        }

        private void inspectMember(JsonObject requirement, boolean method) {
            if (!includeClient && bool(requirement, "clientOnly")) return;
            String owner = string(requirement, "owner"), name = string(requirement, "name");
            String descriptor = string(requirement, "descriptor"), caller = string(requirement, "caller");
            if (!requirement.has("staticExpected")) throw new IllegalArgumentException("Missing member static expectation");
            boolean expectStatic = requirement.get("staticExpected").getAsBoolean();
            String symbol = owner + "." + name + descriptor;
            Shape ownerShape = shape(owner);
            if (ownerShape == null) { failures.add("Missing class " + owner + " required by " + caller); return; }
            requireHierarchy(owner, new HashSet<>());
            if (!accessibleClass(ownerShape, caller)) failures.add("Member owner class is no longer accessible: " + owner + " from " + caller);
            if (method && requirement.has("interfaceOwnerExpected")
                    && ownerShape.isInterface() != requirement.get("interfaceOwnerExpected").getAsBoolean()) {
                failures.add("Method owner class/interface kind changed: " + symbol);
            }
            Member actual = method ? method(owner, name, descriptor, expectStatic, new HashSet<>())
                    : field(owner, name, descriptor, new HashSet<>());
            if (actual == null) failures.add("Missing " + (method ? "method " : "field ") + symbol + " required by " + caller);
            else {
                if (((actual.access & Opcodes.ACC_STATIC) != 0) != expectStatic)
                    failures.add("Static/instance kind changed: " + symbol);
                if (!accessible(actual, caller)) failures.add("Member is no longer accessible: " + symbol + " from " + caller);
                if (!method && bool(requirement, "writeExpected") && (actual.access & Opcodes.ACC_FINAL) != 0)
                    failures.add("Written field became final: " + symbol);
            }
        }

        private Shape shape(String name) {
            if (shapes.containsKey(name)) return shapes.get(name);
            if (absent.contains(name)) return null;
            try (InputStream input = resources.open(name + ".class")) {
                if (input == null) { absent.add(name); return null; }
                var shape = new Shape();
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public void visit(int version, int access, String actualName, String signature,
                                                String superName, String[] interfaces) {
                        shape.name = actualName; shape.access = access; shape.parent = superName;
                        shape.interfaces = interfaces == null ? List.of() : List.of(interfaces);
                    }
                    @Override public void visitNestHost(String nestHost) { shape.nestHost = nestHost; }
                    @Override public void visitInnerClass(String name, String outerName, String innerName, int access) {
                        if (name.equals(shape.name)) { shape.innerAccess = access; shape.outer = outerName; }
                    }
                    @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                        shape.fields.add(new Member(shape.name, name, descriptor, access)); return null;
                    }
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        shape.methods.add(new Member(shape.name, name, descriptor, access)); return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                if (!name.equals(shape.name)) throw new IllegalArgumentException("Class resource name mismatch: " + name + " / " + shape.name);
                shapes.put(name, shape);
                return shape;
            } catch (IOException | RuntimeException failure) {
                absent.add(name); failures.add("Cannot read class " + name + ": " + failure.getClass().getSimpleName()); return null;
            }
        }

        private Member field(String owner, String name, String descriptor, Set<String> seen) {
            if (!seen.add(owner)) return null;
            Shape shape = shape(owner); if (shape == null) return null;
            for (Member member : shape.fields) if (member.name.equals(name) && member.descriptor.equals(descriptor)) return member;
            for (String contract : shape.interfaces) {
                Member match = field(contract, name, descriptor, seen); if (match != null) return match;
            }
            return shape.parent == null ? null : field(shape.parent, name, descriptor, seen);
        }

        private void requireHierarchy(String name, Set<String> path) {
            if (checkedHierarchies.contains(name)) return;
            if (!path.add(name)) { failures.add("Circular superclass/interface hierarchy at " + name); return; }
            Shape current = shape(name);
            if (current != null) {
                if (current.parent != null) {
                    Shape parent = shape(current.parent);
                    if (parent == null) failures.add("Missing superclass " + current.parent + " required by " + name);
                    else {
                        if (parent.isInterface()) failures.add("Superclass became an interface: " + current.parent + " required by " + name);
                        if ((parent.access & Opcodes.ACC_FINAL) != 0) failures.add("Superclass became final: " + current.parent + " required by " + name);
                        requireHierarchy(current.parent, path);
                    }
                }
                for (String contract : current.interfaces) {
                    Shape parent = shape(contract);
                    if (parent == null) failures.add("Missing interface " + contract + " required by " + name);
                    else {
                        if (!parent.isInterface()) failures.add("Interface became a class: " + contract + " required by " + name);
                        requireHierarchy(contract, path);
                    }
                }
            }
            path.remove(name); checkedHierarchies.add(name);
        }

        private Member method(String owner, String name, String descriptor, boolean expectStatic, Set<String> seen) {
            if (!seen.add(owner)) return null;
            Shape shape = shape(owner); if (shape == null) return null;
            for (Member member : shape.methods) if (member.name.equals(name) && member.descriptor.equals(descriptor)) return member;
            // JVM constructors and static interface methods are never inherited.
            if (name.equals("<init>") || name.equals("<clinit>") || shape.isInterface() && expectStatic) return null;
            if (!shape.isInterface() && shape.parent != null) {
                Member inherited = method(shape.parent, name, descriptor, expectStatic, seen);
                if (inherited != null) return inherited;
            }
            for (String contract : shape.interfaces) {
                Member inherited = method(contract, name, descriptor, expectStatic, seen);
                if (inherited != null && (inherited.access & (Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == 0) return inherited;
            }
            // invokeinterface can resolve Object's public instance methods (e.g. equals/toString).
            if (shape.isInterface() && !expectStatic) {
                Member inherited = method("java/lang/Object", name, descriptor, false, seen);
                if (inherited != null && (inherited.access & Opcodes.ACC_PUBLIC) != 0
                        && (inherited.access & Opcodes.ACC_STATIC) == 0) return inherited;
            }
            return null;
        }

        private boolean accessible(Member member, String caller) {
            if ((member.access & Opcodes.ACC_PUBLIC) != 0) return true;
            if ((member.access & Opcodes.ACC_PRIVATE) != 0) return sameNest(member.owner, caller);
            if (samePackage(member.owner, caller)) return true;
            if ((member.access & Opcodes.ACC_PROTECTED) == 0) return false;
            if (subclass(caller, member.owner, new HashSet<>())) return true;
            Shape source = shape(caller);
            return source != null && source.nestHost != null && subclass(source.nestHost, member.owner, new HashSet<>());
        }

        private boolean accessibleClass(Shape target, String caller) {
            int access = target.innerAccess >= 0 ? target.innerAccess : target.access;
            if ((access & Opcodes.ACC_PUBLIC) != 0) return true;
            if ((access & Opcodes.ACC_PRIVATE) != 0) return sameNest(target.name, caller);
            if (samePackage(target.name, caller)) return true;
            if ((access & Opcodes.ACC_PROTECTED) == 0 || target.outer == null) return false;
            if (subclass(caller, target.outer, new HashSet<>())) return true;
            Shape source = shape(caller);
            return source != null && source.nestHost != null && subclass(source.nestHost, target.outer, new HashSet<>());
        }

        private boolean sameNest(String first, String second) {
            if (first.equals(second)) return true;
            Shape a = shape(first), b = shape(second);
            return a != null && b != null && (a.nestHost == null ? a.name : a.nestHost)
                    .equals(b.nestHost == null ? b.name : b.nestHost);
        }

        private boolean subclass(String child, String parent, Set<String> seen) {
            if (child.equals(parent)) return true;
            if (!seen.add(child)) return false;
            Shape shape = shape(child);
            return shape != null && shape.parent != null && subclass(shape.parent, parent, seen);
        }

        private static boolean samePackage(String first, String second) {
            return first.substring(0, Math.max(0, first.lastIndexOf('/')))
                    .equals(second.substring(0, Math.max(0, second.lastIndexOf('/'))));
        }
        private static String string(JsonObject object, String key) {
            if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.get(key).getAsJsonPrimitive().isString()
                    || object.get(key).getAsString().isBlank()) throw new IllegalArgumentException("Missing ABI string: " + key);
            return object.get(key).getAsString();
        }
        private static boolean bool(JsonObject object, String key) { return object.has(key) && object.get(key).getAsBoolean(); }
    }
}
