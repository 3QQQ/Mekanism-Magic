package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeTier;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import com.example.mekanismmagic.integration.common.network.MagicPipeRegistries;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceCapabilities;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceHost;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.ModList;

/** Real Mekanism topology and Nature's Aura 41.9 capability regressions. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagicPipeAuraGameTests {
    private static final BlockPos FIRST = new BlockPos(1, 2, 2);
    private static final BlockPos MIDDLE = new BlockPos(2, 2, 2);
    private static final BlockPos LAST = new BlockPos(3, 2, 2);

    private MagicPipeAuraGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void sharedStorageAndRetainedSidedViews(GameTestHelper helper) {
        place(helper, FIRST);
        helper.startSequence().thenExecuteAfter(5, () -> {
            MagicSourceTransmitter pipe = pipe(helper, FIRST);
            IAuraContainer aura = capability(helper, FIRST, Direction.UP);
            helper.assertTrue(pipe.getSourceCapability(null)
                            .receiveSource(2_000, false) == 2_000,
                    "Could not fill the independent Source pool");
            helper.assertTrue(aura.storeAura(20_000, false) == 20_000,
                    "A full Source pool blocked Aura capacity");
            helper.assertTrue(pipe.getSource() == 2_000
                            && aura.getStoredAura() == 20_000,
                    "Source and Aura were combined or converted");
            helper.assertTrue(aura.storeAura(-1, false) == 0
                            && aura.drainAura(-1, false) == 0
                            && aura.drainAura(100, true) == 100
                            && aura.getStoredAura() == 20_000,
                    "Negative or simulated Aura transfer mutated contents");
            pipe.getSourceCapability(null).extractSource(500, false);
            helper.assertTrue(aura.getStoredAura() == 20_000,
                    "Extracting Source drained Aura");
            mode(pipe, Direction.UP, ConnectionType.PULL);
            helper.assertTrue(aura.drainAura(1, false) == 0,
                    "Retained NORMAL capability bypassed PULL mode");
            mode(pipe, Direction.UP, ConnectionType.PUSH);
            helper.assertTrue(aura.drainAura(100, false) == 100
                            && aura.storeAura(100, false) == 0,
                    "PUSH Aura permissions were reversed");
            mode(pipe, Direction.UP, ConnectionType.NONE);
            helper.assertTrue(aura.drainAura(1, false) == 0
                            && aura.storeAura(1, false) == 0
                            && MagicPipeAuraIntegration.capability(pipe,
                            Direction.UP) == null,
                    "NONE left a cached Aura capability operational");
            helper.assertTrue(pipe.getSource() == 1_500,
                    "Aura side changes altered Source contents");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 160)
    public static void auraSupplyDemandUsesSharedPipeAndRefreshesEndpoints(
            GameTestHelper helper) {
        BlockPos donor = new BlockPos(1, 2, 3);
        BlockPos firstPipe = new BlockPos(2, 2, 3);
        BlockPos lastPipe = new BlockPos(3, 2, 3);
        BlockPos sink = new BlockPos(4, 2, 3);
        helper.setBlock(donor,
                NaturesAuraRegistries.AURA_FIELD_INTERFACE_BLOCK.get());
        helper.setBlock(sink,
                NaturesAuraRegistries.AURA_FIELD_INTERFACE_BLOCK.get());
        place(helper, firstPipe);
        place(helper, lastPipe);
        mode(pipe(helper, firstPipe), Direction.WEST, ConnectionType.PULL);
        mode(pipe(helper, lastPipe), Direction.EAST, ConnectionType.PUSH);
        NatureAuraMachineBlockEntity supply = machine(helper, donor);
        setAuraMode(supply, Direction.EAST,
                NatureAuraMachineBlockEntity.AuraSideMode.OUTPUT);
        setAuraMode(machine(helper, sink), Direction.WEST,
                NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        helper.startSequence().thenExecuteAfter(5, () -> {
            helper.assertTrue(pipe(helper, firstPipe).getConnectionType(
                            Direction.WEST) == ConnectionType.PULL
                            && pipe(helper, lastPipe).getConnectionType(
                            Direction.EAST) == ConnectionType.PUSH,
                    "Aura-only machines did not connect to shared pipes");
            helper.assertTrue(supply.getAuraContainer(Direction.UP)
                            .storeAura(5_000, false) == 5_000,
                    "Could not seed the Aura supply machine");
            pipe(helper, firstPipe).getSourceCapability(null)
                    .receiveSource(1_234, false);
        }).thenExecuteAfter(5, () -> {
            helper.assertTrue(machine(helper, sink).getStoredAura() == 5_000
                            && supply.getStoredAura() == 0,
                    "The shared Aura channel did not move supply to demand");
            helper.assertTrue(pipe(helper, firstPipe).getSource() == 1_234,
                    "An Aura-only endpoint consumed Source");
            helper.setBlock(sink, Blocks.AIR);
        }).thenExecuteAfter(2, () -> {
            helper.setBlock(sink,
                    NaturesAuraRegistries.AURA_FIELD_INTERFACE_BLOCK.get());
            setAuraMode(machine(helper, sink), Direction.WEST,
                    NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
            supply.getAuraContainer(Direction.UP).storeAura(3_000, false);
        }).thenExecuteAfter(5, () -> helper.assertTrue(
                machine(helper, sink).getStoredAura() == 3_000
                        && supply.getStoredAura() == 0,
                "Replaced endpoint retained a stale Aura capability cache"))
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void sourceAuraAndEssenceCoexistOnOneTopology(GameTestHelper helper) {
        // The Nature-only matrix also compiles and runs this class without F&A.
        if (!ModList.get().isLoaded("forbidden_arcanus")) {
            helper.startSequence().thenSucceed();
            return;
        }
        BlockPos donor = new BlockPos(1, 2, 3);
        BlockPos firstPipe = new BlockPos(2, 2, 3);
        BlockPos lastPipe = new BlockPos(3, 2, 3);
        BlockPos sink = new BlockPos(4, 2, 3);
        helper.setBlock(donor, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, "essence_condenser")));
        helper.setBlock(sink, BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, "hephaestus_forge_processor")));
        place(helper, firstPipe);
        place(helper, lastPipe);
        mode(pipe(helper, firstPipe), Direction.WEST, ConnectionType.PULL);
        mode(pipe(helper, lastPipe), Direction.EAST, ConnectionType.PUSH);
        helper.startSequence().thenExecuteAfter(5, () -> {
            var tile = helper.getBlockEntity(donor);
            CompoundTag tag = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
            tag.getCompound("essence_condenser_machine")
                    .putIntArray("banks", new int[]{200, 3, 0, 0});
            tile.loadWithComponents(tag, helper.getLevel().registryAccess());
            helper.assertTrue(pipe(helper, firstPipe).getSourceCapability(null)
                            .receiveSource(432, false) == 432
                            && capability(helper, firstPipe, null).storeAura(1_234, false) == 1_234,
                    "Could not seed independent Source and Aura on the essence topology");
        }).thenExecuteAfter(8, () -> {
            EssenceResourceHost condenser = helper.getLevel().getCapability(
                    EssenceResourceCapabilities.ESSENCE, helper.absolutePos(donor), Direction.EAST);
            EssenceResourceHost forge = helper.getLevel().getCapability(
                    EssenceResourceCapabilities.ESSENCE, helper.absolutePos(sink), Direction.WEST);
            helper.assertTrue(condenser != null && forge != null
                            && condenser.getStoredEssence(0) == 0
                            && condenser.getStoredEssence(1) == 0
                            && forge.getStoredEssence(0) == 200
                            && forge.getStoredEssence(1) == 3,
                    "Essence failed to transfer on the shared Source/Aura topology");
            helper.assertTrue(pipe(helper, firstPipe).getSource() == 432
                            && channel(helper, firstPipe).stored() == 1_234,
                    "Essence transfer changed the independent Source or Aura pools");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void poweredSharedPipeDisablesRetainedAuraView(
            GameTestHelper helper) {
        place(helper, FIRST);
        IAuraContainer[] retained = new IAuraContainer[1];
        helper.startSequence().thenExecuteAfter(5, () -> {
            MagicSourceTransmitter pipe = pipe(helper, FIRST);
            retained[0] = capability(helper, FIRST, Direction.UP);
            retained[0].storeAura(1_000, false);
            pipe.onRightClick(helper.makeMockPlayer(
                    net.minecraft.world.level.GameType.SURVIVAL), Direction.UP);
            helper.setBlock(FIRST.below(), Blocks.REDSTONE_BLOCK);
            pipe.onNeighborBlockChange(Direction.DOWN);
        }).thenExecuteAfter(3, () -> {
            helper.assertTrue(pipe(helper, FIRST).isRedstoneActivated(),
                    "Shared pipe did not react to real redstone power");
            helper.assertTrue(retained[0].storeAura(1, false) == 0
                            && retained[0].drainAura(1, false) == 0,
                    "A retained Aura view bypassed powered isolation");
            helper.setBlock(FIRST.below(), Blocks.AIR);
            pipe(helper, FIRST).onNeighborBlockChange(Direction.DOWN);
        }).thenExecuteAfter(5, () -> helper.assertTrue(
                retained[0].drainAura(1_000, false) == 1_000,
                "Redstone reconnection lost the saved Aura share"))
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 200)
    public static void sharedMergeSplitReloadAndUpgradeConserveBothPools(
            GameTestHelper helper) {
        place(helper, FIRST);
        place(helper, LAST);
        CompoundTag[] saved = new CompoundTag[3];
        long[] detached = new long[2];
        helper.startSequence().thenExecuteAfter(5, () -> {
            for (BlockPos pos : new BlockPos[]{FIRST, LAST}) {
                capability(helper, pos, null).storeAura(15_000, false);
                pipe(helper, pos).getSourceCapability(null)
                        .receiveSource(500, false);
            }
            place(helper, MIDDLE);
        }).thenExecuteAfter(6, () -> {
            assertPools(helper, FIRST, 30_000, 1_000);
            helper.assertTrue(pipe(helper, FIRST).getTransmitterNetwork()
                            == pipe(helper, LAST).getTransmitterNetwork(),
                    "Connected pipes did not merge their topology");
            MagicSourceTransmitter middle = pipe(helper, MIDDLE);
            middle.validateAndTakeShare();
            detached[0] = extension(middle).localStored();
            detached[1] = middle.getShare().getSource();
            middle.validateAndTakeShare();
            helper.assertTrue(extension(middle).localStored() == detached[0]
                            && middle.getShare().getSource() == detached[1],
                    "Repeated lifecycle share claims overwrote local contents");
            helper.setBlock(MIDDLE, Blocks.AIR);
        }).thenExecuteAfter(6, () -> {
            helper.assertTrue(pipe(helper, FIRST).getTransmitterNetwork()
                            != pipe(helper, LAST).getTransmitterNetwork(),
                    "Removing the middle pipe did not split topology");
            helper.assertTrue(channel(helper, FIRST).stored()
                            + channel(helper, LAST).stored() + detached[0]
                            == 30_000,
                    "Aura split lost or duplicated the removed segment share");
            helper.assertTrue(pipe(helper, FIRST).getSource()
                            + pipe(helper, LAST).getSource() + detached[1]
                            == 1_000,
                    "Source split lost or duplicated contents");
            place(helper, MIDDLE);
            CompoundTag restored = new CompoundTag();
            restored.putInt("magic_pipe_aura_share", (int) detached[0]);
            restored.putInt("magic_source_save_share", (int) detached[1]);
            pipe(helper, MIDDLE).read(helper.getLevel().registryAccess(),
                    restored);
        }).thenExecuteAfter(6, () -> {
            assertPools(helper, FIRST, 30_000, 1_000);
            BlockPos[] positions = {FIRST, MIDDLE, LAST};
            for (int i = 0; i < positions.length; i++) {
                saved[i] = pipe(helper, positions[i]).write(
                        helper.getLevel().registryAccess(), new CompoundTag());
            }
            for (BlockPos pos : positions) {
                helper.setBlock(pos, Blocks.AIR);
            }
        }).thenExecuteAfter(5, () -> {
            BlockPos[] positions = {FIRST, MIDDLE, LAST};
            for (int i = 0; i < positions.length; i++) {
                place(helper, positions[i]);
                pipe(helper, positions[i]).read(
                        helper.getLevel().registryAccess(), saved[i]);
            }
        }).thenExecuteAfter(6, () -> {
            assertPools(helper, FIRST, 30_000, 1_000);
            MagicSourceTransmitter middle = pipe(helper, MIDDLE);
            middle.startUpgrading();
            var upgrade = middle.getUpgradeData();
            helper.setBlock(MIDDLE, Blocks.AIR);
            helper.setBlock(MIDDLE,
                    MagicPipeRegistries.ADVANCED_MAGIC_SOURCE_PIPE_BLOCK.get());
            pipe(helper, MIDDLE).parseUpgradeData(upgrade);
        }).thenExecuteAfter(6, () -> {
            assertPools(helper, FIRST, 30_000, 1_000);
            helper.assertTrue(channel(helper, FIRST).capacity() == 120_000,
                    "Advanced upgrade did not recompute independent Aura capacity");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void largeAuraPoolsAndForeignSavedTypesStaySafe(
            GameTestHelper helper) {
        helper.setBlock(FIRST,
                MagicPipeRegistries.INFINITE_MAGIC_SOURCE_PIPE_BLOCK.get());
        helper.setBlock(MIDDLE,
                MagicPipeRegistries.INFINITE_MAGIC_SOURCE_PIPE_BLOCK.get());
        helper.startSequence().thenExecuteAfter(5, () -> {
            IAuraContainer aura = capability(helper, FIRST, null);
            helper.assertTrue(aura.storeAura(Integer.MAX_VALUE, false)
                            == Integer.MAX_VALUE
                            && aura.storeAura(Integer.MAX_VALUE, false)
                            == Integer.MAX_VALUE,
                    "Multi-segment Aura storage was truncated to int");
            helper.assertTrue(channel(helper, FIRST).stored()
                            == 2L * Integer.MAX_VALUE,
                    "A large Aura pool overflowed");
            CompoundTag first = pipe(helper, FIRST).write(
                    helper.getLevel().registryAccess(), new CompoundTag());
            CompoundTag middle = pipe(helper, MIDDLE).write(
                    helper.getLevel().registryAccess(), new CompoundTag());
            helper.assertTrue((long) first.getInt("magic_pipe_aura_share")
                            + middle.getInt("magic_pipe_aura_share")
                            == 2L * Integer.MAX_VALUE,
                    "Large Aura save shares lost contents");
            for (MagicSourcePipeTier tier : MagicSourcePipeTier.values()) {
                helper.assertTrue(MagicPipeAuraTransmitter.tierCapacity(tier)
                                == Math.min(Integer.MAX_VALUE,
                                tier.capacity() * 10L),
                        "Aura tier scaling overflowed at " + tier);
            }
            IAuraType current = IAuraType.forLevel(helper.getLevel());
            IAuraType foreign = NaturesAuraAPI.AURA_TYPES.values().stream()
                    .filter(type -> !current.isSimilar(type)).findFirst()
                    .orElseThrow();
            helper.assertTrue(!aura.isAcceptableType(foreign),
                    "Shared Aura capability accepted a foreign type");
            place(helper, new BlockPos(5, 2, 2));
            MagicSourceTransmitter quarantined = pipe(helper,
                    new BlockPos(5, 2, 2));
            CompoundTag foreignTag = new CompoundTag();
            foreignTag.putInt("natural_aura_save_share", 1_234);
            foreignTag.putString("natural_aura_save_share_type",
                    foreign.getName().toString());
            quarantined.extension().read(helper.getLevel().registryAccess(),
                    foreignTag);
            helper.assertTrue(MagicPipeAuraIntegration.capability(
                            quarantined, null) == null
                            && !quarantined.extension().createValidator()
                            .canAcceptPipe(quarantined),
                    "Foreign Aura escaped its quarantined saved share");
            CompoundTag roundTrip = new CompoundTag();
            quarantined.extension().write(helper.getLevel().registryAccess(),
                    roundTrip);
            helper.assertTrue(roundTrip.getInt("magic_pipe_aura_share") == 1_234
                            && roundTrip.getString("magic_pipe_aura_type")
                            .equals(foreign.getName().toString()),
                "Foreign Aura changed type or amount during save");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void legacyAuraPipeAliasesRestoreIntoSharedTopology(
            GameTestHelper helper) {
        for (String prefix : new String[]{"", "advanced_", "elite_", "ultimate_"}) {
            ResourceLocation oldId = ResourceLocation.fromNamespaceAndPath(
                    MekanismMagic.MOD_ID, prefix + "natural_aura_pipe");
            ResourceLocation newId = ResourceLocation.fromNamespaceAndPath(
                    MekanismMagic.MOD_ID, prefix + "magic_source_pipe");
            helper.assertTrue(BuiltInRegistries.BLOCK.get(oldId)
                            == BuiltInRegistries.BLOCK.get(newId)
                            && BuiltInRegistries.BLOCK.get(oldId) != Blocks.AIR,
                    "Legacy Aura block alias did not resolve: " + oldId);
            helper.assertTrue(BuiltInRegistries.ITEM.get(oldId)
                            == BuiltInRegistries.ITEM.get(newId),
                    "Legacy Aura item alias did not resolve: " + oldId);
            helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(oldId)
                            == BuiltInRegistries.BLOCK_ENTITY_TYPE.get(newId)
                            && BuiltInRegistries.BLOCK_ENTITY_TYPE.get(oldId)
                            != null,
                    "Legacy Aura block-entity alias did not resolve: " + oldId);
        }
        helper.setBlock(FIRST, BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID,
                        "natural_aura_pipe")));
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("natural_aura_save_share", 12_345);
        legacy.putString("natural_aura_save_share_type",
                IAuraType.forLevel(helper.getLevel()).getName().toString());
        pipe(helper, FIRST).read(helper.getLevel().registryAccess(), legacy);
        place(helper, MIDDLE);
        helper.startSequence().thenExecuteAfter(6, () -> {
            assertPools(helper, FIRST, 12_345, 0);
            CompoundTag first = pipe(helper, FIRST).write(
                    helper.getLevel().registryAccess(), new CompoundTag());
            CompoundTag middle = pipe(helper, MIDDLE).write(
                    helper.getLevel().registryAccess(), new CompoundTag());
            helper.assertTrue(first.getInt("magic_pipe_aura_share")
                            + middle.getInt("magic_pipe_aura_share") == 12_345
                            && !first.contains("natural_aura_save_share")
                            && !middle.contains("natural_aura_save_share"),
                    "Legacy Aura did not migrate to exact shared save shares");
        }).thenSucceed();
    }

    private static void place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, MagicPipeRegistries.MAGIC_SOURCE_PIPE_BLOCK.get());
    }

    private static MagicSourceTransmitter pipe(GameTestHelper helper,
                                              BlockPos pos) {
        helper.assertTrue(helper.getBlockEntity(pos)
                        instanceof MagicSourcePipeBlockEntity,
                "Expected shared magic pipe at " + pos);
        return ((MagicSourcePipeBlockEntity) helper.getBlockEntity(pos))
                .getTransmitter();
    }

    private static IAuraContainer capability(GameTestHelper helper,
                                             BlockPos pos, Direction side) {
        IAuraContainer capability = helper.getLevel().getCapability(
                NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                helper.absolutePos(pos), side);
        helper.assertTrue(capability != null,
                "Shared pipe lacks a real Nature's Aura capability");
        return capability;
    }

    private static MagicPipeAuraTransmitter extension(
            MagicSourceTransmitter pipe) {
        return com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks
                .pipeExtension(pipe, MagicPipeAuraTransmitter.class);
    }

    private static MagicPipeAuraChannel channel(GameTestHelper helper,
                                                BlockPos pos) {
        MagicSourceTransmitter pipe = pipe(helper, pos);
        helper.assertTrue(pipe.hasTransmitterNetwork(),
                "Shared pipe has not joined a Mekanism network");
        return com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks
                .networkExtension(pipe.getTransmitterNetwork(), MagicPipeAuraChannel.class);
    }

    private static NatureAuraMachineBlockEntity machine(GameTestHelper helper,
                                                        BlockPos pos) {
        return (NatureAuraMachineBlockEntity) helper.getBlockEntity(pos);
    }

    private static void setAuraMode(NatureAuraMachineBlockEntity machine,
                                    Direction side,
                                    NatureAuraMachineBlockEntity.AuraSideMode mode) {
        while (machine.getAuraSideMode(side) != mode) {
            machine.cycleAuraSideMode(side.ordinal());
        }
    }

    private static void mode(MagicSourceTransmitter pipe, Direction side,
                             ConnectionType mode) {
        pipe.setConnectionTypeRaw(side, mode);
        pipe.onModeChange(side);
    }

    private static void assertPools(GameTestHelper helper, BlockPos pos,
                                    long aura, int source) {
        helper.assertTrue(channel(helper, pos).stored() == aura,
                "Expected Aura " + aura + ", got "
                        + channel(helper, pos).stored());
        helper.assertTrue(pipe(helper, pos).getSource() == source,
                "Expected Source " + source + ", got "
                        + pipe(helper, pos).getSource());
    }
}
