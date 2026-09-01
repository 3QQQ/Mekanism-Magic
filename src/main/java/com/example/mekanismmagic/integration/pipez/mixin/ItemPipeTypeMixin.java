package com.example.mekanismmagic.integration.pipez.mixin;

import com.example.mekanismmagic.integration.pipez.PipezItemHandlerCompat;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Replaces only Pipez' ordered source view. Pipez' filters, distribution,
 * configured rate and destination insertion logic remain authoritative.
 */
@Mixin(targets =
        "de.maxhenkel.pipez.blocks.tileentity.types.ItemPipeType",
        remap = false)
public abstract class ItemPipeTypeMixin {
    @ModifyVariable(
            method = "insertOrdered(Lde/maxhenkel/pipez/blocks/"
                    + "tileentity/PipeLogicTileEntity;"
                    + "Lnet/minecraft/core/Direction;Ljava/util/List;"
                    + "Lnet/neoforged/neoforge/items/IItemHandler;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0)
    private IItemHandler mekanismMagic$expandHighCapacitySource(
            IItemHandler source) {
        return PipezItemHandlerCompat.wrapOrderedSource(source);
    }
}
