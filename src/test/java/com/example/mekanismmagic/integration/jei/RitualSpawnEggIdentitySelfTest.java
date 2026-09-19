package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.item.RitualSpawnEggIdentity;
import com.google.gson.JsonParser;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Executable item/subtype/reload checks without JEI implementation classes, GL or live entities. */
public final class RitualSpawnEggIdentitySelfTest {
    private RitualSpawnEggIdentitySelfTest() { }
    public static void main(String[] args) throws Exception {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        identities();
        legacyAndCosmetics();
        runtimeIngredientReconciliation();
        wiringAndLanguage();
        System.out.println("RitualSpawnEggIdentitySelfTest: entity/job/tier/pool/trait distinctions, legacy and named-egg lookup, stable colors, JEI subtype and reload checks passed");
    }

    private static ItemStack egg(String entity, String job) {
        // A vanilla item supplies the registered holder; the read-only identity functions consume the same saved payload.
        ItemStack stack = new ItemStack(Items.WOLF_SPAWN_EGG);
        CompoundTag data = new CompoundTag();
        if (!entity.isBlank()) data.putString("id", entity);
        if (!job.isBlank()) data.putString("ritual_initialize_spirit_job", "occultism:" + job);
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        return stack;
    }

    private static void update(ItemStack stack, java.util.function.Consumer<CompoundTag> update) {
        CompoundTag data = stack.get(DataComponents.ENTITY_DATA).copyTag();
        update.accept(data);
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(data));
    }

    private static void identities() {
        List<ItemStack> examples = new ArrayList<>();
        for (String job : List.of("crush_tier1", "crystal_tier1", "transport_items", "trader_otherrock", "trader_otherstone")) {
            examples.add(egg("occultism:foliot", job));
        }
        examples.add(egg("occultism:djinni", "crush_tier2"));
        examples.add(egg("occultism:afrit", "crush_tier3"));
        examples.add(egg("occultism:marid", "crush_tier4"));
        examples.add(egg("minecraft:wolf", ""));
        for (String pool : List.of("common", "small", "special", "water", "rideable")) {
            ItemStack stack = egg("", "");
            update(stack, data -> data.putString("ritual_entity_tag", "occultism:random_animals_" + pool));
            examples.add(stack);
        }
        ItemStack owned = egg("minecraft:wolf", "");
        update(owned, data -> data.putInt("ritual_owner_mode", 1));
        examples.add(owned);
        ItemStack chance = owned.copy();
        update(chance, data -> data.putInt("ritual_chicken_fallback_denominator", 3));
        examples.add(chance);
        ItemStack resurrected = owned.copy();
        update(resurrected, data -> data.putInt("ritual_owner_mode", 2));
        examples.add(resurrected);
        var keys = new HashSet<String>();
        var names = new HashSet<Component>();
        for (ItemStack stack : examples) {
            ItemStack before = stack.copy();
            String key = RitualSpawnEggIdentity.variantKey(stack);
            require(keys.add(key) && !key.isBlank(), "Different ritual targets/jobs/pools collapsed");
            require(names.add(RitualSpawnEggIdentity.name(stack)), "Different ritual variants retained the same name");
            for (UidContext context : UidContext.values()) {
                require(key.equals(RitualSpawnEggSubtypeInterpreter.INSTANCE.getSubtypeData(stack, context))
                        && key.equals(RitualSpawnEggSubtypeInterpreter.INSTANCE.getLegacyStringSubtypeInfo(stack, context)), "JEI subtype APIs disagree");
            }
            require(RitualSpawnEggIdentity.color(stack, 0) >>> 24 == 255 && RitualSpawnEggIdentity.color(stack, 1) >>> 24 == 255
                    && RitualSpawnEggIdentity.color(stack, 2) == -1, "Egg tint alpha/layer handling is invalid");
            require(ItemStack.matches(before, stack), "Reading presentation/identity changed the stack");
        }
        require(RitualSpawnEggIdentity.color(examples.get(0), 0) != RitualSpawnEggIdentity.color(examples.get(5), 0), "Spirit ranks use one shell color");
        require(RitualSpawnEggIdentity.color(examples.get(0), 1) != RitualSpawnEggIdentity.color(examples.get(1), 1), "Crusher and crystallizer use one spot color");
        ItemStack wolf = egg("minecraft:wolf", "");
        ItemStack variant = wolf.copy();
        update(variant, data -> data.putBoolean("IsBaby", true));
        require(!RitualSpawnEggIdentity.variantKey(wolf).equals(RitualSpawnEggIdentity.variantKey(variant)), "Behavioral entity NBT was discarded");
        require(RitualSpawnEggIdentity.variantKey(ItemStack.EMPTY).isEmpty(), "Blank egg acquired a fabricated target");
    }

    private static void legacyAndCosmetics() {
        ItemStack modern = egg("occultism:foliot", "crush_tier1");
        update(modern, data -> data.putInt("ritual_owner_mode", 1));
        ItemStack legacy = egg("occultism:foliot", "");
        update(legacy, data -> {
            data.putBoolean("ritual_assign_owner", true);
            CompoundTag job = new CompoundTag();
            job.putString("factoryId", "occultism:crush_tier1");
            data.put("spiritJob", job);
        });
        require(RitualSpawnEggIdentity.variantKey(modern).equals(RitualSpawnEggIdentity.variantKey(legacy)), "Legacy owner/job representation lost JEI lookup");
        require(RitualSpawnEggIdentity.name(modern).equals(RitualSpawnEggIdentity.name(legacy)), "Legacy egg lacks its profession name");
        ItemStack named = modern.copyWithCount(11);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Player label"));
        update(named, data -> {
            data.putString("CustomName", "\"Private spirit name\"");
            data.putUUID("UUID", java.util.UUID.randomUUID());
            data.putBoolean("ritual_generate_spirit_name", true);
        });
        require(RitualSpawnEggIdentity.variantKey(modern).equals(RitualSpawnEggIdentity.variantKey(named)), "Individual names/count/UUID blocked recipe lookup");
        require(RitualSpawnEggIdentity.color(modern, 0) == RitualSpawnEggIdentity.color(named, 0)
                && RitualSpawnEggIdentity.color(modern, 1) == RitualSpawnEggIdentity.color(named, 1), "Cosmetics randomized icon colors");
        ItemStack oldPool = egg("", "");
        update(oldPool, data -> data.putString("ritual_entity_tag", "occultism:random_animals_small"));
        ItemStack newPool = new ItemStack(Items.WOLF_SPAWN_EGG);
        CompoundTag custom = new CompoundTag();
        custom.put("mekanism_magic:deferred_entity_data", oldPool.get(DataComponents.ENTITY_DATA).copyTag());
        newPool.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        require(RitualSpawnEggIdentity.variantKey(oldPool).equals(RitualSpawnEggIdentity.variantKey(newPool)), "Deferred/legacy pool identity mismatch");
        ItemStack book = modern.copy();
        update(book, data -> {
            CompoundTag saved = new CompoundTag(); saved.putString("id", "occultism:book_of_calling_foliot_transport_items");
            data.put("ritual_calling_book", saved);
        });
        ItemStack boundBook = book.copy();
        update(boundBook, data -> data.getCompound("ritual_calling_book").putString("custom_name", "Generated calling name"));
        require(RitualSpawnEggIdentity.variantKey(book).equals(RitualSpawnEggIdentity.variantKey(boundBook))
                && !RitualSpawnEggIdentity.variantKey(modern).equals(RitualSpawnEggIdentity.variantKey(book)), "Calling-book identity ignores its presence or includes its generated name");
        ItemStack reordered = modern.copy();
        update(reordered, data -> { data.putInt("Z", 2); data.putInt("A", 1); });
        ItemStack ordered = modern.copy();
        update(ordered, data -> { data.putInt("A", 1); data.putInt("Z", 2); });
        require(RitualSpawnEggIdentity.variantKey(reordered).equals(RitualSpawnEggIdentity.variantKey(ordered)), "Compound key iteration changed subtype identity");
    }

    private static void runtimeIngredientReconciliation() {
        List<ItemStack> added = new ArrayList<>(), removed = new ArrayList<>();
        IIngredientManager manager = (IIngredientManager) Proxy.newProxyInstance(IIngredientManager.class.getClassLoader(),
                new Class<?>[]{IIngredientManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("addIngredientsAtRuntime")) added.addAll((java.util.Collection<ItemStack>) args[1]);
                    else if (method.getName().equals("removeIngredientsAtRuntime")) removed.addAll((java.util.Collection<ItemStack>) args[1]);
                    else throw new AssertionError("Unexpected ingredient operation " + method);
                    return null;
                });
        ItemStack crusher = egg("occultism:foliot", "crush_tier1"), crystal = egg("occultism:foliot", "crystal_tier1");
        RitualSpawnEggJeiVariants.reconcile(manager, List.of(), List.of(crusher, crystal));
        require(added.size() == 2 && removed.isEmpty() && added.getFirst() != crusher, "Initial egg list is incomplete or borrows recipe stacks");
        added.clear();
        RitualSpawnEggJeiVariants.reconcile(manager, List.of(crusher, crystal), List.of(crystal.copyWithCount(4)));
        require(added.isEmpty() && removed.size() == 1 && removed.getFirst() == crusher, "Reload retained stale/duplicate egg variants");
        removed.clear();
        RitualSpawnEggJeiVariants.reconcile(manager, List.of(crystal), List.of(crystal.copy()));
        require(added.isEmpty() && removed.isEmpty(), "Unchanged refresh creates ingredient churn");
    }

    private static void wiringAndLanguage() throws Exception {
        for (String locale : List.of("zh_cn", "en_us")) {
            var language = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mekanism_magic/lang/" + locale + ".json"))).getAsJsonObject();
            for (String key : List.of("target", "job", "random", "chance", "owned", "resurrected")) {
                require(language.has("item.mekanism_magic.ritual_spawn_egg." + key), "Missing " + locale + " egg name: " + key);
            }
            for (String pool : List.of("common", "small", "special", "water", "rideable")) require(language.has(
                    "tooltip.mekanism_magic.ritual_spawn_egg.pool.occultism.random_animals_" + pool), "Missing friendly random pool name");
            require(language.has("tooltip.mekanism_magic.ritual_spawn_egg.pool.c.villagers"), "Missing random villager pool name");
        }
        var model = JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/mekanism_magic/models/item/ritual_spawn_egg.json"))).getAsJsonObject();
        require(model.get("parent").getAsString().equals("minecraft:item/template_spawn_egg"), "Color handler requires the vanilla tint masks");
        List<String> hooks = new ArrayList<>();
        Path plugin = Path.of("build/classes/java/main/com/example/mekanismmagic/integration/jei/MekanismMagicJeiPlugin.class");
        new ClassReader(Files.readAllBytes(plugin)).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String descriptor) {
                        if (owner.endsWith("/RitualSpawnEggSubtypeInterpreter")) hooks.add(name + ":subtype");
                    }
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                        if (method.equals("ritualEggVariants") || owner.endsWith("/RitualSpawnEggJeiVariants")) hooks.add(name + ":" + method);
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        for (String hook : List.of("registerItemSubtypes:subtype", "registerExtraIngredients:ritualEggVariants",
                "registerRecipes:reconcile", "refreshOccultismRuntime:reconcile")) require(hooks.contains(hook), "Missing real JEI lifecycle hook " + hook);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
