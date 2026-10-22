package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import com.stal111.forbidden_arcanus.common.essence.EssenceStorage;
import com.stal111.forbidden_arcanus.common.essence.EssenceValue;
import com.stal111.forbidden_arcanus.core.init.ModDataComponents;
import mekanism.common.inventory.slot.BasicInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;

/** One four-lane machine, retained native samples and FE-only work. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EssenceCondenserGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private EssenceCondenserGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void retiredThreeMachineIdsAreAbsentRatherThanAliased(
            GameTestHelper helper) {
        ResourceLocation current = ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, "essence_condenser");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(current)
                        == ForbiddenArcanusRegistries
                        .ESSENCE_CONDENSER_BLOCK.get()
                        && BuiltInRegistries.BLOCK_ENTITY_TYPE.get(current)
                        == ForbiddenArcanusRegistries
                        .ESSENCE_CONDENSER_TILE.get(),
                "Unified registry content is absent");
        for (String oldPath : List.of("soul_extractor_processor",
                "simulated_blood_extractor", "experience_bottler")) {
            ResourceLocation old = ResourceLocation.fromNamespaceAndPath(
                    MekanismMagic.MOD_ID, oldPath);
            helper.assertTrue(BuiltInRegistries.BLOCK.getOptional(old).isEmpty()
                            && BuiltInRegistries.ITEM.getOptional(old).isEmpty()
                            && BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(old).isEmpty()
                            && BuiltInRegistries.MENU.getOptional(old).isEmpty()
                            && !BuiltInRegistries.BLOCK.keySet().contains(old)
                            && !BuiltInRegistries.ITEM.keySet().contains(old)
                            && !BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet().contains(old)
                            && !BuiltInRegistries.MENU.keySet().contains(old),
                    "Retired machine ID still registered or aliased: "
                            + oldPath);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fourSamplesRunIndependentParallelLanesWithoutConsumption(
            GameTestHelper helper) {
        EssenceCondenserBlockEntity tile = place(helper, POS);
        ItemStack[] samples = samples();
        int[] amounts = new int[4];
        for (int channel = 0; channel < 4; channel++) {
            var parsed = HephaestusForge261Bridge
                    .previewRetainedEssenceSample(samples[channel]);
            helper.assertTrue(parsed.isPresent()
                            && parsed.get().channel() == channel
                            && parsed.get().amount() > 0,
                    "Native sample classifier missed channel " + channel);
            amounts[channel] = parsed.get().amount();
            tile.getSampleSlot(channel).setStack(samples[channel].copy());
            helper.assertTrue(tile.getLaneDuration(channel)
                            == EssenceCondenserBlockEntity.BASE_TICKS[channel],
                    "Base lane timing changed for channel " + channel);
        }
        long initialEnergy = EssenceCondenserBlockEntity.ENERGY_CAPACITY;
        tile.getNativeEnergyContainer().setEnergy(initialEnergy);
        for (int tick = 0; tick < 200; tick++) tile.onUpdateServer();
        long expectedSpent = 0L;
        for (int channel = 0; channel < 4; channel++) {
            expectedSpent += 200L
                    * EssenceCondenserBlockEntity.ENERGY_PER_TICK[channel];
            int expectedBank = 200
                    / EssenceCondenserBlockEntity.BASE_TICKS[channel]
                    * amounts[channel];
            helper.assertTrue(tile.getStoredEssence(channel) == expectedBank
                            && tile.getLaneProgress(channel)
                            == 200 % EssenceCondenserBlockEntity.BASE_TICKS[channel]
                            && ItemStack.matches(samples[channel],
                            tile.getSampleSlot(channel).getStack()),
                    "Parallel lane consumed/multiplied its retained sample or crossed banks "
                            + channel);
        }
        helper.assertTrue(tile.getNativeEnergyContainer().getEnergy()
                        == initialEnergy - expectedSpent,
                "Four lanes spent a non-FE resource or wrong per-tick FE budget");
        int[] before = banks(tile);
        CompoundTag snapshot = save(helper, tile);
        tile.loadWithComponents(snapshot, helper.getLevel().registryAccess());
        helper.assertTrue(Arrays.equals(before, banks(tile))
                        && tile.getLaneProgress(1) == 25,
                "Reload lost independently paid lane progress/bank balances");
        for (int tick = 0; tick < 10; tick++) tile.onUpdateServer();
        helper.assertTrue(tile.getStoredEssence(1) == before[1] + amounts[1]
                        && tile.getLaneProgress(1) == 0
                        && tile.getStoredEssence(0) == before[0]
                        && tile.getStoredEssence(2) == before[2]
                        && tile.getStoredEssence(3) == before[3],
                "Reloaded Souls lane lost progress or paid another lane early");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fullOrWrongSampleNeverDebitsFeAndStackCountDoesNotScaleYield(
            GameTestHelper helper) {
        EssenceCondenserBlockEntity tile = place(helper, POS);
        ItemStack aureal = samples()[0];
        tile.getSampleSlot(0).setStack(aureal.copy());
        ((BasicInventorySlot) tile.getSampleSlot(1))
                .setStackUnchecked(aureal.copyWithCount(1));
        tile.getSampleSlot(3).setStack(samples()[3]);
        int[] full = new int[4];
        full[0] = EssenceCondenserBlockEntity.BASE_CAPACITY[0];
        setBanks(helper, tile, full);
        tile.getNativeEnergyContainer().setEnergy(
                EssenceCondenserBlockEntity.ENERGY_CAPACITY);
        long beforeEnergy = tile.getNativeEnergyContainer().getEnergy();
        tile.onUpdateServer();
        helper.assertTrue(tile.getLaneStatusKey(0).endsWith("bank_full")
                        && tile.getLaneStatusKey(1).endsWith("invalid_sample")
                        && tile.getLaneProgress(0) == 0
                        && tile.getLaneProgress(1) == 0
                        && tile.getLaneProgress(3) == 1
                        && tile.getNativeEnergyContainer().getEnergy()
                        == beforeEnergy
                        - EssenceCondenserBlockEntity.ENERGY_PER_TICK[3],
                "Blocked lanes spent FE or stopped another independent lane");
        tile.getSampleSlot(1).setEmpty();
        tile.getSampleSlot(3).setEmpty();
        setBanks(helper, tile, new int[4]);
        ItemStack stacked = aureal.copyWithCount(64);
        ((BasicInventorySlot) tile.getSampleSlot(0))
                .setStackUnchecked(stacked.copy());
        long beforeAureal = tile.getNativeEnergyContainer().getEnergy();
        for (int tick = 0; tick < 20; tick++) tile.onUpdateServer();
        int oneSample = HephaestusForge261Bridge
                .previewRetainedEssenceSample(stacked).orElseThrow().amount();
        helper.assertTrue(tile.getStoredEssence(0) == oneSample
                        && ItemStack.matches(stacked,
                        tile.getSampleSlot(0).getStack())
                        && tile.getNativeEnergyContainer().getEnergy()
                        == beforeAureal - 20L
                        * EssenceCondenserBlockEntity.ENERGY_PER_TICK[0],
                "Sample stack count multiplied yield or was consumed");
        tile.getSampleSlot(0).setEmpty();
        tile.getSampleSlot(1).setStack(samples()[1]);
        for (int tick = 0; tick < 10; tick++) tile.onUpdateServer();
        helper.assertTrue(tile.getLaneProgress(1) == 10,
                "Souls lane never began its independently paid work");
        long beforeRemoval = tile.getNativeEnergyContainer().getEnergy();
        tile.getSampleSlot(1).setEmpty();
        tile.onUpdateServer();
        helper.assertTrue(tile.getLaneProgress(1) == 0
                        && tile.getStoredEssence(1) == 0
                        && tile.getNativeEnergyContainer().getEnergy()
                        == beforeRemoval,
                "Removing one sample advanced or paid its lane again");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void missingCurrentStateTagClearsFourBanksAndLaneProgress(
            GameTestHelper helper) {
        EssenceCondenserBlockEntity tile = place(helper, POS);
        tile.getSampleSlot(1).setStack(samples()[1]);
        tile.onUpdateServer();
        setBanks(helper, tile, new int[]{7, 8, 9, 10});
        helper.assertTrue(Arrays.equals(banks(tile), new int[]{7, 8, 9, 10})
                        && tile.getLaneProgress(1) == 1,
                "Current condenser fixture did not establish four balances and in-flight work");

        CompoundTag withoutCurrentState = save(helper, tile);
        withoutCurrentState.remove(EssenceCondenserBlockEntity.STATE_TAG);
        tile.loadWithComponents(withoutCurrentState,
                helper.getLevel().registryAccess());
        helper.assertTrue(Arrays.equals(banks(tile), new int[4])
                        && tile.getLaneProgress(0) == 0
                        && tile.getLaneProgress(1) == 0
                        && tile.getLaneProgress(2) == 0
                        && tile.getLaneProgress(3) == 0,
                "Missing current condenser state retained stale banks or paid lane progress");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void optionalFluidPortDrainsFourBanksOnlyWhenPackagedFaaExists(
            GameTestHelper helper) {
        EssenceCondenserBlockEntity tile = place(helper, POS);
        setBanks(helper, tile, new int[]{35, 1, 25, 91});
        IFluidHandler handler = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(POS), Direction.UP);
        if (!ModCompatibility.loaded("packaged_faa")) {
            helper.assertTrue(handler == null || handler.getTanks() == 0,
                    "Absent optional fluid mod still exposed a magic fluid port");
            helper.succeed();
            return;
        }
        helper.assertTrue(handler != null && handler.getTanks() == 4,
                "Unified machine did not expose four output banks");
        String[] names = {"aureal", "souls", "blood", "experience"};
        int[] balances = {35, 1, 25, 91};
        for (int channel = 0; channel < 4; channel++) {
            var fluid = BuiltInRegistries.FLUID.get(
                    ResourceLocation.fromNamespaceAndPath("packaged_faa",
                            "fluid_" + names[channel]));
            FluidStack request = new FluidStack(fluid, 1);
            helper.assertTrue(handler.getFluidInTank(channel).getFluid()
                            == fluid
                            && handler.getFluidInTank(channel).getAmount()
                            == balances[channel]
                            && handler.drain(request,
                            IFluidHandler.FluidAction.SIMULATE)
                            .getAmount() == 1
                            && tile.getStoredEssence(channel)
                            == balances[channel]
                            && handler.fill(request,
                            IFluidHandler.FluidAction.EXECUTE) == 0,
                    "Fluid view mutated/accepted reverse fill on bank " + channel);
            helper.assertTrue(handler.drain(request,
                            IFluidHandler.FluidAction.EXECUTE)
                            .getAmount() == 1
                            && tile.getStoredEssence(channel)
                            == balances[channel] - 1,
                    "Canonical fluid output did not debit existing bank " + channel);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fourNativeUtremVesselsFillOnlyFromTheirBanks(
            GameTestHelper helper) {
        EssenceCondenserBlockEntity tile = place(helper, POS);
        int[] balances = {70, 2, 25, 91};
        setBanks(helper, tile, balances);
        tile.getNativeEnergyContainer().setEnergy(0);
        for (int channel = 0; channel < 4; channel++) {
            ItemStack empty = item("utrem_jar")
                    .copyWithCount(channel == 0 ? 2 : 1);
            tile.getVesselInputSlot(channel).setStack(empty);
        }
        tile.onUpdateServer();
        for (int channel = 0; channel < 4; channel++) {
            ItemStack filled = tile.getVesselOutputSlot(channel)
                    .getStack();
            EssenceStorage stored = filled.get(
                    ModDataComponents.ESSENCE_STORAGE);
            helper.assertTrue(filled.is(item("essence_utrem_jar").getItem())
                            && filled.getCount() == 1
                            && stored != null
                            && stored.value().type()
                            == EssenceType.values()[channel]
                            && stored.value().amount()
                            == balances[channel]
                            && tile.getStoredEssence(channel) == 0
                            && tile.getVesselInputSlot(channel).getCount()
                            == (channel == 0 ? 1 : 0),
                    "Native vessel minted, mixed, or double-consumed bank "
                            + channel);
        }
        tile.onUpdateServer();
        helper.assertTrue(tile.getVesselInputSlot(0).getCount() == 1
                        && tile.getVesselOutputSlot(0).getCount() == 1
                        && tile.getNativeEnergyContainer().getEnergy() == 0,
                "Zero-bank second stacked vessel filled for free or spent FE");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void vesselWhitelistWrongTypeAndBlockedOutputPreservePayment(
            GameTestHelper helper) {
        EssenceCondenserBlockEntity tile = place(helper, POS);
        setBanks(helper, tile, new int[]{50, 0, 25, 0});
        tile.getNativeEnergyContainer().setEnergy(0);
        ItemStack wrong = item("essence_utrem_jar");
        wrong.set(ModDataComponents.ESSENCE_STORAGE,
                new EssenceStorage(EssenceValue.of(EssenceType.BLOOD, 1),
                        1_000, true));
        ((BasicInventorySlot) tile.getVesselInputSlot(0))
                .setStackUnchecked(wrong);
        tile.onUpdateServer();
        helper.assertTrue(tile.getStoredEssence(0) == 50
                        && tile.getVesselOutputSlot(0).isEmpty(),
                "Wrong-type native vessel withdrew Aureal");
        ItemStack forged = new ItemStack(Items.STICK);
        forged.set(ModDataComponents.ESSENCE_STORAGE,
                new EssenceStorage(EssenceValue.of(EssenceType.AUREAL, 0),
                        1_000, true));
        ((BasicInventorySlot) tile.getVesselInputSlot(0))
                .setStackUnchecked(forged);
        tile.onUpdateServer();
        helper.assertTrue(tile.getStoredEssence(0) == 50
                        && tile.getVesselOutputSlot(0).isEmpty(),
                "Arbitrary component-tagged item bypassed native vessel whitelist");

        ItemStack tubes = item("test_tube").copyWithCount(2);
        tubes.set(DataComponents.CUSTOM_NAME,
                Component.literal("Retained vessel owner"));
        tile.getVesselInputSlot(2).setStack(tubes.copy());
        ((BasicInventorySlot) tile.getVesselOutputSlot(2))
                .setStackUnchecked(new ItemStack(Items.COBBLESTONE, 64));
        tile.onUpdateServer();
        helper.assertTrue(tile.getStoredEssence(2) == 25
                        && ItemStack.matches(tubes,
                        tile.getVesselInputSlot(2).getStack())
                        && tile.getVesselOutputSlot(2).getCount() == 64,
                "Blocked native vessel output debited bank or consumed input");
        CompoundTag blocked = save(helper, tile);
        tile.loadWithComponents(blocked,
                helper.getLevel().registryAccess());
        tile.getVesselOutputSlot(2).setEmpty();
        tile.onUpdateServer();
        ItemStack output = tile.getVesselOutputSlot(2).getStack();
        EssenceStorage result = output.get(
                ModDataComponents.ESSENCE_STORAGE);
        helper.assertTrue(tile.getStoredEssence(2) == 0
                        && tile.getVesselInputSlot(2).getCount() == 1
                        && output.is(item("blood_test_tube").getItem())
                        && output.getCount() == 1
                        && output.getHoverName().equals(tubes.getHoverName())
                        && result != null
                        && result.value().type() == EssenceType.BLOOD
                        && result.value().amount() == 25
                        && result.limit() == 3_000
                        && tile.getNativeEnergyContainer().getEnergy() == 0,
                "Unblocked vessel did not preserve native components/limit or exact bank debit");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fluidAndNativeVesselShareOneAuthoritativeBank(
            GameTestHelper helper) {
        if (!ModCompatibility.loaded("packaged_faa")) {
            MekanismMagic.LOGGER.info(
                    "SKIP fluid/vessel shared-bank test: optional mod absent");
            helper.succeed();
            return;
        }
        EssenceCondenserBlockEntity tile = place(helper, POS);
        setBanks(helper, tile, new int[]{100, 0, 0, 0});
        tile.getNativeEnergyContainer().setEnergy(0);
        ItemStack vessel = item("essence_utrem_jar");
        vessel.set(ModDataComponents.ESSENCE_STORAGE,
                new EssenceStorage(EssenceValue.of(EssenceType.AUREAL, 0),
                        60, true));
        vessel.set(DataComponents.CUSTOM_NAME,
                Component.literal("Persistent sixty-point vessel"));
        tile.getVesselInputSlot(0).setStack(vessel.copy());
        tile.onUpdateServer();
        ItemStack output = tile.getVesselOutputSlot(0).getStack();
        EssenceStorage data = output.get(ModDataComponents.ESSENCE_STORAGE);
        helper.assertTrue(tile.getStoredEssence(0) == 40
                        && data != null && data.value().amount() == 60
                        && data.limit() == 60
                        && data.showInTooltip()
                        && output.getHoverName().equals(vessel.getHoverName()),
                "Native vessel did not debit one bank or preserve storage components");
        tile.loadWithComponents(save(helper, tile),
                helper.getLevel().registryAccess());
        IFluidHandler fluid = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK,
                helper.absolutePos(POS), Direction.UP);
        var aureal = BuiltInRegistries.FLUID.get(
                ResourceLocation.fromNamespaceAndPath("packaged_faa",
                        "fluid_aureal"));
        helper.assertTrue(fluid != null
                        && fluid.drain(new FluidStack(aureal, 40),
                        IFluidHandler.FluidAction.EXECUTE)
                        .getAmount() == 40
                        && tile.getStoredEssence(0) == 0
                        && tile.getVesselOutputSlot(0).getStack()
                        .get(ModDataComponents.ESSENCE_STORAGE)
                        .value().amount() == 60,
                "PFAA fluid and native vessel did not conserve the shared 100 points");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void partialWorkingVesselRefillsInPlaceAndAutomationWaitsForFull(
            GameTestHelper helper) {
        EssenceCondenserBlockEntity tile = place(helper, POS);
        tile.getNativeEnergyContainer().setEnergy(0);
        ItemStack input = item("utrem_jar").copyWithCount(2);
        input.set(DataComponents.CUSTOM_NAME,
                Component.literal("One continuing owner's jar"));
        tile.getVesselInputSlot(0).setStack(input.copy());
        setBanks(helper, tile, new int[]{30, 0, 0, 0});
        tile.onUpdateServer();
        ItemStack first = tile.getVesselOutputSlot(0).getStack();
        EssenceStorage firstData = first.get(
                ModDataComponents.ESSENCE_STORAGE);
        helper.assertTrue(tile.getVesselInputSlot(0).getCount() == 1
                        && firstData != null
                        && firstData.value().amount() == 30
                        && first.getHoverName().equals(input.getHoverName())
                        && tile.getStoredEssence(0) == 0
                        && tile.getVesselOutputSlot(0).extractItem(1,
                        mekanism.api.Action.SIMULATE,
                        mekanism.api.AutomationType.EXTERNAL).isEmpty()
                        && tile.getVesselOutputSlot(0).extractItem(1,
                        mekanism.api.Action.SIMULATE,
                        mekanism.api.AutomationType.MANUAL).getCount() == 1,
                "Partial working jar was removed by automation or lost manual recovery");
        tile.loadWithComponents(save(helper, tile),
                helper.getLevel().registryAccess());
        setBanks(helper, tile, new int[]{70, 0, 0, 0});
        tile.onUpdateServer();
        EssenceStorage continued = tile.getVesselOutputSlot(0)
                .getStack().get(ModDataComponents.ESSENCE_STORAGE);
        helper.assertTrue(continued != null
                        && continued.value().amount() == 100
                        && tile.getVesselInputSlot(0).getCount() == 1
                        && tile.getVesselOutputSlot(0).getCount() == 1
                        && tile.getStoredEssence(0) == 0,
                "Refill consumed a second jar instead of continuing the same singleton");
        setBanks(helper, tile, new int[]{10_000, 0, 0, 0});
        tile.onUpdateServer();
        EssenceStorage full = tile.getVesselOutputSlot(0)
                .getStack().get(ModDataComponents.ESSENCE_STORAGE);
        helper.assertTrue(full != null
                        && full.value().amount() == full.limit()
                        && tile.getStoredEssence(0) == 100
                        && tile.getVesselInputSlot(0).getCount() == 1
                        && tile.getVesselOutputSlot(0).extractItem(1,
                        mekanism.api.Action.SIMULATE,
                        mekanism.api.AutomationType.EXTERNAL).getCount() == 1,
                "Full native jar could not be extracted or overfilled the bank");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void onePointSoulsContinueOneWorkingJarUntilPlayerTakesIt(
            GameTestHelper helper) {
        EssenceCondenserBlockEntity tile = place(helper, POS);
        tile.getNativeEnergyContainer().setEnergy(0);
        tile.getVesselInputSlot(1).setStack(
                item("utrem_jar").copyWithCount(3));
        setBanks(helper, tile, new int[]{0, 1, 0, 0});
        tile.onUpdateServer();
        helper.assertTrue(tile.getVesselInputSlot(1).getCount() == 2
                        && tile.getVesselOutputSlot(1).getCount() == 1
                        && tile.getVesselOutputSlot(1).getStack()
                        .get(ModDataComponents.ESSENCE_STORAGE)
                        .value().amount() == 1,
                "One Soul did not start exactly one physical working jar");
        setBanks(helper, tile, new int[]{0, 1, 0, 0});
        tile.onUpdateServer();
        ItemStack working = tile.getVesselOutputSlot(1).getStack().copy();
        helper.assertTrue(tile.getVesselInputSlot(1).getCount() == 2
                        && working.getCount() == 1
                        && working.get(ModDataComponents.ESSENCE_STORAGE)
                        .value().amount() == 2
                        && tile.getVesselOutputSlot(1).extractItem(1,
                        mekanism.api.Action.SIMULATE,
                        mekanism.api.AutomationType.EXTERNAL).isEmpty(),
                "Soul top-up opened a second nearly empty jar or exposed partial output");
        ItemStack wrong = item("essence_utrem_jar");
        wrong.set(ModDataComponents.ESSENCE_STORAGE,
                new EssenceStorage(EssenceValue.of(EssenceType.BLOOD, 1),
                        10_000, true));
        ((BasicInventorySlot) tile.getVesselOutputSlot(1))
                .setStackUnchecked(wrong);
        setBanks(helper, tile, new int[]{0, 1, 0, 0});
        tile.onUpdateServer();
        helper.assertTrue(tile.getStoredEssence(1) == 1
                        && tile.getVesselInputSlot(1).getCount() == 2,
                "Wrong-type occupied output consumed Soul bank or next jar");
        ((BasicInventorySlot) tile.getVesselOutputSlot(1))
                .setStackUnchecked(working.copyWithCount(2));
        tile.onUpdateServer();
        helper.assertTrue(tile.getStoredEssence(1) == 1
                        && tile.getVesselInputSlot(1).getCount() == 2,
                "Stacked working output consumed Soul bank or next jar");
        tile.getVesselOutputSlot(1).setStack(working);
        tile.onUpdateServer();
        helper.assertTrue(tile.getStoredEssence(1) == 0
                        && tile.getVesselOutputSlot(1).getStack()
                        .get(ModDataComponents.ESSENCE_STORAGE)
                        .value().amount() == 3
                        && tile.getVesselInputSlot(1).getCount() == 2,
                "Restored partial jar did not continue the same paid output");
        ItemStack manual = tile.getVesselOutputSlot(1).extractItem(1,
                mekanism.api.Action.EXECUTE,
                mekanism.api.AutomationType.MANUAL);
        helper.assertTrue(!manual.isEmpty()
                        && manual.get(ModDataComponents.ESSENCE_STORAGE)
                        .value().amount() == 3
                        && tile.getVesselOutputSlot(1).isEmpty(),
                "Player could not recover the partial working jar");
        setBanks(helper, tile, new int[]{0, 1, 0, 0});
        tile.onUpdateServer();
        helper.assertTrue(tile.getVesselInputSlot(1).getCount() == 1
                        && tile.getVesselOutputSlot(1).getStack()
                        .get(ModDataComponents.ESSENCE_STORAGE)
                        .value().amount() == 1,
                "A new jar did not begin only after manual recovery of the old one");
        helper.succeed();
    }

    private static EssenceCondenserBlockEntity place(GameTestHelper helper,
                                                     BlockPos pos) {
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos,
                ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get());
        EssenceCondenserBlockEntity tile = helper.getBlockEntity(pos);
        tile.getNativeEnergyContainer().setEnergy(
                EssenceCondenserBlockEntity.ENERGY_CAPACITY);
        return tile;
    }

    private static ItemStack[] samples() {
        ItemStack aureal = item("aureal_bottle").copyWithCount(4);
        ItemStack souls = item("soul").copyWithCount(2);
        ItemStack blood = item("blood_test_tube").copyWithCount(2);
        blood.set(ModDataComponents.ESSENCE_STORAGE,
                new EssenceStorage(EssenceValue.of(EssenceType.BLOOD, 25),
                        3_000, true));
        ItemStack experience = item("xpetrified_orb").copyWithCount(3);
        return new ItemStack[]{aureal, souls, blood, experience};
    }

    private static ItemStack item(String path) {
        return BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath(
                        "forbidden_arcanus", path))
                .orElseThrow().getDefaultInstance();
    }

    private static CompoundTag save(GameTestHelper helper,
                                    EssenceCondenserBlockEntity tile) {
        return tile.saveWithoutMetadata(helper.getLevel().registryAccess());
    }

    private static void setBanks(GameTestHelper helper,
                                 EssenceCondenserBlockEntity tile,
                                 int[] balances) {
        CompoundTag saved = save(helper, tile);
        saved.getCompound(EssenceCondenserBlockEntity.STATE_TAG)
                .putIntArray("banks", balances);
        tile.loadWithComponents(saved,
                helper.getLevel().registryAccess());
    }

    private static int[] banks(EssenceCondenserBlockEntity tile) {
        return new int[]{tile.getStoredEssence(0), tile.getStoredEssence(1),
                tile.getStoredEssence(2), tile.getStoredEssence(3)};
    }

}
