package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.item.MeTierInstallerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Installs the ME provider capability in-place for spirit factories. A
 * Mekanism Magic factory has no external Mek Energistics block variant, so
 * replacing it through the generic installer would select me_crusher.
 */
@Mixin(value = MeTierInstallerItem.class, remap = false)
public abstract class MekEnergisticsSpiritFactoryInstallerMixin {
    @Inject(method = "tryInstall", at = @At("HEAD"), cancellable = true)
    private static void mekanismMagic$installInPlace(
            ItemStack installer, Level level, BlockPos pos, Player player,
            CallbackInfoReturnable<InteractionResult> cir) {
        var state = level.getBlockState(pos);
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null
                || !"mekanism_magic".equals(id.getNamespace())
                || !id.getPath().endsWith("spirit_factory")) {
            return;
        }
        if (level.isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        try {
            Class<?> adapterClass = Class.forName(
                    "com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter");
            if (!adapterClass.isInstance(blockEntity)) {
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }
            Object container = adapterClass.getMethod("getMeUpgradeContainer")
                    .invoke(blockEntity);
            Class<?> upgradeTypeClass = Class.forName(
                    "com.beipuo.mekenergistics.upgrade.MeUpgradeType");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object patternProvider = Enum.valueOf(
                    (Class<? extends Enum>) upgradeTypeClass,
                    "PATTERN_PROVIDER");
            Object result = container.getClass()
                    .getMethod("install", upgradeTypeClass)
                    .invoke(container, patternProvider);
            boolean successful = (Boolean) result.getClass()
                    .getMethod("successful")
                    .invoke(result);
            if (!successful) {
                cir.setReturnValue(InteractionResult.FAIL);
                return;
            }
            if (!player.getAbilities().instabuild) {
                installer.shrink(1);
            }
            blockEntity.setChanged();
            adapterClass.getMethod("onMeUpgradeStateChanged")
                    .invoke(blockEntity);
            cir.setReturnValue(InteractionResult.SUCCESS);
        } catch (ReflectiveOperationException | LinkageError
                 | RuntimeException failure) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
    }
}
