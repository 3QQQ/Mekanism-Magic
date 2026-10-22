package com.example.mekanismmagic.integration.ae2;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.config.Actionable;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEKey;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.helpers.InventoryAction;
import appeng.menu.me.common.IncrementalUpdateHelper;
import appeng.menu.me.common.MEStorageMenu;
import appeng.parts.reporting.ItemTerminalPart;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKey;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCells;
import com.mojang.authlib.GameProfile;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import de.ellpeck.naturesaura.items.ItemAuraBottle;
import de.ellpeck.naturesaura.items.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** AE's own FILL_ITEM / EMPTY_ITEM menu actions, not a custom bottling packet. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Ae2AuraBottleContainerGameTests {
    private static final BlockPos ENERGY = new BlockPos(1, 1, 1);
    private static final BlockPos TERMINAL = new BlockPos(1, 2, 1);
    private static final BlockPos DRIVE = new BlockPos(2, 1, 1);

    private Ae2AuraBottleContainerGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 260)
    public static void nativeAeMenuFillsAndEmptiesOneOrEntireHeldStack(GameTestHelper helper) {
        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(DRIVE, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE);
        helper.assertTrue(drive.getInternalInventory().insertItem(0,
                        new ItemStack(MagicStorageCells.CELLS.get(1).get()), false).isEmpty()
                        && drive.getInternalInventory().insertItem(1,
                        new ItemStack(AEItems.ITEM_CELL_1K.asItem()), false).isEmpty(),
                "Real AE drive did not mount magic and item cells");
        helper.setBlock(TERMINAL, Blocks.AIR);
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "aura-container-test"));
        BlockPos pos = helper.absolutePos(TERMINAL);
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() - 2);
        helper.assertTrue(PartHelper.setPart(helper.getLevel(), pos, null, player,
                        AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) != null,
                "AE terminal fixture has no center cable");
        ItemTerminalPart part = PartHelper.setPart(helper.getLevel(), pos, Direction.NORTH,
                player, AEParts.TERMINAL.get());
        helper.assertTrue(part != null, "Real AE storage terminal part was not placed");
        MEStorageMenu menu = new MEStorageMenu(MEStorageMenu.TYPE, 81, player.getInventory(), part);
        player.containerMenu = menu;
        IAuraType type = IAuraType.forLevel(helper.getLevel());
        AEKey aura = MagicResourceKeys.key(type.getName());
        ItemStack empty = new ItemStack(ModItems.BOTTLE_TWO_THE_REBOTTLING);
        ItemStack filled = ItemAuraBottle.setType(new ItemStack(ModItems.AURA_BOTTLE), type);
        var content = ContainerItemStrategies.getContainedStack(filled);
        helper.assertTrue(aura instanceof MagicResourceKey && ContainerItemStrategies.isKeySupported(aura)
                        && content != null && content.amount() == Ae2AuraBottleContainerStrategy.AURA_PER_BOTTLE,
                "Aura key is not registered as AE's native held-container interaction");

        helper.startSequence().thenWaitUntil(() -> {
            var node = part.getGridNode();
            helper.assertTrue(node != null && node.isActive() && drive.getMainNode().isActive()
                            && node.getGrid() == drive.getMainNode().getGrid(),
                    "Waiting for real terminal and cells on a powered AE grid");
        }).thenExecute(() -> {
            var storage = part.getGridNode().getGrid().getStorageService().getInventory();
            helper.assertTrue(storage.insert(aura, 100_000, Actionable.MODULATE,
                            menu.getActionSource()) == 100_000,
                    "AE grid rejected a typed Aura balance");
            menu.broadcastChanges();
        }).thenWaitUntil(() -> {
            menu.broadcastChanges();
            helper.assertTrue(serial(menu, aura) > 0 && menu.getLinkStatus().connected(),
                    "Waiting for AE's server-owned Aura row serial");
        }).thenExecute(() -> {
            long serial = serial(menu, aura);
            var storage = part.getGridNode().getGrid().getStorageService().getInventory();
            var actor = menu.getActionSource();

            menu.setCarried(empty.copy());
            menu.handleInteraction(serial, InventoryAction.FILL_ITEM); // ordinary left click
            helper.assertTrue(ItemStack.matches(menu.getCarried(), filled)
                            && storage.extract(aura, 100_000, Actionable.SIMULATE, actor) == 80_000,
                    "AE left-click did not consume exactly 20k selected Aura and fill the held native bottle");
            menu.handleInteraction(-1, InventoryAction.EMPTY_ITEM); // ordinary right click
            helper.assertTrue(ItemStack.matches(menu.getCarried(), empty)
                            && storage.extract(aura, 100_000, Actionable.SIMULATE, actor) == 100_000,
                    "AE right-click did not return exactly 20k of the bottle's own Aura type");

            menu.setCarried(empty.copyWithCount(3));
            menu.handleInteraction(serial, InventoryAction.FILL_ENTIRE_ITEM); // Shift-left click
            helper.assertTrue(ItemStack.matches(menu.getCarried(), filled)
                            && countInventory(player, filled) == 2
                            && storage.extract(aura, 100_000, Actionable.SIMULATE, actor) == 40_000,
                    "AE Shift-left did not fill all three bottles without losing cursor/overflow items");
            clearInventory(player, filled);
            menu.setCarried(filled.copyWithCount(3));
            menu.handleInteraction(-1, InventoryAction.EMPTY_ENTIRE_ITEM); // Shift-right click
            helper.assertTrue(ItemStack.matches(menu.getCarried(), empty)
                            && countInventory(player, empty) == 2
                            && storage.extract(aura, 100_000, Actionable.SIMULATE, actor) == 100_000,
                    "AE Shift-right did not empty all three bottles with exact Aura conservation");

            // A short balance must not be partially extracted for a full bottle.
            storage.extract(aura, 81_000, Actionable.MODULATE, actor);
            menu.setCarried(empty.copy());
            menu.handleInteraction(serial, InventoryAction.FILL_ITEM);
            helper.assertTrue(ItemStack.matches(menu.getCarried(), empty)
                            && storage.extract(aura, 100_000, Actionable.SIMULATE, actor) == 19_000,
                    "Partial Aura simulation consumed a bottle or less than 20k Aura");
            helper.succeed();
        });
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void bottleStrategyRejectsSourceAndSimulationDoesNotMutate(GameTestHelper helper) {
        try (var config = Ae2AuraBottleContainerGameTests.class.getClassLoader()
                .getResourceAsStream("ae2_compat.mixins.json")) {
            helper.assertTrue(config != null, "Missing optional AE mixin configuration");
            String mixins = new String(config.readAllBytes(), StandardCharsets.UTF_8);
            helper.assertTrue(mixins.contains("Ae2AuraBottleContainerUnitMixin")
                            && !mixins.contains("Ae2AuraBottleTerminalScreenMixin")
                            && !mixins.contains("Ae2StorageMenuSerialAccessor"),
                    "Retired Shift-right-click Aura interception remains registered");
            try {
                Class.forName("com.example.mekanismmagic.integration.ae2.Ae2AuraBottleRequestPayload");
                helper.fail("Retired custom Aura bottle packet is still on the runtime classpath");
            } catch (ClassNotFoundException expected) {
                // Native AE MEInteractionPacket is the only manual network route.
            }
        } catch (java.io.IOException failure) {
            throw new AssertionError("Unable to inspect bundled AE mixin configuration", failure);
        }
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "aura-strategy-test"));
        var menu = player.inventoryMenu;
        var strategy = new Ae2AuraBottleContainerStrategy();
        MagicResourceKey aura = (MagicResourceKey) MagicResourceKeys.key(
                IAuraType.forLevel(helper.getLevel()).getName());
        MagicResourceKey source = new MagicResourceKey(MagicResourceKeys.SOURCE_ID);
        ItemStack empty = new ItemStack(ModItems.BOTTLE_TWO_THE_REBOTTLING);
        menu.setCarried(empty.copy());
        var context = strategy.findCarriedContext(player, menu);
        helper.assertTrue(context != null
                        && strategy.insert(context, source, 20_000, Actionable.MODULATE) == 0
                        && strategy.insert(context, aura, 19_999, Actionable.SIMULATE) == 0
                        && strategy.insert(context, aura, 20_000, Actionable.SIMULATE) == 20_000
                        && ItemStack.matches(menu.getCarried(), empty),
                "Source or partial/simulated Aura changed the native held bottle");
        IAuraType foreign = NaturesAuraAPI.AURA_TYPES.values().stream()
                .filter(type -> !type.getName().equals(aura.resource())).findFirst().orElseThrow();
        MagicResourceKey foreignKey = (MagicResourceKey) MagicResourceKeys.key(foreign.getName());
        ItemStack foreignBottle = ItemAuraBottle.setType(new ItemStack(ModItems.AURA_BOTTLE), foreign);
        menu.setCarried(foreignBottle.copy());
        var filledContext = strategy.findCarriedContext(player, menu);
        var content = strategy.getContainedStack(foreignBottle);
        helper.assertTrue(filledContext != null && content != null && foreignKey.equals(content.what())
                        && content.amount() == 20_000
                        && strategy.extract(filledContext, aura, 20_000, Actionable.MODULATE) == 0
                        && ItemStack.matches(menu.getCarried(), foreignBottle),
                "A filled bottle lost its typed Aura identity or emptied into another type");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fullInventoryOverflowDropsFilledBottleInsteadOfLosingIt(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "aura-overflow-test"));
        BlockPos position = helper.absolutePos(TERMINAL);
        player.setPos(position.getX() + 0.5, position.getY() + 0.5, position.getZ() + 0.5);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        var strategy = new Ae2AuraBottleContainerStrategy();
        MagicResourceKey aura = (MagicResourceKey) MagicResourceKeys.key(
                IAuraType.forLevel(helper.getLevel()).getName());
        ItemStack empty = new ItemStack(ModItems.BOTTLE_TWO_THE_REBOTTLING);
        ItemStack filled = ItemAuraBottle.setType(new ItemStack(ModItems.AURA_BOTTLE),
                IAuraType.forLevel(helper.getLevel()));
        player.inventoryMenu.setCarried(empty.copyWithCount(2));
        var context = strategy.findCarriedContext(player, player.inventoryMenu);
        helper.assertTrue(context != null
                        && strategy.insert(context, aura, 20_000, Actionable.SIMULATE) == 20_000
                        && player.inventoryMenu.getCarried().getCount() == 2
                        && strategy.insert(context, aura, 20_000, Actionable.MODULATE) == 20_000,
                "Full-inventory held-bottle simulation or commit failed");
        int overflow = countInventory(player, filled) + helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(3)).stream()
                .filter(entity -> ItemStack.isSameItemSameComponents(entity.getItem(), filled))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
        helper.assertTrue(ItemStack.matches(player.inventoryMenu.getCarried(), empty)
                        && overflow == 1,
                "Native AE overflow rule lost the filled Aura bottle when player inventory was full");
        helper.succeed();
    }

    private static long serial(MEStorageMenu menu, AEKey key) {
        try {
            Field field = MEStorageMenu.class.getDeclaredField("updateHelper");
            field.setAccessible(true);
            Long value = ((IncrementalUpdateHelper) field.get(menu)).getSerial(key);
            return value == null ? -1 : value;
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to inspect AE's server-assigned row serial", failure);
        }
    }

    private static int countInventory(net.minecraft.world.entity.player.Player player, ItemStack expected) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, expected)) total += stack.getCount();
        }
        return total;
    }

    private static void clearInventory(net.minecraft.world.entity.player.Player player, ItemStack expected) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(stack, expected))
                player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }
}
