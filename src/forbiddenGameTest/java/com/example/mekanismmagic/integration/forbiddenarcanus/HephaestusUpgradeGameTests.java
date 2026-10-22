package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualView;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeLevel;
import mekanism.api.Upgrade;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Coreless machine-level upgrades and one-way recovery of old inventories. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HephaestusUpgradeGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final BlockPos TARGET = new BlockPos(3, 2, 1);

    private HephaestusUpgradeGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fourNativeUpgradesAdvanceOnlyTheMachineTier(
            GameTestHelper helper) {
        helper.assertTrue(HephaestusForge261Bridge.upgradeRecipes(
                        helper.getLevel().getServer()).size() == 4,
                "Expected four supported native tier rituals");
        HephaestusForgeProcessorBlockEntity tile = place(helper, POS);
        helper.assertTrue(tile.getForgeTier() == 1,
                "New forge did not begin at internal level one");
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        tile.getReturnSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        for (int tier = 1; tier <= 4; tier++) {
            RitualView ritual = ritual(helper, "upgrade_tier_" + (tier + 1));
            materials(tile, ritual);
            int[] initial = HephaestusForge261Bridge.capacities(tier);
            seed(helper, tile, initial);
            tick(tile, 1);
            helper.assertTrue(tile.hasPaidJob()
                            && tile.getPaidUpgradeTier() == tier + 1
                            && tile.getPendingUpgradeTier() == 0
                            && tile.getForgeTier() == tier
                            && tile.getPaidOutput().isEmpty()
                            && tile.getMainInputSlot().isEmpty()
                            && auxiliaryEmpty(tile)
                            && tile.getProgressRequired() == ritual.duration(),
                    "Upgrade did not consume native materials into one coreless paid job");
            int[] raw = costs(ritual);
            int[] paid = bank(tile);
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(paid[channel]
                                == initial[channel] - Math.min(initial[channel], raw[channel]),
                        "Upgrade changed native essence debit " + channel);
            }
            CompoundTag reduced = tile.getReducedUpdateTag(
                    helper.getLevel().registryAccess());
            helper.assertTrue(reduced.getBoolean("hephaestus_paid")
                            && !reduced.contains(
                            HephaestusForgeProcessorBlockEntity.STATE_TAG),
                    "Paid update exposed a full receipt to world rendering");
            untilTier(helper, tile, tier + 1);
            helper.assertTrue(!tile.hasPaidJob()
                            && tile.getPaidUpgradeTier() == 0
                            && tile.getPendingUpgradeTier() == 0
                            && tile.getForgeTier() == tier + 1
                            && Arrays.equals(bank(tile), paid)
                            && tile.getEssenceCapacity(0)
                            == HephaestusForge261Bridge.capacities(tier + 1)[0]
                            && tile.getNativeOutputSlot().getCount() == 64
                            && tile.getReturnSlot().getCount() == 64,
                    "Upgrade minted a core item, repaid essence, or overwrote outputs");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void paidUpgradeSurvivesPowerPauseReloadAndMachineDrop(
            GameTestHelper helper) {
        HephaestusForgeProcessorBlockEntity tile = place(helper, POS);
        RitualView ritual = ritual(helper, "upgrade_tier_2");
        materials(tile, ritual);
        seed(helper, tile, costs(ritual));
        tick(tile, 1);
        int[] paidBank = bank(tile);
        int progress = tile.getProgress();
        helper.assertTrue(tile.getPaidUpgradeTier() == 2
                        && tile.getForgeTier() == 1,
                "Could not establish a paid internal tier upgrade");
        tile.getNativeEnergyContainer().setEnergy(0);
        tick(tile, 4);
        helper.assertTrue(tile.isPaidJobPaused()
                        && tile.getProgress() == progress
                        && tile.getPaidUpgradeTier() == 2,
                "Power loss released or advanced the paid tier change");
        tile.getNativeEnergyContainer().setEnergy(
                HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY);
        tile.getEnhancerSlot(0).setStack(item("artisan_relic"));
        tick(tile, 2);
        helper.assertTrue(tile.hasContextMismatch()
                        && tile.getProgress() == progress,
                "Changing an enabled enhancer bypassed paid context");
        tile.getEnhancerSlot(0).setEmpty();
        tile.loadWithComponents(save(helper, tile),
                helper.getLevel().registryAccess());
        ForbiddenArcanusRecipeCatalog.clear();
        try {
            tick(tile, 2);
            helper.assertTrue(tile.hasPaidJob()
                            && tile.getPaidUpgradeTier() == 2
                            && tile.getProgress() == progress + 2
                            && Arrays.equals(bank(tile), paidBank),
                    "Reload or missing recipe catalog repaid a locked upgrade");
            List<ItemEntity> drops = new ArrayList<>();
            BlockPos absolute = tile.getBlockPos();
            drops.add(new ItemEntity(helper.getLevel(), absolute.getX(),
                    absolute.getY(), absolute.getZ(), new ItemStack(
                    ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get(), 2)));
            NeoForge.EVENT_BUS.post(new BlockDropsEvent(helper.getLevel(),
                    absolute, tile.getBlockState(), tile, drops,
                    null, ItemStack.EMPTY));
            List<ItemStack> stateful = drops.stream().map(ItemEntity::getItem)
                    .filter(stack -> stack.has(DataComponents.BLOCK_ENTITY_DATA))
                    .toList();
            helper.assertTrue(stateful.size() == 1
                            && stateful.getFirst().getCount() == 1,
                    "Dropping a paid forge duplicated its saved state");
            helper.setBlock(POS, Blocks.AIR);
            HephaestusForgeProcessorBlockEntity restored = place(helper,
                    TARGET);
            helper.assertTrue(BlockItem.updateCustomBlockEntityTag(
                            helper.getLevel(), null,
                            helper.absolutePos(TARGET), stateful.getFirst()),
                    "Dropped paid upgrade could not restore NBT");
            restored.applyComponentsFromItemStack(stateful.getFirst());
            helper.assertTrue(restored.getForgeTier() == 1
                            && restored.getPaidUpgradeTier() == 2
                            && Arrays.equals(bank(restored), paidBank),
                    "Placement lost the paid target or replayed payment");
            untilTier(helper, restored, 2);
            helper.assertTrue(restored.getPaidUpgradeTier() == 0
                            && restored.getPendingUpgradeTier() == 0
                            && restored.getNativeOutputSlot().isEmpty()
                            && Arrays.equals(bank(restored), paidBank),
                    "Restored upgrade minted an item or repeated essence debit");
        } finally {
            ForbiddenArcanusRecipeCatalog.refresh(
                    helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void creativeAndSpeedStillRequireMaterialsAndFe(
            GameTestHelper helper) {
        HephaestusForgeProcessorBlockEntity tile = place(helper, POS);
        materials(tile, ritual(helper, "upgrade_tier_2"));
        tile.getComponent().addUpgrades(MagicUpgrades.creativeMagic(), 1);
        tile.getNativeEnergyContainer().setEnergy(0);
        tick(tile, 3);
        helper.assertTrue(!tile.hasPaidJob()
                        && tile.getForgeTier() == 1
                        && tile.getMainInputSlot().getCount() == 1,
                "Creative upgrade bypassed the required FE/material gate");
        tile.getNativeEnergyContainer().setEnergy(
                HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY);
        tick(tile, 1);
        helper.assertTrue(tile.hasPaidJob()
                        && tile.getPaidUpgradeTier() == 2
                        && tile.getMainInputSlot().isEmpty()
                        && auxiliaryEmpty(tile)
                        && Arrays.equals(new int[4], bank(tile)),
                "Creative upgrade did not preserve material payment or waived essence only");
        int normalDuration = tile.getProgressRequired();
        int progress = tile.getProgress();
        tile.setControlType(RedstoneControl.HIGH);
        tick(tile, 4);
        helper.assertTrue(tile.getProgress() == progress
                        && tile.getPaidUpgradeTier() == 2,
                "Redstone pause advanced a paid upgrade");
        tile.getComponent().addUpgrades(Upgrade.SPEED, 8);
        tile.recalculateUpgrades(Upgrade.SPEED);
        tile.setControlType(RedstoneControl.DISABLED);
        tick(tile, 1);
        helper.assertTrue(tile.getProgressRequired() < normalDuration,
                "Speed upgrades did not accelerate the paid tier ritual");
        untilTier(helper, tile, 2);
        helper.assertTrue(tile.getForgeTier() == 2
                        && Arrays.equals(new int[4], bank(tile))
                        && tile.getNativeOutputSlot().isEmpty(),
                "Creative/speed transition minted essence or a physical core");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void legacyCoreHammerAndPaidReceiptReturnExactlyOnce(
            GameTestHelper helper) {
        HephaestusForgeProcessorBlockEntity source = place(helper, POS);
        ItemStack oldCore = markedCore(3);
        ItemStack oldHammer = item("diamond_blacksmith_gavel");
        oldHammer.setDamageValue(13);
        ((BasicInventorySlot) source.getInventorySlots(null).get(11))
                .setStackUnchecked(oldCore.copy());
        ((BasicInventorySlot) source.getInventorySlots(null).get(12))
                .setStackUnchecked(oldHammer.copy());
        source.getReturnSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        CompoundTag legacy = save(helper, source);
        legacy.getCompound(HephaestusForgeProcessorBlockEntity.STATE_TAG)
                .remove("internal_tier");
        HephaestusForgeProcessorBlockEntity restored = place(helper, TARGET);
        restored.loadWithComponents(legacy,
                helper.getLevel().registryAccess());
        tick(restored, 1);
        helper.assertTrue(restored.getForgeTier() == 3
                        && restored.hasPendingOutput()
                        && restored.getReturnSlot().getCount() == 64,
                "Legacy physical core/hammer did not migrate into tier and return queue");
        helper.assertTrue(((BasicInventorySlot) restored.getInventorySlots(null)
                        .get(11)).createContainerSlot() == null
                        && ((BasicInventorySlot) restored.getInventorySlots(null)
                        .get(12)).createContainerSlot() == null,
                "Retired physical inputs remained visible in the menu");
        restored.getReturnSlot().setEmpty();
        List<ItemStack> returned = new ArrayList<>();
        for (int tick = 0; tick < 30 && returned.size() < 2; tick++) {
            restored.onUpdateServer();
            if (!restored.getReturnSlot().isEmpty()) {
                returned.add(restored.getReturnSlot().getStack().copy());
                restored.getReturnSlot().setEmpty();
            }
        }
        helper.assertTrue(returned.size() == 2
                        && returned.stream().anyMatch(stack ->
                        ItemStack.matches(stack, oldCore))
                        && returned.stream().anyMatch(stack ->
                        ItemStack.matches(stack, oldHammer)),
                "Legacy core and hammer were not returned with exact components");
        CompoundTag settled = save(helper, restored);
        restored.loadWithComponents(settled,
                helper.getLevel().registryAccess());
        tick(restored, 20);
        helper.assertTrue(!restored.hasPendingOutput()
                        && restored.getReturnSlot().isEmpty()
                        && restored.getForgeTier() == 3,
                "Legacy refund replayed or changed the migrated tier");

        // An old paid upgrade carried its old core in escrow and a new core
        // as a receipt. Both must be interpreted once, never paid again.
        HephaestusForgeProcessorBlockEntity paid = place(helper, POS);
        materials(paid, ritual(helper, "upgrade_tier_2"));
        seed(helper, paid, costs(ritual(helper, "upgrade_tier_2")));
        tick(paid, 1);
        int[] paidBank = bank(paid);
        CompoundTag oldPaid = save(helper, paid);
        CompoundTag state = oldPaid.getCompound(
                HephaestusForgeProcessorBlockEntity.STATE_TAG);
        state.remove("internal_tier");
        CompoundTag receipt = state.getCompound("paid_job");
        receipt.remove("source_tier");
        receipt.remove("upgrade_tier");
        receipt.putBoolean("core_upgrade", true);
        receipt.put("core", counted(markedCore(1), helper));
        receipt.put("output", counted(markedCore(2), helper));
        state.put("paid_job", receipt);
        oldPaid.put(HephaestusForgeProcessorBlockEntity.STATE_TAG, state);
        paid.loadWithComponents(oldPaid,
                helper.getLevel().registryAccess());
        helper.assertTrue(paid.getPaidUpgradeTier() == 2
                        && paid.getForgeTier() == 1
                        && paid.getPaidOutput().isEmpty()
                        && Arrays.equals(bank(paid), paidBank),
                "Legacy paid core receipt minted an item or repaid essence");
        untilTier(helper, paid, 2);
        helper.assertTrue(paid.getForgeTier() == 2
                        && paid.getNativeOutputSlot().isEmpty()
                        && Arrays.equals(bank(paid), paidBank),
                "Legacy paid upgrade did not finish as a machine-level change");
        helper.succeed();
    }

    private static HephaestusForgeProcessorBlockEntity place(
            GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos,
                ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        HephaestusForgeProcessorBlockEntity tile = helper.getBlockEntity(pos);
        tile.getNativeEnergyContainer().setEnergy(
                HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY);
        tile.setControlType(RedstoneControl.DISABLED);
        return tile;
    }

    private static void materials(HephaestusForgeProcessorBlockEntity tile,
                                  RitualView view) {
        tile.getMainInputSlot().setStack(view.mainIngredient().examples()
                .getFirst().copyWithCount(1));
        for (int index = 0; index < 8; index++)
            tile.getAuxInputSlot(index).setEmpty();
        for (int index = 0; index < view.inputs().size(); index++) {
            var input = view.inputs().get(index);
            tile.getAuxInputSlot(7 - index).setStack(input.ingredient()
                    .examples().getFirst().copyWithCount(input.amount()));
        }
    }

    private static RitualView ritual(GameTestHelper helper, String path) {
        return ForbiddenArcanusRecipeCatalog.snapshot(
                helper.getLevel().getServer()).ritualsById()
                .get(ResourceLocation.fromNamespaceAndPath(
                        "forbidden_arcanus", path));
    }

    private static ItemStack item(String path) {
        return BuiltInRegistries.ITEM.getOptional(
                ResourceLocation.fromNamespaceAndPath(
                        "forbidden_arcanus", path))
                .orElseThrow().getDefaultInstance();
    }

    private static ItemStack markedCore(int tier) {
        ItemStack stack = new ItemStack(
                HephaestusForgeLevel.values()[tier - 1].getBlock());
        CompoundTag tag = new CompoundTag();
        tag.putInt("legacy_owner_marker", 137 + tier);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static CompoundTag counted(ItemStack stack,
                                       GameTestHelper helper) {
        CompoundTag entry = new CompoundTag();
        entry.put("stack", stack.copyWithCount(1).save(
                helper.getLevel().registryAccess()));
        entry.putLong("count", stack.getCount());
        return entry;
    }

    private static int[] costs(RitualView view) {
        var values = view.essences();
        return new int[]{values.aureal(), values.souls(),
                values.blood(), values.experience()};
    }

    private static int[] bank(HephaestusForgeProcessorBlockEntity tile) {
        return new int[]{tile.getStoredEssence(0),
                tile.getStoredEssence(1), tile.getStoredEssence(2),
                tile.getStoredEssence(3)};
    }

    private static boolean auxiliaryEmpty(
            HephaestusForgeProcessorBlockEntity tile) {
        for (int index = 0; index < 8; index++)
            if (!tile.getAuxInputSlot(index).isEmpty()) return false;
        return true;
    }

    private static CompoundTag save(GameTestHelper helper,
                                    HephaestusForgeProcessorBlockEntity tile) {
        return tile.saveWithoutMetadata(helper.getLevel().registryAccess());
    }

    private static void seed(GameTestHelper helper,
                             HephaestusForgeProcessorBlockEntity tile,
                             int[] balances) {
        CompoundTag saved = save(helper, tile);
        saved.getCompound(HephaestusForgeProcessorBlockEntity.STATE_TAG)
                .putIntArray("essences", balances);
        tile.loadWithComponents(saved,
                helper.getLevel().registryAccess());
    }

    private static void tick(HephaestusForgeProcessorBlockEntity tile,
                             int ticks) {
        for (int index = 0; index < ticks; index++) tile.onUpdateServer();
    }

    private static void untilTier(GameTestHelper helper,
                                  HephaestusForgeProcessorBlockEntity tile,
                                  int tier) {
        for (int tick = 0; tick < 2_000
                && tile.getForgeTier() < tier; tick++) {
            if (tile.getNativeEnergyContainer().getEnergy()
                    < HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY / 2)
                tile.getNativeEnergyContainer().setEnergy(
                        HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY);
            tile.onUpdateServer();
        }
        helper.assertTrue(tile.getForgeTier() == tier,
                "Paid native upgrade did not reach internal level " + tier);
    }
}
