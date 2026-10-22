package com.example.mekanismmagic.integration.jei;

import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge.SpiritJeiData;
import com.example.mekanismmagic.integration.occultism.SpiritRecipeGroup;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Actual category/registration/reload calls through API proxies, without client startup or GL. */
public final class SpiritJeiGroupingSelfTest {
    private SpiritJeiGroupingSelfTest() { }

    public static void main(String[] args) throws IOException {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        classifierAndPartition();
        categoriesAndSlots();
        registrationAndReload();
        machineButtonsUseAllGroups();
        System.out.println("SpiritJeiGroupingSelfTest: 9 categories, 13 slot scenarios, grouped registration/reload and 3 GUI routes passed");
    }

    private static List<SpiritJeiData> examples() {
        return List.of(row("crush", "crushing", "occultism:crush_tier1", 0),
                row("crystal", "crystallize", "occultism:crystallize_tier1", 0),
                row("smelt", "smelting", "occultism:smelt_tier1", 0),
                row("blast", "blasting", "occultism:smelt_tier2", 0),
                row("smoke", "smoking", "occultism:smelt_tier3", 0),
                row("campfire", "campfire_cooking", "occultism:smelt_tier4", 0),
                row("rock", "spirit_trade", "occultism:trader_otherrock", 0),
                row("stone", "spirit_trade", "occultism:trader_otherstone", 0),
                row("sapling", "spirit_trade", "occultism:trader_otherworld_saplings", 0),
                row("gamble", "spirit_trade", "occultism:gambler", 17),
                row("legacy_gamble", "spirit_trade", "occultism:trader_gem", 4),
                row("fire", "spirit_fire", "", 0),
                row("unknown", "spirit_trade", "third_party:new_trader", 0));
    }

    private static void classifierAndPartition() {
        var expected = List.of(SpiritRecipeGroup.CRUSHING, SpiritRecipeGroup.CRYSTALLIZING,
                SpiritRecipeGroup.SMELTING, SpiritRecipeGroup.SMELTING, SpiritRecipeGroup.SMELTING, SpiritRecipeGroup.SMELTING,
                SpiritRecipeGroup.OTHERROCK_TRADER, SpiritRecipeGroup.OTHERSTONE_TRADER, SpiritRecipeGroup.SAPLING_TRADER,
                SpiritRecipeGroup.GAMBLER, SpiritRecipeGroup.GAMBLER, SpiritRecipeGroup.SPIRIT_FIRE, SpiritRecipeGroup.OTHER);
        var rows = examples();
        var copies = rows.stream().map(SpiritJeiGroupingSelfTest::copy).toList();
        for (int i = 0; i < rows.size(); i++) require(SpiritRecipeGroup.classify(rows.get(i)) == expected.get(i), "Wrong profession " + i);
        require(SpiritRecipeGroup.classify(null, null) == SpiritRecipeGroup.OTHER
                && SpiritRecipeGroup.classify("spirit_trade", null) == SpiritRecipeGroup.OTHER
                && SpiritRecipeGroup.classify("future_type", "") == SpiritRecipeGroup.OTHER, "Unknown rows must not vanish");
        var partition = SpiritRecipeGroup.partition(rows);
        Set<SpiritJeiData> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var group : SpiritRecipeGroup.values()) {
            var grouped = partition.get(group);
            require(grouped.equals(rows.stream().filter(row -> SpiritRecipeGroup.classify(row) == group).toList()), "Row order changed");
            grouped.forEach(row -> require(identities.add(row), "Duplicate registration"));
        }
        require(identities.size() == rows.size(), "Lost recipe rows");
        for (int i = 0; i < rows.size(); i++) require(ItemStack.matches(rows.get(i).spirit(), copies.get(i).spirit())
                && ItemStack.matches(rows.get(i).output(), copies.get(i).output()), "Partition mutated a recipe");
        try { partition.get(SpiritRecipeGroup.CRUSHING).clear(); throw new AssertionError("Mutable grouped list"); }
        catch (UnsupportedOperationException expectedFailure) { }
    }

    private static void categoriesAndSlots() {
        List<IRecipeCategory<?>> categories = new ArrayList<>();
        IJeiHelpers helpers = proxy(IJeiHelpers.class, (object, method, args) -> {
            if (method.getName().equals("getGuiHelper")) return gui();
            throw unexpected(method);
        });
        MekanismMagicJeiPlugin.registerSpiritCategories(proxy(IRecipeCategoryRegistration.class, (object, method, args) -> {
            if (method.getName().equals("getJeiHelpers")) return helpers;
            if (method.getName().equals("addRecipeCategories")) { Collections.addAll(categories, (IRecipeCategory<?>[]) args[0]); return null; }
            throw unexpected(method);
        }));
        require(categories.size() == SpiritRecipeGroup.values().length, "Missing profession category");
        Set<ResourceLocation> ids = new HashSet<>(), icons = new HashSet<>(), views = new HashSet<>();
        var rows = examples();
        for (int i = 0; i < categories.size(); i++) {
            var group = SpiritRecipeGroup.values()[i];
            var category = (SpiritJeiCategory) categories.get(i);
            require(category.getRecipeType() == SpiritJeiRecipeTypes.type(group)
                    && category.getRecipeType().getUid().equals(group.id()) && ids.add(group.id()), "Unstable/duplicate category ID");
            require(((TranslatableContents) category.getTitle().getContents()).getKey().equals(group.titleKey()), "Wrong title");
            require(icons.add(group.iconId()) && BuiltInRegistries.ITEM.get(group.iconId()) != Items.AIR, "Missing/duplicate profession icon");
            require(category.getWidth() == 176 && category.getHeight() == 70, "Changed layout bounds");
            for (var row : rows) {
                boolean handled = SpiritRecipeGroup.classify(row) == group;
                require(category.isHandled(row) == handled, "Cross-profession recipe leaked into a page");
                if (!handled) continue;
                require(views.add(category.getRegistryName(row)), "Display ID collision");
                Layout layout = new Layout();
                category.setRecipe(layout.builder, row, proxy(IFocusGroup.class, (object, method, args) -> { throw unexpected(method); }));
                var slots = layout.named();
                require(slots.size() == 3, "Missing or extra slot");
                slots.get("spirit").check(RecipeIngredientRole.CATALYST, 4, 27, row.spirit());
                slots.get("input").check(RecipeIngredientRole.INPUT, 48, 27, row.input().getItems()[0]);
                slots.get("output").check(RecipeIngredientRole.OUTPUT, 148, 27, row.output());
                var tooltips = slots.get("output").tooltipKeys();
                require(tooltips.contains("jei.mekanism_magic.trade_weight") == (row.weight() > 0)
                        && tooltips.contains("jei.mekanism_magic.random_trade_no_pattern") == (row.weight() > 0), "Weighted trade warnings changed");
            }
        }
        require(SpiritJeiRecipeTypes.all().length == ids.size(), "Recipe button type count mismatch");
    }

    private static void registrationAndReload() {
        List<Call> calls = new ArrayList<>();
        var rows = examples();
        MekanismMagicJeiPlugin.registerSpiritRecipes(proxy(IRecipeRegistration.class, (object, method, args) -> {
            if (!method.getName().equals("addRecipes")) throw unexpected(method);
            calls.add(call(method, args)); return null;
        }), rows);
        require(calls.size() == SpiritRecipeGroup.values().length && calls.stream().mapToInt(call -> call.rows.size()).sum() == rows.size(), "Initial registration lost/duplicated rows");
        for (var call : calls) for (var row : call.rows) require(call.type == SpiritJeiRecipeTypes.type(SpiritRecipeGroup.classify(row)), "Recipe in wrong registered category");
        IRecipeManager manager = proxy(IRecipeManager.class, (object, method, args) -> {
            if (!List.of("addRecipes", "hideRecipes", "unhideRecipes").contains(method.getName())) throw unexpected(method);
            calls.add(call(method, args)); return null;
        });
        calls.clear();
        var reordered = new ArrayList<>(rows.stream().map(SpiritJeiGroupingSelfTest::copy).toList());
        Collections.reverse(reordered);
        var unchanged = MekanismMagicJeiPlugin.reconcileSpiritRecipes(manager, rows, reordered);
        require(calls.isEmpty() && unchanged.stream().allMatch(row -> rows.stream().anyMatch(old -> old == row)), "Unchanged reload duplicated rows");
        var oldTrade = rows.get(6);
        var changed = new ArrayList<>(rows);
        changed.set(6, row("rock", "spirit_trade", "occultism:trader_otherstone", 0));
        var regrouped = MekanismMagicJeiPlugin.reconcileSpiritRecipes(manager, unchanged, changed);
        require(calls.size() == 3 && calls.get(0).action.equals("hideRecipes") && calls.get(0).rows.getFirst() == oldTrade
                && calls.get(0).type == SpiritJeiRecipeTypes.type(SpiritRecipeGroup.OTHERROCK_TRADER)
                && calls.get(1).action.equals("addRecipes") && calls.get(1).type == SpiritJeiRecipeTypes.type(SpiritRecipeGroup.OTHERSTONE_TRADER)
                && calls.get(2).action.equals("unhideRecipes"), "Profession change must retire old page and enter new page");
        calls.clear();
        var altered = new ArrayList<>(changed);
        var output = changed.getFirst().output().copyWithCount(3);
        output.set(DataComponents.CUSTOM_NAME, Component.literal("Reloaded result"));
        altered.set(0, new SpiritJeiData(changed.getFirst().recipeId(), "crushing", Ingredient.of(Items.STONE, Items.COBBLESTONE),
                changed.getFirst().spirit().copy(), output, 0));
        MekanismMagicJeiPlugin.reconcileSpiritRecipes(manager, regrouped, altered);
        require(calls.size() == 3 && calls.stream().allMatch(call -> call.type == SpiritJeiRecipeTypes.type(SpiritRecipeGroup.CRUSHING)
                && call.rows.size() == 1), "Deep recipe changes must only replace the affected profession row");
        calls.clear();
        var cleared = MekanismMagicJeiPlugin.reconcileSpiritRecipes(manager, altered, List.of());
        require(cleared.isEmpty() && calls.stream().allMatch(call -> call.action.equals("hideRecipes"))
                && calls.stream().mapToInt(call -> call.rows.size()).sum() == rows.size(), "Removed rows linger after reload");
    }

    private static void machineButtonsUseAllGroups() throws IOException {
        for (String path : List.of("client/screen/NativeSpiritScreen", "client/screen/NativeSpiritFactoryScreen",
                "integration/mekextras/client/ExtraSpiritFactoryScreen")) {
            boolean[] allGroups = {false};
            try (var input = SpiritJeiGroupingSelfTest.class.getResourceAsStream("/com/example/mekanismmagic/" + path + ".class")) {
                require(input != null, "Missing machine GUI class " + path);
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                                if (owner.equals("com/example/mekanismmagic/client/OccultismRecipeViewerTypes") && name.equals("spiritCategories")) allGroups[0] = true;
                            }
                            @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                                require(!owner.equals("com/example/mekanismmagic/client/OccultismRecipeViewerTypes") || !name.equals("SPIRIT"), "Legacy single-category GUI route " + path);
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            require(allGroups[0], "Machine GUI cannot open profession pages: " + path);
        }
    }

    private static SpiritJeiData row(String path, String type, String job, int weight) {
        // Offline fixtures only model the native job component; real entity tiers are covered by installed GameTests.
        ItemStack spirit = new ItemStack(Items.COW_SPAWN_EGG);
        CompoundTag entity = new CompoundTag(), profession = new CompoundTag();
        entity.putString("id", "minecraft:cow");
        if (!job.isEmpty()) { profession.putString("factoryId", job); entity.put("spiritJob", profession); }
        spirit.set(DataComponents.ENTITY_DATA, CustomData.of(entity));
        return new SpiritJeiData(ResourceLocation.fromNamespaceAndPath("spirit_jei_test", path), type,
                Ingredient.of(Items.STONE), spirit, new ItemStack(Items.DIAMOND, 2), weight);
    }
    private static SpiritJeiData copy(SpiritJeiData row) {
        return new SpiritJeiData(row.recipeId(), row.recipeType(), Ingredient.of(row.input().getItems()), row.spirit().copy(), row.output().copy(), row.weight());
    }

    private static final class Layout {
        final List<Slot> slots = new ArrayList<>();
        final IRecipeLayoutBuilder builder = proxy(IRecipeLayoutBuilder.class, (object, method, args) -> {
            if (method.isDefault()) return InvocationHandler.invokeDefault(object, method, args);
            if (!method.getName().equals("addSlot")) throw unexpected(method);
            Slot slot = new Slot((RecipeIngredientRole) args[0]); slots.add(slot); return slot.builder;
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
                case "setBackground", "setOverlay" -> require(args[0] instanceof IDrawable, "Missing slot drawable");
                case "addIngredient" -> { require(args[0] == VanillaTypes.ITEM_STACK, "Wrong ingredient type"); items.add(((ItemStack) args[1]).copy()); }
                case "addIngredients" -> { require(args[0] == VanillaTypes.ITEM_STACK, "Wrong ingredient type"); for (var item : (List<?>) args[1]) items.add(((ItemStack) item).copy()); }
                case "addRichTooltipCallback" -> tooltips.add((IRecipeSlotRichTooltipCallback) args[0]);
                default -> throw unexpected(method);
            }
            return object;
        });
        Slot(RecipeIngredientRole role) { this.role = role; }
        void check(RecipeIngredientRole expected, int x, int y, ItemStack item) {
            require(role == expected && this.x == x && this.y == y && items.size() == 1 && ItemStack.matches(items.getFirst(), item), "Wrong slot: " + name);
        }
        List<String> tooltipKeys() {
            List<String> keys = new ArrayList<>();
            ITooltipBuilder tooltip = proxy(ITooltipBuilder.class, (object, method, args) -> {
                if (!method.getName().equals("add")) throw unexpected(method);
                keys.add(((TranslatableContents) ((Component) args[0]).getContents()).getKey()); return null;
            });
            for (var callback : tooltips) callback.onRichTooltip(proxy(IRecipeSlotView.class, (object, method, args) -> { throw unexpected(method); }), tooltip);
            return keys;
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
    @SuppressWarnings("unchecked")
    private static Call call(Method method, Object[] args) { return new Call(method.getName(), (RecipeType<?>) args[0], List.copyOf((Collection<SpiritJeiData>) args[1])); }
    private record Call(String action, RecipeType<?> type, List<SpiritJeiData> rows) { }
    private static AssertionError unexpected(Method method) { return new AssertionError("Unexpected API/render call: " + method); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
