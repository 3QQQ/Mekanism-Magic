package com.example.mekanismmagic.mixin;

import com.example.mekanismmagic.container.RetainedSlotAccess;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inert unless an addon client screen explicitly opts this exact slot into its retained module. */
@Mixin(Slot.class)
public abstract class RetainedSlotMixin implements RetainedSlotAccess {
    @Shadow @Final @Mutable public int x;
    @Shadow @Final @Mutable public int y;
    @Unique private boolean mekanismMagic$retainedManaged;
    @Unique private boolean mekanismMagic$retainedVisible;
    @Unique private int mekanismMagic$originalX;
    @Unique private int mekanismMagic$originalY;

    @Override public void mekanismMagic$positionRetainedSlot(int x, int y, boolean visible) {
        if (!mekanismMagic$retainedManaged) {
            mekanismMagic$originalX = this.x;
            mekanismMagic$originalY = this.y;
        }
        mekanismMagic$retainedManaged = true;
        mekanismMagic$retainedVisible = visible;
        this.x = x;
        this.y = y;
    }

    @Override public void mekanismMagic$setRetainedVisible(boolean visible) {
        mekanismMagic$retainedVisible = visible;
    }

    @Override public void mekanismMagic$restoreRetainedSlot() {
        if (!mekanismMagic$retainedManaged) return;
        x = mekanismMagic$originalX;
        y = mekanismMagic$originalY;
        mekanismMagic$retainedManaged = false;
        mekanismMagic$retainedVisible = false;
    }

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void mekanismMagic$hideClosedModuleSlot(CallbackInfoReturnable<Boolean> callback) {
        if (mekanismMagic$retainedManaged && !mekanismMagic$retainedVisible) callback.setReturnValue(false);
    }
}
