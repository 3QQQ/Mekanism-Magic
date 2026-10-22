package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import com.example.mekanismmagic.integration.common.network.MagicPipeRegistries;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Both optional APIs are deliberately loaded only in the full runtime suite. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SharedMagicPipeArsGameTests {
    private static final BlockPos FIRST = new BlockPos(1, 2, 2);
    private static final BlockPos MIDDLE = new BlockPos(2, 2, 2);
    private static final BlockPos LAST = new BlockPos(3, 2, 2);

    private SharedMagicPipeArsGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void nativeSourceAndAuraCapsFollowTopologyAndSideChanges(
            GameTestHelper helper) {
        helper.setBlock(FIRST, MagicPipeRegistries.MAGIC_SOURCE_PIPE_BLOCK.get());
        helper.setBlock(LAST, MagicPipeRegistries.MAGIC_SOURCE_PIPE_BLOCK.get());
        ISourceCap[] retainedSource = new ISourceCap[1];
        IAuraContainer[] retainedAura = new IAuraContainer[1];
        int[] detached = new int[2];
        helper.startSequence().thenExecuteAfter(5, () -> {
            for (BlockPos pos : new BlockPos[]{FIRST, LAST}) {
                ISourceCap source = helper.getLevel().getCapability(
                        CapabilityRegistry.SOURCE_CAPABILITY,
                        helper.absolutePos(pos), Direction.UP);
                IAuraContainer aura = helper.getLevel().getCapability(
                        NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                        helper.absolutePos(pos), Direction.UP);
                helper.assertTrue(source != null && aura != null,
                        "Shared pipe did not expose both native APIs");
                helper.assertTrue(source.receiveSource(2_000, false) == 2_000
                                && aura.storeAura(20_000, false) == 20_000,
                        "One full native resource API blocked the other");
                if (pos.equals(FIRST)) {
                    retainedSource[0] = source;
                    retainedAura[0] = aura;
                }
            }
            helper.setBlock(MIDDLE,
                    MagicPipeRegistries.MAGIC_SOURCE_PIPE_BLOCK.get());
        }).thenExecuteAfter(6, () -> {
            ISourceCap source = retainedSource[0];
            IAuraContainer aura = retainedAura[0];
            helper.assertTrue(source.getSource() == 4_000
                            && aura.getStoredAura() == 40_000,
                    "Native cached views retained an adopted network pool");
            helper.assertTrue(source.receiveSource(500, false) == 500
                            && pipe(helper, LAST).getSource() == 4_500
                            && aura.getStoredAura() == 40_000,
                    "Native Source insert did not update the live isolated pool");
            source.extractSource(500, false);
            helper.assertTrue(aura.drainAura(5_000, false) == 5_000
                            && channel(helper, LAST).stored() == 35_000
                            && source.getSource() == 4_000,
                    "Native Aura extraction altered Source or a detached pool");
            aura.storeAura(5_000, false);
            MagicSourceTransmitter first = pipe(helper, FIRST);
            first.setConnectionTypeRaw(Direction.UP, ConnectionType.PULL);
            first.onModeChange(Direction.UP);
            helper.assertTrue(source.extractSource(1, false) == 0
                            && aura.drainAura(1, false) == 0,
                    "Native retained views bypassed PULL mode");
            first.setConnectionTypeRaw(Direction.UP, ConnectionType.PUSH);
            first.onModeChange(Direction.UP);
            helper.assertTrue(source.receiveSource(1, false) == 0
                            && aura.storeAura(1, false) == 0,
                    "Native retained views bypassed PUSH mode");
            first.setConnectionTypeRaw(Direction.UP, ConnectionType.NONE);
            first.onModeChange(Direction.UP);
            helper.assertTrue(source.receiveSource(1, false) == 0
                            && source.extractSource(1, false) == 0
                            && aura.storeAura(1, false) == 0
                            && aura.drainAura(1, false) == 0,
                    "Native retained views bypassed NONE mode");
            first.setConnectionTypeRaw(Direction.UP, ConnectionType.NORMAL);
            first.onModeChange(Direction.UP);
            first.validateAndTakeShare();
            detached[0] = first.getShare().getSource();
            detached[1] = com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks
                    .pipeExtension(first, MagicPipeAuraTransmitter.class)
                    .localStored();
            helper.setBlock(FIRST, Blocks.AIR);
            helper.assertTrue(source.receiveSource(1, false) == 0
                            && source.extractSource(1, false) == 0
                            && aura.storeAura(1, false) == 0
                            && aura.drainAura(1, false) == 0,
                    "Removed pipe native capabilities remained operational");
        }).thenExecuteAfter(6, () -> {
            helper.assertTrue(pipe(helper, LAST).getSource() + detached[0]
                            == 4_000
                            && channel(helper, LAST).stored() + detached[1]
                            == 40_000,
                    "Native API topology transition duplicated or lost contents");
        }).thenSucceed();
    }

    private static MagicSourceTransmitter pipe(GameTestHelper helper,
                                              BlockPos pos) {
        return ((MagicSourcePipeBlockEntity) helper.getBlockEntity(pos))
                .getTransmitter();
    }

    private static MagicPipeAuraChannel channel(GameTestHelper helper,
                                                BlockPos pos) {
        return com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks
                .networkExtension(pipe(helper, pos).getTransmitterNetwork(), MagicPipeAuraChannel.class);
    }
}
