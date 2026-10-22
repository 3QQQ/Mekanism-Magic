package com.example.mekanismmagic.integration.jade;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.example.mekanismmagic.integration.naturesaura.AuraSideModeHost;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runs even without Jade installed; only the nested plugin probe links its API. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JadeMachineDataGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private JadeMachineDataGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void jadeSnapshotIsReadOnlyAndBoundedForOrdinaryMachine(GameTestHelper helper) {
        helper.setBlock(POS, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        ImbuementProcessorBlockEntity machine = helper.getBlockEntity(POS);
        machine.setSource(321);
        machine.getNativeEnergyContainer().setEnergy(4_567);
        machine.getNativeInputSlot().setStack(new ItemStack(Items.DIRT, 7));
        var energyBefore = machine.getNativeEnergyContainer().getEnergy();
        ItemStack inputBefore = machine.getNativeInputSlot().getStack().copy();
        CompoundTag snapshot = MachineJadeData.capture(machine);
        helper.assertTrue(snapshot.getInt("source") == 321
                        && snapshot.getInt("sourceCapacity") == machine.getMaxSource(),
                "Jade snapshot did not read exact server Source/capacity");
        for (int index = 0; index < 100; index++) {
            helper.assertTrue(snapshot.equals(MachineJadeData.capture(machine)),
                    "Repeated status reads changed their own data");
        }
        helper.assertTrue(machine.getSource() == 321
                        && machine.getNativeEnergyContainer().getEnergy() == energyBefore
                        && ItemStack.matches(inputBefore, machine.getNativeInputSlot().getStack())
                        && snapshot.getAllKeys().stream().noneMatch(key ->
                        key.toLowerCase(java.util.Locale.ROOT).contains("inventory")),
                "Jade status read mutated resources or exposed inventory data");
        if (ModList.get().isLoaded("jade")) {
            JadePresentProbe.verify(helper, machine);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void jadeSnapshotCoversExtrasAndNatureWithoutTouchingTheirStorage(GameTestHelper helper) {
        helper.setBlock(POS, MekanismExtrasImbuementFactories.ABSOLUTE_BLOCK.get());
        BlockEntity extras = helper.getBlockEntity(POS);
        helper.assertTrue(extras instanceof IMekanismMagicAutomation,
                "Missing Extras factory fixture");
        CompoundTag factory = MachineJadeData.capture(extras);
        helper.assertTrue(factory.contains("source") && factory.contains("active")
                        && factory.getInt("lanes") == 11
                        && factory.getInt("workingLanes") == 0,
                "Jade omitted factory magic or machine state");
        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get());
        BlockEntity nature = helper.getBlockEntity(POS);
        AuraSideModeHost aura = (AuraSideModeHost) nature;
        int before = aura.getStoredAura();
        CompoundTag status = MachineJadeData.capture(nature);
        helper.assertTrue(status.getInt("aura") == before
                        && status.getInt("auraCapacity") == aura.getAuraCapacity()
                        && aura.getStoredAura() == before,
                "Jade omitted or changed native Aura state");
        helper.setBlock(POS, Blocks.CHEST);
        helper.assertTrue(MachineJadeData.capture(helper.getBlockEntity(POS)).isEmpty(),
                "Magic status leaked into an unrelated block entity");
        helper.succeed();
    }

    private static final class JadePresentProbe {
        private static void verify(GameTestHelper helper, TileEntityMekanism machine) {
            CompoundTag payload = new CompoundTag();
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            var accessor = (snownee.jade.api.BlockAccessor) java.lang.reflect.Proxy.newProxyInstance(
                    JadePresentProbe.class.getClassLoader(),
                    new Class<?>[]{snownee.jade.api.BlockAccessor.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getBlockEntity" -> machine;
                        case "getLevel" -> helper.getLevel();
                        case "getPlayer" -> player;
                        case "getServerData" -> payload;
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            helper.assertTrue(MagicMachineJadeProvider.INSTANCE.shouldRequestData(accessor),
                    "Installed Jade did not request supported machine data");
            MagicMachineJadeProvider.INSTANCE.appendServerData(payload, accessor);
            helper.assertTrue(payload.contains(MachineJadeData.KEY)
                            && payload.getCompound(MachineJadeData.KEY).equals(MachineJadeData.capture(machine))
                            && MagicMachineJadeProvider.INSTANCE.getUid().getNamespace().equals(MekanismMagic.MOD_ID),
                    "Installed Jade provider did not transmit the bounded status snapshot");
            var security = machine.getSecurity();
            java.util.UUID previousOwner = security.getOwnerUUID();
            var previousMode = security.getMode();
            java.lang.reflect.Field ownerField;
            try {
                // GameTestServer has no GameProfileCache. Seed only the
                // ownership field instead of invoking name-cache lookups;
                // the real Mekanism access checker below remains unchanged.
                ownerField = security.getClass().getDeclaredField("ownerUUID");
                ownerField.setAccessible(true);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot seed test ownership", failure);
            }
            try {
                ownerField.set(security, java.util.UUID.randomUUID());
                security.setMode(mekanism.api.security.SecurityMode.PRIVATE);
                MagicMachineJadeProvider.INSTANCE.appendServerData(payload, accessor);
                helper.assertTrue(!payload.contains(MachineJadeData.KEY),
                        "Jade retained or transmitted private machine status to a different player");
            } catch (IllegalAccessException failure) {
                throw new AssertionError("Cannot set test ownership", failure);
            } finally {
                security.setMode(previousMode);
                try {
                    ownerField.set(security, previousOwner);
                } catch (IllegalAccessException failure) {
                    throw new AssertionError("Cannot restore test ownership", failure);
                }
            }
        }
    }
}
