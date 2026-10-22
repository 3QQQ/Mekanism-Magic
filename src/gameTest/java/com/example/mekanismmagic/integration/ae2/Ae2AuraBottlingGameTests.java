package com.example.mekanismmagic.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCellInventory;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCells;
import com.example.mekanismmagic.integration.naturesaura.FeAuraConverterBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.items.ItemAuraBottle;
import de.ellpeck.naturesaura.items.ModItems;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Real powered-grid conservation for manual and CPU-delivered bottling. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Ae2AuraBottlingGameTests {
    private static final BlockPos ENERGY = new BlockPos(2, 1, 2);
    private static final BlockPos DRIVE = new BlockPos(2, 2, 2);
    private static final BlockPos CPU = new BlockPos(1, 1, 2);
    private static final BlockPos GENERATOR = new BlockPos(3, 1, 2);
    private Ae2AuraBottlingGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 160)
    public static void manualBottlingPaysTypedAuraAndNativeBottle(
            GameTestHelper helper) {
        DriveBlockEntity drive = createDrive(helper);
        AEKey nether = MagicResourceKeys.key(
                NaturesAuraAPI.TYPE_NETHER.getName());
        AEItemKey empty = AEItemKey.of(new ItemStack(
                ModItems.BOTTLE_TWO_THE_REBOTTLING));
        AEItemKey filled = Ae2AuraBottlingService.filledBottle(
                NaturesAuraAPI.TYPE_NETHER);
        helper.startSequence().thenWaitUntil(() ->
                assertReady(helper, drive, nether, empty))
                .thenExecute(() -> {
                    MEStorage storage = network(drive);
                    helper.assertTrue(storage.insert(nether, 40_000,
                            Actionable.MODULATE, IActionSource.empty())
                            == 40_000 && storage.insert(empty, 2,
                            Actionable.MODULATE, IActionSource.empty()) == 2,
                            "Powered network rejected exact bottling inputs");
                    var result = Ae2AuraBottlingService.bottleFromNetwork(
                            helper.getLevel(), helper.absolutePos(DRIVE),
                            drive.getMainNode().getGrid(),
                            IActionSource.empty(), nether, 2);
                    helper.assertTrue(result.bottled() == 2
                                    && result.status()
                                    == Ae2AuraBottlingService.Status.SUCCESS,
                            "Manual bottling did not complete two paid bottles");
                    helper.assertTrue(available(storage, nether) == 0
                                    && available(storage, empty) == 0
                                    && available(storage, filled) == 2,
                            "Bottling did not conserve typed Aura and empties");
                    AEKey wrong = MagicResourceKeys.source();
                    var rejected = Ae2AuraBottlingService.bottleFromNetwork(
                            helper.getLevel(), helper.absolutePos(DRIVE),
                            drive.getMainNode().getGrid(),
                            IActionSource.empty(), wrong, 1);
                    helper.assertTrue(rejected.bottled() == 0
                                    && rejected.status()
                                    == Ae2AuraBottlingService.Status.INVALID_TYPE
                                    && available(storage, filled) == 2,
                            "Unrelated AE magic key minted or altered a bottle");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 160)
    public static void craftingReceiptRequiresExactInputsAndPendingRecovers(
            GameTestHelper helper) {
        DriveBlockEntity drive = createDrive(helper);
        AEKey aura = MagicResourceKeys.key(
                NaturesAuraAPI.TYPE_OVERWORLD.getName());
        AEItemKey empty = AEItemKey.of(new ItemStack(
                ModItems.BOTTLE_TWO_THE_REBOTTLING));
        AEItemKey filled = Ae2AuraBottlingService.filledBottle(
                NaturesAuraAPI.TYPE_OVERWORLD);
        helper.startSequence().thenWaitUntil(() ->
                assertReady(helper, drive, aura, empty))
                .thenExecute(() -> {
                    ItemStack definition = ItemAuraBottle.setType(
                            new ItemStack(ModItems.AURA_BOTTLE),
                            NaturesAuraAPI.TYPE_OVERWORLD);
                    CustomData.update(DataComponents.CUSTOM_DATA, definition,
                            tag -> tag.putBoolean(
                                    "mekanism_magic_virtual_aura_bottling",
                                    true));
                    var decoded = Ae2AuraBottlingProvider.tryDecode(
                            AEItemKey.of(definition));
                    helper.assertTrue(decoded != null
                                    && decoded.getInputs().length == 2
                                    && decoded.getOutputs().size() == 1,
                            "Persisted virtual bottling pattern did not decode");
                    KeyCounter partial = new KeyCounter();
                    partial.add(empty, 1);
                    helper.assertFalse(Ae2AuraBottlingService
                                    .acceptCraftingInputs(helper.getLevel(),
                                            helper.absolutePos(DRIVE),
                                            drive.getMainNode().getGrid(),
                                            IActionSource.empty(), aura,
                                            new KeyCounter[]{partial}),
                            "A bottle without paid Aura was accepted");
                    // With only a magic disk mounted, a paid CPU receipt must
                    // become durable pending output, not vanish or mint an item.
                    ItemStack itemCell = drive.getInternalInventory()
                            .extractItem(1, 1, false);
                    helper.assertFalse(itemCell.isEmpty(),
                            "Could not detach the item disk");
                    KeyCounter paid = new KeyCounter();
                    paid.add(empty, 1);
                    paid.add(aura,
                            Ae2AuraBottlingService.AURA_PER_BOTTLE);
                    helper.assertTrue(Ae2AuraBottlingService
                                    .acceptCraftingInputs(helper.getLevel(),
                                            helper.absolutePos(DRIVE),
                                            drive.getMainNode().getGrid(),
                                            IActionSource.empty(), aura,
                                            new KeyCounter[]{paid}),
                            "Exact CPU receipt was not accepted");
                    helper.assertTrue(drive.getInternalInventory()
                                    .insertItem(1, itemCell, false).isEmpty(),
                            "Could not restore the item disk");
                }).thenWaitUntil(() -> helper.assertTrue(
                        drive.getOriginalCellInventory(1) != null,
                        "Item disk did not remount"))
                .thenExecute(() -> {
                    helper.assertTrue(Ae2AuraBottlingService.flushPending(
                                    helper.getLevel(),
                                    helper.absolutePos(DRIVE),
                                    drive.getMainNode().getGrid(),
                                    IActionSource.empty())
                                    && available(network(drive), filled) == 1,
                            "Paid pending bottle was not recovered exactly once");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 700)
    public static void selectedNetherGeneratorFeedsRealAeCraftingCpu(
            GameTestHelper helper) {
        DriveBlockEntity drive = createDrive(helper);
        helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_4K.block());
        helper.setBlock(GENERATOR,
                NaturesAuraRegistries.FE_AURA_CONVERTER_BLOCK.get());
        FeAuraConverterBlockEntity generator =
                helper.getBlockEntity(GENERATOR);
        helper.assertTrue(generator.cycleAuraTypeMode()
                        && generator.cycleAuraTypeMode()
                        && generator.getGenerationAuraType()
                        == NaturesAuraAPI.TYPE_NETHER,
                "Generator could not select Nether Aura in the Overworld");
        AEKey nether = MagicResourceKeys.key(
                NaturesAuraAPI.TYPE_NETHER.getName());
        AEKey overworld = MagicResourceKeys.key(
                NaturesAuraAPI.TYPE_OVERWORLD.getName());
        AEItemKey empty = AEItemKey.of(new ItemStack(
                ModItems.BOTTLE_TWO_THE_REBOTTLING));
        AEItemKey filled = Ae2AuraBottlingService.filledBottle(
                NaturesAuraAPI.TYPE_NETHER);
        AtomicReference<Future<ICraftingPlan>> calculation =
                new AtomicReference<>();
        AtomicReference<ICraftingSubmitResult> submission =
                new AtomicReference<>();
        AtomicReference<CraftingCPUCluster> selectedCpu =
                new AtomicReference<>();
        AtomicLong auraBefore = new AtomicLong();
        IActionSource source = IActionSource.ofMachine(
                Ae2MachineResourceProvider.forTile(generator));

        helper.startSequence().thenWaitUntil(() -> {
            assertReady(helper, drive, nether, empty);
            var node = Ae2MachineResourceProvider.forTile(generator)
                    .getActionableNode();
            helper.assertTrue(node != null && node.isActive()
                            && node.getGrid() == drive.getMainNode().getGrid(),
                    "Generator's existing AE node did not join the grid");
            ICraftingProvider provider = node.getService(
                    ICraftingProvider.class);
            helper.assertTrue(provider != null
                            && provider.getAvailablePatterns().stream()
                            .anyMatch(pattern -> pattern.getOutputs().stream()
                            .anyMatch(output -> filled.equals(output.what()))),
                    "Existing generator node did not advertise typed bottling");
            var livePattern = provider.getAvailablePatterns().stream()
                    .filter(pattern -> pattern.getOutputs().stream()
                            .anyMatch(output -> filled.equals(output.what())))
                    .findFirst().orElseThrow();
            var decoded = PatternDetailsHelper.decodePattern(
                    livePattern.getDefinition().toStack(),
                    helper.getLevel());
            helper.assertTrue(decoded != null && decoded.equals(livePattern)
                            && livePattern.equals(decoded)
                            && decoded.hashCode() == livePattern.hashCode(),
                    "AE CPU could not map a saved bottle pattern to its live provider");
            helper.assertTrue(node.getGrid().getCraftingService()
                            .isCraftable(filled)
                            && !node.getGrid().getCraftingService()
                            .getCpus().isEmpty(),
                    "Real AE crafting service did not index the bottling pattern");
        }).thenWaitUntil(() -> helper.assertTrue(
                available(network(drive), nether) >= 20_000L,
                "Selected Nether generator has not exported 20k Aura to AE"))
                .thenExecute(() -> generator.setControlType(
                        RedstoneControl.HIGH))
                .thenWaitUntil(() -> helper.assertTrue(
                        generator.getStoredAura() == 0,
                        "Paused generator has not exported its final Aura"))
                .thenExecute(() -> {
                    MEStorage storage = network(drive);
                    auraBefore.set(available(storage, nether));
                    helper.assertTrue(auraBefore.get() >= 20_000L
                                    && available(storage, overworld) == 0L,
                            "Cross-dimension export credited the world type");
                    helper.assertTrue(storage.insert(empty, 1,
                            Actionable.MODULATE, source) == 1,
                            "AE item disk rejected the native empty bottle");
                }).thenWaitUntil(() -> helper.assertTrue(
                        drive.getMainNode().getGrid().getStorageService()
                                .getCachedInventory().get(empty) == 1,
                        "AE cache has not seen the empty bottle"))
                .thenExecute(() -> calculation.set(
                        drive.getMainNode().getGrid().getCraftingService()
                                .beginCraftingCalculation(
                                        helper.getLevel(), () -> source,
                                        filled, 1,
                                        CalculationStrategy.REPORT_MISSING_ITEMS)))
                .thenWaitUntil(() -> {
                    helper.assertTrue(calculation.get() != null
                                    && calculation.get().isDone(),
                            "Real Aura bottling craft is still calculating");
                    if (submission.get() == null) {
                        ICraftingPlan plan;
                        try {
                            plan = calculation.get().get();
                        } catch (Exception failure) {
                            helper.fail("Aura bottling plan failed: " + failure);
                            return;
                        }
                        helper.assertTrue(plan != null
                                        && !plan.simulation()
                                        && plan.missingItems().isEmpty(),
                                "AE could not plan 1 empty + 20k typed Aura");
                        CraftingCPUCluster cpu = drive.getMainNode()
                                .getGrid().getCraftingService().getCpus()
                                .stream().filter(CraftingCPUCluster.class::isInstance)
                                .map(CraftingCPUCluster.class::cast)
                                .filter(candidate -> !candidate.isBusy()
                                        && candidate.getAvailableStorage()
                                        >= plan.bytes())
                                .findFirst().orElse(null);
                        helper.assertTrue(cpu != null,
                                "No real AE CPU can hold the Aura craft");
                        selectedCpu.set(cpu);
                        submission.set(drive.getMainNode().getGrid()
                                .getCraftingService().submitJob(plan, null,
                                        cpu, false, source));
                    }
                    helper.assertTrue(submission.get().successful(),
                            "AE rejected Aura craft: "
                                    + submission.get().errorCode());
                }).thenWaitUntil(() -> helper.assertTrue(
                        selectedCpu.get() != null
                                && !selectedCpu.get().isBusy()
                                && selectedCpu.get().getJobStatus() == null,
                        "AE CPU still waits for typed bottle output"))
                .thenExecute(() -> {
                    MEStorage storage = network(drive);
                    helper.assertTrue(available(storage, filled) == 1L
                                    && available(storage, empty) == 0L
                                    && available(storage, nether)
                                    == auraBefore.get() - 20_000L
                                    && available(storage, overworld) == 0L,
                            "Real CPU craft did not conserve selected Aura and empty bottle");
                }).thenSucceed();
    }

    private static DriveBlockEntity createDrive(GameTestHelper helper) {
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(DRIVE, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE);
        helper.assertTrue(drive.getInternalInventory().insertItem(0,
                        new ItemStack(MagicStorageCells.CELLS.get(1).get()),
                        false).isEmpty()
                        && drive.getInternalInventory().insertItem(1,
                        AEItems.ITEM_CELL_1K.stack(), false).isEmpty(),
                "Could not mount both magic and item storage cells");
        return drive;
    }

    private static void assertReady(GameTestHelper helper,
                                    DriveBlockEntity drive,
                                    AEKey aura, AEItemKey empty) {
        helper.assertTrue(drive.getMainNode().isActive()
                        && drive.getOriginalCellInventory(0)
                        instanceof MagicStorageCellInventory
                        && drive.getOriginalCellInventory(1) != null
                        && network(drive).insert(aura, 1,
                        Actionable.SIMULATE, IActionSource.empty()) == 1
                        && network(drive).insert(empty, 1,
                        Actionable.SIMULATE, IActionSource.empty()) == 1,
                "Waiting for a powered two-cell AE grid");
    }

    private static MEStorage network(DriveBlockEntity drive) {
        return drive.getMainNode().getGrid()
                .getStorageService().getInventory();
    }

    private static long available(MEStorage storage, AEKey key) {
        return storage.extract(key, Long.MAX_VALUE,
                Actionable.SIMULATE, IActionSource.empty());
    }
}
