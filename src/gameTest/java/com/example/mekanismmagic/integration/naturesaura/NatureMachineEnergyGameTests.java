package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import de.ellpeck.naturesaura.api.aura.chunk.IAuraChunk;
import mekanism.api.Upgrade;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeEnergy;
import mekanism.common.config.MekanismConfig;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Real native field transfers and internal spawn-egg production, with exact Joule accounting. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NatureMachineEnergyGameTests {
    private static final BlockPos MACHINE = new BlockPos(4, 2, 4);

    private NatureMachineEnergyGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void natureProfilesMatchRegistrationContainerAndRuntimePricing(GameTestHelper helper) {
        Block[] blocks = {NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get(),
                NaturesAuraRegistries.OFFERING_PROCESSOR_BLOCK.get(),
                NaturesAuraRegistries.TREE_RITUAL_PROCESSOR_BLOCK.get(),
                NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK.get()};
        int[] expected = {MagicMachineEnergyProfiles.MATERIAL_PROCESSING, MagicMachineEnergyProfiles.ASSEMBLY,
                MagicMachineEnergyProfiles.MAGIC_REACTION, MagicMachineEnergyProfiles.MAGIC_REACTION};
        MachineRecipeResult recipe = new MachineRecipeResult(ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, "nature_energy_profile_test"), ItemStack.EMPTY, 200, List.of(), -1);
        for (int index = 0; index < blocks.length; index++) {
            helper.setBlock(MACHINE, Blocks.AIR);
            helper.setBlock(MACHINE, blocks[index]);
            NativeMagicMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
            AttributeEnergy attribute = Attribute.get(machine.getBlockHolder(), AttributeEnergy.class);
            helper.assertTrue(attribute != null && attribute.getUsage() == expected[index]
                            && machine.getNativeEnergyContainer().getBaseEnergyPerTick() == expected[index]
                            && runtimePrice(machine, recipe) == expected[index],
                    "Nature registration, cached energy base and runtime pricing disagree on " + blocks[index]);
            machine.getComponent().addUpgrades(Upgrade.SPEED, 4);
            machine.getComponent().addUpgrades(Upgrade.ENERGY, 4);
            helper.assertTrue(runtimePrice(machine, recipe) == MekanismUtils.getEnergyPerTick(machine, expected[index]),
                    "Nature energy profile bypassed the native upgrade formula on " + blocks[index]);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void animalPowerPreservesNativeRoundingUntilTheIntervalCapBinds(GameTestHelper helper) {
        int previousMultiplier = MekanismConfig.general.maxUpgradeMultiplier.get();
        try {
            MekanismConfig.general.maxUpgradeMultiplier.set(10);
            AnimalSpawnerProcessorBlockEntity machine = placeAnimal(helper);
            machine.getComponent().addUpgrades(Upgrade.SPEED, 4);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, "energy_rounding_test");
            MachineRecipeResult uncapped = new MachineRecipeResult(id, ItemStack.EMPTY, 200, List.of(), -1);
            long nativeCost = MekanismUtils.getEnergyPerTick(machine, AnimalSpawnerProcessorBlockEntity.BASE_ENERGY_PER_TICK);
            long base = AnimalSpawnerProcessorBlockEntity.BASE_ENERGY_PER_TICK;
            helper.assertTrue(nativeCost == base * 10 && machine.energyUsagePerTick(uncapped) == nativeCost,
                    "Uncapped S4/E0 work squared sqrt(10) instead of using the exact native profile charge");
            MachineRecipeResult capped = new MachineRecipeResult(id, ItemStack.EMPTY, 22, List.of(), -1);
            long cappedCost = (long) Math.ceil(base * (double) 22 * 22 / (20D * 20));
            helper.assertTrue(machine.energyUsagePerTick(capped) == cappedCost,
                    "The exact profile * 22^2 / 20^2 cap was rounded up by a floating-point tail");
            machine.getComponent().addUpgrades(Upgrade.ENERGY, 8);
            helper.assertTrue(machine.energyUsagePerTick(uncapped)
                            == MekanismUtils.getEnergyPerTick(machine, AnimalSpawnerProcessorBlockEntity.BASE_ENERGY_PER_TICK)
                            && machine.energyUsagePerTick(capped) == (long) Math.ceil(cappedCost / 10D),
                    "Energy reduction changed uncapped native rounding or discarded the real capped fraction");
        } finally {
            MekanismConfig.general.maxUpgradeMultiplier.set(previousMultiplier);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fieldMovesExistingAuraForTwentyFePerAuraAndKeepsItsInterval(GameTestHelper helper) {
        double previousConversion = MekanismConfig.general.forgeConversionRate.get();
        int previousMultiplier = MekanismConfig.general.maxUpgradeMultiplier.get();
        try {
            MekanismConfig.general.forgeConversionRate.set(2.5D);
            MekanismConfig.general.maxUpgradeMultiplier.set(10);
            for (int energyUpgrades : new int[]{0, 8}) {
                AuraFieldInterfaceBlockEntity machine = placeField(helper);
                helper.assertTrue(machine.getComponent().supports(Upgrade.ENERGY)
                                && !machine.getComponent().supports(Upgrade.SPEED),
                        "Field transport acquired a speed upgrade or lost its energy upgrade");
                if (energyUpgrades > 0) {
                    machine.getComponent().addUpgrades(Upgrade.ENERGY, energyUpgrades);
                }
                helper.assertTrue(AuraFieldInterfaceBlockEntity.baseEnergyUsage() == 12_500L,
                        "Registry baseline is not 5,000 FE/t converted into Joules");
                long batchCost = energyUpgrades == 0 ? 250_000L : 25_000L;
                machine.cycleFieldMode(); // RELEASE: transfer existing internal Aura into the native world.
                helper.assertTrue(machine.storeAuraExact(10_000, false), "Could not seed field Aura");
                machine.getNativeEnergyContainer().setEnergy(batchCost * 2);
                BlockPos target = IAuraChunk.getLowestSpot(helper.getLevel(), machine.getBlockPos(),
                        NatureAuraMachineConfig.FIELD_RADIUS, machine.getBlockPos());
                IAuraChunk chunk = IAuraChunk.getAuraChunk(helper.getLevel(), target);
                int beforeWorld = chunk.getDrainSpot(target);
                tickFieldNow(helper, machine);
                helper.assertTrue(machine.getStoredAura() == 5_000
                                && chunk.getDrainSpot(target) - beforeWorld == 5_000
                                && machine.getNativeEnergyContainer().getEnergy() == batchCost,
                        "Field transfer created/lost Aura or did not charge its exact FE-denominated batch");
                helper.assertTrue(nextFieldOperation(machine) == helper.getLevel().getGameTime() + 20,
                        "Successful field transfer no longer observes its twenty-world-tick interval");
                for (int repeat = 0; repeat < 25; repeat++) {
                    machine.onUpdateServer();
                }
                helper.assertTrue(machine.getStoredAura() == 5_000
                                && chunk.getDrainSpot(target) - beforeWorld == 5_000
                                && machine.getNativeEnergyContainer().getEnergy() == batchCost,
                        "Repeated updates in one world tick bypassed the transfer interval");
                // Restore precisely the Aura this test released, so neighbouring tests see no new field resource.
                helper.assertTrue(chunk.drainAura(target, 5_000, false, false) == 5_000,
                        "Could not restore native field fixture");
            }
        } finally {
            MekanismConfig.general.forgeConversionRate.set(previousConversion);
            MekanismConfig.general.maxUpgradeMultiplier.set(previousMultiplier);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fieldFractionalConversionChargesOneRoundedBatchAndRespectsBackpressure(GameTestHelper helper) {
        double previousConversion = MekanismConfig.general.forgeConversionRate.get();
        try {
            MekanismConfig.general.forgeConversionRate.set(0.12525D);
            AuraFieldInterfaceBlockEntity machine = placeField(helper);
            machine.cycleFieldMode();
            helper.assertTrue(machine.storeAuraExact(5_000, false), "Could not seed fractional field test");
            machine.getNativeEnergyContainer().setEnergy(20L);
            BlockPos target = IAuraChunk.getLowestSpot(helper.getLevel(), machine.getBlockPos(),
                    NatureAuraMachineConfig.FIELD_RADIUS, machine.getBlockPos());
            IAuraChunk chunk = IAuraChunk.getAuraChunk(helper.getLevel(), target);
            int beforeWorld = chunk.getDrainSpot(target);
            tickFieldNow(helper, machine);
            helper.assertTrue(machine.getStoredAura() == 4_993
                            && chunk.getDrainSpot(target) - beforeWorld == 7
                            && machine.getNativeEnergyContainer().getEnergy() == 2L,
                    "Fractional conversion did not charge ceil(7 * 20 FE * 0.12525 J/FE) = 18 J");
            tickFieldNow(helper, machine);
            helper.assertTrue(machine.getStoredAura() == 4_993
                            && machine.getNativeEnergyContainer().getEnergy() == 2L
                            && chunk.getDrainSpot(target) - beforeWorld == 7,
                    "Less than one Aura's rounded fee moved Aura or discarded the unpaid remainder");
            helper.assertTrue(chunk.drainAura(target, 7, false, false) == 7,
                    "Could not restore the fractional native field fixture");

            AuraFieldInterfaceBlockEntity full = placeField(helper);
            helper.assertTrue(full.storeAuraExact(full.getAuraCapacity(), false), "Could not fill the Aura buffer");
            full.getNativeEnergyContainer().setEnergy(100_000L);
            tickFieldNow(helper, full); // ABSORB with no internal room must neither move nor charge.
            helper.assertTrue(full.getStoredAura() == full.getAuraCapacity()
                            && full.getNativeEnergyContainer().getEnergy() == 100_000L,
                    "A full internal buffer consumed energy or overfilled from the native field");

            AuraFieldInterfaceBlockEntity empty = placeField(helper);
            empty.cycleFieldMode();
            empty.getNativeEnergyContainer().setEnergy(100_000L);
            tickFieldNow(helper, empty);
            helper.assertTrue(empty.getStoredAura() == 0
                            && empty.getNativeEnergyContainer().getEnergy() == 100_000L,
                    "An empty release buffer created world Aura or charged a nonexistent transfer");
        } finally {
            MekanismConfig.general.forgeConversionRate.set(previousConversion);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void cowUsesOnlyRealizableAccelerationAndStillRequiresTwentyTicks(GameTestHelper helper) {
        int previousMultiplier = MekanismConfig.general.maxUpgradeMultiplier.get();
        try {
            MekanismConfig.general.maxUpgradeMultiplier.set(10);
            for (int[] upgrades : new int[][]{{0, 0}, {8, 0}, {8, 8}}) {
                AnimalSpawnerProcessorBlockEntity machine = placeAnimal(helper);
                var recipe = AnimalSpawnerRecipeBridge.scan(helper.getLevel().getRecipeManager()).stream()
                        .filter(view -> view.id().getPath().equals("animal_spawner/cow"))
                        .findFirst().orElseThrow();
                helper.assertTrue(recipe.time() == 60 && recipe.entityType() == EntityType.COW,
                        "The native cow oracle no longer has its expected sixty-tick duration");
                List<ItemStack> inputs = new ArrayList<>();
                for (int index = 0; index < recipe.ingredients().size(); index++) {
                    ItemStack input = NatureAuraRecipeBridge.representativeChoice(recipe.ingredients().get(index))
                            .orElseThrow().copyWithCount(recipe.inputAmounts().get(index));
                    inputs.add(input);
                    machine.mekanismMagicPatternInputs().get(index).setStack(input);
                }
                machine.getComponent().addUpgrades(Upgrade.SPEED, upgrades[0]);
                machine.getComponent().addUpgrades(Upgrade.ENERGY, upgrades[1]);
                long base = AnimalSpawnerProcessorBlockEntity.BASE_ENERGY_PER_TICK;
                long perTick = upgrades[0] == 0 ? base : upgrades[1] == 0 ? base * 9 : (long) Math.ceil(base * 9 / 10D);
                int duration = upgrades[0] == 0 ? 60 : 20;
                helper.assertTrue(machine.energyUsagePerTick(recipe.toMachineRecipe(recipe.match(inputs).orElseThrow()))
                                == perTick,
                        "Animal power ignored the realizable speed cap or energy upgrade reduction");
                machine.refreshAuraTypeState();
                helper.assertTrue(machine.storeAuraExact(recipe.auraCost(), false), "Could not fund native cow Aura");
                machine.getNativeEnergyContainer().setEnergy(1_000_000L);
                for (int tick = 0; tick < duration - 1; tick++) {
                    machine.onUpdateServer();
                }
                helper.assertTrue(machine.getProgress() == duration - 1
                                && machine.getProgressRequired() == duration
                                && cows(helper).isEmpty()
                                && machine.getStoredAura() == recipe.auraCost(),
                        "The speed floor spawned early or changed the native resource commit point");
                machine.onUpdateServer();
                helper.assertTrue(cows(helper).isEmpty() && machine.hasPendingOutput()
                                && machine.getPendingOutputs().getFirst().is(net.minecraft.world.item.Items.COW_SPAWN_EGG)
                                && machine.getStoredAura() == 0
                                && machine.mekanismMagicPatternInputs().stream().allMatch(slot -> slot.isEmpty())
                                && machine.getNativeEnergyContainer().getEnergy() == 1_000_000L - duration * perTick,
                        "Cow egg production did not charge exactly the capped power for actual processing ticks");
                machine.onUpdateServer();
                helper.assertTrue(machine.getNativeOutputSlot().getStack().is(net.minecraft.world.item.Items.COW_SPAWN_EGG)
                                && machine.getNativeOutputSlot().getCount() == 1 && !machine.hasPendingOutput()
                                && machine.getNativeEnergyContainer().getEnergy() == 1_000_000L - duration * perTick,
                        "Delivering a paid egg charged an extra processing tick");
            }
        } finally {
            cows(helper).forEach(Entity::discard);
            MekanismConfig.general.maxUpgradeMultiplier.set(previousMultiplier);
        }
        helper.succeed();
    }

    private static AuraFieldInterfaceBlockEntity placeField(GameTestHelper helper) {
        helper.setBlock(MACHINE, Blocks.AIR);
        helper.setBlock(MACHINE, NaturesAuraRegistries.AURA_FIELD_INTERFACE_BLOCK.get());
        AuraFieldInterfaceBlockEntity machine = helper.getBlockEntity(MACHINE);
        for (Direction side : Direction.values()) {
            machine.setAuraSideMode(side, NatureAuraMachineBlockEntity.AuraSideMode.NONE);
        }
        machine.refreshAuraTypeState();
        return machine;
    }

    /** Inspect the actual virtual pricing hook without requiring a world ritual to complete. */
    private static long runtimePrice(NativeMagicMachineBlockEntity machine, MachineRecipeResult recipe) {
        try {
            var method = NativeMagicMachineBlockEntity.class.getDeclaredMethod("energyUsagePerTick", MachineRecipeResult.class);
            method.setAccessible(true);
            return (long) method.invoke(machine, recipe);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect Nature runtime energy pricing", exception);
        }
    }

    /** Force only the scheduling boundary; the real server update still executes all resource checks. */
    private static void tickFieldNow(GameTestHelper helper, AuraFieldInterfaceBlockEntity machine) {
        try {
            Field next = AuraFieldInterfaceBlockEntity.class.getDeclaredField("nextFieldOperation");
            next.setAccessible(true);
            next.setLong(machine, helper.getLevel().getGameTime());
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to schedule field test operation", exception);
        }
        machine.onUpdateServer();
    }

    private static long nextFieldOperation(AuraFieldInterfaceBlockEntity machine) {
        try {
            Field next = AuraFieldInterfaceBlockEntity.class.getDeclaredField("nextFieldOperation");
            next.setAccessible(true);
            return next.getLong(machine);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to inspect field interval", exception);
        }
    }

    private static AnimalSpawnerProcessorBlockEntity placeAnimal(GameTestHelper helper) {
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                for (int y = 2; y <= 8; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        helper.setBlock(MACHINE, NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK.get());
        AnimalSpawnerProcessorBlockEntity machine = helper.getBlockEntity(MACHINE);
        return machine;
    }

    private static List<Entity> cows(GameTestHelper helper) {
        return helper.getLevel().getEntities((Entity) null, new AABB(helper.absolutePos(MACHINE)).inflate(5),
                entity -> entity.getType() == EntityType.COW);
    }
}
