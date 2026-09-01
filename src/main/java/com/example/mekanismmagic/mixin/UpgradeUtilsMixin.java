package com.example.mekanismmagic.mixin;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import mekanism.api.Upgrade;
import mekanism.common.util.UpgradeUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets Mekanism's native removal/output path recover our upgrade item. */
@Mixin(value = UpgradeUtils.class, remap = false)
abstract class UpgradeUtilsMixin {
    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true)
    private static void mekanismMagic$getCreativeMagicItem(
            Upgrade upgrade,
            CallbackInfoReturnable<Holder<Item>> callback) {
        if (upgrade == MagicUpgrades.creativeMagic()) {
            callback.setReturnValue(MekanismMagic.CREATIVE_MAGIC_UPGRADE);
        }
    }
}
