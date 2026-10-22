package com.example.mekanismmagic.integration.common.network;

import java.util.Objects;
import java.util.function.Function;
import mekanism.common.lib.transmitter.acceptor.AbstractAcceptorCache;
import mekanism.common.lib.transmitter.acceptor.AcceptorInfo;
import mekanism.common.tile.transmitter.TileEntityTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/** Keeps optional Source capability classes out of the shared pipe core. */
public final class SourceAcceptorHooks {
    private static Function<TileEntityTransmitter,
            AbstractAcceptorCache<PipeSourceStorage, ?>> factory = EmptyCache::new;

    private SourceAcceptorHooks() {}

    public static void register(Function<TileEntityTransmitter,
            AbstractAcceptorCache<PipeSourceStorage, ?>> cacheFactory) {
        factory = Objects.requireNonNull(cacheFactory);
    }

    public static AbstractAcceptorCache<PipeSourceStorage, ?> create(
            TileEntityTransmitter tile) {
        return factory.apply(tile);
    }

    private static final class EmptyCache extends AbstractAcceptorCache<
            PipeSourceStorage, AcceptorInfo<PipeSourceStorage>> {
        private EmptyCache(TileEntityTransmitter tile) { super(tile); }

        @Override
        protected AcceptorInfo<PipeSourceStorage> initializeCache(
                ServerLevel level, BlockPos pos, Direction side,
                RefreshListener listener) {
            return () -> null;
        }
    }
}
