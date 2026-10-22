package com.example.mekanismmagic.integration.mekenergistics.mixin;

import com.beipuo.mekenergistics.api.upgrade.IMePatternAutomationHost;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.mekenergistics.MekEnergisticsUpgradeEligibility;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

/** Optional host bridge isolated from Nature-free MekE installations. */
@Mixin(targets = {
        "com.example.mekanismmagic.integration.naturesaura."
                + "NatureAltarFactoryBlockEntity",
        "com.example.mekanismmagic.integration.mekextras.ExtraNatureAltarFactoryBlockEntity"
}, remap = false)
public abstract class MekEnergisticsNatureFactoryAutomationMixin
        implements IMePatternAutomationHost {
    public com.beipuo.mekenergistics.upgrade.MeUpgradeMachineProfile<?> getMeUpgradeProfile() {
        return com.beipuo.mekenergistics.upgrade.MePatternAutomationProfiles.forTile(
                (mekanism.common.tile.base.TileEntityMekanism) (Object) this);
    }

    private IMekanismMagicAutomation mekanismMagicHost() {
        return (IMekanismMagicAutomation) (Object) this;
    }

    public boolean supportsUpgrade(MeUpgradeType upgrade) {
        return MekEnergisticsUpgradeEligibility.allows(
                (mekanism.common.tile.base.TileEntityMekanism) (Object) this,
                upgrade);
    }

    @Override
    public boolean meSupportsPatternAutomation() {
        return mekanismMagicHost().mekanismMagicSupportsPatternAutomation();
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

    public boolean isPatternBusy() {
        return mekanismMagicHost().mekanismMagicIsBusy();
    }

    @Override
    public boolean meGroupParallelItemInputs() {
        return mekanismMagicHost().mekanismMagicGroupParallelItemInputs();
    }
}
