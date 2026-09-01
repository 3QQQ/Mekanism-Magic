package com.example.mekanismmagic.upgrade;

import mekanism.api.Upgrade;

/**
 * Upgrade types contributed to Mekanism's native upgrade system.
 *
 * <p>The value is created while {@link Upgrade} is initialized by
 * {@code UpgradeMixin}. Keeping it distinct from Mekanism Extras' creative
 * upgrade allows both plugins to be installed at the same time.</p>
 */
public final class MagicUpgrades {
    public static Upgrade CREATIVE_MAGIC;

    private MagicUpgrades() {
    }

    public static Upgrade creativeMagic() {
        // Force Upgrade's class initializer (and our enum-extension mixin) to
        // finish before callers read the contributed value.
        Upgrade.values();
        if (CREATIVE_MAGIC == null) {
            throw new IllegalStateException(
                    "Creative magic upgrade was not initialized");
        }
        return CREATIVE_MAGIC;
    }
}
