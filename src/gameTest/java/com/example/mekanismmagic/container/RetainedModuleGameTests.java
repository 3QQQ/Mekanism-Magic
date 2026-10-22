package com.example.mekanismmagic.container;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryWindowSlot;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryLayout;
import com.example.mekanismmagic.integration.mekextras.ExtraImbuementFactoryBlockEntity;
import mekanism.api.AutomationType;
import mekanism.api.Action;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeGui;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exercises the transformed vanilla Slot against real registered menus, including every loaded factory tier. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RetainedModuleGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private RetainedModuleGameTests() { }

    private static Map<String, List<Integer>> expectedProfiles() {
        Map<String, List<Integer>> expected = new LinkedHashMap<>();
        expected.put("ritual_engine", List.of(18));
        expected.put("nature_altar_processor", List.of(17, 38));
        expected.put("offering_processor", List.of(20));
        expected.put("clibano_processor", List.of(11));
        expected.put("hephaestus_forge_processor", List.of(24, 25, 26, 27));
        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "absolute", "supreme", "cosmic", "infinite")) {
            expected.put(tier + "_nature_altar_factory", List.of(-1, -2));
        }
        return expected;
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void allRetainedMenusPreserveIdentityPermissionsAndStoredData(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int menus = 0, slots = 0;
        for (var expected : expectedProfiles().entrySet()) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, expected.getKey());
            if (!BuiltInRegistries.BLOCK.containsKey(id)) continue; // Optional integrations may be absent in other matrices.
            helper.setBlock(POS, Blocks.AIR);
            helper.setBlock(POS, BuiltInRegistries.BLOCK.get(id));
            TileEntityMekanism tile = helper.getBlockEntity(POS);
            var entries = RetainedSlotProfiles.entries(tile);
            helper.assertTrue(entries.size() == expected.getValue().size(), "Incorrect retained role count: " + id);
            if (tile instanceof NativeMagicMachineBlockEntity nativeTile) {
                for (int index = 0; index < entries.size(); index++) {
                    helper.assertTrue(entries.get(index).inventory() == nativeTile.getLogicalSlot(expected.getValue().get(index)),
                            "Retained profile selected a consumable/output or changed its logical index: " + id);
                }
            }
            var gui = Attribute.get(tile.getBlockState().getBlock(), AttributeGui.class);
            helper.assertTrue(gui != null, "Retained machine lacks its real registered menu: " + id);
            var menu = (MekanismTileContainer<?>) gui.getProvider(tile, false).createMenu(71, player.getInventory(), player);
            var other = (MekanismTileContainer<?>) gui.getProvider(tile, false).createMenu(72, player.getInventory(), player);
            helper.assertTrue(menu != null && other != null, "Registered menu provider returned null: " + id);
            Map<Slot, Position> original = new IdentityHashMap<>();
            for (Slot slot : menu.slots) original.put(slot, new Position(slot.x, slot.y, slot.isActive(), slot.index));
            var layout = RetainedModuleLayout.of(210, entries.size(), id.getPath().equals("ritual_engine"));
            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);
                var matches = menu.getInventoryContainerSlots().stream().filter(slot -> slot.getInventorySlot() == entry.inventory()).toList();
                helper.assertTrue(matches.size() == 1, "Retained storage was duplicated in the menu: " + id);
                InventoryContainerSlot slot = matches.getFirst();
                helper.assertTrue(slot instanceof RetainedSlotAccess, "Real Slot Mixin did not apply: " + id);
                Slot secondSlot = other.getInventoryContainerSlots().stream().filter(candidate -> candidate.getInventorySlot() == entry.inventory())
                        .findFirst().orElseThrow();
                Position before = original.get(slot);
                // Unchecked vanilla menu sync can carry any saved stack. UI must preserve its components even if no recipe accepts it.
                ItemStack specimen = new ItemStack(Items.DIAMOND_HOE);
                specimen.setDamageValue(17);
                specimen.set(DataComponents.CUSTOM_NAME, Component.literal("retained module component sentinel"));
                slot.set(specimen);
                ItemStack saved = slot.getItem().copy();
                var stored = tile.saveWithFullMetadata(helper.getLevel().registryAccess());
                boolean place = slot.mayPlace(specimen), pickup = slot.mayPickup(player);
                int limit = slot.getMaxStackSize();
                ItemStack[] extracted = java.util.Arrays.stream(AutomationType.values())
                        .map(mode -> entry.inventory().extractItem(1, Action.SIMULATE, mode)).toArray(ItemStack[]::new);
                RetainedSlotAccess access = (RetainedSlotAccess) slot;
                for (boolean visible : new boolean[]{false, true, false, true}) {
                    access.mekanismMagic$positionRetainedSlot(layout.slotX(index), layout.slotY(index), visible);
                    helper.assertTrue(slot.isActive() == visible && slot.x == layout.slotX(index) && slot.y == layout.slotY(index),
                            "Retained slot visibility/position differs from its sidebar: " + id);
                    helper.assertTrue(slot.index == before.index() && menu.slots.get(before.index()) == slot
                                    && slot.getInventorySlot() == entry.inventory() && slot.getItem() == entry.inventory().getStack()
                                    && ItemStack.matches(saved, slot.getItem()), "UI replaced a slot, item or stable menu index: " + id);
                    helper.assertTrue(slot.mayPlace(specimen) == place && slot.mayPickup(player) == pickup && slot.getMaxStackSize() == limit,
                            "UI changed native item permissions/limits: " + id);
                    for (AutomationType mode : AutomationType.values()) helper.assertTrue(
                            ItemStack.matches(extracted[mode.ordinal()], entry.inventory().extractItem(1, Action.SIMULATE, mode)),
                            "UI changed automation extraction permissions: " + id + "/" + mode);
                    helper.assertTrue(stored.equals(tile.saveWithFullMetadata(helper.getLevel().registryAccess())), "UI modified serialized machine data: " + id);
                    helper.assertTrue(secondSlot != slot && secondSlot.x == before.x() && secondSlot.y == before.y()
                                    && secondSlot.isActive() == before.active(), "Client projection leaked into another menu: " + id);
                }
                access.mekanismMagic$setRetainedVisible(false);
                helper.assertTrue(!slot.isActive(), "Collapse left an active invisible slot: " + id);
                access.mekanismMagic$positionRetainedSlot(990, 991, true); // Resize must not replace the original restoration point.
                access.mekanismMagic$restoreRetainedSlot();
                access.mekanismMagic$restoreRetainedSlot();
                helper.assertTrue(slot.x == before.x() && slot.y == before.y() && slot.isActive() == before.active(),
                        "Close/resize failed to restore the original menu presentation: " + id);
                slot.set(ItemStack.EMPTY);
                slots++;
            }
            for (var entry : original.entrySet()) {
                Slot slot = entry.getKey(); Position before = entry.getValue();
                helper.assertTrue(slot.x == before.x() && slot.y == before.y() && slot.index == before.index()
                                && slot.isActive() == before.active(), "Non-retained/auxiliary/player slot changed: " + id);
            }
            menus++;
        }
        helper.assertTrue(menus >= 3, "Retained menu fixture silently exercised no machines");
        MekanismMagic.LOGGER.info("Retained modules: {} registered menus / {} real slots passed identity, permission, component, save and independent-menu checks", menus, slots);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void mainSlotsAndExistingLibrariesNeverAcquireRetainedProfiles(GameTestHelper helper) {
        for (String id : List.of("tree_ritual_processor", "animal_spawner_processor", "essence_condenser",
                "imbuement_processor", "mini_ritual_assembler", "enchanting_apparatus", "source_converter", "aura_fuel_generator")) {
            helper.assertTrue(RetainedSlotProfiles.specs(id).isEmpty(), "Consumable machine/existing library was remapped: " + id);
            var key = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, id);
            if (!BuiltInRegistries.BLOCK.containsKey(key)) continue;
            helper.setBlock(POS, Blocks.AIR);
            helper.setBlock(POS, BuiltInRegistries.BLOCK.get(key));
            TileEntityMekanism tile = helper.getBlockEntity(POS);
            helper.assertTrue(RetainedSlotProfiles.entries(tile).isEmpty(), "Consumable or existing library has generic retained entries: " + id);
        }
        // Non-addon slots must remain entirely inert unless explicitly opted in.
        Slot vanilla = new Slot(new net.minecraft.world.SimpleContainer(1), 0, 13, 19);
        helper.assertTrue(vanilla instanceof RetainedSlotAccess && vanilla.isActive() && vanilla.x == 13 && vanilla.y == 19,
                "Shared hook changed an unmanaged vanilla slot");
        ((RetainedSlotAccess) vanilla).mekanismMagic$setRetainedVisible(false);
        helper.assertTrue(vanilla.isActive(), "Visibility setter managed an unrelated slot");
        helper.succeed();
    }

    private record Position(int x, int y, boolean active, int index) { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void minerDrygmyAndAllSpiritMachinesKeepTheirOriginalMainSlots(GameTestHelper helper) {
        Map<String, List<int[]>> restored = new LinkedHashMap<>();
        restored.put("dimension_miner", List.of(new int[]{0, 96, 16}));
        restored.put("spirit_processor", List.of(new int[]{17, 64, 53}));
        restored.put("drygmy_simulator", java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> new int[]{index, 24 + index * 18, 16}).toList());
        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "absolute", "supreme", "cosmic", "infinite")) {
            restored.put(tier + "_spirit_factory", List.of(new int[]{-1, 7, 57}));
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int count = 0;
        for (var entry : restored.entrySet()) {
            var id = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, entry.getKey());
            if (!BuiltInRegistries.BLOCK.containsKey(id)) continue;
            TileEntityMekanism tile = place(helper, id);
            helper.assertTrue(RetainedSlotProfiles.entries(tile).isEmpty(), "Restored machine still opts into a sidebar: " + id);
            var menu = menu(tile, player);
            for (int[] expected : entry.getValue()) {
                var inventory = expected[0] < 0 ? ((IMekanismMagicAutomation) tile).mekanismMagicPersistentInputs().getFirst()
                        : ((NativeMagicMachineBlockEntity) tile).getLogicalSlot(expected[0]);
                var slots = menu.getInventoryContainerSlots().stream().filter(slot -> slot.getInventorySlot() == inventory).toList();
                helper.assertTrue(slots.size() == 1 && slots.getFirst().x == expected[1] && slots.getFirst().y == expected[2]
                        && slots.getFirst().isActive(), "Restored main slot is hidden, duplicated or displaced: " + id);
            }
            count++;
        }
        helper.assertTrue(count >= 3, "Restored main-menu regression silently skipped the requested machines");
        MekanismMagic.LOGGER.info("Sidebar exclusions: {} miner/Drygmy/spirit menus retain their original visible main slots", count);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void chalkAndDictionaryUseCenteredRealSlotsWithStableLogicalStorage(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (boolean chalk : new boolean[]{true, false}) {
            var id = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, chalk ? "mini_ritual_assembler" : "ritual_engine");
            NativeMagicMachineBlockEntity tile = (NativeMagicMachineBlockEntity) place(helper, id);
            var panel = chalk ? DockedPanelLayout.CHALK : DockedPanelLayout.DICTIONARY;
            int count = chalk ? 16 : 1;
            var menu = menu(tile, player);
            for (int index = 0; index < count; index++) {
                var inventory = tile.getLogicalSlot(NativeMagicMachineBlockEntity.CHALK_SLOT_START + index);
                var matches = menu.getInventoryContainerSlots().stream().filter(slot -> slot.getInventorySlot() == inventory).toList();
                helper.assertTrue(matches.size() == 1, "Legacy sidebar duplicated/lost a logical slot");
                var slot = matches.getFirst();
                helper.assertTrue(slot.x == panel.slotX(index, count) && slot.y == panel.slotY(index, count)
                        && panel.isGridSlot(slot.x, slot.y, count) && panel.contains(slot.x - 1, slot.y - 1)
                        && panel.contains(slot.x + 16, slot.y + 16), "Actual chalk/dictionary slot does not match its centered screen frame");
                ItemStack specimen = new ItemStack(Items.PAPER);
                specimen.set(DataComponents.CUSTOM_NAME, Component.literal("sidebar position sentinel " + index));
                slot.set(specimen);
                var saved = tile.saveWithFullMetadata(helper.getLevel().registryAccess());
                var secondMenu = menu(tile, player);
                var second = secondMenu.getInventoryContainerSlots().stream().filter(candidate -> candidate.getInventorySlot() == inventory).findFirst().orElseThrow();
                helper.assertTrue(second.index == slot.index && ItemStack.matches(second.getItem(), specimen)
                        && inventory == second.getInventorySlot()
                        && saved.equals(tile.saveWithFullMetadata(helper.getLevel().registryAccess())), "Centered legacy sidebar changed slot ordering or stored data");
                slot.set(ItemStack.EMPTY);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void everyImbuementTierAlignsItsActualLibrarySlotsAndHitboxes(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        List<String> ids = new java.util.ArrayList<>(List.of("imbuement_processor"));
        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "absolute", "supreme", "cosmic", "infinite")) ids.add(tier + "_imbuement_factory");
        int checked = 0;
        for (String path : ids) {
            var id = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, path);
            if (!BuiltInRegistries.BLOCK.containsKey(id)) continue;
            var tile = place(helper, id);
            int width = tile instanceof ImbuementFactoryBlockEntity factory ? ImbuementFactoryLayout.standardImageWidth(factory.tier)
                    : tile instanceof ExtraImbuementFactoryBlockEntity factory ? ImbuementFactoryLayout.extraImageWidth(factory.tier.ordinal()) : 176;
            var menu = menu(tile, player);
            var slots = menu.getInventoryContainerSlots().stream().filter(slot -> slot.getInventorySlot() instanceof CatalystLibraryWindowSlot).toList();
            helper.assertTrue(slots.size() == 16 && RetainedSlotProfiles.entries(tile).isEmpty(), "Catalyst library acquired a duplicate retained inventory");
            var indices = new java.util.HashSet<Integer>();
            for (var slot : slots) {
                var inventory = (CatalystLibraryWindowSlot) slot.getInventorySlot();
                int index = inventory.windowSlot();
                helper.assertTrue(indices.add(index) && slot.x == CatalystLibraryLayout.slotX(width, index)
                        && slot.y == CatalystLibraryLayout.slotY(index)
                        && CatalystLibraryLayout.pageSlotAt(width, slot.x - 1, slot.y - 1) == index
                        && CatalystLibraryLayout.pageSlotAt(width, slot.x + 16, slot.y + 16) == index,
                        "Real library menu border points at another page slot: " + id + "/" + index);
                helper.assertTrue(inventory.extractItem(1, Action.SIMULATE, AutomationType.EXTERNAL).isEmpty(),
                        "Geometry change altered the library's external-extraction policy");
            }
            checked++;
        }
        helper.assertTrue(checked >= 5, "Library geometry fixture missed ordinary factories");
        MekanismMagic.LOGGER.info("Sidebar library alignment: {} real imbuement menus / {} page slots passed", checked, checked * 16);
        helper.succeed();
    }

    private static TileEntityMekanism place(GameTestHelper helper, ResourceLocation id) {
        helper.setBlock(POS, Blocks.AIR);
        helper.setBlock(POS, BuiltInRegistries.BLOCK.get(id));
        return helper.getBlockEntity(POS);
    }

    private static MekanismTileContainer<?> menu(TileEntityMekanism tile, Player player) {
        var gui = Attribute.get(tile.getBlockState().getBlock(), AttributeGui.class);
        return (MekanismTileContainer<?>) gui.getProvider(tile, false).createMenu(86, player.getInventory(), player);
    }
}
