package com.example.mekanismmagic.integration.forbiddenarcanus.pipe;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeUpgradeData;
import com.example.mekanismmagic.integration.common.network.MagicPipeRegistries;
import com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks;
import com.example.mekanismmagic.integration.common.resource.EssenceResources;
import com.example.mekanismmagic.integration.forbiddenarcanus.EssenceCondenserBlockEntity;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusForgeProcessorBlockEntity;
import mekanism.api.Action;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real shared topology with four typed essence balances, without PFAA or Nature. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagicPipeEssenceGameTests {
    private static final BlockPos FIRST = new BlockPos(1, 2, 2);
    private static final BlockPos MIDDLE = new BlockPos(2, 2, 2);
    private static final BlockPos LAST = new BlockPos(3, 2, 2);

    private MagicPipeEssenceGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 180)
    public static void orderedFiniteCondenserToForgeTransfersAndFullBankSwitch(GameTestHelper helper) {
        BlockPos donorPos = new BlockPos(1, 2, 3);
        BlockPos firstPipe = new BlockPos(2, 2, 3);
        BlockPos lastPipe = new BlockPos(3, 2, 3);
        BlockPos forgePos = new BlockPos(4, 2, 3);
        helper.setBlock(donorPos, ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get());
        helper.setBlock(forgePos, ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        place(helper, firstPipe);
        place(helper, lastPipe);
        mode(pipe(helper, firstPipe), Direction.WEST, ConnectionType.PULL);
        mode(pipe(helper, lastPipe), Direction.EAST, ConnectionType.PUSH);
        EssenceCondenserBlockEntity donor = helper.getBlockEntity(donorPos);
        HephaestusForgeProcessorBlockEntity forge = helper.getBlockEntity(forgePos);
        helper.startSequence().thenExecuteAfter(5, () -> {
            setBanks(helper, donor, 600, 5, 50, 15);
            helper.assertTrue(forge.getForgeTier() == 1 && forge.getStoredEssence(0) == 0,
                    "Fresh forge tier/bank fixture is not empty");
        }).thenExecuteAfter(2, () -> {
            int aureal = forge.getStoredEssence(0);
            helper.assertTrue(aureal > 0 && aureal <= 500
                            && donor.getStoredEssence(0) >= 100
                            && forge.getStoredEssence(1) == 0
                            && channel(pipe(helper, firstPipe)).currentChannel() == 0,
                    "Pipe exceeded basic 250/t or switched before Aureal was exhausted");
        }).thenExecuteAfter(12, () -> {
            helper.assertTrue(donor.getStoredEssence(0) == 0 && donor.getStoredEssence(1) == 0
                            && donor.getStoredEssence(2) == 0 && donor.getStoredEssence(3) == 0
                            && forge.getStoredEssence(0) == 600 && forge.getStoredEssence(1) == 5
                            && forge.getStoredEssence(2) == 50 && forge.getStoredEssence(3) == 15,
                    "Ordered four-type pipe transfer lost, mixed or duplicated essence");
            setBanks(helper, donor, 300, 4, 0, 0);
            helper.assertTrue(forge.insertEssence(0, 400, Action.EXECUTE) == 400,
                    "Could not fill the forge's remaining Aureal room");
        }).thenExecuteAfter(8, () -> {
            helper.assertTrue(forge.getStoredEssence(0) == forge.getEssenceCapacity(0)
                            && donor.getStoredEssence(0) == 300
                            && forge.getStoredEssence(1) == 9 && donor.getStoredEssence(1) == 0,
                    "Full Aureal bank did not switch to Souls without draining the blocked type");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void typedSharesSurviveMergeSplitSaveReloadAndTierUpgrade(GameTestHelper helper) {
        place(helper, FIRST);
        place(helper, LAST);
        CompoundTag[] saved = new CompoundTag[3];
        long[] detached = new long[EssenceResources.CHANNELS];
        helper.startSequence().thenExecuteAfter(5, () -> {
            seedShare(helper, FIRST, 300, 4, 40, 8);
            seedShare(helper, LAST, 200, 6, 60, 12);
            place(helper, MIDDLE);
        }).thenExecuteAfter(6, () -> {
            MagicPipeEssenceChannel merged = channel(pipe(helper, FIRST));
            helper.assertTrue(merged == channel(pipe(helper, LAST))
                            && merged.stored(0) == 500 && merged.stored(1) == 10
                            && merged.stored(2) == 100 && merged.stored(3) == 20,
                    "Essence merge did not preserve all four independent typed ledgers");
            MagicSourceTransmitter middle = pipe(helper, MIDDLE);
            middle.validateAndTakeShare();
            for (int channel = 0; channel < detached.length; channel++)
                detached[channel] = extension(middle).local(channel);
            middle.validateAndTakeShare();
            for (int channel = 0; channel < detached.length; channel++)
                helper.assertTrue(extension(middle).local(channel) == detached[channel],
                        "Repeated share claim duplicated channel " + channel);
            helper.setBlock(MIDDLE, Blocks.AIR);
        }).thenExecuteAfter(6, () -> {
            helper.assertTrue(channel(pipe(helper, FIRST)) != channel(pipe(helper, LAST)),
                    "Removing center pipe did not split the shared topology");
            long[] expected = {500, 10, 100, 20};
            for (int channel = 0; channel < expected.length; channel++) {
                helper.assertTrue(channel(pipe(helper, FIRST)).stored(channel)
                                + channel(pipe(helper, LAST)).stored(channel) + detached[channel]
                                == expected[channel],
                        "Split lost or duplicated essence channel " + channel);
            }
            place(helper, MIDDLE);
            pipe(helper, MIDDLE).read(helper.getLevel().registryAccess(), shareTag(detached));
        }).thenExecuteAfter(6, () -> {
            assertTotals(helper, FIRST, "rejoined split", 500, 10, 100, 20);
            BlockPos[] positions = {FIRST, MIDDLE, LAST};
            for (int i = 0; i < positions.length; i++) saved[i] = pipe(helper, positions[i])
                    .write(helper.getLevel().registryAccess(), new CompoundTag());
            for (BlockPos position : positions) helper.setBlock(position, Blocks.AIR);
        }).thenExecuteAfter(5, () -> {
            BlockPos[] positions = {FIRST, MIDDLE, LAST};
            for (int i = 0; i < positions.length; i++) {
                place(helper, positions[i]);
                pipe(helper, positions[i]).read(helper.getLevel().registryAccess(), saved[i]);
            }
        }).thenExecuteAfter(6, () -> {
            assertTotals(helper, FIRST, "save/reload", 500, 10, 100, 20);
            MagicSourceTransmitter middle = pipe(helper, MIDDLE);
            middle.startUpgrading();
            var upgrade = middle.getUpgradeData();
            helper.setBlock(MIDDLE, Blocks.AIR);
            helper.setBlock(MIDDLE, MagicPipeRegistries.ADVANCED_MAGIC_SOURCE_PIPE_BLOCK.get());
            pipe(helper, MIDDLE).parseUpgradeData(upgrade);
        }).thenExecuteAfter(6, () -> {
            assertTotals(helper, FIRST, "tier upgrade", 500, 10, 100, 20);
            helper.assertTrue(channel(pipe(helper, FIRST)).capacity(0) == 12_000,
                    "Advanced tier did not recompute per-channel finite capacity");
        }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 160)
    public static void departingPipeKeepsFrozenUpgradeShareThroughOldNetworkTicks(GameTestHelper helper) {
        place(helper, FIRST);
        MagicSourcePipeUpgradeData[] payload = new MagicSourcePipeUpgradeData[1];
        long[] expected = {500, 10, 100, 20};
        helper.startSequence().thenExecuteAfter(5, () -> {
            seedShare(helper, FIRST, expected);
        }).thenExecuteAfter(3, () -> {
            assertTotals(helper, FIRST, "before deterministic upgrade", expected);
            MagicSourceTransmitter departing = pipe(helper, FIRST);
            MagicPipeEssenceChannel oldNetwork = channel(departing);
            departing.startUpgrading();
            payload[0] = departing.getUpgradeData();
            CompoundTag frozen = payload[0].resources.copy();
            helper.assertTrue(frozen.equals(departing.getUpgradeData().resources),
                    "Repeated upgrade snapshot claimed another network share");
            // Force the adverse registry ordering: the old topology still
            // contains this segment after its payload has been captured.
            oldNetwork.tickServer();
            oldNetwork.absorbBuffer(departing);
            oldNetwork.updateSaveShares();
            departing.validateAndTakeShare();
            for (int index = 0; index < expected.length; index++) {
                helper.assertTrue(oldNetwork.stored(index) == 0
                                && extension(departing).local(index) == expected[index],
                        "Old topology reclaimed an already-owned upgrade share in channel " + index);
            }
            helper.assertTrue(frozen.equals(payload[0].resources)
                            && frozen.equals(departing.getUpgradeData().resources),
                    "Old topology tick changed the frozen upgrade payload");
            helper.setBlock(FIRST, Blocks.AIR);
            helper.setBlock(FIRST, MagicPipeRegistries.ADVANCED_MAGIC_SOURCE_PIPE_BLOCK.get());
            pipe(helper, FIRST).parseUpgradeData(payload[0]);
            pipe(helper, FIRST).parseUpgradeData(payload[0]);
        }).thenExecuteAfter(6, () -> {
            assertTotals(helper, FIRST, "deterministic upgrade", expected);
            // Reapplying the same payload after adoption must not recredit it.
            pipe(helper, FIRST).parseUpgradeData(payload[0]);
        }).thenExecuteAfter(3, () -> {
            assertTotals(helper, FIRST, "repeated upgrade parse", expected);
        }).thenSucceed();
    }

    private static void assertTotals(GameTestHelper helper, BlockPos pos, String stage, long... amounts) {
        MagicPipeEssenceChannel channel = channel(pipe(helper, pos));
        for (int index = 0; index < amounts.length; index++)
            helper.assertTrue(channel.stored(index) == amounts[index],
                    "Typed essence balance changed during " + stage + " in channel " + index
                            + ": expected=" + amounts[index] + ", actual=" + channel.stored(index));
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 160)
    public static void sameTickChunkReviveReturnsClaimedShareToItsExistingNetwork(GameTestHelper helper) {
        place(helper, FIRST);
        long[] expected = {500, 10, 100, 20};
        helper.startSequence().thenExecuteAfter(5, () -> {
            seedShare(helper, FIRST, expected);
        }).thenExecuteAfter(3, () -> {
            assertTotals(helper, FIRST, "before same-tick revive", expected);
            var tile = (com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeBlockEntity)
                    helper.getBlockEntity(FIRST);
            MagicSourceTransmitter pipe = tile.getTransmitter();
            var previousNetwork = pipe.getTransmitterNetwork();
            MagicPipeEssenceChannel contents = channel(pipe);
            tile.chunkAccessibilityChange(false);
            helper.assertTrue(!pipe.isValid(), "Inaccessible pipe remained loaded");
            tile.chunkAccessibilityChange(true);
            helper.assertTrue(pipe.isValid() && pipe.getTransmitterNetwork() == previousNetwork,
                    "Same-tick revive did not exercise Mekanism's retained network path");
            contents.tickServer();
            for (int index = 0; index < expected.length; index++) {
                helper.assertTrue(contents.stored(index) == 0
                                && extension(pipe).local(index) == expected[index],
                        "Network scan released a claim before its physical pipe resumed ticking");
            }
            // Dispatch the actual physical block entity tick, before any
            // registry pass can substitute a freshly constructed network.
            mekanism.common.tile.transmitter.TileEntityTransmitter.tickServer(
                    helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
            contents.tickServer();
            assertTotals(helper, FIRST, "same-network revive", expected);
            for (int index = 0; index < expected.length; index++) {
                helper.assertTrue(extension(pipe).local(index) == 0,
                        "Live revived pipe left its claimed share outside the network");
            }
        }).thenExecuteAfter(3, () -> {
            assertTotals(helper, FIRST, "after same-tick revive", expected);
        }).thenSucceed();
    }
    private static void setBanks(GameTestHelper helper, EssenceCondenserBlockEntity tile, int... values) {
        CompoundTag saved = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.getCompound(EssenceCondenserBlockEntity.STATE_TAG).putIntArray("banks", values.clone());
        tile.loadWithComponents(saved, helper.getLevel().registryAccess());
    }
    private static void seedShare(GameTestHelper helper, BlockPos pos, long... values) {
        pipe(helper, pos).read(helper.getLevel().registryAccess(), shareTag(values));
    }
    private static CompoundTag shareTag(long... values) {
        CompoundTag tag = new CompoundTag();
        CompoundTag shares = new CompoundTag();
        for (int channel = 0; channel < values.length; channel++) {
            if (values[channel] > 0) shares.putLong(EssenceResources.IDS.get(channel).toString(), values[channel]);
        }
        tag.put("magic_pipe_essence_shares", shares);
        return tag;
    }
    private static void place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, MagicPipeRegistries.MAGIC_SOURCE_PIPE_BLOCK.get());
    }
    private static MagicSourceTransmitter pipe(GameTestHelper helper, BlockPos pos) {
        return ((com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeBlockEntity)
                helper.getBlockEntity(pos)).getTransmitter();
    }
    private static MagicPipeEssenceTransmitter extension(MagicSourceTransmitter pipe) {
        return MagicPipeResourceHooks.pipeExtension(pipe, MagicPipeEssenceTransmitter.class);
    }
    private static MagicPipeEssenceChannel channel(MagicSourceTransmitter pipe) {
        return MagicPipeResourceHooks.networkExtension(pipe.getTransmitterNetwork(), MagicPipeEssenceChannel.class);
    }
    private static void mode(MagicSourceTransmitter pipe, Direction side, ConnectionType type) {
        pipe.setConnectionTypeRaw(side, type);
        pipe.onModeChange(side);
    }
}
