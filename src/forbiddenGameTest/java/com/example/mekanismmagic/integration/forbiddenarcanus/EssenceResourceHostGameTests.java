package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceCapabilities;
import com.example.mekanismmagic.integration.common.resource.EssenceResourceHost;
import com.example.mekanismmagic.integration.jade.MachineJadeData;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import mekanism.api.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The same native bank must back item, optional fluid, pipe and AE access. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EssenceResourceHostGameTests {
    private static final BlockPos SOURCE = new BlockPos(1, 2, 1);
    private static final BlockPos SINK = new BlockPos(3, 2, 1);

    private EssenceResourceHostGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void nativeEssencePortsShareLedgerAndSimulationIsReadOnly(GameTestHelper helper) {
        helper.setBlock(SOURCE, ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get());
        helper.setBlock(SINK, ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        EssenceCondenserBlockEntity producer = helper.getBlockEntity(SOURCE);
        HephaestusForgeProcessorBlockEntity forge = helper.getBlockEntity(SINK);
        CompoundTag saved = producer.saveWithoutMetadata(helper.getLevel().registryAccess());
        CompoundTag state = saved.getCompound(EssenceCondenserBlockEntity.STATE_TAG);
        state.putIntArray("banks", new int[]{37, 3, 59, 83});
        saved.put(EssenceCondenserBlockEntity.STATE_TAG, state);
        producer.loadWithComponents(saved, helper.getLevel().registryAccess());
        for (Direction side : Direction.values()) {
            EssenceResourceHost output = helper.getLevel().getCapability(EssenceResourceCapabilities.ESSENCE,
                    helper.absolutePos(SOURCE), side);
            EssenceResourceHost input = helper.getLevel().getCapability(EssenceResourceCapabilities.ESSENCE,
                    helper.absolutePos(SINK), side);
            helper.assertTrue(output == producer && input == forge,
                    "Native essence endpoint missing or duplicated its ledger on " + side);
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(output.canOutputEssence(channel, side) && !output.canInputEssence(channel, side)
                                && input.canInputEssence(channel, side) && !input.canOutputEssence(channel, side),
                        "Source/sink roles or PFAA-independent port sides changed");
            }
        }
        CompoundTag beforeProducer = producer.saveWithoutMetadata(helper.getLevel().registryAccess());
        CompoundTag beforeForge = forge.saveWithoutMetadata(helper.getLevel().registryAccess());
        for (int repeat = 0; repeat < 20; repeat++) {
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(producer.extractEssence(channel, Integer.MAX_VALUE, Action.SIMULATE)
                                == producer.getStoredEssence(channel), "Output simulation changed the offered count");
                helper.assertTrue(forge.insertEssence(channel, Integer.MAX_VALUE, Action.SIMULATE)
                                == forge.getEssenceCapacity(channel), "Input simulation exceeded its real room");
            }
        }
        helper.assertTrue(beforeProducer.equals(producer.saveWithoutMetadata(helper.getLevel().registryAccess()))
                        && beforeForge.equals(forge.saveWithoutMetadata(helper.getLevel().registryAccess())),
                "Native transport simulation mutated machine persistence");
        for (int channel = 0; channel < 4; channel++) {
            int moved = Math.min(producer.getStoredEssence(channel), forge.getEssenceCapacity(channel));
            int before = producer.getStoredEssence(channel);
            helper.assertTrue(producer.extractEssence(channel, moved, Action.EXECUTE) == moved
                            && forge.insertEssence(channel, moved, Action.EXECUTE) == moved
                            && producer.getStoredEssence(channel) + forge.getStoredEssence(channel) == before
                            && producer.getEssenceFluidStored(channel) == producer.getStoredEssence(channel)
                            && forge.getEssenceFluidStored(channel) == forge.getStoredEssence(channel),
                    "Native transport duplicated a channel or maintained a second fluid bank");
        }
        var hud = MachineJadeData.capture(producer);
        for (int channel = 0; channel < 4; channel++) {
            helper.assertTrue(hud.getInt("essence" + channel) == producer.getStoredEssence(channel)
                            && hud.getInt("essenceCapacity" + channel) == producer.getEssenceCapacity(channel),
                    "Jade omitted the authoritative native essence bank");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void nativeEssenceConsumerNeverImportsWhileCreative(GameTestHelper helper) {
        helper.setBlock(SINK, ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        HephaestusForgeProcessorBlockEntity forge = helper.getBlockEntity(SINK);
        forge.getComponent().addUpgrades(MagicUpgrades.creativeMagic(), 1);
        for (int channel = -1; channel <= 4; channel++) {
            helper.assertTrue(!forge.canInputEssence(channel)
                            && forge.insertEssence(channel, 10, Action.SIMULATE) == 0
                            && forge.insertEssence(channel, 10, Action.EXECUTE) == 0
                            && forge.extractEssence(channel, 10, Action.EXECUTE) == 0,
                    "Creative or invalid essence channel imported or exported resources");
        }
        helper.succeed();
    }
}
