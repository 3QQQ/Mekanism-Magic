package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.network.PipeSourceStorage;
import com.example.mekanismmagic.integration.common.network.SourceAcceptorHooks;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import mekanism.common.lib.transmitter.acceptor.AbstractAcceptorCache;
import mekanism.common.lib.transmitter.acceptor.AcceptorInfo;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

/** Loaded only when Ars is present; adapts its API to the shared pipe core. */
public final class ArsSourcePipeAdapter {
    private ArsSourcePipeAdapter() {}

    public static void install() {
        SourceAcceptorHooks.register(SourceCache::new);
    }

    public static MagicSourcePipeBlockEntity createPipe(
            Holder<Block> block, BlockPos pos, BlockState state) {
        return new ArsMagicSourcePipeBlockEntity(block, pos, state);
    }

    public static @Nullable ISourceCap wrap(@Nullable PipeSourceStorage source) {
        return source == null ? null : new ExternalView(source);
    }

    private record ExternalView(PipeSourceStorage source) implements ISourceCap {
        @Override public boolean canAcceptSource(int amount) { return source.canAcceptSource(amount); }
        @Override public boolean canProvideSource(int amount) { return source.canProvideSource(amount); }
        @Override public boolean canExtract() { return source.canExtract(); }
        @Override public boolean canReceive() { return source.canReceive(); }
        @Override public int getMaxExtract() { return source.getMaxExtract(); }
        @Override public int getMaxReceive() { return source.getMaxReceive(); }
        @Override public int getSource() { return source.getSource(); }
        @Override public int getSourceCapacity() { return source.getSourceCapacity(); }
        @Override public void setSource(int amount) { source.setSource(amount); }
        @Override public void setMaxSource(int amount) { source.setMaxSource(amount); }
        @Override public int receiveSource(int amount, boolean simulate) { return source.receiveSource(amount, simulate); }
        @Override public int extractSource(int amount, boolean simulate) { return source.extractSource(amount, simulate); }
    }

    private record InternalView(ISourceCap source) implements PipeSourceStorage {
        @Override public boolean canAcceptSource(int amount) { return source.canAcceptSource(amount); }
        @Override public boolean canProvideSource(int amount) { return source.canProvideSource(amount); }
        @Override public boolean canExtract() { return source.canExtract(); }
        @Override public boolean canReceive() { return source.canReceive(); }
        @Override public int getMaxExtract() { return source.getMaxExtract(); }
        @Override public int getMaxReceive() { return source.getMaxReceive(); }
        @Override public int getSource() { return source.getSource(); }
        @Override public int getSourceCapacity() { return source.getSourceCapacity(); }
        @Override public void setSource(int amount) { source.setSource(amount); }
        @Override public void setMaxSource(int amount) { source.setMaxSource(amount); }
        @Override public int receiveSource(int amount, boolean simulate) { return source.receiveSource(amount, simulate); }
        @Override public int extractSource(int amount, boolean simulate) { return source.extractSource(amount, simulate); }
    }

    private static final class SourceCache extends AbstractAcceptorCache<
            PipeSourceStorage, AcceptorInfo<PipeSourceStorage>> {
        private SourceCache(TileEntityTransmitter tile) { super(tile); }

        @Override
        protected AcceptorInfo<PipeSourceStorage> initializeCache(
                ServerLevel level, BlockPos pos, Direction side,
                RefreshListener listener) {
            var cache = BlockCapabilityCache.create(
                    CapabilityRegistry.SOURCE_CAPABILITY,
                    level, pos, side, listener, listener);
            return () -> {
                ISourceCap source = cache.getCapability();
                return source == null ? null : new InternalView(source);
            };
        }
    }
}
