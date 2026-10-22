package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.common.energy.MagicMachineEnergyProfiles;
import com.example.mekanismmagic.integration.mekextras.ExtraImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras.ExtraSpiritFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasSpiritFactories;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import mekanism.api.Upgrade;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.interfaces.IUpgradeTile;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/** Real CachedRecipe lane execution, not a nominal-container accounting proxy. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArsOccultFactoryEnergyGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private ArsOccultFactoryEnergyGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void nativeAndExtraFactoriesChargeEachWorkingLaneExactlyOnce(GameTestHelper helper) {
        SpiritInput spirit = spiritInput(helper.getLevel());
        ItemStack imbuement = ArsNouveauRecipeScanner.scan(helper.getLevel().getRecipeManager()).stream()
                .filter(holder -> holder.value().getPedestalItems().isEmpty())
                .map(holder -> ArsNouveauRecipeBridge.representativeChoice(holder.value().getInput()))
                .filter(stack -> !stack.isEmpty()).findFirst().orElseThrow();
        for (Block block : List.of(NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.get(),
                MekanismExtrasSpiritFactories.ABSOLUTE_BLOCK.get(),
                ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get(),
                MekanismExtrasImbuementFactories.ABSOLUTE_BLOCK.get())) {
            helper.setBlock(POS, Blocks.AIR);
            helper.setBlock(POS, block);
            TileEntityMekanism tile = helper.getBlockEntity(POS);
            IMekanismMagicAutomation automation = (IMekanismMagicAutomation) tile;
            ItemStack input;
            if (tile instanceof NativeSpiritFactoryBlockEntity || tile instanceof ExtraSpiritFactoryBlockEntity) {
                automation.mekanismMagicPersistentInputs().getFirst().setStack(spirit.source());
                input = spirit.input();
            } else {
                if (tile instanceof ImbuementFactoryBlockEntity factory) factory.setSource(factory.getMaxSource());
                else if (tile instanceof ExtraImbuementFactoryBlockEntity factory) factory.setSource(factory.getMaxSource());
                input = imbuement;
            }
            automation.mekanismMagicPatternInputs().forEach(slot -> slot.setStack(input.copyWithCount(input.getMaxStackSize())));
            int lanes = automation.mekanismMagicPatternInputs().size();
            helper.assertTrue(lanes == 3 || lanes == 11, "Unexpected representative factory tier");
            // Populate each real recipe monitor before taking exact deltas.
            automation.mekanismMagicEnergyContainer().setEnergy(100_000);
            tick(helper, tile);
            assertWorkingTick(helper, tile, automation, lanes);
            assertWorkingTick(helper, tile, automation, lanes);
            ((IUpgradeTile) tile).getComponent().addUpgrades(Upgrade.SPEED, 2);
            ((IUpgradeTile) tile).getComponent().addUpgrades(Upgrade.ENERGY, 1);
            assertWorkingTick(helper, tile, automation, lanes);
            // The nominal container rate must not become a per-factory
            // multiplier when all but one process becomes idle.
            for (int lane = 1; lane < lanes; lane++) automation.mekanismMagicPatternInputs().get(lane).setStack(ItemStack.EMPTY);
            assertWorkingTick(helper, tile, automation, 1);
            automation.mekanismMagicPatternInputs().getFirst().setStack(ItemStack.EMPTY);
            long before = automation.mekanismMagicEnergyContainer().getEnergy();
            tick(helper, tile);
            helper.assertTrue(automation.mekanismMagicEnergyContainer().getEnergy() == before,
                    automation.mekanismMagicMachineId() + " charged energy with no working lanes");
        }
        helper.succeed();
    }

    private static void assertWorkingTick(GameTestHelper helper, TileEntityMekanism tile,
                                           IMekanismMagicAutomation automation, int lanes) {
        long expected = MekanismUtils.getEnergyPerTick(tile, MagicMachineEnergyProfiles.MATERIAL_PROCESSING) * lanes;
        long before = automation.mekanismMagicEnergyContainer().getEnergy();
        helper.assertTrue(before >= expected, "Factory fixture cannot supply its expected work");
        tick(helper, tile);
        long actual = before - automation.mekanismMagicEnergyContainer().getEnergy();
        helper.assertTrue(actual == expected, automation.mekanismMagicMachineId() + " working-lane energy mismatch: lanes="
                + lanes + ", expected=" + expected + ", actual=" + actual);
        long displayed = tile instanceof NativeSpiritFactoryBlockEntity factory ? factory.getLastUsage()
                : tile instanceof ExtraSpiritFactoryBlockEntity factory ? factory.getLastUsage()
                : tile instanceof ImbuementFactoryBlockEntity factory ? factory.getLastUsage()
                : ((ExtraImbuementFactoryBlockEntity) tile).getLastUsage();
        helper.assertTrue(displayed == actual, "Factory GUI usage disagrees with its measured energy delta");
    }

    private static void tick(GameTestHelper helper, TileEntityMekanism tile) {
        TileEntityMekanism.tickServer(helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
    }

    private static SpiritInput spiritInput(Level level) {
        for (var candidate : OccultismRecipeBridge.spiritJeiRecipes(level)) {
            if (!"crushing".equals(candidate.recipeType()) || candidate.output().isEmpty() || candidate.spirit().isEmpty()) continue;
            for (ItemStack possible : candidate.input().getItems()) {
                ItemStackHandler inventory = new ItemStackHandler(23);
                inventory.setStackInSlot(0, possible.copyWithCount(possible.getMaxStackSize()));
                inventory.setStackInSlot(NativeMagicMachineBlockEntity.CONTAINMENT_SLOT, candidate.spirit());
                var found = OccultismRecipeBridge.findSpiritMachineRecipe(level, inventory, candidate.spirit(), 0L);
                if (found.isPresent() && !found.get().randomTrade() && found.get().recipe().duration() >= 40
                        && !found.get().recipe().inputs().isEmpty()
                        && found.get().recipe().inputs().getFirst().count() <= possible.getMaxStackSize()) {
                    return new SpiritInput(possible.copy(), candidate.spirit().copy());
                }
            }
        }
        throw new IllegalStateException("No native crushing recipe available for per-lane energy regression");
    }

    private record SpiritInput(ItemStack input, ItemStack source) {
    }
}
