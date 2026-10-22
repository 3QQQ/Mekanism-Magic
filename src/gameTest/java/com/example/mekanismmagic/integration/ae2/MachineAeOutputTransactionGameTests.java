package com.example.mekanismmagic.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasNatureAltarFactories;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasSpiritFactories;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Adversarial callbacks exercise AE's real poweredInsert transaction boundary. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineAeOutputTransactionGameTests {
    private static final BlockPos ORIGINAL = new BlockPos(1, 1, 2);
    private static final BlockPos RESTORED = new BlockPos(3, 1, 2);
    private static final IActionSource ACTOR = IActionSource.empty();

    private MachineAeOutputTransactionGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void reservePrecedesCommitAndReentrantExportCannotSpendItTwice(GameTestHelper helper) {
        ImbuementProcessorBlockEntity machine = place(helper, ORIGINAL);
        AEKey key = AEItemKey.of(Items.STONE);
        AtomicLong local = new AtomicLong(100);
        AtomicLong network = new AtomicLong();
        AtomicInteger reservations = new AtomicInteger();
        AtomicInteger executions = new AtomicInteger();
        AtomicReference<IGrid> grid = new AtomicReference<>();
        grid.set(grid((what, amount, mode) -> {
            if (mode == Actionable.SIMULATE) return amount;
            executions.incrementAndGet();
            helper.assertTrue(local.get() == 0 && pending(helper, machine, key) == 100,
                    "Network commit observed unreserved local output or an unsaved owned reservation");
            long nested = Ae2MachineOutputAccess.exportResource(machine, grid.get(), ACTOR,
                    key, 100, 0, requested -> {
                        reservations.incrementAndGet();
                        return requested;
                    }, requested -> requested);
            helper.assertTrue(nested == 0 && !Ae2MachineOutputAccess.flushPending(machine, grid.get(), ACTOR),
                    "A reentrant storage callback exported an in-flight reservation twice");
            network.addAndGet(40);
            return 40;
        }));
        long inserted = Ae2MachineOutputAccess.exportResource(machine, grid.get(), ACTOR, key, 100, 0,
                requested -> {
                    reservations.incrementAndGet();
                    return Math.min(requested, local.getAndSet(0));
                }, requested -> {
                    local.addAndGet(requested);
                    return requested;
                });
        helper.assertTrue(inserted == 40 && network.get() == 40 && local.get() == 60
                        && pending(helper, machine, key) == 0 && reservations.get() == 1 && executions.get() == 1,
                "Partial commit did not restore exactly the rejected remainder after one reservation");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void failedLocalRollbackEscrowSurvivesRepeatedLoadAndOneStatefulDrop(GameTestHelper helper) {
        ImbuementProcessorBlockEntity original = place(helper, ORIGINAL);
        AEKey key = AEItemKey.of(Items.STONE);
        AtomicLong network = new AtomicLong();
        original.getNativeOutputSlot().setStack(new ItemStack(Items.STONE, 10));
        IGrid partial = grid((what, amount, mode) -> {
            if (mode == Actionable.SIMULATE) return amount;
            helper.assertTrue(original.getNativeOutputSlot().isEmpty(),
                    "Output slot was still available during an external storage commit");
            original.getNativeOutputSlot().setStack(new ItemStack(Items.DIRT, 64));
            network.addAndGet(4);
            return 4;
        });
        Ae2MachineOutputAccess.exportItems(original, partial, ACTOR);
        helper.assertTrue(network.get() == 4 && pending(helper, original, key) == 6
                        && original.getNativeOutputSlot().getStack().is(Items.DIRT)
                        && original.getNativeOutputSlot().getCount() == 64,
                "Rollback overwrote callback-created output or lost the six still-owned items");
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        original.loadWithComponents(saved.copy(), helper.getLevel().registryAccess());
        original.loadWithComponents(saved.copy(), helper.getLevel().registryAccess());
        helper.assertTrue(pending(helper, original, key) == 6,
                "Repeated block-entity load merged the same export escrow twice");

        original.setRemoved();
        var drops = new ArrayList<ItemEntity>();
        BlockPos absolute = helper.absolutePos(ORIGINAL);
        drops.add(new ItemEntity(helper.getLevel(), absolute.getX(), absolute.getY(), absolute.getZ(),
                new ItemStack(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get(), 2)));
        NeoForge.EVENT_BUS.post(new BlockDropsEvent(helper.getLevel(), absolute, original.getBlockState(),
                original, drops, null, ItemStack.EMPTY));
        List<ItemStack> stateful = drops.stream().map(ItemEntity::getItem)
                .filter(stack -> stack.has(DataComponents.BLOCK_ENTITY_DATA)).toList();
        helper.assertTrue(stateful.size() == 1 && stateful.getFirst().getCount() == 1
                        && drops.stream().mapToInt(drop -> drop.getItem().getCount()).sum() == 2,
                "One export escrow was copied to multiple dropped machines");
        ItemStack savedItem = ItemStack.parse(helper.getLevel().registryAccess(),
                stateful.getFirst().save(helper.getLevel().registryAccess())).orElseThrow();
        helper.setBlock(ORIGINAL, Blocks.AIR);
        ImbuementProcessorBlockEntity restored = place(helper, RESTORED);
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null,
                        helper.absolutePos(RESTORED), savedItem),
                "Dropped machine did not restore its saved export escrow");
        restored.applyComponentsFromItemStack(savedItem);
        helper.assertTrue(pending(helper, restored, key) == 6,
                "Dropped item roundtrip changed the owned export remainder");
        IGrid accepting = grid((what, amount, mode) -> {
            if (mode == Actionable.MODULATE) network.addAndGet(amount);
            return amount;
        });
        helper.assertTrue(Ae2MachineOutputAccess.flushPending(restored, accepting, ACTOR)
                        && Ae2MachineOutputAccess.flushPending(restored, accepting, ACTOR)
                        && pending(helper, restored, key) == 0 && network.get() == 10
                        && restored.getNativeOutputSlot().getStack().is(Items.DIRT)
                        && restored.getNativeOutputSlot().getCount() == 64,
                "Reloaded escrow was lost, replayed twice, or overwrote the independent physical output");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void throwAfterStorageCommitQuarantinesWithoutRefundOrAutomaticReplay(GameTestHelper helper) {
        ImbuementProcessorBlockEntity machine = place(helper, ORIGINAL);
        AEKey key = AEItemKey.of(Items.STONE);
        AtomicLong local = new AtomicLong(10);
        AtomicLong network = new AtomicLong();
        AtomicInteger refunds = new AtomicInteger();
        IGrid throwing = grid((what, amount, mode) -> {
            if (mode == Actionable.SIMULATE) return amount;
            network.addAndGet(amount);
            throw new IllegalStateException("Intentional storage callback failure after commit");
        });
        long returned = Ae2MachineOutputAccess.exportResource(machine, throwing, ACTOR, key, 10, 0,
                requested -> Math.min(requested, local.getAndSet(0)), requested -> {
                    refunds.incrementAndGet();
                    local.addAndGet(requested);
                    return requested;
                });
        helper.assertTrue(returned == 0 && local.get() == 0 && network.get() == 10 && refunds.get() == 0
                        && pending(helper, machine, key) == 10 && uncertain(helper, machine, key),
                "Unknown storage commit was refunded locally or lost its quarantined ownership record");
        CompoundTag saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        machine.loadWithComponents(saved.copy(), helper.getLevel().registryAccess());
        machine.loadWithComponents(saved.copy(), helper.getLevel().registryAccess());
        IGrid accepting = grid((what, amount, mode) -> {
            if (mode == Actionable.MODULATE) network.addAndGet(amount);
            return amount;
        });
        helper.assertTrue(!Ae2MachineOutputAccess.flushPending(machine, accepting, ACTOR)
                        && pending(helper, machine, key) == 10 && uncertain(helper, machine, key)
                        && network.get() == 10 && local.get() == 0,
                "Reload automatically replayed an unknown storage commit and duplicated its contents");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void throwingStoragePreviewDoesNotReserveOrCreateAnEscrow(GameTestHelper helper) {
        ImbuementProcessorBlockEntity machine = place(helper, ORIGINAL);
        AEKey key = AEItemKey.of(Items.STONE);
        AtomicInteger reservations = new AtomicInteger();
        boolean threw = false;
        try {
            Ae2MachineOutputAccess.exportResource(machine, grid((what, amount, mode) -> {
                throw new IllegalStateException("Intentional storage preview failure");
            }), ACTOR, key, 10, 0, requested -> {
                reservations.incrementAndGet();
                return requested;
            }, requested -> requested);
        } catch (IllegalStateException expected) {
            threw = true;
        }
        helper.assertTrue(threw && reservations.get() == 0 && pending(helper, machine, key) == 0,
                "Failed storage simulation changed ownership before a local reservation");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void snapshotDuringCommitNeverReplaysAnAlreadyCommittedReservation(GameTestHelper helper) {
        ImbuementProcessorBlockEntity original = place(helper, ORIGINAL);
        AEKey key = AEItemKey.of(Items.STONE);
        AtomicLong network = new AtomicLong();
        AtomicReference<CompoundTag> snapshot = new AtomicReference<>();
        original.getNativeOutputSlot().setStack(new ItemStack(Items.STONE, 10));
        Ae2MachineOutputAccess.exportItems(original, grid((what, amount, mode) -> {
            if (mode == Actionable.SIMULATE) return amount;
            snapshot.set(original.saveWithoutMetadata(helper.getLevel().registryAccess()));
            network.addAndGet(amount);
            return amount;
        }), ACTOR);
        helper.assertTrue(network.get() == 10 && pending(helper, original, key) == 0
                        && original.getNativeOutputSlot().isEmpty() && snapshot.get() != null,
                "Successful commit retained an owned duplicate or failed to capture its in-flight save");
        ImbuementProcessorBlockEntity restored = place(helper, RESTORED);
        restored.loadWithComponents(snapshot.get(), helper.getLevel().registryAccess());
        helper.assertTrue(restored.getNativeOutputSlot().isEmpty()
                        && pending(helper, restored, key) == 10 && uncertain(helper, restored, key),
                "An in-flight machine snapshot was saved as safely replayable output");
        IGrid accepting = grid((what, amount, mode) -> {
            if (mode == Actionable.MODULATE) network.addAndGet(amount);
            return amount;
        });
        helper.assertTrue(!Ae2MachineOutputAccess.flushPending(restored, accepting, ACTOR)
                        && network.get() == 10 && pending(helper, restored, key) == 10,
                "Restoring an in-flight snapshot duplicated output that the original callback committed");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void removalDuringPreviewCannotReserveOutputAlreadySavedInTheDroppedItem(GameTestHelper helper) {
        ImbuementProcessorBlockEntity original = place(helper, ORIGINAL);
        AEKey key = AEItemKey.of(Items.STONE);
        AtomicLong network = new AtomicLong();
        ItemStack dropped = new ItemStack(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        original.getNativeOutputSlot().setStack(new ItemStack(Items.STONE, 10));
        AtomicInteger previews = new AtomicInteger();
        Ae2MachineOutputAccess.exportItems(original, grid((what, amount, mode) -> {
            if (mode == Actionable.SIMULATE) {
                if (previews.incrementAndGet() == 1) {
                    original.saveToItem(dropped, helper.getLevel().registryAccess());
                    original.setRemoved();
                }
                return amount;
            }
            network.addAndGet(amount);
            return amount;
        }), ACTOR);
        helper.assertTrue(network.get() == 0 && pending(helper, original, key) == 0
                        && original.getNativeOutputSlot().getCount() == 10,
                "Removed machine exported output already owned by its saved dropped item");
        ImbuementProcessorBlockEntity restored = place(helper, RESTORED);
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null,
                        helper.absolutePos(RESTORED), dropped),
                "Preview-removal fixture did not save its machine item");
        restored.applyComponentsFromItemStack(dropped);
        helper.assertTrue(restored.getNativeOutputSlot().getStack().is(Items.STONE)
                        && restored.getNativeOutputSlot().getCount() == 10 && network.get() == 0,
                "Preview-removal moved output out of the saved item's authoritative inventory");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void absentKeyProviderPreservesRawEscrowUntilItsOriginalKeyCanDecodeAgain(GameTestHelper helper) {
        ImbuementProcessorBlockEntity machine = place(helper, ORIGINAL);
        AEKey key = AEItemKey.of(Items.STONE);
        long amount = (1L << 40) + 7;
        CompoundTag known = GenericStack.writeTag(helper.getLevel().registryAccess(), new GenericStack(key, amount));
        CompoundTag dormant = known.copy();
        dormant.putString(AEKey.TYPE_FIELD, "unavailable_export_provider:original_key_type");
        dormant.putBoolean("uncertain", true);
        CompoundTag opaque = new CompoundTag();
        opaque.putString("owner", "untouched-native-provider-payload");
        dormant.put("provider_data", opaque);
        ListTag entries = new ListTag();
        entries.add(dormant.copy());
        CompoundTag seeded = new CompoundTag();
        seeded.put(Ae2MachineOutputAccess.PENDING_TAG, entries);
        Ae2MachineOutputAccess.load(machine, seeded, helper.getLevel().registryAccess());
        Ae2MachineOutputAccess.load(machine, seeded, helper.getLevel().registryAccess());
        CompoundTag saved = new CompoundTag();
        Ae2MachineOutputAccess.save(machine, saved, helper.getLevel().registryAccess());
        helper.assertTrue(saved.getList(Ae2MachineOutputAccess.PENDING_TAG, Tag.TAG_COMPOUND).equals(entries),
                "An absent optional key provider lost, truncated or rewrote its opaque owned escrow");
        AtomicLong inserted = new AtomicLong();
        IGrid accepting = grid((what, requested, mode) -> {
            if (mode == Actionable.MODULATE) inserted.addAndGet(requested);
            return requested;
        });
        helper.assertTrue(!Ae2MachineOutputAccess.flushPending(machine, accepting, ACTOR) && inserted.get() == 0,
                "An undecodable key was silently discarded or replayed as another resource");
        ItemStack machineItem = new ItemStack(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        machine.saveToItem(machineItem, helper.getLevel().registryAccess());
        var itemData = machineItem.get(DataComponents.BLOCK_ENTITY_DATA);
        helper.assertTrue(itemData != null && itemData.copyTag()
                        .getList(Ae2MachineOutputAccess.PENDING_TAG, Tag.TAG_COMPOUND).equals(entries),
                "Machine item serialization lost a dormant provider's raw export reservation");

        // Restore the provider's original serialized key and verify that the
        // loader decodes, rather than merging the dormant and live copies.
        ListTag decodable = new ListTag();
        decodable.add(known);
        CompoundTag available = new CompoundTag();
        available.put(Ae2MachineOutputAccess.PENDING_TAG, decodable);
        Ae2MachineOutputAccess.load(machine, available, helper.getLevel().registryAccess());
        helper.assertTrue(pending(helper, machine, key) == amount
                        && Ae2MachineOutputAccess.flushPending(machine, accepting, ACTOR)
                        && inserted.get() == amount,
                "Restoring the key provider lost or duplicated the exact long escrow amount");
        // Reusing the same save tag must remove its old nonempty escrow list.
        Ae2MachineOutputAccess.save(machine, saved, helper.getLevel().registryAccess());
        helper.assertTrue(!saved.contains(Ae2MachineOutputAccess.PENDING_TAG),
                "Clearing escrow left a stale replayable list in a reused save tag");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void throwingRollbackNeverReplaysItemsAlreadyRestoredLocally(GameTestHelper helper) {
        ImbuementProcessorBlockEntity original = place(helper, ORIGINAL);
        AEKey key = AEItemKey.of(Items.STONE);
        AtomicLong network = new AtomicLong();
        AtomicReference<CompoundTag> rollbackSnapshot = new AtomicReference<>();
        original.getNativeOutputSlot().setStack(new ItemStack(Items.STONE, 10));
        IGrid partial = grid((what, amount, mode) -> {
            if (mode == Actionable.SIMULATE) return amount;
            network.addAndGet(4);
            return 4;
        });
        long inserted = Ae2MachineOutputAccess.exportResource(original, partial, ACTOR, key, 10, 0,
                requested -> original.getNativeOutputSlot().extractItem((int) requested,
                        Action.EXECUTE, AutomationType.EXTERNAL).getCount(), requested -> {
                    original.getNativeOutputSlot().setStack(new ItemStack(Items.STONE, (int) requested));
                    rollbackSnapshot.set(original.saveWithoutMetadata(helper.getLevel().registryAccess()));
                    throw new IllegalStateException("Intentional failure after local rollback mutation");
                });
        helper.assertTrue(inserted == 4 && network.get() == 4 && original.getNativeOutputSlot().getCount() == 6
                        && pending(helper, original, key) == 6 && uncertain(helper, original, key),
                "A rollback failure lost trusted insertion or left locally restored items replayable");
        ImbuementProcessorBlockEntity restored = place(helper, RESTORED);
        restored.loadWithComponents(rollbackSnapshot.get(), helper.getLevel().registryAccess());
        helper.assertTrue(restored.getNativeOutputSlot().getCount() == 6
                        && pending(helper, restored, key) == 6 && uncertain(helper, restored, key),
                "Saving inside local rollback produced an automatically replayable duplicate");
        IGrid accepting = grid((what, amount, mode) -> {
            if (mode == Actionable.MODULATE) network.addAndGet(amount);
            return amount;
        });
        helper.assertTrue(!Ae2MachineOutputAccess.flushPending(original, accepting, ACTOR)
                        && !Ae2MachineOutputAccess.flushPending(restored, accepting, ACTOR)
                        && network.get() == 4 && original.getNativeOutputSlot().getCount() == 6
                        && restored.getNativeOutputSlot().getCount() == 6,
                "Unknown rollback ownership was replayed and duplicated the rejected six items");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void spiritUpgradeCarriesOwnedAndQuarantinedExportsAcrossNativeAndExtrasTiers(GameTestHelper helper) {
        upgradeRoute(helper, NativeMekanismRegistries.SPIRIT_BLOCK.get(),
                NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.get(),
                MekanismExtrasSpiritFactories.INFINITE_BLOCK.get());
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void imbuementUpgradeCarriesOwnedAndQuarantinedExportsAcrossNativeAndExtrasTiers(GameTestHelper helper) {
        upgradeRoute(helper, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get(),
                ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get(),
                MekanismExtrasImbuementFactories.INFINITE_BLOCK.get());
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void natureUpgradeCarriesOwnedAndQuarantinedExportsAcrossNativeAndExtrasTiers(GameTestHelper helper) {
        upgradeRoute(helper, NaturesAuraRegistries.NATURE_ALTAR_PROCESSOR_BLOCK.get(),
                NaturesAuraRegistries.BASIC_NATURE_ALTAR_FACTORY_BLOCK.get(),
                MekanismExtrasNatureAltarFactories.INFINITE_BLOCK.get());
    }

    private static void upgradeRoute(GameTestHelper helper, Block ordinary, Block nativeFactory, Block extrasFactory) {
        AEKey normal = AEItemKey.of(Items.STONE);
        AEKey quarantined = AEItemKey.of(Items.DIRT);
        CompoundTag dormant = GenericStack.writeTag(helper.getLevel().registryAccess(),
                new GenericStack(AEItemKey.of(Items.DIAMOND), (1L << 40) + 9));
        dormant.putString(AEKey.TYPE_FIELD, "unavailable_upgrade_provider:owned_output");
        dormant.putBoolean("uncertain", true);
        ListTag entries = new ListTag();
        entries.add(GenericStack.writeTag(helper.getLevel().registryAccess(), new GenericStack(normal, 11)));
        CompoundTag uncertain = GenericStack.writeTag(helper.getLevel().registryAccess(), new GenericStack(quarantined, 7));
        uncertain.putBoolean("uncertain", true);
        entries.add(uncertain);
        entries.add(dormant.copy());
        CompoundTag seeded = new CompoundTag();
        seeded.putBoolean("magic_native_ae_supply_enabled", false);
        seeded.put(Ae2MachineOutputAccess.PENDING_TAG, entries);

        helper.setBlock(ORIGINAL, ordinary);
        TileEntityMekanism current = helper.getBlockEntity(ORIGINAL);
        MachineAeResourceHooks.load(current, seeded, helper.getLevel().registryAccess());
        assertUpgradeEscrow(helper, current, normal, quarantined, dormant, true);
        for (Block next : List.of(nativeFactory, extrasFactory)) {
            var payload = current.getUpgradeData(helper.getLevel().registryAccess());
            helper.setBlock(ORIGINAL, Blocks.AIR);
            helper.setBlock(ORIGINAL, next);
            current = helper.getBlockEntity(ORIGINAL);
            current.parseUpgradeData(helper.getLevel().registryAccess(), payload);
            current.parseUpgradeData(helper.getLevel().registryAccess(), payload);
            assertUpgradeEscrow(helper, current, normal, quarantined, dormant, true);
        }

        AtomicLong normalInserted = new AtomicLong();
        AtomicLong otherInserted = new AtomicLong();
        IGrid accepting = grid((key, amount, mode) -> {
            if (mode == Actionable.MODULATE) {
                if (normal.equals(key)) normalInserted.addAndGet(amount);
                else otherInserted.addAndGet(amount);
            }
            return amount;
        });
        helper.assertTrue(!Ae2MachineOutputAccess.flushPending(current, accepting, ACTOR)
                        && !Ae2MachineOutputAccess.flushPending(current, accepting, ACTOR)
                        && normalInserted.get() == 11 && otherInserted.get() == 0,
                "Tier replacement duplicated normal escrow or replayed an uncertain/dormant reservation");
        assertUpgradeEscrow(helper, current, normal, quarantined, dormant, false);
        helper.succeed();
    }

    private static void assertUpgradeEscrow(GameTestHelper helper, TileEntityMekanism machine,
                                            AEKey normal, AEKey quarantined, CompoundTag dormant,
                                            boolean includesNormal) {
        CompoundTag saved = new CompoundTag();
        MachineAeResourceHooks.save(machine, saved, helper.getLevel().registryAccess());
        ListTag entries = saved.getList(Ae2MachineOutputAccess.PENDING_TAG, Tag.TAG_COMPOUND);
        helper.assertTrue(!MachineAeResourceHooks.isEnabled(machine)
                        && !saved.getBoolean("magic_native_ae_supply_enabled")
                        && entries.size() == (includesNormal ? 3 : 2) && entries.contains(dormant)
                        && pending(helper, machine, normal) == (includesNormal ? 11 : 0)
                        && pending(helper, machine, quarantined) == 7 && uncertain(helper, machine, quarantined),
                "Tier replacement lost supply OFF, merged escrow twice, or cleared a quarantined/dormant entry");
    }

    private static ImbuementProcessorBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        ImbuementProcessorBlockEntity machine = helper.getBlockEntity(pos);
        machine.getNativeEnergyContainer().setEnergy(0);
        machine.setSource(0);
        return machine;
    }

    private static long pending(GameTestHelper helper, BlockEntity machine, AEKey key) {
        CompoundTag tag = new CompoundTag();
        Ae2MachineOutputAccess.save(machine, tag, helper.getLevel().registryAccess());
        long result = 0;
        for (Tag value : tag.getList(Ae2MachineOutputAccess.PENDING_TAG, Tag.TAG_COMPOUND)) {
            try {
                GenericStack stack = GenericStack.readTag(helper.getLevel().registryAccess(), (CompoundTag) value);
                if (stack != null && key.equals(stack.what())) result += stack.amount();
            } catch (RuntimeException missingProvider) {
                // An intentionally dormant provider entry is inspected raw.
            }
        }
        return result;
    }

    private static boolean uncertain(GameTestHelper helper, BlockEntity machine, AEKey key) {
        CompoundTag tag = new CompoundTag();
        Ae2MachineOutputAccess.save(machine, tag, helper.getLevel().registryAccess());
        for (Tag value : tag.getList(Ae2MachineOutputAccess.PENDING_TAG, Tag.TAG_COMPOUND)) {
            try {
                GenericStack stack = GenericStack.readTag(helper.getLevel().registryAccess(), (CompoundTag) value);
                if (stack != null && key.equals(stack.what()) && ((CompoundTag) value).getBoolean("uncertain")) return true;
            } catch (RuntimeException missingProvider) {
                // An intentionally dormant provider entry is inspected raw.
            }
        }
        return false;
    }

    private static IGrid grid(Insertion insertion) {
        MEStorage storage = new MEStorage() {
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource actor) {
                return insertion.insert(key, amount, mode);
            }
            @Override public Component getDescription() { return Component.literal("Adversarial test storage"); }
        };
        IStorageService storageService = (IStorageService) Proxy.newProxyInstance(
                IStorageService.class.getClassLoader(), new Class<?>[]{IStorageService.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getInventory")) return storage;
                    throw new UnsupportedOperationException(method.toString());
                });
        IEnergyService energyService = (IEnergyService) Proxy.newProxyInstance(
                IEnergyService.class.getClassLoader(), new Class<?>[]{IEnergyService.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("extractAEPower")) return arguments[0];
                    throw new UnsupportedOperationException(method.toString());
                });
        return (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[]{IGrid.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getStorageService" -> storageService;
                    case "getEnergyService" -> energyService;
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    @FunctionalInterface
    private interface Insertion {
        long insert(AEKey key, long amount, Actionable mode);
    }
}
