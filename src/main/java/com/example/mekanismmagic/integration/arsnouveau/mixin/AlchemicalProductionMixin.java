package com.example.mekanismmagic.integration.arsnouveau.mixin;

import com.example.mekanismmagic.integration.arsnouveau
        .SourceAmplifierBlockEntity;
import com.hollingsworth.arsnouveau.common.block.tile
        .AlchemicalSourcelinkTile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks consumed-potion Source as an Ars-owned production commit. */
@Mixin(value = AlchemicalSourcelinkTile.class, remap = false)
public abstract class AlchemicalProductionMixin {
    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/hollingsworth/arsnouveau/common/block/"
                            + "tile/AlchemicalSourcelinkTile;addSource(I)I"),
            remap = false)
    private int mekanismMagic$commitPotionSource(
            AlchemicalSourcelinkTile sourcelink, int amount,
            Operation<Integer> original) {
        int before = sourcelink.getSource();
        int result = original.call(sourcelink, amount);
        SourceAmplifierBlockEntity.amplifyOriginalProductionDelta(
                sourcelink, before);
        return result;
    }
}
