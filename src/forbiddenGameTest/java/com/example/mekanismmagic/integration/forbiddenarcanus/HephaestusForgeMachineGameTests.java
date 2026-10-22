package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualView;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import com.stal111.forbidden_arcanus.common.essence.EssenceStorage;
import com.stal111.forbidden_arcanus.common.essence.EssenceValue;
import com.stal111.forbidden_arcanus.core.init.ModDataComponents;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.RelativeSide;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeUpgradeable;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import java.util.Set;

/** Paid jobs and physical essence containers in the actual registered machine. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HephaestusForgeMachineGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final BlockPos TARGET = new BlockPos(3, 2, 1);

    private HephaestusForgeMachineGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void allTenCreateItemRitualsPayNativeCostsAndAssembleOnce(GameTestHelper helper) {
        var recipes = HephaestusForge261Bridge.itemRecipes(helper.getLevel().getServer()).stream()
                .filter(recipe -> recipe.view().resultType().getPath().equals("create_item")).toList();
        helper.assertTrue(recipes.size() == 10, "Expected all ten supported create_item rituals");
        for (var recipe : recipes) {
            var tile = place(helper, POS);
            configure(helper, tile, recipe.view(), 1);
            int[] initial = HephaestusForge261Bridge.capacities(tile.getForgeTier());
            seed(helper, tile, initial);
            ItemStack mainBefore = tile.getMainInputSlot().getStack().copy();
            int tierBefore = tile.getForgeTier();
            List<ItemStack> enhancersBefore = enhancers(tile);
            var nativeRitual = helper.getLevel().registryAccess().registryOrThrow(FARegistries.RITUAL).get(recipe.id());
            ItemStack expected = nativeRitual.result().getResultItem(mainBefore.copy());
            int[] raw = amounts(recipe.view());
            tick(tile, 1);
            helper.assertTrue(tile.hasPaidJob() && ItemStack.matches(expected, tile.getPaidOutput())
                            && tile.getMainInputSlot().isEmpty() && auxiliaries(tile).stream().allMatch(ItemStack::isEmpty),
                    "Native result or material payment differs for " + recipe.id());
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(tile.getStoredEssence(channel) == initial[channel] - Math.min(initial[channel], raw[channel]),
                        "Raw native payment differs on channel " + channel + " for " + recipe.id());
            }
            helper.assertTrue(tile.getForgeTier() == tierBefore
                            && sameStacks(enhancersBefore, enhancers(tile)), "Payment changed tier or retained enhancers");
            int[] afterPayment = bank(tile);
            untilPending(helper, tile);
            helper.assertTrue(ItemStack.matches(expected, tile.getPendingOutputs().getFirst())
                            && Arrays.equals(afterPayment, bank(tile)),
                    "Advancing a paid ritual repeated resource payment for " + recipe.id());
            tick(tile, 1);
            helper.assertTrue(ItemStack.matches(expected, tile.getNativeOutputSlot().getStack()) && !tile.hasPaidJob(),
                    "Prepared native ritual result was not delivered for " + recipe.id());
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void everyEssenceShortageMissingMaterialAndFeRejectBeforePayment(GameTestHelper helper) {
        RitualView ritual = ritual(helper, "quantum_catcher");
        int[] costs = amounts(ritual);
        for (int missing = 0; missing < 6; missing++) {
            var tile = place(helper, POS);
            configure(helper, tile, ritual, 1);
            int[] initial = costs.clone();
            if (missing < 4) initial[missing]--;
            seed(helper, tile, initial);
            if (missing == 4) {
                tile.getAuxInputSlot(2).shrinkStack(1, Action.EXECUTE);
            }
            if (missing == 5) tile.getNativeEnergyContainer().setEnergy(0);
            ItemStack main = tile.getMainInputSlot().getStack().copy();
            List<ItemStack> aux = auxiliaries(tile);
            tick(tile, 5);
            helper.assertTrue(!tile.hasPaidJob() && tile.getProgress() == 0 && Arrays.equals(initial, bank(tile))
                            && ItemStack.matches(main, tile.getMainInputSlot().getStack())
                            && sameStacks(aux, auxiliaries(tile)),
                    "A partial payment occurred for missing resource/material/FE case " + missing);
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void wrongUpgradeTierAndInsufficientRealCoreCannotConsumeAnything(GameTestHelper helper) {
        var catalog = ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer());
        int rejected = 0;
        int foreign = 0;
        for (RitualView ritual : catalog.rituals()) {
            if (!ritual.resultType().getPath().equals("upgrade_tier")) continue;
            if (ritual.id().equals(ResourceLocation.fromNamespaceAndPath(
                    "forbidden_arcanus", "package_forge"))) {
                foreign++;
                helper.assertTrue(ModCompatibility.loaded("packaged_faa")
                                && HephaestusForge261Bridge.upgradeRecipes(
                                helper.getLevel().getServer()).stream()
                                .noneMatch(view -> view.id().equals(ritual.id())),
                        "Foreign packaged-forge ritual entered our upgrade path");
                continue;
            }
            var tile = place(helper, POS);
            configure(helper, tile, ritual, 1);
            // A machine already at the target tier cannot pay an old-tier
            // upgrade again, even with enough essence for that ritual.
            setTier(helper, tile, ritual.tier() + 1);
            seed(helper, tile, HephaestusForge261Bridge.capacities(tile.getForgeTier()));
            ItemStack main = tile.getMainInputSlot().getStack().copy();
            int[] before = bank(tile);
            tick(tile, 2);
            helper.assertTrue(!tile.hasPaidJob() && ItemStack.matches(main, tile.getMainInputSlot().getStack())
                            && Arrays.equals(before, bank(tile)),
                    "Wrong internal tier executed a native upgrade ritual: " + ritual.id());
            rejected++;
        }
        helper.assertTrue(rejected == 4, "All four exact-tier upgrade requirements were not exercised");
        helper.assertTrue(foreign == (ModCompatibility.loaded("packaged_faa") ? 1 : 0),
                "Unexpected foreign packaged-forge ritual count");
        var tile = place(helper, POS);
        configure(helper, tile, ritual(helper, "boss_catcher"), 1);
        seed(helper, tile, HephaestusForge261Bridge.capacities(5));
        setTier(helper, tile, 1);
        tick(tile, 1);
        helper.assertTrue(!tile.hasPaidJob() && tile.getMainInputSlot().getCount() == 1,
                "A low-tier real core bypassed a higher-tier native ritual");
        CompoundTag fakeTier = new CompoundTag();
        fakeTier.putInt("forge_tier", 5);
        ItemStack spoof = new ItemStack(Items.STICK);
        spoof.set(DataComponents.CUSTOM_DATA, CustomData.of(fakeTier));
        helper.assertTrue(HephaestusForge261Bridge.forgeTier(spoof) == 0
                        && tile.getForgeTier() == 1,
                "Arbitrary item NBT forged a machine tier qualification");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void paidJobPausesForItsContextAndSurvivesMissingCatalogSaveAndDrop(GameTestHelper helper) {
        var tile = place(helper, POS);
        RitualView recipe = ritual(helper, "quantum_catcher");
        configure(helper, tile, recipe, 1);
        seed(helper, tile, HephaestusForge261Bridge.capacities(1));
        tick(tile, 1);
        ItemStack expected = tile.getPaidOutput();
        int[] paidBank = bank(tile);
        int progress = tile.getProgress();
        tile.getNativeEnergyContainer().setEnergy(0);
        tick(tile, 4);
        helper.assertTrue(tile.hasPaidJob() && tile.isPaidJobPaused() && tile.getProgress() == progress
                        && Arrays.equals(paidBank, bank(tile)), "Power loss discarded or advanced paid work");
        tile.getNativeEnergyContainer().setEnergy(HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY);
        tick(tile, 1);
        helper.assertTrue(tile.getProgress() == progress + 1, "Restoring FE did not resume paid work");
        tile.getEnhancerSlot(0).setStack(item("artisan_relic"));
        tick(tile, 2);
        helper.assertTrue(tile.hasContextMismatch() && tile.getProgress() == progress + 1,
                "Changed active enhancer did not pause the paid context");
        tile.getEnhancerSlot(0).setEmpty();
        tile.getEnhancerSlot(3).setStack(item("artisan_relic"));
        tick(tile, 1);
        helper.assertTrue(!tile.hasContextMismatch() && tile.getProgress() == progress + 2,
                "A locked enhancer became a paid-job requirement");
        CompoundTag update = tile.getReducedUpdateTag(helper.getLevel().registryAccess());
        helper.assertTrue(recipe.magicCircle().equals(tile.getActiveMagicCircleId())
                        && update.getBoolean("hephaestus_paid") && !update.contains(HephaestusForgeProcessorBlockEntity.STATE_TAG),
                "World circle state is missing or leaks the entire persistent transaction");
        ForbiddenArcanusRecipeCatalog.clear();
        try {
            tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
            tick(tile, 3);
            helper.assertTrue(ItemStack.matches(expected, tile.getPaidOutput()) && Arrays.equals(paidBank, bank(tile)),
                    "A disappeared/reloaded recipe invalidated its already-paid result");
            List<ItemEntity> drops = new ArrayList<>();
            BlockPos absolute = tile.getBlockPos();
            drops.add(new ItemEntity(helper.getLevel(), absolute.getX(), absolute.getY(), absolute.getZ(),
                    new ItemStack(ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get(), 2)));
            NeoForge.EVENT_BUS.post(new BlockDropsEvent(helper.getLevel(), absolute, tile.getBlockState(), tile,
                    drops, null, ItemStack.EMPTY));
            List<ItemStack> stateful = drops.stream().map(ItemEntity::getItem)
                    .filter(stack -> stack.has(DataComponents.BLOCK_ENTITY_DATA)).toList();
            helper.assertTrue(stateful.size() == 1 && stateful.getFirst().getCount() == 1,
                    "A paid forge job was copied onto more than one dropped machine");
            helper.setBlock(POS, Blocks.AIR);
            var restored = place(helper, TARGET);
            helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null,
                    helper.absolutePos(TARGET), stateful.getFirst()), "Paid forge data was absent from its dropped item");
            restored.applyComponentsFromItemStack(stateful.getFirst());
            restored.getNativeOutputSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
            int pausedProgress = restored.getProgress();
            tick(restored, 6);
            helper.assertTrue(restored.hasPaidJob() && restored.getProgress() == pausedProgress
                            && Arrays.equals(paidBank, bank(restored)), "Blocked paid placement repeated or lost its payment");
            restored.getNativeOutputSlot().setEmpty();
            untilPending(helper, restored);
            tick(restored, 1);
            helper.assertTrue(ItemStack.matches(expected, restored.getNativeOutputSlot().getStack())
                            && !restored.hasPaidJob() && Arrays.equals(paidBank, bank(restored)),
                    "Paid job could not finish without a live recipe or new material payment");
        } finally {
            ForbiddenArcanusRecipeCatalog.refresh(helper.getLevel().getServer());
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void stackedSixtyFourStorageJarsKeepOneWorkingContainerAndEveryRemainder(GameTestHelper helper) {
        var tile = place(helper, POS);
        setTier(helper, tile, 5);
        ItemStack original = storage("essence_utrem_jar", EssenceType.AUREAL, 25, 1_000);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Original filled jar batch"));
        tile.getEssenceInputSlot().setStack(original.copyWithCount(64));
        tile.getReturnSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        tick(tile, 1);
        helper.assertTrue(tile.getStoredEssence(0) == 10 && tile.getEssenceInputSlot().getCount() == 63
                        && ItemStack.isSameItemSameComponents(original, tile.getEssenceInputSlot().getStack())
                        && tile.getWorkingEssenceSlot().getCount() == 1
                        && tile.getWorkingEssenceSlot().getStack().get(ModDataComponents.ESSENCE_STORAGE).value().amount() == 15,
                "A first partial transfer modified all sixty-four containers or lost the working singleton");
        tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
        tick(tile, 10);
        helper.assertTrue(tile.getStoredEssence(0) == 25 && tile.getEssenceInputSlot().getCount() == 63
                        && tile.getWorkingEssenceSlot().isEmpty() && tile.hasPendingOutput()
                        && tile.getPendingOutputs().get(1).getCount() == 1,
                "Blocked empty-container return duplicated resources or began another jar");
        tile.getReturnSlot().setEmpty();
        int returned = 0;
        for (int count = 0; count < 500; count++) {
            tick(tile, 1);
            returned += tile.getReturnSlot().getCount();
            tile.getReturnSlot().setEmpty();
            if (tile.getEssenceInputSlot().isEmpty() && tile.getWorkingEssenceSlot().isEmpty() && !tile.hasPendingOutput()) break;
        }
        helper.assertTrue(tile.getStoredEssence(0) == 64 * 25 && returned == 64
                        && tile.getEssenceInputSlot().isEmpty() && tile.getWorkingEssenceSlot().isEmpty(),
                "Processing the stored batch did not conserve all sixty-four jars and finite essence");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fourChannelsUseNativeInputsAndPartialCapacityNeverConsumesAnOversizedDataItem(GameTestHelper helper) {
        List<ItemStack> inputs = List.of(item("aureal_bottle"), item("soul"),
                storage("blood_test_tube", EssenceType.BLOOD, 25, 3_000), item("xpetrified_orb"));
        for (int channel = 0; channel < 4; channel++) {
            var tile = place(helper, POS);
            ItemStack source = inputs.get(channel);
            int[] capacity = HephaestusForge261Bridge.capacities(1);
            var expected = HephaestusForge261Bridge.prepareEssenceInput(helper.getLevel(), source, capacity).orElseThrow();
            tile.getEssenceInputSlot().setStack(source.copy());
            tick(tile, 1);
            helper.assertTrue(tile.getStoredEssence(channel) == expected.amount(), "Native channel transfer changed amount/type");
            for (int other = 0; other < 4; other++) {
                if (other != channel) helper.assertTrue(tile.getStoredEssence(other) == 0, "Essence crossed channel boundaries");
            }
        }
        var tile = place(helper, POS);
        int[] capacity = HephaestusForge261Bridge.capacities(1);
        int[] initial = new int[4];
        initial[0] = capacity[0] - 5;
        seed(helper, tile, initial);
        tile.getEssenceInputSlot().setStack(item("aureal_bottle").copyWithCount(2));
        tick(tile, 2);
        helper.assertTrue(tile.getStoredEssence(0) == initial[0] && tile.getEssenceInputSlot().getCount() == 2
                        && tile.getWorkingEssenceSlot().isEmpty(), "A whole-value item was wasted against insufficient capacity");
        tile.getEssenceInputSlot().setStack(storage("essence_utrem_jar", EssenceType.AUREAL, 25, 1_000));
        tick(tile, 1);
        helper.assertTrue(tile.getStoredEssence(0) == capacity[0]
                        && tile.getWorkingEssenceSlot().getStack().get(ModDataComponents.ESSENCE_STORAGE).value().amount() == 20,
                "Partial storage input removed more essence than the offered five units of space");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void loadedLowerTierPreservesOverfullBalancesUntilSpent(GameTestHelper helper) {
        var tile = place(helper, POS);
        configure(helper, tile, ritual(helper, "quantum_catcher"), 1);
        setTier(helper, tile, 5);
        int[] upper = HephaestusForge261Bridge.capacities(5);
        seed(helper, tile, upper);
        setTier(helper, tile, 1);
        tile.getEssenceInputSlot().setStack(item("soul").copyWithCount(2));
        tile.setControlType(mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.HIGH);
        tick(tile, 3);
        helper.assertTrue(!tile.hasPaidJob() && Arrays.equals(upper, bank(tile)) && tile.getEssenceInputSlot().getCount() == 2,
                "Internal tier load truncated overfull balances or consumed an essence input");
        tile.setControlType(mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl.DISABLED);
        tick(tile, 1);
        helper.assertTrue(tile.hasPaidJob() && tile.getEssenceInputSlot().getCount() == 2,
                "An overfull bank prevented a qualified lower-tier ritual from spending its contents");
        int[] raw = amounts(ritual(helper, "quantum_catcher"));
        for (int channel = 0; channel < 4; channel++) {
            helper.assertTrue(tile.getStoredEssence(channel) == upper[channel] - raw[channel],
                    "Internal tier load truncated a channel to its new capacity");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void creativeUpgradeStopsInputsAndWaivesOnlyTheFourEssenceDebits(GameTestHelper helper) {
        var tile = place(helper, POS);
        configure(helper, tile, ritual(helper, "quantum_catcher"), 1);
        tile.getEssenceInputSlot().setStack(item("soul").copyWithCount(64));
        tile.getComponent().addUpgrades(MagicUpgrades.creativeMagic(), 1);
        tick(tile, 1);
        helper.assertTrue(tile.isCreativeMagic() && tile.hasPaidJob() && Arrays.equals(new int[4], bank(tile))
                        && tile.getEssenceInputSlot().getCount() == 64 && tile.getMainInputSlot().isEmpty()
                        && tile.getNativeEnergyContainer().getEnergy() < HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY,
                "Creative magic generated essence, consumed an input, or waived materials/FE");
        tile.getComponent().removeUpgrade(MagicUpgrades.creativeMagic(), true);
        tick(tile, 2);
        helper.assertTrue(tile.hasPaidJob() && tile.getEssenceInputSlot().getCount() == 62
                        && Arrays.equals(new int[]{0, 2, 0, 0}, bank(tile)),
                "Ending creative mode did not resume explicit input or charged the already-paid ritual again");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void paidJobAcceptsFiniteRefillsWithoutChangingItsReceiptAndWaitsForContainerReturn(GameTestHelper helper) {
        var tile = place(helper, POS);
        RitualView recipe = ritual(helper, "quantum_catcher");
        configure(helper, tile, recipe, 1);
        seed(helper, tile, amounts(recipe));
        tick(tile, 1);
        helper.assertTrue(tile.hasPaidJob() && Arrays.equals(new int[4], bank(tile)),
                "Could not establish an exactly paid ritual with room for future essence");
        ItemStack paidOutput = tile.getPaidOutput();
        List<ItemStack> paidAuxiliary = auxiliaries(tile);
        String paidId = tile.getPaidRecipeId();
        tile.getEssenceInputSlot().setStack(storage("blood_test_tube", EssenceType.BLOOD, 25, 3_000).copyWithCount(2));
        tile.getReturnSlot().setStack(new ItemStack(Items.COBBLESTONE, 64));
        int initialProgress = tile.getProgress();
        tick(tile, 1);
        helper.assertTrue(tile.getStoredEssence(2) == 10 && tile.getEssenceInputSlot().getCount() == 1
                        && tile.getWorkingEssenceSlot().getStack().get(ModDataComponents.ESSENCE_STORAGE).value().amount() == 15
                        && tile.getProgress() == initialProgress + 1,
                "Paid processing did not receive exactly one native ten-unit blood transfer");
        tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
        tick(tile, 1);
        helper.assertTrue(tile.getStoredEssence(2) == 20 && tile.getProgress() == initialProgress + 2
                        && tile.getWorkingEssenceSlot().getStack().get(ModDataComponents.ESSENCE_STORAGE).value().amount() == 5,
                "Restart duplicated or lost a partial paid-job refill");
        tick(tile, 1);
        int blockedProgress = tile.getProgress();
        helper.assertTrue(tile.getStoredEssence(2) == 25 && tile.hasPaidJob() && tile.hasPendingOutput()
                        && tile.getWorkingEssenceSlot().isEmpty() && blockedProgress == initialProgress + 2,
                "The final partial refill did not preserve its empty tube before pausing progress");
        tick(tile, 10);
        helper.assertTrue(tile.getStoredEssence(2) == 25 && tile.getEssenceInputSlot().getCount() == 1
                        && tile.getProgress() == blockedProgress && tile.getMainInputSlot().isEmpty()
                        && sameStacks(paidAuxiliary, auxiliaries(tile))
                        && paidId.equals(tile.getPaidRecipeId()) && ItemStack.matches(paidOutput, tile.getPaidOutput()),
                "Blocked return consumed the next tube or altered paid materials/result");
        tile.getReturnSlot().setEmpty();
        for (int tick = 0; tick < 8 && tile.hasPendingOutput(); tick++) tile.onUpdateServer();
        helper.assertTrue(!tile.hasPendingOutput() && tile.getStoredEssence(2) == 25
                        && tile.getEssenceInputSlot().getCount() == 1,
                "The return-delivery tick also consumed the next essence container");
        tick(tile, 1);
        helper.assertTrue(tile.getStoredEssence(2) == 35 && tile.getEssenceInputSlot().isEmpty()
                        && tile.getProgress() == blockedProgress + 1
                        && ItemStack.matches(paidOutput, tile.getPaidOutput()),
                "Resuming a paid job failed to refill independently of its fixed payment");
        var starting = place(helper, TARGET);
        configure(helper, starting, recipe, 1);
        int[] justShort = amounts(recipe);
        justShort[2] -= 10;
        seed(helper, starting, justShort);
        starting.getEssenceInputSlot().setStack(storage("blood_test_tube", EssenceType.BLOOD, 25, 3_000));
        tick(starting, 1);
        helper.assertTrue(starting.hasPaidJob() && Arrays.equals(new int[4], bank(starting))
                        && starting.getWorkingEssenceSlot().getStack().get(ModDataComponents.ESSENCE_STORAGE).value().amount() == 15,
                "The tick which filled the last missing blood and paid the job also performed a second storage transfer");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void paidContextHashIsCachedUntilEnabledComponentsRevisionOrLoadChange(GameTestHelper helper) {
        var tile = place(helper, POS);
        RitualView recipe = ritual(helper, "quantum_catcher");
        configure(helper, tile, recipe, 1);
        seed(helper, tile, amounts(recipe));
        tick(tile, 1);
        helper.assertTrue(tile.hasPaidJob() && tile.contextResolutionCount() == 1,
                "Initial paid context was not resolved once");
        tile.getEnhancerSlot(3).setStack(item("artisan_relic"));
        tile.getEssenceInputSlot().setStack(item("aureal_bottle"));
        tile.getNativeEnergyContainer().setEnergy(1_000_000L);
        for (int tick = 0; tick < 200; tick++) {
            tile.getReducedUpdateTag(helper.getLevel().registryAccess());
            tile.getStoredEssence(0);
            tile.getEssenceCapacity(0);
            tile.getStatusKey();
            tile.onUpdateServer();
        }
        helper.assertTrue(tile.hasPaidJob() && tile.contextResolutionCount() == 1
                        && tile.getStoredEssence(0) == 35 && tile.getProgress() > 190,
                "Stable paid ticks, balances, FE, GUI reads or locked enhancer repeatedly hashed the context");
        int progress = tile.getProgress();
        tile.getEnhancerSlot(0).setStack(item("artisan_relic"));
        tick(tile, 4);
        helper.assertTrue(tile.hasContextMismatch() && tile.getProgress() == progress && tile.contextResolutionCount() == 2,
                "Changed enabled enhancer was not resolved exactly once before pausing");
        tile.getEnhancerSlot(0).growStack(1, Action.EXECUTE);
        tick(tile, 2);
        helper.assertTrue(tile.contextResolutionCount() == 2 && tile.getProgress() == progress,
                "Enabled enhancer stock count unnecessarily changed its unit context");
        tile.getEnhancerSlot(0).setEmpty();
        tick(tile, 1);
        helper.assertTrue(!tile.hasContextMismatch() && tile.getProgress() == progress + 1 && tile.contextResolutionCount() == 3,
                "Restoring the paid enhancer context did not resolve once and resume");
        ForbiddenArcanusRecipeCatalog.refresh(helper.getLevel().getServer());
        tick(tile, 1);
        helper.assertTrue(tile.contextResolutionCount() == 4 && !tile.hasContextMismatch(),
                "Catalog revision reused possibly invalid native holder qualification");
        tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
        tick(tile, 1);
        helper.assertTrue(tile.contextResolutionCount() == 5 && tile.hasPaidJob() && !tile.hasContextMismatch(),
                "Machine NBT load retained a stale paid-context cache");
        setTier(helper, tile, 2);
        int beforeTierChange = tile.getProgress();
        tick(tile, 2);
        helper.assertTrue(tile.hasContextMismatch() && tile.getProgress() == beforeTierChange,
                "A cached context allowed paid work to run after internal tier changed");
        setTier(helper, tile, 1);
        tick(tile, 1);
        helper.assertTrue(!tile.hasContextMismatch() && tile.getProgress() == beforeTierChange + 1,
                "Restoring the paid internal tier could not resume work");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void physicalPortsHideLegacyInputsAndProtectWorkingContainers(GameTestHelper helper) {
        var tile = place(helper, POS);
        configure(helper, tile, ritual(helper, "quantum_catcher"), 1);
        tile.getEssenceInputSlot().setStack(storage("essence_utrem_jar", EssenceType.AUREAL, 25, 1_000));
        tick(tile, 1);
        for (IInventorySlot slot : List.of(tile.getWorkingEssenceSlot(),
                tile.getInventorySlots(null).get(11), tile.getInventorySlots(null).get(12))) {
            helper.assertTrue(slot.extractItem(1, Action.SIMULATE, AutomationType.EXTERNAL).isEmpty(),
                    "A retired input or working container can be automatically extracted");
            int index = tile.getInventorySlots(null).indexOf(slot);
            helper.assertTrue(tile.extractItem(index, 1, null, Action.EXECUTE).isEmpty(),
                    "Unsided extraction widened protected-slot permissions");
        }
        helper.assertTrue(((BasicInventorySlot) tile.getInventorySlots(null).get(11)).createContainerSlot() == null
                        && ((BasicInventorySlot) tile.getInventorySlots(null).get(12)).createContainerSlot() == null
                        && !tile.getInventorySlots(Direction.UP).contains(tile.getInventorySlots(null).get(11))
                        && !tile.getInventorySlots(Direction.UP).contains(tile.getInventorySlots(null).get(12)),
                "Retired core/hammer slots remained visible or routable");
        helper.assertTrue(!tile.getInventorySlots(Direction.UP).contains(tile.getWorkingEssenceSlot())
                        && tile.getWorkingEssenceSlot().extractItem(1, Action.SIMULATE, AutomationType.MANUAL).getCount() == 1,
                "Working container is externally exposed or cannot be manually recovered");
        ItemStack filled = storage("essence_utrem_jar", EssenceType.AUREAL, 25, 1_000);
        helper.assertTrue(!tile.getWorkingEssenceSlot().insertItem(filled, Action.SIMULATE, AutomationType.EXTERNAL).isEmpty()
                        && !tile.getWorkingEssenceSlot().insertItem(filled, Action.SIMULATE, AutomationType.MANUAL).isEmpty(),
                "Working slot accepted an externally supplied extra container");
        ItemStack template = item("apply_modifier_smithing_template");
        helper.assertTrue(!tile.getMainInputSlot().isItemValid(template), "An unrelated smithing template became forge material");
        for (RelativeSide side : RelativeSide.values()) {
            helper.assertTrue(tile.getConfig().getConfig(TransmissionType.ENERGY).getDataType(side) == DataType.INPUT,
                    "FE is not available on " + side);
        }
        helper.assertTrue(tile.getComponent().getSupportedTypes().equals(Set.of(Upgrade.SPEED, Upgrade.ENERGY, MagicUpgrades.creativeMagic()))
                        && !tile.mekanismMagicSupportsPatternAutomation()
                        && !Attribute.has(tile.getBlockState().getBlock(), AttributeUpgradeable.class),
                "Unsupported pattern/installer or upgrade behavior was enabled");
        helper.succeed();
    }

    private static HephaestusForgeProcessorBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        HephaestusForgeProcessorBlockEntity tile = helper.getBlockEntity(pos);
        tile.getNativeEnergyContainer().setEnergy(HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY);
        return tile;
    }

    private static void configure(GameTestHelper helper, HephaestusForgeProcessorBlockEntity tile, RitualView ritual, int batches) {
        setTier(helper, tile, Math.max(1, ritual.tier()));
        ((BasicInventorySlot) tile.getMainInputSlot()).setStackUnchecked(ritual.mainIngredient().examples().getFirst().copyWithCount(batches));
        for (int index = 0; index < ritual.inputs().size(); index++) {
            var input = ritual.inputs().get(index);
            ((BasicInventorySlot) tile.getAuxInputSlot((index * 3 + 2) % 8)).setStackUnchecked(
                    input.ingredient().examples().getFirst().copyWithCount(input.amount() * batches));
        }
        var catalog = ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer());
        for (int index = 0; index < ritual.enhancers().size(); index++) {
            var view = catalog.enhancersById().get(ritual.enhancers().get(index));
            tile.getEnhancerSlot(index).setStack(BuiltInRegistries.ITEM.get(view.displayItem()).getDefaultInstance());
        }
    }

    private static RitualView ritual(GameTestHelper helper, String path) {
        return ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer()).ritualsById()
                .get(ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path));
    }

    private static ItemStack item(String path) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path))
                .orElseThrow().getDefaultInstance();
    }

    private static ItemStack storage(String item, EssenceType type, int amount, int capacity) {
        ItemStack stack = item(item);
        stack.set(ModDataComponents.ESSENCE_STORAGE, new EssenceStorage(EssenceValue.of(type, amount), capacity, true));
        return stack;
    }

    private static int[] amounts(RitualView view) {
        var value = view.essences();
        return new int[]{value.aureal(), value.souls(), value.blood(), value.experience()};
    }

    private static int[] bank(HephaestusForgeProcessorBlockEntity tile) {
        return new int[]{tile.getStoredEssence(0), tile.getStoredEssence(1), tile.getStoredEssence(2), tile.getStoredEssence(3)};
    }

    private static List<ItemStack> auxiliaries(HephaestusForgeProcessorBlockEntity tile) {
        List<ItemStack> result = new ArrayList<>();
        for (int index = 0; index < 8; index++) result.add(tile.getAuxInputSlot(index).getStack().copy());
        return result;
    }

    private static List<ItemStack> enhancers(HephaestusForgeProcessorBlockEntity tile) {
        List<ItemStack> result = new ArrayList<>();
        for (int index = 0; index < 4; index++) result.add(tile.getEnhancerSlot(index).getStack().copy());
        return result;
    }

    private static boolean sameStacks(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) {
            if (!ItemStack.matches(first.get(index), second.get(index))) return false;
        }
        return true;
    }

    private static CompoundTag save(GameTestHelper helper, HephaestusForgeProcessorBlockEntity tile) {
        return tile.saveWithoutMetadata(helper.getLevel().registryAccess());
    }

    private static void seed(GameTestHelper helper, HephaestusForgeProcessorBlockEntity tile, int[] balances) {
        CompoundTag saved = save(helper, tile);
        saved.getCompound(HephaestusForgeProcessorBlockEntity.STATE_TAG).putIntArray("essences", balances);
        tile.loadAdditional(saved, helper.getLevel().registryAccess());
    }

    private static void setTier(GameTestHelper helper, HephaestusForgeProcessorBlockEntity tile, int tier) {
        CompoundTag saved = save(helper, tile);
        saved.getCompound(HephaestusForgeProcessorBlockEntity.STATE_TAG).putInt("internal_tier", tier);
        tile.loadAdditional(saved, helper.getLevel().registryAccess());
    }

    private static void tick(HephaestusForgeProcessorBlockEntity tile, int ticks) {
        for (int index = 0; index < ticks; index++) tile.onUpdateServer();
    }

    private static void untilPending(GameTestHelper helper, HephaestusForgeProcessorBlockEntity tile) {
        for (int index = 0; index < 2_000 && !tile.hasPendingOutput(); index++) tile.onUpdateServer();
        helper.assertTrue(tile.hasPendingOutput(), "Paid forge job did not finish its finite FE processing phase");
    }
}
