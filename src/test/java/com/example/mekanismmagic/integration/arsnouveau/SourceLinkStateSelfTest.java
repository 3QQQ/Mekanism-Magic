package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Offline assertions for Source-link persistence and dimension rebasing. */
public final class SourceLinkStateSelfTest {
    private SourceLinkStateSelfTest() {
    }

    public static void main(String[] args) {
        ResourceLocation overworld = ResourceLocation.withDefaultNamespace(
                "overworld");
        ResourceLocation nether = ResourceLocation.withDefaultNamespace(
                "the_nether");
        BlockPos host = new BlockPos(1, 64, 1);
        BlockPos firstJar = new BlockPos(5, 64, 5);
        BlockPos secondJar = new BlockPos(-7, 70, 9);

        SourceLinkState original = new SourceLinkState();
        check(!original.link(overworld, host, host),
                "A machine must not link to itself");
        check(original.link(overworld, host, firstJar),
                "First Source Jar link was rejected");
        check(original.link(overworld, host, secondJar),
                "Second Source Jar link was rejected");
        check(original.link(overworld, host, firstJar),
                "A duplicate link should be idempotent");
        check(original.snapshot().size() == 2,
                "Duplicate link created duplicate state");

        CompoundTag saved = new CompoundTag();
        original.save(saved);
        SourceLinkState restored = new SourceLinkState();
        restored.load(saved);
        check(restored.snapshot().equals(original.snapshot()),
                "Source Jar links did not survive NBT round-trip");

        BlockPos netherJar = new BlockPos(12, 80, -3);
        check(restored.link(nether, host, netherJar),
                "Explicit link did not rebase a moved machine");
        check(restored.snapshot().size() == 1
                        && restored.snapshot().getFirst().equals(netherJar),
                "Cross-dimension rebase retained stale coordinates");
        check(restored.clear() == 1 && restored.snapshot().isEmpty(),
                "Clearing links did not report/remove saved links");

        check(ArsNouveauMachineConfig.apparatusSourceCost(0) == 100,
                "Zero-cost apparatus recipe bypassed machine Source");
        check(ArsNouveauMachineConfig.apparatusSourceCost(500) == 500,
                "Declared apparatus Source cost was not preserved");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
