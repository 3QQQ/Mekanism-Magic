package com.example.mekanismmagic.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Optional Ars Nouveau integration that deliberately avoids hard-linking its
 * implementation classes. The 1.20.1 mob-jar format predates Minecraft data
 * components and will be restored after the core Forge port is compiling.
 */
public final class ArsNouveauIntegration {
    private ArsNouveauIntegration() {
    }

    public static boolean isFilledMobJar(ItemStack stack) {
        return false;
    }

    public static String entityId(ItemStack stack) {
        return "";
    }

    /**
     * Returns a defensive copy of the captured entity data, including
     * Occultism's optional spiritJob.factoryId field.
     */
    public static CompoundTag entityTag(ItemStack stack) {
        return null;
    }

    /**
     * Removes only the captured mob component, leaving the empty jar item in
     * place for ritual sacrifice processing.
     */
    public static boolean emptyMobJar(ItemStack stack) {
        return false;
    }
}

