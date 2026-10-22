package com.example.mekanismmagic.integration.naturesaura.client;

import com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerRecipeBridge.AnimalSpawnerRecipeView;
import com.example.mekanismmagic.integration.naturesaura.OfferingRecipeBridge.OfferingRecipeView;
import com.example.mekanismmagic.integration.naturesaura.TreeRitualRecipeBridge.TreeRitualRecipeView;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Real JEI slot builders plus a bytecode boundary preventing world-scanning/spawning from returning. */
public final class NatureInternalJeiSelfTest {
    private NatureInternalJeiSelfTest() { }

    public static void main(String[] args) throws IOException {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        actualRecipeSlots();
        encodedEggSubtypes();
        noExternalProcessingCalls();
        System.out.println("NatureInternalJeiSelfTest: 3 JEI layouts, egg subtypes and 4 no-world processing boundaries passed");
    }

    private static void actualRecipeSlots() {
        IFocusGroup focus = proxy(IFocusGroup.class, (object, method, args) -> { throw unexpected(method); });
        var tree = new TreeRitualRecipeView(id("tree"), Ingredient.of(Items.OAK_SAPLING),
                List.of(Ingredient.of(Items.STONE), Ingredient.of(Items.STONE)), new ItemStack(Items.DIAMOND, 2), 80);
        var treeCategory = new TreeRitualJeiCategory(gui(), Items.BARRIER, Items.GLOWSTONE_DUST);
        Layout layout = new Layout();
        treeCategory.setRecipe(layout.builder, tree, focus);
        var slots = layout.named();
        require(slots.size() == 5, "Internal tree page lost a real input/output");
        slots.get("material_0").check(RecipeIngredientRole.INPUT, 4, 18, new ItemStack(Items.STONE));
        slots.get("material_1").check(RecipeIngredientRole.INPUT, 22, 18, new ItemStack(Items.STONE));
        slots.get("sapling").check(RecipeIngredientRole.INPUT, 88, 18, new ItemStack(Items.OAK_SAPLING));
        slots.get("gold_powder").check(RecipeIngredientRole.INPUT, 108, 18, new ItemStack(Items.GLOWSTONE_DUST, 16));
        slots.get("output").check(RecipeIngredientRole.OUTPUT, 151, 28, tree.output());
        require(treeCategory.getRegistryName(tree).equals(tree.id()), "Tree recipe identity changed");

        var offering = new OfferingRecipeView(id("offering"), Ingredient.of(Items.STONE), 2,
                Ingredient.of(Items.GOLD_NUGGET), new ItemStack(Items.DIAMOND));
        var offeringCategory = new OfferingJeiCategory(gui(), Items.BARRIER);
        layout = new Layout();
        offeringCategory.setRecipe(layout.builder, offering, focus);
        slots = layout.named();
        require(slots.size() == 4, "Offering page must separate optional retained roses from consumed inputs");
        slots.get("offering_input").check(RecipeIngredientRole.INPUT, 14, 26, new ItemStack(Items.STONE, 2));
        slots.get("start_item").check(RecipeIngredientRole.INPUT, 52, 26, new ItemStack(Items.GOLD_NUGGET));
        slots.get("optional_wither_roses").check(RecipeIngredientRole.CATALYST, 84, 26, new ItemStack(Items.WITHER_ROSE, 36));
        slots.get("output").check(RecipeIngredientRole.OUTPUT, 120, 26, offering.output());

        var animal = new AnimalSpawnerRecipeView(id("animal"), EntityType.COW,
                List.of(Ingredient.of(Items.WHEAT), Ingredient.of(Items.FEATHER)), List.of(2, 4), 18000, 60);
        var animalCategory = new AnimalSpawnerJeiCategory(gui(), Items.BARRIER);
        layout = new Layout();
        animalCategory.setRecipe(layout.builder, animal, focus);
        slots = layout.named();
        require(slots.size() == 3, "Animal page must expose its real spawn-egg output");
        slots.get("material_0").check(RecipeIngredientRole.INPUT, 5, 16, new ItemStack(Items.WHEAT, 2));
        slots.get("material_1").check(RecipeIngredientRole.INPUT, 25, 16, new ItemStack(Items.FEATHER, 4));
        slots.get("spawn_egg").check(RecipeIngredientRole.OUTPUT, 136, 28, new ItemStack(Items.COW_SPAWN_EGG));
        require(animalCategory.getRegistryName(animal).equals(animal.id()), "Animal recipe identity changed");
    }

    private static void encodedEggSubtypes() {
        var interpreter = AnimalSpawnEggSubtypeInterpreter.INSTANCE;
        var context = UidContext.values()[0];
        ItemStack first = new ItemStack(Items.PAPER), second = new ItemStack(Items.PAPER);
        CompoundTag data = new CompoundTag();
        data.putString("id", "minecraft:giant");
        first.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        data.putString("id", "minecraft:illusioner");
        second.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        Object giant = interpreter.getSubtypeData(first, context);
        require(giant != null && !giant.equals(interpreter.getSubtypeData(second, context))
                        && giant.equals(interpreter.getSubtypeData(first.copyWithCount(32), context)),
                "Encoded targets collapsed or stack count changed JEI subtype");
        require(interpreter.getSubtypeData(ItemStack.EMPTY, context) == null
                        && interpreter.getLegacyStringSubtypeInfo(ItemStack.EMPTY, context).isEmpty(),
                "Empty subtype invented a minecraft: target");
    }

    private static void noExternalProcessingCalls() throws IOException {
        String prefix = "/com/example/mekanismmagic/integration/naturesaura/";
        Set<String> worldCalls = Set.of("setBlock", "setBlockAndUpdate", "destroyBlock", "removeBlock", "getBlockState",
                "getEntities", "getEntitiesOfClass", "noCollision", "addFreshEntity", "addFreshEntityWithPassengers");
        for (String type : List.of("TreeRitualProcessorBlockEntity", "OfferingProcessorBlockEntity",
                "AnimalSpawnerProcessorBlockEntity", "AnimalSpawnEggs")) {
            try (var input = NatureInternalJeiSelfTest.class.getResourceAsStream(prefix + type + ".class")) {
                require(input != null, "Missing production class " + type);
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                                require(!(owner.startsWith("net/minecraft/world/level/") || owner.equals("net/minecraft/server/level/ServerLevel"))
                                                || !worldCalls.contains(method), "External world access returned in " + type + ": " + method);
                                require(!owner.equals("net/minecraft/world/entity/EntityType")
                                                || !Set.of("create", "spawn").contains(method), "Production constructed/spawned a real entity");
                                require(!owner.contains("Multiblocks") && !method.equals("advanceTree") && !method.equals("performBonemeal"),
                                        "Internal production regained structure/growth work");
                                require(!method.equals("getRitualCenter"), "Production used the diagnostic legacy ritual location");
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        for (String deleted : List.of("TreeRitualWorldActions", "TreeRitualEnvironment", "OfferingEnvironment",
                "AnimalSpawnerEnvironment", "AnimalSpawnerSpawnTransaction")) {
            require(NatureInternalJeiSelfTest.class.getResource(prefix + deleted + ".class") == null,
                    "Obsolete world-processing helper remained packaged: " + deleted);
        }
    }

    private static final class Layout {
        final List<Slot> slots = new ArrayList<>();
        final IRecipeLayoutBuilder builder = proxy(IRecipeLayoutBuilder.class, (object, method, args) -> {
            if (method.isDefault()) return InvocationHandler.invokeDefault(object, method, args);
            if (!method.getName().equals("addSlot")) throw unexpected(method);
            Slot slot = new Slot((RecipeIngredientRole) args[0]);
            slots.add(slot);
            return slot.builder;
        });

        Map<String, Slot> named() {
            Map<String, Slot> result = new LinkedHashMap<>();
            for (Slot slot : slots) require(slot.name != null && result.put(slot.name, slot) == null, "Missing/duplicate slot name");
            return result;
        }
    }

    private static final class Slot {
        final RecipeIngredientRole role;
        final List<ItemStack> items = new ArrayList<>();
        final List<IRecipeSlotRichTooltipCallback> tooltips = new ArrayList<>();
        String name;
        int x, y;
        final IRecipeSlotBuilder builder = proxy(IRecipeSlotBuilder.class, (object, method, args) -> {
            if (method.isDefault()) return InvocationHandler.invokeDefault(object, method, args);
            switch (method.getName()) {
                case "setPosition" -> { x = (int) args[0]; y = (int) args[1]; }
                case "setSlotName" -> name = (String) args[0];
                case "setBackground" -> require(args[0] instanceof IDrawableStatic, "Missing themed slot drawable");
                case "addIngredient" -> {
                    require(args[0] == VanillaTypes.ITEM_STACK, "Unexpected ingredient type");
                    items.add(((ItemStack) args[1]).copy());
                }
                case "addIngredients" -> {
                    require(args[0] == VanillaTypes.ITEM_STACK, "Unexpected ingredient type");
                    for (Object item : (List<?>) args[1]) items.add(((ItemStack) item).copy());
                }
                case "addRichTooltipCallback" -> tooltips.add((IRecipeSlotRichTooltipCallback) args[0]);
                default -> throw unexpected(method);
            }
            return object;
        });

        Slot(RecipeIngredientRole role) { this.role = role; }

        void check(RecipeIngredientRole expectedRole, int expectedX, int expectedY, ItemStack expectedItem) {
            require(role == expectedRole && x == expectedX && y == expectedY && items.size() == 1
                    && ItemStack.matches(items.getFirst(), expectedItem), "Wrong role, location or ingredient contents in " + name);
        }
    }

    private static IGuiHelper gui() {
        IDrawableStatic drawable = proxy(IDrawableStatic.class, (object, method, args) -> {
            if (method.getName().equals("getWidth") || method.getName().equals("getHeight")) return 18;
            throw unexpected(method);
        });
        return proxy(IGuiHelper.class, (object, method, args) -> switch (method.getName()) {
            case "createDrawable", "createDrawableItemLike", "createBlankDrawable" -> drawable;
            default -> throw unexpected(method);
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "equals" -> object == args[0];
                case "hashCode" -> System.identityHashCode(object);
                case "toString" -> "Recording " + type.getSimpleName();
                default -> throw unexpected(method);
            };
            return handler.invoke(object, method, args == null ? new Object[0] : args);
        }));
    }


    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("internal_nature_jei_test", path); }
    private static AssertionError unexpected(Method method) { return new AssertionError("Unexpected API/render call: " + method); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
