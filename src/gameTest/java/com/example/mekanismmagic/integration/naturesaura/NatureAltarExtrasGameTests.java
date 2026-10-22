package com.example.mekanismmagic.integration.naturesaura;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryWindowSlot;
import com.example.mekanismmagic.gametest.NatureAuraIntegrationGameTests;
import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.example.mekanismmagic.integration.mekextras.ExtraNatureAltarFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras.ExtraNatureAltarFactoryContainer;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeEnergy;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the real Extras installer and server scheduler, without AE mocks. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NatureAltarExtrasGameTests {
    private static final BlockPos FIRST = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND = new BlockPos(3, 1, 1);
    private static final String[] TIERS = {"absolute", "supreme", "cosmic", "infinite"};

    private NatureAltarExtrasGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void bothNativeCatalystsExposeSoulSoilChoicesWithoutOverridingExactPatterns(GameTestHelper helper) {
        var ghastTear = recipe(helper, "ghast_tear");
        var soulSand = recipe(helper, "soul_sand_crushing");
        ItemStack soil = new ItemStack(Items.SOUL_SOIL);
        ResourceLocation conversionId = ResourceLocation.fromNamespaceAndPath(
                "naturesaura", "conversion_catalyst");
        ResourceLocation crushingId = ResourceLocation.fromNamespaceAndPath(
                "naturesaura", "crushing_catalyst");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(conversionId)
                        && BuiltInRegistries.ITEM.containsKey(crushingId),
                "Nature's Aura 41.9 catalyst fixture is missing");
        ItemStack conversion = new ItemStack(BuiltInRegistries.ITEM.get(conversionId));
        ItemStack crushing = new ItemStack(BuiltInRegistries.ITEM.get(crushingId));
        helper.assertTrue(ghastTear.input().test(soil)
                        && soulSand.input().test(soil)
                        && ghastTear.catalyst().test(conversion)
                        && soulSand.catalyst().test(crushing),
                "Expected native soul-soil recipe conflict changed");

        List<ItemStack> both = List.of(conversion, crushing);
        var choices = NatureAuraRecipeBridge.matchingAltarRecipes(
                helper.getLevel(), soil, both);
        helper.assertTrue(choices.size() == 2
                        && choices.get(0).id().equals(ghastTear.id())
                        && choices.get(1).id().equals(soulSand.id()),
                "Two installed catalysts did not expose both native soul-soil recipes in stable order");
        helper.assertTrue(NatureAuraRecipeBridge.findAltarRecipe(
                        helper.getLevel(), soil, both, null, soulSand.id())
                        .map(view -> view.id().equals(soulSand.id())).orElse(false),
                "Explicit choice did not select the crushing recipe");
        helper.assertTrue(NatureAuraRecipeBridge.findAltarRecipe(
                        helper.getLevel(), soil, both, ghastTear.id(), soulSand.id())
                        .map(view -> view.id().equals(ghastTear.id())).orElse(false),
                "A running recipe lock was overridden by the ordinary recipe selector");
        helper.assertTrue(NatureAuraRecipeBridge.findAltarRecipe(
                        helper.getLevel(), soil, List.of(conversion), null, soulSand.id())
                        .map(view -> view.id().equals(ghastTear.id())).orElse(false),
                "A removed crushing catalyst left an invalid recipe selected");

        var factory = (NatureAltarFactoryBlockEntity) place(helper, FIRST, "basic", 3);
        var inputs = automation(factory).mekanismMagicPersistentInputs();
        helper.assertTrue(inputs.size() == 2,
                "Nature factory did not provide a separate slot for each native catalyst");
        inputs.get(0).setStack(conversion.copy());
        inputs.get(1).setStack(crushing.copy());
        helper.assertTrue(factory.getCatalystStacks().size() == 2
                        && ItemStack.matches(factory.getCatalystStacks().get(0), conversion)
                        && ItemStack.matches(factory.getCatalystStacks().get(1), crushing),
                "Factory did not retain both nonconsumed catalysts at the same time");
        helper.assertTrue(factory.acceptNatureAltarPattern(
                        soulSand.id(), marked(helper, soulSand, 1))
                        && soulSand.id().equals(data(helper, factory).lockedRecipe(0)),
                "An exact AE crushing pattern followed the ambiguous default recipe instead");

        helper.setBlock(SECOND, NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get());
        var processor = (NatureAltarProcessorBlockEntity) helper.getBlockEntity(SECOND);
        var processorCatalysts = automation(processor).mekanismMagicPersistentInputs();
        helper.assertTrue(processorCatalysts.size() == 2,
                "Single Nature infusion machine did not expose both catalyst slots");
        processorCatalysts.get(0).setStack(conversion.copy());
        processorCatalysts.get(1).setStack(crushing.copy());
        var processorInput = automation(processor).mekanismMagicPatternInputs().getFirst();
        processorInput.setStack(soil.copy());
        processor.cycleMatchingRecipe();
        helper.assertTrue(ItemStack.matches(processor.getSelectedRecipeOutput(),
                        soulSand.output()),
                "Single-machine recipe button did not switch ambiguous soul soil to soul sand");
        processorInput.setStack(ItemStack.EMPTY);
        helper.assertTrue(processor.acceptNatureAltarPattern(
                        ghastTear.id(), marked(helper, ghastTear, 1))
                        && ghastTear.id().equals(processor.getLockedRecipe()),
                "Single-machine exact AE recipe followed the ordinary selector instead of its signature");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void legacyNatureFactoryMachineItemsPreserveOldEnergyAndCatalystSlots(GameTestHelper helper) {
        ResourceLocation tabletId = ResourceLocation.fromNamespaceAndPath(
                "mekanism", "energy_tablet");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(tabletId),
                "Mekanism energy tablet fixture is unavailable");
        ItemStack legacyEnergy = new ItemStack(BuiltInRegistries.ITEM.get(tabletId));
        var recipe = recipe(helper, "glowstone");
        ItemStack legacyInput = representative(recipe.input());
        ItemStack legacyOutput = recipe.output();
        ItemStack legacyCatalyst = representative(recipe.catalyst());
        String[] tiers = {"basic", "infinite"};
        int[] lanes = {3, 17};
        BlockPos[] positions = {FIRST, SECOND};
        for (int index = 0; index < tiers.length; index++) {
            TileEntityMekanism tile = place(helper, positions[index], tiers[index], lanes[index]);
            var machine = automation(tile);
            List<IInventorySlot> currentSlots = tile.getInventorySlots(null);
            IInventorySlot newCatalyst = machine.mekanismMagicPersistentInputs().get(1);
            helper.assertTrue(currentSlots.contains(newCatalyst),
                    tiers[index] + " new catalyst is absent from the physical inventory");
            // A .50 machine item has every old physical slot, but no second
            // catalyst. Model precisely that old AttachedItems length/order.
            List<IInventorySlot> legacySlots = currentSlots.stream()
                    .filter(slot -> slot != newCatalyst
                            && !(slot instanceof CatalystLibraryWindowSlot))
                    .toList();
            int inputIndex = legacySlots.indexOf(machine.mekanismMagicPatternInputs().getFirst());
            int outputIndex = legacySlots.indexOf(machine.mekanismMagicPatternOutputs().getFirst());
            int catalystIndex = legacySlots.indexOf(machine.mekanismMagicPersistentInputs().getFirst());
            int energyIndex = -1;
            for (int slot = 0; slot < legacySlots.size(); slot++) {
                if (legacySlots.get(slot) instanceof EnergyInventorySlot) {
                    energyIndex = slot;
                    break;
                }
            }
            helper.assertTrue(inputIndex >= 0 && outputIndex >= 0
                            && catalystIndex == 2 * lanes[index]
                            && energyIndex == 2 * lanes[index] + 1,
                    tiers[index] + " no longer matches the pre-second-catalyst factory save layout");
            List<ItemStack> legacyItems = new ArrayList<>(legacySlots.size());
            for (int slot = 0; slot < legacySlots.size(); slot++) {
                legacyItems.add(ItemStack.EMPTY);
            }
            legacyItems.set(inputIndex, legacyInput.copy());
            legacyItems.set(outputIndex, legacyOutput.copy());
            legacyItems.set(catalystIndex, legacyCatalyst.copy());
            legacyItems.set(energyIndex, legacyEnergy.copy());
            ItemStack oldMachineItem = new ItemStack(tile.getBlockState().getBlock());
            oldMachineItem.set(MekanismDataComponents.ATTACHED_ITEMS,
                    new AttachedItems(List.copyOf(legacyItems)));
            ItemStack before = oldMachineItem.copy();
            tile.applyComponentsFromItemStack(oldMachineItem);
            assertStack(helper, machine.mekanismMagicPatternInputs().getFirst().getStack(),
                    legacyInput, tiers[index] + " legacy input");
            assertStack(helper, machine.mekanismMagicPatternOutputs().getFirst().getStack(),
                    legacyOutput, tiers[index] + " legacy output");
            assertStack(helper, machine.mekanismMagicPersistentInputs().getFirst().getStack(),
                    legacyCatalyst, tiers[index] + " legacy catalyst");
            assertStack(helper, legacySlots.get(energyIndex).getStack(),
                    legacyEnergy, tiers[index] + " legacy energy item");
            helper.assertTrue(newCatalyst.isEmpty()
                            && ItemStack.matches(before, oldMachineItem),
                    tiers[index] + " old item migrated with a populated second catalyst or mutated attachment");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void legacyNatureFactoryWorldNbtMovesEnergyPastNewCatalystSlot(GameTestHelper helper) {
        ResourceLocation tabletId = ResourceLocation.fromNamespaceAndPath(
                "mekanism", "energy_tablet");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(tabletId),
                "Mekanism energy tablet fixture is unavailable");
        ItemStack legacyEnergy = new ItemStack(BuiltInRegistries.ITEM.get(tabletId));
        var recipe = recipe(helper, "glowstone");
        ItemStack legacyInput = representative(recipe.input());
        ItemStack legacyOutput = recipe.output();
        ItemStack legacyCatalyst = representative(recipe.catalyst());
        String[] tiers = {"basic", "infinite"};
        int[] lanes = {3, 17};
        for (int index = 0; index < tiers.length; index++) {
            TileEntityMekanism source = place(helper, FIRST, tiers[index], lanes[index]);
            var sourceMachine = automation(source);
            sourceMachine.mekanismMagicPatternInputs().getFirst().setStack(legacyInput.copy());
            sourceMachine.mekanismMagicPatternOutputs().getFirst().setStack(legacyOutput.copy());
            sourceMachine.mekanismMagicPersistentInputs().getFirst().setStack(legacyCatalyst.copy());
            IInventorySlot newCatalyst = sourceMachine.mekanismMagicPersistentInputs().get(1);
            List<IInventorySlot> legacySlots = source.getInventorySlots(null).stream()
                    .filter(slot -> slot != newCatalyst
                            && !(slot instanceof CatalystLibraryWindowSlot))
                    .toList();
            int oldEnergyIndex = 2 * lanes[index] + 1;
            helper.assertTrue(legacySlots.get(oldEnergyIndex) instanceof EnergyInventorySlot,
                    tiers[index] + " old world inventory did not place energy after the catalyst");
            legacySlots.get(oldEnergyIndex).setStack(legacyEnergy.copy());

            CompoundTag oldWorldTag = new CompoundTag();
            ContainerType.ITEM.saveTo(helper.getLevel().registryAccess(),
                    oldWorldTag, legacySlots);
            helper.assertTrue(oldWorldTag.contains(ContainerType.ITEM.getTag(), Tag.TAG_LIST)
                            && !oldWorldTag.contains("nature_altar_catalyst_slots"),
                    tiers[index] + " fixture did not create an unmarked old-world inventory");
            CompoundTag untouchedOldTag = oldWorldTag.copy();
            TileEntityMekanism restored = place(helper, SECOND, tiers[index], lanes[index]);
            restored.loadAdditional(oldWorldTag, helper.getLevel().registryAccess());
            var restoredMachine = automation(restored);
            assertStack(helper, restoredMachine.mekanismMagicPatternInputs().getFirst().getStack(),
                    legacyInput, tiers[index] + " world NBT input");
            assertStack(helper, restoredMachine.mekanismMagicPatternOutputs().getFirst().getStack(),
                    legacyOutput, tiers[index] + " world NBT output");
            assertStack(helper, restoredMachine.mekanismMagicPersistentInputs().getFirst().getStack(),
                    legacyCatalyst, tiers[index] + " world NBT old catalyst");
            helper.assertTrue(restoredMachine.mekanismMagicPersistentInputs().get(1).isEmpty(),
                    tiers[index] + " old world NBT populated the new catalyst slot");
            assertStack(helper, restored.getInventorySlots(null).get(oldEnergyIndex
                            + 1 + NatureAltarCatalystLibrary.WINDOW_SIZE).getStack(),
                    legacyEnergy, tiers[index] + " migrated world NBT energy item");
            helper.assertTrue(untouchedOldTag.equals(oldWorldTag),
                    tiers[index] + " migration modified the loaded save tag in place");
            CompoundTag newWorldTag = new CompoundTag();
            restored.saveAdditional(newWorldTag, helper.getLevel().registryAccess());
            helper.assertTrue(newWorldTag.getInt("nature_altar_catalyst_slots")
                            == NatureAltarCatalystLibrary.SAVED_CATALYST_SLOTS,
                    tiers[index] + " new world NBT lacked the current catalyst-layout marker");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void autoSortEvenlySplitsOrdinaryInputsAcrossStandardAndExtrasLanes(GameTestHelper helper) {
        var recipe = recipe(helper, "glowstone");
        ItemStack input = representative(recipe.input());
        ItemStack catalyst = representative(recipe.catalyst());
        String[] tiers = {"basic", "infinite"};
        int[] lanes = {3, 17};
        int[] perLane = {3, 2};
        for (int index = 0; index < tiers.length; index++) {
            helper.setBlock(FIRST, Blocks.AIR);
            TileEntityMekanism tile = place(helper, FIRST, tiers[index], lanes[index]);
            var machine = automation(tile);
            machine.mekanismMagicPersistentInputs().getFirst().setStack(catalyst.copy());
            machine.mekanismMagicPatternInputs().getFirst().setStack(
                    input.copyWithCount(lanes[index] * perLane[index]));
            if (tile instanceof NatureAltarFactoryBlockEntity ordinary) {
                if (!ordinary.isSorting()) ordinary.toggleSorting();
                helper.assertTrue(ordinary.isSorting(),
                        "Standard Nature factory refused to enable auto-sort");
            } else if (tile instanceof ExtraNatureAltarFactoryBlockEntity extras) {
                if (!extras.isSorting()) extras.toggleSorting();
                helper.assertTrue(extras.isSorting(),
                        "Extras Nature factory refused to enable auto-sort");
            } else {
                throw new AssertionError("Unknown Nature factory tier: " + tiers[index]);
            }
            updateFactory(tile);
            for (int lane = 0; lane < lanes[index]; lane++) {
                ItemStack actual = machine.mekanismMagicPatternInputs().get(lane).getStack();
                helper.assertTrue(actual.getCount() == perLane[index]
                                && ItemStack.isSameItemSameComponents(actual, input),
                        "Auto-sort did not evenly distribute " + tiers[index]
                                + " Nature infusion input in lane " + lane);
            }
            helper.assertTrue(inputCount(tile) == lanes[index] * perLane[index]
                            && ItemStack.matches(machine.mekanismMagicPersistentInputs()
                                    .getFirst().getStack(), catalyst),
                    "Auto-sort consumed or changed the ordinary input/catalyst in " + tiers[index]);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void autoSortSpreadsSoulSoilBeforeCatalystsAreInstalled(GameTestHelper helper) {
        String[] tiers = {"basic", "infinite"};
        int[] lanes = {3, 17};
        for (int index = 0; index < tiers.length; index++) {
            helper.setBlock(FIRST, Blocks.AIR);
            TileEntityMekanism tile = place(helper, FIRST, tiers[index], lanes[index]);
            var machine = automation(tile);
            helper.assertTrue(machine.mekanismMagicPersistentInputs()
                            .stream().allMatch(IInventorySlot::isEmpty),
                    "Nature factory fixture unexpectedly contains a catalyst");
            machine.mekanismMagicPatternInputs().getFirst().setStack(
                    new ItemStack(Items.SOUL_SOIL, 64));
            if (tile instanceof NatureAltarFactoryBlockEntity ordinary) {
                if (!ordinary.isSorting()) ordinary.toggleSorting();
            } else if (tile instanceof ExtraNatureAltarFactoryBlockEntity extras) {
                if (!extras.isSorting()) extras.toggleSorting();
            } else {
                throw new AssertionError("Unknown Nature factory tier: " + tiers[index]);
            }
            updateFactory(tile);
            for (int lane = 0; lane < lanes[index]; lane++) {
                ItemStack actual = machine.mekanismMagicPatternInputs()
                        .get(lane).getStack();
                int expected = 64 / lanes[index]
                        + (lane < 64 % lanes[index] ? 1 : 0);
                helper.assertTrue(actual.is(Items.SOUL_SOIL)
                                && actual.getCount() == expected,
                        "Auto-sort failed without a catalyst in " + tiers[index]
                                + " Nature factory lane " + lane);
            }
            helper.assertTrue(inputCount(tile) == 64,
                    "Auto-sort changed the total amount of Soul Soil");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void autoSortLeavesRunningExactPatternLaneLocked(GameTestHelper helper) {
        var recipe = recipe(helper, "glowstone");
        ItemStack input = representative(recipe.input());
        TileEntityMekanism tile = place(helper, FIRST, "basic", 3);
        var machine = automation(tile);
        machine.mekanismMagicPersistentInputs().getFirst().setStack(
                representative(recipe.catalyst()));
        machine.mekanismMagicPatternInputs().getFirst().setStack(
                marked(helper, recipe, 2));
        fillEnergy(tile);
        fund(helper, FIRST, recipe.auraCost() * 2);
        for (int warmup = 0; warmup < 5
                && data(helper, tile).progress[0] == 0; warmup++) {
            updateFactory(tile);
        }
        var before = data(helper, tile);
        helper.assertTrue(before.progress[0] > 0
                        && recipe.id().equals(before.lockedRecipe(0))
                        && before.contextRemaining(0) == 2,
                "Exact-pattern lane did not establish a running recipe context");
        ItemStack lockedInput = machine.mekanismMagicPatternInputs()
                .getFirst().getStack().copy();
        machine.mekanismMagicPatternInputs().get(1).setStack(
                input.copyWithCount(8));
        var ordinary = (NatureAltarFactoryBlockEntity) tile;
        if (!ordinary.isSorting()) ordinary.toggleSorting();
        helper.assertTrue(ordinary.isSorting(),
                "Standard Nature factory refused to enable auto-sort");
        updateFactory(tile);
        var after = data(helper, tile);
        helper.assertTrue(ItemStack.matches(lockedInput,
                        machine.mekanismMagicPatternInputs().getFirst().getStack())
                        && recipe.id().equals(after.lockedRecipe(0))
                        && after.lockedRecipeSignature(0)
                                .equals(before.lockedRecipeSignature(0))
                        && after.contextRemaining(0) == before.contextRemaining(0)
                        && after.progress[0] >= before.progress[0],
                "Auto-sort moved or invalidated a running exact-pattern lane");
        for (int lane = 1; lane < 3; lane++) {
            ItemStack actual = machine.mekanismMagicPatternInputs().get(lane).getStack();
            helper.assertTrue(actual.getCount() == 4
                            && ItemStack.isSameItemSameComponents(actual, input),
                    "Auto-sort did not split ordinary input around the locked lane " + lane);
        }
        // A missing catalyst must not turn the protected AE lane into an
        // ordinary sorting source when the remaining plain inputs are spread.
        machine.mekanismMagicPersistentInputs().getFirst().setStack(ItemStack.EMPTY);
        machine.mekanismMagicPatternInputs().get(1).setStack(
                new ItemStack(Items.SOUL_SOIL, 8));
        machine.mekanismMagicPatternInputs().get(2).setStack(ItemStack.EMPTY);
        updateFactory(tile);
        var catalystAbsent = data(helper, tile);
        helper.assertTrue(ItemStack.matches(lockedInput,
                        machine.mekanismMagicPatternInputs().getFirst().getStack())
                        && recipe.id().equals(catalystAbsent.lockedRecipe(0))
                        && catalystAbsent.contextRemaining(0) == before.contextRemaining(0),
                "Catalyst-free auto-sort changed a running AE recipe lane");
        for (int lane = 1; lane < 3; lane++) {
            ItemStack actual = machine.mekanismMagicPatternInputs()
                    .get(lane).getStack();
            helper.assertTrue(actual.is(Items.SOUL_SOIL)
                            && actual.getCount() == 4,
                    "Catalyst-free auto-sort failed around the AE lane " + lane);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void allEightFactoryTiersShareOneLaneProfileAndChargeOnlyWorkingLanes(GameTestHelper helper) {
        String[] tiers = {"basic", "advanced", "elite", "ultimate", "absolute", "supreme", "cosmic", "infinite"};
        long perLane = MagicMachineEnergyProfiles.MATERIAL_PROCESSING;
        // The native glowstone recipe has a real catalyst and its 3,000 Aura fee
        // fits each lane's 5,000 Aura/t sided funding budget. Infused stone has
        // no catalyst and costs 7,500 Aura, so it cannot serve this one-tick fixture.
        var recipe = recipe(helper, "glowstone");
        helper.assertTrue(recipe.duration() > 12, "The energy fixture recipe is too short for bounded scheduler checks");
        helper.assertTrue(!recipe.catalyst().isEmpty()
                        && recipe.auraCost() > 0 && recipe.auraCost() <= NatureAuraMachineConfig.ALTAR_TRANSFER,
                "The energy fixture needs a real catalyst and an Aura fee within the actual per-lane transfer budget");
        for (int index = 0; index < tiers.length; index++) {
            int lanes = 3 + index * 2;
            helper.setBlock(FIRST, Blocks.AIR);
            TileEntityMekanism tile = place(helper, FIRST, tiers[index], lanes);
            var machine = automation(tile);
            var energy = machine.mekanismMagicEnergyContainer();
            AttributeEnergy attribute = Attribute.get(tile.getBlockHolder(), AttributeEnergy.class);
            helper.assertTrue(energy instanceof MachineEnergyContainer<?>,
                    "Nature factory lost its native machine energy container: " + tiers[index]);
            var nativeEnergy = (MachineEnergyContainer<?>) energy;
            helper.assertTrue(attribute != null && attribute.getUsage() == perLane
                            && nativeEnergy.getBaseEnergyPerTick() == perLane
                            && nativeEnergy.getEnergyPerTick() == perLane
                            && nativeEnergy.getBaseMaxEnergy() == NatureAuraMachineConfig.altarFactoryEnergyCapacity(lanes),
                    "Factory registration/container multiplied its per-lane profile or changed storage: " + tiers[index]);
            machine.mekanismMagicPersistentInputs().getFirst().setStack(representative(recipe.catalyst()));
            machine.mekanismMagicPatternInputs().getFirst().setStack(marked(helper, recipe, 1));
            fillEnergy(tile);
            fund(helper, FIRST, recipe.auraCost() * lanes);
            for (int warmup = 0; warmup < 5 && Arrays.stream(data(helper, tile).progress).sum() == 0; warmup++) {
                updateFactory(tile);
            }
            int progressBefore = Arrays.stream(data(helper, tile).progress).sum();
            helper.assertTrue(progressBefore > 0 && outputCount(tile) == 0,
                    "The real single-lane energy fixture failed to start: " + tiers[index]);
            long energyBefore = energy.getEnergy();
            updateFactory(tile);
            helper.assertTrue(energyBefore - energy.getEnergy() == perLane
                            && Arrays.stream(data(helper, tile).progress).sum() == progressBefore + 1,
                    "One working lane paid for idle lanes or a multiplied factory base: " + tiers[index]);

            for (int lane = 1; lane < lanes; lane++) {
                machine.mekanismMagicPatternInputs().get(lane).setStack(marked(helper, recipe, 1));
            }
            for (int warmup = 0; warmup < 5
                    && Arrays.stream(data(helper, tile).progress).anyMatch(progress -> progress == 0); warmup++) {
                updateFactory(tile);
            }
            helper.assertTrue(Arrays.stream(data(helper, tile).progress).allMatch(progress -> progress > 0)
                            && outputCount(tile) == 0,
                    "Not all funded native factory lanes started: " + tiers[index]);
            energyBefore = energy.getEnergy();
            progressBefore = Arrays.stream(data(helper, tile).progress).sum();
            updateFactory(tile);
            helper.assertTrue(energyBefore - energy.getEnergy() == perLane * lanes
                            && Arrays.stream(data(helper, tile).progress).sum() == progressBefore + lanes
                            && requireAura(helper, FIRST, Direction.UP).getStoredAura() == recipe.auraCost() * lanes,
                    "Parallel Nature lanes duplicated their energy multiplier or changed the Aura commit point: " + tiers[index]);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void compactMenusKeepNativeSlotsAcrossAllEightFactoryTiers(GameTestHelper helper) {
        String[] tiers = {"basic", "advanced", "elite", "ultimate", "absolute", "supreme", "cosmic", "infinite"};
        int[] widths = {184, 184, 184, 218, 256, 294, 332, 370};
        int[] inventoryXs = {12, 12, 12, 29, 48, 67, 86, 105};
        int[] firstProcessXs = {55, 35, 29, 27, 27, 27, 27, 27};
        int[] processSpacings = {38, 26, 19, 19, 19, 19, 19, 19};
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int index = 0; index < tiers.length; index++) {
            int lanes = 3 + index * 2;
            TileEntityMekanism tile = place(helper, FIRST, tiers[index], lanes);
            var menu = tile instanceof NatureAltarFactoryBlockEntity ordinary
                    ? new NatureAltarFactoryContainer(100 + index, player.getInventory(), ordinary)
                    : new ExtraNatureAltarFactoryContainer(100 + index, player.getInventory(),
                            (ExtraNatureAltarFactoryBlockEntity) tile);
            IInventorySlot secondCatalyst = automation(tile)
                    .mekanismMagicPersistentInputs().get(1);
            List<IInventorySlot> tileSlots = tile.getInventorySlots(null);
            helper.assertTrue(tileSlots.contains(secondCatalyst)
                            && menu.getInventoryContainerSlots().stream().anyMatch(slot ->
                            slot.getInventorySlot() == secondCatalyst),
                    "Second catalyst is not registered in both tile and factory menu: tier="
                            + tiers[index] + ", tileSlots=" + tileSlots.size()
                            + ", tileContains=" + tileSlots.contains(secondCatalyst)
                            + ", menuSlots=" + menu.getInventoryContainerSlots().size()
                            + ", menuBackingTypes=" + menu.getInventoryContainerSlots().stream()
                            .map(slot -> slot.getInventorySlot().getClass().getSimpleName())
                            .distinct().toList());
            int[][] machineCoordinates = new int[2 * lanes + 3][2];
            machineCoordinates[0] = new int[]{7, 13};
            machineCoordinates[1] = new int[]{7, 35};
            machineCoordinates[2] = new int[]{7, 57};
            for (int lane = 0; lane < lanes; lane++) {
                int x = firstProcessXs[index] + lane * processSpacings[index];
                machineCoordinates[3 + lane * 2] = new int[]{x, 13};
                machineCoordinates[4 + lane * 2] = new int[]{x, 57};
            }
            NatureAuraIntegrationGameTests.verifyCompactMenuLayout(helper,
                    menu, player, widths[index], NatureAuraScreenLayout.COMPACT_HEIGHT,
                    inventoryXs[index], NatureAuraScreenLayout.COMPACT_INVENTORY_Y,
                    NatureAuraScreenLayout.COMPACT_LABEL_Y,
                    NatureAuraScreenLayout.COMPACT_WARNING_Y, 1, machineCoordinates);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void auraSideControlsCoverAllEightFactoryTiers(GameTestHelper helper) {
        String[] tiers = {"basic", "advanced", "elite", "ultimate", "absolute", "supreme", "cosmic", "infinite"};
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int index = 0; index < tiers.length; index++) {
            TileEntityMekanism tile = place(helper, FIRST, tiers[index], 3 + index * 2);
            fillEnergy(tile);
            automation(tile).mekanismMagicPatternInputs().getFirst().setStack(
                    named(representative(recipe(helper, "infused_stone").input()), "Preserved Aura-side input"));
            automation(tile).mekanismMagicPatternOutputs().getFirst().setStack(
                    named(new ItemStack(Items.COBBLESTONE, 7), "Preserved Aura-side output"));
            var menu = tile instanceof NatureAltarFactoryBlockEntity ordinary
                    ? new NatureAltarFactoryContainer(90 + index, player.getInventory(), ordinary)
                    : new ExtraNatureAltarFactoryContainer(90 + index, player.getInventory(),
                            (ExtraNatureAltarFactoryBlockEntity) tile);
            NatureAuraIntegrationGameTests.verifyAuraSideMenu(helper, menu, player);
            var host = (AuraSideModeHost) tile;
            int stored = host.getStoredAura();
            int capacity = host.getAuraCapacity();
            helper.assertTrue(!host.isCreativeAuraActive(),
                    "Factory tier implicitly enabled creative Aura: " + tiers[index]);
            tile.getComponent().addUpgrades(MagicUpgrades.creativeMagic(), 1);
            helper.assertTrue(host.isCreativeAuraActive()
                            && host.getStoredAura() == stored && host.getAuraCapacity() == capacity,
                    "Creative Aura display changed actual buffer/capacity: " + tiers[index]);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void auraSideBatchesNotifyOnlyOnceAndNoOpDoesNotInvalidate(GameTestHelper helper) {
        int[] notifications = new int[3];
        var storage = new NatureAuraStorageComponent(helper::getLevel, () -> false,
                () -> notifications[0]++, () -> notifications[1]++, () -> notifications[2]++,
                1_000, 250, NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        helper.assertTrue(storage.exactStore(137, false), "Could not fund storage component fixture");
        Arrays.fill(notifications, 0);
        storage.setAuraSideMode(Direction.UP, NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        helper.assertTrue(Arrays.equals(notifications, new int[]{0, 0, 0}),
                "Unchanged Aura side mode dirtied storage or invalidated capabilities");
        storage.setAuraSideMode(Direction.UP, NatureAuraMachineBlockEntity.AuraSideMode.OUTPUT);
        helper.assertTrue(Arrays.equals(notifications, new int[]{1, 1, 0}),
                "One Aura side change did not notify once or triggered recipe/resource changes");
        var batch = new EnumMap<Direction, NatureAuraMachineBlockEntity.AuraSideMode>(Direction.class);
        for (Direction side : Direction.values()) {
            batch.put(side, NatureAuraMachineBlockEntity.AuraSideMode.NONE);
        }
        storage.setAuraSideModes(batch);
        helper.assertTrue(Arrays.equals(notifications, new int[]{2, 2, 0}),
                "Six Aura side changes did not share one dirty/capability notification");
        storage.setAuraSideModes(batch);
        helper.assertTrue(Arrays.equals(notifications, new int[]{2, 2, 0})
                        && storage.getStoredAura() == 137 && storage.getAuraCapacity() == 1_000,
                "No-op Aura batch notified listeners or changed the buffer");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void energyUpgradesPreserveFullFeAcrossNativeAndExtrasInstallers(GameTestHelper helper) {
        TileEntityMekanism basic = place(helper, FIRST, "basic", 3);
        basic.getComponent().addUpgrades(Upgrade.ENERGY, 8);
        helper.assertTrue(basic.getComponent().getUpgrades(Upgrade.ENERGY) == 8,
                "Basic Nature factory did not install all eight energy upgrades");
        fillEnergy(basic);
        long expectedEnergy = automation(basic).mekanismMagicEnergyContainer().getEnergy();
        String[] tiers = {"advanced", "elite", "ultimate", "absolute", "supreme", "cosmic", "infinite"};
        for (int index = 0; index < tiers.length; index++) {
            int lanes = 5 + 2 * index;
            helper.assertTrue(expectedEnergy > NatureAuraMachineConfig.ALTAR_ENERGY_CAPACITY * lanes,
                    "FE preservation precondition did not exceed the target's unupgraded capacity: " + tiers[index]);
            TileEntityMekanism upgraded = install(helper, FIRST, tiers[index], lanes);
            helper.assertTrue(upgraded.getComponent().getUpgrades(Upgrade.ENERGY) == 8,
                    tiers[index] + " installer lost the energy upgrades");
            long actualEnergy = automation(upgraded).mekanismMagicEnergyContainer().getEnergy();
            helper.assertTrue(actualEnergy == expectedEnergy,
                    tiers[index] + " installer clamped FE before restoring energy upgrades: "
                            + expectedEnergy + "->" + actualEnergy);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 400)
    public static void realInstallerChainPreservesRunningNatureState(GameTestHelper helper) {
        NatureAltarFactoryBlockEntity ultimate = (NatureAltarFactoryBlockEntity)
                place(helper, FIRST, "ultimate", 9);
        var recipe = recipe(helper, "glowstone");
        var automation = automation(ultimate);
        ItemStack catalyst = named(representative(recipe.catalyst()), "Persistent catalyst");
        automation.mekanismMagicPersistentInputs().getFirst().setStack(catalyst);
        ItemStack secondCatalyst = named(new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("naturesaura", "conversion_catalyst"))),
                "Other persistent catalyst");
        automation.mekanismMagicPersistentInputs().get(1).setStack(secondCatalyst);
        ItemStack input = named(representative(recipe.input()).copyWithCount(3), "Input components");
        helper.assertTrue(ultimate.acceptNatureAltarPattern(recipe.id(), input),
                "Ultimate factory refused a valid signed batch");
        automation.mekanismMagicPatternInputs().get(8).setStack(
                named(marked(helper, recipe, 2), "Last original input components"));
        ItemStack retainedOutput = named(new ItemStack(Items.COBBLESTONE, 7), "Output components");
        automation.mekanismMagicPatternOutputs().get(7).setStack(retainedOutput);
        ultimate.getComponent().addUpgrades(Upgrade.ENERGY, 2);
        ultimate.getComponent().addUpgrades(Upgrade.SPEED, 1);
        ultimate.configComponent.getConfig(TransmissionType.ITEM)
                .setDataType(DataType.OUTPUT, RelativeSide.LEFT);
        ultimate.cycleAuraSideMode(Direction.DOWN.ordinal());
        ultimate.cycleAuraSideMode(Direction.NORTH.ordinal());
        ultimate.cycleAuraSideMode(Direction.NORTH.ordinal());
        ultimate.cycleAuraSideMode(Direction.WEST.ordinal());
        ultimate.cycleAuraSideMode(Direction.WEST.ordinal());
        ultimate.cycleAuraSideMode(Direction.WEST.ordinal());
        fillEnergy(ultimate);
        fund(helper, FIRST, recipe.auraCost() * 6);
        AtomicReference<TileEntityMekanism> upgraded = new AtomicReference<>();

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ultimate.getProgress(0) > 0 && ultimate.getProgress(8) > 0,
                        "Ultimate recipe never began before the installer chain"))
                .thenExecute(() -> {
                    FactoryState expected = state(helper, ultimate);
                    helper.assertTrue(expected.progress()[0] > 0
                                    && expected.contexts()[0] == 3
                                    && recipe.id().equals(expected.locks()[0])
                                    && expected.contexts()[8] == 2 && expected.progress()[8] > 0
                                    && recipe.id().equals(expected.locks()[8]),
                            "Upgrade precondition lacks real running progress and a counted recipe lock");
                    helper.assertTrue(expected.aura().boundAuraType().equals(
                                    IAuraType.forLevel(helper.getLevel()).getName()),
                            "Upgrade precondition did not bind the actual level Aura type");
                    for (int index = 0; index < TIERS.length; index++) {
                        TileEntityMekanism next = install(helper, FIRST, TIERS[index], 11 + 2 * index);
                        assertRetained(helper, expected, state(helper, next), TIERS[index]);
                        helper.assertTrue(((TileEntityConfigurableMachine) next).configComponent
                                        .getConfig(TransmissionType.ITEM).getDataType(RelativeSide.LEFT)
                                        == DataType.OUTPUT,
                                "Installer lost the configured item output side");
                        helper.assertTrue(capability(helper, FIRST, Direction.WEST) == null,
                                "Installer exposed Aura on the disabled side");
                        helper.assertTrue(requireAura(helper, FIRST, Direction.DOWN).storeAura(1, true) == 0,
                                "Installer changed the output-only Aura side into an input");
                        upgraded.set(next);
                    }
                    var infinite = automation(requireUpgraded(helper, upgraded));
                    infinite.mekanismMagicPatternInputs().get(16).setStack(marked(helper, recipe, 1));
                })
                .thenWaitUntil(() -> {
                    var infinite = automation(requireUpgraded(helper, upgraded));
                    helper.assertTrue(infinite.mekanismMagicPatternInputs().getFirst().isEmpty()
                                    && infinite.mekanismMagicPatternInputs().get(8).isEmpty()
                                    && infinite.mekanismMagicPatternOutputs().get(16).getCount()
                                    == recipe.output().getCount(),
                            "Upgraded running lane or the new seventeenth lane did not finish");
                })
                .thenExecute(() -> {
                    var infinite = automation(requireUpgraded(helper, upgraded));
                    assertStack(helper, infinite.mekanismMagicPatternOutputs().getFirst().getStack(),
                            recipe.output().copyWithCount(recipe.output().getCount() * 3), "Resumed output");
                    assertStack(helper, infinite.mekanismMagicPatternOutputs().get(8).getStack(),
                            recipe.output().copyWithCount(recipe.output().getCount() * 2), "Resumed last original lane");
                    assertStack(helper, infinite.mekanismMagicPatternOutputs().get(7).getStack(),
                            retainedOutput, "Retained output components");
                    assertStack(helper, infinite.mekanismMagicPersistentInputs().getFirst().getStack(),
                            catalyst, "Persistent catalyst after six completions");
                    assertStack(helper, infinite.mekanismMagicPersistentInputs().get(1).getStack(),
                            secondCatalyst, "Second persistent catalyst after six completions");
                    helper.assertTrue(requireAura(helper, FIRST, Direction.UP).getStoredAura() == 0,
                            "Upgrade/resumption did not debit exactly six complete recipe costs");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 160)
    public static void everyExtrasTierExecutesItsLastLane(GameTestHelper helper) {
        BlockPos[] positions = {FIRST, SECOND, new BlockPos(1, 1, 3), new BlockPos(3, 1, 3)};
        TileEntityMekanism[] machines = new TileEntityMekanism[TIERS.length];
        var recipe = recipe(helper, "infused_stone");
        for (int index = 0; index < TIERS.length; index++) {
            machines[index] = place(helper, positions[index], TIERS[index], 11 + 2 * index);
            fillEnergy(machines[index]);
            fund(helper, positions[index], recipe.auraCost());
            automation(machines[index]).mekanismMagicPatternInputs().get(10 + 2 * index)
                    .setStack(marked(helper, recipe, 1));
        }
        helper.startSequence().thenWaitUntil(() -> {
            for (int index = 0; index < machines.length; index++) {
                var machine = automation(machines[index]);
                assertStack(helper, machine.mekanismMagicPatternOutputs().get(10 + 2 * index).getStack(),
                        recipe.output(), TIERS[index] + " last-lane output");
                helper.assertTrue(machine.mekanismMagicPatternInputs().stream().allMatch(IInventorySlot::isEmpty),
                        TIERS[index] + " did not consume exactly its one input");
                helper.assertTrue(requireAura(helper, positions[index], Direction.UP).getStoredAura() == 0,
                        TIERS[index] + " did not debit exactly one native Aura cost");
                helper.assertTrue(machine.mekanismMagicEnergyContainer().getEnergy()
                                < machine.mekanismMagicEnergyContainer().getMaxEnergy(),
                        TIERS[index] + " produced output without spending FE");
            }
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void seventeenLanesCannotOverdrawOneSharedAuraPool(GameTestHelper helper) {
        TileEntityMekanism infinite = place(helper, FIRST, "infinite", 17);
        var machine = automation(infinite);
        var recipe = recipe(helper, "glowstone");
        machine.mekanismMagicPersistentInputs().getFirst().setStack(representative(recipe.catalyst()));
        for (IInventorySlot lane : machine.mekanismMagicPatternInputs()) {
            lane.setStack(marked(helper, recipe, 1));
        }
        fillEnergy(infinite);
        fund(helper, FIRST, 17 * recipe.auraCost() - 1);
        helper.startSequence()
                .thenWaitUntil(() -> assertConstrained(helper, infinite, recipe))
                .thenExecuteAfter(12, () -> {
                    assertConstrained(helper, infinite, recipe);
                    fund(helper, FIRST, 1);
                })
                .thenWaitUntil(() -> helper.assertTrue(outputCount(infinite)
                                == 17 * recipe.output().getCount(),
                        "Final lane did not resume after its missing one Aura arrived"))
                .thenExecute(() -> {
                    helper.assertTrue(inputCount(infinite) == 0
                                    && requireAura(helper, FIRST, Direction.UP).getStoredAura() == 0,
                            "Seventeen actual lane completions did not conserve input and Aura");
                    for (IInventorySlot output : machine.mekanismMagicPatternOutputs()) {
                        assertStack(helper, output.getStack(), recipe.output(), "Parallel native output");
                    }
                    assertStack(helper, machine.mekanismMagicPersistentInputs().getFirst().getStack(),
                            representative(recipe.catalyst()), "Shared nonconsumed catalyst");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void creativeMagicBypassesOnlyAuraAndStillNeedsEnergy(GameTestHelper helper) {
        TileEntityMekanism ordinary = place(helper, FIRST, "infinite", 17);
        TileEntityMekanism creative = place(helper, SECOND, "infinite", 17);
        var recipe = recipe(helper, "infused_stone");
        automation(ordinary).mekanismMagicPatternInputs().get(16).setStack(marked(helper, recipe, 1));
        automation(creative).mekanismMagicPatternInputs().get(16).setStack(marked(helper, recipe, 1));
        fillEnergy(ordinary);
        creative.getComponent().addUpgrades(MagicUpgrades.creativeMagic(), 1);
        helper.assertTrue(creative.getComponent().getUpgrades(MagicUpgrades.creativeMagic()) == 1,
                "Infinite factory does not support the creative magic upgrade");
        helper.startSequence().thenExecuteAfter(recipe.duration() + 8, () -> {
            helper.assertTrue(inputCount(ordinary) == 1 && outputCount(ordinary) == 0,
                    "Infinite tier provided implicit creative Aura");
            helper.assertTrue(inputCount(creative) == 1 && outputCount(creative) == 0,
                    "Creative magic upgrade bypassed the required FE");
            helper.assertTrue(requireAura(helper, SECOND, Direction.UP).storeAura(1, false) == 0,
                    "Creative magic machine accepted external Aura it cannot spend");
            ordinary.getComponent().addUpgrades(MagicUpgrades.creativeMagic(), 1);
            fillEnergy(creative);
        }).thenWaitUntil(() -> {
            assertStack(helper, automation(ordinary).mekanismMagicPatternOutputs().get(16).getStack(),
                    recipe.output(), "Ordinary lane awakened by creative magic");
            assertStack(helper, automation(creative).mekanismMagicPatternOutputs().get(16).getStack(),
                    recipe.output(), "Creative magic lane awakened by FE");
        }).thenExecute(() -> {
            helper.assertTrue(requireAura(helper, FIRST, Direction.UP).getStoredAura() == 0
                            && requireAura(helper, SECOND, Direction.UP).getStoredAura() == 0,
                    "Creative completion created Aura");
            helper.assertTrue(automation(creative).mekanismMagicEnergyContainer().getEnergy()
                            < automation(creative).mekanismMagicEnergyContainer().getMaxEnergy(),
                    "Creative magic completed without consuming FE");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 160)
    public static void hugeAutomationBatchIsAtomicAndSeventeenLaneWorkIsBounded(GameTestHelper helper) {
        TileEntityMekanism infinite = place(helper, FIRST, "infinite", 17);
        var machine = automation(infinite);
        var recipe = recipe(helper, "glowstone");
        machine.mekanismMagicPersistentInputs().getFirst().setStack(representative(recipe.catalyst()));
        var host = (NatureAltarAutomationHost) infinite;
        ItemStack offered = representative(recipe.input());
        int totalCapacity = machine.mekanismMagicPatternInputs().stream()
                .mapToInt(slot -> slot.getLimit(offered)).reduce(0, Math::addExact);
        helper.assertTrue(totalCapacity > 17 && totalCapacity < Integer.MAX_VALUE,
                "Extras input capacity is not finite or cannot hold the pressure-test batch");
        FactoryState empty = state(helper, infinite);
        for (int attempt = 0; attempt < 64; attempt++) {
            helper.assertTrue(!host.acceptNatureAltarPattern(recipe.id(), offered.copyWithCount(Integer.MAX_VALUE)),
                    "A maximal integer batch exceeded physical 17-lane capacity");
        }
        assertRetained(helper, empty, state(helper, infinite), "Rejected maximal batch");
        helper.assertTrue(host.acceptNatureAltarPattern(recipe.id(), offered.copyWithCount(totalCapacity)),
                "All seventeen complete native slot capacities did not fit in the infinite factory");
        FactoryState full = state(helper, infinite);
        helper.assertTrue(!host.acceptNatureAltarPattern(recipe.id(), offered),
                "A full factory accepted an eighteenth stack fragment");
        assertRetained(helper, full, state(helper, infinite), "Rejected overflowing batch");
        infinite.getComponent().addUpgrades(Upgrade.SPEED, 8);
        fillEnergy(infinite);
        fund(helper, FIRST, 17 * recipe.auraCost());
        helper.startSequence().thenExecuteAfter(1, () -> helper.assertTrue(
                        outputCount(infinite) <= 17 * recipe.output().getCount(),
                "One scheduler tick completed more than one operation per lane"))
                .thenWaitUntil(() -> helper.assertTrue(outputCount(infinite) == 17 * recipe.output().getCount(),
                        "Funded bounded batch never made progress"))
                .thenExecuteAfter(8, () -> {
                    helper.assertTrue(outputCount(infinite) == 17 * recipe.output().getCount()
                                    && inputCount(infinite) == totalCapacity - 17
                                    && requireAura(helper, FIRST, Direction.UP).getStoredAura() == 0,
                            "Speed-upgraded large batch exceeded the funded operation count");
                    int[] remaining = data(helper, infinite).contextRemaining();
                    helper.assertTrue(Arrays.stream(remaining).sum() == inputCount(infinite),
                            "Large batch lost its remaining exact-recipe context");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 360)
    public static void realExtrasInstallersPreserveMePatternsRecoveryAndOnlineNode(GameTestHelper helper) {
        TileEntityMekanism ultimate = place(helper, FIRST, "ultimate", 9);
        helper.setBlock(new BlockPos(2, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        MeUpgradeRecipeMachineAdapter original = meAdapter(helper, ultimate);
        var installed = original.getMeUpgradeContainer().install(MeUpgradeType.PATTERN_PROVIDER);
        helper.assertTrue(installed.successful(), "Could not install an actual MekE pattern provider: " + installed);
        original.onMeUpgradeStateChanged();
        original.createMeNodeIfActive();
        AtomicReference<MeUpgradeRecipeMachineAdapter> current = new AtomicReference<>(original);
        AtomicReference<CompoundTag> nodeState = new AtomicReference<>();
        var recipe = recipe(helper, "infused_stone");
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(generic(representative(recipe.input()), 1),
                        generic(NatureAuraRecipeBridge.createPatternContext(helper.getLevel(), recipe.id()), 1)),
                List.of(generic(recipe.output(), recipe.output().getCount())));
        var details = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        helper.assertTrue(!encoded.isEmpty() && details != null,
                "Could not encode/decode the native Nature altar pattern");
        AEItemKey recoveryKey = AEItemKey.of(Items.GOLD_INGOT);

        var sequence = helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(meSupport(current.get()).getMainNode().isActive(),
                        "Ultimate Nature factory never joined the real powered AE grid"))
                .thenExecute(() -> {
                    AbstractMeAeSupport<?> support = meSupport(current.get());
                    support.getPatternSlots().getFirst().setStack(encoded.copy());
                    support.setVisibleInPatternAccessTerminal(false);
                    support.updatePatterns();
                    CompoundTag seeded = saveMe(helper, current.get());
                    seeded.putInt("PatternPriority", 73);
                    ListTag pending = new ListTag();
                    pending.add(GenericStack.writeTag(helper.getLevel().registryAccess(),
                            new GenericStack(recoveryKey, 17)));
                    seeded.put("MeInterfaceRecovery", pending);
                    current.get().loadMeState(seeded, helper.getLevel().registryAccess());
                    current.get().onMeUpgradeStateChanged();
                    CompoundTag before = saveMe(helper, current.get());
                    helper.assertTrue(before.contains("node", Tag.TAG_COMPOUND),
                            "ME upgrade precondition did not save a real managed-node identity");
                    nodeState.set(before.getCompound("node").copy());
                    assertMeRetained(helper, current.get(), encoded, recoveryKey, "ultimate");
                    assertMeNodeRetained(helper, current.get(), nodeState.get(), "ultimate");
                });
        for (int index = 0; index < TIERS.length; index++) {
            String tier = TIERS[index];
            int lanes = 11 + 2 * index;
            sequence.thenExecute(() -> {
                current.set(meAdapter(helper, install(helper, FIRST, tier, lanes)));
                assertMeRetained(helper, current.get(), encoded, recoveryKey, tier);
            }).thenWaitUntil(() -> helper.assertTrue(meSupport(current.get()).getMainNode().isActive(),
                    tier + " Nature factory ME node did not rejoin the powered AE grid"))
                    .thenExecute(() -> {
                        assertMeRetained(helper, current.get(), encoded, recoveryKey, tier + " online");
                        assertMeNodeRetained(helper, current.get(), nodeState.get(), tier + " online");
                        helper.assertTrue(meSupport(current.get()).getAvailablePatterns().stream()
                                        .anyMatch(pattern -> pattern.getDefinition().equals(details.getDefinition())),
                                tier + " did not advertise its retained Nature pattern after ME reconnection");
                    });
        }
        sequence.thenSucceed();
    }

    private static MeUpgradeRecipeMachineAdapter meAdapter(GameTestHelper helper, TileEntityMekanism tile) {
        helper.assertTrue(tile instanceof MeUpgradeRecipeMachineAdapter,
                "Nature factory lost the MekE adapter mixin");
        var adapter = (MeUpgradeRecipeMachineAdapter) tile;
        helper.assertTrue(adapter.isMeUpgradeTarget(), "Nature factory was absent from MekE target registration");
        return adapter;
    }

    private static AbstractMeAeSupport<?> meSupport(MeUpgradeRecipeMachineAdapter adapter) {
        return adapter.getOrCreateMeUpgradeRuntime().support();
    }

    private static CompoundTag saveMe(GameTestHelper helper, MeUpgradeRecipeMachineAdapter adapter) {
        CompoundTag state = new CompoundTag();
        adapter.saveMeState(state, helper.getLevel().registryAccess());
        return state;
    }

    private static void assertMeRetained(GameTestHelper helper, MeUpgradeRecipeMachineAdapter adapter,
                                         ItemStack pattern, AEItemKey recoveryKey, String step) {
        AbstractMeAeSupport<?> support = meSupport(adapter);
        helper.assertTrue(adapter.getMeUpgradeContainer().isInstalled(MeUpgradeType.PATTERN_PROVIDER)
                        && support.getPatternPriority() == 73 && !support.isVisibleInPatternAccessTerminal(),
                step + " lost ME installation, priority or terminal visibility");
        helper.assertTrue(!support.getPatternSlots().isEmpty(), step + " lost the ME pattern inventory");
        assertStack(helper, support.getPatternSlots().getFirst().getStack(), pattern, step + " ME pattern");
        CompoundTag saved = saveMe(helper, adapter);
        ListTag recovery = saved.getList("MeInterfaceRecovery", Tag.TAG_COMPOUND);
        long pending = 0;
        for (int index = 0; index < recovery.size(); index++) {
            GenericStack entry = GenericStack.readTag(helper.getLevel().registryAccess(), recovery.getCompound(index));
            helper.assertTrue(entry != null && recoveryKey.equals(entry.what()) && entry.amount() > 0,
                    step + " changed a persisted ME recovery item");
            pending = Math.addExact(pending, entry.amount());
        }
        helper.assertTrue(pending == 17, step + " lost or duplicated the pending ME recovery amount");
    }

    private static void assertMeNodeRetained(GameTestHelper helper, MeUpgradeRecipeMachineAdapter adapter,
                                             CompoundTag node, String step) {
        helper.assertTrue(node != null, step + " ran before the original ME node snapshot was captured");
        AbstractMeAeSupport<?> support = meSupport(adapter);
        // AE2 caches loaded identity in initData until the node is created.
        // Its saveToNBT intentionally writes no node while creation is pending,
        // so compare the complete persisted identity after actual reconnection.
        helper.assertTrue(support.getMainNode().isActive(), step + " has no active ME node to verify");
        CompoundTag saved = saveMe(helper, adapter);
        helper.assertTrue(saved.contains("node", Tag.TAG_COMPOUND) && node.equals(saved.getCompound("node")),
                step + " replaced or lost the managed ME node identity; before=" + node
                        + ", after=" + saved.getCompound("node")
                        + ", nodePresent=" + saved.contains("node", Tag.TAG_COMPOUND)
                        + ", active=" + support.getMainNode().isActive());
    }

    private static TileEntityMekanism requireUpgraded(GameTestHelper helper,
                                                       AtomicReference<TileEntityMekanism> upgraded) {
        TileEntityMekanism tile = upgraded.get();
        // GameTestSequence can advance after thenExecute records a failure.
        // Report an assertion if setup failed before publishing its fixture,
        // so a secondary NPE cannot escape the sequence and crash the server.
        helper.assertTrue(tile != null, "The preceding installer event did not finish creating its upgraded fixture");
        helper.assertTrue(!tile.isRemoved() && helper.getBlockEntity(FIRST) == tile,
                "The upgraded Nature factory fixture was replaced or removed before its continuation");
        return tile;
    }

    private static GenericStack generic(ItemStack stack, long amount) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null || amount <= 0) {
            throw new IllegalArgumentException("Invalid AE item stack");
        }
        return new GenericStack(key, amount);
    }

    private static void assertConstrained(GameTestHelper helper, TileEntityMekanism tile,
                                          NatureAuraRecipeBridge.AltarRecipeView recipe) {
        int outputs = outputCount(tile);
        int inputs = inputCount(tile);
        int aura = requireAura(helper, FIRST, Direction.UP).getStoredAura();
        var data = data(helper, tile);
        String actual = "; outputs=" + outputs + ", inputs=" + inputs + ", aura=" + aura
                + ", outputPerRecipe=" + recipe.output().getCount() + ", auraPerRecipe=" + recipe.auraCost()
                + ", progress=" + Arrays.toString(data.progress)
                + ", requiredTicks=" + Arrays.toString(data.requiredTicks())
                + ", contexts=" + Arrays.toString(data.contextRemaining())
                + ", energy=" + data.energyContainer.getEnergy();
        helper.assertTrue(outputs == 16 * recipe.output().getCount() && inputs == 1,
                "Underfunded shared Aura completed other than sixteen of seventeen real lanes" + actual);
        helper.assertTrue(aura == recipe.auraCost() - 1,
                "Unfunded lane partially debited or overdrew shared Aura" + actual);
    }

    private static TileEntityMekanism install(GameTestHelper helper, BlockPos pos, String tier, int lanes) {
        ResourceLocation installerId = ResourceLocation.fromNamespaceAndPath(
                lanes <= 9 ? "mekanism" : "mekanism_extras", tier + "_tier_installer");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(installerId), "Missing real tier installer " + installerId);
        Item installerItem = BuiltInRegistries.ITEM.get(installerId);
        ItemStack installer = new ItemStack(installerItem);
        BlockPos absolute = helper.absolutePos(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        InteractionResult result = installerItem.useOn(new UseOnContext(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, installer,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        helper.assertTrue(result == InteractionResult.CONSUME && installer.isEmpty(),
                "Survival tier installer did not consume exactly one successful upgrade: " + tier + "/" + result);
        helper.assertTrue(BuiltInRegistries.BLOCK.getKey(helper.getBlockState(pos).getBlock())
                        .equals(id(tier + "_nature_altar_factory")),
                "Tier installer converted the Nature altar into another factory");
        return requireFactory(helper, pos, lanes);
    }

    private static TileEntityMekanism place(GameTestHelper helper, BlockPos pos, String tier, int lanes) {
        ResourceLocation blockId = id(tier + "_nature_altar_factory");
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(blockId), "Missing Nature factory registration: " + blockId);
        helper.setBlock(pos, BuiltInRegistries.BLOCK.get(blockId));
        return requireFactory(helper, pos, lanes);
    }

    private static TileEntityMekanism requireFactory(GameTestHelper helper, BlockPos pos, int lanes) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        helper.assertTrue(blockEntity instanceof TileEntityMekanism
                        && blockEntity instanceof IMekanismMagicAutomation
                        && blockEntity instanceof NatureAltarAutomationHost,
                "Expected our Nature factory/automation host at " + pos);
        TileEntityMekanism tile = (TileEntityMekanism) blockEntity;
        helper.assertTrue(automation(tile).mekanismMagicPatternInputs().size() == lanes
                        && automation(tile).mekanismMagicPatternOutputs().size() == lanes
                        && data(helper, tile).progress.length == lanes,
                "Nature factory has the wrong lane/slot/progress count: " + lanes);
        if (lanes > 9) {
            helper.assertTrue(tile.getClass().getName().equals(
                            "com.example.mekanismmagic.integration.mekextras.ExtraNatureAltarFactoryBlockEntity"),
                    "Extras installer returned a native Mekanism machine instead of our Nature machine");
        }
        return tile;
    }

    private static NatureAuraRecipeBridge.AltarRecipeView recipe(GameTestHelper helper, String path) {
        return NatureAuraRecipeScanner.find(helper.getLevel().getRecipeManager(),
                ResourceLocation.fromNamespaceAndPath("naturesaura", "altar/" + path)).orElseThrow();
    }

    private static ItemStack marked(GameTestHelper helper, NatureAuraRecipeBridge.AltarRecipeView recipe, int count) {
        return NatureAuraRecipeBridge.markPatternInput(representative(recipe.input()).copyWithCount(count),
                NatureAuraRecipeBridge.createPatternContext(helper.getLevel(), recipe.id()));
    }

    private static ItemStack representative(net.minecraft.world.item.crafting.Ingredient ingredient) {
        return NatureAuraRecipeBridge.representativeChoice(ingredient).orElseThrow();
    }

    private static ItemStack named(ItemStack stack, String name) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return stack;
    }

    private static IMekanismMagicAutomation automation(TileEntityMekanism tile) {
        return (IMekanismMagicAutomation) tile;
    }

    private static void fillEnergy(TileEntityMekanism tile) {
        var energy = automation(tile).mekanismMagicEnergyContainer();
        energy.setEnergy(energy.getMaxEnergy());
    }

    /** Run each real factory scheduler; reflection only bridges the Extras package's protected hook. */
    private static void updateFactory(TileEntityMekanism tile) {
        try {
            var method = tile.getClass().getDeclaredMethod("onUpdateServer");
            method.setAccessible(true);
            method.invoke(tile);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to update the real Nature factory scheduler", exception);
        }
    }

    private static void fund(GameTestHelper helper, BlockPos pos, int amount) {
        helper.assertTrue(requireAura(helper, pos, Direction.UP).storeAura(amount, false) == amount,
                "Real sided Aura capability could not accept the test funding: " + amount);
    }

    private static IAuraContainer capability(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                helper.absolutePos(pos), side);
    }

    private static IAuraContainer requireAura(GameTestHelper helper, BlockPos pos, Direction side) {
        IAuraContainer aura = capability(helper, pos, side);
        helper.assertTrue(aura != null, "Nature factory has no actual Aura capability on " + side);
        return aura;
    }

    private static int inputCount(TileEntityMekanism tile) {
        return automation(tile).mekanismMagicPatternInputs().stream().mapToInt(IInventorySlot::getCount).sum();
    }

    private static int outputCount(TileEntityMekanism tile) {
        return automation(tile).mekanismMagicPatternOutputs().stream().mapToInt(IInventorySlot::getCount).sum();
    }

    private static NatureAltarFactoryUpgradeData data(GameTestHelper helper, TileEntityMekanism tile) {
        var data = tile.getUpgradeData(helper.getLevel().registryAccess());
        helper.assertTrue(data instanceof NatureAltarFactoryUpgradeData, "Factory dropped the Nature upgrade payload");
        return (NatureAltarFactoryUpgradeData) data;
    }

    private static FactoryState state(GameTestHelper helper, TileEntityMekanism tile) {
        var data = data(helper, tile);
        return new FactoryState(data.inputSlots.stream().map(slot -> slot.getStack().copy()).toList(),
                data.outputSlots.stream().map(slot -> slot.getStack().copy()).toList(), data.catalysts(),
                data.energyContainer.getEnergy(), data.progress.clone(), data.lockedRecipes(),
                data.lockedRecipeSignatures(), data.contextRemaining(), data.requiredTicks(),
                data.components.copy(), data.auraSnapshot());
    }

    private static void assertRetained(GameTestHelper helper, FactoryState expected, FactoryState actual, String step) {
        helper.assertTrue(actual.inputs().size() >= expected.inputs().size(), step + " shrank factory lanes");
        for (int lane = 0; lane < actual.inputs().size(); lane++) {
            if (lane < expected.inputs().size()) {
                assertStack(helper, actual.inputs().get(lane), expected.inputs().get(lane), step + " input " + lane);
                assertStack(helper, actual.outputs().get(lane), expected.outputs().get(lane), step + " output " + lane);
                helper.assertTrue(actual.progress()[lane] == expected.progress()[lane]
                                && java.util.Objects.equals(actual.locks()[lane], expected.locks()[lane])
                                && actual.signatures()[lane].equals(expected.signatures()[lane])
                                && actual.contexts()[lane] == expected.contexts()[lane]
                                && actual.ticks()[lane] == expected.ticks()[lane],
                        step + " changed a lane's progress, signed lock, count or duration: " + lane
                                + "; progress=" + expected.progress()[lane] + "->" + actual.progress()[lane]
                                + ", lock=" + expected.locks()[lane] + "->" + actual.locks()[lane]
                                + ", context=" + expected.contexts()[lane] + "->" + actual.contexts()[lane]
                                + ", duration=" + expected.ticks()[lane] + "->" + actual.ticks()[lane]
                                + ", signature=" + expected.signatures()[lane] + "->" + actual.signatures()[lane]);
            } else {
                helper.assertTrue(actual.inputs().get(lane).isEmpty() && actual.outputs().get(lane).isEmpty()
                                && actual.progress()[lane] == 0 && actual.locks()[lane] == null
                                && actual.signatures()[lane].isEmpty() && actual.contexts()[lane] == 0,
                        step + " copied an old lane into a newly added lane: " + lane);
            }
        }
        helper.assertTrue(actual.catalysts().size() == expected.catalysts().size(),
                step + " changed the number of persistent catalyst slots");
        for (int slot = 0; slot < expected.catalysts().size(); slot++) {
            assertStack(helper, actual.catalysts().get(slot), expected.catalysts().get(slot),
                    step + " catalyst " + slot);
        }
        helper.assertTrue(actual.energy() == expected.energy(),
                step + " changed FE: " + expected.energy() + "->" + actual.energy());
        helper.assertTrue(actual.components().equals(expected.components()),
                step + " changed machine components; before=" + expected.components() + ", after=" + actual.components());
        helper.assertTrue(actual.aura().equals(expected.aura()),
                step + " changed Aura type/count or side modes; before=" + expected.aura() + ", after=" + actual.aura());
    }

    private static void assertStack(GameTestHelper helper, ItemStack actual, ItemStack expected, String context) {
        helper.assertTrue(ItemStack.matches(actual, expected), context + ": expected " + expected + ", found " + actual);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, path);
    }

    private record FactoryState(List<ItemStack> inputs, List<ItemStack> outputs, List<ItemStack> catalysts,
                                long energy, int[] progress, ResourceLocation[] locks, String[] signatures,
                                int[] contexts, int[] ticks, CompoundTag components,
                                NatureAuraStorageComponent.Snapshot aura) {}
}
