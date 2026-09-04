package com.example.mekanismmagic.integration.arsnouveau.mixin;

import com.example.mekanismmagic.integration.arsnouveau
        .SourceAmplifierBlockEntity;
import com.hollingsworth.arsnouveau.common.block.tile
        .MycelialSourcelinkTile;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Marks consumed-food Source as an Ars-owned production commit. */
@Mixin(value = MycelialSourcelinkTile.class, remap = false)
public abstract class MycelialProductionMixin {
    @WrapOperation(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/hollingsworth/arsnouveau/common/block/"
                            + "tile/MycelialSourcelinkTile;addSource(I)I"),
            remap = false, require = 2)
    private int mekanismMagic$commitFoodSource(
            MycelialSourcelinkTile sourcelink, int amount,
            Operation<Integer> original) {
        int before = sourcelink.getSource();
        int result = original.call(sourcelink, amount);
        SourceAmplifierBlockEntity.amplifyOriginalProductionDelta(
                sourcelink, before);
        return result;
    }
}
