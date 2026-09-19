package com.example.mekanismmagic.upgrade;

import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.util.StringRepresentable;

/** Reproduces the 16 -> 17 upgrade startup failure without loading optional addons. */
public final class UpgradeSerializationSelfTest {
    private enum Fixture implements StringRepresentable {
        SPEED("speed"), ENERGY("energy"), FILTER("filter"), CHEMICAL("chemical"),
        MUFFLING("muffling"), ANCHOR("anchor"), STONE_GENERATOR("stone_generator"),
        STACK("stack"), IONIC_MEMBRANE("ionic_membrane"), CREATIVE("creative"),
        THREAD("thread"), CAPACITY_FIRST("capacity"), VOID("void"),
        CAPACITY_SECOND("capacity"), EXTRA_A("extra_a"), EXTRA_B("extra_b"),
        CREATIVE_MAGIC("creative_magic");

        private final String serializedName;

        Fixture(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    public static void main(String[] args) {
        Fixture[] all = Fixture.values();
        Fixture[] beforeMagic = Arrays.copyOf(all, 16);
        check(StringRepresentable.createNameLookup(beforeMagic, Function.identity())
                .apply("capacity") == Fixture.CAPACITY_FIRST, "baseline first-match lookup");
        boolean reproduced = false;
        try {
            StringRepresentable.createNameLookup(all, Function.identity());
        } catch (IllegalStateException expected) {
            reproduced = expected.getMessage().contains("Duplicate key capacity");
        }
        check(reproduced, "must reproduce the reported startup exception with 17 values");

        verify(beforeMagic);
        verify(all);
        Codec<Fixture> codec = UpgradeSerialization.createCodec(all, Map.of("gas", Fixture.CHEMICAL));
        check(codec.parse(JsonOps.INSTANCE, new JsonPrimitive("gas")).getOrThrow()
                == Fixture.CHEMICAL, "legacy gas save alias");
        check(codec.encodeStart(JsonOps.INSTANCE, Fixture.CHEMICAL).getOrThrow()
                .equals(new JsonPrimitive("chemical")), "canonical chemical save name");
        check(codec.parse(JsonOps.INSTANCE, new JsonPrimitive("missing")).error().isPresent(),
                "unknown names must remain errors");
        check(codec.parse(JsonOps.COMPRESSED, new JsonPrimitive(-1)).error().isPresent(),
                "negative IDs must remain errors");
        check(codec.parse(JsonOps.COMPRESSED, new JsonPrimitive(all.length)).error().isPresent(),
                "out-of-range IDs must remain errors");
        check(Arrays.equals(all, Fixture.values()), "enum values must not be mutated");
        System.out.println("Upgrade serialization: reproduced duplicate-capacity crash; "
                + "16/17-value lookups, all numeric IDs, save names, gas alias and invalid inputs passed.");
    }

    private static void verify(Fixture[] values) {
        Codec<Fixture> codec = UpgradeSerialization.createCodec(values, Map.of("gas", Fixture.CHEMICAL));
        for (Fixture value : values) {
            var encoded = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
            check(encoded.equals(new JsonPrimitive(value.getSerializedName())), "save name: " + value);
            Fixture expected = value == Fixture.CAPACITY_SECOND ? Fixture.CAPACITY_FIRST : value;
            check(codec.parse(JsonOps.INSTANCE, encoded).getOrThrow() == expected, "name lookup: " + value);
            var numeric = codec.encodeStart(JsonOps.COMPRESSED, value).getOrThrow();
            check(numeric.equals(new JsonPrimitive(value.ordinal())), "numeric ID: " + value);
            check(codec.parse(JsonOps.COMPRESSED, numeric).getOrThrow() == value,
                    "numeric round-trip must preserve even duplicate-name upgrades: " + value);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
