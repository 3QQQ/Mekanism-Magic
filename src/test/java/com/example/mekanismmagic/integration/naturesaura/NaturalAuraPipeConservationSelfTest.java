package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeTier;

import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline conservation, split/rebuild and Aura-type boundary assertions. */
public final class NaturalAuraPipeConservationSelfTest {
    private NaturalAuraPipeConservationSelfTest() {
    }

    public static void main(String[] args) {
        FakeAuraType overworld = new FakeAuraType("overworld", 0x55BB77);
        FakeAuraType nether = new FakeAuraType("nether", 0xCC5533);
        AtomicReference<IAuraType> type =
                new AtomicReference<>(overworld);
        AtomicInteger capacity = new AtomicInteger(400_000);
        AtomicInteger changes = new AtomicInteger();
        NaturalAuraBuffer network = new NaturalAuraBuffer(type::get,
                capacity::get, changes::incrementAndGet);

        require(network.storeAura(275_000, true) == 275_000,
                "store simulation rejected available capacity");
        require(network.getStoredAura() == 0 && changes.get() == 0,
                "store simulation mutated the network");
        require(network.storeAura(275_000, false) == 275_000,
                "store execution diverged from simulation");
        require(network.getStoredAura() == 275_000,
                "stored Aura changed after insertion");

        int[] capacities = {
                MagicPipeAuraTransmitter.tierCapacity(MagicSourcePipeTier.BASIC),
                MagicPipeAuraTransmitter.tierCapacity(MagicSourcePipeTier.ADVANCED),
                MagicPipeAuraTransmitter.tierCapacity(MagicSourcePipeTier.ELITE)
        };
        int remaining = network.getStoredAura();
        int savedTotal = 0;
        NaturalAuraBuffer[] split = new NaturalAuraBuffer[capacities.length];
        for (int i = 0; i < capacities.length; i++) {
            int segmentCapacity = capacities[i];
            int share = NaturalAuraPipeSemantics.saveShare(
                    remaining, segmentCapacity);
            split[i] = new NaturalAuraBuffer(type::get,
                    () -> segmentCapacity, () -> { });
            require(split[i].storeAura(share, false) == share,
                    "split share exceeded its segment");
            savedTotal += share;
            remaining -= share;
        }
        require(remaining == 0 && savedTotal == 275_000,
                "disconnect save shares did not conserve Aura");

        NaturalAuraBuffer rebuilt = new NaturalAuraBuffer(type::get,
                () -> 420_000, () -> { });
        for (NaturalAuraBuffer share : split) {
            int amount = share.drainAura(Integer.MAX_VALUE, false);
            require(rebuilt.storeAura(amount, false) == amount,
                    "network rebuild lost a saved share");
        }
        require(rebuilt.getStoredAura() == 275_000,
                "split/rebuild total changed");

        require(rebuilt.isAcceptableType(overworld),
                "matching Aura type was rejected");
        require(!rebuilt.isAcceptableType(nether),
                "cross-dimension Aura type was accepted");
        require(NaturalAuraPipeSemantics.compatible(overworld, overworld),
                "same type failed compatibility check");
        require(!NaturalAuraPipeSemantics.compatible(overworld, nether),
                "different types passed compatibility check");
        require(!NaturalAuraPipeSemantics.compatible(overworld, null),
                "missing persisted type passed compatibility check");
        require(NaturalAuraPipeSemantics.parseOptionalId("") == null
                        && NaturalAuraPipeSemantics.parseOptionalId("   ")
                        == null
                        && NaturalAuraPipeSemantics.parseOptionalId(
                        "minecraft:") == null,
                "blank Aura type tag was converted into a registry id");
        require(ResourceLocation.fromNamespaceAndPath("naturesaura",
                        "overworld").equals(
                        NaturalAuraPipeSemantics.parseOptionalId(
                                "naturesaura:overworld")),
                "valid persisted Aura type was not restored");
        require(NaturalAuraPipeSemantics.mergeRepresentable(
                        Integer.MAX_VALUE - 1, 1),
                "representable integer-boundary merge was rejected");
        require(!NaturalAuraPipeSemantics.mergeRepresentable(
                        Integer.MAX_VALUE, 1),
                "overflowing network merge was allowed");

        capacity.set(100_000);
        require(network.storeAura(1, false) == 0,
                "shrunk over-capacity buffer returned a negative/positive store");
        network.setAura(network.getStoredAura());
        require(network.getStoredAura() == 100_000,
                "capacity shrink did not clamp the buffer");
        require(network.drainAura(-1, false) == 0
                        && network.storeAura(-1, false) == 0,
                "negative request changed Aura");

        require(MagicPipeAuraTransmitter.transferRate(MagicSourcePipeTier.BASIC) == 5_000
                        && MagicPipeAuraTransmitter.transferRate(MagicSourcePipeTier.ADVANCED) == 20_000
                        && MagicPipeAuraTransmitter.transferRate(MagicSourcePipeTier.ELITE) == 80_000
                        && MagicPipeAuraTransmitter.transferRate(MagicSourcePipeTier.ULTIMATE) == 320_000,
                "tier pull curve changed");
        require(MagicPipeAuraTransmitter.tierCapacity(MagicSourcePipeTier.BASIC) == 20_000
                        && MagicPipeAuraTransmitter.tierCapacity(MagicSourcePipeTier.ULTIMATE)
                        == 1_280_000,
                "tier capacity curve changed");

        testCumulativeMergeGuard(overworld, nether);
        testDynamicCapabilityView(overworld);
        require(!NaturalAuraPipeSemantics.preserveLocalShare(0)
                        && NaturalAuraPipeSemantics
                        .preserveLocalShare(1),
                "non-empty local share did not take persistence priority");
    }

    private static void testCumulativeMergeGuard(
            IAuraType overworld, IAuraType nether) {
        NaturalAuraMergeGuard guard = new NaturalAuraMergeGuard();
        require(guard.accept(overworld, 1_500_000_000),
                "first large network was rejected");
        require(!guard.accept(nether, 1),
                "cross-type orphan network was accepted");
        require(guard.stored() == 1_500_000_000L,
                "rejected network mutated cumulative accounting");
        int exactRemainder = Integer.MAX_VALUE - 1_500_000_000;
        require(guard.accept(overworld, exactRemainder),
                "exact integer-boundary merge was rejected");
        require(guard.stored() == Integer.MAX_VALUE,
                "cumulative merge total changed");
        require(!guard.accept(overworld, 1),
                "multi-network integer overflow was accepted");
        require(guard.stored() == Integer.MAX_VALUE,
                "overflow rejection mutated cumulative accounting");

        NaturalAuraMergeGuard dimensionGuard =
                new NaturalAuraMergeGuard(nether);
        require(!dimensionGuard.accept(overworld, 10_000)
                        && dimensionGuard.stored() == 0,
                "network Aura type ignored the orphan dimension type");
        require(dimensionGuard.accept(nether, 10_000),
                "matching orphan dimension type was rejected");
    }

    private static void testDynamicCapabilityView(IAuraType type) {
        NaturalAuraBuffer isolated = new NaturalAuraBuffer(() -> type,
                () -> 100, () -> { });
        NaturalAuraBuffer network = new NaturalAuraBuffer(() -> type,
                () -> 200, () -> { });
        AtomicReference<NaturalAuraBuffer> selected =
                new AtomicReference<>(isolated);
        AtomicBoolean compatible = new AtomicBoolean(true);
        DynamicNaturalAuraView cached = new DynamicNaturalAuraView(
                selected::get, true, true, compatible::get);

        require(cached.storeAura(75, false) == 75
                        && isolated.getStoredAura() == 75
                        && network.getStoredAura() == 0,
                "cached view did not write the isolated share");
        selected.set(network);
        require(cached.getMaxAura() == 200,
                "cached view retained the old network capacity");
        require(cached.storeAura(150, false) == 150
                        && network.getStoredAura() == 150
                        && isolated.getStoredAura() == 75,
                "cached view wrote into a detached network");
        require(cached.drainAura(40, false) == 40
                        && network.getStoredAura() == 110,
                "cached view did not drain the current network");
        compatible.set(false);
        require(cached.storeAura(1, false) == 0
                        && cached.drainAura(1, false) == 0
                        && !cached.isAcceptableType(type)
                        && network.getStoredAura() == 110,
                "fail-closed view modified an incompatible share");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record FakeAuraType(ResourceLocation name, int color)
            implements IAuraType {
        private FakeAuraType(String path, int color) {
            this(ResourceLocation.fromNamespaceAndPath("test", path), color);
        }

        @Override
        public ResourceLocation getName() {
            return name;
        }

        @Override
        public boolean isPresentInLevel(Level level) {
            return false;
        }

        @Override
        public int getColor() {
            return color;
        }

        @Override
        public int getPriority() {
            return 0;
        }

        @Override
        public boolean isSimilar(IAuraType other) {
            return other != null && name.equals(other.getName());
        }
    }
}
