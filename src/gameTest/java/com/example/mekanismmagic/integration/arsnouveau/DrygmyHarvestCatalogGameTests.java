package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.monster.Bogged;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Exercises the reloaded catalog without replacing rules or mutating tags. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrygmyHarvestCatalogGameTests {
    private static final List<String> VANILLA_RULES = List.of(
            "minecraft_bee_honey", "minecraft_bee_honeycomb",
            "minecraft_bogged_shearing", "minecraft_chicken_egg",
            "minecraft_dragon_breath", "minecraft_frog_lights",
            "minecraft_goat_horn", "minecraft_milk",
            "minecraft_mooshroom_brown_mushrooms",
            "minecraft_mooshroom_red_mushrooms", "minecraft_mooshroom_stew",
            "minecraft_panda_sneeze", "minecraft_sheep_wool",
            "minecraft_sniffer_digging", "minecraft_sniffer_egg",
            "minecraft_turtle_egg");
    private static final List<String> EARTH_RULES = List.of(
            "earthmobs_cluck_shroom_brown_mushrooms",
            "earthmobs_cluck_shroom_red_mushrooms", "earthmobs_duck_egg",
            "earthmobs_fancy_egg", "earthmobs_horned_sheep_wool",
            "earthmobs_magma_cow_lava", "earthmobs_moobloom_flowers",
            "earthmobs_moolip_flowers", "earthmobs_moss_sheep",
            "earthmobs_muddy_pig_dye", "earthmobs_smelly_egg",
            "earthmobs_umbra_cow_wool", "earthmobs_wooly_cow_wool");
    private static final List<String> OCCULTISM_RULES = List.of(
            "occultism_beaver_nugget", "occultism_deer_datura",
            "occultism_fairy_breath", "occultism_mercy_goat_horn");

    private DrygmyHarvestCatalogGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void defaultRulesAreReloadedAndOptionalRulesStayGated(
            GameTestHelper helper) {
        helper.assertTrue(DrygmyHarvestCatalog.revision() > 0,
                "Drygmy resource reload listener never ran");
        Set<ResourceLocation> loaded = DrygmyHarvestCatalog.ruleIds();
        Set<ResourceLocation> expected = new HashSet<>();
        VANILLA_RULES.forEach(name -> expected.add(ruleId(name)));
        checkModGate(helper, loaded, expected, "earthmobsmod", EARTH_RULES);
        checkModGate(helper, loaded, expected, "occultism", OCCULTISM_RULES);
        helper.assertTrue(loaded.equals(expected)
                        && DrygmyHarvestCatalog.ruleCount() == expected.size(),
                "Default harvest catalog does not match enabled mod gates: "
                        + loaded + ", expected " + expected);
        helper.assertTrue(!loaded.contains(ruleId("minecraft_cat_gift"))
                        && !loaded.contains(ruleId("minecraft_villager_gift")),
                "Optional gifts were enabled by default");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void chickenEggsRespectAgeJockeyDeduplicationAndBatchScaling(
            GameTestHelper helper) {
        Chicken chicken = create(helper, EntityType.CHICKEN);
        Chicken duplicateType = create(helper, EntityType.CHICKEN);
        List<LivingEntity> adults = List.of(chicken, duplicateType, chicken);
        CompoundTag before = snapshot(chicken);
        for (int multiplier : new int[]{0, 1, 4, 256, 257, Integer.MAX_VALUE}) {
            var harvest = collect(helper, adults, List.of(), multiplier, false);
            long expected = Math.clamp(multiplier, 1, 256);
            assertItemCount(helper, harvest.items(), Items.EGG, expected);
            helper.assertTrue(harvest.items().size() == 1
                            && harvest.fluids().isEmpty(),
                    "Chicken harvest duplicated stacks or produced fluid");
        }
        helper.assertTrue(snapshot(chicken).equals(before),
                "Catalog harvesting changed the captured chicken");
        chicken.setAge(-24000);
        assertEmpty(helper, collect(helper, List.of(chicken), List.of(), 4, false),
                "Baby chickens must not lay eggs");
        chicken.setAge(0);
        chicken.setChickenJockey(true);
        assertEmpty(helper, collect(helper, List.of(chicken), List.of(), 4, false),
                "Chicken jockeys must not lay eggs");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void existingCandidatesSuppressOnlyTheSameItemComponents(
            GameTestHelper helper) {
        Chicken chicken = create(helper, EntityType.CHICKEN);
        ItemStack existing = new ItemStack(Items.EGG, 12);
        List<ItemStack> ordinary = List.of(existing,
                new ItemStack(Items.FEATHER, 3));
        var skipped = collect(helper, List.of(chicken), ordinary, 16, false);
        assertEmpty(helper, skipped,
                "An ordinary egg candidate received a duplicate live output");
        helper.assertTrue(existing.getCount() == 12
                        && ordinary.size() == 2,
                "Catalog mutated the caller's ordinary candidates");
        ItemStack named = new ItemStack(Items.EGG);
        named.set(DataComponents.CUSTOM_NAME,
                Component.literal("Different egg components"));
        var distinct = collect(helper, List.of(chicken), List.of(named), 4, false);
        assertItemCount(helper, distinct.items(), Items.EGG, 4);
        helper.assertTrue(!distinct.items().getFirst()
                        .has(DataComponents.CUSTOM_NAME),
                "An ordinary candidate's custom components leaked into eggs");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void giftAndShearingTablesUseLegalLootContexts(
            GameTestHelper helper) {
        Sniffer sniffer = create(helper, EntityType.SNIFFER);
        List<ItemStack> seeds = DrygmyHarvestCatalog.sampleTable(helper.getLevel(),
                sniffer, vanillaId("gameplay/sniffer_digging"));
        helper.assertTrue(total(seeds) == 1 && seeds.stream().allMatch(stack ->
                        stack.is(Items.TORCHFLOWER_SEEDS)
                                || stack.is(Items.PITCHER_POD)),
                "Sniffer gift table did not return one valid seed");
        var seedHarvest = collect(helper, List.of(sniffer), List.of(), 8, false);
        helper.assertTrue(total(seedHarvest.items()) == 8
                        && seedHarvest.items().stream().allMatch(stack ->
                        stack.is(Items.TORCHFLOWER_SEEDS)
                                || stack.is(Items.PITCHER_POD)),
                "Catalog failed to scale the sniffer's native gift output");
        Panda panda = create(helper, EntityType.PANDA);
        panda.setAge(-24000);
        // An empty sneeze is the normal result (699/700), never a failed test.
        List<ItemStack> sneeze = DrygmyHarvestCatalog.sampleTable(helper.getLevel(),
                panda, vanillaId("gameplay/panda_sneeze"));
        helper.assertTrue(sneeze.stream().allMatch(stack ->
                        stack.is(Items.SLIME_BALL)) && total(sneeze) <= 1,
                "Panda sneeze returned an item outside its native table");
        var pandaHarvest = collect(helper, List.of(panda), List.of(), 8, false);
        helper.assertTrue(pandaHarvest.items().stream().allMatch(stack ->
                        stack.is(Items.SLIME_BALL))
                        && (total(pandaHarvest.items()) == 0
                        || total(pandaHarvest.items()) == 8),
                "Catalog changed the panda table's empty-result semantics");
        panda.setAge(0);
        assertEmpty(helper, collect(helper, List.of(panda), List.of(), 8, false),
                "Adult panda matched the baby-only sneeze rule");
        Bogged bogged = create(helper, EntityType.BOGGED);
        List<ItemStack> mushrooms = DrygmyHarvestCatalog.sampleTable(
                helper.getLevel(), bogged, vanillaId("shearing/bogged"));
        helper.assertTrue(total(mushrooms) == 2
                        && mushrooms.stream().allMatch(
                                DrygmyHarvestCatalogGameTests::isMushroom),
                "Bogged shearing context failed to preserve two rolls");
        var boggedHarvest = collect(helper, List.of(bogged), List.of(), 4, false);
        helper.assertTrue(total(boggedHarvest.items()) == 8
                        && boggedHarvest.items().stream().allMatch(
                                DrygmyHarvestCatalogGameTests::isMushroom),
                "Catalog failed to load or scale bogged shearing");
        bogged.setSheared(true);
        assertEmpty(helper, collect(helper, List.of(bogged), List.of(), 4, false),
                "Lowercase bogged sheared NBT predicate was not enforced");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void milkRequiresFluidOutputAndNeverCreatesContainers(
            GameTestHelper helper) {
        Cow cow = create(helper, EntityType.COW);
        Cow sameType = create(helper, EntityType.COW);
        List<LivingEntity> cows = List.of(cow, sameType);
        assertEmpty(helper, collect(helper, cows, List.of(), 8, false),
                "Disconnected fluid output produced milk or a milk container");
        var milk = BuiltInRegistries.FLUID.getOptional(vanillaId("milk"))
                .orElseThrow(() -> new IllegalStateException(
                        "Expected registered minecraft:milk fluid"));
        var harvest = collect(helper, cows, List.of(), 8, true);
        helper.assertTrue(harvest.items().isEmpty()
                        && harvest.fluids().size() == 1,
                "Connected milk harvesting must create only one fluid stack");
        FluidStack output = harvest.fluids().getFirst();
        helper.assertTrue(output.getFluid() == milk && output.getAmount() == 8000,
                "Milk fluid identity, type deduplication or batch scaling failed");
        cow.setAge(-24000);
        assertEmpty(helper, collect(helper, List.of(cow), List.of(), 8, true),
                "Baby cow produced milk through the fluid rule");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void productiveBeesHoneyUsesRegisteredSourceFluid(
            GameTestHelper helper) {
        Bee bee = create(helper, EntityType.BEE);
        var disconnected = collect(helper, List.of(bee), List.of(), 4, false);
        helper.assertTrue(disconnected.fluids().isEmpty()
                        && disconnected.items().stream().noneMatch(stack ->
                        stack.is(Items.HONEY_BOTTLE) || stack.is(Items.MILK_BUCKET)),
                "Disconnected bee harvest produced a fluid container");
        assertItemCount(helper, disconnected.items(), Items.HONEYCOMB, 12);
        if (ModCompatibility.loaded("productivebees")) {
            var honey = BuiltInRegistries.FLUID.getOptional(
                            ResourceLocation.parse("productivebees:honey"))
                    .orElseThrow(() -> new IllegalStateException(
                            "Productive Bees honey source fluid is missing"));
            var connected = collect(helper, List.of(bee), List.of(), 4, true);
            helper.assertTrue(honey != Fluids.EMPTY
                            && connected.fluids().size() == 1
                            && connected.fluids().getFirst().getFluid() == honey
                            && connected.fluids().getFirst().getAmount() == 1000,
                    "Bee honey did not resolve to 1000 mB of Productive Bees honey");
            helper.assertTrue(connected.items().stream().noneMatch(stack ->
                            stack.is(Items.HONEY_BOTTLE)),
                    "Real honey fluid was accompanied by a free bottle");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void optionalEarthEggsAndShearingRetainTheirOwnVariants(
            GameTestHelper helper) {
        if (!ModCompatibility.loaded("earthmobsmod")) {
            helper.assertTrue(EARTH_RULES.stream().noneMatch(name ->
                            DrygmyHarvestCatalog.ruleIds().contains(ruleId(name))),
                    "Earth harvest rules loaded without Earth Mobs");
            helper.succeed();
            return;
        }
        for (String[] pair : List.of(new String[]{"duck", "duck_egg"},
                new String[]{"fancy_chicken", "fancy_egg"},
                new String[]{"cluck_shroom", "smelly_egg"})) {
            LivingEntity bird = create(helper,
                    ResourceLocation.parse("earthmobsmod:" + pair[0]));
            Item egg = BuiltInRegistries.ITEM.getOptional(
                            ResourceLocation.parse("earthmobsmod:" + pair[1]))
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing Earth egg " + pair[1]));
            var harvest = collect(helper, List.of(bird), List.of(), 4, false);
            assertItemCount(helper, harvest.items(), egg, 4);
            assertItemCount(helper, harvest.items(), Items.EGG, 0);
        }
        Sheep mossSheep = create(helper, EntityType.SHEEP);
        CompoundTag sheepData = snapshot(mossSheep);
        sheepData.putBoolean("Moss", true);
        sheepData.putBoolean("Sheared", false);
        mossSheep.load(sheepData.copy());
        assertItemCount(helper, collect(helper, List.of(mossSheep), List.of(),
                4, false).items(), Items.MOSS_BLOCK, 4);
        mossSheep.setSheared(true);
        assertItemCount(helper, collect(helper, List.of(mossSheep), List.of(),
                4, false).items(), Items.MOSS_BLOCK, 0);
        Pig pig = create(helper, EntityType.PIG);
        CompoundTag pigData = snapshot(pig);
        pigData.putBoolean("Muddy", true);
        pigData.putBoolean("Sheared", false);
        pigData.putByte("Color", (byte) 9);
        pig.load(pigData.copy());
        CompoundTag beforePig = snapshot(pig);
        var dye = collect(helper, List.of(pig), List.of(), 4, false);
        long count = itemCount(dye.items(), Items.CYAN_DYE);
        helper.assertTrue(count >= 4 && count <= 12 && count % 4 == 0
                        && dye.items().stream().allMatch(stack ->
                        stack.is(Items.CYAN_DYE)),
                "Muddy pig Color or explicit 1-3 count override was ignored");
        helper.assertTrue(snapshot(pig).equals(beforePig),
                "Catalog modified the muddy pig while collecting dye");
        pigData.putBoolean("Muddy", false);
        pig.load(pigData.copy());
        assertEmpty(helper, collect(helper, List.of(pig), List.of(), 4, false),
                "Clean pig still matched the muddy dye rule");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void authorDefinitionsValidateBoundsAndPreserveOverrides(
            GameTestHelper helper) throws ReflectiveOperationException {
        for (String invalid : List.of(
                "{\"entities\":[],\"kind\":\"item\",\"item\":\"minecraft:egg\"}",
                "{\"entities\":[\"minecraft:chicken\"],\"kind\":\"java_class\"}",
                "{\"entities\":[\"minecraft:chicken\"],\"kind\":\"item\"}",
                "{\"entities\":[\"minecraft:cow\"],\"kind\":\"fluid\"}",
                "{\"entities\":[\"minecraft:chicken\"],\"kind\":\"item\",\"item\":\"minecraft:egg\",\"count\":65}",
                "{\"entities\":[\"minecraft:chicken\"],\"kind\":\"item\",\"item\":\"minecraft:egg\",\"count\":{\"min\":3,\"max\":1}}",
                "{\"entities\":[\"minecraft:chicken\"],\"kind\":\"item\",\"item\":\"minecraft:egg\",\"chance\":-0.1}",
                "{\"entities\":[\"minecraft:cow\"],\"kind\":\"fluid\",\"fluid\":\"minecraft:milk\",\"amount\":0}",
                "{\"entities\":[\"minecraft:chicken\"],\"kind\":\"item\",\"item\":\"minecraft:egg\",\"adult\":true,\"baby\":true}")) {
            try {
                parseRule(invalid);
                helper.assertTrue(false, "Invalid harvest definition accepted: " + invalid);
            } catch (InvocationTargetException failure) {
                helper.assertTrue(failure.getCause() instanceof IllegalArgumentException,
                        "Invalid definition failed for an unrelated reason: " + failure);
            }
        }
        long revision = DrygmyHarvestCatalog.revision();
        Set<ResourceLocation> loaded = DrygmyHarvestCatalog.ruleIds();
        Object override = parseRule("""
                {"entities":["minecraft:pig"],"kind":"colored_dye",
                 "count":{"min":1,"max":3},"chance":0,"adult":true,
                 "nbt":{"Muddy":true,"Sheared":false},
                 "variant_keys":["Color","VillagerData.profession"]}
                """);
        helper.assertTrue(field(override, "kind").equals("colored_dye")
                        && field(override, "overrideCount").equals(true)
                        && field(override, "minCount").equals(1)
                        && field(override, "maxCount").equals(3)
                        && field(override, "chance").equals(0.0)
                        && field(override, "variantKeys").equals(
                        List.of("Color", "VillagerData.profession")),
                "Author-defined behavior/count/chance/variant overrides were lost");
        CompoundTag nbt = (CompoundTag) field(override, "nbt");
        helper.assertTrue(nbt.getBoolean("Muddy") && !nbt.getBoolean("Sheared"),
                "JSON boolean predicates did not parse as matching NBT bytes");
        Object typed = parseRule("""
                {"entities":["minecraft:pig"],"kind":"colored_dye",
                 "nbt":"{Muddy:true,Color:9b}"}
                """);
        CompoundTag typedNbt = (CompoundTag) field(typed, "nbt");
        CompoundTag savedColor = new CompoundTag();
        savedColor.putBoolean("Muddy", true);
        savedColor.putByte("Color", (byte) 9);
        helper.assertTrue(typedNbt.contains("Color", Tag.TAG_BYTE)
                        && NbtUtils.compareNbt(typedNbt, savedColor, true),
                "Typed SNBT predicate failed to match an entity's byte-valued color");
        savedColor.putByte("Color", (byte) 10);
        helper.assertTrue(!NbtUtils.compareNbt(typedNbt, savedColor, true),
                "Typed SNBT color predicate matched a different variant");
        helper.assertTrue(DrygmyHarvestCatalog.revision() == revision
                        && DrygmyHarvestCatalog.ruleIds().equals(loaded),
                "Pure schema parsing changed the reloaded catalog");
        helper.succeed();
    }

    private static void checkModGate(GameTestHelper helper,
                                     Set<ResourceLocation> loaded,
                                     Set<ResourceLocation> expected,
                                     String mod, List<String> names) {
        boolean enabled = ModCompatibility.loaded(mod);
        for (String name : names) {
            ResourceLocation id = ruleId(name);
            helper.assertTrue(loaded.contains(id) == enabled,
                    "Wrong required_mods gate for " + id);
            if (enabled) {
                expected.add(id);
            }
        }
    }

    private static Object parseRule(String json) throws ReflectiveOperationException {
        Method method = DrygmyHarvestCatalog.class.getDeclaredMethod("parse",
                ResourceLocation.class, JsonObject.class);
        method.setAccessible(true);
        return method.invoke(null, ruleId("author_schema_test"),
                JsonParser.parseString(json).getAsJsonObject());
    }

    private static Object field(Object rule, String name)
            throws ReflectiveOperationException {
        Method accessor = rule.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return accessor.invoke(rule);
    }

    private static DrygmyHarvestCatalog.Harvest collect(GameTestHelper helper,
            List<LivingEntity> entities, List<ItemStack> ordinary,
            int multiplier, boolean fluids) {
        return DrygmyHarvestCatalog.collect(helper.getLevel(), entities,
                ordinary, multiplier, fluids);
    }

    private static void assertEmpty(GameTestHelper helper,
            DrygmyHarvestCatalog.Harvest harvest, String message) {
        helper.assertTrue(harvest.items().isEmpty() && harvest.fluids().isEmpty(),
                message + ": " + harvest);
    }

    private static void assertItemCount(GameTestHelper helper,
            List<ItemStack> stacks, Item item, long expected) {
        long actual = itemCount(stacks, item);
        helper.assertTrue(actual == expected,
                "Expected " + expected + " " + BuiltInRegistries.ITEM.getKey(item)
                        + ", got " + actual + " in " + stacks);
    }

    private static long itemCount(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(stack -> stack.is(item))
                .mapToLong(ItemStack::getCount).sum();
    }

    private static long total(List<ItemStack> stacks) {
        return stacks.stream().mapToLong(ItemStack::getCount).sum();
    }

    private static boolean isMushroom(ItemStack stack) {
        return stack.is(Items.RED_MUSHROOM) || stack.is(Items.BROWN_MUSHROOM);
    }

    private static ResourceLocation ruleId(String name) {
        return ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, name);
    }

    private static ResourceLocation vanillaId(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static CompoundTag snapshot(LivingEntity entity) {
        return entity.saveWithoutId(new CompoundTag());
    }

    private static <T extends LivingEntity> T create(
            GameTestHelper helper, EntityType<T> type) {
        T entity = type.create(helper.getLevel());
        if (entity == null) {
            throw new IllegalStateException("Cannot create test entity " + type);
        }
        return entity;
    }

    private static LivingEntity create(GameTestHelper helper, ResourceLocation id) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Expected registered entity " + id));
        Entity entity = type.create(helper.getLevel());
        if (!(entity instanceof LivingEntity living)) {
            throw new IllegalStateException("Expected living entity " + id);
        }
        return living;
    }
}
