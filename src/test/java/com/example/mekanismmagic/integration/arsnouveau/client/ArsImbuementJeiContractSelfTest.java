package com.example.mekanismmagic.integration.arsnouveau.client;

import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge.CatalystIdentifierJeiData;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge.IdentifierImbuementJeiData;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real setRecipe and detached registration-diff calls; no Minecraft client, draw, or JEI runtime. */
public final class ArsImbuementJeiContractSelfTest {
    private ArsImbuementJeiContractSelfTest() { }

    public static void main(String[] args) {
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        categoryInputsFollowTheActualCatalystRequirement();
        reloadsCompareComponentsAndReuseUnchangedRows();
        System.out.println("ArsImbuementJeiContractSelfTest: 2 category shapes and incremental reload contracts passed");
    }

    private static void categoryInputsFollowTheActualCatalystRequirement() {
        var category = new IdentifierImbuementJeiCategory(gui(), Items.BARRIER);
        for (boolean requiresIdentifier : List.of(false, true)) {
            var recipe = imbuement("shape", requiresIdentifier, 75);
            Layout layout = new Layout();
            category.setRecipe(layout.builder, recipe, proxy(IFocusGroup.class, (object, method, args) -> { throw unexpected(method); }));
            Map<String, Slot> slots = layout.named();
            require(slots.size() == (requiresIdentifier ? 3 : 2), "Unexpected recipe slot count");
            require(slots.values().stream().filter(slot -> slot.role == RecipeIngredientRole.INPUT).count()
                    == (requiresIdentifier ? 2 : 1), "Pedestal-free imbuement gained an extra AE input");
            if (requiresIdentifier) slots.get("catalyst_identifier").check(RecipeIngredientRole.INPUT, 10, 27, recipe.identifier());
            else require(!slots.containsKey("catalyst_identifier"), "Empty identifier created a phantom input slot");
            slots.get("input").check(RecipeIngredientRole.INPUT, 58, 27, new ItemStack(Items.AMETHYST_SHARD));
            slots.get("output").check(RecipeIngredientRole.OUTPUT, 122, 27, recipe.output());
            require(category.getRegistryName(recipe).equals(recipe.id()), "Category lost the exact recipe ID");
            for (Slot slot : slots.values()) for (IRecipeSlotRichTooltipCallback callback : slot.tooltips) {
                List<Object> lines = new ArrayList<>();
                ITooltipBuilder tooltip = proxy(ITooltipBuilder.class, (object, method, args) -> {
                    if (method.getName().equals("add")) { lines.add(args[0]); return null; }
                    throw unexpected(method);
                });
                callback.onRichTooltip(proxy(IRecipeSlotView.class, (object, method, args) -> { throw unexpected(method); }), tooltip);
                require(!lines.isEmpty() && lines.stream().allMatch(Component.class::isInstance), "Missing marker/Source tooltip text");
            }
        }
    }

    private static void reloadsCompareComponentsAndReuseUnchangedRows() {
        List<Call> calls = new ArrayList<>();
        IRecipeManager manager = proxy(IRecipeManager.class, (object, method, args) -> {
            if (!List.of("addRecipes", "hideRecipes", "unhideRecipes").contains(method.getName())) throw unexpected(method);
            calls.add(new Call(method.getName(), (RecipeType<?>) args[0], List.copyOf((Collection<?>) args[1])));
            return null;
        });
        ArsNouveauJeiIntegration.onRuntimeUnavailable();
        calls.clear();
        IdentifierImbuementJeiData free = imbuement("free", false, 0), contextual = imbuement("contextual", true, 75);
        CatalystIdentifierJeiData catalyst = catalyst();
        ArsNouveauJeiIntegration.refreshImbuementRecipes(manager, List.of(catalyst), List.of(free, contextual));
        require(calls.stream().filter(call -> call.action().equals("addRecipes")).count() == 2, "Both Ars pages must register initially");
        calls.clear();
        // Fresh ItemStack and Ingredient objects with equal contents are not changed recipes.
        ArsNouveauJeiIntegration.refreshImbuementRecipes(manager, List.of(catalyst()),
                List.of(imbuement("contextual", true, 75), imbuement("free", false, 0)));
        require(calls.isEmpty(), "Equal deep contents or reordered rows duplicated JEI registrations");

        IdentifierImbuementJeiData changedCost = imbuement("free", false, 20);
        ArsNouveauJeiIntegration.refreshImbuementRecipes(manager, List.of(catalyst()), List.of(changedCost, contextual));
        assertOnlyImbuementChanged(calls, "free");
        calls.clear();
        ItemStack changedOutput = changedCost.output().copy();
        changedOutput.set(DataComponents.CUSTOM_NAME, Component.literal("Component-aware result"));
        changedOutput.setCount(2);
        var changedComponents = new IdentifierImbuementJeiData(changedCost.id(), changedCost.input(),
                ItemStack.EMPTY, changedOutput, changedCost.sourceCost());
        ArsNouveauJeiIntegration.refreshImbuementRecipes(manager, List.of(catalyst()), List.of(changedComponents, contextual));
        assertOnlyImbuementChanged(calls, "free");
        calls.clear();
        var changedChoices = new IdentifierImbuementJeiData(changedComponents.id(), Ingredient.of(Items.AMETHYST_SHARD, Items.QUARTZ),
                ItemStack.EMPTY, changedOutput.copy(), changedComponents.sourceCost());
        ArsNouveauJeiIntegration.refreshImbuementRecipes(manager, List.of(catalyst()), List.of(changedChoices, contextual));
        assertOnlyImbuementChanged(calls, "free");
        calls.clear();
        // The unchanged remaining row stays registered while the obsolete crafting page disappears.
        ArsNouveauJeiIntegration.refreshImbuementRecipes(manager, List.of(), List.of(changedChoices));
        require(calls.size() == 2 && calls.stream().allMatch(call -> call.action().equals("hideRecipes")),
                "Removed catalysts/recipes were not hidden without re-registering the remaining row");
        ArsNouveauJeiIntegration.refreshImbuementRecipes(manager, List.of(catalyst()), List.of(changedChoices));
        calls.clear();
        ArsNouveauJeiIntegration.onRuntimeUnavailable();
        require(calls.isEmpty(), "Runtime teardown mutated the disposed recipe manager");
        ArsNouveauJeiIntegration.refreshImbuementRecipes(manager, List.of(catalyst()), List.of(changedChoices));
        require(calls.stream().filter(call -> call.action().equals("addRecipes")).count() == 2
                        && calls.stream().noneMatch(call -> call.action().equals("hideRecipes")),
                "A new JEI runtime reused recipe registrations retained from the old runtime");
        ArsNouveauJeiIntegration.onRuntimeUnavailable();
    }

    private static void assertOnlyImbuementChanged(List<Call> calls, String id) {
        require(calls.size() == 3 && calls.stream().map(Call::action).toList().equals(List.of("hideRecipes", "addRecipes", "unhideRecipes")),
                "Expected one incremental recipe replacement");
        for (Call call : calls) require(call.type() == ArsNouveauJeiIntegration.IDENTIFIER_IMBUEMENT_RECIPE_TYPE
                        && call.recipes().size() == 1 && ((IdentifierImbuementJeiData) call.recipes().getFirst()).id().equals(id(id)),
                "Unchanged catalyst or imbuement rows were re-registered");
    }

    private static CatalystIdentifierJeiData catalyst() {
        return new CatalystIdentifierJeiData(id("catalyst"), List.of(Ingredient.of(Items.PAPER)),
                List.of(id("contextual")), marker());
    }

    private static IdentifierImbuementJeiData imbuement(String id, boolean identifier, int sourceCost) {
        return new IdentifierImbuementJeiData(id(id), Ingredient.of(Items.AMETHYST_SHARD),
                identifier ? marker() : ItemStack.EMPTY, new ItemStack(Items.DIAMOND), sourceCost);
    }

    private static ItemStack marker() {
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Exact catalyst marker"));
        return stack;
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

    private record Call(String action, RecipeType<?> type, List<?> recipes) { }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("ars_jei_contract", path); }
    private static AssertionError unexpected(Method method) { return new AssertionError("Unexpected API/render call: " + method); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
