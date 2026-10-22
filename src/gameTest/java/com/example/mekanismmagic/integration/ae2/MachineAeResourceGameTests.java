package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.blockentity.NativeDimensionMinerBlockEntity;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCellInventory;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCells;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ArsSourceMachineBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceRatePolicy;
import com.example.mekanismmagic.integration.common.network.MachineAeNodeOwnershipHooks;
import com.example.mekanismmagic.integration.mekextras.ExtraImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras.ExtraNatureAltarFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasNatureAltarFactories;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarFactoryBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraMachineBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import mekanism.api.Upgrade;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.api.energy.IEnergyConversionHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/** Finite powered-grid checks for automatic FE, Source and Aura supply. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineAeResourceGameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 2);
    private static final BlockPos ENERGY_POS = new BlockPos(2, 1, 2);
    private static final BlockPos DRIVE_POS = new BlockPos(2, 2, 2);
    private static final BlockPos SOURCE_JAR_POS = new BlockPos(4, 2, 2);
    private static final long SOURCE_AMOUNT = 6_000;
    private static final long AURA_AMOUNT = 7_000;
    private static final long APPFLUX_FE_AMOUNT = 20_000;

    private MachineAeResourceGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void adaptiveResourceBudgetsAreBoundedAndScaleWithWork(
            GameTestHelper helper) {
        helper.assertTrue(MachineAeResourceRatePolicy.energyBudget(
                        2_000_000L, 2_000_000L, 50L, 1)
                        == 50_000L
                        && MachineAeResourceRatePolicy.energyBudget(
                        46_000_000L, 46_000_000L, 12_800L, 23)
                        > 2_048L
                        && MachineAeResourceRatePolicy.energyBudget(
                        Long.MAX_VALUE, Long.MAX_VALUE,
                        Long.MAX_VALUE, Integer.MAX_VALUE)
                        == MachineAeResourceRatePolicy.MAX_ENERGY_J_PER_TICK
                        && MachineAeResourceRatePolicy.energyBudget(
                        46_000_000L, 31L, 12_800L, 23) == 31L,
                "Adaptive Joule budget ignored capacity, peak work, hard cap or small deficit");
        helper.assertTrue(MachineAeResourceRatePolicy.auraBudget(
                        500_000, 500_000) == 25_000
                        && MachineAeResourceRatePolicy.auraBudget(
                        11_500_000, 11_500_000)
                        == MachineAeResourceRatePolicy.MAX_AURA_PER_TICK
                        && MachineAeResourceRatePolicy.auraBudget(
                        11_500_000, 17) == 17,
                "Adaptive Aura budget ignored local capacity, hard cap or small deficit");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 100)
    public static void isolatedMachineNeverReportsConnectedOrCreatesEnergy(
            GameTestHelper helper) {
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK.get());
        TileEntityMekanism machine = helper.getBlockEntity(MACHINE_POS);
        setEnergy(machine, 0);
        setSource(machine, 0);
        helper.startSequence().thenIdle(40).thenExecute(() -> {
            var status = MachineAeResourceHooks.snapshot(machine);
            helper.assertTrue(status.supported() && status.enabled()
                            && !status.connected() && !status.active()
                            && energy(machine) == 0 && source(machine) == 0,
                    "Standalone machine reported a connected AE resource node"
                            + " or fabricated FE/Source");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 250)
    public static void optionalAppliedFluxCellSuppliesStoredFeWithoutMekE(
            GameTestHelper helper) {
        AEKey feKey = Ae2AppliedFluxEnergySource.keyIfAvailable();
        if (!ModCompatibility.loaded("appflux")) {
            helper.assertTrue(feKey == null,
                    "Absent Applied Flux exposed a phantom FE key");
            helper.succeed();
            return;
        }
        helper.assertTrue(feKey != null,
                "Installed Applied Flux has no native FE key");
        Network network = place(helper,
                NativeMekanismRegistries.RITUAL_BLOCK.get());
        TileEntityMekanism machine = network.machine();
        helper.assertTrue(((MeUpgradeRecipeMachineAdapter) machine)
                        .getMeUpgradeContainer().isEmpty(),
                "Applied Flux fixture unexpectedly uses MekE upgrades");
        var item = BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath(
                        "appflux", "fe_1k_cell"));
        helper.assertTrue(item.isPresent(),
                "Applied Flux 2.1.5 has no FE 1K storage cell");
        helper.assertTrue(network.drive().getInternalInventory().insertItem(
                        1, new ItemStack(item.orElseThrow()), false).isEmpty(),
                "Real AE drive rejected Applied Flux FE cell");
        helper.startSequence().thenWaitUntil(() -> {
            assertReady(helper, network, MachineAeResourceHooks.ENERGY);
            helper.assertTrue(network.drive().getOriginalCellInventory(1)
                            != null
                            && available(network, feKey) == 0,
                    "Applied Flux FE cell is not mounted or starts nonempty");
        }).thenExecute(() -> {
            helper.assertTrue(MachineAeResourceHooks.toggleSupply(machine),
                    "Could not stop native AE supply before FE seeding");
            setEnergy(machine, 0);
            seed(helper, network, feKey, APPFLUX_FE_AMOUNT);
        }).thenIdle(40).thenExecute(() -> {
            helper.assertTrue(energy(machine) == 0
                            && available(network, feKey)
                            == APPFLUX_FE_AMOUNT,
                    "Disabled machine consumed stored FE or gained free Joules");
            helper.assertTrue(MachineAeResourceHooks.toggleSupply(machine),
                    "Could not enable native AE stored-FE supply");
        }).thenWaitUntil(() -> {
            helper.assertTrue(energy(machine) > 0
                            && available(network, feKey)
                            < APPFLUX_FE_AMOUNT,
                    "Installed Applied Flux FE cell did not power machine");
        }).thenExecute(() -> {
            long spentFe = APPFLUX_FE_AMOUNT - available(network, feKey);
            double deliveredFe = IEnergyConversionHelper.INSTANCE
                    .feConversion().convertToDouble(energy(machine));
            helper.assertTrue(spentFe > 0
                            && Math.abs(spentFe - deliveredFe) <= 2.0
                            && MachineAeResourceHooks.snapshot(machine)
                            .lastEnergyJ()
                            <= Ae2MachineResourceAccess.MAX_ENERGY_J_PER_TICK,
                    "Stored-FE withdrawal and local Joules differ beyond"
                            + " conversion rounding");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 650)
    public static void apparatusFiniteGridSupplyToggleReloadAndDisconnect(
            GameTestHelper helper) {
        Block block = ArsNouveauRegistries
                .ENCHANTING_APPARATUS_PROCESSOR_BLOCK.get();
        Network network = place(helper, block);
        TileEntityMekanism initial = network.machine();
        ResourceLocation auraId = IAuraType.forLevel(helper.getLevel())
                .getName();
        AEKey sourceKey = MagicResourceKeys.source();
        AEKey auraKey = MagicResourceKeys.key(auraId);
        helper.assertTrue(sourceKey != null && auraKey != null
                        && !sourceKey.equals(auraKey),
                "Source and Aura did not retain distinct canonical keys");
        double initialPower = network.energyCell().getAECurrentPower();

        helper.startSequence().thenWaitUntil(() ->
                assertReady(helper, network, MachineAeResourceHooks.ENERGY
                        | MachineAeResourceHooks.SOURCE))
                .thenIdle(30).thenExecute(() -> {
                    helper.assertTrue(source(initial) == 0
                                    && network.cell().getAvailableStacks()
                                    .isEmpty(),
                            "Powered grid created Source without stored Source");
                    seed(helper, network, sourceKey, SOURCE_AMOUNT);
                    seed(helper, network, auraKey, 2_000);
                }).thenWaitUntil(() -> {
                    helper.assertTrue(source(initial) > 0
                                    && energy(initial) > 0,
                            "Apparatus did not receive finite-grid FE and Source");
                }).thenExecute(() -> {
                    assertSourceConserved(helper, network, initial,
                            SOURCE_AMOUNT);
                    helper.assertTrue(network.cell().storedAmount(auraId)
                                    == 2_000,
                            "Source consumer withdrew the wrong Aura key");
                    helper.assertTrue(network.energyCell().getAECurrentPower()
                                    < initialPower,
                            "FE appeared without finite AE power draw");
                    assertFiniteFe(helper, initial);
                    helper.assertTrue(MachineAeResourceHooks.toggleSupply(
                                    initial),
                            "The AE resource master switch did not turn off");
                    setSource(initial, 0);
                    setEnergy(initial, 0);
                    seed(helper, network, sourceKey, 1_000);
                }).thenIdle(40).thenExecute(() -> {
                    helper.assertTrue(!MachineAeResourceHooks.snapshot(initial)
                                    .enabled() && source(initial) == 0
                                    && energy(initial) == 0,
                            "Disabled machine continued network supply");
                    long disabledDisk = network.cell().storedAmount(
                            MagicResourceKeys.SOURCE_ID);
                    CompoundTag saved = initial.saveWithoutMetadata(
                            helper.getLevel().registryAccess());
                    helper.setBlock(MACHINE_POS, Blocks.AIR);
                    helper.setBlock(MACHINE_POS, block);
                    TileEntityMekanism restored = helper.getBlockEntity(
                            MACHINE_POS);
                    restored.loadAdditional(saved,
                            helper.getLevel().registryAccess());
                    helper.assertTrue(!MachineAeResourceHooks.snapshot(restored)
                                    .enabled() && source(restored) == 0,
                            "Saved AE supply switch did not survive machine reload");
                    network.currentMachine = restored;
                    network.disabledDisk = disabledDisk;
                }).thenIdle(40).thenExecute(() -> {
                    TileEntityMekanism restored = network.machine();
                    helper.assertTrue(source(restored) == 0
                                    && network.cell().storedAmount(
                                    MagicResourceKeys.SOURCE_ID)
                                    == network.disabledDisk,
                            "Reloaded disabled machine drained the magic disk");
                    helper.assertTrue(MachineAeResourceHooks.toggleSupply(
                                    restored),
                            "The saved AE supply switch did not turn on");
                }).thenWaitUntil(() -> {
                    helper.assertTrue(source(network.machine()) > 0,
                            "Re-enabled machine did not resume Source supply");
                }).thenExecute(() -> {
                    helper.setBlock(ENERGY_POS, Blocks.AIR);
                    setSource(network.machine(), 0);
                    setEnergy(network.machine(), 0);
                }).thenIdle(40).thenExecute(() -> {
                    helper.assertTrue(source(network.machine()) == 0
                                    && energy(network.machine()) == 0
                                    && !MachineAeResourceHooks.snapshot(
                                    network.machine()).active(),
                            "Disconnected machine received free FE or Source");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 260)
    public static void nativeImbuementFactoryReceivesFiniteFeAndSource(
            GameTestHelper helper) {
        runSourceFixture(helper,
                ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get());
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 260)
    public static void extrasImbuementFactoryReceivesFiniteFeAndSource(
            GameTestHelper helper) {
        runSourceFixture(helper,
                MekanismExtrasImbuementFactories.INFINITE_BLOCK.get());
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 220)
    public static void connectedSourceRefillsEveryTickWithoutDoublePull(
            GameTestHelper helper) {
        Network network = place(helper,
                ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get());
        ImbuementFactoryBlockEntity factory =
                (ImbuementFactoryBlockEntity) network.machine();
        AtomicInteger previous = new AtomicInteger(factory.getSource());
        AtomicInteger peak = new AtomicInteger();
        helper.onEachTick(() -> {
            int now = factory.getSource();
            peak.accumulateAndGet(Math.max(0,
                    now - previous.getAndSet(now)), Math::max);
        });
        helper.startSequence().thenWaitUntil(() ->
                        assertReady(helper, network,
                                MachineAeResourceHooks.ENERGY
                                        | MachineAeResourceHooks.SOURCE))
                .thenExecute(() -> seed(helper, network,
                        MagicResourceKeys.source(), 60_000L))
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertTrue(factory.getSource() >= 20_000
                                    && peak.get() > 0
                                    && peak.get() <= factory.getTransferRate(),
                            "Connected Source did not refill each tick or pulled AE twice in one tick");
                    assertSourceConserved(helper, network, factory, 60_000L);
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 230)
    public static void explicitlyLinkedJarStillPrecedesFastAeSource(
            GameTestHelper helper) {
        Network network = place(helper,
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        ArsSourceMachineBlockEntity machine =
                (ArsSourceMachineBlockEntity) network.machine();
        Block jarBlock = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(
                        "ars_nouveau", "source_jar"));
        helper.assertTrue(jarBlock != Blocks.AIR,
                "Vanilla Ars Source Jar is unavailable");
        helper.setBlock(SOURCE_JAR_POS, jarBlock);
        ISourceCap jar = helper.getLevel().getCapability(
                CapabilityRegistry.SOURCE_CAPABILITY,
                helper.absolutePos(SOURCE_JAR_POS), null);
        helper.assertTrue(jar != null
                        && machine.mekanismMagicLinkSourceJar(
                        helper.absolutePos(SOURCE_JAR_POS)),
                "Could not explicitly link vanilla Source Jar");
        helper.assertTrue(machine.mekanismMagicAeFastSourceBudget() == 0,
                "Fast AE path ignored an explicit Source link");
        helper.startSequence().thenWaitUntil(() ->
                        assertReady(helper, network,
                                MachineAeResourceHooks.ENERGY
                                        | MachineAeResourceHooks.SOURCE))
                .thenExecute(() -> {
                    helper.assertTrue(jar.receiveSource(10_000, false)
                                    == 10_000,
                            "Could not seed linked vanilla Source Jar");
                    seed(helper, network,
                            MagicResourceKeys.source(), 60_000L);
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        machine.getSource() > 0,
                        "Linked jar did not refill on its existing cadence"))
                .thenExecute(() -> {
                    helper.assertTrue(jar.getSource() < 10_000
                                    && network.cell().storedAmount(
                                    MagicResourceKeys.SOURCE_ID)
                                    == 60_000L,
                            "High-frequency AE pull displaced linked-jar priority");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 260)
    public static void natureAltarReceivesOnlyItsAuraKey(
            GameTestHelper helper) {
        runAuraFixture(helper,
                NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get());
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 260)
    public static void nativeNatureFactoryReceivesFiniteFeAndAura(
            GameTestHelper helper) {
        runAuraFixture(helper,
                NaturesAuraRegistries.BASIC_NATURE_ALTAR_FACTORY_BLOCK.get());
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 260)
    public static void extrasNatureFactoryReceivesFiniteFeAndAura(
            GameTestHelper helper) {
        runAuraFixture(helper,
                MekanismExtrasNatureAltarFactories.INFINITE_BLOCK.get());
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 300)
    public static void speedUpgradedFullWidthNatureFactoryIsNotThrottledByOldAeCaps(
            GameTestHelper helper) {
        Network network = place(helper,
                MekanismExtrasNatureAltarFactories.INFINITE_BLOCK.get());
        TileEntityMekanism factory = network.machine();
        var automation = (IMekanismMagicAutomation) factory;
        helper.assertTrue(automation.mekanismMagicPatternInputs().size() >= 17,
                "High-tier Nature factory fixture has too few parallel lanes");
        factory.getComponent().addUpgrades(Upgrade.SPEED, 8);
        helper.assertTrue(factory.getComponent().getUpgrades(Upgrade.SPEED) == 8,
                "High-tier factory speed upgrade was not installed");
        AtomicLong previousJ = new AtomicLong(energy(factory));
        AtomicInteger previousAura = new AtomicInteger(aura(factory));
        AtomicLong peakJ = new AtomicLong();
        AtomicInteger peakAura = new AtomicInteger();
        helper.onEachTick(() -> {
            long nowJ = energy(factory);
            int nowAura = aura(factory);
            peakJ.accumulateAndGet(Math.max(0L,
                    nowJ - previousJ.getAndSet(nowJ)), Math::max);
            peakAura.accumulateAndGet(Math.max(0,
                    nowAura - previousAura.getAndSet(nowAura)), Math::max);
        });
        ResourceLocation auraId = IAuraType.forLevel(helper.getLevel())
                .getName();
        AEKey auraKey = MagicResourceKeys.key(auraId);
        helper.assertTrue(auraKey != null,
                "High-tier factory Aura key is unavailable");
        helper.startSequence().thenWaitUntil(() ->
                        assertReady(helper, network,
                                MachineAeResourceHooks.ENERGY
                                        | MachineAeResourceHooks.AURA))
                .thenExecute(() -> seed(helper, network,
                        auraKey, 500_000L))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(peakJ.get() > 2_048L
                                    && peakJ.get()
                                    <= MachineAeResourceRatePolicy
                                    .MAX_ENERGY_J_PER_TICK,
                            "Speed-upgraded factory still used the old 2048 J/t AE limit");
                    helper.assertTrue(peakAura.get() > 4_096
                                    && peakAura.get()
                                    <= MachineAeResourceRatePolicy
                                    .MAX_AURA_PER_TICK,
                            "Full-width factory still used the old 4096 Aura/t AE limit");
                    helper.assertTrue(network.cell().storedAmount(auraId)
                                    + aura(factory) == 500_000L,
                            "Fast Aura refill lost or duplicated typed units");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 350)
    public static void minerDrygmyAndImbuementReuseTheirExistingAeNodes(
            GameTestHelper helper) {
        Network network = place(helper,
                ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        helper.startSequence().thenWaitUntil(() ->
                assertReady(helper, network, MachineAeResourceHooks.ENERGY
                        | MachineAeResourceHooks.SOURCE))
                .thenExecute(() -> {
                    IGridNode node = machineNode(helper);
                    helper.assertTrue(node == Ae2DirectOutputProvider
                                    .forTile((DrygmySimulatorBlockEntity)
                                            network.machine())
                                    .getGridNode(Direction.EAST)
                                    && node.getGrid().size() == 3,
                            "Drygmy resource supply created a duplicate AE node");
                    replaceMachine(helper, network,
                            ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
                }).thenWaitUntil(() ->
                        assertReady(helper, network,
                                MachineAeResourceHooks.ENERGY
                                        | MachineAeResourceHooks.SOURCE))
                .thenExecute(() -> {
                    IGridNode node = machineNode(helper);
                    helper.assertTrue(node == Ae2ImbuementProvider
                                    .forTile((ImbuementProcessorBlockEntity)
                                            network.machine())
                                    .getGridNode(Direction.EAST)
                                    && node.getGrid().size() == 3,
                            "Imbuement resource supply created a second AE node");
                    replaceMachine(helper, network,
                            NativeMekanismRegistries.DIMENSION_MINER_BLOCK.get());
                }).thenWaitUntil(() ->
                        assertReady(helper, network,
                                MachineAeResourceHooks.ENERGY))
                .thenExecute(() -> {
                    IGridNode node = machineNode(helper);
                    helper.assertTrue(node == Ae2DirectOutputProvider
                                    .forTile((NativeDimensionMinerBlockEntity)
                                            network.machine())
                                    .getGridNode(Direction.EAST)
                                    && node.getGrid().size() == 3,
                            "Dimension miner gained a duplicate resource node");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 500)
    public static void mekEUpgradeSwapsOneResourceNodeWithoutDuplication(
            GameTestHelper helper) {
        Network network = place(helper,
                NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.get());
        TileEntityMekanism machine = network.machine();
        var adapter = (MeUpgradeRecipeMachineAdapter) machine;
        helper.startSequence().thenWaitUntil(() ->
                assertReady(helper, network,
                        MachineAeResourceHooks.ENERGY))
                .thenExecute(() -> {
                    helper.assertTrue(machineNode(helper).getGrid().size() == 3,
                            "Initial fallback resource node was duplicated");
                    setEnergy(machine, 0);
                    helper.assertTrue(adapter.isMeUpgradeTarget()
                                    && adapter.getMeUpgradeContainer()
                                    .install(MeUpgradeType.PATTERN_PROVIDER)
                                    .successful(),
                            "Could not install real in-machine MekE upgrade");
                    adapter.onMeUpgradeStateChanged();
                    adapter.createMeNodeIfActive();
                    helper.assertTrue(network.energyCell().getMainNode()
                                    .getGrid().size() <= 3,
                            "MekE install temporarily left two machine AE nodes"
                                    + " before the next server tick");
                }).thenWaitUntil(() -> {
                    assertReady(helper, network,
                            MachineAeResourceHooks.ENERGY);
                    helper.assertTrue(machineNode(helper).getGrid().size() == 3
                                    && energy(machine) > 0,
                            "MekE node coexisted with fallback or stopped FE supply");
                }).thenExecute(() -> {
                    helper.assertTrue(MachineAeResourceHooks.toggleSupply(
                                    machine),
                            "MekE machine could not disable native AE supply");
                    setEnergy(machine, 0);
                    rechargeFiniteCell(helper, network.energyCell());
                }).thenIdle(40).thenExecute(() -> {
                    helper.assertTrue(energy(machine) == 0
                                    && !MachineAeResourceHooks.snapshot(machine)
                                    .enabled()
                                    && network.energyCell()
                                    .getAECurrentPower() > 0
                                    && machineNode(helper).getGrid().size() == 3,
                            "MekE node ignored the supply switch, lost"
                                    + " finite grid power or duplicated nodes");
                    helper.assertTrue(adapter.getMeUpgradeContainer()
                                    .uninstall(MeUpgradeType.PATTERN_PROVIDER),
                            "Could not remove empty in-machine MekE upgrade");
                    adapter.onMeUpgradeStateChanged();
                    helper.assertTrue(network.energyCell().getMainNode()
                                    .getGrid().size() <= 3,
                            "MekE removal temporarily duplicated fallback"
                                    + " and upgrade nodes before the next tick");
                    setEnergy(machine, 0);
                }).thenIdle(40).thenWaitUntil(() -> {
                    var status = MachineAeResourceHooks.snapshot(machine);
                    IInWorldGridNodeHost host = helper.getLevel()
                            .getCapability(
                                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                                    helper.absolutePos(MACHINE_POS),
                                    (Void) null);
                    IGridNode node = host == null ? null
                            : host.getGridNode(Direction.EAST);
                    int gridSize = node == null || node.getGrid() == null
                            ? -1 : node.getGrid().size();
                    double gridPower = node == null
                            || node.getGrid() == null ? -1
                            : node.getGrid().getEnergyService()
                            .getStoredPower();
                    double gridCapacity = node == null
                            || node.getGrid() == null ? -1
                            : node.getGrid().getEnergyService()
                            .getMaxStoredPower();
                    helper.assertTrue(node != null && gridSize == 3
                                    && status.connected() && status.active()
                                    && !status.enabled() && energy(machine) == 0,
                            "Disabled fallback node was duplicated or supplied"
                                    + " energy: host=" + (host == null ? "null"
                                    : host.getClass().getName())
                                    + ", node=" + node
                                    + ", gridSize=" + gridSize
                                    + ", cellAE="
                                    + network.energyCell().getAECurrentPower()
                                    + ", gridAE=" + gridPower
                                    + "/" + gridCapacity
                                    + ", gridPowered="
                                    + (node != null && node.getGrid() != null
                                    && node.getGrid().getEnergyService()
                                    .isNetworkPowered())
                                    + ", nodePowered="
                                    + (node != null && node.isPowered())
                                    + ", gridBooted="
                                    + (node != null && node.hasGridBooted())
                                    + ", channelsMet="
                                    + (node != null
                                    && node.meetsChannelRequirements())
                                    + ", localJ=" + energy(machine)
                                    + ", enabled=" + status.enabled()
                                    + ", connected=" + status.connected()
                                    + ", active=" + status.active()
                                    + ", externallyOwned="
                                    + MachineAeNodeOwnershipHooks
                                    .externallyOwned(machine));
                }).thenExecute(() -> {
                    helper.assertTrue(MachineAeResourceHooks.toggleSupply(
                                    machine),
                            "Fallback AE supply could not be re-enabled");
                }).thenWaitUntil(() -> {
                    assertReady(helper, network,
                            MachineAeResourceHooks.ENERGY);
                    helper.assertTrue(machineNode(helper).getGrid().size() == 3
                                    && energy(machine) > 0,
                            "Fallback node did not resume as sole FE supply node");
                }).thenSucceed();
    }

    private static void runSourceFixture(GameTestHelper helper, Block block) {
        Network network = place(helper, block);
        TileEntityMekanism machine = network.machine();
        assertBoundedEnergyEachTick(helper, machine);
        AtomicLong nearCapacityDisk = new AtomicLong();
        AEKey sourceKey = MagicResourceKeys.source();
        ResourceLocation auraId = IAuraType.forLevel(helper.getLevel())
                .getName();
        AEKey auraKey = MagicResourceKeys.key(auraId);
        helper.assertTrue(sourceKey != null && auraKey != null,
                "Canonical magic disk keys are unavailable");
        helper.startSequence().thenWaitUntil(() ->
                assertReady(helper, network, MachineAeResourceHooks.ENERGY
                        | MachineAeResourceHooks.SOURCE))
                .thenIdle(25).thenExecute(() -> {
                    helper.assertTrue(source(machine) == 0,
                            "Empty magic disk fabricated factory Source");
                    seed(helper, network, sourceKey, SOURCE_AMOUNT);
                    seed(helper, network, auraKey, 1_500);
                }).thenWaitUntil(() -> {
                    helper.assertTrue(source(machine) > 0
                                    && energy(machine) > 0,
                            "Factory did not receive network FE and Source");
                }).thenIdle(5).thenExecute(() -> {
                    assertSourceConserved(helper, network, machine,
                            SOURCE_AMOUNT);
                    helper.assertTrue(network.cell().storedAmount(auraId)
                                    == 1_500,
                            "Source factory drained an unrelated Aura key");
                    assertFiniteFe(helper, machine);
                    setSource(machine, maxSource(machine) - 3);
                    seed(helper, network, sourceKey, 1_000);
                    nearCapacityDisk.set(network.cell().storedAmount(
                            MagicResourceKeys.SOURCE_ID));
                }).thenIdle(25).thenExecute(() -> {
                    helper.assertTrue(source(machine) == maxSource(machine)
                                    && network.cell().storedAmount(
                                    MagicResourceKeys.SOURCE_ID)
                                    >= nearCapacityDisk.get() - 3,
                            "Source refill overfilled the local tank or withdrew"
                                    + " beyond its three-unit free capacity");
                }).thenSucceed();
    }

    private static void runAuraFixture(GameTestHelper helper, Block block) {
        Network network = place(helper, block);
        TileEntityMekanism machine = network.machine();
        assertBoundedEnergyEachTick(helper, machine);
        ResourceLocation auraId = IAuraType.forLevel(helper.getLevel())
                .getName();
        AEKey auraKey = MagicResourceKeys.key(auraId);
        AEKey sourceKey = MagicResourceKeys.source();
        helper.assertTrue(auraKey != null && sourceKey != null,
                "Canonical magic disk keys are unavailable");
        helper.startSequence().thenWaitUntil(() ->
                assertReady(helper, network, MachineAeResourceHooks.ENERGY
                        | MachineAeResourceHooks.AURA))
                .thenIdle(25).thenExecute(() -> {
                    helper.assertTrue(aura(machine) == 0,
                            "Empty magic disk fabricated machine Aura");
                    seed(helper, network, auraKey, AURA_AMOUNT);
                    seed(helper, network, sourceKey, 1_500);
                }).thenWaitUntil(() -> {
                    helper.assertTrue(aura(machine) > 0
                                    && energy(machine) > 0,
                            "Nature machine did not receive network FE and Aura");
                }).thenIdle(5).thenExecute(() -> {
                    helper.assertTrue(network.cell().storedAmount(auraId)
                                    + aura(machine) == AURA_AMOUNT
                                    && aura(machine) <= maxAura(machine)
                                    && network.cell().storedAmount(
                                    MagicResourceKeys.SOURCE_ID) == 1_500,
                            "Aura transfer lost units or withdrew Source key");
                    assertFiniteFe(helper, machine);
                }).thenSucceed();
    }

    private static Network place(GameTestHelper helper, Block block) {
        helper.setBlock(MACHINE_POS, block);
        TileEntityMekanism machine = helper.getBlockEntity(MACHINE_POS);
        setEnergy(machine, 0);
        helper.setBlock(ENERGY_POS, AEBlocks.ENERGY_CELL.block());
        EnergyCellBlockEntity energyCell = helper.getBlockEntity(ENERGY_POS);
        double stored = energyCell.getAEMaxPower() * 0.75;
        helper.assertTrue(stored > 0
                        && energyCell.injectAEPower(stored,
                        Actionable.MODULATE) < stored
                        && energyCell.getAECurrentPower() > 0,
                "Finite AE energy cell could not be charged");
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE_POS);
        helper.assertTrue(drive.getInternalInventory().insertItem(0,
                        new ItemStack(MagicStorageCells.CELLS.get(16).get()),
                        false).isEmpty(),
                "AE drive rejected the finite 16K magic disk");
        return new Network(machine, energyCell, drive);
    }

    private static void rechargeFiniteCell(GameTestHelper helper,
                                           EnergyCellBlockEntity cell) {
        double target = cell.getAEMaxPower() * 0.75;
        double needed = Math.max(0.0,
                target - cell.getAECurrentPower());
        if (needed > 0) {
            cell.injectAEPower(needed, Actionable.MODULATE);
        }
        helper.assertTrue(cell.getAECurrentPower()
                        >= cell.getAEMaxPower() * 0.5,
                "Could not externally recharge the finite AE test cell");
    }

    private static void replaceMachine(GameTestHelper helper,
                                       Network network, Block block) {
        helper.setBlock(MACHINE_POS, Blocks.AIR);
        helper.setBlock(MACHINE_POS, block);
        network.currentMachine = helper.getBlockEntity(MACHINE_POS);
        setEnergy(network.machine(), 0);
    }

    private static IGridNode machineNode(GameTestHelper helper) {
        IInWorldGridNodeHost host = helper.getLevel().getCapability(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                helper.absolutePos(MACHINE_POS), (Void) null);
        return host == null ? null : host.getGridNode(Direction.EAST);
    }

    private static void assertReady(GameTestHelper helper, Network network,
                                    int requiredMask) {
        var host = helper.getLevel().getCapability(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                helper.absolutePos(MACHINE_POS), (Void) null);
        IGridNode node = host == null ? null : host.getGridNode(
                Direction.EAST);
        MachineAeResourceHooks.Snapshot status =
                MachineAeResourceHooks.snapshot(network.machine());
        helper.assertTrue(network.drive().getMainNode().isActive()
                        && network.drive().isPowered()
                        && network.drive().getOriginalCellInventory(0)
                        instanceof MagicStorageCellInventory
                        && node != null && node.isActive()
                        && node.getGrid()
                        == network.drive().getMainNode().getGrid()
                        && status.supported() && status.enabled()
                        && status.connected() && status.active()
                        && (status.resourceMask() & requiredMask)
                        == requiredMask,
                "Waiting for powered AE machine supply node and magic disk");
    }

    private static void seed(GameTestHelper helper, Network network,
                             AEKey key, long amount) {
        long accepted = network.drive().getMainNode().getGrid()
                .getStorageService().getInventory().insert(key, amount,
                        Actionable.MODULATE, IActionSource.empty());
        helper.assertTrue(accepted == amount,
                "Magic disk rejected requested Source/Aura key or amount");
    }

    private static long available(Network network, AEKey key) {
        var cell = network.drive().getOriginalCellInventory(1);
        return cell == null ? 0 : cell.getAvailableStacks().get(key);
    }

    private static void assertSourceConserved(GameTestHelper helper,
                                              Network network,
                                              TileEntityMekanism machine,
                                              long total) {
        helper.assertTrue(network.cell().storedAmount(
                        MagicResourceKeys.SOURCE_ID) + source(machine) == total,
                "Machine Source and magic disk did not conserve units");
    }

    private static void assertFiniteFe(GameTestHelper helper,
                                       TileEntityMekanism machine) {
        var status = MachineAeResourceHooks.snapshot(machine);
        helper.assertTrue(energy(machine) > 0
                        && energy(machine) <= ((IMekanismMagicAutomation) machine)
                        .mekanismMagicEnergyContainer().getMaxEnergy()
                        && status.lastEnergyJ() > 0
                        && status.lastEnergyJ()
                        <= Ae2MachineResourceAccess.MAX_ENERGY_J_PER_TICK,
                "Finite AE power produced invalid or unbounded local FE");
    }

    private static void assertBoundedEnergyEachTick(
            GameTestHelper helper, TileEntityMekanism machine) {
        AtomicLong previous = new AtomicLong(energy(machine));
        helper.onEachTick(() -> {
            long current = energy(machine);
            long increase = current - previous.getAndSet(current);
            helper.assertTrue(increase
                            <= Ae2MachineResourceAccess.MAX_ENERGY_J_PER_TICK,
                    "One AE tick created more local Joules than its cap");
        });
    }

    private static long energy(TileEntityMekanism machine) {
        return ((IMekanismMagicAutomation) machine)
                .mekanismMagicEnergyContainer().getEnergy();
    }

    private static void setEnergy(TileEntityMekanism machine, long amount) {
        ((IMekanismMagicAutomation) machine)
                .mekanismMagicEnergyContainer().setEnergy(amount);
    }

    private static int source(TileEntityMekanism machine) {
        if (machine instanceof ArsSourceMachineBlockEntity ars) {
            return ars.getSource();
        }
        if (machine instanceof ImbuementFactoryBlockEntity factory) {
            return factory.getSource();
        }
        return ((ExtraImbuementFactoryBlockEntity) machine).getSource();
    }

    private static int maxSource(TileEntityMekanism machine) {
        if (machine instanceof ArsSourceMachineBlockEntity ars) {
            return ars.getMaxSource();
        }
        if (machine instanceof ImbuementFactoryBlockEntity factory) {
            return factory.getMaxSource();
        }
        return ((ExtraImbuementFactoryBlockEntity) machine)
                .getMaxSource();
    }

    private static void setSource(TileEntityMekanism machine, int amount) {
        if (machine instanceof ArsSourceMachineBlockEntity ars) {
            ars.setSource(amount);
        } else if (machine instanceof ImbuementFactoryBlockEntity factory) {
            factory.getSourceStorage().setSource(amount);
        } else {
            ((ExtraImbuementFactoryBlockEntity) machine)
                    .getSourceStorage().setSource(amount);
        }
    }

    private static int aura(TileEntityMekanism machine) {
        if (machine instanceof NatureAuraMachineBlockEntity nature) {
            return nature.getStoredAura();
        }
        if (machine instanceof NatureAltarFactoryBlockEntity factory) {
            return factory.getStoredAura();
        }
        return ((ExtraNatureAltarFactoryBlockEntity) machine)
                .getStoredAura();
    }

    private static int maxAura(TileEntityMekanism machine) {
        if (machine instanceof NatureAuraMachineBlockEntity nature) {
            return nature.getAuraCapacity();
        }
        if (machine instanceof NatureAltarFactoryBlockEntity factory) {
            return factory.getAuraCapacity();
        }
        return ((ExtraNatureAltarFactoryBlockEntity) machine)
                .getAuraCapacity();
    }

    private static final class Network {
        private TileEntityMekanism currentMachine;
        private final EnergyCellBlockEntity energyCell;
        private final DriveBlockEntity drive;
        private long disabledDisk;

        private Network(TileEntityMekanism machine,
                        EnergyCellBlockEntity energyCell,
                        DriveBlockEntity drive) {
            this.currentMachine = machine;
            this.energyCell = energyCell;
            this.drive = drive;
        }

        private TileEntityMekanism machine() {
            return currentMachine;
        }

        private EnergyCellBlockEntity energyCell() {
            return energyCell;
        }

        private DriveBlockEntity drive() {
            return drive;
        }

        private MagicStorageCellInventory cell() {
            return (MagicStorageCellInventory)
                    drive.getOriginalCellInventory(0);
        }
    }
}
