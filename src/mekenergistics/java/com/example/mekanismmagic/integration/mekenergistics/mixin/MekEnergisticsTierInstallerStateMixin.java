package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import mekanism.common.item.ItemTierInstaller;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.interfaces.ITierUpgradable;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Carries Mek Energistics' independently attached runtime across a normal
 * Mekanism tier-installer block replacement.
 *
 * <p>The machine's {@link IUpgradeData} deliberately remains owned by the
 * machine implementation. MekE state is captured separately at the exact
 * point Mekanism asks the old tile for that payload, and is restored only
 * after the replacement tile has parsed it successfully. This covers pattern
 * slots, priority, interface recovery/pending work and managed-node state
 * without coupling those fields to every machine-specific upgrade payload.</p>
 */
@Mixin(value = ItemTierInstaller.class, remap = false)
public abstract class MekEnergisticsTierInstallerStateMixin {
    @Unique
    private static final ThreadLocal<CapturedMeState>
            mekanismMagic$capturedMeState = new ThreadLocal<>();

    @Inject(method = "useOn", at = @At("HEAD"))
    private void mekanismMagic$clearStaleCapture(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir) {
        mekanismMagic$capturedMeState.remove();
    }

    @Redirect(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/tile/interfaces/"
                            + "ITierUpgradable;getUpgradeData("
                            + "Lnet/minecraft/core/HolderLookup$Provider;)"
                            + "Lmekanism/common/upgrade/IUpgradeData;"))
    private IUpgradeData mekanismMagic$captureMeState(
            ITierUpgradable upgradable,
            HolderLookup.Provider registries) {
        IUpgradeData upgradeData = upgradable.getUpgradeData(registries);
        if (!(upgradable instanceof BlockEntity blockEntity)
                || !(upgradable
                instanceof MeUpgradeRecipeMachineAdapter adapter)
                || adapter.getExistingMeUpgradeRuntime() == null
                || !mekanismMagic$isOurBlock(blockEntity)) {
            return upgradeData;
        }
        CompoundTag state = new CompoundTag();
        adapter.saveMeState(state, registries);
        mekanismMagic$capturedMeState.set(new CapturedMeState(
                blockEntity.getLevel(), blockEntity.getBlockPos(), state));
        return upgradeData;
    }

    @Inject(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lmekanism/common/tile/base/"
                            + "TileEntityMekanism;parseUpgradeData("
                            + "Lnet/minecraft/core/HolderLookup$Provider;"
                            + "Lmekanism/common/upgrade/IUpgradeData;)V",
                    shift = At.Shift.AFTER))
    private void mekanismMagic$restoreMeState(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir) {
        CapturedMeState captured = mekanismMagic$capturedMeState.get();
        mekanismMagic$capturedMeState.remove();
        if (captured == null || captured.level() != context.getLevel()) {
            return;
        }
        BlockEntity replacement = captured.level()
                .getBlockEntity(captured.pos());
        if (!(replacement instanceof TileEntityMekanism)
                || !(replacement
                instanceof MeUpgradeRecipeMachineAdapter adapter)
                || !mekanismMagic$isOurBlock(replacement)
                || !adapter.isMeUpgradeTarget()) {
            return;
        }
        // A replacement tile can initialize its runtime while the block is
        // being installed. Tear down that empty node before loading the old
        // node data, then let MekE rebuild its active state exactly once.
        adapter.destroyMeNode();
        adapter.loadMeState(captured.state(),
                captured.level().registryAccess());
        adapter.onMeUpgradeStateChanged();
        replacement.setChanged();
    }

    @Inject(method = "useOn", at = @At("RETURN"))
    private void mekanismMagic$clearCaptureOnReturn(
            UseOnContext context,
            CallbackInfoReturnable<InteractionResult> cir) {
        mekanismMagic$capturedMeState.remove();
    }

    @Unique
    private static boolean mekanismMagic$isOurBlock(
            BlockEntity blockEntity) {
        var id = BuiltInRegistries.BLOCK.getKey(
                blockEntity.getBlockState().getBlock());
        return id != null && "mekanism_magic".equals(id.getNamespace());
    }

    @Unique
    private record CapturedMeState(
            Level level, BlockPos pos, CompoundTag state) {
        private CapturedMeState {
            pos = pos.immutable();
            state = state.copy();
        }
    }
}
