package com.example.mekanismmagic.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.beipuo.mekenergistics.api.upgrade.IMePatternAutomationHost;
import com.beipuo.mekenergistics.blockentity.api.MeUpgradeableMachine;
import com.beipuo.mekenergistics.blockentity.slot.MePatternInventorySlot;
import com.beipuo.mekenergistics.config.MekEnergisticsConfig;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.ae2.Ae2NatureAltarPattern;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryWindowSlot;
import com.example.mekanismmagic.integration.mekextras.ExtraNatureAltarFactoryBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.AnimalSpawnerProcessorContainer;
import com.example.mekanismmagic.integration.naturesaura.AuraFieldInterfaceBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.AuraFieldInterfaceContainer;
import com.example.mekanismmagic.integration.naturesaura.AuraSideConfigControls;
import com.example.mekanismmagic.integration.naturesaura.AuraSideModeHost;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarFactoryBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarFactoryContainer;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarProcessorContainer;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraMachineBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraRecipeBridge;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraRecipeScanner;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraScreenLayout;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import mekanism.api.RelativeSide;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.container.SelectedWindowData;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.container.slot.VirtualInventoryContainerSlot;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.List;
import java.util.EnumMap;
import java.util.Set;

/** Runtime smoke/regression coverage for Nature's Aura 41.9 integration. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NatureAuraIntegrationGameTests {
    private static final BlockPos PROCESSOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos FACTORY_POS = new BlockPos(3, 1, 1);

    private NatureAuraIntegrationGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void compactAuraMenusKeepSlotsAndInventoryClicksAligned(
            GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(PROCESSOR_POS,
                NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get());
        var processor = require(helper, PROCESSOR_POS,
                NatureAltarProcessorBlockEntity.class);
        verifyCompactMenuLayout(helper, new NatureAltarProcessorContainer(83,
                        player.getInventory(), processor), player, 176,
                NatureAuraScreenLayout.COMPACT_HEIGHT, 8,
                NatureAuraScreenLayout.COMPACT_INVENTORY_Y,
                NatureAuraScreenLayout.COMPACT_LABEL_Y,
                NatureAuraScreenLayout.COMPACT_WARNING_Y, 1,
                new int[][]{{28, 53}, {46, 35}, {116, 35},
                        {64, 53}, {82, 53}});

        helper.setBlock(PROCESSOR_POS,
                NaturesAuraRegistries.AURA_FIELD_INTERFACE_BLOCK.get());
        var field = require(helper, PROCESSOR_POS,
                AuraFieldInterfaceBlockEntity.class);
        verifyCompactMenuLayout(helper, new AuraFieldInterfaceContainer(84,
                        player.getInventory(), field), player, 176,
                NatureAuraScreenLayout.COMPACT_HEIGHT, 8,
                NatureAuraScreenLayout.COMPACT_INVENTORY_Y,
                NatureAuraScreenLayout.COMPACT_LABEL_Y, 56, 1,
                new int[][]{{28, 35}});

        helper.setBlock(PROCESSOR_POS,
                NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK.get());
        var animal = require(helper, PROCESSOR_POS,
                AnimalSpawnerProcessorBlockEntity.class);
        verifyCompactMenuLayout(helper, new AnimalSpawnerProcessorContainer(85,
                        player.getInventory(), animal), player, 176,
                NatureAuraScreenLayout.ANIMAL_HEIGHT, 8,
                NatureAuraScreenLayout.ANIMAL_INVENTORY_Y,
                NatureAuraScreenLayout.ANIMAL_LABEL_Y,
                NatureAuraScreenLayout.ANIMAL_WARNING_Y, 2,
                new int[][]{{20, 62}, {38, 26}, {56, 26}, {74, 26},
                        {38, 44}, {56, 44}, {74, 44}, {134, 44}});
        helper.assertTrue(NatureAuraScreenLayout.COMPACT_HEIGHT == 180
                        && NatureAuraScreenLayout.COMPACT_INVENTORY_Y == 99
                        && NatureAuraScreenLayout.ANIMAL_HEIGHT == 194
                        && NatureAuraScreenLayout.ANIMAL_INVENTORY_Y == 113
                        && NatureAuraScreenLayout.TREE_HEIGHT == 186
                        && NatureAuraScreenLayout.TREE_INVENTORY_Y == 105,
                "Nature menus no longer use the compact work-area spacing");
        helper.succeed();
    }

    /** Checks the real server menu; factory tests reuse this for every tier. */
    public static void verifyCompactMenuLayout(GameTestHelper helper,
                                               MekanismTileContainer<?> menu,
                                               Player player, int width,
                                               int height, int inventoryX,
                                               int inventoryY, int labelY,
                                               int warningY, int warningLines,
                                               int[][] machineCoordinates) {
        var main = menu.getMainInventorySlots();
        var hotbar = menu.getHotBarSlots();
        var allMachine = menu.getInventoryContainerSlots();
        // MekanismTileContainer always adds these two virtual upgrade-window
        // slots at (0, 0). They are part of the server menu, but do not exist
        // in its main window and are not part of the compact machine frame.
        var upgradeInput = menu.getUpgradeSlot();
        var upgradeOutput = menu.getUpgradeOutputSlot();
        helper.assertTrue(upgradeInput != null && upgradeOutput != null
                        && upgradeInput != upgradeOutput
                        && allMachine.contains(upgradeInput)
                        && allMachine.contains(upgradeOutput)
                        && upgradeInput.getInventorySlot()
                        == menu.getTileEntity().getComponent().getUpgradeSlot()
                        && upgradeOutput.getInventorySlot()
                        == menu.getTileEntity().getComponent().getUpgradeOutputSlot()
                        && !upgradeInput.exists(null) && !upgradeOutput.exists(null),
                "Compact fixture did not retain Mekanism's two hidden upgrade-window slots");
        // MekE's PatternSlotHolder appends its configured 4x9 pattern pages
        // even before an ME upgrade is installed. Verify each slot against
        // the owner's actual pattern inventory, not a guessed count or all
        // virtual slots. Machines with no ME profile add no pattern slots.
        List<? extends IInventorySlot> expectedPatterns =
                menu.getTileEntity() instanceof MeUpgradeableMachine meMachine
                        && meMachine.isMeUpgradeTarget()
                        ? meMachine.getPatternSlots() : List.of();
        var patternSlots = allMachine.stream().filter(slot ->
                slot.getInventorySlot() instanceof MePatternInventorySlot).toList();
        helper.assertTrue(patternSlots.size() == expectedPatterns.size()
                        && (expectedPatterns.isEmpty()
                        || expectedPatterns.size() == MekEnergisticsConfig.patternSlots()),
                "MekE pattern pages differ from the owner's declared inventory: actual="
                        + patternSlots.size() + ", owner=" + expectedPatterns.size());
        for (var slot : patternSlots) {
            helper.assertTrue(slot instanceof VirtualInventoryContainerSlot virtual
                            && !virtual.exists(null)
                            && virtual.exists(SelectedWindowData.UNSPECIFIED)
                            && virtual.getLinkedWindow() == null
                            && virtual.getSlotType() == ContainerSlotType.IGNORED
                            && virtual.x == 0 && virtual.y == 0
                            && expectedPatterns.stream().anyMatch(pattern ->
                            pattern == virtual.getInventorySlot()),
                    "MekE auxiliary slot is not its owner's hidden pattern-window slot: " + slot.index);
        }
        for (var pattern : expectedPatterns) {
            helper.assertTrue(patternSlots.stream().filter(slot ->
                            slot.getInventorySlot() == pattern).count() == 1,
                    "MekE pattern backing slot is missing or registered more than once");
        }
        var librarySlots = allMachine.stream().filter(slot ->
                slot.getInventorySlot() instanceof CatalystLibraryWindowSlot)
                .toList();
        boolean altarLibrary = menu.getTileEntity()
                instanceof NatureAltarProcessorBlockEntity
                || menu.getTileEntity() instanceof NatureAltarFactoryBlockEntity
                || menu.getTileEntity()
                instanceof ExtraNatureAltarFactoryBlockEntity;
        int expectedLibrarySlots = altarLibrary
                ? CatalystLibraryLayout.PAGE_SIZE : 0;
        helper.assertTrue(librarySlots.size() == expectedLibrarySlots,
                "Compact menu is missing its fixed catalyst page window");
        for (var slot : librarySlots) {
            var window = (CatalystLibraryWindowSlot)
                    slot.getInventorySlot();
            helper.assertTrue(slot.x == CatalystLibraryLayout.slotX(
                            width, window.windowSlot())
                            && slot.y == CatalystLibraryLayout.slotY(
                            window.windowSlot()),
                    "Catalyst page window slot moved from its docked panel: "
                            + window.windowSlot());
        }
        var machine = allMachine.stream().filter(slot ->
                slot != upgradeInput && slot != upgradeOutput
                        && !patternSlots.contains(slot)
                        && !librarySlots.contains(slot)).toList();
        var visibleSlots = menu.slots.stream().filter(slot ->
                slot != upgradeInput && slot != upgradeOutput
                        && !patternSlots.contains(slot)
                        && !librarySlots.contains(slot)).toList();
        helper.assertTrue(main.size() == 27 && hotbar.size() == 9
                        && machine.size() == machineCoordinates.length
                        && allMachine.size() == machineCoordinates.length
                        + 2 + expectedPatterns.size()
                        + expectedLibrarySlots
                        && menu.slots.size() == machineCoordinates.length
                        + 38 + expectedPatterns.size()
                        + expectedLibrarySlots,
                "Compact menu slot count drifted for " + menu.getTileEntity().getClass().getSimpleName()
                        + ": main=" + main.size() + ", hotbar=" + hotbar.size()
                        + ", visibleMachine=" + machine.size() + ", allMachine=" + allMachine.size()
                        + ", patternPages=" + patternSlots.size()
                        + ", total=" + menu.slots.size() + ", expectedMachine=" + machineCoordinates.length);
        for (int index = 0; index < 27; index++) {
            var slot = main.get(index);
            helper.assertTrue(slot.x == inventoryX + index % 9 * 18
                            && slot.y == inventoryY + index / 9 * 18
                            && slot.container == player.getInventory()
                            && slot.getContainerSlot() == index + 9,
                    "Compact main inventory coordinates/backing index drifted: " + index);
        }
        for (int index = 0; index < 9; index++) {
            var slot = hotbar.get(index);
            helper.assertTrue(slot.x == inventoryX + index * 18
                            && slot.y == inventoryY + 58
                            && slot.container == player.getInventory()
                            && slot.getContainerSlot() == index,
                    "Compact hotbar coordinates/backing index drifted: " + index);
        }
        for (int[] coordinate : machineCoordinates) {
            helper.assertTrue(machine.stream().filter(slot ->
                            slot.x == coordinate[0] && slot.y == coordinate[1]).count() == 1,
                    "Compacting moved a machine slot from "
                            + coordinate[0] + "," + coordinate[1]);
        }
        helper.assertTrue(labelY == inventoryY - 10
                        && warningY + warningLines * 9 <= labelY
                        && 16 + 52 <= labelY - 2
                        && inventoryY + 75 <= height - 6,
                "Aura warnings, resource bars or hotbar exceed their compact regions");
        for (int index = 0; index < visibleSlots.size(); index++) {
            var slot = visibleSlots.get(index);
            helper.assertTrue(slot.x - 1 >= 4 && slot.x + 17 <= width - 4
                            && slot.y - 1 >= 4 && slot.y + 17 <= height - 6,
                    "Compact slot border extends outside the frame: " + index);
            for (int other = index + 1; other < visibleSlots.size(); other++) {
                var candidate = visibleSlots.get(other);
                helper.assertTrue(Math.abs(slot.x - candidate.x) >= 18
                                || Math.abs(slot.y - candidate.y) >= 18,
                        "Compact slot borders overlap: " + index + "/" + other);
            }
        }
        for (var slot : machine) {
            helper.assertTrue(slot.y + 17 <= warningY,
                    "Aura status text overlaps a machine slot");
        }

        player.containerMenu = menu;
        player.getInventory().clearContent();
        var savedMachineStacks = allMachine.stream()
                .map(slot -> slot.getItem().copy()).toList();
        player.getInventory().setItem(9, new ItemStack(Items.BARRIER, 7));
        player.getInventory().setItem(0, new ItemStack(Items.STRUCTURE_VOID, 3));
        menu.clicked(main.getFirst().index, 0, ClickType.SWAP, player);
        helper.assertTrue(main.getFirst().getItem().is(Items.STRUCTURE_VOID)
                        && main.getFirst().getItem().getCount() == 3
                        && hotbar.getFirst().getItem().is(Items.BARRIER)
                        && hotbar.getFirst().getItem().getCount() == 7,
                "Number-key swap used a stale compact inventory index");

        player.getInventory().clearContent();
        player.getInventory().setItem(9, new ItemStack(Items.BARRIER, 7));
        menu.clicked(main.getFirst().index, 0, ClickType.QUICK_MOVE, player);
        helper.assertTrue(!main.getFirst().hasItem()
                        && hotbar.stream().filter(slot -> slot.getItem().is(Items.BARRIER))
                        .mapToInt(slot -> slot.getItem().getCount()).sum() == 7
                        && menu.getCarried().isEmpty(),
                "Shift-click did not move the visible main slot into the hotbar");
        for (int index = 0; index < allMachine.size(); index++) {
            helper.assertTrue(ItemStack.matches(savedMachineStacks.get(index),
                            allMachine.get(index).getItem()),
                    "Player inventory clicks modified a machine slot: " + index);
        }
        player.getInventory().clearContent();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void moduleRecipesSidesAndAutomationLoad(
            GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("naturesaura"),
                "Nature's Aura 41.9 was absent from the runtime GameTest");
        List<NatureAuraRecipeBridge.AltarRecipeView> recipes =
                NatureAuraRecipeScanner.scan(
                        helper.getLevel().getRecipeManager());
        long nativeRecipeCount = recipes.stream().filter(recipe ->
                recipe.id().getNamespace().equals("naturesaura")).count();
        helper.assertTrue(nativeRecipeCount == 36,
                "Expected all 36 Nature's Aura 41.9 altar recipes, found "
                        + nativeRecipeCount);
        var originalIds = Set.copyOf(recipes.stream()
                .map(NatureAuraRecipeBridge.AltarRecipeView::id).toList());
        for (String path : List.of("infused_iron_bee", "tainted_gold_bee")) {
            var id = ResourceLocation.fromNamespaceAndPath("productivebees",
                    "naturesaura/altar/" + path);
            helper.assertTrue(originalIds.contains(id)
                            == ModList.get().isLoaded("productivebees"),
                    "Productive Bees altar recipe has the wrong loading state: " + id);
        }

        helper.setBlock(PROCESSOR_POS,
                NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get());
        helper.setBlock(FACTORY_POS,
                NaturesAuraRegistries.BASIC_NATURE_ALTAR_FACTORY_BLOCK.get());
        NatureAltarProcessorBlockEntity processor = require(
                helper, PROCESSOR_POS, NatureAltarProcessorBlockEntity.class);
        NatureAltarFactoryBlockEntity factory = require(
                helper, FACTORY_POS, NatureAltarFactoryBlockEntity.class);

        // This is implemented by the Nature-factory-specific MekE mixin.
        helper.assertTrue((Object) factory instanceof IMePatternAutomationHost,
                "Nature factory MekE automation mixin was not applied");
        helper.assertTrue((Object) processor
                        instanceof MeUpgradeRecipeMachineAdapter
                        && (Object) factory
                        instanceof MeUpgradeRecipeMachineAdapter,
                "Nature machines were not registered as MekE upgrade hosts");
        helper.assertTrue(((MeUpgradeRecipeMachineAdapter) (Object) processor)
                        .isMeUpgradeTarget()
                        && ((MeUpgradeRecipeMachineAdapter) (Object) factory)
                        .isMeUpgradeTarget(),
                "Nature machines were absent from MekE target registration");

        verifyServerSideButtons(helper, processor, factory);
        verifyCatalystDirectSetGuard(helper, processor, recipes,
                "Nature altar processor");
        verifyCatalystDirectSetGuard(helper, factory, recipes,
                "Nature altar factory");
        verifyAeDecoderMixin(helper, recipes);

        NatureAuraRecipeBridge.AltarRecipeView uncatalyzed = recipes.stream()
                .filter(recipe -> !recipe.requiresCatalyst())
                .filter(recipe -> NatureAuraRecipeBridge
                        .representativeChoice(recipe.input()).isPresent())
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Nature 41.9 exposed no uncatalyzed altar recipe"));
        factory.mekanismMagicPatternInputs().getFirst().setStack(
                NatureAuraRecipeBridge.representativeChoice(
                        uncatalyzed.input()).orElseThrow());
        RecipeCacheLookupMonitor<?> firstMonitor = recipeMonitor(factory, 0);
        firstMonitor.setHasNoRecipe(0);
        helper.assertTrue(firstMonitor.hasNoRecipe(0),
                "Could not establish the factory's negative recipe cache");

        NatureAuraRecipeScanner.refresh(
                helper.getLevel().getRecipeManager());
        var refreshed = NatureAuraRecipeScanner.scan(
                helper.getLevel().getRecipeManager());
        helper.assertTrue(refreshed.size() == recipes.size()
                        && Set.copyOf(refreshed.stream()
                        .map(NatureAuraRecipeBridge.AltarRecipeView::id).toList())
                        .equals(originalIds),
                "Data-pack refresh did not preserve the complete altar catalog");
        long refreshedVersion = NatureAuraRecipeScanner.version(
                helper.getLevel().getRecipeManager());
        helper.startSequence()
                .thenExecuteAfter(2, () -> helper.assertTrue(
                        observedCatalogVersion(factory) >= refreshedVersion
                                && observedCatalogVersion(factory) == NatureAuraRecipeScanner.version(
                                helper.getLevel().getRecipeManager()),
                        "Nature factory did not observe and refresh its "
                                + "catalog monitor after a recipe reload"))
                .thenExecute(() -> helper.assertTrue(
                        !firstMonitor.hasNoRecipe(0),
                        "Nature recipe reload did not clear the factory's "
                                + "negative recipe cache"))
                .thenSucceed();
    }

    private static void verifyServerSideButtons(
            GameTestHelper helper, NatureAltarProcessorBlockEntity processor,
            NatureAltarFactoryBlockEntity factory) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        Direction side = Direction.DOWN;
        helper.assertTrue(processor.getAuraSideMode(side)
                        == NatureAuraMachineBlockEntity.AuraSideMode.INPUT,
                "Processor Aura sides did not default to INPUT");
        var processorMenu = new NatureAltarProcessorContainer(71,
                player.getInventory(), processor);
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(processor.getBlockPos()));
        player.containerMenu = processorMenu;
        processorMenu.clickMenuButton(player,
                        NatureAltarProcessorContainer.SIDE_MODE_BUTTON_START
                                + side.ordinal());
        helper.assertTrue(processor.getAuraSideMode(side)
                        == NatureAuraMachineBlockEntity.AuraSideMode.OUTPUT,
                "Processor Aura side button did not reach the server tile");

        helper.assertTrue(factory.getAuraSideMode(side)
                        == NatureAuraMachineBlockEntity.AuraSideMode.INPUT,
                "Factory Aura sides did not default to INPUT");
        var factoryMenu = new NatureAltarFactoryContainer(72,
                player.getInventory(), factory);
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(factory.getBlockPos()));
        player.containerMenu = factoryMenu;
        factoryMenu.clickMenuButton(player,
                        NatureAltarFactoryContainer.SIDE_MODE_BUTTON_START
                                + side.ordinal());
        helper.assertTrue(factory.getAuraSideMode(side)
                        == NatureAuraMachineBlockEntity.AuraSideMode.OUTPUT,
                "Factory Aura side button did not reach the server tile");
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 120)
    public static void auraSideMenusUseOpenedMachineAndPreserveOtherState(
            GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(PROCESSOR_POS,
                NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get());
        var processor = require(helper, PROCESSOR_POS,
                NatureAltarProcessorBlockEntity.class);
        verifyAuraSideMenu(helper, new NatureAltarProcessorContainer(80,
                player.getInventory(), processor), player);

        helper.setBlock(PROCESSOR_POS,
                NaturesAuraRegistries.AURA_FIELD_INTERFACE_BLOCK.get());
        var field = require(helper, PROCESSOR_POS,
                AuraFieldInterfaceBlockEntity.class);
        var fieldMenu = new AuraFieldInterfaceContainer(81,
                player.getInventory(), field);
        verifyAuraSideMenu(helper, fieldMenu, player);
        var oldFieldMode = field.getFieldMode();
        helper.assertTrue(fieldMenu.clickMenuButton(player,
                        AuraFieldInterfaceContainer.CYCLE_MODE_BUTTON)
                        && field.getFieldMode() == oldFieldMode.next(),
                "Aura field work-mode button no longer functions");

        helper.setBlock(PROCESSOR_POS,
                NaturesAuraRegistries.ANIMAL_SPAWNER_PROCESSOR_BLOCK.get());
        var animal = require(helper, PROCESSOR_POS,
                AnimalSpawnerProcessorBlockEntity.class);
        var animalMenu = new AnimalSpawnerProcessorContainer(82,
                player.getInventory(), animal);
        verifyAuraSideMenu(helper, animalMenu, player);
        helper.assertTrue(animalMenu.clickMenuButton(player,
                        AnimalSpawnerProcessorContainer.SELECT_NEXT_RECIPE),
                "Animal spawner recipe button was lost");

        var beforeRemoval = animal.getAuraSideMode(Direction.UP);
        helper.setBlock(PROCESSOR_POS, net.minecraft.world.level.block.Blocks.AIR);
        helper.assertTrue(!animalMenu.clickMenuButton(player,
                        AuraSideConfigControls.FORWARD_BUTTON_START + Direction.UP.ordinal())
                        && animal.getAuraSideMode(Direction.UP) == beforeRemoval,
                "Removed machine accepted an Aura configuration packet");
        helper.succeed();
    }

    /** Reused by the native/Extras tier test, exercising the actual menu route. */
    public static void verifyAuraSideMenu(GameTestHelper helper,
                                         MekanismTileContainer<?> menu,
                                         Player player) {
        var tile = menu.getTileEntity();
        player.setPos(net.minecraft.world.phys.Vec3.atCenterOf(tile.getBlockPos()));
        var host = (AuraSideModeHost) tile;
        var initialSide = host.getAuraSideMode(Direction.DOWN);
        helper.assertTrue(!menu.clickMenuButton(player,
                        AuraSideConfigControls.FORWARD_BUTTON_START)
                        && host.getAuraSideMode(Direction.DOWN) == initialSide,
                "An unopened menu changed a machine's Aura sides");
        player.containerMenu = menu;
        helper.assertTrue(AuraSideConfigControls.canConfigure(menu, player),
                "Test fixture could not open its own Aura menu");
        host.setAuraSideMode(Direction.UP,
                NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
        var aura = helper.getLevel().getCapability(
                NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                tile.getBlockPos(), Direction.UP);
        helper.assertTrue(aura != null && aura.storeAura(137, false) == 137,
                "Aura side fixture could not fund its real buffer");
        int stored = host.getStoredAura();
        int capacity = host.getAuraCapacity();

        for (Direction facing : List.of(Direction.NORTH, Direction.EAST,
                Direction.SOUTH, Direction.WEST)) {
            tile.setFacing(facing);
            CompoundTag before = withoutAuraSides(savedState(tile, helper));
            helper.assertTrue(menu.clickMenuButton(player,
                            AuraSideConfigControls.DISABLE_ALL_BUTTON),
                    "Disable-all Aura action was refused");
            for (RelativeSide relative : RelativeSide.values()) {
                Direction absolute = relative.getDirection(tile.getDirection());
                Direction expectedAbsolute = switch (relative) {
                    case FRONT -> facing;
                    case BACK -> facing.getOpposite();
                    case LEFT -> facing.getClockWise();
                    case RIGHT -> facing.getCounterClockWise();
                    case TOP -> Direction.UP;
                    case BOTTOM -> Direction.DOWN;
                };
                helper.assertTrue(absolute == expectedAbsolute
                                && RelativeSide.fromDirections(facing, absolute) == relative,
                        "Relative Aura face did not round-trip for " + facing + "/" + relative);
                for (int step = 1; step <= 4; step++) {
                    helper.assertTrue(menu.clickMenuButton(player,
                                    AuraSideConfigControls.FORWARD_BUTTON_START + absolute.ordinal())
                                    && host.getAuraSideMode(absolute)
                                    == NatureAuraMachineBlockEntity.AuraSideMode.values()[step % 4],
                            "Forward Aura cycle failed for " + facing + "/" + relative);
                    verifyOnlyOneAuraSide(helper, tile, host, absolute);
                }
                for (int step = 1; step <= 4; step++) {
                    helper.assertTrue(menu.clickMenuButton(player,
                                    AuraSideConfigControls.REVERSE_BUTTON_START + absolute.ordinal())
                                    && host.getAuraSideMode(absolute)
                                    == NatureAuraMachineBlockEntity.AuraSideMode.values()[Math.floorMod(-step, 4)],
                            "Reverse Aura cycle failed for " + facing + "/" + relative);
                    verifyOnlyOneAuraSide(helper, tile, host, absolute);
                }
            }
            for (int step = 1; step <= 4; step++) {
                var target = AuraSideConfigControls.nextAllMode(host);
                helper.assertTrue(menu.clickMenuButton(player, AuraSideConfigControls.NEXT_ALL_BUTTON),
                        "Next-all Aura action was refused");
                for (Direction side : Direction.values()) {
                    helper.assertTrue(host.getAuraSideMode(side) == target,
                            "Uniform Aura sides did not advance together");
                }
            }
            host.setAuraSideMode(Direction.UP, NatureAuraMachineBlockEntity.AuraSideMode.INPUT);
            helper.assertTrue(menu.clickMenuButton(player, AuraSideConfigControls.NEXT_ALL_BUTTON),
                    "Mixed-side Aura reset was refused");
            for (Direction side : Direction.values()) {
                helper.assertTrue(host.getAuraSideMode(side) == NatureAuraMachineBlockEntity.AuraSideMode.NONE,
                        "Mixed Aura modes did not reset together");
            }
            for (int invalid : new int[]{509, 516, 519, 526, 532, Integer.MAX_VALUE}) {
                helper.assertTrue(!menu.clickMenuButton(player, invalid),
                        "Unknown Aura button was accepted: " + invalid);
            }
            helper.assertTrue(withoutAuraSides(savedState(tile, helper)).equals(before)
                            && host.getStoredAura() == stored && host.getAuraCapacity() == capacity,
                    "Aura side actions changed items, FE, progress, work modes, buffer or capacity");
        }

        var expected = new EnumMap<Direction,
                NatureAuraMachineBlockEntity.AuraSideMode>(Direction.class);
        for (Direction side : Direction.values()) {
            expected.put(side, NatureAuraMachineBlockEntity.AuraSideMode.values()[side.ordinal() % 4]);
        }
        host.setAuraSideModes(expected);
        CompoundTag persisted = savedState(tile, helper);
        menu.clickMenuButton(player, AuraSideConfigControls.DISABLE_ALL_BUTTON);
        tile.loadAdditional(persisted, helper.getLevel().registryAccess());
        for (Direction side : Direction.values()) {
            helper.assertTrue(host.getAuraSideMode(side) == expected.get(side),
                    "Aura side mode did not survive NBT reload: " + side);
        }
        helper.assertTrue(host.getStoredAura() == stored && host.getAuraCapacity() == capacity,
                "Aura side persistence changed the stored buffer or capacity");
    }

    private static CompoundTag savedState(TileEntityMekanism tile, GameTestHelper helper) {
        CompoundTag state = new CompoundTag();
        tile.saveAdditional(state, helper.getLevel().registryAccess());
        return state;
    }

    private static void verifyOnlyOneAuraSide(GameTestHelper helper,
                                             TileEntityMekanism tile,
                                             AuraSideModeHost host,
                                             Direction configured) {
        for (Direction other : Direction.values()) {
            if (other != configured) {
                helper.assertTrue(host.getAuraSideMode(other)
                                == NatureAuraMachineBlockEntity.AuraSideMode.NONE,
                        "Single-side Aura action changed another side");
            }
        }
        var mode = host.getAuraSideMode(configured);
        var aura = helper.getLevel().getCapability(
                NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                tile.getBlockPos(), configured);
        if (mode == NatureAuraMachineBlockEntity.AuraSideMode.NONE) {
            helper.assertTrue(aura == null, "Disabled Aura side still exposes its capability");
        } else {
            helper.assertTrue(aura != null
                            && (aura.storeAura(1, true) == 1) == mode.allowsInput()
                            && (aura.drainAura(1, true) == 1) == mode.allowsOutput(),
                    "Aura side capability did not follow the selected mode");
        }
    }

    private static CompoundTag withoutAuraSides(CompoundTag state) {
        state.remove("nature_aura_side_modes");
        state.getCompound("nature_altar_factory_aura").remove("nature_aura_side_modes");
        return state;
    }

    private static void verifyCatalystDirectSetGuard(
            GameTestHelper helper, TileEntityMekanism tile,
            List<NatureAuraRecipeBridge.AltarRecipeView> recipes,
            String description) {
        ItemStack catalyst = recipes.stream()
                .filter(NatureAuraRecipeBridge.AltarRecipeView
                        ::requiresCatalyst)
                .map(recipe -> representative(recipe.catalyst()))
                .filter(stack -> !stack.isEmpty())
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "Nature 41.9 exposed no catalyst recipe"));
        IInventorySlot persistent = ((IMekanismMagicAutomation) tile)
                .mekanismMagicPersistentInputs().getFirst();
        persistent.setStack(catalyst.copyWithCount(1));
        ExposedSlot exposed = findExposedSlot(tile, persistent);
        tile.setStackInSlot(exposed.index(), ItemStack.EMPTY,
                exposed.side());
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        persistent.getStack(), catalyst),
                description + " allowed an external direct-set clear of "
                        + "its persistent catalyst");
    }

    private static void verifyAeDecoderMixin(
            GameTestHelper helper,
            List<NatureAuraRecipeBridge.AltarRecipeView> recipes) {
        NatureAuraRecipeBridge.AltarRecipeView recipe = recipes.stream()
                .filter(candidate -> !representative(
                        candidate.input()).isEmpty())
                .findFirst().orElseThrow();
        ItemStack input = representative(recipe.input());
        ItemStack context = NatureAuraRecipeBridge.createPatternContext(
                helper.getLevel(), recipe.id());
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(generic(input, 1), generic(context, 1)),
                List.of(generic(recipe.output(),
                        recipe.output().getCount())));
        helper.assertTrue(PatternDetailsHelper.decodePattern(
                        encoded, helper.getLevel())
                        instanceof Ae2NatureAltarPattern,
                "AE2 Nature altar decoder mixin was not applied");
    }

    private static ExposedSlot findExposedSlot(
            TileEntityMekanism tile, IInventorySlot target) {
        for (Direction side : Direction.values()) {
            List<IInventorySlot> slots = tile.getInventorySlots(side);
            for (int index = 0; index < slots.size(); index++) {
                if (slots.get(index) == target) {
                    return new ExposedSlot(side, index);
                }
            }
        }
        List<IInventorySlot> unsided = tile.getInventorySlots(null);
        for (int index = 0; index < unsided.size(); index++) {
            if (unsided.get(index) == target) {
                return new ExposedSlot(null, index);
            }
        }
        throw new IllegalStateException(
                "Persistent catalyst was not exposed on any configured side");
    }

    private static long observedCatalogVersion(
            NatureAltarFactoryBlockEntity factory) {
        try {
            Field field = NatureAltarFactoryBlockEntity.class
                    .getDeclaredField("observedCatalogVersion");
            field.setAccessible(true);
            return field.getLong(factory);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "Unable to inspect Nature catalog refresh state", failure);
        }
    }

    private static RecipeCacheLookupMonitor<?> recipeMonitor(
            NatureAltarFactoryBlockEntity factory, int index) {
        Class<?> type = factory.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(
                        "recipeCacheLookupMonitors");
                field.setAccessible(true);
                Object value = field.get(factory);
                if (value instanceof RecipeCacheLookupMonitor<?>[] monitors
                        && index >= 0 && index < monitors.length
                        && monitors[index] != null) {
                    return monitors[index];
                }
                throw new IllegalStateException(
                        "Nature factory recipe monitor was unavailable");
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(
                        "Unable to inspect Nature recipe monitor", failure);
            }
        }
        throw new IllegalStateException(
                "Nature factory recipe monitor field was not found");
    }

    private static GenericStack generic(ItemStack stack, long amount) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null || amount <= 0) {
            throw new IllegalArgumentException("Invalid AE item stack");
        }
        return new GenericStack(key, amount);
    }

    private static ItemStack representative(Ingredient ingredient) {
        if (ingredient == null) {
            return ItemStack.EMPTY;
        }
        ItemStack[] choices = ingredient.getItems();
        return choices.length == 0 ? ItemStack.EMPTY
                : choices[0].copyWithCount(1);
    }

    private static <T extends BlockEntity> T require(
            GameTestHelper helper, BlockPos pos, Class<T> type) {
        BlockEntity blockEntity = helper.getBlockEntity(pos);
        helper.assertTrue(type.isInstance(blockEntity),
                "Expected " + type.getSimpleName() + " at " + pos);
        return type.cast(blockEntity);
    }

    private record ExposedSlot(Direction side, int index) {
    }
}
