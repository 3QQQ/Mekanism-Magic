package com.example.mekanismmagic.upgrade;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.StringRepresentable;
import org.slf4j.Logger;

/** Preserves Mekanism upgrade IDs even when addons contribute duplicate names. */
public final class UpgradeSerialization {
    private static final Logger LOGGER = LogUtils.getLogger();

    private UpgradeSerialization() {
    }

    public static <E extends Enum<E> & StringRepresentable> Codec<E> createCodec(
            E[] values, Map<String, E> aliases) {
        Map<String, E> byName = new HashMap<>();
        for (E value : values) {
            E first = byName.putIfAbsent(value.getSerializedName(), value);
            if (first != null && first != value) {
                LOGGER.warn("Duplicate Mekanism upgrade name '{}': keeping {} (ID {}) "
                                + "for name decoding; {} (ID {}) retains its numeric ID. "
                                + "Check addons registering this upgrade.",
                        value.getSerializedName(), first.name(), first.ordinal(),
                        value.name(), value.ordinal());
            }
        }
        // Vanilla's <=16-value lookup chooses the first match. Keep that rule
        // above 16 too, instead of switching to Collectors.toMap and crashing.
        // Never deduplicate the enum array: ordinal IDs are used on the wire
        // and by compressed codecs. Existing aliases override normal names.
        byName.putAll(aliases);
        return new StringRepresentable.EnumCodec<>(values, byName::get);
    }
}
