package com.example.mekanismmagic.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import com.example.mekanismmagic.integration.common.network.MachineDirectOutputHooks;
import com.example.mekanismmagic.integration.common.network.MachineDirectOutputHooks.DirectInsertResult;
import com.example.mekanismmagic.integration.common.network.MachineDirectOutputHooks.DirectNetworkStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Real AE storage coverage; the empty Drygmy never runs a harvest. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Ae2FluidDirectOutputGameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY_POS = new BlockPos(2, 1, 1);
    private static final BlockPos DRIVE_POS = new BlockPos(2, 2, 1);

    private Ae2FluidDirectOutputGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void offlineEmptyAndItemOnlyNetworksRejectFluids(
            GameTestHelper helper) {
        NetworkFixture fixture = createFixture(helper, false);
        FluidStack milk = milk(helper);
        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        AEItemKey stone = AEItemKey.of(Items.STONE);

        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertTrue(MachineDirectOutputHooks
                            .simulateFluidAcceptance(fixture.machine(),
                                    milk, 1000) == 0,
                    "Unpowered AE network simulated fluid acceptance");
            DirectInsertResult offline = MachineDirectOutputHooks
                    .insertFluidDetailed(fixture.machine(), milk, 1000);
            helper.assertTrue(offline.accepted() == 0
                            && !offline.attempted() && !offline.online(),
                    "Unpowered AE network accepted or attempted fluid output");
            helper.setBlock(ENERGY_POS,
                    AEBlocks.CREATIVE_ENERGY_CELL.block());
        }).thenWaitUntil(() -> assertNetworkReady(helper, fixture))
                .thenExecute(() -> {
                    assertRejected(helper, fixture, milk,
                            "Powered network without a cell");
                    installCell(helper, fixture.drive(), 0,
                            AEItems.ITEM_CELL_1K.stack());
                }).thenWaitUntil(() -> {
                    assertNetworkReady(helper, fixture);
                    helper.assertTrue(fixture.storage().insert(stone, 1,
                                    Actionable.SIMULATE,
                                    IActionSource.empty()) == 1,
                            "Item cell did not mount in the real AE drive");
                }).thenExecute(() -> {
                    // A different fluid key proves that this rejection is
                    // storage incompatibility, not milk's retry delay.
                    assertRejected(helper, fixture, water,
                            "Powered network with only an item cell");
                    helper.assertTrue(fixture.storage()
                                    .getAvailableStacks().isEmpty(),
                            "Rejected fluid output created a stored item");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void realMilkUsesMillibucketsAndSimulationDoesNotInsert(
            GameTestHelper helper) {
        NetworkFixture fixture = createFixture(helper, true,
                AEItems.FLUID_CELL_1K.stack());
        FluidStack milk = milk(helper);
        AEFluidKey milkKey = AEFluidKey.of(milk);

        helper.startSequence()
                .thenWaitUntil(() -> assertStorageReady(helper, fixture,
                        milkKey))
                .thenExecute(() -> {
                    for (int attempt = 0; attempt < 12; attempt++) {
                        helper.assertTrue(MachineDirectOutputHooks
                                        .simulateFluidAcceptance(
                                                fixture.machine(), milk,
                                                1000) == 1000,
                                "Fresh milk cell rejected a simulation");
                    }
                    helper.assertTrue(stored(fixture, milkKey) == 0,
                            "Fluid simulation wrote milk into AE storage");
                    DirectInsertResult inserted = MachineDirectOutputHooks
                            .insertFluidDetailed(fixture.machine(), milk,
                                    1000);
                    helper.assertTrue(inserted.accepted() == 1000
                                    && inserted.attempted()
                                    && inserted.status()
                                    == DirectNetworkStatus.ONLINE,
                            "Milk output did not insert exactly 1000 mB");
                    helper.assertTrue(stored(fixture, milkKey) == 1000
                                    && milk.getAmount() == 1000,
                            "Milk amount changed units or mutated its template");
                    DirectInsertResult repeated = MachineDirectOutputHooks
                            .insertFluidDetailed(fixture.machine(), milk,
                                    1000);
                    helper.assertTrue(repeated.accepted() == 0
                                    && !repeated.attempted()
                                    && repeated.retryAtGameTime()
                                    == helper.getLevel().getGameTime() + 1
                                    && stored(fixture, milkKey) == 1000,
                            "One fluid key bypassed the once-per-tick limit");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 260)
    public static void partialFluidAcceptanceRetriesAfterTwentyTicks(
            GameTestHelper helper) {
        NetworkFixture fixture = createFixture(helper, true,
                AEItems.FLUID_CELL_1K.stack());
        FluidStack milk = milk(helper);
        AEFluidKey milkKey = AEFluidKey.of(milk);
        AtomicLong capacity = new AtomicLong();
        AtomicLong retryAt = new AtomicLong();

        helper.startSequence()
                .thenWaitUntil(() -> assertStorageReady(helper, fixture,
                        milkKey))
                .thenExecute(() -> {
                    MEStorage storage = fixture.storage();
                    long total = storage.insert(milkKey, Long.MAX_VALUE,
                            Actionable.SIMULATE, IActionSource.empty());
                    helper.assertTrue(total > 1000,
                            "Real fluid cell has no usable capacity");
                    capacity.set(total);
                    helper.assertTrue(storage.insert(milkKey, total - 500,
                                    Actionable.MODULATE,
                                    IActionSource.empty()) == total - 500,
                            "Could not leave exactly 500 mB in the fluid cell");
                    helper.assertTrue(MachineDirectOutputHooks
                                    .simulateFluidAcceptance(fixture.machine(),
                                            milk, 1000) == 500
                                    && stored(fixture, milkKey) == total - 500,
                            "Partial fluid simulation changed storage or units");
                    DirectInsertResult partial = MachineDirectOutputHooks
                            .insertFluidDetailed(fixture.machine(), milk,
                                    1000);
                    retryAt.set(helper.getLevel().getGameTime() + 20);
                    helper.assertTrue(partial.accepted() == 500
                                    && partial.attempted()
                                    && partial.status()
                                    == DirectNetworkStatus.BLOCKED
                                    && partial.retryAtGameTime() == retryAt.get()
                                    && stored(fixture, milkKey) == total,
                            "Partial insert did not accept 500 mB and back off");
                    // Free real capacity so the next rejection can only come
                    // from the adapter's retry state, not a still-full cell.
                    helper.assertTrue(storage.extract(milkKey, 1000,
                                    Actionable.MODULATE,
                                    IActionSource.empty()) == 1000,
                            "Could not free fluid space for the retry check");
                }).thenIdle(1).thenExecute(() -> {
                    DirectInsertResult early = MachineDirectOutputHooks
                            .insertFluidDetailed(fixture.machine(), milk,
                                    1000);
                    helper.assertTrue(early.accepted() == 0
                                    && !early.attempted()
                                    && early.retryAtGameTime() == retryAt.get()
                                    && stored(fixture, milkKey)
                                    == capacity.get() - 1000,
                            "Fluid retry ignored its 20-tick rejection delay");
                }).thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().getGameTime() >= retryAt.get(),
                        "Waiting for the fluid retry deadline"))
                .thenExecute(() -> {
                    DirectInsertResult retried = MachineDirectOutputHooks
                            .insertFluidDetailed(fixture.machine(), milk,
                                    1000);
                    helper.assertTrue(retried.accepted() == 1000
                                    && retried.attempted()
                                    && stored(fixture, milkKey) == capacity.get(),
                            "Fluid output did not resume at its retry deadline");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void itemAndFluidInsertsShareEightAttemptsButNotSimulations(
            GameTestHelper helper) {
        NetworkFixture fixture = createFixture(helper, true,
                AEItems.FLUID_CELL_1K.stack(), AEItems.ITEM_CELL_1K.stack());
        FluidStack milk = milk(helper);
        FluidStack water = new FluidStack(Fluids.WATER, 1000);
        AEFluidKey milkKey = AEFluidKey.of(milk);
        AEFluidKey waterKey = AEFluidKey.of(water);
        List<Item> items = List.of(Items.STONE, Items.DIRT,
                Items.COBBLESTONE, Items.GRANITE, Items.DIORITE,
                Items.ANDESITE, Items.SAND);

        helper.startSequence().thenWaitUntil(() -> {
            assertStorageReady(helper, fixture, milkKey);
            assertStorageReady(helper, fixture, AEItemKey.of(Items.STONE));
        }).thenExecute(() -> {
            for (int attempt = 0; attempt < 12; attempt++) {
                helper.assertTrue(MachineDirectOutputHooks
                                .simulateFluidAcceptance(fixture.machine(),
                                        milk, 1000) == 1000,
                        "Fluid simulations consumed the execution budget");
            }
            for (Item item : items) {
                DirectInsertResult inserted = MachineDirectOutputHooks
                        .insertDetailed(fixture.machine(),
                                new ItemStack(item), 1);
                helper.assertTrue(inserted.accepted() == 1
                                && inserted.attempted(),
                        "A fluid simulation blocked an item execution");
            }
            DirectInsertResult eighth = MachineDirectOutputHooks
                    .insertFluidDetailed(fixture.machine(), milk, 1000);
            helper.assertTrue(eighth.accepted() == 1000
                            && eighth.attempted(),
                    "The eighth shared item/fluid execution was rejected");
            DirectInsertResult ninth = MachineDirectOutputHooks
                    .insertFluidDetailed(fixture.machine(), water, 1000);
            long nextTick = helper.getLevel().getGameTime() + 1;
            helper.assertTrue(ninth.accepted() == 0 && !ninth.attempted()
                            && ninth.retryAtGameTime() == nextTick
                            && stored(fixture, waterKey) == 0,
                    "Fluid execution bypassed the shared eight-attempt budget");
            DirectInsertResult extraItem = MachineDirectOutputHooks
                    .insertDetailed(fixture.machine(),
                            new ItemStack(Items.GLASS), 1);
            helper.assertTrue(extraItem.accepted() == 0
                            && !extraItem.attempted()
                            && extraItem.retryAtGameTime() == nextTick,
                    "Item execution bypassed the exhausted shared budget");
            helper.assertTrue(MachineDirectOutputHooks
                            .simulateFluidAcceptance(fixture.machine(),
                                    water, 1000) == 1000
                            && stored(fixture, waterKey) == 0,
                    "An exhausted execution budget blocked or executed a preview");
        }).thenIdle(1).thenExecute(() -> {
            DirectInsertResult deferred = MachineDirectOutputHooks
                    .insertFluidDetailed(fixture.machine(), water, 1000);
            helper.assertTrue(deferred.accepted() == 1000
                            && deferred.attempted()
                            && stored(fixture, waterKey) == 1000
                            && stored(fixture, milkKey) == 1000,
                    "Shared execution budget did not reset on the next tick");
            for (Item item : items) {
                helper.assertTrue(stored(fixture, AEItemKey.of(item)) == 1,
                        "Shared fluid budget changed an accepted item amount");
            }
        }).thenSucceed();
    }

    private static NetworkFixture createFixture(GameTestHelper helper,
                                                boolean powered,
                                                ItemStack... cells) {
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        DrygmySimulatorBlockEntity machine =
                helper.getBlockEntity(MACHINE_POS);
        machine.getNativeEnergyContainer().setEnergy(0);
        machine.setSource(0);
        if (powered) {
            helper.setBlock(ENERGY_POS,
                    AEBlocks.CREATIVE_ENERGY_CELL.block());
        }
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE_POS);
        for (int slot = 0; slot < cells.length; slot++) {
            installCell(helper, drive, slot, cells[slot]);
        }
        return new NetworkFixture(machine, drive);
    }

    private static void installCell(GameTestHelper helper,
                                    DriveBlockEntity drive, int slot,
                                    ItemStack cell) {
        helper.assertTrue(drive.getInternalInventory()
                        .insertItem(slot, cell, false).isEmpty(),
                "Could not install a real AE storage cell");
    }

    private static FluidStack milk(GameTestHelper helper) {
        FluidStack milk = FluidUtil.getFluidContained(
                new ItemStack(Items.MILK_BUCKET)).orElse(FluidStack.EMPTY);
        helper.assertTrue(!milk.isEmpty() && milk.getAmount() == 1000
                        && ResourceLocation.withDefaultNamespace("milk")
                        .equals(BuiltInRegistries.FLUID.getKey(milk.getFluid())),
                "Milk bucket does not expose the real minecraft:milk fluid");
        return milk;
    }

    private static void assertNetworkReady(GameTestHelper helper,
                                           NetworkFixture fixture) {
        IGridNode node = fixture.node();
        helper.assertTrue(node != null && node.isActive()
                        && fixture.drive().isPowered(),
                "Drygmy direct-output node did not join the powered AE grid");
    }

    private static void assertStorageReady(GameTestHelper helper,
                                           NetworkFixture fixture, AEKey key) {
        assertNetworkReady(helper, fixture);
        helper.assertTrue(fixture.storage().insert(key, 1,
                        Actionable.SIMULATE, IActionSource.empty()) == 1,
                "Real AE cell did not mount for " + key);
    }

    private static void assertRejected(GameTestHelper helper,
                                        NetworkFixture fixture,
                                        FluidStack fluid, String description) {
        helper.assertTrue(MachineDirectOutputHooks
                        .simulateFluidAcceptance(fixture.machine(), fluid,
                                1000) == 0,
                description + " simulated fluid acceptance");
        DirectInsertResult result = MachineDirectOutputHooks
                .insertFluidDetailed(fixture.machine(), fluid, 1000);
        helper.assertTrue(result.accepted() == 0 && result.attempted()
                        && result.status() == DirectNetworkStatus.BLOCKED
                        && result.retryAtGameTime()
                        == helper.getLevel().getGameTime() + 20
                        && stored(fixture, AEFluidKey.of(fluid)) == 0,
                description + " did not reject an actual fluid attempt");
    }

    private static long stored(NetworkFixture fixture, AEKey key) {
        return fixture.storage().getAvailableStacks().get(key);
    }

    private record NetworkFixture(DrygmySimulatorBlockEntity machine,
                                  DriveBlockEntity drive) {
        private IGridNode node() {
            return Ae2DirectOutputProvider.forTile(machine).getActionableNode();
        }

        private MEStorage storage() {
            return node().getGrid().getStorageService().getInventory();
        }
    }
}
