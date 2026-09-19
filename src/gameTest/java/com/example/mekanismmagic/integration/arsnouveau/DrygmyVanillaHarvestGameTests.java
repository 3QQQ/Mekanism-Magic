package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MekanismMagic;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Real registry, component and loot-context checks for virtual harvesting. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrygmyVanillaHarvestGameTests {
    private DrygmyVanillaHarvestGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void hornsRetainInstrumentAndEntityState(GameTestHelper helper) {
        Goat goat = create(helper, EntityType.GOAT);
        List<LivingEntity> group = List.of(goat);
        CompoundTag before = snapshot(goat);
        ItemStack first = single(helper, "goat_horn", goat, group);
        ItemStack second = single(helper, "goat_horn", goat, group);
        helper.assertTrue(first.is(Items.GOAT_HORN)
                        && first.has(DataComponents.INSTRUMENT)
                        && ItemStack.isSameItemSameComponents(first, second),
                "Repeated virtual horns lost their UUID-selected instrument");
        helper.assertTrue(snapshot(goat).equals(before),
                "Virtual horn harvesting changed the captured goat");
        goat.removeHorns();
        assertIneligible(helper, "goat_horn", goat, group);
        goat.addHorns();
        goat.setAge(-24000);
        assertIneligible(helper, "goat_horn", goat, group);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void stewPreservesStoredEffectsWithoutConsumingThem(
            GameTestHelper helper) {
        MushroomCow cow = create(helper, EntityType.MOOSHROOM);
        CompoundTag input = snapshot(cow);
        input.putString("Type", "brown");
        CompoundTag effect = new CompoundTag();
        effect.putString("id", "minecraft:night_vision");
        effect.putInt("duration", 160);
        ListTag effects = new ListTag();
        effects.add(effect);
        input.put("stew_effects", effects);
        cow.load(input.copy());
        CompoundTag before = snapshot(cow);
        ItemStack stew = single(helper, "mooshroom_stew", cow, List.of(cow));
        var actual = stew.get(DataComponents.SUSPICIOUS_STEW_EFFECTS);
        helper.assertTrue(stew.is(Items.SUSPICIOUS_STEW) && actual != null
                        && actual.effects().size() == 1
                        && actual.effects().getFirst().effect()
                                .equals(MobEffects.NIGHT_VISION)
                        && actual.effects().getFirst().duration() == 160,
                "Suspicious stew lost its stored effect or duration");
        helper.assertTrue(snapshot(cow).equals(before),
                "Virtual milking consumed the captured mooshroom's effects");
        // Native lightning conversion preserves prepared effects while changing
        // the mushroom color, so a captured red cow can legitimately retain them.
        cow.setVariant(MushroomCow.MushroomType.RED);
        CompoundTag beforeRed = snapshot(cow);
        ItemStack redStew = single(helper, "mooshroom_stew", cow, List.of(cow));
        helper.assertTrue(ItemStack.isSameItemSameComponents(stew, redStew),
                "Changing a prepared mooshroom to red lost its stored stew effects");
        helper.assertTrue(snapshot(cow).equals(beforeRed),
                "Harvesting a prepared red mooshroom consumed its stored effects");
        MushroomCow unprepared = create(helper, EntityType.MOOSHROOM);
        helper.assertTrue(single(helper, "mooshroom_stew", unprepared,
                        List.of(unprepared)).is(Items.MUSHROOM_STEW),
                "An unprepared mooshroom received invented stew effects");
        cow.setAge(-24000);
        assertIneligible(helper, "mooshroom_stew", cow, List.of(cow));
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void frogLightsUseThePreyLootTableAndCorrectFrogVariant(
            GameTestHelper helper) {
        Frog frog = create(helper, EntityType.FROG);
        MagmaCube cube = create(helper, EntityType.MAGMA_CUBE);
        cube.setSize(1, true);
        List<LivingEntity> group = List.of(frog, cube);
        frog.setVariant(BuiltInRegistries.FROG_VARIANT.getHolderOrThrow(
                FrogVariant.WARM));
        assertLight(helper, frog, group, Items.PEARLESCENT_FROGLIGHT);
        frog.setVariant(BuiltInRegistries.FROG_VARIANT.getHolderOrThrow(
                FrogVariant.COLD));
        assertLight(helper, frog, group, Items.VERDANT_FROGLIGHT);
        frog.setVariant(BuiltInRegistries.FROG_VARIANT.getHolderOrThrow(
                FrogVariant.TEMPERATE));
        assertLight(helper, frog, group, Items.OCHRE_FROGLIGHT);
        assertIneligible(helper, "frog_lights", frog, List.of(frog));
        cube.setSize(2, true);
        assertIneligible(helper, "frog_lights", frog, group);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void virtualBreedingNeedsDistinctAdultPartners(
            GameTestHelper helper) {
        Turtle turtle = create(helper, EntityType.TURTLE);
        Turtle partner = create(helper, EntityType.TURTLE);
        assertIneligible(helper, "turtle_egg", turtle, List.of(turtle, turtle));
        partner.setAge(-24000);
        assertIneligible(helper, "turtle_egg", turtle, List.of(turtle, partner));
        partner.setAge(0);
        CompoundTag before = snapshot(turtle);
        ItemStack eggs = single(helper, "turtle_egg", turtle,
                List.of(turtle, partner));
        helper.assertTrue(eggs.is(Items.TURTLE_EGG)
                        && eggs.getCount() >= 1 && eggs.getCount() <= 4,
                "Turtle virtual breeding produced an invalid clutch");
        helper.assertTrue(snapshot(turtle).equals(before),
                "Virtual breeding changed the captured turtle");
        Sniffer sniffer = create(helper, EntityType.SNIFFER);
        Sniffer snifferPartner = create(helper, EntityType.SNIFFER);
        assertIneligible(helper, "sniffer_egg", sniffer,
                List.of(sniffer, turtle));
        ItemStack snifferEgg = single(helper, "sniffer_egg", sniffer,
                List.of(sniffer, snifferPartner));
        helper.assertTrue(snifferEgg.is(Items.SNIFFER_EGG)
                        && snifferEgg.getCount() == 1,
                "Sniffer virtual breeding must produce one egg");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void woolRetainsColorAndRejectsIneligibleSheep(
            GameTestHelper helper) {
        Sheep sheep = create(helper, EntityType.SHEEP);
        sheep.setColor(DyeColor.CYAN);
        CompoundTag before = snapshot(sheep);
        ItemStack wool = single(helper, "colored_wool", sheep, List.of(sheep));
        helper.assertTrue(wool.is(Items.CYAN_WOOL)
                        && wool.getCount() >= 1 && wool.getCount() <= 3,
                "Colored wool ignored the captured sheep color");
        helper.assertTrue(single(helper, "colored_dye", sheep, List.of(sheep))
                        .is(Items.CYAN_DYE),
                "Explicit colored dye behavior used the wrong dye");
        helper.assertTrue(snapshot(sheep).equals(before),
                "Virtual wool harvesting sheared or recolored the sheep");
        sheep.setSheared(true);
        assertIneligible(helper, "colored_wool", sheep, List.of(sheep));
        sheep.setSheared(false);
        sheep.setAge(-24000);
        assertIneligible(helper, "colored_wool", sheep, List.of(sheep));
        helper.succeed();
    }

    private static void assertLight(GameTestHelper helper, Frog frog,
                                    List<LivingEntity> group, Item item) {
        CompoundTag beforeFrog = snapshot(frog);
        CompoundTag beforeCube = snapshot(group.get(1));
        ItemStack output = single(helper, "frog_lights", frog, group);
        helper.assertTrue(output.is(item) && output.getCount() == 1,
                "Frog loot context selected the wrong light for its variant");
        helper.assertTrue(snapshot(frog).equals(beforeFrog)
                        && snapshot(group.get(1)).equals(beforeCube),
                "Virtual predation changed the captured frog or magma cube");
    }

    private static ItemStack single(GameTestHelper helper, String kind,
                                    LivingEntity entity,
                                    List<LivingEntity> group) {
        helper.assertTrue(DrygmyVanillaHarvest.variantKey(kind,
                        helper.getLevel(), entity, group).isPresent(),
                "Eligible entity rejected for " + kind);
        List<ItemStack> outputs = DrygmyVanillaHarvest.collect(kind,
                helper.getLevel(), entity, group);
        helper.assertTrue(outputs.size() == 1,
                "Expected one output stack for " + kind + ", got " + outputs);
        return outputs.getFirst();
    }

    private static void assertIneligible(GameTestHelper helper, String kind,
                                         LivingEntity entity,
                                         List<LivingEntity> group) {
        helper.assertTrue(DrygmyVanillaHarvest.variantKey(kind,
                        helper.getLevel(), entity, group).isEmpty()
                        && DrygmyVanillaHarvest.collect(kind,
                        helper.getLevel(), entity, group).isEmpty(),
                "Ineligible entity produced a virtual harvest for " + kind);
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
}
