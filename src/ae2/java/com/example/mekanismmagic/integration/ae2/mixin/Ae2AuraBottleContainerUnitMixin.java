package com.example.mekanismmagic.integration.ae2.mixin;

import appeng.api.stacks.AEKey;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.common.MEStorageMenu;
import com.example.mekanismmagic.integration.ae2.Ae2AuraBottleContainerStrategy;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** One bottle is 20k Aura without changing any AE key's global display unit. */
@Mixin(value = AEBaseMenu.class, remap = false)
public abstract class Ae2AuraBottleContainerUnitMixin {
    @Redirect(method = {"handleFillingHeldItem", "handleEmptyHeldItem"},
            at = @At(value = "INVOKE", target = "Lappeng/api/stacks/AEKey;getAmountPerUnit()I"))
    private int mekanismMagic$nativeAuraBottleUnit(AEKey key) {
        if ((Object) this instanceof MEStorageMenu menu
                && key instanceof MagicResourceKey aura
                && Ae2AuraBottleContainerStrategy.isAura(aura)
                && Ae2AuraBottleContainerStrategy.isNativeBottle(menu.getCarried())) {
            return (int) Ae2AuraBottleContainerStrategy.AURA_PER_BOTTLE;
        }
        return key.getAmountPerUnit();
    }
}
