package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.api.upgrade.IMePatternAutomationHost;
import com.beipuo.mekenergistics.api.upgrade.MePatternAutomation;
import com.beipuo.mekenergistics.item.MeTierInstallerItem;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
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
 * Installs the ME pattern provider in-place for every registered Mekanism
 * Magic automation machine. These machines deliberately have no external
 * Mek Energistics block variant, so the generic replacement resolver must
 * never substitute an unrelated Mekanism machine.
 */
@Mixin(value = MeTierInstallerItem.class, remap = false)
public abstract class MekEnergisticsSpiritFactoryInstallerMixin {
    @Inject(method = "tryInstall", at = @At("HEAD"), cancellable = true)
    private static void mekanismMagic$installInPlace(
            ItemStack installer, Level level, BlockPos pos, Player player,
            CallbackInfoReturnable<InteractionResult> cir) {
        var state = level.getBlockState(pos);
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null || !"mekanism_magic".equals(id.getNamespace())) {
            return;
        }
        // Every Mekanism Magic block is owned by this branch. Registered
        // automation machines are upgraded in place; unregistered/new blocks
        // fail safely instead of entering ME's generic replacement resolver.
        if (!MePatternAutomation.registeredBlockIds().contains(id)) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        if (level.isClientSide) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof MeUpgradeRecipeMachineAdapter adapter)
                || !(blockEntity instanceof IMePatternAutomationHost host)
                || !host.meSupportsPatternAutomation()
                || !adapter.isMeUpgradeTarget()) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        var result = adapter.getMeUpgradeContainer()
                .install(MeUpgradeType.PATTERN_PROVIDER);
        if (!result.successful()) {
            cir.setReturnValue(InteractionResult.FAIL);
            return;
        }
        if (!player.getAbilities().instabuild) {
            installer.shrink(1);
        }
        blockEntity.setChanged();
        adapter.onMeUpgradeStateChanged();
        cir.setReturnValue(InteractionResult.SUCCESS);
    }
}
