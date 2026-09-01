package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.core.BlockPos;

/** A machine that can persist direct links to original Ars Source jars. */
public interface SourceLinkHost {
    /** @return true when the link exists after this call. */
    boolean mekanismMagicLinkSourceJar(BlockPos sourceJar);

    /** @return number of links removed. */
    int mekanismMagicClearSourceJarLinks();
}
