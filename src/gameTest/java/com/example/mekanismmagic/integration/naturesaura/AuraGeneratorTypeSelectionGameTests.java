package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MekanismMagic;
import com.mojang.authlib.GameProfile;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Fixed generator types are a choice of future output, never a storage conversion. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AuraGeneratorTypeSelectionGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void converterSelectionPersistsAndCannotRelabelStoredAura(GameTestHelper helper) {
        helper.setBlock(POS, NaturesAuraRegistries.FE_AURA_CONVERTER_BLOCK.get());
        FeAuraConverterBlockEntity tile = helper.getBlockEntity(POS);
        tile.setControlType(RedstoneControl.HIGH);
        helper.assertTrue(tile.getSelectedAuraTypeMode() == 0
                && tile.cycleAuraTypeMode() && tile.cycleAuraTypeMode(), "Could not select Nether output");
        helper.assertTrue(tile.beginAuraOutputTransaction()
                && !tile.beginAuraOutputTransaction()
                && !tile.cycleAuraTypeMode(), "Reentrant network callback changed an empty generator's selected type");
        tile.endAuraOutputTransaction();
        tile.getNativeEnergyContainer().setEnergy(1_000_000);
        helper.assertTrue(tile.generateAura(helper.getLevel()) > 0, "Selected generator produced no Aura");
        int stored = tile.getStoredAura();
        var capability = tile.getAuraContainer(Direction.UP);
        helper.assertTrue(capability != null && capability.isAcceptableType(NaturesAuraAPI.TYPE_NETHER)
                && !capability.isAcceptableType(NaturesAuraAPI.TYPE_OVERWORLD), "Selected output was mislabeled as the physical world");
        helper.assertTrue(!tile.cycleAuraTypeMode() && tile.getSelectedAuraTypeMode() == 2
                && tile.getStoredAura() == stored, "Switch relabeled a nonempty tank");
        var saved = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
        tile.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(tile.getSelectedAuraTypeMode() == 2 && tile.getStoredAura() == stored
                && !tile.hasAuraTypeMismatch(), "Save/reload lost selection or changed output type");
        helper.assertTrue(tile.drainAuraExact(stored, false) && tile.cycleAuraTypeMode()
                && tile.getGenerationAuraType() == NaturesAuraAPI.TYPE_END, "Empty generator could not change to End");
        helper.assertTrue(tile.generateAura(helper.getLevel()) > 0
                && tile.getAuraContainer(Direction.UP).isAcceptableType(NaturesAuraAPI.TYPE_END), "End output still used Nether or local type");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void paidFuelLocksTypeUntilQuotaAndTankAreEmpty(GameTestHelper helper) {
        helper.setBlock(POS, NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK.get());
        AuraFuelGeneratorBlockEntity tile = helper.getBlockEntity(POS);
        tile.setControlType(RedstoneControl.HIGH);
        helper.assertTrue(tile.cycleAuraTypeMode() && tile.cycleAuraTypeMode(), "Could not select Nether fuel generation");
        tile.getNativeInputSlot().setStack(new ItemStack(Items.EGG));
        helper.assertTrue(tile.generateAura(helper.getLevel()) == 1_000
                && tile.getRemainingFuelAura() == 9_000, "Fuel debit or generation quota changed");
        helper.assertTrue(tile.drainAuraExact(1_000, false) && !tile.canCycleAuraTypeMode()
                && !tile.cycleAuraTypeMode(), "Unconverted paid fuel was relabeled after clearing tank");
        var saved = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
        tile.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(tile.getSelectedAuraTypeMode() == 2 && tile.getRemainingFuelAura() == 9_000
                && !tile.hasFuelTypeMismatch(), "Reload lost the paid fuel's selected type");
        for (int tick = 0; tick < 9; tick++) {
            helper.assertTrue(tile.generateAura(helper.getLevel()) == 1_000, "Paid fuel failed to resume");
            helper.assertTrue(tile.drainAuraExact(1_000, false), "Could not remove typed test output");
        }
        helper.assertTrue(tile.canCycleAuraTypeMode() && tile.cycleAuraTypeMode(), "Completed fuel kept switch locked");
        tile.getNativeInputSlot().setStack(new ItemStack(Items.DANDELION));
        helper.assertTrue(tile.generateAura(helper.getLevel()) == 0
                && tile.getNativeInputSlot().getCount() == 1, "Overworld-only flowers produced End Aura");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void menuSwitchRequiresCurrentValidMenu(GameTestHelper helper) {
        helper.setBlock(POS, NaturesAuraRegistries.FE_AURA_CONVERTER_BLOCK.get());
        FeAuraConverterBlockEntity tile = helper.getBlockEntity(POS);
        tile.setControlType(RedstoneControl.HIGH);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "aura-mode-test"));
        var position = tile.getBlockPos();
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        var menu = new FeAuraConverterContainer(81, player.getInventory(), tile);
        int action = NatureAuraGeneratorBlockEntity.CYCLE_AURA_TYPE_BUTTON_ID;
        helper.assertTrue(!menu.clickMenuButton(player, action) && tile.getSelectedAuraTypeMode() == 0,
                "A stale menu changed generator type");
        player.containerMenu = menu;
        helper.assertTrue(menu.clickMenuButton(player, action) && tile.getSelectedAuraTypeMode() == 1,
                "Owner's live menu could not change generator type");
        player.setPos(position.getX() + 100, position.getY(), position.getZ());
        helper.assertTrue(!menu.clickMenuButton(player, action) && tile.getSelectedAuraTypeMode() == 1,
                "A remote stale menu changed generator type");
        helper.succeed();
    }
}
