package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ClibanoRecipeView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeUpgradeable;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Actual ordinary-machine transactions with the installed 2.6.1 recipes. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClibanoMachineGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final BlockPos TARGET = new BlockPos(3, 2, 1);

    private ClibanoMachineGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void allFifteenRecipesAndReversedAlloyCommitNativeResults(GameTestHelper helper) {
        var catalog = ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer());
        List<ClibanoRecipeView> recipes = catalog.clibanoRecipes().stream()
                .filter(recipe -> recipe.id().getNamespace().equals("forbidden_arcanus")).toList();
        helper.assertTrue(recipes.size() == 15, "Expected all fifteen native recipes");
        for (ClibanoRecipeView recipe : recipes) {
            for (boolean reversed : recipe.doubleInput() ? List.of(false, true) : List.of(false)) {
                ClibanoProcessorBlockEntity tile = place(helper, POS);
                ItemStack first = recipe.ingredients().getFirst().examples().getFirst().copyWithCount(1);
                ItemStack second = recipe.doubleInput()
                        ? recipe.ingredients().get(1).examples().getFirst().copyWithCount(1) : ItemStack.EMPTY;
                ItemStack enhancer = recipe.requiredEnhancer().map(catalog.enhancersById()::get)
                        .map(view -> BuiltInRegistries.ITEM.get(view.displayItem()).getDefaultInstance())
                        .orElse(ItemStack.EMPTY);
                tile.getInputSlot(0).setStack(reversed ? second : first);
                tile.getInputSlot(1).setStack(reversed ? first : second);
                tile.getEnhancerSlot().setStack(enhancer);
                if (recipe.requiredFire() != FireType.FIRE) {
                    tile.getFireSlot().setStack(item(recipe.requiredFire() == FireType.SOUL_FIRE ? "soul" : "enchanted_soul"));
                }
                ItemStack expected = ForbiddenArcanusRecipeCatalog.assembleClibano(helper.getLevel(), recipe.id(),
                        reversed ? second : first, reversed ? first : second, recipe.requiredFire(), enhancer).orElseThrow();
                untilPending(helper, tile);
                helper.assertTrue(ItemStack.matches(expected, tile.getPendingOutputs().getFirst()),
                        "Machine output differs from native assembly: " + recipe.id());
                helper.assertTrue(tile.getInputSlot(0).isEmpty() && tile.getInputSlot(1).isEmpty()
                                && ItemStack.matches(enhancer, tile.getEnhancerSlot().getStack())
                                && Math.abs(tile.getStoredExperience() - recipe.experience()) < 1E-8,
                        "Input/enhancer or XP conservation failed: " + recipe.id());
                if (recipe.requiredFire() == FireType.FIRE) {
                    helper.assertTrue(tile.getStoredResidueCount() == 0 && tile.getPendingOutputs().get(1).isEmpty(),
                            "Ordinary fire generated residue: " + recipe.id());
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void twoIndependentSinglesProcessInOrderWithoutSpendingTheOtherSlot(GameTestHelper helper) {
        ClibanoProcessorBlockEntity tile = place(helper, POS);
        tile.getInputSlot(0).setStack(new ItemStack(Items.RAW_IRON));
        tile.getInputSlot(1).setStack(new ItemStack(Items.RAW_GOLD));
        untilPending(helper, tile);
        helper.assertTrue(tile.getPendingOutputs().getFirst().is(Items.IRON_INGOT)
                        && tile.getInputSlot(0).isEmpty() && tile.getInputSlot(1).getCount() == 1,
                "Independent single recipe spent or selected the wrong input");
        tick(tile, 1);
        tile.getNativeOutputSlot().setEmpty();
        untilPending(helper, tile);
        helper.assertTrue(tile.getPendingOutputs().getFirst().is(Items.GOLD_INGOT)
                        && tile.getInputSlot(1).isEmpty(), "Second single did not follow the first");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void feAndOutputGateIgnitionButBurningSoulsContinueWhileStopped(GameTestHelper helper) {
        ClibanoProcessorBlockEntity tile = place(helper, POS);
        tile.getInputSlot(0).setStack(new ItemStack(Items.RAW_IRON, 4));
        tile.getFireSlot().setStack(item("soul").copyWithCount(3));
        tile.getNativeEnergyContainer().setEnergy(0);
        tick(tile, 3);
        helper.assertTrue(tile.getProgress() == 0 && tile.getSoulTicks() == 0
                        && tile.getFireSlot().getCount() == 3 && tile.hasNotEnoughEnergyWarning(),
                "Unpowered machine consumed a soul or advanced");
        tile.getNativeEnergyContainer().setEnergy(ClibanoProcessorBlockEntity.ENERGY_CAPACITY);
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        long beforeEnergy = tile.getNativeEnergyContainer().getEnergy();
        tick(tile, 3);
        helper.assertTrue(tile.hasOutputBlocked() && tile.getSoulTicks() == 0 && tile.getFireSlot().getCount() == 3
                        && tile.getNativeEnergyContainer().getEnergy() == beforeEnergy,
                "Blocked output consumed FE or ignited a new soul");
        tile.getNativeOutputSlot().setEmpty();
        tile.getEnhancerSlot().setStack(item("crimson_stone"));
        tick(tile, 1);
        helper.assertTrue(tile.getFireType() == FireType.SOUL_FIRE && tile.getSoulTicks() == 3510
                        && tile.getSoulDuration() == 3510 && tile.getFireSlot().getCount() == 2,
                "Soul ignition did not use the native medium and duration enhancer");
        tile.getFireSlot().setStack(item("enchanted_soul").copyWithCount(2));
        tile.setControlType(RedstoneControl.HIGH);
        tick(tile, 7);
        helper.assertTrue(tile.getSoulTicks() == 3503 && tile.getFireType() == FireType.SOUL_FIRE
                        && tile.getFireSlot().getCount() == 2, "Stopping or swapping fuel changed the paid active flame");
        CompoundTag saved = save(helper, tile);
        saved.getCompound(ClibanoProcessorBlockEntity.STATE_TAG).putInt("soul_ticks", 1);
        tile.loadAdditional(saved, helper.getLevel().registryAccess());
        tile.setControlType(RedstoneControl.DISABLED);
        tick(tile, 1);
        helper.assertTrue(tile.getFireType() == FireType.FIRE && tile.getSoulTicks() == 0
                        && tile.getFireSlot().getCount() == 2, "Expiry did not return to FIRE before the next refill tick");
        tick(tile, 1);
        helper.assertTrue(tile.getFireType() == FireType.ENCHANTED_FIRE && tile.getSoulTicks() == 3510
                        && tile.getFireSlot().getCount() == 1, "Enchanted fuel was not consumed exactly once on refill");
        tile.getNativeEnergyContainer().setEnergy(0);
        tick(tile, 9);
        helper.assertTrue(tile.getSoulTicks() == 3501 && tile.hasNotEnoughEnergyWarning(),
                "Paid soul duration froze when FE ran out");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void blockedPreparedNativeResultResidueAndXpSurviveSaveAndRealDrop(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        List<RecipeHolder<?>> original = List.copyOf(manager.getRecipes());
        try {
            ItemStack expected = new ItemStack(Items.DIAMOND_PICKAXE);
            expected.set(DataComponents.CUSTOM_NAME, Component.literal("Clibano component fixture"));
            expected.setDamageValue(17);
            manager.replaceRecipes(List.of(fixture(helper, expected, 20, 0.75F)));
            ForbiddenArcanusRecipeCatalog.refresh(helper.getLevel().getServer());
            ClibanoProcessorBlockEntity tile = place(helper, POS);
            tile.getInputSlot(0).setStack(new ItemStack(Items.RAW_IRON, 2));
            tile.getFireSlot().setStack(item("enchanted_soul"));
            untilPending(helper, tile);
            ItemStack expectedResidue = item("deorum_nugget").copyWithCount(2);
            helper.assertTrue(ItemStack.matches(expected, tile.getPendingOutputs().getFirst())
                            && ItemStack.matches(expectedResidue, tile.getPendingOutputs().get(1))
                            && tile.getStoredExperience() == 0.75 && tile.getStoredResidueCount() == 0,
                    "Guaranteed residue did not use native one-unit to two-nugget aggregation");
            tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
            tile.getResidueOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
            int paidTicks = tile.getSoulTicks();
            tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
            tick(tile, 30);
            helper.assertTrue(ItemStack.matches(expected, tile.getPendingOutputs().getFirst())
                            && ItemStack.matches(expectedResidue, tile.getPendingOutputs().get(1))
                            && tile.getInputSlot(0).getCount() == 1 && tile.getStoredExperience() == 0.75
                            && tile.getSoulTicks() == paidTicks - 30,
                    "Blocked restart rerolled, reassembled, repaid XP or froze the soul timer");
            List<ItemEntity> drops = new ArrayList<>();
            BlockPos absolute = tile.getBlockPos();
            drops.add(new ItemEntity(helper.getLevel(), absolute.getX(), absolute.getY(), absolute.getZ(),
                    new ItemStack(ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK.get(), 3)));
            NeoForge.EVENT_BUS.post(new BlockDropsEvent(helper.getLevel(), absolute, tile.getBlockState(),
                    tile, drops, null, ItemStack.EMPTY));
            List<ItemStack> stateful = drops.stream().map(ItemEntity::getItem)
                    .filter(stack -> stack.has(DataComponents.BLOCK_ENTITY_DATA)).toList();
            helper.assertTrue(stateful.size() == 1 && stateful.getFirst().getCount() == 1
                            && drops.stream().mapToInt(drop -> drop.getItem().getCount()).sum() == 3,
                    "Machine drop cloned prepared work onto multiple physical items");
            ItemStack savedItem = stateful.getFirst();
            helper.setBlock(POS, Blocks.AIR);
            ClibanoProcessorBlockEntity restored = place(helper, TARGET);
            helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null,
                    helper.absolutePos(TARGET), savedItem), "Dropped item lost its custom Clibano state");
            restored.applyComponentsFromItemStack(savedItem);
            helper.assertTrue(ItemStack.matches(expected, restored.getPendingOutputs().getFirst())
                            && ItemStack.matches(expectedResidue, restored.getPendingOutputs().get(1))
                            && restored.getStoredExperience() == 0.75 && restored.getInputSlot(0).getCount() == 1,
                    "Placement lost committed outputs, XP or remaining materials");
            restored.setControlType(RedstoneControl.HIGH);
            restored.getNativeOutputSlot().setEmpty();
            restored.getResidueOutputSlot().setEmpty();
            tick(restored, 12);
            helper.assertTrue(!restored.hasPendingOutput() && ItemStack.matches(expected, restored.getNativeOutputSlot().getStack())
                            && ItemStack.matches(expectedResidue, restored.getResidueOutputSlot().getStack())
                            && restored.getStoredExperience() == 0.75 && restored.getInputSlot(0).getCount() == 1,
                    "Draining prepared work repeated production or lost one of its outputs");
        } finally {
            manager.replaceRecipes(original);
            ForbiddenArcanusRecipeCatalog.refresh(helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void allResidueIdentitiesAndUnknownTypesSurviveAndRespectSharedCapacity(GameTestHelper helper) {
        ClibanoProcessorBlockEntity tile = place(helper, POS);
        var catalog = ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer());
        CompoundTag saved = save(helper, tile);
        ListTag residues = new ListTag();
        catalog.residuesById().keySet().forEach(id -> residues.add(residue(id, 1)));
        ResourceLocation missing = ResourceLocation.parse("removed_pack:retained_residue");
        residues.add(residue(missing, 7));
        saved.getCompound(ClibanoProcessorBlockEntity.STATE_TAG).put("residues", residues);
        tile.loadAdditional(saved, helper.getLevel().registryAccess());
        Map<ResourceLocation, Integer> expected = tile.getResidueCounts();
        tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
        helper.assertTrue(expected.equals(tile.getResidueCounts()) && expected.size() == 12
                        && tile.getStoredResidueCount() == 18 && expected.get(missing) == 7,
                "Typed residue storage lost native or unknown registry identities");
        ListTag full = new ListTag();
        full.add(residue(missing, ClibanoProcessorBlockEntity.RESIDUE_CAPACITY));
        saved = save(helper, tile);
        saved.getCompound(ClibanoProcessorBlockEntity.STATE_TAG).put("residues", full);
        tile.loadAdditional(saved, helper.getLevel().registryAccess());
        tile.getInputSlot(0).setStack(new ItemStack(Items.RAW_IRON));
        tile.getFireSlot().setStack(item("soul"));
        tick(tile, 10);
        helper.assertTrue(tile.hasOutputBlocked() && tile.getProgress() == 0 && tile.getInputSlot(0).getCount() == 1
                        && tile.getFireSlot().getCount() == 1 && tile.getStoredResidueCount() == 64,
                "Full shared residue capacity allowed another operation or discarded an unknown residue");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void countsPreserveProgressButComponentsEnhancerFireAndReloadResetIt(GameTestHelper helper) {
        ClibanoProcessorBlockEntity tile = place(helper, POS);
        tile.getInputSlot(0).setStack(new ItemStack(Items.RAW_IRON, 16));
        tick(tile, 10);
        helper.assertTrue(tile.getProgress() == 10, "Could not establish in-progress work");
        tile.getInputSlot(0).growStack(1, Action.EXECUTE);
        tick(tile, 1);
        helper.assertTrue(tile.getProgress() == 11, "Refilling an unchanged material reset progress");
        ItemStack named = tile.getInputSlot(0).getStack().copy();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Changed recipe context"));
        tile.getInputSlot(0).setStack(named);
        tick(tile, 1);
        helper.assertTrue(tile.getProgress() == 1, "Input component changes retained stale progress");
        tick(tile, 4);
        tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
        tick(tile, 1);
        helper.assertTrue(tile.getProgress() == 6, "Save/load discarded unchanged in-progress work");
        ForbiddenArcanusRecipeCatalog.refresh(helper.getLevel().getServer());
        tick(tile, 1);
        helper.assertTrue(tile.getProgress() == 1, "Published catalog revision retained stale progress");
        tick(tile, 4);
        tile.getEnhancerSlot().setStack(item("artisan_relic"));
        tick(tile, 1);
        helper.assertTrue(tile.getProgress() == 1, "Enhancer changes retained stale progress");
        tick(tile, 4);
        tile.getFireSlot().setStack(item("soul"));
        tick(tile, 1);
        helper.assertTrue(tile.getFireType() == FireType.SOUL_FIRE && tile.getProgress() == 1,
                "Fire change retained the old fire's progress");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void slotPermissionsFeSidesAndSupportedUpgradesFollowTheNativeContract(GameTestHelper helper) {
        ClibanoProcessorBlockEntity tile = place(helper, POS);
        tile.getInputSlot(0).setStack(new ItemStack(Items.RAW_IRON, 2));
        tile.getFireSlot().setStack(item("soul").copyWithCount(2));
        tile.getEnhancerSlot().setStack(item("artisan_relic"));
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.IRON_INGOT, 2));
        tile.getResidueOutputSlot().setStack(item("deorum_nugget").copyWithCount(2));
        for (var slot : List.of(tile.getInputSlot(0), tile.getFireSlot(), tile.getEnhancerSlot())) {
            helper.assertTrue(slot.extractItem(1, Action.SIMULATE, AutomationType.EXTERNAL).isEmpty()
                            && slot.extractItem(1, Action.EXECUTE, AutomationType.EXTERNAL).isEmpty(),
                    "An input/context slot can be extracted by external automation");
            int index = tile.getInventorySlots(null).indexOf(slot);
            helper.assertTrue(index >= 0 && tile.extractItem(index, 1, null, Action.EXECUTE).isEmpty(),
                    "Unsided capability widened context-slot extraction permissions");
        }
        for (var slot : List.of(tile.getNativeOutputSlot(), tile.getResidueOutputSlot())) {
            ItemStack sample = slot.getStack().copyWithCount(1);
            helper.assertTrue(!slot.insertItem(sample, Action.SIMULATE, AutomationType.EXTERNAL).isEmpty()
                            && slot.extractItem(1, Action.SIMULATE, AutomationType.EXTERNAL).getCount() == 1,
                    "Output slot lost output-only automation permissions");
        }
        helper.assertTrue(tile.getInventorySlots(Direction.UP).containsAll(List.of(tile.getInputSlot(0),
                        tile.getInputSlot(1), tile.getFireSlot(), tile.getEnhancerSlot(), tile.getNativeOutputSlot(),
                        tile.getResidueOutputSlot())), "Top item port omitted declared slots");
        for (RelativeSide side : RelativeSide.values()) {
            helper.assertTrue(tile.getConfig().getConfig(TransmissionType.ENERGY).getDataType(side) == DataType.INPUT,
                    "FE input is not enabled on " + side);
        }
        helper.assertTrue(tile.getComponent().getSupportedTypes().equals(Set.of(Upgrade.SPEED, Upgrade.ENERGY))
                        && !Attribute.has(tile.getBlockState().getBlock(), AttributeUpgradeable.class)
                        && !tile.mekanismMagicSupportsPatternAutomation(),
                "Clibano enabled unsupported upgrades, installer migration or external pattern automation");
        tile.getComponent().addUpgrades(Upgrade.ENERGY, 8);
        tile.recalculateUpgrades(Upgrade.ENERGY);
        helper.assertTrue(tile.getNativeEnergyContainer().getMaxEnergy() > ClibanoProcessorBlockEntity.ENERGY_CAPACITY,
                "Native energy upgrades did not expand FE capacity");
        tile.getComponent().addUpgrades(Upgrade.SPEED, 8);
        tile.recalculateUpgrades(Upgrade.SPEED);
        helper.assertTrue(tile.getComponent().getUpgrades(Upgrade.SPEED) == 8, "Native speed upgrades were rejected");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void speedEightKeepsActiveAcrossDeliveryButStopsWhenBlockedUnpoweredOrDisabled(GameTestHelper helper) {
        ClibanoProcessorBlockEntity tile = place(helper, POS);
        tile.getComponent().addUpgrades(Upgrade.SPEED, 8);
        tile.recalculateUpgrades(Upgrade.SPEED);
        tile.getInputSlot(0).setStack(new ItemStack(Items.RAW_IRON, 64));
        int deliveredTransactions = 0;
        for (int index = 0; index < 32; index++) {
            tile.getNativeEnergyContainer().setEnergy(ClibanoProcessorBlockEntity.ENERGY_CAPACITY);
            boolean delivering = tile.hasPendingOutput();
            int beforeInput = tile.getInputSlot(0).getCount();
            double beforeExperience = tile.getStoredExperience();
            long beforeEnergy = tile.getNativeEnergyContainer().getEnergy();
            tile.onUpdateServer();
            helper.assertTrue(tile.getActive(), "A continuous speed-8 FIRE job flickered inactive on tick " + index);
            if (delivering) {
                deliveredTransactions++;
                helper.assertTrue(!tile.hasPendingOutput() && tile.getInputSlot(0).getCount() == beforeInput
                                && tile.getStoredExperience() == beforeExperience && tile.getProgress() == 0
                                && tile.getNativeEnergyContainer().getEnergy() == beforeEnergy,
                        "Keeping active also started or paid for another transaction on the delivery tick");
            }
        }
        helper.assertTrue(deliveredTransactions >= 2, "Regression did not cross repeated completion/delivery boundaries");

        untilPending(helper, tile);
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        tick(tile, 1);
        helper.assertTrue(!tile.getActive() && tile.hasOutputBlocked() && tile.hasPendingOutput(),
                "A blocked committed output kept the machine active");
        tile.getNativeOutputSlot().setEmpty();
        for (int index = 0; index < 8 && tile.hasPendingOutput(); index++) {
            tick(tile, 1);
        }
        helper.assertTrue(!tile.hasPendingOutput() && !tile.getActive(),
                "A stopped machine became active solely by draining a prepared output");

        untilPending(helper, tile);
        int beforeInput = tile.getInputSlot(0).getCount();
        tile.getNativeEnergyContainer().setEnergy(0);
        tick(tile, 1);
        helper.assertTrue(!tile.hasPendingOutput() && !tile.getActive() && tile.getInputSlot(0).getCount() == beforeInput,
                "Unpowered delivery advertised ongoing processing or consumed another input");

        tile.getNativeEnergyContainer().setEnergy(ClibanoProcessorBlockEntity.ENERGY_CAPACITY);
        untilPending(helper, tile);
        tile.setControlType(RedstoneControl.HIGH);
        tick(tile, 1);
        helper.assertTrue(!tile.hasPendingOutput() && !tile.getActive(),
                "Redstone-disabled delivery advertised ongoing processing");

        tile.setControlType(RedstoneControl.DISABLED);
        untilPending(helper, tile);
        tile.getNativeOutputSlot().setStack(new ItemStack(Items.IRON_INGOT, 63));
        tick(tile, 1);
        helper.assertTrue(!tile.hasPendingOutput() && tile.getNativeOutputSlot().getCount() == 64
                        && !tile.getActive() && tile.hasOutputBlocked(),
                "Filling the last output space retained active despite blocking the next job");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void experienceClaimsAreBoundedAndFractionIsPaidAtMostOnce(GameTestHelper helper) {
        ClibanoProcessorBlockEntity tile = place(helper, POS);
        CompoundTag saved = save(helper, tile);
        saved.getCompound(ClibanoProcessorBlockEntity.STATE_TAG).putDouble("experience", 10_000.75);
        tile.loadAdditional(saved, helper.getLevel().registryAccess());
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        Vec3 oldPosition = player.position();
        try {
            player.setPos(Vec3.atCenterOf(tile.getBlockPos()).add(50, 0, 0));
            helper.assertTrue(tile.takeExperience(player) == 0 && tile.getStoredExperience() == 10_000.75,
                    "Remote player drained the experience bank");
            player.setPos(Vec3.atCenterOf(tile.getBlockPos()));
            helper.assertTrue(tile.takeExperience(player) == 10_000 && tile.getStoredExperience() == 0.75,
                    "A claim exceeded the bounded payout or lost the raw remainder");
            int fractional = tile.takeExperience(player);
            helper.assertTrue((fractional == 0 || fractional == 1) && tile.getStoredExperience() == 0
                            && tile.takeExperience(player) == 0,
                    "Fractional XP was paid more than once");
            tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
            helper.assertTrue(tile.takeExperience(player) == 0, "Save/load resurrected claimed experience");
        } finally {
            player.setPos(oldPosition);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void menuAuthorizationAndAutomationCannotClaimExperienceTwice(GameTestHelper helper) {
        ClibanoProcessorBlockEntity tile = place(helper, POS);
        CompoundTag saved = save(helper, tile);
        saved.getCompound(ClibanoProcessorBlockEntity.STATE_TAG).putDouble("experience", 33);
        tile.loadAdditional(saved, helper.getLevel().registryAccess());
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        Vec3 oldPosition = player.position();
        var oldMenu = player.containerMenu;
        var menu = new ClibanoProcessorContainer(610, player.getInventory(), tile);
        try {
            player.setPos(Vec3.atCenterOf(tile.getBlockPos()));
            player.containerMenu = player.inventoryMenu;
            Map<UUID, CompoundTag> before = experienceOrbs(helper, tile);
            helper.assertTrue(!menu.clickMenuButton(player, ClibanoProcessorContainer.CLAIM_EXPERIENCE)
                            && tile.getStoredExperience() == 33 && before.equals(experienceOrbs(helper, tile)),
                    "An unopened menu claimed XP or created an orb");
            player.containerMenu = menu;
            player.setPos(Vec3.atCenterOf(tile.getBlockPos()).add(50, 0, 0));
            helper.assertTrue(!menu.clickMenuButton(player, ClibanoProcessorContainer.CLAIM_EXPERIENCE)
                            && tile.getStoredExperience() == 33 && before.equals(experienceOrbs(helper, tile)),
                    "A remote menu click claimed XP or created an orb");
            tile.getNativeOutputSlot().setStack(new ItemStack(Items.IRON_INGOT));
            int outputIndex = tile.getInventorySlots(Direction.UP).indexOf(tile.getNativeOutputSlot());
            helper.assertTrue(outputIndex >= 0 && tile.extractItem(outputIndex, 1, Direction.UP, Action.EXECUTE).is(Items.IRON_INGOT)
                            && tile.getStoredExperience() == 33 && before.equals(experienceOrbs(helper, tile)),
                    "Native external output extraction claimed experience");
            player.setPos(Vec3.atCenterOf(tile.getBlockPos()));
            helper.assertTrue(menu.clickMenuButton(player, ClibanoProcessorContainer.CLAIM_EXPERIENCE)
                            && tile.getStoredExperience() == 0,
                    "An authorized open menu could not claim the experience bank");
            Map<UUID, CompoundTag> afterFirst = experienceOrbs(helper, tile);
            helper.assertTrue(!before.equals(afterFirst), "Authorized claim did not award any experience orbs");
            helper.assertTrue(menu.clickMenuButton(player, ClibanoProcessorContainer.CLAIM_EXPERIENCE)
                            && tile.getStoredExperience() == 0 && afterFirst.equals(experienceOrbs(helper, tile)),
                    "A repeated menu click paid experience twice");
        } finally {
            menu.removed(player);
            player.containerMenu = oldMenu;
            player.setPos(oldPosition);
        }
        helper.succeed();
    }

    private static ClibanoProcessorBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_BLOCK.get());
        ClibanoProcessorBlockEntity tile = helper.getBlockEntity(pos);
        tile.getNativeEnergyContainer().setEnergy(ClibanoProcessorBlockEntity.ENERGY_CAPACITY);
        tile.setControlType(RedstoneControl.DISABLED);
        return tile;
    }

    private static void untilPending(GameTestHelper helper, ClibanoProcessorBlockEntity tile) {
        for (int tick = 0; tick < 5_000 && !tile.hasPendingOutput(); tick++) {
            tile.onUpdateServer();
        }
        helper.assertTrue(tile.hasPendingOutput(), "Machine never committed a valid native recipe");
    }

    private static void tick(ClibanoProcessorBlockEntity tile, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            tile.onUpdateServer();
        }
    }

    private static CompoundTag save(GameTestHelper helper, ClibanoProcessorBlockEntity tile) {
        return tile.saveWithoutMetadata(helper.getLevel().registryAccess());
    }

    private static Map<UUID, CompoundTag> experienceOrbs(GameTestHelper helper, ClibanoProcessorBlockEntity tile) {
        Map<UUID, CompoundTag> states = new LinkedHashMap<>();
        for (ExperienceOrb orb : helper.getLevel().getEntitiesOfClass(ExperienceOrb.class,
                new AABB(tile.getBlockPos()).inflate(8))) {
            states.put(orb.getUUID(), orb.saveWithoutId(new CompoundTag()));
        }
        return states;
    }

    private static CompoundTag residue(ResourceLocation id, int count) {
        CompoundTag entry = new CompoundTag();
        entry.putString("id", id.toString());
        entry.putInt("count", count);
        return entry;
    }

    private static ItemStack item(String path) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path))
                .orElseThrow().getDefaultInstance();
    }

    private static RecipeHolder<?> fixture(GameTestHelper helper, ItemStack output, int ticks, float experience) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "forbidden_arcanus:clibano_combustion");
        json.addProperty("category", "misc");
        json.addProperty("cooking_time", ticks);
        json.addProperty("experience", experience);
        json.addProperty("fire_type", "enchanted_fire");
        JsonObject ingredient = new JsonObject();
        ingredient.addProperty("item", "minecraft:raw_iron");
        json.add("ingredients", ingredient);
        JsonObject residue = new JsonObject();
        residue.addProperty("type", "forbidden_arcanus:deorum");
        residue.addProperty("chance", 1.0);
        json.add("residue", residue);
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        json.add("result", ItemStack.CODEC.encodeStart(ops, output).getOrThrow());
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, "clibano_machine_fixture"),
                Recipe.CODEC.parse(ops, json).getOrThrow());
    }
}
