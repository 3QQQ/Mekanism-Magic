package com.example.mekanismmagic.integration.common.network;

import com.example.mekanismmagic.integration.arsnouveau.MagicSourceNetwork;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeTier;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private static final Map<String, Registration> REGISTRATIONS = new LinkedHashMap<>();
    private static final List<BiConsumer<MagicSourcePipeTier, List<Component>>> TOOLTIPS = new ArrayList<>();

    private MagicPipeResourceHooks() {}

    /** Install during mod setup, before any pipe or network is constructed. */
    public static synchronized void register(String id,
            Function<MagicSourceTransmitter, PipeExtension> pipeFactory,
            Function<MagicSourceNetwork, NetworkExtension> networkFactory) {
        if (REGISTRATIONS.putIfAbsent(Objects.requireNonNull(id), new Registration(
                Objects.requireNonNull(pipeFactory), Objects.requireNonNull(networkFactory))) != null) {
            throw new IllegalStateException("Duplicate magic pipe resource extension: " + id);
        }
    }

    /** Existing source integrations compile against this entry, but no longer replace one another. */
    public static void register(Function<MagicSourceTransmitter, PipeExtension> pipeFactory,
                                Function<MagicSourceNetwork, NetworkExtension> networkFactory) {
        register("legacy", pipeFactory, networkFactory);
    }

    public static synchronized PipeExtension createPipe(MagicSourceTransmitter pipe) {
        List<PipeExtension> parts = REGISTRATIONS.values().stream()
                .map(registration -> Objects.requireNonNull(registration.pipeFactory().apply(pipe))).toList();
        return parts.isEmpty() ? new PipeExtension() {} : parts.size() == 1
                ? parts.getFirst() : new CompositePipe(parts);
    }

    public static synchronized NetworkExtension createNetwork(MagicSourceNetwork network) {
        List<NetworkExtension> parts = REGISTRATIONS.values().stream()
                .map(registration -> Objects.requireNonNull(registration.networkFactory().apply(network))).toList();
        return parts.isEmpty() ? new NetworkExtension() {} : parts.size() == 1
                ? parts.getFirst() : new CompositeNetwork(parts);
    }

    public static <T extends PipeExtension> T pipeExtension(MagicSourceTransmitter pipe, Class<T> type) {
        PipeExtension extension = pipe.extension();
        if (type.isInstance(extension)) return type.cast(extension);
        return extension instanceof CompositePipe composite ? composite.find(type) : null;
    }

    public static <T extends NetworkExtension> T networkExtension(MagicSourceNetwork network, Class<T> type) {
        NetworkExtension extension = network.extension();
        if (type.isInstance(extension)) return type.cast(extension);
        return extension instanceof CompositeNetwork composite ? composite.find(type) : null;
    }

    public static synchronized void registerTooltip(
            BiConsumer<MagicSourcePipeTier, List<Component>> tooltip) {
        TOOLTIPS.add(Objects.requireNonNull(tooltip));
    }

    public static void appendTooltip(MagicSourcePipeTier tier, List<Component> tooltip) {
        List<BiConsumer<MagicSourcePipeTier, List<Component>>> registered;
        synchronized (MagicPipeResourceHooks.class) { registered = List.copyOf(TOOLTIPS); }
        registered.forEach(entry -> entry.accept(tier, tooltip));
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

    private record Registration(Function<MagicSourceTransmitter, PipeExtension> pipeFactory,
                                Function<MagicSourceNetwork, NetworkExtension> networkFactory) { }

    private record CompositePipe(List<PipeExtension> members) implements PipeExtension {
        private <T extends PipeExtension> T find(Class<T> type) {
            for (PipeExtension member : members) if (type.isInstance(member)) return type.cast(member);
            return null;
        }
        @Override public void tickServer() { members.forEach(PipeExtension::tickServer); }
        @Override public void read(HolderLookup.Provider provider, CompoundTag tag) {
            members.forEach(part -> part.read(provider, tag));
        }
        @Override public void write(HolderLookup.Provider provider, CompoundTag tag) {
            members.forEach(part -> part.write(provider, tag));
        }
        @Override public void writeUpdateTag(CompoundTag tag) {
            members.forEach(part -> part.writeUpdateTag(tag));
        }
        @Override public void readUpdateTag(CompoundTag tag) {
            members.forEach(part -> part.readUpdateTag(tag));
        }
        @Override public float renderScale() {
            return members.stream().map(PipeExtension::renderScale).max(Float::compare).orElse(0F);
        }
        @Override public int renderColor() {
            PipeExtension selected = null;
            for (PipeExtension member : members) {
                if (selected == null || member.renderScale() > selected.renderScale()) selected = member;
            }
            return selected == null ? 0xFFFFFF : selected.renderColor();
        }
        @Override public CompoundTag writeUpgradeData() {
            CompoundTag merged = new CompoundTag();
            for (PipeExtension member : members) {
                CompoundTag data = member.writeUpgradeData();
                for (String key : data.getAllKeys()) {
                    if (merged.contains(key)) throw new IllegalStateException("Duplicate magic pipe upgrade key " + key);
                    merged.put(key, data.get(key).copy());
                }
            }
            return merged;
        }
        @Override public void parseUpgradeData(CompoundTag tag) {
            members.forEach(part -> part.parseUpgradeData(tag));
        }
        @Override public void takeShare() { members.forEach(PipeExtension::takeShare); }
        @Override public boolean isEmpty() { return members.stream().allMatch(PipeExtension::isEmpty); }
        @Override public boolean isEndpoint(Level level, BlockPos pos, Direction side) {
            return members.stream().anyMatch(part -> part.isEndpoint(level, pos, side));
        }
        @Override public void sideChanged(Direction side) { members.forEach(part -> part.sideChanged(side)); }
        @Override public void redstoneChanged(boolean powered) {
            members.forEach(part -> part.redstoneChanged(powered));
        }
        @Override public ResourceValidator createValidator() {
            // Normal adoption is linear. A rejected candidate rebuilds and
            // replays only then, so earlier stateful channel guards cannot
            // retain a partial acceptance from a later channel's rejection.
            return new ResourceValidator() {
                private final List<Object> accepted = new ArrayList<>();
                private List<ResourceValidator> guards = newGuards();
                private List<ResourceValidator> newGuards() {
                    return members.stream().map(PipeExtension::createValidator).toList();
                }
                private boolean canAccept(ResourceValidator guard, Object candidate) {
                    return candidate instanceof MagicSourceTransmitter pipe
                            ? guard.canAcceptPipe(pipe)
                            : guard.canAcceptNetwork((MagicSourceNetwork) candidate);
                }
                private boolean accept(Object candidate) {
                    for (ResourceValidator guard : guards) {
                        if (!canAccept(guard, candidate)) {
                            guards = newGuards();
                            for (Object previous : accepted) {
                                for (ResourceValidator replay : guards) {
                                    if (!canAccept(replay, previous)) {
                                        throw new IllegalStateException("Accepted magic topology changed during validation");
                                    }
                                }
                            }
                            return false;
                        }
                    }
                    accepted.add(candidate);
                    return true;
                }
                @Override public boolean canAcceptPipe(MagicSourceTransmitter pipe) { return accept(pipe); }
                @Override public boolean canAcceptNetwork(MagicSourceNetwork network) { return accept(network); }
            };
        }
    }

    private record CompositeNetwork(List<NetworkExtension> members) implements NetworkExtension {
        private <T extends NetworkExtension> T find(Class<T> type) {
            for (NetworkExtension member : members) if (type.isInstance(member)) return type.cast(member);
            return null;
        }
        @Override public void absorbBuffer(MagicSourceTransmitter pipe) {
            members.forEach(part -> part.absorbBuffer(pipe));
        }
        @Override public void adoptFrom(MagicSourceNetwork other) {
            if (!isCompatibleWith(other)) throw new IllegalArgumentException("Incompatible magic resources");
            members.forEach(part -> part.adoptFrom(other));
        }
        @Override public boolean isCompatibleWith(MagicSourceNetwork other) {
            return members.stream().allMatch(part -> part.isCompatibleWith(other));
        }
        @Override public void updateCapacity() { members.forEach(NetworkExtension::updateCapacity); }
        @Override public void clampBuffer() { members.forEach(NetworkExtension::clampBuffer); }
        @Override public void updateSaveShares() { members.forEach(NetworkExtension::updateSaveShares); }
        @Override public void tickServer() { members.forEach(NetworkExtension::tickServer); }
    }
}
