package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasSpiritFactories;
import com.jerry.mekextras.common.registries.ExtraBlockTypes;
import com.jerry.mekextras.common.tier.ExtraFactoryTier;
import mekanism.api.Upgrade;
import mekanism.api.math.MathUtils;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeEnergy;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Registration and native container prices must agree without changing factory storage. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArsOccultEnergyProfileGameTests {
    private static final BlockPos MACHINE = new BlockPos(3, 2, 3);

    private ArsOccultEnergyProfileGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void ordinaryProfilesMatchRegistrationContainerRuntimeAndNativeUpgrades(GameTestHelper helper) {
        List<MachineCase> machines = List.of(
                new MachineCase(NativeMekanismRegistries.SPIRIT_BLOCK.get(), 50),
                new MachineCase(NativeMekanismRegistries.DIMENSION_MINER_BLOCK.get(), 1_000),
                new MachineCase(NativeMekanismRegistries.RITUAL_BLOCK.get(), 200),
                new MachineCase(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.get(), 100),
                new MachineCase(ArsNouveauRegistries.CATALYST_IDENTIFIER_ASSEMBLER_BLOCK.get(), 100),
                new MachineCase(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get(), 50),
                new MachineCase(ArsNouveauRegistries.ENCHANTING_APPARATUS_PROCESSOR_BLOCK.get(), 200),
                new MachineCase(ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get(), 1_000));
        MachineRecipeResult recipe = new MachineRecipeResult(ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, "ars_occult_energy_profile_test"), ItemStack.EMPTY, 200, List.of(), -1);
        for (MachineCase entry : machines) {
            NativeMagicMachineBlockEntity machine = (NativeMagicMachineBlockEntity) place(helper, entry.block());
            assertOrdinaryPrice(helper, machine, recipe, entry.usage(), "without upgrades");
            assertFilledIdleMachine(helper, machine, machine.getNativeEnergyContainer());

            machine.getComponent().addUpgrades(Upgrade.SPEED, 4);
            helper.assertTrue(machine.getComponent().getUpgrades(Upgrade.SPEED) == 4,
                    "Could not install four speed upgrades on " + entry.block());
            assertOrdinaryPrice(helper, machine, recipe, entry.usage(), "with four speed upgrades");

            machine.getComponent().addUpgrades(Upgrade.ENERGY, 4);
            helper.assertTrue(machine.getComponent().getUpgrades(Upgrade.ENERGY) == 4,
                    "Could not install four energy upgrades on " + entry.block());
            assertOrdinaryPrice(helper, machine, recipe, entry.usage(), "with four speed and energy upgrades");
        }
        helper.assertTrue(ArsNouveauRegistries.SOURCE_AMPLIFIER_TYPE.get(AttributeEnergy.class).getUsage() == 500,
                "Source amplifier was included in the processing profile rebalance");
        helper.assertTrue(ArsNouveauRegistries.SOURCE_CONVERTER_TYPE.get(AttributeEnergy.class).getUsage() == 5_000,
                "Source converter was included in the processing profile rebalance");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void sixteenFactoriesKeepSingleLanePricesAndOriginalStorage(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("mekanism_extras"),
                "The sixteen-factory energy matrix requires Mekanism Extras");
        Block[] spiritFactories = {
                NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.get(),
                NativeMekanismRegistries.ADVANCED_SPIRIT_FACTORY_BLOCK.get(),
                NativeMekanismRegistries.ELITE_SPIRIT_FACTORY_BLOCK.get(),
                NativeMekanismRegistries.ULTIMATE_SPIRIT_FACTORY_BLOCK.get()};
        long[] spiritStorage = {3_000_000, 5_000_000, 7_000_000, 9_000_000};
        Block[] imbuementFactories = {
                ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get(),
                ArsNouveauRegistries.ADVANCED_IMBUEMENT_FACTORY_BLOCK.get(),
                ArsNouveauRegistries.ELITE_IMBUEMENT_FACTORY_BLOCK.get(),
                ArsNouveauRegistries.ULTIMATE_IMBUEMENT_FACTORY_BLOCK.get()};
        Block[] extraSpiritFactories = {
                MekanismExtrasSpiritFactories.ABSOLUTE_BLOCK.get(),
                MekanismExtrasSpiritFactories.SUPREME_BLOCK.get(),
                MekanismExtrasSpiritFactories.COSMIC_BLOCK.get(),
                MekanismExtrasSpiritFactories.INFINITE_BLOCK.get()};
        Block[] extraImbuementFactories = {
                MekanismExtrasImbuementFactories.ABSOLUTE_BLOCK.get(),
                MekanismExtrasImbuementFactories.SUPREME_BLOCK.get(),
                MekanismExtrasImbuementFactories.COSMIC_BLOCK.get(),
                MekanismExtrasImbuementFactories.INFINITE_BLOCK.get()};
        ExtraFactoryTier[] extraTiers = {ExtraFactoryTier.ABSOLUTE, ExtraFactoryTier.SUPREME,
                ExtraFactoryTier.COSMIC, ExtraFactoryTier.INFINITE};
        // ExtraFactory creates its storage supplier from these original machine types.
        AttributeEnergy crusher = ExtraBlockTypes.CRUSHER.get(AttributeEnergy.class);
        AttributeEnergy smelter = ExtraBlockTypes.ENERGIZED_SMELTER.get(AttributeEnergy.class);
        helper.assertTrue(crusher != null && smelter != null, "Extras original machine energy attributes are absent");
        for (int index = 0; index < 4; index++) {
            assertFactoryPriceAndStorage(helper, spiritFactories[index], spiritStorage[index]);
            assertFactoryPriceAndStorage(helper, imbuementFactories[index], 4_000_000);
            assertFactoryPriceAndStorage(helper, extraSpiritFactories[index],
                    originalExtraStorage(crusher, extraTiers[index]));
            assertFactoryPriceAndStorage(helper, extraImbuementFactories[index],
                    originalExtraStorage(smelter, extraTiers[index]));
        }
        helper.succeed();
    }

    private static void assertOrdinaryPrice(GameTestHelper helper, NativeMagicMachineBlockEntity machine,
                                            MachineRecipeResult recipe, int baseUsage, String stage) {
        AttributeEnergy attribute = Attribute.get(machine.getBlockHolder(), AttributeEnergy.class);
        MachineEnergyContainer<?> energy = machine.getNativeEnergyContainer();
        long expectedUsage = MekanismUtils.getEnergyPerTick(machine, baseUsage);
        helper.assertTrue(attribute != null && attribute.getUsage() == baseUsage
                        && machine.baseEnergyPerTick() == baseUsage && energy.getBaseEnergyPerTick() == baseUsage,
                "Registration or cached base price changed " + stage + " on " + machine.getBlockState());
        helper.assertTrue(energy.getEnergyPerTick() == expectedUsage
                        && machine.energyUsagePerTick(recipe) == expectedUsage,
                "Container/runtime price differs from native Mekanism formula " + stage + " on "
                        + machine.getBlockState() + ": expected=" + expectedUsage
                        + ", container=" + energy.getEnergyPerTick()
                        + ", runtime=" + machine.energyUsagePerTick(recipe));
    }

    private static void assertFactoryPriceAndStorage(GameTestHelper helper, Block block, long expectedStorage) {
        TileEntityMekanism tile = place(helper, block);
        AttributeEnergy attribute = Attribute.get(tile.getBlockHolder(), AttributeEnergy.class);
        helper.assertTrue(tile instanceof IMekanismMagicAutomation,
                "Factory lost its native automation energy view: " + block);
        var exposedEnergy = ((IMekanismMagicAutomation) tile).mekanismMagicEnergyContainer();
        helper.assertTrue(exposedEnergy instanceof MachineEnergyContainer<?>,
                "Factory no longer exposes its Mekanism machine energy container: " + block);
        MachineEnergyContainer<?> energy = (MachineEnergyContainer<?>) exposedEnergy;
        helper.assertTrue(attribute != null && attribute.getUsage() == 50
                        && energy.getBaseEnergyPerTick() == 50 && energy.getEnergyPerTick() == 50,
                "Factory price is no longer 50 J/t per active lane: " + block);
        helper.assertTrue(attribute.getConfigStorage() == expectedStorage
                        && energy.getBaseMaxEnergy() == expectedStorage && energy.getMaxEnergy() == expectedStorage,
                "Factory storage changed on " + block + ": expected=" + expectedStorage
                        + ", configured=" + attribute.getConfigStorage()
                        + ", container=" + energy.getMaxEnergy());
        assertFilledIdleMachine(helper, tile, energy);
    }

    private static long originalExtraStorage(AttributeEnergy original, ExtraFactoryTier tier) {
        // Preserve ExtraFactory's original multiplication and clamp behavior exactly.
        return MathUtils.clampToLong((double) (Math.max(original.getConfigStorage(), original.getUsage())
                * tier.processes));
    }

    private static void assertFilledIdleMachine(GameTestHelper helper, TileEntityMekanism tile,
                                                 MachineEnergyContainer<?> energy) {
        energy.setEnergy(energy.getMaxEnergy());
        long stored = energy.getEnergy();
        helper.assertTrue(stored == energy.getMaxEnergy() && stored > 0,
                "Could not fill the empty-input energy fixture: " + tile.getBlockState());
        TileEntityMekanism.tickServer(helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
        helper.assertTrue(energy.getEnergy() == stored && !tile.getActive()
                        && !((IMekanismMagicAutomation) tile).mekanismMagicIsBusy(),
                "Filling stored energy started processing without inputs: " + tile.getBlockState());
    }

    private static TileEntityMekanism place(GameTestHelper helper, Block block) {
        helper.setBlock(MACHINE, Blocks.AIR);
        for (Direction side : Direction.values()) helper.setBlock(MACHINE.relative(side), Blocks.AIR);
        helper.setBlock(MACHINE, block);
        return helper.getBlockEntity(MACHINE);
    }

    private record MachineCase(Block block, int usage) {
    }
}
