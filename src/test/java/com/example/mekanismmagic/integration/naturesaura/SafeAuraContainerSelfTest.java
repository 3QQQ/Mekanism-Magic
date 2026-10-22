package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Offline assertions for Aura transfer, simulation and persistence safety. */
public final class SafeAuraContainerSelfTest {
    private SafeAuraContainerSelfTest() {
    }

    public static void main(String[] args) {
        FakeAuraType overworld = new FakeAuraType("overworld", 0x44AA66);
        FakeAuraType nether = new FakeAuraType("nether", 0xCC5533);
        AtomicReference<IAuraType> type = new AtomicReference<>(overworld);
        AtomicInteger changes = new AtomicInteger();
        SafeAuraContainer storage = new SafeAuraContainer(type::get,
                1_000, 250, 100, changes::incrementAndGet);

        require(storage.getStoredAura() == 0, "new storage is not empty");
        require(storage.getMaxAura() == 1_000, "capacity changed");
        require(storage.getMaxReceive() == 250, "receive rate changed");
        require(storage.getMaxExtract() == 100, "extract rate changed");
        require(storage.getAuraColor() == 0x44AA66, "wrong Aura color");
        require(storage.isAcceptableType(overworld), "same type rejected");
        require(!storage.isAcceptableType(nether), "wrong type accepted");
        require(!storage.isAcceptableType(null), "null type accepted");

        require(storage.storeAura(-1, false) == 0,
                "negative store changed Aura");
        require(storage.drainAura(-1, false) == 0,
                "negative drain changed Aura");
        require(storage.storeAura(0, false) == 0,
                "zero store changed Aura");
        require(storage.getStoredAura() == 0 && changes.get() == 0,
                "invalid operation notified listener");

        require(storage.storeAura(400, true) == 250,
                "store simulation ignored receive limit");
        require(storage.getStoredAura() == 0 && changes.get() == 0,
                "store simulation mutated state");
        require(storage.storeAura(400, false) == 250,
                "store execution differs from simulation");
        require(storage.getStoredAura() == 250 && changes.get() == 1,
                "store execution state/listener mismatch");

        require(storage.drainAura(500, true) == 100,
                "drain simulation ignored extract limit");
        require(storage.getStoredAura() == 250 && changes.get() == 1,
                "drain simulation mutated state");
        require(storage.drainAura(500, false) == 100,
                "drain execution differs from simulation");
        require(storage.getStoredAura() == 150 && changes.get() == 2,
                "drain execution state/listener mismatch");

        require(!storage.drainExact(151, true),
                "exact drain accepted insufficient Aura");
        require(storage.drainExact(150, true),
                "exact drain simulation rejected balance");
        require(storage.getStoredAura() == 150 && changes.get() == 2,
                "exact drain simulation mutated state");
        require(storage.drainExact(150, false),
                "exact drain execution failed");
        require(storage.getStoredAura() == 0 && changes.get() == 3,
                "exact drain execution state/listener mismatch");
        require(!storage.drainExact(-1, false),
                "negative exact drain succeeded");
        require(storage.drainExact(0, false),
                "zero exact drain failed");
        require(storage.getStoredAura() == 0 && changes.get() == 3,
                "invalid exact drain changed state");

        storage.setStoredAura(Integer.MAX_VALUE);
        require(storage.getStoredAura() == 1_000 && changes.get() == 4,
                "setStoredAura did not clamp");
        storage.setStoredAura(1_000);
        require(changes.get() == 4, "no-op setter notified listener");

        type.set(nether);
        require(storage.getAuraColor() == 0xCC5533,
                "dynamic type color did not update");
        require(storage.isAcceptableType(nether),
                "dynamic type was not accepted");
        type.set(null);
        require(storage.getAuraColor() == 0x42A6BC,
                "null type fallback color changed");
        require(!storage.isAcceptableType(nether),
                "null current type did not fail closed");

        CompoundTag saved = storage.serializeNBT(null);
        SafeAuraContainer loaded = new SafeAuraContainer(() -> overworld,
                1_000, 250, 100, () -> {
                    throw new AssertionError("NBT load notified listener");
                });
        loaded.deserializeNBT(null, saved);
        require(loaded.getStoredAura() == 1_000, "NBT roundtrip failed");

        CompoundTag malformed = new CompoundTag();
        malformed.putLong("stored_aura", Long.MIN_VALUE);
        loaded.deserializeNBT(null, malformed);
        require(loaded.getStoredAura() == 0, "negative NBT was not clamped");
        malformed.putLong("stored_aura", Long.MAX_VALUE);
        loaded.deserializeNBT(null, malformed);
        require(loaded.getStoredAura() == 1_000,
                "oversized NBT was not clamped before int conversion");
        loaded.deserializeNBT(null, new CompoundTag());
        require(loaded.getStoredAura() == 0, "missing NBT did not clear Aura");

        AtomicInteger syncChanges = new AtomicInteger();
        SafeAuraContainer synced = new SafeAuraContainer(() -> overworld,
                1_000, 250, 100, syncChanges::incrementAndGet);
        synced.setStoredAuraFromSync(-1);
        require(synced.getStoredAura() == 0 && syncChanges.get() == 0,
                "negative sync value or listener handling failed");
        synced.setStoredAuraFromSync(Integer.MAX_VALUE);
        require(synced.getStoredAura() == 1_000 && syncChanges.get() == 0,
                "oversized sync value or listener handling failed");

        AtomicInteger rollbackChanges = new AtomicInteger();
        SafeAuraContainer rollback = new SafeAuraContainer(() -> overworld,
                200, 1, 1, rollbackChanges::incrementAndGet);
        require(rollback.storeExact(150, true)
                        && rollback.getStoredAura() == 0
                        && rollbackChanges.get() == 0,
                "exact store simulation changed state");
        require(rollback.storeExact(150, false)
                        && rollback.getStoredAura() == 150
                        && rollbackChanges.get() == 1,
                "exact store execution failed or obeyed external rate");
        require(!rollback.storeExact(51, false)
                        && rollback.getStoredAura() == 150
                        && rollbackChanges.get() == 1,
                "over-capacity exact store was not atomic");

        expectFailure(() -> new SafeAuraContainer(() -> overworld,
                -1, 0, 0, () -> { }), IllegalArgumentException.class);
        expectFailure(() -> new SafeAuraContainer(null,
                1, 1, 1, () -> { }), NullPointerException.class);
        expectFailure(() -> new SafeAuraContainer(() -> overworld,
                1, 1, 1, null), NullPointerException.class);

        SafeAuraContainer huge = new SafeAuraContainer(() -> overworld,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
                () -> { });
        huge.setStoredAura(Integer.MAX_VALUE - 1);
        require(huge.storeAura(Integer.MAX_VALUE, false) == 1,
                "integer boundary remaining-space store failed");
        require(huge.drainAura(Integer.MAX_VALUE, false)
                        == Integer.MAX_VALUE,
                "integer boundary drain failed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectFailure(Runnable action,
                                      Class<? extends Throwable> type) {
        try {
            action.run();
            throw new AssertionError("Expected " + type.getSimpleName());
        } catch (Throwable failure) {
            if (!type.isInstance(failure)) {
                throw new AssertionError("Unexpected exception type",
                        failure);
            }
        }
    }

    private record FakeAuraType(
            ResourceLocation name, int color) implements IAuraType {
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
