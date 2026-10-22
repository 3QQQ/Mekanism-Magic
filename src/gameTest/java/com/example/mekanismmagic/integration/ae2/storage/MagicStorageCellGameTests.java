package com.example.mekanismmagic.integration.ae2.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.common.resource.EssenceResources;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Lossless storage boundaries, registered codecs and a real powered ME drive. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MagicStorageCellGameTests {
    private static final ResourceLocation SOURCE = ResourceLocation.fromNamespaceAndPath(
            "ars_nouveau", "source");
    private static final ResourceLocation AURA = ResourceLocation.fromNamespaceAndPath(
            "naturesaura", "overworld");
    private static final ResourceLocation DORMANT = ResourceLocation.fromNamespaceAndPath(
            "missing_magic_test", "unloaded_resource");
    private static final BlockPos ENERGY_POS = new BlockPos(1, 1, 1);
    private static final BlockPos DRIVE_POS = new BlockPos(1, 2, 1);

    private MagicStorageCellGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void auraVisualColorsAreNativeAndDoNotTurnResourcesIntoItems(GameTestHelper helper) {
        for (var entry : de.ellpeck.naturesaura.api.NaturesAuraAPI.AURA_TYPES.entrySet()) {
            helper.assertTrue(java.util.Objects.equals(MagicAuraResources.color(entry.getKey()), entry.getValue().getColor()),
                    "Resource display lost native Aura type color");
            AEKey key = MagicResourceKeys.key(entry.getKey());
            helper.assertTrue(key instanceof MagicResourceKey && !(key instanceof AEItemKey) && !(key instanceof AEFluidKey),
                    "Aura rendering converted the stored resource into an item or fluid");
            helper.assertTrue(MagicResourceKeys.resourceId(key).equals(entry.getKey()), "Aura resource identity changed");
        }
        helper.assertTrue(MagicAuraResources.color(DORMANT) == null, "Missing Aura type must not be assigned another type's color");
        AEKey source = requiredKey(helper, SOURCE);
        helper.assertTrue(!(source instanceof AEItemKey) && !(source instanceof AEFluidKey)
                        && MagicResourceKeys.resourceId(source).equals(SOURCE), "Source resource identity changed");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 40)
    public static void fourEssenceKeysKeepOldDiskDataAndNeverBecomeItemsOrFluids(
            GameTestHelper helper) {
        String[] icons = {"aureal_bottle", "soul",
                "blood_test_tube", "xpetrified_orb"};
        ItemStack disk = cellStack(helper);
        MagicCellContents old = new MagicCellContents(
                Map.of(SOURCE, 17L, AURA, 23L), List.of());
        disk.set(MagicStorageCells.CONTENTS, old);
        MagicStorageCellInventory cell = inventory(disk);
        for (int channel = 0; channel < EssenceResources.CHANNELS;
             channel++) {
            ResourceLocation id = EssenceResources.IDS.get(channel);
            AEKey key = MagicResourceKeys.essence(channel);
            helper.assertTrue(key instanceof MagicResourceKey
                            && !(key instanceof AEItemKey)
                            && !(key instanceof AEFluidKey)
                            && id.equals(MagicResourceKeys.resourceId(key))
                            && MagicResourceKeys.essenceChannel(id)
                            == channel
                            && MagicResourceKeys.icon(id).equals(
                            ResourceLocation.fromNamespaceAndPath(
                                    "forbidden_arcanus", icons[channel]))
                            && MagicResourceKeys.resources().contains(id)
                            == ModList.get().isLoaded("forbidden_arcanus"),
                    "F&A essence key was aliased to an item/fluid or lost its native icon");
            helper.assertTrue(insert(cell, key, 11L * (channel + 1),
                            Actionable.MODULATE) == 11L * (channel + 1),
                    "Magic disk rejected typed essence channel " + channel);
            ItemStack marker = new ItemStack(MagicStorageCells.MARKER.get());
            MagicResourceMarkerItem.select(marker, id);
            GenericStack ghost = GenericStack.unwrapItemStack(marker);
            helper.assertTrue(ghost != null
                            && key.equals(ghost.what())
                            && ghost.amount() == 0L,
                    "Essence partition marker minted stored points");
        }
        ItemStack restored = roundTrip(helper, disk);
        MagicCellContents saved = restored.get(MagicStorageCells.CONTENTS);
        helper.assertTrue(saved.amounts().get(SOURCE) == 17L
                        && saved.amounts().get(AURA) == 23L
                        && saved.amounts().size() == 6,
                "Adding essence keys changed historical Source/Aura balances");
        for (int channel = 0; channel < EssenceResources.CHANNELS;
             channel++) {
            ResourceLocation id = EssenceResources.IDS.get(channel);
            helper.assertTrue(saved.amounts().get(id)
                            == 11L * (channel + 1)
                            && extract(inventory(restored),
                            MagicResourceKeys.essence(channel),
                            1L, Actionable.SIMULATE) == 1L,
                    "Typed essence did not round-trip its own disk amount");
        }
        helper.assertTrue(MagicResourceKeys.essence(-1) == null
                        && MagicResourceKeys.essence(4) == null
                        && MagicResourceKeys.essenceChannel(DORMANT) == -1,
                "An invalid or dormant resource became a live essence key");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void simulationsDoNotWriteOrNotifySaveProvider(GameTestHelper helper) {
        ItemStack stack = cellStack(helper);
        MagicCellContents original = new MagicCellContents(Map.of(SOURCE, 17L), List.of());
        stack.set(MagicStorageCells.CONTENTS, original);
        AtomicInteger saves = new AtomicInteger();
        MagicStorageCellInventory cell = new MagicStorageCellInventory(
                (MagicStorageCellItem) stack.getItem(), stack, saves::incrementAndGet);
        AEKey source = requiredKey(helper, SOURCE);

        for (int attempt = 0; attempt < 12; attempt++) {
            helper.assertTrue(insert(cell, source, Long.MAX_VALUE, Actionable.SIMULATE)
                            == 1_023_983,
                    "Source simulation did not report the exact remaining 1K capacity");
            helper.assertTrue(extract(cell, source, Long.MAX_VALUE, Actionable.SIMULATE) == 17,
                    "Source extraction simulation changed its stored amount");
        }
        for (long amount : new long[]{0, -1, Long.MIN_VALUE}) {
            helper.assertTrue(insert(cell, source, amount, Actionable.MODULATE) == 0
                            && extract(cell, source, amount, Actionable.MODULATE) == 0
                            && insert(cell, source, amount, Actionable.SIMULATE) == 0
                            && extract(cell, source, amount, Actionable.SIMULATE) == 0,
                    "A non-positive transfer amount changed magic storage");
        }
        cell.persist();
        helper.assertTrue(saves.get() == 0 && cell.storedAmount(SOURCE) == 17
                        && stack.get(MagicStorageCells.CONTENTS) == original,
                "Simulation notified the save provider, dirtied or replaced the component");

        helper.assertTrue(insert(cell, source, 23, Actionable.MODULATE) == 23
                        && saves.get() == 1 && cell.storedAmount(SOURCE) == 40,
                "Accepted insertion did not notify its save provider exactly once");
        cell.persist();
        helper.assertTrue(stack.get(MagicStorageCells.CONTENTS).amounts().get(SOURCE) == 40
                        && original.amounts().get(SOURCE) == 17,
                "Persist mutated an already installed immutable contents value");
        helper.assertTrue(extract(cell, source, Long.MAX_VALUE, Actionable.MODULATE) == 40
                        && saves.get() == 2,
                "Extraction did not remove the exact Source amount and request a save");
        cell.persist();
        helper.assertTrue(cell.canFitInsideCell()
                        && MagicStorageCellItem.contents(stack).amounts().isEmpty(),
                "Emptied cell retained phantom persisted contents");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void mixedResourcesKeepIndependentPartialBytes(GameTestHelper helper) {
        ItemStack stack = cellStack(helper);
        MagicStorageCellInventory cell = inventory(stack);
        AEKey source = requiredKey(helper, SOURCE);
        AEKey aura = requiredKey(helper, AURA);
        helper.assertTrue(insert(cell, source, 1_022_001, Actionable.MODULATE) == 1_022_001
                        && insert(cell, aura, 1, Actionable.MODULATE) == 1
                        && cell.usedBytes() == 1024,
                "Mixed Source and Aura combined their independent partial bytes");
        helper.assertTrue(insert(cell, source, Long.MAX_VALUE, Actionable.SIMULATE) == 999
                        && insert(cell, aura, Long.MAX_VALUE, Actionable.SIMULATE) == 999,
                "Full byte pool lost space inside an already allocated resource byte");
        helper.assertTrue(insert(cell, source, Long.MAX_VALUE, Actionable.MODULATE) == 999
                        && insert(cell, aura, Long.MAX_VALUE, Actionable.MODULATE) == 999,
                "Partial-byte insertion accepted too much or discarded the remaining tail");
        helper.assertTrue(insert(cell, source, 1, Actionable.MODULATE) == 0
                        && insert(cell, aura, 1, Actionable.MODULATE) == 0,
                "Completely full mixed cell accepted additional magic");
        helper.assertTrue(extract(cell, source, 1, Actionable.MODULATE) == 1
                        && cell.usedBytes() == 1024
                        && insert(cell, aura, 1, Actionable.MODULATE) == 0,
                "Removing a Source unit incorrectly released a byte for Aura");
        helper.assertTrue(extract(cell, source, 999, Actionable.MODULATE) == 999
                        && cell.usedBytes() == 1023
                        && insert(cell, aura, Long.MAX_VALUE, Actionable.MODULATE) == 1000,
                "A fully freed Source byte was not reusable by Aura");
        helper.assertTrue(cell.storedAmount(SOURCE) == 1_022_000
                        && cell.storedAmount(AURA) == 2000
                        && cell.getAvailableStacks().size() == 2
                        && cell.getAvailableStacks().get(source) == 1_022_000
                        && cell.getAvailableStacks().get(aura) == 2000,
                "Mixed-cell enumeration converted or duplicated magic resources");
        ItemStack restored = roundTrip(helper, stack);
        helper.assertTrue(inventory(restored).storedAmount(SOURCE) == 1_022_000
                        && inventory(restored).storedAmount(AURA) == 2000,
                "Cell without a save provider failed to persist accepted transfers immediately");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void workbenchFiltersSurviveItemSaveAndStillAllowExtraction(GameTestHelper helper) {
        ItemStack stack = cellStack(helper);
        stack.set(MagicStorageCells.CONTENTS,
                new MagicCellContents(Map.of(SOURCE, 37L, AURA, 49L), List.of()));
        MagicStorageCellItem item = (MagicStorageCellItem) stack.getItem();
        AEKey source = requiredKey(helper, SOURCE);
        AEKey aura = requiredKey(helper, AURA);
        ConfigInventory config = item.getConfigInventory(stack);
        helper.assertTrue(config.isAllowedIn(0, source) && config.isAllowedIn(0, aura)
                        && !config.isAllowedIn(0, AEItemKey.of(Items.STONE))
                        && !config.isAllowedIn(0, AEFluidKey.of(Fluids.WATER)),
                "Workbench did not restrict its partition slots to supported magic keys");
        config.setStack(0, new GenericStack(source, 1));
        ItemStack restored = roundTrip(helper, stack);
        helper.assertTrue(restored.get(MagicStorageCells.CONTENTS).filter().equals(List.of(SOURCE))
                        && item.getConfigInventory(restored).getKey(0).equals(source),
                "Workbench partition did not survive the registered item-component codec");
        MagicStorageCellInventory cell = inventory(restored);
        helper.assertTrue(insert(cell, aura, 1, Actionable.MODULATE) == 0
                        && insert(cell, source, 1, Actionable.MODULATE) == 1
                        && insert(cell, AEItemKey.of(Items.STONE), 1, Actionable.MODULATE) == 0
                        && insert(cell, AEFluidKey.of(Fluids.WATER), 1000, Actionable.MODULATE) == 0,
                "Partitioned magic cell accepted an excluded resource, item or fluid");
        helper.assertTrue(extract(cell, aura, Long.MAX_VALUE, Actionable.MODULATE) == 49
                        && cell.storedAmount(SOURCE) == 38 && cell.storedAmount(AURA) == 0,
                "New partition trapped old Aura or changed the unrelated Source amount");
        helper.assertTrue(stack.get(MagicStorageCells.CONTENTS).amounts().get(SOURCE) == 37
                        && stack.get(MagicStorageCells.CONTENTS).amounts().get(AURA) == 49,
                "Editing a restored cell mutated the original stack's component");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void dormantResourcesReserveSpaceAndPreventCellNesting(GameTestHelper helper) {
        helper.assertTrue(MagicResourceKeys.key(DORMANT) == null,
                "Dormant test resource unexpectedly resolves to a live AE key");
        ItemStack stack = cellStack(helper);
        MagicCellContents expected = new MagicCellContents(Map.of(DORMANT, 1001L), List.of());
        stack.set(MagicStorageCells.CONTENTS, expected);
        MagicStorageCellInventory cell = inventory(roundTrip(helper, stack));
        AEKey source = requiredKey(helper, SOURCE);
        helper.assertTrue(cell.usedBytes() == 2 && cell.storedAmount(DORMANT) == 1001
                        && cell.getAvailableStacks().isEmpty() && !cell.canFitInsideCell(),
                "Unavailable magic was exposed, discarded, or treated as an empty cell");
        helper.assertTrue(insert(cell, source, Long.MAX_VALUE, Actionable.SIMULATE) == 1_022_000,
                "Dormant resource stopped occupying its two storage bytes");
        StorageCell outer = StorageCells.getCellInventory(AEItems.ITEM_CELL_1K.stack(), null);
        helper.assertTrue(outer != null
                        && insert(outer, AEItemKey.of(stack), 1, Actionable.MODULATE) == 0,
                "AE's real item cell accepted a magic cell containing unavailable resources");
        helper.assertTrue(expected.equals(roundTrip(helper, stack).get(MagicStorageCells.CONTENTS)),
                "Item NBT roundtrip silently deleted unavailable magic");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void dormantTypesStillCountTowardTheTypeLimit(GameTestHelper helper) {
        Map<ResourceLocation, Long> resources = new LinkedHashMap<>();
        for (int index = 0; index < 63; index++) {
            resources.put(ResourceLocation.fromNamespaceAndPath(
                    "missing_magic_test", "resource_" + index), 1L);
        }
        ItemStack stack = cellStack(helper);
        stack.set(MagicStorageCells.CONTENTS, new MagicCellContents(resources, List.of()));
        MagicStorageCellInventory cell = inventory(roundTrip(helper, stack));
        AEKey source = requiredKey(helper, SOURCE);
        helper.assertTrue(cell.usedBytes() == 63
                        && insert(cell, source, 1, Actionable.MODULATE) == 0,
                "A sixty-fourth resource bypassed the persisted type limit");
        resources.remove(ResourceLocation.fromNamespaceAndPath("missing_magic_test", "resource_62"));
        stack.set(MagicStorageCells.CONTENTS, new MagicCellContents(resources, List.of()));
        helper.assertTrue(insert(inventory(stack), source, 1, Actionable.MODULATE) == 1,
                "Sixty-two dormant resource types incorrectly blocked the final free type");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void oversizedDormantCountsRemainLosslessAndCannotCreateSpace(GameTestHelper helper) {
        ItemStack stack = cellStack(helper);
        Map<ResourceLocation, Long> amounts = Map.of(DORMANT, Long.MAX_VALUE, SOURCE, 11L);
        stack.set(MagicStorageCells.CONTENTS, new MagicCellContents(amounts, List.of(DORMANT)));
        ItemStack restored = roundTrip(helper, stack);
        helper.assertTrue(restored.get(MagicStorageCells.CONTENTS).amounts().equals(amounts),
                "The registered component codec truncated an unavailable long quantity");
        // Opening the workbench must not rewrite an invisible saved filter.
        MagicCellContents beforeWorkbench = restored.get(MagicStorageCells.CONTENTS);
        ((MagicStorageCellItem) restored.getItem()).getConfigInventory(restored);
        helper.assertTrue(restored.get(MagicStorageCells.CONTENTS).equals(beforeWorkbench),
                "Opening the workbench cleared a dormant resource partition");
        MagicStorageCellInventory cell = inventory(restored);
        AEKey source = requiredKey(helper, SOURCE);
        ItemStack unpartitioned = restored.copy();
        unpartitioned.set(MagicStorageCells.CONTENTS,
                beforeWorkbench.withFilter(List.of()));
        helper.assertTrue(cell.usedBytes() > 1024 && !cell.canFitInsideCell()
                        && insert(inventory(unpartitioned), source, Long.MAX_VALUE, Actionable.MODULATE) == 0,
                "Oversized persisted quantity overflowed into free cell capacity");
        helper.assertTrue(extract(cell, source, Long.MAX_VALUE, Actionable.MODULATE) == 11,
                "An over-capacity cell prevented recovery of an available resource");
        helper.assertTrue(restored.get(MagicStorageCells.CONTENTS).amounts().equals(
                        Map.of(DORMANT, Long.MAX_VALUE))
                        && restored.get(MagicStorageCells.CONTENTS).filter().equals(List.of(DORMANT)),
                "Recovering Source changed dormant contents or its saved filter");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 40)
    public static void registeredMagicKeysRoundTripAndUseTheCanonicalSource(GameTestHelper helper) {
        AEKey source = requiredKey(helper, SOURCE);
        AEKey aura = requiredKey(helper, AURA);
        helper.assertTrue(source == MagicResourceKeys.source() && !source.equals(aura),
                "Canonical Source resolver is unstable or aliases Aura");
        for (AEKey key : List.of(source, aura)) {
            AEKey nbtCopy = AEKey.fromTagGeneric(helper.getLevel().registryAccess(),
                    key.toTagGeneric(helper.getLevel().registryAccess()));
            helper.assertTrue(key.equals(nbtCopy)
                            && key.getPrimaryKey() == nbtCopy.getPrimaryKey()
                            && MagicResourceKeys.resourceId(key).equals(MagicResourceKeys.resourceId(nbtCopy)),
                    "Registered AE key NBT codec changed a magic resource identity");
            KeyCounter nbtCounter = new KeyCounter();
            nbtCounter.add(key, 17);
            helper.assertTrue(nbtCounter.get(nbtCopy) == 17,
                    "NBT-decoded magic key cannot find the original key's AE counter balance");
            nbtCounter.add(nbtCopy, 23);
            helper.assertTrue(nbtCounter.get(key) == 40 && nbtCounter.size() == 1,
                    "NBT-decoded magic key created a second AE counter balance for the same resource");
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                    helper.getLevel().registryAccess());
            try {
                AEKey.writeKey(buffer, key);
                AEKey packetCopy = AEKey.readKey(buffer);
                helper.assertTrue(key.equals(packetCopy)
                                && key.getPrimaryKey() == packetCopy.getPrimaryKey()
                                && MagicResourceKeys.resourceId(key).equals(MagicResourceKeys.resourceId(packetCopy)),
                        "Registered AE key packet codec changed a magic resource identity");
                KeyCounter packetCounter = new KeyCounter();
                packetCounter.add(key, 17);
                helper.assertTrue(packetCounter.get(packetCopy) == 17,
                        "Packet-decoded magic key cannot find the original key's AE counter balance");
                packetCounter.add(packetCopy, 23);
                helper.assertTrue(packetCounter.get(key) == 40 && packetCounter.size() == 1,
                        "Packet-decoded magic key created a second AE counter balance for the same resource");
            } finally {
                buffer.release();
            }
        }
        if (ModList.get().isLoaded("arseng")) {
            try {
                Object nativeSource = Class.forName("gripe._90.arseng.me.key.SourceKey")
                        .getField("KEY").get(null);
                helper.assertTrue(source == nativeSource,
                        "Ars Energistique is installed but its native singleton is not canonical Source");
                AEKey alias = new MagicResourceKey(SOURCE);
                MagicStorageCellInventory cell = inventory(cellStack(helper));
                helper.assertTrue(MagicResourceKeys.resourceId(alias) == null
                                && insert(cell, alias, 1000, Actionable.MODULATE) == 0
                                && insert(cell, source, 17, Actionable.MODULATE) == 17
                                && insert(cell, alias, 1, Actionable.MODULATE) == 0
                                && extract(cell, alias, 17, Actionable.MODULATE) == 0
                                && cell.getAvailableStacks().size() == 1
                                && cell.getAvailableStacks().getFirstKey() == source
                                && cell.getAvailableStacks().get(source) == 17,
                        "Fallback Source alias bypassed the native canonical key or duplicated network contents");
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Installed Ars Energistique Source key is unavailable", exception);
            }
        } else {
            helper.assertTrue(source.getType().getId().getNamespace().equals(MekanismMagic.MOD_ID),
                    "Source fallback did not use Mekanism Magic's registered key type");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void realDriveMountsMixedCellAndPersistsItsEjectedStack(GameTestHelper helper) {
        ItemStack stack = cellStack(helper);
        helper.assertTrue(StorageCells.getCellInventory(stack, null) instanceof MagicStorageCellInventory,
                "AE's registered cell handler did not select the custom mixed inventory");
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(DRIVE_POS, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE_POS);
        install(helper, drive, stack);
        AEKey source = requiredKey(helper, SOURCE);
        AEKey aura = requiredKey(helper, AURA);

        helper.startSequence().thenWaitUntil(() -> assertMounted(helper, drive, source, aura))
                .thenExecute(() -> {
                    MEStorage network = network(drive);
                    helper.assertTrue(insert(network, source, 12_345, Actionable.SIMULATE) == 12_345
                                    && network.getAvailableStacks().isEmpty(),
                            "Real ME network simulation wrote into the magic cell");
                    helper.assertTrue(insert(network, source, 12_345, Actionable.MODULATE) == 12_345
                                    && insert(network, aura, 6789, Actionable.MODULATE) == 6789,
                            "Real ME drive did not accept both independent magic resource types");
                    logDriveState("before-eject", drive, source, aura);
                    ItemStack ejected = drive.getInternalInventory().extractItem(0, 1, false);
                    helper.assertTrue(!ejected.isEmpty(), "ME drive could not eject its magic cell");
                    ItemStack reloaded = roundTrip(helper, ejected);
                    MagicCellContents saved = reloaded.get(MagicStorageCells.CONTENTS);
                    helper.assertTrue(saved != null
                                    && saved.amounts().equals(Map.of(SOURCE, 12_345L, AURA, 6789L)),
                            "ME drive ejection did not flush the cell's pending contents before item save: "
                                    + saved);
                    install(helper, drive, reloaded);
                    logDriveState("after-reinstall", drive, source, aura);
                }).thenWaitUntil(() -> {
                    assertMounted(helper, drive, source, aura);
                    helper.assertTrue(network(drive).getAvailableStacks().get(source) == 12_345
                                    && network(drive).getAvailableStacks().get(aura) == 6789,
                            "Reinstalled cell did not remount its saved Source and Aura: "
                                    + driveState(drive, source, aura));
                }).thenExecute(() -> {
                    MEStorage network = network(drive);
                    helper.assertTrue(extract(network, source, Long.MAX_VALUE, Actionable.MODULATE) == 12_345
                                    && extract(network, aura, Long.MAX_VALUE, Actionable.MODULATE) == 6789,
                            "Remounted ME cell duplicated or lost stored magic during extraction");
                    ItemStack empty = drive.getInternalInventory().extractItem(0, 1, false);
                    StorageCell reloaded = StorageCells.getCellInventory(roundTrip(helper, empty), null);
                    helper.assertTrue(reloaded != null && reloaded.getAvailableStacks().isEmpty()
                                    && reloaded.canFitInsideCell(),
                            "Real ME extraction left persisted ghost contents in the removed cell");
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 240)
    public static void realMeChestExposesBothMagicKeysAndPersistsRemoval(GameTestHelper helper) {
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(DRIVE_POS, AEBlocks.ME_CHEST.block());
        MEChestBlockEntity chest = helper.getBlockEntity(DRIVE_POS);
        // The chest's combined inventory contains the input slot first and cell slot second.
        helper.assertTrue(chest.getInternalInventory().insertItem(1, cellStack(helper), false).isEmpty(),
                "Real ME chest rejected the registered magic cell in its storage slot");
        AEKey source = requiredKey(helper, SOURCE);
        AEKey aura = requiredKey(helper, AURA);

        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(chest.getMainNode().isActive() && chest.isPowered()
                            && chest.getOriginalCellInventory(0) instanceof MagicStorageCellInventory,
                    "Waiting for the powered ME chest to mount its custom magic cell");
            helper.assertTrue(insert(chest.getInventory(), source, 1, Actionable.SIMULATE) == 1
                            && insert(chest.getInventory(), aura, 1, Actionable.SIMULATE) == 1,
                    "ME chest terminal storage did not expose both magic types");
        }).thenExecute(() -> {
            MEStorage terminal = chest.getInventory();
            helper.assertTrue(insert(terminal, source, 4321, Actionable.MODULATE) == 4321
                            && insert(terminal, aura, 8765, Actionable.MODULATE) == 8765,
                    "Real ME chest did not store Source and Aura independently");
            MekanismMagic.LOGGER.info("Magic cell ME chest before-eject: {}",
                    chestState(chest, source, aura));
            ItemStack removed = chest.getInternalInventory().extractItem(1, 1, false);
            helper.assertTrue(!removed.isEmpty(), "ME chest could not remove its magic cell");
            ItemStack restored = roundTrip(helper, removed);
            helper.assertTrue(MagicStorageCellItem.contents(restored).amounts().equals(
                            Map.of(SOURCE, 4321L, AURA, 8765L)),
                    "Removing the ME chest cell discarded its stored magic: "
                            + MagicStorageCellItem.contents(restored));
            helper.assertTrue(chest.getInternalInventory().insertItem(1, restored, false).isEmpty(),
                    "ME chest rejected its restored magic cell");
            MekanismMagic.LOGGER.info("Magic cell ME chest after-reinstall: {}",
                    chestState(chest, source, aura));
        }).thenWaitUntil(() -> helper.assertTrue(
                chest.getInventory().getAvailableStacks().get(source) == 4321
                        && chest.getInventory().getAvailableStacks().get(aura) == 8765,
                "ME chest terminal did not rediscover both resource amounts after reinstalling the cell: "
                        + chestState(chest, source, aura)))
                .thenExecute(() -> {
                    helper.assertTrue(extract(chest.getInventory(), source, Long.MAX_VALUE,
                                    Actionable.MODULATE) == 4321
                                    && extract(chest.getInventory(), aura, Long.MAX_VALUE,
                                    Actionable.MODULATE) == 8765,
                            "ME chest terminal lost or duplicated magic during extraction");
                    ItemStack emptied = chest.getInternalInventory().extractItem(1, 1, false);
                    helper.assertTrue(MagicStorageCellItem.contents(roundTrip(helper, emptied))
                                    .amounts().isEmpty(),
                            "ME chest extraction left ghost magic in the persisted cell");
                }).thenSucceed();
    }

    private static ItemStack cellStack(GameTestHelper helper) {
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                MekanismMagic.MOD_ID, "magic_storage_cell_1k"));
        helper.assertTrue(item instanceof MagicStorageCellItem,
                "The registered 1K magic storage cell item is unavailable");
        return new ItemStack(item);
    }

    private static MagicStorageCellInventory inventory(ItemStack stack) {
        return new MagicStorageCellInventory((MagicStorageCellItem) stack.getItem(), stack, null);
    }

    private static AEKey requiredKey(GameTestHelper helper, ResourceLocation id) {
        AEKey key = MagicResourceKeys.key(id);
        helper.assertTrue(key != null && id.equals(MagicResourceKeys.resourceId(key)),
                "Canonical test magic resource is unavailable: " + id);
        return key;
    }

    private static long insert(MEStorage storage, AEKey key, long amount, Actionable mode) {
        return storage.insert(key, amount, mode, IActionSource.empty());
    }

    private static long extract(MEStorage storage, AEKey key, long amount, Actionable mode) {
        return storage.extract(key, amount, mode, IActionSource.empty());
    }

    private static ItemStack roundTrip(GameTestHelper helper, ItemStack stack) {
        ItemStack restored = ItemStack.parse(helper.getLevel().registryAccess(),
                stack.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(ItemStack.isSameItemSameComponents(stack, restored),
                "Item serialization changed the magic cell's identity or components");
        return restored;
    }

    private static void install(GameTestHelper helper, DriveBlockEntity drive, ItemStack stack) {
        helper.assertTrue(drive.getInternalInventory().insertItem(0, stack, false).isEmpty(),
                "Real AE drive rejected the registered magic storage cell item");
    }

    private static void assertMounted(GameTestHelper helper, DriveBlockEntity drive,
                                      AEKey source, AEKey aura) {
        helper.assertTrue(drive.getMainNode().isActive() && drive.isPowered(),
                "Waiting for the real ME drive to join its powered grid");
        helper.assertTrue(drive.getOriginalCellInventory(0) instanceof MagicStorageCellInventory
                        && insert(network(drive), source, 1, Actionable.SIMULATE) == 1
                        && insert(network(drive), aura, 1, Actionable.SIMULATE) == 1,
                "Real ME drive has not mounted both supported magic resource keys");
    }

    private static MEStorage network(DriveBlockEntity drive) {
        return drive.getMainNode().getGrid().getStorageService().getInventory();
    }

    private static void logDriveState(String stage, DriveBlockEntity drive, AEKey source, AEKey aura) {
        MekanismMagic.LOGGER.info("Magic cell ME drive {}: {}", stage,
                driveState(drive, source, aura));
    }

    private static String driveState(DriveBlockEntity drive, AEKey source, AEKey aura) {
        return "mounted=" + resourceCounts(drive.getOriginalCellInventory(0), source, aura)
                + ", network=" + resourceCounts(network(drive), source, aura);
    }

    private static String chestState(MEChestBlockEntity chest, AEKey source, AEKey aura) {
        return "mounted=" + resourceCounts(chest.getOriginalCellInventory(0), source, aura)
                + ", terminal=" + resourceCounts(chest.getInventory(), source, aura);
    }

    private static String resourceCounts(MEStorage storage, AEKey source, AEKey aura) {
        if (storage == null) return "absent";
        var amounts = storage.getAvailableStacks();
        StringBuilder entries = new StringBuilder();
        for (var entry : amounts) {
            if (!entries.isEmpty()) entries.append(", ");
            entries.append(entry.getKey().getId()).append('=').append(entry.getLongValue())
                    .append("@primary:").append(Integer.toHexString(
                            System.identityHashCode(entry.getKey().getPrimaryKey())));
        }
        return "{source=" + amounts.get(source) + ", aura=" + amounts.get(aura)
                + ", keys=" + amounts.size() + ", instance="
                + Integer.toHexString(System.identityHashCode(storage))
                + ", entries=[" + entries + "]}"
                + ", queriedPrimary={source="
                + Integer.toHexString(System.identityHashCode(source.getPrimaryKey()))
                + ", aura=" + Integer.toHexString(System.identityHashCode(aura.getPrimaryKey())) + "}";
    }
}
