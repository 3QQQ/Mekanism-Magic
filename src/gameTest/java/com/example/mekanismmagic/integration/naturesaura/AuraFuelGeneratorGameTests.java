package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.blocks.ModBlocks;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Runtime conservation and native-fuel semantics; these tests need Nature's Aura 41.9. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AuraFuelGeneratorGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private AuraFuelGeneratorGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void nativeFuelCatalogUsesRealAmountsAndRejectsInventedFuels(GameTestHelper helper) {
        assertFuel(helper, Items.EGG, 10_000);
        assertFuel(helper, Items.SNOWBALL, 7_000);
        assertFuel(helper, Items.FIRE_CHARGE, 35_000);
        assertFuel(helper, Items.ENDER_PEARL, 50_000);
        assertFuel(helper, Items.EXPERIENCE_BOTTLE, 200_000);
        assertFuel(helper, Items.ARROW, 30_000);
        assertFuel(helper, Items.TIPPED_ARROW, 30_000);
        assertFuel(helper, Items.SPECTRAL_ARROW, 40_000);
        assertFuel(helper, Items.TRIDENT, 3_000_000);
        assertFuel(helper, Items.DANDELION, 25_000);
        for (Item item : List.of(Items.COAL, Items.BONE_MEAL, Items.SLIME_BALL,
                Items.CHORUS_FRUIT, Items.OAK_SAPLING, Items.MOSS_BLOCK,
                Items.MOSSY_COBBLESTONE_STAIRS, Items.FIREWORK_ROCKET,
                Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.ENDER_EYE)) {
            helper.assertTrue(NatureAuraFuelCatalog.lookup(new ItemStack(item)).isEmpty(),
                    "Unsupported item became an Aura fuel: " + item);
        }
        Integer original = NaturesAuraAPI.PROJECTILE_GENERATIONS.put(EntityType.EGG, 12_345);
        try {
            assertFuel(helper, Items.EGG, 12_345);
        } finally {
            if (original == null) NaturesAuraAPI.PROJECTILE_GENERATIONS.remove(EntityType.EGG);
            else NaturesAuraAPI.PROJECTILE_GENERATIONS.put(EntityType.EGG, original);
        }
        ItemStack serializedRose = new ItemStack(ModBlocks.END_FLOWER);
        serializedRose.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(new CompoundTag()));
        helper.assertTrue(NatureAuraFuelCatalog.lookup(serializedRose).isEmpty(),
                "Serialized reservoir data could be reset into a fresh Rose quota");
        ItemStack serializedLeaves = new ItemStack(ModBlocks.ANCIENT_LEAVES);
        serializedLeaves.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(new CompoundTag()));
        helper.assertTrue(NatureAuraFuelCatalog.lookup(serializedLeaves).isEmpty(),
                "Serialized ancient leaves could regain their fresh reservoir");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void nativePotionAndFireworkComponentsDetermineFuelValue(GameTestHelper helper) {
        helper.assertTrue(NatureAuraFuelCatalog.potionAura(
                        PotionContents.createItemStack(Items.LINGERING_POTION, Potions.SWIFTNESS)) == 129_600,
                "Fresh native swiftness cloud did not retain its nine absorption pulses");
        helper.assertTrue(NatureAuraFuelCatalog.potionAura(
                        PotionContents.createItemStack(Items.LINGERING_POTION, Potions.STRONG_SWIFTNESS)) == 518_400,
                "Potion amplifier multiplier differs from the native ritual");
        helper.assertTrue(NatureAuraFuelCatalog.potionAura(
                        PotionContents.createItemStack(Items.LINGERING_POTION, Potions.LONG_SWIFTNESS)) == 345_600,
                "Potion duration was scaled as an entity effect instead of its native contents");
        helper.assertTrue(NatureAuraFuelCatalog.potionAura(
                        PotionContents.createItemStack(Items.LINGERING_POTION, Potions.HEALING)) == 0
                        && NatureAuraFuelCatalog.potionAura(
                        PotionContents.createItemStack(Items.LINGERING_POTION, Potions.POISON)) == 0,
                "Instant or harmful potions became Aura fuel");
        ItemStack simple = new ItemStack(Items.FIREWORK_ROCKET);
        simple.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(
                new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL,
                        IntArrayList.of(0xFF0000, 0xFF0000), IntArrayList.of(0x00FF00), false, false))));
        helper.assertTrue(NatureAuraFuelCatalog.fireworkAura(simple) == 32_500,
                "Native firework valuation counted duplicate or fade colors");
        simple.set(DataComponents.FIREWORKS, new Fireworks(2, List.of(
                new FireworkExplosion(FireworkExplosion.Shape.CREEPER,
                        IntArrayList.of(0xFF0000, 0x00FF00), IntArrayList.of(), true, true))));
        helper.assertTrue(NatureAuraFuelCatalog.fireworkAura(simple) == 340_000,
                "Native firework shape/trail/twinkle weights changed");
        helper.assertTrue(NatureAuraFuelCatalog.displayFuels(helper.getLevel().registryAccess())
                        .stream().anyMatch(fuel -> fuel.kind() == NatureAuraFuelCatalog.Kind.POTION),
                "JEI catalog omitted the dynamic potion registry");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void paidFuelSurvivesReloadFullBufferRedstoneAndTypeMismatch(GameTestHelper helper) {
        AuraFuelGeneratorBlockEntity tile = place(helper);
        helper.assertTrue(tile.getNativeEnergyContainer() == null && tile.getNativeEnergySlot() == null
                        && tile.getConfig().getConfig(TransmissionType.ENERGY) == null,
                "Consumable generator unexpectedly requires or exposes FE");
        tile.getNativeInputSlot().setStack(new ItemStack(Items.EGG, 2));
        helper.assertTrue(tile.storeAuraExact(tile.getAuraCapacity(), false), "Could not fill test buffer");
        tile.onUpdateServer();
        helper.assertTrue(tile.getNativeInputSlot().getCount() == 2 && tile.getRemainingFuelAura() == 0,
                "Full buffer consumed a new fuel");
        helper.assertTrue(tile.drainAuraExact(tile.getAuraCapacity(), false), "Could not clear test buffer");
        tile.onUpdateServer();
        helper.assertTrue(tile.getStoredAura() == 1_000 && tile.getRemainingFuelAura() == 9_000
                        && tile.getNativeInputSlot().getCount() == 1,
                "First paid fuel tick did not debit exactly one egg and produce 1,000 Aura");
        CompoundTag paid = save(helper, tile);
        tile.loadAdditional(paid.copy(), helper.getLevel().registryAccess());
        tile.setControlType(RedstoneControl.HIGH);
        tile.onUpdateServer();
        helper.assertTrue(tile.getStoredAura() == 1_000 && tile.getRemainingFuelAura() == 9_000,
                "Redstone stop advanced paid fuel");
        tile.setControlType(RedstoneControl.DISABLED);
        CompoundTag wrongType = save(helper, tile);
        wrongType.putString("nature_aura_fuel_type", NaturesAuraAPI.TYPE_NETHER.getName().toString());
        tile.loadAdditional(wrongType, helper.getLevel().registryAccess());
        helper.assertTrue(tile.drainAuraExact(tile.getStoredAura(), false),
                "Could not isolate an empty buffer with a paid quota of another type");
        tile.onUpdateServer();
        helper.assertTrue(tile.getStoredAura() == 0 && tile.getRemainingFuelAura() == 9_000
                        && tile.hasFuelTypeMismatch(),
                "An empty buffer hid the paid quota's Aura type mismatch or converted it");
        tile.loadAdditional(paid, helper.getLevel().registryAccess());
        helper.assertTrue(!tile.hasFuelTypeMismatch(), "Restoring the matching fuel type kept a stale warning");
        for (int i = 0; i < 9; i++) tile.onUpdateServer();
        helper.assertTrue(tile.getStoredAura() == 10_000 && tile.getRemainingFuelAura() == 0
                        && tile.getNativeInputSlot().getCount() == 1,
                "Reload either repaid a consumed egg or discarded its pending Aura");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void nativeMossResidueAndFlowerFatigueAreConserved(GameTestHelper helper) {
        AuraFuelGeneratorBlockEntity tile = place(helper);
        tile.getNativeInputSlot().setStack(new ItemStack(Items.MOSSY_COBBLESTONE));
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        tile.onUpdateServer();
        helper.assertTrue(tile.getNativeInputSlot().getCount() == 1 && tile.getRemainingFuelAura() == 0,
                "Blocked clean-stone remainder consumed its moss fuel");
        tile.getNativeOutputSlot().setStack(ItemStack.EMPTY);
        for (int i = 0; i < 5; i++) tile.onUpdateServer();
        helper.assertTrue(tile.getStoredAura() == 5_000 && tile.getRemainingFuelAura() == 0
                        && tile.getNativeOutputSlot().getStack().is(Items.COBBLESTONE)
                        && tile.getNativeOutputSlot().getCount() == 1,
                "Moss deconstruction lost or duplicated its clean stone or Aura");
        tile.getNativeInputSlot().setStack(new ItemStack(Items.DANDELION, 2));
        for (int i = 0; i < 25; i++) tile.onUpdateServer();
        tile.onUpdateServer();
        helper.assertTrue(tile.getFuelTotalAura() == 24_500
                        && tile.getFlowerFatigue(BuiltInRegistries.ITEM.getKey(Items.DANDELION)) == 10,
                "Consecutive flowers did not apply the native 500-Aura fatigue increment");
        tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
        helper.assertTrue(tile.getFlowerFatigue(BuiltInRegistries.ITEM.getKey(Items.DANDELION)) == 10,
                "Reload cleared native flower fatigue");
        for (int i = 0; i < 24; i++) tile.onUpdateServer();
        tile.getNativeInputSlot().setStack(new ItemStack(Items.POPPY));
        tile.onUpdateServer();
        helper.assertTrue(tile.getFlowerFatigue(BuiltInRegistries.ITEM.getKey(Items.DANDELION)) == 9
                        && tile.getFlowerFatigue(BuiltInRegistries.ITEM.getKey(Items.POPPY)) == 5,
                "A different flower failed to recover the other species' fatigue");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void roseNeedsBothNativeInputsAndKeepsOneFiniteReservoir(GameTestHelper helper) {
        AuraFuelGeneratorBlockEntity tile = place(helper);
        IInventorySlot second = tile.mekanismMagicPatternInputs().get(1);
        tile.getNativeInputSlot().setStack(new ItemStack(Items.ENDER_EYE));
        tile.onUpdateServer();
        helper.assertTrue(tile.getNativeInputSlot().getCount() == 1 && tile.getRemainingFuelAura() == 0,
                "Ender eye alone became the Rose's finite Aura reservoir");
        second.setStack(new ItemStack(ModBlocks.END_FLOWER));
        tile.onUpdateServer();
        helper.assertTrue(tile.getNativeInputSlot().isEmpty() && second.isEmpty()
                        && tile.getFuelTotalAura() == 500_000 && tile.getRemainingFuelAura() == 499_000
                        && tile.getNativeOutputSlot().getStack().is(Items.DEAD_BUSH),
                "Reversed rose/eye input order did not conserve the finite reservoir and residue");
        tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
        tile.onUpdateServer();
        helper.assertTrue(tile.getStoredAura() == 2_000 && tile.getRemainingFuelAura() == 498_000
                        && tile.getNativeOutputSlot().getCount() == 1,
                "Reload repaid a consumed Rose or duplicated its dead-bush residue");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void blockDropEventPreservesPaidQuotaAndFlowerHistoryOnOnlyOneItem(GameTestHelper helper) {
        AuraFuelGeneratorBlockEntity tile = place(helper);
        tile.getNativeInputSlot().setStack(new ItemStack(Items.DANDELION, 2));
        tile.onUpdateServer();
        BlockPos absolute = tile.getBlockPos();
        List<ItemEntity> drops = new ArrayList<>();
        drops.add(new ItemEntity(helper.getLevel(), absolute.getX(), absolute.getY(), absolute.getZ(),
                new ItemStack(NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK.get(), 3)));
        NeoForge.EVENT_BUS.post(new BlockDropsEvent(helper.getLevel(), absolute,
                tile.getBlockState(), tile, drops, null, ItemStack.EMPTY));
        List<ItemStack> stateful = drops.stream().map(ItemEntity::getItem)
                .filter(stack -> stack.has(DataComponents.BLOCK_ENTITY_DATA)).toList();
        helper.assertTrue(stateful.size() == 1 && stateful.getFirst().getCount() == 1
                        && drops.stream().mapToInt(drop -> drop.getItem().getCount()).sum() == 3,
                "Registered machine-drop hook duplicated a paid quota across physical items");
        ItemStack machineItem = stateful.getFirst();
        helper.setBlock(POS, Blocks.AIR);
        AuraFuelGeneratorBlockEntity restored = place(helper);
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null, absolute, machineItem),
                "Native placement did not restore the paid fuel's custom data");
        restored.applyComponentsFromItemStack(machineItem);
        helper.assertTrue(restored.getStoredAura() == 1_000 && restored.getRemainingFuelAura() == 24_000
                        && restored.getFuelTotalAura() == 25_000 && restored.getNativeInputSlot().getCount() == 1
                        && restored.getFlowerFatigue(BuiltInRegistries.ITEM.getKey(Items.DANDELION)) == 5,
                "Break/place lost paid Aura or reset consumed items or flower fatigue");
        for (int i = 0; i < 24; i++) restored.onUpdateServer();
        helper.assertTrue(restored.getStoredAura() == 25_000 && restored.getRemainingFuelAura() == 0
                        && restored.getNativeInputSlot().getCount() == 1,
                "Placed generator repaid its consumed flower");
        restored.onUpdateServer();
        helper.assertTrue(restored.getFuelTotalAura() == 24_500,
                "Breaking the machine bypassed the native same-flower penalty");
        helper.succeed();
    }

    private static AuraFuelGeneratorBlockEntity place(GameTestHelper helper) {
        helper.setBlock(POS, NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK.get());
        helper.assertTrue(helper.getBlockEntity(POS) instanceof AuraFuelGeneratorBlockEntity,
                "Fuel generator registration created the wrong block entity");
        return (AuraFuelGeneratorBlockEntity) helper.getBlockEntity(POS);
    }

    private static CompoundTag save(GameTestHelper helper, AuraFuelGeneratorBlockEntity tile) {
        CompoundTag tag = new CompoundTag();
        tile.saveAdditional(tag, helper.getLevel().registryAccess());
        return tag;
    }

    private static void assertFuel(GameTestHelper helper, Item item, long expected) {
        var fuel = NatureAuraFuelCatalog.lookup(new ItemStack(item));
        helper.assertTrue(fuel.isPresent() && fuel.get().aura() == expected,
                "Native fuel amount differs for " + item + ": " + fuel);
    }
}
