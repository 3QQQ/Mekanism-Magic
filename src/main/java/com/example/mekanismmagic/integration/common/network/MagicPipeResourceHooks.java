package com.example.mekanismmagic.integration.common.network;

import com.example.mekanismmagic.integration.arsnouveau.MagicSourceNetwork;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeTier;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Optional, independently stored resources sharing the magic pipe topology. */
public final class MagicPipeResourceHooks {
    private static Function<MagicSourceTransmitter, PipeExtension> pipes =
            ignored -> new PipeExtension() {};
    private static Function<MagicSourceNetwork, NetworkExtension> networks =
            ignored -> new NetworkExtension() {};
    private static BiConsumer<MagicSourcePipeTier, List<Component>> tooltips =
            (tier, tooltip) -> {};

    private MagicPipeResourceHooks() {}

    /** Install during mod setup, before any pipe or network is constructed. */
    public static void register(
            Function<MagicSourceTransmitter, PipeExtension> pipeFactory,
            Function<MagicSourceNetwork, NetworkExtension> networkFactory) {
        pipes = Objects.requireNonNull(pipeFactory);
        networks = Objects.requireNonNull(networkFactory);
    }

    public static PipeExtension createPipe(MagicSourceTransmitter pipe) {
        return Objects.requireNonNull(pipes.apply(pipe));
    }

    public static NetworkExtension createNetwork(MagicSourceNetwork network) {
        return Objects.requireNonNull(networks.apply(network));
    }

    public static void registerTooltip(
            BiConsumer<MagicSourcePipeTier, List<Component>> tooltip) {
        tooltips = Objects.requireNonNull(tooltip);
    }

    public static void appendTooltip(MagicSourcePipeTier tier, List<Component> tooltip) {
        tooltips.accept(tier, tooltip);
    }

    public interface PipeExtension {
        default void tickServer() {}
        default void read(HolderLookup.Provider provider, CompoundTag tag) {}
        /** Called after the network has validated save shares. */
        default void write(HolderLookup.Provider provider, CompoundTag tag) {}
        default void writeUpdateTag(CompoundTag tag) {}
        default void readUpdateTag(CompoundTag tag) {}
        /** Client render state, independent of the Source network scale. */
        default float renderScale() { return 0; }
        default int renderColor() { return 0xFFFFFF; }
        default CompoundTag writeUpgradeData() { return new CompoundTag(); }
        default void parseUpgradeData(CompoundTag tag) {}
        /** Called while the old network is still attached, after share validation. */
        default void takeShare() {}
        /** Includes the connected network's contents when the local buffer is empty. */
        default boolean isEmpty() { return true; }
        default boolean isEndpoint(Level level, BlockPos pos, Direction side) {
            return false;
        }
        default void sideChanged(Direction side) {}
        default void redstoneChanged(boolean powered) {}
        /** A fresh, stateful guard for each orphan path-finding pass. */
        default ResourceValidator createValidator() {
            return new ResourceValidator() {};
        }
    }

    public interface ResourceValidator {
        default boolean canAcceptPipe(MagicSourceTransmitter pipe) { return true; }
        default boolean canAcceptNetwork(MagicSourceNetwork network) { return true; }
    }

    public interface NetworkExtension {
        /** Commit already added the pipe and updated capacity; consume its local share. */
        default void absorbBuffer(MagicSourceTransmitter pipe) {}
        /** Called after topology adoption and capacity update; move and clear old contents. */
        default void adoptFrom(MagicSourceNetwork other) {}
        default boolean isCompatibleWith(MagicSourceNetwork other) { return true; }
        default void updateCapacity() {}
        default void clampBuffer() {}
        /** Allocate independent save shares across the live transmitter set. */
        default void updateSaveShares() {}
        /** Runs once per network update, including when Source is empty. */
        default void tickServer() {}
    }
}
