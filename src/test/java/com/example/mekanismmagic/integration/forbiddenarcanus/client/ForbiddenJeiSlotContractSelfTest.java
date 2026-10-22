package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.*;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenRecipeDisplayCatalog.*;
import com.example.mekanismmagic.integration.forbiddenarcanus.EssencePreparationDisplay;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.builder.IIngredientConsumer;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotRichTooltipCallback;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static mezz.jei.api.recipe.RecipeIngredientRole.*;

/** Calls real category constructors/setRecipe with recording API proxies, never draw or JEI runtime. */
public final class ForbiddenJeiSlotContractSelfTest {
    private ForbiddenJeiSlotContractSelfTest() { }

    public static void main(String[] args) {
        // Vanilla-only fixture in its own JavaExec process. NeoForge's patched
        // feature-flag bootstrap expects a discovery list even when there are no mods.
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        clibanoSingleAndDoubleInputs();
        forgeCreateTransmuteAndUpgradeRoles();
        independentlyFilteredSlotsStayPaired();
        essencePreparationShowsOnlyRetainedSamples();
        System.out.println("ForbiddenJeiSlotContractSelfTest: retained setRecipe scenarios and display-pair filtering passed (API contract only)");
    }

    private static void essencePreparationShowsOnlyRetainedSamples() {
        for (var kind : EssencePreparationDisplay.Kind.values()) {
            ItemStack sample = stack(Items.BARRIER);
            var display = new EssencePreparationDisplay(kind, sample, 25, 35, 50);
            var category = new EssencePreparationJeiCategory(gui(), Items.BARRIER);
            Layout layout = new Layout();
            category.setRecipe(layout.builder, display, noFocus());
            require(category.isHandled(display), "Condensing guide rejected a retained sample");
            layout.slot("sample", CATALYST, 29, 24).stacks(sample);
            require(layout.slots.stream().noneMatch(slot -> slot.role == INPUT),
                    "Retained sample or vessel was misreported as a consumed item input");
            layout.verify(category.getWidth(), category.getHeight());
        }
    }

    private static void independentlyFilteredSlotsStayPaired() {
        ItemStack sword = stack(Items.IRON_SWORD);
        ItemStack helmet = stack(Items.IRON_HELMET);
        ItemStack swordResult = stack(Items.DIAMOND_SWORD);
        ItemStack helmetResult = stack(Items.DIAMOND_HELMET);
        List<TransformExample> pairs = List.of(new TransformExample(sword, swordResult), new TransformExample(helmet, helmetResult));
        var input = new DisplaySlot("main_material", helmet);
        var output = new DisplaySlot("result", swordResult); // Simulates independently filtered lists selecting different indices.
        List<IRecipeSlotDrawable> slots = List.of(input.drawable, output.drawable);
        ForbiddenJeiCategory.synchronizeDisplayedPair(slots, pairs, "main_material", "result", stack -> true);
        require(output.overrides.size() == 1 && ItemStack.matches(output.overrides.getFirst(), helmetResult),
                "Visible helmet was paired with independently cycling sword output");
        input.displayed = sword;
        ForbiddenJeiCategory.synchronizeDisplayedPair(slots, pairs, "main_material", "result", stack -> true);
        require(output.overrides.size() == 1 && ItemStack.matches(output.overrides.getFirst(), swordResult),
                "Next JEI cycle retained a stale output override");
        ForbiddenJeiCategory.synchronizeDisplayedPair(slots, pairs, "main_material", "result", stack -> false);
        require(output.overrides.isEmpty(), "Paired output ignored the user's hidden-item setting");
        input.displayed = ItemStack.EMPTY;
        ForbiddenJeiCategory.synchronizeDisplayedPair(slots, pairs, "main_material", "result", stack -> true);
        require(output.overrides.isEmpty(), "Empty displayed input left an unrelated result visible");
    }

    private static final class DisplaySlot {
        final String name;
        ItemStack displayed;
        final List<ItemStack> overrides = new ArrayList<>();
        final IIngredientConsumer consumer = proxy(IIngredientConsumer.class, (object, method, args) -> {
            if (method.isDefault()) return InvocationHandler.invokeDefault(object, method, args);
            if (method.getName().equals("addIngredients")) {
                require(args[0] == VanillaTypes.ITEM_STACK, "Display override changed ingredient type");
                for (var stack : (List<?>) args[1]) overrides.add(((ItemStack) stack).copy());
                return object;
            }
            throw unsupported(method);
        });
        final IRecipeSlotDrawable drawable;

        DisplaySlot(String name, ItemStack displayed) {
            this.name = name;
            this.displayed = displayed;
            drawable = proxy(IRecipeSlotDrawable.class, (object, method, args) -> switch (method.getName()) {
                case "getSlotName" -> Optional.of(this.name);
                case "getDisplayedItemStack" -> this.displayed.isEmpty() ? Optional.empty() : Optional.of(this.displayed.copy());
                case "clearDisplayOverrides" -> { overrides.clear(); yield null; }
                case "createDisplayOverrides" -> consumer;
                default -> throw unsupported(method);
            });
        }
    }

    private static void clibanoSingleAndDoubleInputs() {
        var category = new ClibanoJeiCategory(gui(), Items.BARRIER);
        for (boolean doubleInput : List.of(false, true)) {
            var ingredients = doubleInput ? List.of(ingredient(Items.IRON_INGOT), ingredient(Items.COPPER_INGOT))
                    : List.of(ingredient(Items.IRON_INGOT));
            var recipe = new ClibanoRecipeView(id("clibano"), doubleInput, ingredients,
                    new CookingTimes(200, 100, 50), FireType.SOUL_FIRE, Optional.of(id("enhancer")),
                    Optional.of(new ResidueChanceView(id("residue"), 0.125)), 0.5F, stack(Items.GOLD_INGOT, 2));
            var display = new ClibanoDisplay(recipe, List.of(stack(Items.AMETHYST_SHARD)),
                    Optional.of(new ResidueView(id("residue"), 3, stack(Items.GOLD_NUGGET, 2))),
                    Map.of(FireType.FIRE, List.of(stack(Items.COAL)),
                            FireType.SOUL_FIRE, List.of(stack(Items.SOUL_SAND)),
                            FireType.ENCHANTED_FIRE, List.of(stack(Items.BLAZE_POWDER))), "fixture");
            require(category.isHandled(display), "Valid Clibano fixture was rejected");
            Layout layout = new Layout();
            category.setRecipe(layout.builder, display, noFocus());
            layout.slot("material_0", INPUT, 16, 23).stacks(stack(Items.IRON_INGOT));
            if (doubleInput) layout.slot("material_1", INPUT, 40, 23).stacks(stack(Items.COPPER_INGOT));
            else require(!layout.named().containsKey("material_1"), "Single-input Clibano created a second input");
            layout.slot("result", OUTPUT, 138, 23).stacks(stack(Items.GOLD_INGOT, 2));
            layout.slot("fire_medium", CATALYST, 16, 62).stacks(stack(Items.SOUL_SAND), stack(Items.BLAZE_POWDER));
            layout.slot("enhancer", CATALYST, 64, 62).stacks(stack(Items.AMETHYST_SHARD));
            layout.slot("residue_combination", RENDER_ONLY, 138, 62).stacks(stack(Items.GOLD_NUGGET, 2));
            require(layout.shapeless == doubleInput && layout.links.isEmpty(), "Wrong Clibano shape/focus contract");
            layout.verify(category.getWidth(), category.getHeight());
            require(!category.isHandled(new ClibanoDisplay(recipe, List.of(), display.residue(),
                    display.fireMedia(), "missing enhancer")), "Empty required enhancer tag was accepted");
        }
    }

    private static void forgeCreateTransmuteAndUpgradeRoles() {
        var category = new HephaestusForgeJeiCategory(gui(), Items.BARRIER);
        for (String mode : List.of("create_item", "transmute", "upgrade_tier")) {
            boolean upgrade = mode.equals("upgrade_tier");
            List<TransformExample> examples = switch (mode) {
                case "create_item" -> List.of(new TransformExample(stack(Items.IRON_INGOT), stack(Items.GOLD_INGOT, 2)),
                        new TransformExample(stack(Items.COPPER_INGOT), stack(Items.GOLD_INGOT, 2)));
                case "transmute" -> List.of(new TransformExample(stack(Items.IRON_SWORD), stack(Items.DIAMOND_SWORD)),
                        new TransformExample(stack(Items.IRON_HELMET), stack(Items.DIAMOND_HELMET)));
                default -> List.of(new TransformExample(stack(Items.NETHERITE_INGOT), ItemStack.EMPTY));
            };
            var main = upgrade ? ingredient(Items.NETHERITE_INGOT)
                    : new IngredientView(examples.stream().map(TransformExample::input).toList());
            var recipe = new RitualView(id(mode), main, List.of(new RitualIngredient(ingredient(Items.REDSTONE), 3)),
                    new EssenceRequirements(10, 20, 30, 40), 1, true, List.of(id("enhancer")),
                    id("circle"), 200, id(mode));
            var display = new RitualDisplay(recipe, examples, List.of(1),
                    List.of(List.of(stack(Items.AMETHYST_SHARD))), upgrade ? 2 : 0, "fixture");
            require(category.isHandled(display), "Valid forge fixture was rejected: " + mode);
            Layout layout = new Layout();
            category.setRecipe(layout.builder, display, noFocus());
            layout.slot("main_material", INPUT, 12, 22).stacks(main.examples().toArray(ItemStack[]::new));
            layout.slot("auxiliary_0", INPUT, 46, 22).stacks(stack(Items.REDSTONE, 3));
            layout.slot("enhancer_0", CATALYST, 20, 82).stacks(stack(Items.AMETHYST_SHARD));
            if (upgrade) {
                require(layout.slots.stream().noneMatch(slot -> slot.role == OUTPUT),
                        "Machine-tier upgrade invented an output-slot item");
            } else {
                layout.slot("result", OUTPUT, 146, 32).stacks(examples.stream().map(TransformExample::output).toArray(ItemStack[]::new));
                layout.pairs("main_material", "result", examples);
            }
            layout.verify(category.getWidth(), category.getHeight());
            require(layout.slots.stream().noneMatch(slot -> slot.name != null
                            && (slot.name.equals("core") || slot.name.equals("starter")
                            || slot.name.equals("installed_core"))),
                    "Removed core or hammer was shown in the ritual layout");
            require(!category.isHandled(new RitualDisplay(recipe, examples, List.of(), display.enhancers(),
                    display.upgradeTier(), "empty machine tiers")), "No valid machine tier was accepted");
            require(!category.isHandled(new RitualDisplay(recipe, examples, display.supportedTiers(), List.of(List.of()),
                    display.upgradeTier(), "empty enhancer")), "Empty enhancer tag was accepted");
            if (upgrade) {
                require(!category.isHandled(new RitualDisplay(recipe,
                                List.of(new TransformExample(stack(Items.NETHERITE_INGOT), stack(Items.DIAMOND))),
                                List.of(1), display.enhancers(), 2, "invented output")),
                        "Upgrade display accepted a fake return item");
            }
        }
    }

    private static final class Layout {
        final List<Slot> slots = new ArrayList<>();
        final List<List<Slot>> links = new ArrayList<>();
        final Map<Object, Slot> byProxy = new IdentityHashMap<>();
        boolean shapeless;
        final IRecipeLayoutBuilder builder = proxy(IRecipeLayoutBuilder.class, (object, method, args) -> {
            if (method.isDefault()) return InvocationHandler.invokeDefault(object, method, args);
            return switch (method.getName()) {
                case "addSlot" -> {
                    Slot slot = new Slot((RecipeIngredientRole) args[0]);
                    slots.add(slot);
                    byProxy.put(slot.builder, slot);
                    yield slot.builder;
                }
                case "setShapeless" -> { shapeless = true; yield null; }
                case "createFocusLink" -> {
                    List<Slot> linked = new ArrayList<>();
                    for (Object slot : (Object[]) args[0]) {
                        require(byProxy.containsKey(slot), "Focus link used an unknown slot");
                        linked.add(byProxy.get(slot));
                    }
                    require(linked.size() >= 2 && linked.stream().map(slot -> slot.items.size()).distinct().count() == 1,
                            "Focus-linked slots must have equal ingredient counts");
                    links.add(List.copyOf(linked));
                    yield null;
                }
                default -> throw unsupported(method);
            };
        });

        Map<String, Slot> named() {
            Map<String, Slot> names = new LinkedHashMap<>();
            for (Slot slot : slots) require(slot.name != null && names.put(slot.name, slot) == null, "Missing/duplicate slot name");
            return names;
        }

        Slot slot(String name, RecipeIngredientRole role, int x, int y) {
            Slot slot = named().get(name);
            require(slot != null && slot.role == role && slot.x == x && slot.y == y, "Wrong role/position for " + name);
            return slot;
        }

        void pairs(String from, String to, List<TransformExample> examples) {
            Slot input = named().get(from);
            Slot output = named().get(to);
            require(links.size() == 1 && links.getFirst().equals(List.of(input, output)), "Wrong focus-linked slot pair");
            input.stacks(examples.stream().map(TransformExample::input).toArray(ItemStack[]::new));
            output.stacks(examples.stream().map(TransformExample::output).toArray(ItemStack[]::new));
        }

        void verify(int width, int height) {
            named();
            for (Slot slot : slots) {
                require(slot.x >= 0 && slot.y >= 0 && slot.x + 16 <= width && slot.y + 16 <= height, "Slot outside category bounds");
                for (IRecipeSlotRichTooltipCallback callback : slot.tooltips) {
                    List<Object> text = new ArrayList<>();
                    ITooltipBuilder tooltip = proxy(ITooltipBuilder.class, (object, method, args) -> {
                        if (method.getName().equals("add")) { text.add(args[0]); return null; }
                        throw unsupported(method);
                    });
                    IRecipeSlotView view = proxy(IRecipeSlotView.class, (object, method, args) -> switch (method.getName()) {
                        case "getRole" -> slot.role;
                        case "getSlotName" -> Optional.of(slot.name);
                        default -> throw unsupported(method);
                    });
                    callback.onRichTooltip(view, tooltip);
                    require(!text.isEmpty() && text.stream().allMatch(Component.class::isInstance), "Rich tooltip callback emitted no text");
                }
            }
        }
    }

    private static final class Slot {
        final RecipeIngredientRole role;
        final List<ItemStack> items = new ArrayList<>();
        final List<IRecipeSlotRichTooltipCallback> tooltips = new ArrayList<>();
        String name;
        int x = -1;
        int y = -1;
        final IRecipeSlotBuilder builder = proxy(IRecipeSlotBuilder.class, (object, method, args) -> {
            if (method.isDefault()) return InvocationHandler.invokeDefault(object, method, args);
            switch (method.getName()) {
                case "setPosition" -> { x = (int) args[0]; y = (int) args[1]; }
                case "setSlotName" -> name = (String) args[0];
                case "setBackground" -> require(args[0] instanceof IDrawableStatic && (int) args[1] == -1 && (int) args[2] == -1,
                        "Unexpected themed slot background");
                case "addIngredients" -> {
                    require(args[0] == VanillaTypes.ITEM_STACK, "Unexpected ingredient type");
                    for (Object stack : (List<?>) args[1]) items.add(((ItemStack) stack).copy());
                }
                case "addIngredient" -> {
                    require(args[0] == VanillaTypes.ITEM_STACK, "Unexpected ingredient type");
                    items.add(((ItemStack) args[1]).copy());
                }
                case "addRichTooltipCallback" -> tooltips.add((IRecipeSlotRichTooltipCallback) args[0]);
                default -> throw unsupported(method);
            }
            return object;
        });

        Slot(RecipeIngredientRole role) { this.role = role; }

        void stacks(ItemStack... expected) {
            require(items.size() == expected.length, "Wrong ingredient count in " + name);
            for (int index = 0; index < expected.length; index++) {
                require(ItemStack.matches(items.get(index), expected[index]), "Wrong item/count/components at " + name + "[" + index + "]");
            }
        }
    }

    private static IGuiHelper gui() {
        return proxy(IGuiHelper.class, (object, method, args) -> switch (method.getName()) {
            case "createDrawable" -> drawable((int) args[3], (int) args[4]);
            case "createBlankDrawable" -> drawable((int) args[0], (int) args[1]);
            case "createDrawableItemLike", "createDrawableItemStack", "createDrawableIngredient" -> drawable(16, 16);
            default -> throw unsupported(method);
        });
    }

    private static IDrawableStatic drawable(int width, int height) {
        return proxy(IDrawableStatic.class, (object, method, args) -> switch (method.getName()) {
            case "getWidth" -> width;
            case "getHeight" -> height;
            default -> throw new AssertionError("Drawable/rendering method must not run in this contract test: " + method);
        });
    }

    private static IFocusGroup noFocus() {
        return proxy(IFocusGroup.class, (object, method, args) -> { throw unsupported(method); });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (object, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "equals" -> object == args[0];
                case "hashCode" -> System.identityHashCode(object);
                case "toString" -> "Recording " + type.getSimpleName();
                default -> throw unsupported(method);
            };
            return handler.invoke(object, method, args == null ? new Object[0] : args);
        }));
    }

    private static AssertionError unsupported(Method method) { return new AssertionError("Unexpected API call: " + method); }
    private static IngredientView ingredient(Item item) { return new IngredientView(List.of(stack(item))); }
    private static ItemStack stack(Item item) { return new ItemStack(item); }
    private static ItemStack stack(Item item, int count) { return new ItemStack(item, count); }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("slot_contract", path); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
