package com.example.mekanismmagic.integration.ae2.mixin;

import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigMenuInventory;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceMarkerItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zero-amount selectors belong only in ghost configuration, not physical storage. */
@Mixin(value = ConfigMenuInventory.class, remap = false)
public abstract class Ae2MagicMarkerStorageGuardMixin {
    @Shadow public abstract GenericStackInv getDelegate();

    @Inject(method = "isItemValid", at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$rejectRealSlot(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof MagicResourceMarkerItem
                && getDelegate().getMode() == GenericStackInv.Mode.STORAGE) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "setItemDirect", at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$preserveRealSlot(int slot, ItemStack stack, CallbackInfo ci) {
        if (stack.getItem() instanceof MagicResourceMarkerItem
                && getDelegate().getMode() == GenericStackInv.Mode.STORAGE) {
            ci.cancel();
        }
    }
}
