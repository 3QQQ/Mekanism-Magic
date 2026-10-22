package com.example.mekanismmagic.mixin;

import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared server-tick/persistence bridge, including Mekanism factory subclasses. */
@Mixin(value = TileEntityMekanism.class, remap = false)
public abstract class TileEntityMekanismAeResourceMixin {
    @Inject(method = "tickServer", at = @At("HEAD"))
    private static void mekanismMagic$pullNativeAeResources(
            Level level, BlockPos pos, BlockState state,
            TileEntityMekanism tile, CallbackInfo ci) {
        MachineAeResourceHooks.tick(tile);
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void mekanismMagic$suspendNativeAeResources(CallbackInfo ci) {
        MachineAeResourceHooks.onRemoved((TileEntityMekanism) (Object) this);
    }

    @Inject(method = "blockRemoved", at = @At("TAIL"))
    private void mekanismMagic$removeNativeAeResources(CallbackInfo ci) {
        MachineAeResourceHooks.onBlockRemoved((TileEntityMekanism) (Object) this);
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void mekanismMagic$saveNativeAeResources(
            CompoundTag tag, HolderLookup.Provider registries,
            CallbackInfo ci) {
        MachineAeResourceHooks.save((TileEntityMekanism) (Object) this,
                tag, registries);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void mekanismMagic$loadNativeAeResources(
            CompoundTag tag, HolderLookup.Provider registries,
            CallbackInfo ci) {
        MachineAeResourceHooks.load((TileEntityMekanism) (Object) this,
                tag, registries);
    }

    @Inject(method = "addContainerTrackers", at = @At("TAIL"))
    private void mekanismMagic$trackNativeAeResources(
            MekanismContainer container, CallbackInfo ci) {
        MachineAeResourceHooks.addContainerTrackers(
                (TileEntityMekanism) (Object) this, container);
    }
}
