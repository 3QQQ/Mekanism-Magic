package com.example.mekanismmagic.mixin.client;

import com.example.mekanismmagic.client.gui.SideConfigDefaultPage;
import mekanism.client.gui.element.window.GuiSideConfiguration;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.interfaces.ISideConfiguration;
import net.minecraft.core.registries.BuiltInRegistries;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = GuiSideConfiguration.class, remap = false)
abstract class GuiSideConfigurationDefaultPageMixin {
    @Shadow @Final
    private TileEntityMekanism tile;

    // Intercept only the constructor's initial selection. Calling updateTabs
    // here would touch widgets that the native/Ars/Aura constructors haven't
    // created yet, and intercepting the setter itself would prevent user changes.
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE",
            target = "Lmekanism/client/gui/element/window/GuiSideConfiguration;setCurrentType(Lmekanism/common/lib/transmitter/TransmissionType;)V",
            ordinal = 0), index = 0)
    private TransmissionType mekanismMagic$preferItemPageOnOpen(TransmissionType original) {
        return SideConfigDefaultPage.select(BuiltInRegistries.BLOCK.getKey(tile.getBlockState().getBlock()),
                ((ISideConfiguration) tile).getConfig().getTransmissions(), original);
    }

}
