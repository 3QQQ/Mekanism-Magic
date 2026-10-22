package com.example.mekanismmagic.integration.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigInventory;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceMarkerItem;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeBlockEntity;
import com.example.mekanismmagic.integration.common.network.MagicPipeRegistries;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraMachineBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

/** AE + Nature coverage that also runs with Ars Nouveau absent. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Ae2AuraInterfaceGameTests {
    private Ae2AuraInterfaceGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void auraInterfaceSeparatesTypesAndBatchesTransfers(
            GameTestHelper helper) {
        IAuraType local = IAuraType.forLevel(helper.getLevel());
        IAuraType foreign = NaturesAuraAPI.AURA_TYPES.values().stream()
                .filter(type -> !type.isSimilar(local)).findFirst().orElseThrow();
        AEKey key = MagicResourceKeys.key(local.getName());
        AEKey foreignKey = MagicResourceKeys.key(foreign.getName());
        AtomicInteger changed = new AtomicInteger();
        ConfigInventory inventory = ConfigInventory.storage(4)
                .supportedTypes(key.getType(), AEKeyType.items())
                .changeListener(changed::incrementAndGet).build();
        inventory.setCapacity(key.getType(), 100L);
        inventory.setCapacity(AEKeyType.items(), 64L);
        inventory.setStack(0, new GenericStack(foreignKey, 75));
        inventory.setStack(1, new GenericStack(AEItemKey.of(Items.STONE), 64));
        inventory.setStack(2, new GenericStack(key, 80));
        changed.set(0);
        IAuraContainer aura = Ae2AuraInterfaceStorage.create(inventory, local);
        helper.assertTrue(aura != null && aura.isAcceptableType(local)
                        && !aura.isAcceptableType(foreign)
                        && aura.getStoredAura() == 80 && aura.getMaxAura() == 200,
                "Aura view mixed incompatible Aura types or unrelated items");
        helper.assertTrue(aura.storeAura(150, true) == 120
                        && aura.getStoredAura() == 80 && changed.get() == 0,
                "Simulated Aura insertion changed interface contents");
        helper.assertTrue(aura.storeAura(150, false) == 120
                        && aura.getStoredAura() == 200 && changed.get() == 1,
                "Multi-slot Aura insertion lost resources or notified repeatedly");
        helper.assertTrue(aura.drainAura(175, true) == 175
                        && aura.getStoredAura() == 200 && changed.get() == 1,
                "Simulated Aura extraction changed interface contents");
        helper.assertTrue(aura.drainAura(175, false) == 175
                        && aura.getStoredAura() == 25 && changed.get() == 2,
                "Multi-slot Aura extraction lost resources or notified repeatedly");
        helper.assertTrue(aura.storeAura(-1, false) == 0
                        && aura.drainAura(-1, false) == 0
                        && inventory.getAmount(0) == 75
                        && foreignKey.equals(inventory.getKey(0))
                        && inventory.getAmount(1) == 64,
                "Aura adapter changed a foreign resource or a negative request");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void auraMarkerConfiguresStockWithoutCreatingAura(
            GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 2);
        helper.setBlock(pos, AEBlocks.INTERFACE.block());
        var tile = (InterfaceBlockEntity) helper.getBlockEntity(pos);
        IAuraType type = IAuraType.forLevel(helper.getLevel());
        AEKey key = MagicResourceKeys.key(type.getName());
        ItemStack marker = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID,
                        "magic_resource_marker")));
        helper.assertFalse(marker.isEmpty(), "Resource marker is unregistered");
        MagicResourceMarkerItem.select(marker, type.getName());
        GenericStack ghost = GenericStack.unwrapItemStack(marker);
        helper.assertTrue(ghost != null && key.equals(ghost.what())
                        && ghost.amount() == 0,
                "Aura marker unwraps as an item or contains real Aura");
        var config = tile.getInterfaceLogic().getConfig();
        var storage = tile.getInterfaceLogic().getStorage();
        FakeSlot slot = new FakeSlot(config.createMenuWrapper(), 0);
        helper.assertTrue(slot.canSetFilterTo(marker),
                "Native AE fake slot rejected the Aura marker");
        slot.set(marker);
        helper.assertTrue(key.equals(config.getKey(0))
                        && config.getAmount(0) == 1,
                "AE did not turn the empty Aura marker into a stock request");
        slot.set(GenericStack.wrapInItemStack(key, 4_096));
        helper.assertTrue(key.equals(config.getKey(0))
                        && config.getAmount(0) == 4_096,
                "The Aura stock request could not change its amount");
        var realSlots = storage.createMenuWrapper();
        helper.assertFalse(realSlots.isItemValid(0, marker),
                "Physical interface slots accept and destroy a reusable Aura selector");
        helper.assertTrue(ItemStack.isSameItemSameComponents(marker,
                        realSlots.insertItem(0, marker.copy(), false)),
                "Shift/automation insertion consumed the Aura selector");
        realSlots.setItemDirect(0, marker);
        helper.assertTrue(storage.isEmpty(),
                "A zero-amount Aura marker created actual stored Aura");
        storage.setStack(0, new GenericStack(key, 57));
        realSlots.setItemDirect(0, marker);
        helper.assertTrue(storage.getAmount(0) == 57 && key.equals(storage.getKey(0)),
                "Direct selector placement erased existing Aura");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 160)
    public static void auraInterfaceFeedsExistingMagicPipeAndMachine(
            GameTestHelper helper) {
        BlockPos interfacePos = new BlockPos(1, 2, 3);
        BlockPos pipePos = new BlockPos(2, 2, 3);
        BlockPos machinePos = new BlockPos(3, 2, 3);
        helper.setBlock(interfacePos, AEBlocks.INTERFACE.block());
        helper.setBlock(pipePos, MagicPipeRegistries.MAGIC_SOURCE_PIPE_BLOCK.get());
        helper.setBlock(machinePos,
                NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get());
        var pipeTile = (MagicSourcePipeBlockEntity) helper.getBlockEntity(pipePos);
        var pipe = pipeTile.getTransmitter();
        for (Direction side : Direction.values()) {
            ConnectionType mode = side == Direction.WEST
                    ? ConnectionType.PULL : side == Direction.EAST
                    ? ConnectionType.PUSH : ConnectionType.NONE;
            pipe.setConnectionTypeRaw(side, mode);
            pipe.onModeChange(side);
        }
        pipeTile.setForceUpdate();
        var machine = (NatureAuraMachineBlockEntity)
                helper.getBlockEntity(machinePos);
        machine.setAuraSideMode(Direction.WEST,
                NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        helper.startSequence().thenExecuteAfter(5, () -> {
            var level = helper.getLevel();
            var absolute = helper.absolutePos(interfacePos);
            IAuraContainer sided = level.getCapability(
                    NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                    absolute, Direction.EAST);
            IAuraContainer unsided = level.getCapability(
                    NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                    absolute, null);
            helper.assertTrue(sided != null && unsided != null,
                    "AE interface did not register Aura sided/unsided capabilities");
            helper.assertTrue(sided.storeAura(5_000, false) == 5_000
                            && unsided.getStoredAura() == 5_000,
                    "Aura interface capability used a duplicate or missing buffer");
        }).thenWaitUntil(() -> helper.assertTrue(machine.getStoredAura() == 5_000,
                "Existing magic pipe did not deliver interface Aura to the machine"))
                .thenExecute(() -> {
                    IAuraContainer source = helper.getLevel().getCapability(
                            NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                            helper.absolutePos(interfacePos), Direction.EAST);
                    helper.assertTrue(source != null && source.getStoredAura() == 0,
                            "Aura delivered to a machine remained in its AE interface");
                }).thenSucceed();
    }
}
