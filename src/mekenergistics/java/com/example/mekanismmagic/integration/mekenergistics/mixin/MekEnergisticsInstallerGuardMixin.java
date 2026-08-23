package com.example.mekanismmagic.integration.mekenergistics.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mek Energistics 3.0.6 has no registered ME block variant for Mekanism
 * Magic. Its generic factory resolver sees the internal CRUSHING recipe type
 * and would silently convert a spirit factory into me_crusher. Reject that
 * conversion until the external mod exposes a real magic factory variant.
 */
@Mixin(targets = "com.beipuo.mekenergistics.item.MeInstallerUpgradeHandler",
        remap = false)
public abstract class MekEnergisticsInstallerGuardMixin {
    @Inject(method = "tryUpgrade", at = @At("HEAD"), cancellable = true)
    private static void mekanismMagic$guardSpiritFactory(
            ItemStack installer, BlockState state, Level level, BlockPos pos,
            Player player, CallbackInfoReturnable<ItemInteractionResult> cir) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id != null
                && "mekanism_magic".equals(id.getNamespace())
                && id.getPath().endsWith("spirit_factory")) {
            cir.setReturnValue(
                    ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
        }
    }
}
