package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.api.upgrade.IMePatternAutomationHost;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.energy.IEnergyContainer;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

/**
 * Bridges the stable Mekanism Magic automation contract to Mek Energistics'
 * official external-host contract without linking the core mod to AE2.
 */
@Mixin(targets = {
        "com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity",
        "com.example.mekanismmagic.blockentity.NativeSpiritFactoryBlockEntity",
        "com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity",
        "com.example.mekanismmagic.integration.mekextras.ExtraSpiritFactoryBlockEntity",
        "com.example.mekanismmagic.integration.mekextras.ExtraImbuementFactoryBlockEntity"
}, remap = false)
public abstract class MekEnergisticsAutomationMixin
        implements IMePatternAutomationHost {
    private IMekanismMagicAutomation mekanismMagicHost() {
        return (IMekanismMagicAutomation) (Object) this;
    }

    @Override
    public boolean meSupportsPatternAutomation() {
        return mekanismMagicHost()
                .mekanismMagicSupportsPatternAutomation();
    }

    @Override
    public List<IInventorySlot> mePatternItemInputs() {
        return mekanismMagicHost().mekanismMagicPatternInputs();
    }

    @Override
    public List<IInventorySlot> mePatternItemOutputs() {
        return mekanismMagicHost().mekanismMagicPatternOutputs();
    }

    @Override
    public List<IInventorySlot> mePersistentItemInputs() {
        return mekanismMagicHost().mekanismMagicPersistentInputs();
    }

    @Override
    public List<IInventorySlot> meManualOnlyItemSlots() {
        return mekanismMagicHost().mekanismMagicManualOnlySlots();
    }

    @Override
    public IEnergyContainer meEnergyContainer() {
        return mekanismMagicHost().mekanismMagicEnergyContainer();
    }

    @Override
    public boolean meIsBusy() {
        return mekanismMagicHost().mekanismMagicIsBusy();
    }
}
