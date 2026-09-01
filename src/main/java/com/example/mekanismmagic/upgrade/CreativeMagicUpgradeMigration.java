package com.example.mekanismmagic.upgrade;

import com.example.mekanismmagic.MekanismMagic;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentUpgrade;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;

/** Moves pre-native-upgrade saves out of the retired hidden plugin slot. */
public final class CreativeMagicUpgradeMigration {
    private CreativeMagicUpgradeMigration() {
    }

    public static void migrate(TileEntityMekanism tile,
                               BasicInventorySlot legacySlot) {
        if (legacySlot == null || tile.getLevel() == null
                || tile.getLevel().isClientSide()) {
            return;
        }
        ItemStack stack = legacySlot.getStack();
        if (!stack.is(MekanismMagic.CREATIVE_MAGIC_UPGRADE.get())) {
            return;
        }
        TileComponentUpgrade upgrades = tile.getComponent();
        ItemStack remainder = stack.copy();
        if (upgrades != null
                && upgrades.supports(MagicUpgrades.creativeMagic())) {
            int installed = upgrades.addUpgrades(
                    MagicUpgrades.creativeMagic(), remainder.getCount());
            remainder.shrink(installed);
        }
        // A duplicate or no-longer-supported legacy item must never remain
        // trapped in an invisible compatibility slot. Prefer Mekanism's
        // native upgrade output and fall back to a recoverable world drop.
        if (!remainder.isEmpty() && upgrades != null) {
            remainder = upgrades.getUpgradeOutputSlot().insertItem(
                    remainder, Action.EXECUTE, AutomationType.INTERNAL);
        }
        legacySlot.setStack(ItemStack.EMPTY);
        if (!remainder.isEmpty()) {
            Containers.dropItemStack(tile.getLevel(),
                    tile.getBlockPos().getX() + 0.5,
                    tile.getBlockPos().getY() + 0.5,
                    tile.getBlockPos().getZ() + 0.5,
                    remainder);
        }
        tile.setChanged();
    }
}
