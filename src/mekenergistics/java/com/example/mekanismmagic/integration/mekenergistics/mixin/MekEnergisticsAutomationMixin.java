package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.api.upgrade.IMePatternAutomationHost;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.mekenergistics.MekEnergisticsUpgradeEligibility;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.energy.IEnergyContainer;
import mekanism.common.tile.base.TileEntityMekanism;
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

    /** All MekE upgrade types share the same target policy as its installer. */
    public boolean supportsUpgrade(MeUpgradeType upgrade) {
        return MekEnergisticsUpgradeEligibility.allows(
                (TileEntityMekanism) (Object) this, upgrade);
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

    /** Mek Energistics 3.0.6 does not consult meIsBusy internally. */
    public boolean isPatternBusy() {
        return mekanismMagicHost().mekanismMagicIsBusy();
    }

    @Override
    public boolean meGroupParallelItemInputs() {
        return mekanismMagicHost()
                .mekanismMagicGroupParallelItemInputs();
    }
}
