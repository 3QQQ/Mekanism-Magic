package com.example.mekanismmagic.gametest;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import com.beipuo.mekenergistics.blockentity.support.AbstractMeAeSupport;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.ae2.Ae2IdentifierImbuementPattern;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeScanner;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierAssemblerBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierItem;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryLayout;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryStorage;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.mekextras.ExtraImbuementFactoryBlockEntity;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.attachments.containers.item.AttachedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Runtime regressions for identifier-aware Ars imbuement automation. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArsImbuementAutomationGameTests {
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY_POS = new BlockPos(2, 1, 1);
    private static final BlockPos FACTORY_POS = new BlockPos(3, 1, 1);
    private static final BlockPos EXTRA_FACTORY_POS = new BlockPos(5, 1, 1);
    private static final BlockPos OUTPUT_TARGET_POS = new BlockPos(1, 2, 1);
    private static final BlockPos AE_RELAY_POS = new BlockPos(3, 1, 1);
    private static final BlockPos SOURCE_DRIVE_POS = new BlockPos(4, 1, 1);
    private static final BlockPos ME_SOURCE_JAR_POS = new BlockPos(4, 2, 1);

    private ArsImbuementAutomationGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 200)
    public static void identifierPatternRequiresExactShape(
            GameTestHelper helper) {
        ImbuementCase recipe = imbuementCases(helper.getLevel(), 1).getFirst();
        ItemStack valid = encode(recipe,
                List.of(generic(recipe.input(), 1),
                        generic(recipe.identifier(), 1)),
                List.of(generic(recipe.output(),
                        recipe.output().getCount())));
        helper.assertTrue(PatternDetailsHelper.decodePattern(
                        valid, helper.getLevel())
                        instanceof Ae2IdentifierImbuementPattern,
                "A valid identifier imbuement pattern was rejected");
        ItemStack componentInput = recipe.input();
        CustomData.update(DataComponents.CUSTOM_DATA, componentInput,
                tag -> tag.putString("unrelated_test_data", "kept"));
        ItemStack cleaned = ArsNouveauRecipeBridge
                .clearPatternRecipeMarker(
                        ArsNouveauRecipeBridge.markPatternInput(
                                componentInput, recipe.id()));
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        componentInput, cleaned),
                "Clearing the AE recipe marker removed unrelated components");

        assertRejected(helper, recipe, List.of(
                        generic(recipe.input(), 2),
                        generic(recipe.identifier(), 1)),
                List.of(generic(recipe.output(), recipe.output().getCount())),
                "A reagent amount other than one was accepted");
        assertRejected(helper, recipe, List.of(
                        generic(recipe.input(), 1),
                        generic(new ItemStack(Items.DIRT), 1),
                        generic(recipe.identifier(), 1)),
                List.of(generic(recipe.output(), recipe.output().getCount())),
                "An extra physical input was accepted");
        assertRejected(helper, recipe, List.of(
                        generic(recipe.input(), 1),
                        generic(recipe.identifier(), 2)),
                List.of(generic(recipe.output(), recipe.output().getCount())),
                "A catalyst identifier amount other than one was accepted");
        assertRejected(helper, recipe, List.of(
                        generic(recipe.input(), 1),
                        generic(recipe.identifier(), 1)),
                List.of(generic(recipe.output(),
                                recipe.output().getCount() + 1L)),
                "An incorrect output amount was accepted");
        assertRejected(helper, recipe, List.of(
                        generic(recipe.input(), 1),
                        generic(recipe.identifier(), 1)),
                List.of(generic(recipe.output(), recipe.output().getCount()),
                        generic(new ItemStack(Items.DIRT), 1)),
                "An extra output was accepted");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 100)
    public static void catalystIdentifierAssemblerEjectsFromTop(
            GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, ArsNouveauRegistries
                .CATALYST_IDENTIFIER_ASSEMBLER_BLOCK.get());
        helper.setBlock(OUTPUT_TARGET_POS, Blocks.CHEST);
        BlockEntity machineEntity = helper.getBlockEntity(MACHINE_POS);
        BlockEntity targetEntity = helper.getBlockEntity(OUTPUT_TARGET_POS);
        helper.assertTrue(machineEntity
                        instanceof CatalystIdentifierAssemblerBlockEntity,
                "Catalyst identifier assembler block entity was not created");
        helper.assertTrue(targetEntity instanceof ChestBlockEntity,
                "Catalyst identifier output chest was not created");
        CatalystIdentifierAssemblerBlockEntity assembler =
                (CatalystIdentifierAssemblerBlockEntity) machineEntity;
        ChestBlockEntity target = (ChestBlockEntity) targetEntity;
        ItemStack identifier = ArsNouveauRecipeBridge
                .catalystIdentifierJeiStacks(helper.getLevel()).getFirst();
        assembler.mekanismMagicPatternOutputs().getFirst()
                .setStack(identifier.copy());

        helper.startSequence()
                .thenExecuteAfter(5, () -> helper.assertTrue(
                        java.util.stream.IntStream.range(0,
                                        target.getContainerSize())
                                .mapToObj(target::getItem)
                                .anyMatch(stack ->
                                        ItemStack.isSameItemSameComponents(
                                                stack, identifier)),
                        "Catalyst identifier assembler did not eject upward"))
                .thenExecute(() -> helper.assertTrue(assembler
                                .mekanismMagicPatternOutputs().getFirst()
                                .getStack().isEmpty(),
                        "Catalyst identifier remained in the assembler output"))
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 240)
    public static void arsMachinePullsNetworkSourceAndLinksMeJar(
            GameTestHelper helper) {
        if (!ModList.get().isLoaded("arseng")) {
            helper.succeed();
            return;
        }
        var sourceCell = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(
                        "arseng", "source_storage_cell_1k"));
        var meSourceJar = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(
                        "arseng", "me_source_jar"));
        helper.assertFalse(sourceCell == Items.AIR,
                "Ars Energistique Source cell is unavailable");
        helper.assertFalse(meSourceJar == Blocks.AIR,
                "Ars Energistique ME Source Jar is unavailable");

        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(AE_RELAY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(SOURCE_DRIVE_POS, AEBlocks.DRIVE.block());
        helper.setBlock(ME_SOURCE_JAR_POS, meSourceJar);
        BlockEntity machineEntity = helper.getBlockEntity(MACHINE_POS);
        BlockEntity driveEntity = helper.getBlockEntity(SOURCE_DRIVE_POS);
        helper.assertTrue(machineEntity instanceof ImbuementProcessorBlockEntity,
                "Imbuement processor block entity was not created");
        helper.assertTrue(driveEntity instanceof DriveBlockEntity,
                "AE Source drive block entity was not created");
        ImbuementProcessorBlockEntity processor =
                (ImbuementProcessorBlockEntity) machineEntity;
        DriveBlockEntity drive = (DriveBlockEntity) driveEntity;
        ItemStack remainder = drive.getInternalInventory().insertItem(
                0, new ItemStack(sourceCell), false);
        helper.assertTrue(remainder.isEmpty(),
                "Unable to install the Ars Energistique Source cell");

        final int seededSource = 25_000;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(drive.isPowered()
                                    && drive.getCellStatus(0)
                                    != CellState.ABSENT,
                            "AE Source storage cell did not join the grid");
                    ISourceCap jar = meSourceJarCapability(helper);
                    helper.assertTrue(jar != null
                                    && jar.receiveSource(1, true) == 1,
                            "ME Source Jar did not expose powered storage");
                })
                .thenExecute(() -> {
                    ISourceCap jar = meSourceJarCapability(helper);
                    helper.assertTrue(jar != null
                                    && jar.receiveSource(seededSource, false)
                                    == seededSource,
                            "ME Source Jar could not seed network Source");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        processor.getSource() > 0,
                        "AE-connected Ars machine did not pull network Source"))
                .thenExecute(() -> {
                    ISourceCap jar = meSourceJarCapability(helper);
                    helper.assertTrue(jar != null
                                    && processor.getSource() + jar.getSource()
                                    == seededSource,
                            "Direct AE Source refill did not conserve Source");
                    helper.setBlock(ENERGY_POS, Blocks.AIR);
                })
                .thenExecuteAfter(5, () -> {
                    processor.setSource(0);
                    helper.assertTrue(processor.mekanismMagicLinkSourceJar(
                                    helper.absolutePos(ME_SOURCE_JAR_POS)),
                            "Source link tool contract rejected the ME Source Jar");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        processor.getSource() > 0,
                        "Linked ME Source Jar did not refill a disconnected machine"))
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 200)
    public static void mekEImbuementFactoriesPullNetworkSource(
            GameTestHelper helper) {
        if (!ModList.get().isLoaded("arseng")) {
            helper.succeed();
            return;
        }
        var sourceCell = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(
                        "arseng", "source_storage_cell_1k"));
        var meSourceJar = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(
                        "arseng", "me_source_jar"));
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.ULTIMATE_IMBUEMENT_FACTORY_BLOCK.get());
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(FACTORY_POS,
                MekanismExtrasImbuementFactories.INFINITE_BLOCK.get());
        helper.setBlock(SOURCE_DRIVE_POS, AEBlocks.DRIVE.block());
        helper.setBlock(ME_SOURCE_JAR_POS, meSourceJar);
        BlockEntity standardEntity = helper.getBlockEntity(MACHINE_POS);
        BlockEntity extraEntity = helper.getBlockEntity(FACTORY_POS);
        BlockEntity driveEntity = helper.getBlockEntity(SOURCE_DRIVE_POS);
        helper.assertTrue(standardEntity instanceof ImbuementFactoryBlockEntity,
                "Standard imbuement factory was not created");
        helper.assertTrue(extraEntity instanceof ExtraImbuementFactoryBlockEntity,
                "Extras imbuement factory was not created");
        helper.assertTrue(driveEntity instanceof DriveBlockEntity,
                "AE Source drive was not created");
        ImbuementFactoryBlockEntity standard =
                (ImbuementFactoryBlockEntity) standardEntity;
        ExtraImbuementFactoryBlockEntity extra =
                (ExtraImbuementFactoryBlockEntity) extraEntity;
        DriveBlockEntity drive = (DriveBlockEntity) driveEntity;
        AbstractMeAeSupport<?> standardSupport = installPatternProvider(
                helper, standard);
        AbstractMeAeSupport<?> extraSupport = installPatternProvider(
                helper, extra);
        ItemStack remainder = drive.getInternalInventory().insertItem(
                0, new ItemStack(sourceCell), false);
        helper.assertTrue(remainder.isEmpty(),
                "Unable to install the factory Source storage cell");

        final int seededSource = 30_000;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        standardSupport.getMainNode().isActive()
                                && extraSupport.getMainNode().isActive()
                                && drive.isPowered()
                                && drive.getCellStatus(0) != CellState.ABSENT,
                        "MekE factory nodes did not join the Source grid"))
                .thenExecute(() -> {
                    ISourceCap jar = meSourceJarCapability(helper);
                    helper.assertTrue(jar != null
                                    && jar.receiveSource(seededSource, false)
                                    == seededSource,
                            "ME Source Jar could not seed factory storage");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        standard.getSource() > 0 && extra.getSource() > 0,
                        "MekE-connected imbuement factories did not pull Source"))
                .thenExecute(() -> {
                    ISourceCap jar = meSourceJarCapability(helper);
                    helper.assertTrue(jar != null
                                    && standard.getSource() + extra.getSource()
                                    + jar.getSource() == seededSource,
                            "Factory AE Source refill did not conserve Source");
                })
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 160)
    public static void drygmyPullsArsEngNetworkSource(
            GameTestHelper helper) {
        if (!ModList.get().isLoaded("arseng")) {
            helper.succeed();
            return;
        }
        var sourceCell = BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(
                        "arseng", "source_storage_cell_1k"));
        var meSourceJar = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath(
                        "arseng", "me_source_jar"));
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(AE_RELAY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(SOURCE_DRIVE_POS, AEBlocks.DRIVE.block());
        helper.setBlock(ME_SOURCE_JAR_POS, meSourceJar);
        BlockEntity machineEntity = helper.getBlockEntity(MACHINE_POS);
        BlockEntity driveEntity = helper.getBlockEntity(SOURCE_DRIVE_POS);
        helper.assertTrue(machineEntity instanceof DrygmySimulatorBlockEntity,
                "Drygmy simulator was not created");
        helper.assertTrue(driveEntity instanceof DriveBlockEntity,
                "Drygmy Source drive was not created");
        DrygmySimulatorBlockEntity drygmy =
                (DrygmySimulatorBlockEntity) machineEntity;
        DriveBlockEntity drive = (DriveBlockEntity) driveEntity;
        helper.assertTrue(drive.getInternalInventory().insertItem(
                        0, new ItemStack(sourceCell), false).isEmpty(),
                "Unable to install the Drygmy Source storage cell");

        final int seededSource = 12_000;
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        drive.isPowered()
                                && drive.getCellStatus(0) != CellState.ABSENT,
                        "Drygmy Source network did not become active"))
                .thenExecute(() -> {
                    ISourceCap jar = meSourceJarCapability(helper);
                    helper.assertTrue(jar != null
                                    && jar.receiveSource(seededSource, false)
                                    == seededSource,
                            "ME Source Jar could not seed Drygmy storage");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        drygmy.getSource() > 0,
                        "Drygmy did not pull Source through its native AE node"))
                .thenExecute(() -> {
                    ISourceCap jar = meSourceJarCapability(helper);
                    helper.assertTrue(jar != null
                                    && drygmy.getSource() + jar.getSource()
                                    == seededSource,
                            "Drygmy AE refill did not conserve Source");
                })
                .thenSucceed();
    }

    private static ISourceCap meSourceJarCapability(GameTestHelper helper) {
        return helper.getLevel().getCapability(
                CapabilityRegistry.SOURCE_CAPABILITY,
                helper.absolutePos(ME_SOURCE_JAR_POS), null);
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 160)
    public static void catalystLibraryTracksCatalogAndMigratesLegacySlots(
            GameTestHelper helper) {
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        helper.setBlock(FACTORY_POS,
                ArsNouveauRegistries.ULTIMATE_IMBUEMENT_FACTORY_BLOCK.get());
        helper.setBlock(EXTRA_FACTORY_POS,
                MekanismExtrasImbuementFactories.INFINITE_BLOCK.get());

        BlockEntity processorEntity = helper.getBlockEntity(MACHINE_POS);
        BlockEntity factoryEntity = helper.getBlockEntity(FACTORY_POS);
        BlockEntity extraEntity = helper.getBlockEntity(EXTRA_FACTORY_POS);
        helper.assertTrue(processorEntity
                        instanceof ImbuementProcessorBlockEntity,
                "Imbuement processor block entity was not created");
        helper.assertTrue(factoryEntity
                        instanceof ImbuementFactoryBlockEntity,
                "Ultimate imbuement factory block entity was not created");
        helper.assertTrue(extraEntity
                        instanceof ExtraImbuementFactoryBlockEntity,
                "Infinite imbuement factory block entity was not created");
        ImbuementProcessorBlockEntity processor =
                (ImbuementProcessorBlockEntity) processorEntity;
        ImbuementFactoryBlockEntity factory =
                (ImbuementFactoryBlockEntity) factoryEntity;
        ExtraImbuementFactoryBlockEntity extra =
                (ExtraImbuementFactoryBlockEntity) extraEntity;

        List<ItemStack> identifiers = ArsNouveauRecipeBridge
                .catalystIdentifierJeiStacks(helper.getLevel());
        helper.assertFalse(identifiers.isEmpty(),
                "Ars exposed no catalyst identifiers for library sizing");
        int windowSlots = NativeMagicMachineBlockEntity
                .CATALYST_LIBRARY_SLOT_COUNT;
        int expectedVisible = Math.max(1, identifiers.size());
        assertLibrarySizing(helper,
                processor.mekanismMagicPersistentInputs(),
                processor.catalystVisibleSlotCount(),
                processor.catalystPageCount(),
                processor.getInventorySlots(null).size(),
                expectedVisible, windowSlots, "Imbuement processor");
        assertLibrarySizing(helper,
                factory.mekanismMagicPersistentInputs(),
                factory.catalystVisibleSlotCount(),
                factory.catalystPageCount(),
                factory.getInventorySlots(null).size(),
                expectedVisible, windowSlots, "Ultimate imbuement factory");
        assertLibrarySizing(helper,
                extra.mekanismMagicPersistentInputs(),
                extra.catalystVisibleSlotCount(),
                extra.catalystPageCount(),
                extra.getInventorySlots(null).size(),
                expectedVisible, windowSlots, "Infinite imbuement factory");

        int legacySelectedIndex = NativeMagicMachineBlockEntity
                .LEGACY_CATALYST_LIBRARY_SLOT_COUNT - 1;
        CompoundTag standardLegacyTag = new CompoundTag();
        standardLegacyTag.putInt("catalyst_selected_index",
                legacySelectedIndex);
        factory.loadAdditional(standardLegacyTag,
                helper.getLevel().registryAccess());
        List<IInventorySlot> allFactorySlots = factory.getInventorySlots(null);
        int firstFactoryWindow = allFactorySlots.indexOf(factory
                .mekanismMagicPersistentInputs().getFirst());
        factory.applyInventorySlots(null, allFactorySlots,
                legacyLibraryAttachment(allFactorySlots.size(),
                        firstFactoryWindow, legacySelectedIndex,
                        identifiers.getFirst()));
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        factory.selectedCatalystIdentifier(),
                        identifiers.getFirst()),
                "Standard factory forgot its selected legacy catalyst");

        CompoundTag extraLegacyTag = new CompoundTag();
        extraLegacyTag.putInt("catalyst_selected_index",
                legacySelectedIndex);
        extra.loadAdditional(extraLegacyTag,
                helper.getLevel().registryAccess());
        List<IInventorySlot> allExtraSlots = extra.getInventorySlots(null);
        int firstExtraWindow = allExtraSlots.indexOf(extra
                .mekanismMagicPersistentInputs().getFirst());
        extra.applyInventorySlots(null, allExtraSlots,
                legacyLibraryAttachment(allExtraSlots.size(),
                        firstExtraWindow, legacySelectedIndex,
                        identifiers.getFirst()));
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        extra.selectedCatalystIdentifier(),
                        identifiers.getFirst()),
                "Extras factory forgot its selected legacy catalyst");

        List<IInventorySlot> processorLibrary = processor
                .mekanismMagicPersistentInputs();
        List<IInventorySlot> allProcessorSlots =
                processor.getInventorySlots(null);
        int firstWindowSlot = allProcessorSlots.indexOf(
                processorLibrary.getFirst());
        int legacySize = allProcessorSlots.size()
                - CatalystLibraryLayout.PAGE_SIZE
                + NativeMagicMachineBlockEntity
                .LEGACY_CATALYST_LIBRARY_SLOT_COUNT;
        int legacyTailIndex = firstWindowSlot
                + NativeMagicMachineBlockEntity
                .LEGACY_CATALYST_LIBRARY_SLOT_COUNT - 1;
        int legacyOutputIndex = firstWindowSlot
                + NativeMagicMachineBlockEntity
                .LEGACY_CATALYST_LIBRARY_SLOT_COUNT;
        helper.assertTrue(firstWindowSlot >= 0
                        && legacyOutputIndex < legacySize,
                "The processor window cannot map the released slot layout");
        List<ItemStack> legacyItems = new ArrayList<>(legacySize);
        for (int index = 0; index < legacySize; index++) {
            legacyItems.add(ItemStack.EMPTY);
        }
        legacyItems.set(legacyTailIndex, identifiers.getFirst().copy());
        legacyItems.set(legacyOutputIndex, new ItemStack(Items.DIAMOND));
        processor.applyInventorySlots(null, allProcessorSlots,
                new AttachedItems(List.copyOf(legacyItems)));
        int migratedVisible = Math.max(expectedVisible,
                NativeMagicMachineBlockEntity
                        .LEGACY_CATALYST_LIBRARY_SLOT_COUNT);
        helper.assertTrue(processor.catalystVisibleSlotCount()
                        == migratedVisible,
                "A populated legacy tail slot became unreachable");
        processor.cycleCatalystPage(1);
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                        processorLibrary.get(13).getStack(),
                        identifiers.getFirst()),
                "The released library's final catalyst slot was lost");
        helper.assertTrue(processor.mekanismMagicPatternOutputs().getFirst()
                        .getStack().is(Items.DIAMOND),
                "The legacy processor output moved into a page window");
        processorLibrary.get(13).setStack(ItemStack.EMPTY);
        helper.assertTrue(processor.catalystVisibleSlotCount()
                        == expectedVisible,
                "Clearing a migrated tail did not shrink to recipe count");

        CatalystLibraryStorage sparse = new CatalystLibraryStorage();
        sparse.set(257, new ItemStack(Items.NETHER_STAR));
        CompoundTag encoded = sparse.serializeNBT(
                helper.getLevel().registryAccess());
        CatalystLibraryStorage decoded = new CatalystLibraryStorage();
        decoded.deserializeNBT(helper.getLevel().registryAccess(), encoded);
        helper.assertTrue(decoded.retainedSlotCount() == 258,
                "A catalyst entry beyond Mekanism's byte slot range was lost");
        helper.assertTrue(decoded.pageCount(expectedVisible) == 17,
                "The dynamic catalyst tail exposed the wrong page count");
        helper.assertTrue(decoded.get(257).is(Items.NETHER_STAR),
                "Sparse catalyst storage did not preserve its high index");
        decoded.set(257, ItemStack.EMPTY);
        helper.assertTrue(decoded.visibleSlotCount(expectedVisible)
                        == expectedVisible,
                "Clearing a sparse catalyst tail did not shrink to recipes");
        helper.succeed();
    }

    private static AttachedItems legacyLibraryAttachment(
            int currentSlotCount, int firstWindowSlot,
            int libraryIndex, ItemStack stack) {
        int legacySize = currentSlotCount - CatalystLibraryLayout.PAGE_SIZE
                + NativeMagicMachineBlockEntity
                .LEGACY_CATALYST_LIBRARY_SLOT_COUNT;
        List<ItemStack> items = new ArrayList<>(legacySize);
        for (int index = 0; index < legacySize; index++) {
            items.add(ItemStack.EMPTY);
        }
        items.set(firstWindowSlot + libraryIndex, stack.copy());
        return new AttachedItems(List.copyOf(items));
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 300)
    public static void failedMekEPushDoesNotSwitchCatalyst(
            GameTestHelper helper) {
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.ULTIMATE_IMBUEMENT_FACTORY_BLOCK.get());
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        BlockEntity blockEntity = helper.getBlockEntity(MACHINE_POS);
        helper.assertTrue(blockEntity instanceof ImbuementFactoryBlockEntity,
                "Imbuement factory block entity was not created");
        ImbuementFactoryBlockEntity factory =
                (ImbuementFactoryBlockEntity) blockEntity;
        AbstractMeAeSupport<?> support = installPatternProvider(
                helper, blockEntity);
        List<ImbuementCase> recipes = imbuementCases(helper.getLevel(), 2);
        ImbuementCase initial = recipes.get(0);
        ImbuementCase target = recipes.get(1);
        helper.assertTrue(factory.selectCatalystIdentifierForRecipe(
                        initial.id()),
                "Unable to select the initial catalyst identifier");

        ItemStack encoded = encode(target,
                List.of(generic(target.input(), 1),
                        generic(target.identifier(), 1)),
                List.of(generic(target.output(),
                        target.output().getCount())));
        support.getPatternSlots().getFirst().setStack(encoded);
        support.updatePatterns();
        IPatternDetails pattern = PatternDetailsHelper.decodePattern(
                encoded, helper.getLevel());
        helper.assertTrue(pattern instanceof Ae2IdentifierImbuementPattern,
                "Target imbuement pattern did not use the custom decoder");
        List<IInventorySlot> inputSlots =
                ((IMekanismMagicAutomation) factory)
                        .mekanismMagicPatternInputs();
        helper.assertFalse(inputSlots.isEmpty(),
                "Imbuement factory exposed no MekE input lanes");

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        support.getMainNode().isActive(),
                        "Imbuement factory MekE node did not become active"))
                .thenExecute(() -> {
                    inputSlots.getFirst().setStack(initial.input());
                    boolean contextRejected = support.pushPatternWithAdapter(
                            pattern, counters(target.input(), 1));
                    helper.assertFalse(contextRejected,
                            "MekE switched a shared catalyst while another "
                                    + "lane still owned input");
                    helper.assertTrue(matchesCatalyst(
                                    factory.selectedCatalystIdentifier(),
                                    initial),
                            "A rejected cross-catalyst push changed the "
                                    + "shared selection");
                    inputSlots.forEach(slot -> slot.setStack(ItemStack.EMPTY));

                    inputSlots.forEach(slot -> slot.setStack(
                            new ItemStack(Items.COBBLESTONE, 64)));
                    boolean rejected = support.pushPatternWithAdapter(
                            pattern, counters(target.input(), 1));
                    helper.assertFalse(rejected,
                            "MekE accepted a push with no available input lane");
                    helper.assertTrue(matchesCatalyst(
                                    factory.selectedCatalystIdentifier(),
                                    initial),
                            "A failed MekE push changed the catalyst selection");

                    inputSlots.forEach(slot -> slot.setStack(ItemStack.EMPTY));
                    boolean accepted = support.pushPatternWithAdapter(
                            pattern, counters(target.input(), 1));
                    helper.assertTrue(accepted,
                            "MekE rejected a valid imbuement push");
                    helper.assertTrue(matchesCatalyst(
                                    factory.selectedCatalystIdentifier(),
                                    target),
                            "A successful MekE push did not commit its catalyst");
                    helper.assertTrue(inputSlots.stream().anyMatch(slot ->
                                    ItemStack.isSameItemSameComponents(
                                            slot.getStack(), target.input())
                                            && slot.getStack().getCount() == 1),
                            "A successful MekE push did not route its reagent");
                })
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 160)
    public static void markedInputChangesOnlyUncontestedContext(
            GameTestHelper helper) {
        helper.setBlock(MACHINE_POS,
                ArsNouveauRegistries.ULTIMATE_IMBUEMENT_FACTORY_BLOCK.get());
        BlockEntity blockEntity = helper.getBlockEntity(MACHINE_POS);
        helper.assertTrue(blockEntity instanceof ImbuementFactoryBlockEntity,
                "Imbuement factory block entity was not created");
        ImbuementFactoryBlockEntity factory =
                (ImbuementFactoryBlockEntity) blockEntity;
        List<ImbuementCase> recipes = imbuementCases(helper.getLevel(), 2);
        ImbuementCase initial = recipes.get(0);
        ImbuementCase target = recipes.get(1);
        helper.assertTrue(factory.selectCatalystIdentifierForRecipe(
                        initial.id()),
                "Unable to select initial physical-provider context");
        List<IInventorySlot> inputs = ((IMekanismMagicAutomation) factory)
                .mekanismMagicPatternInputs();
        helper.assertTrue(inputs.size() >= 2,
                "Factory exposed fewer than two input lanes");
        inputs.get(0).setStack(initial.input());
        inputs.get(1).setStack(ArsNouveauRecipeBridge.markPatternInput(
                target.input(), target.id()));

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(matchesCatalyst(
                                    factory.selectedCatalystIdentifier(),
                                    initial),
                            "A marked lane displaced an uncontested manual "
                                    + "lane's catalyst context");
                    inputs.get(0).setStack(ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> helper.assertTrue(matchesCatalyst(
                                factory.selectedCatalystIdentifier(), target),
                        "Marked input did not adopt its context after the "
                                + "conflicting lane was cleared"))
                .thenSucceed();
    }

    private static boolean matchesCatalyst(
            ItemStack selected, ImbuementCase recipe) {
        return CatalystIdentifierItem.matchesCatalystId(
                selected, recipe.catalystId());
    }

    private static void assertLibrarySizing(
            GameTestHelper helper, List<IInventorySlot> slots,
            int visibleSlots, int pageCount, int totalMachineSlots,
            int expectedVisible, int expectedCapacity,
            String description) {
        helper.assertTrue(slots.size() == expectedCapacity,
                description + " reserved " + slots.size()
                        + " catalyst slots instead of " + expectedCapacity);
        helper.assertTrue(visibleSlots == expectedVisible,
                description + " exposed " + visibleSlots
                        + " slots for " + expectedVisible
                        + " catalog entries");
        helper.assertTrue(pageCount == Math.ceilDiv(
                        expectedVisible, CatalystLibraryLayout.PAGE_SIZE),
                description + " computed an incorrect page count");
        helper.assertTrue(totalMachineSlots <= Byte.MAX_VALUE,
                description + " exceeded Mekanism's serialized slot index "
                        + "limit: " + totalMachineSlots);
    }

    private static void assertRejected(
            GameTestHelper helper, ImbuementCase recipe,
            List<GenericStack> inputs, List<GenericStack> outputs,
            String message) {
        ItemStack encoded = encode(recipe, inputs, outputs);
        helper.assertTrue(PatternDetailsHelper.decodePattern(
                        encoded, helper.getLevel()) == null,
                message);
    }

    private static ItemStack encode(
            ImbuementCase recipe, List<GenericStack> inputs,
            List<GenericStack> outputs) {
        ItemStack encoded = PatternDetailsHelper.encodeProcessingPattern(
                inputs, outputs);
        if (encoded.isEmpty()) {
            throw new IllegalStateException(
                    "AE2 could not encode imbuement recipe " + recipe.id());
        }
        return encoded;
    }

    private static AbstractMeAeSupport<?> installPatternProvider(
            GameTestHelper helper, BlockEntity blockEntity) {
        helper.assertTrue(blockEntity instanceof MeUpgradeRecipeMachineAdapter,
                "MekE adapter mixin was not applied to the Ars machine");
        MeUpgradeRecipeMachineAdapter adapter =
                (MeUpgradeRecipeMachineAdapter) blockEntity;
        helper.assertTrue(adapter.isMeUpgradeTarget(),
                "Ars machine was not registered as a MekE target");
        var installed = adapter.getMeUpgradeContainer()
                .install(MeUpgradeType.PATTERN_PROVIDER);
        helper.assertTrue(installed.successful(),
                "Unable to install MekE pattern provider: " + installed);
        adapter.onMeUpgradeStateChanged();
        adapter.createMeNodeIfActive();
        return adapter.getOrCreateMeUpgradeRuntime().support();
    }

    private static List<ImbuementCase> imbuementCases(
            Level level, int required) {
        List<ImbuementCase> result = new ArrayList<>();
        for (RecipeHolder<ImbuementRecipe> holder :
                ArsNouveauRecipeScanner.scan(level.getRecipeManager())) {
            if (holder.value().getPedestalItems().isEmpty()) {
                continue;
            }
            ItemStack input = ArsNouveauRecipeBridge.representativeChoice(
                    holder.value().getInput());
            ItemStack output = holder.value().getResultItem(
                    level.registryAccess()).copy();
            ItemStack identifier = ArsNouveauRecipeBridge
                    .createPatternIdentifierForRecipe(level, holder.id());
            ResourceLocation catalystId = CatalystIdentifierItem.catalystId(
                    identifier);
            if (input.isEmpty() || output.isEmpty() || identifier.isEmpty()
                    || result.stream().anyMatch(existing ->
                    existing.catalystId().equals(catalystId))) {
                continue;
            }
            result.add(new ImbuementCase(holder.id(), input, identifier,
                    output, catalystId));
            if (result.size() >= required) {
                return List.copyOf(result);
            }
        }
        throw new IllegalStateException("Expected " + required
                + " distinct catalyst imbuement recipes, found "
                + result.size());
    }

    private static GenericStack generic(ItemStack stack, long amount) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null || amount <= 0L) {
            throw new IllegalArgumentException("Invalid AE item stack");
        }
        return new GenericStack(key, amount);
    }

    private static KeyCounter[] counters(ItemStack stack, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(AEItemKey.of(stack), amount);
        return new KeyCounter[]{counter};
    }

    private record ImbuementCase(
            ResourceLocation id, ItemStack input, ItemStack identifier,
            ItemStack output, ResourceLocation catalystId) {
        private ImbuementCase {
            input = input.copyWithCount(1);
            identifier = identifier.copyWithCount(1);
            output = output.copy();
        }

        @Override
        public ItemStack input() {
            return input.copy();
        }

        @Override
        public ItemStack identifier() {
            return identifier.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }
    }
}
