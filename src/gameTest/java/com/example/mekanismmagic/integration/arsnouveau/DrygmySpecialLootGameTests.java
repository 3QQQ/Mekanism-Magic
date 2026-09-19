package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import mekanism.api.Upgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Runtime coverage for loaded special-loot tags and real Drygmy batches. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrygmySpecialLootGameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final List<ExpectedDrop> NEW_DROPS = List.of(
            new ExpectedDrop(EntityType.WARDEN, Items.ECHO_SHARD),
            new ExpectedDrop(EntityType.TURTLE, Items.TURTLE_SCUTE),
            new ExpectedDrop(EntityType.ARMADILLO, Items.ARMADILLO_SCUTE));

    private DrygmySpecialLootGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void loadedEntityMappingsRetainNewAndBossDrops(
            GameTestHelper helper) {
        List<LivingEntity> entities = new ArrayList<>();
        try {
            for (ExpectedDrop mapping : NEW_DROPS) {
                assertSingleMapping(helper, entities, mapping);
            }
            assertSingleMapping(helper, entities,
                    new ExpectedDrop(EntityType.WITHER, Items.NETHER_STAR));
            ResourceLocation wildenBossId = ResourceLocation
                    .fromNamespaceAndPath("ars_nouveau", "wilden_boss");
            ResourceLocation wildenTributeId = ResourceLocation
                    .fromNamespaceAndPath("ars_nouveau", "wilden_tribute");
            EntityType<?> wildenBoss = BuiltInRegistries.ENTITY_TYPE
                    .getOptional(wildenBossId).orElseThrow(() ->
                            new IllegalStateException(
                                    "Missing registered Wilden boss"));
            Item wildenTribute = BuiltInRegistries.ITEM
                    .getOptional(wildenTributeId).orElseThrow(() ->
                            new IllegalStateException(
                                    "Missing registered Wilden tribute"));
            assertSingleMapping(helper, entities,
                    new ExpectedDrop(wildenBoss, wildenTribute));
            LivingEntity cow = createEntity(helper, EntityType.COW, entities);
            helper.assertTrue(DrygmyLootPolicy.extractFixedOutputs(
                            new ArrayList<>(), List.of(cow), 1).isEmpty(),
                    "A cow matched a supplemental or boss fixed-drop tag");
        } finally {
            entities.forEach(LivingEntity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void repeatedEntityTypesScaleFixedDropsOnce(
            GameTestHelper helper) {
        List<LivingEntity> entities = new ArrayList<>();
        try {
            for (ExpectedDrop mapping : NEW_DROPS) {
                createEntity(helper, mapping.entityType(), entities);
                createEntity(helper, mapping.entityType(), entities);
            }
            for (int multiplier : new int[]{4, 256, Integer.MAX_VALUE}) {
                List<ItemStack> outputs = DrygmyLootPolicy.extractFixedOutputs(
                        new ArrayList<>(), entities, multiplier);
                helper.assertTrue(outputs.size() == NEW_DROPS.size(),
                        "Duplicate entity types produced extra fixed stacks "
                                + "at operation multiplier " + multiplier);
                int expected = Math.min(multiplier, 256);
                for (ExpectedDrop mapping : NEW_DROPS) {
                    assertCount(helper, outputs, mapping.item(), expected,
                            "Repeated entity types at multiplier "
                                    + multiplier);
                }
            }
        } finally {
            entities.forEach(LivingEntity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fixedDropsLeaveTheRandomCandidatePool(
            GameTestHelper helper) {
        List<LivingEntity> entities = new ArrayList<>();
        try {
            List<ItemStack> candidates = new ArrayList<>();
            for (ExpectedDrop mapping : NEW_DROPS) {
                createEntity(helper, mapping.entityType(), entities);
                candidates.add(new ItemStack(mapping.item(), 2));
                candidates.add(new ItemStack(mapping.item(), 7));
            }
            candidates.add(new ItemStack(Items.SCULK_CATALYST, 3));
            candidates.add(new ItemStack(Items.SEAGRASS, 5));
            DrygmyLootPolicy.filterCandidates(candidates);
            List<ItemStack> outputs = DrygmyLootPolicy.extractFixedOutputs(
                    candidates, entities, 4);
            helper.assertTrue(outputs.size() == NEW_DROPS.size(),
                    "Existing loot-table candidates duplicated fixed stacks");
            for (ExpectedDrop mapping : NEW_DROPS) {
                assertCount(helper, candidates, mapping.item(), 0,
                        "Fixed material remained randomly selectable");
                assertCount(helper, outputs, mapping.item(), 4,
                        "Existing candidate counts changed the fixed output");
            }
            helper.assertTrue(candidates.size() == 2,
                    "Extracting fixed drops removed ordinary loot candidates");
            assertCount(helper, candidates, Items.SCULK_CATALYST, 3,
                    "Ordinary Warden loot changed");
            assertCount(helper, candidates, Items.SEAGRASS, 5,
                    "Ordinary turtle loot changed");
        } finally {
            entities.forEach(LivingEntity::discard);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void stackedMachineKeepsExactSpecialCountsAndBlacklist(
            GameTestHelper helper) {
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        DrygmySimulatorBlockEntity machine =
                helper.getBlockEntity(MACHINE_POS);
        Upgrade stackUpgrade = NativeMekanismRegistries
                .dimensionMinerStackUpgrade();
        helper.assertTrue(stackUpgrade != null,
                "Drygmy stack upgrade is unavailable");
        machine.getComponent().addUpgrades(stackUpgrade, 8);
        helper.assertTrue(machine.getComponent().getUpgrades(stackUpgrade) == 8,
                "Unable to install eight Drygmy stack upgrades");
        for (ExpectedDrop mapping : NEW_DROPS) {
            ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE
                    .getKey(mapping.entityType());
            int prepared = machine.seedDevelopmentTest(
                    helper.getLevel(), entityId);
            helper.assertTrue(prepared > 0,
                    "Drygmy prepared no output for " + entityId);
            int multiplier = machine.developmentPendingOperationMultiplier();
            helper.assertTrue(multiplier == 256,
                    "Eight stack upgrades did not prepare 256 operations for "
                            + entityId);
            List<ItemStack> outputs = machine.developmentPendingOutputs();
            for (ExpectedDrop possible : NEW_DROPS) {
                assertCount(helper, outputs, possible.item(),
                        possible.item() == mapping.item() ? multiplier : 0,
                        "Stacked machine batch for " + entityId);
            }
            if (mapping.entityType() == EntityType.WARDEN) {
                helper.assertTrue(count(outputs, Items.SCULK_CATALYST) > 0,
                        "Adding Echo Shards removed ordinary Warden loot");
            }
        }
        helper.assertTrue(machine.seedDevelopmentTest(helper.getLevel(),
                        ResourceLocation.withDefaultNamespace("cow")) > 0,
                "The non-matching cow stopped producing ordinary loot");
        for (ExpectedDrop mapping : NEW_DROPS) {
            assertCount(helper, machine.developmentPendingOutputs(),
                    mapping.item(), 0,
                    "A cow inherited a previous entity's fixed output");
        }
        helper.assertTrue(machine.seedDevelopmentTest(helper.getLevel(),
                        ResourceLocation.withDefaultNamespace("iron_golem"))
                        == 0 && machine.developmentPendingOutputs().isEmpty(),
                "Special-loot support bypassed the Ars Drygmy entity blacklist");
        helper.succeed();
    }

    private static void assertSingleMapping(
            GameTestHelper helper, List<LivingEntity> entities,
            ExpectedDrop mapping) {
        LivingEntity entity = createEntity(
                helper, mapping.entityType(), entities);
        List<ItemStack> outputs = DrygmyLootPolicy.extractFixedOutputs(
                new ArrayList<>(), List.of(entity), 1);
        String context = "Loaded fixed-drop mapping for "
                + BuiltInRegistries.ENTITY_TYPE.getKey(mapping.entityType());
        helper.assertTrue(outputs.size() == 1,
                context + " did not produce exactly one fixed stack");
        assertCount(helper, outputs, mapping.item(), 1, context);
    }

    private static LivingEntity createEntity(
            GameTestHelper helper, EntityType<?> type,
            List<LivingEntity> tracked) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", entityId.toString());
        Entity entity = EntityType.create(tag, helper.getLevel()).orElse(null);
        if (!(entity instanceof LivingEntity living)) {
            if (entity != null) {
                entity.discard();
            }
            throw new IllegalStateException(
                    "Unable to create living test entity " + entityId);
        }
        tracked.add(living);
        return living;
    }

    private static void assertCount(
            GameTestHelper helper, List<ItemStack> outputs, Item item,
            long expected, String context) {
        long actual = count(outputs, item);
        helper.assertTrue(actual == expected,
                context + ": expected " + expected + " "
                        + BuiltInRegistries.ITEM.getKey(item)
                        + ", got " + actual);
    }

    private static long count(List<ItemStack> outputs, Item item) {
        return outputs.stream().filter(stack -> stack.is(item))
                .mapToLong(ItemStack::getCount).sum();
    }

    private record ExpectedDrop(EntityType<?> entityType, Item item) {
    }
}
