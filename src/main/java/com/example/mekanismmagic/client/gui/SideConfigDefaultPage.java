package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.MekanismMagic;
import mekanism.common.lib.transmitter.TransmissionType;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

/** Opening-page policy only; never mutates the available pages or side settings. */
public final class SideConfigDefaultPage {
    private SideConfigDefaultPage() {
    }

    public static TransmissionType select(ResourceLocation blockId,
                                           Collection<TransmissionType> available,
                                           TransmissionType original) {
        return blockId != null && MekanismMagic.MOD_ID.equals(blockId.getNamespace())
                && available.contains(TransmissionType.ITEM)
                ? TransmissionType.ITEM : original;
    }
}
