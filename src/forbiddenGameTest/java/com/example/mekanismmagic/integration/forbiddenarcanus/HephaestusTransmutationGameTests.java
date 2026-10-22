package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualView;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.TransmuteInputResult;
import com.stal111.forbidden_arcanus.core.init.ModDataComponents;
import com.stal111.forbidden_arcanus.core.registry.FARegistries;
import io.netty.buffer.Unpooled;
import mekanism.api.Upgrade;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Native transmuteCopy component, quantity and paid-job regression coverage. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HephaestusTransmutationGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);
    private static final BlockPos TARGET = new BlockPos(3, 2, 1);

    private HephaestusTransmutationGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void allEightNativeTransmutationsPreserveFullComponentsAndConsumeOneMainItem(GameTestHelper helper) {
        var supported = HephaestusForge261Bridge.itemRecipes(helper.getLevel().getServer());
        var transformations = supported.stream().filter(recipe -> recipe.view().resultType().equals(id("transmute_input"))).toList();
        helper.assertTrue(supported.size() == 18 && transformations.size() == 8,
                "Expected ten create-item and eight transmutation recipes, with upgrades excluded");
        int changedDefaultDurabilities = 0;
        for (var recipe : transformations) {
            RitualView view = recipe.view();
            ItemStack source = decoratedMain(helper, view).copyWithCount(3);
            ItemStack original = source.copy();
            ItemStack expected = nativeResult(helper, view, source);
            helper.assertTrue(expected.getCount() == 1 && source.isEnchanted()
                            && source.has(ModDataComponents.ITEM_MODIFIER) && source.getDamageValue() == 37,
                    "Fixture did not exercise a single transformed unit with enchantments, wear and native modifier");
            var tile = place(helper, POS);
            helper.assertTrue(tile.getMainInputSlot().isItemValid(source), "Actual machine input validation rejected transmutable armor");
            configure(helper, tile, view, source);
            helper.assertTrue(ItemStack.matches(original, tile.getMainInputSlot().getStack()),
                    "Configure changed the three-item source fixture: original=" + stackState(original)
                            + ", configured=" + stackState(tile.getMainInputSlot().getStack()));
            seed(helper, tile, HephaestusForge261Bridge.capacities(tile.getForgeTier()));
            helper.assertTrue(ItemStack.matches(original, tile.getMainInputSlot().getStack()),
                    "NBT seed round trip changed the source before payment: original=" + stackState(original)
                            + ", restored=" + stackState(tile.getMainInputSlot().getStack()));
            var selection = selection(helper, tile);
            helper.assertTrue(selection.id().equals(view.id()) && ItemStack.matches(expected, selection.resultPreview()),
                    "Component-aware preview differs from native transmutation: " + view.id());
            ItemStack escapedPreview = selection.resultPreview();
            escapedPreview.set(DataComponents.CUSTOM_NAME, Component.literal("Mutated preview copy"));
            helper.assertTrue(ItemStack.matches(expected, selection.resultPreview()) && ItemStack.matches(original, source),
                    "Preview leaked mutable components or changed the supplied main item");
            int[] before = bank(tile);
            tick(tile, 1);
            String payment = "recipe=" + view.id() + ", paid=" + tile.hasPaidJob() + ", status=" + tile.getStatusKey()
                    + ", main=" + stackState(tile.getMainInputSlot().getStack())
                    + ", paidOutput=" + stackState(tile.getPaidOutput()) + ", auxiliary=" + auxiliaries(tile)
                    + ", tier=" + tile.getForgeTier() + ", bank=" + Arrays.toString(bank(tile));
            helper.assertTrue(tile.hasPaidJob(), "Valid transmutation was not paid: " + payment);
            helper.assertTrue(ItemStack.matches(expected, tile.getPaidOutput()),
                    "Prepared output differs from the native single-unit result: expected=" + stackState(expected) + ", " + payment);
            helper.assertTrue(tile.getMainInputSlot().getCount() == 2,
                    "Payment must consume exactly one source item from count 3, including an overstacked max-1 item: " + payment);
            helper.assertTrue(ItemStack.isSameItemSameComponents(original, tile.getMainInputSlot().getStack()),
                    "Payment changed the unconsumed source item's components: expected=" + stackState(original) + ", " + payment);
            helper.assertTrue(auxiliaries(tile).stream().allMatch(ItemStack::isEmpty),
                    "Payment consumed incorrect auxiliary amounts: " + payment);
            int[] raw = rawCosts(view);
            for (int channel = 0; channel < 4; channel++) {
                helper.assertTrue(tile.getStoredEssence(channel) == before[channel] - Math.min(before[channel], raw[channel]),
                        "Transmutation changed native essence debit on channel " + channel);
            }
            assertPreservedComponents(helper, source, tile.getPaidOutput());
            ItemStack targetDefaults = expected.getItem().getDefaultInstance();
            helper.assertTrue(source.getComponentsPatch().get(DataComponents.MAX_DAMAGE) == null
                            && source.getComponentsPatch().get(DataComponents.ATTRIBUTE_MODIFIERS) == null
                            && tile.getPaidOutput().getMaxDamage() == targetDefaults.getMaxDamage()
                            && Objects.equals(tile.getPaidOutput().get(DataComponents.ATTRIBUTE_MODIFIERS),
                            targetDefaults.get(DataComponents.ATTRIBUTE_MODIFIERS)),
                    "Unmodified source defaults replaced the target item's durability or armor attributes");
            if (source.getMaxDamage() != targetDefaults.getMaxDamage()) changedDefaultDurabilities++;
            untilPending(helper, tile);
            helper.assertTrue(ItemStack.matches(expected, roundTrip(helper, tile.getPendingOutputs().getFirst())),
                    "Prepared transmutation changed during storage/network serialization: " + view.id());
            tick(tile, 1);
            helper.assertTrue(ItemStack.matches(expected, tile.getNativeOutputSlot().getStack())
                            && tile.getMainInputSlot().getCount() == 2,
                    "Delivery repeated the operation or changed native output components: " + view.id());
        }
        helper.assertTrue(changedDefaultDurabilities > 0, "Default-durability comparison never exercised distinct source/target defaults");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void eternalModifierRetainsExplicitRemovedComponentsAcrossEveryNativeTransform(GameTestHelper helper) {
        var recipes = HephaestusForge261Bridge.itemRecipes(helper.getLevel().getServer()).stream()
                .filter(recipe -> recipe.view().resultType().equals(id("transmute_input"))).toList();
        helper.assertTrue(recipes.size() == 8, "Expected eight Eternal transformation checks");
        for (var recipe : recipes) {
            ItemStack source = decoratedMain(helper, recipe.view(), "eternal", "eternal_stella");
            helper.assertTrue(!source.has(DataComponents.DAMAGE) && !source.has(DataComponents.MAX_DAMAGE)
                            && source.getComponentsPatch().get(DataComponents.DAMAGE) != null
                            && source.getComponentsPatch().get(DataComponents.DAMAGE).isEmpty()
                            && source.getComponentsPatch().get(DataComponents.MAX_DAMAGE).isEmpty(),
                    "Native Eternal modifier did not provide actual removal records");
            ItemStack expected = nativeResult(helper, recipe.view(), source);
            var tile = place(helper, POS);
            configure(helper, tile, recipe.view(), source);
            seed(helper, tile, HephaestusForge261Bridge.capacities(tile.getForgeTier()));
            tick(tile, 1);
            ItemStack paid = tile.getPaidOutput();
            helper.assertTrue(tile.hasPaidJob() && ItemStack.matches(expected, paid)
                            && source.getComponentsPatch().equals(paid.getComponentsPatch())
                            && !paid.has(DataComponents.DAMAGE) && !paid.has(DataComponents.MAX_DAMAGE)
                            && ItemStack.matches(paid, roundTrip(helper, paid)),
                    "Transmutation restored removed durability or lost Eternal's native component patch: " + recipe.id());
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void changedMainComponentsInvalidateAnUnpaidPreviewBeforePayment(GameTestHelper helper) {
        RitualView view = ritual(helper, "draco_arcanus_boots");
        var tile = place(helper, POS);
        ItemStack initial = decoratedMain(helper, view);
        configure(helper, tile, view, initial);
        seed(helper, tile, HephaestusForge261Bridge.capacities(tile.getForgeTier()));
        var oldSelection = selection(helper, tile);
        ItemStack changed = initial.copy();
        changed.set(DataComponents.CUSTOM_NAME, Component.literal("Changed before payment"));
        changed.setDamageValue(41);
        int[] balances = bank(tile);
        long randomSeed = 0x5A7E71L;
        helper.getLevel().getRandom().setSeed(randomSeed);
        long unspentRandom = helper.getLevel().getRandom().nextLong();
        helper.getLevel().getRandom().setSeed(randomSeed);
        var stale = HephaestusForge261Bridge.prepareJob(helper.getLevel(), oldSelection, changed,
                auxiliaries(tile), tile.getForgeTier(), enhancers(tile), balances, false);
        helper.assertTrue(stale.isEmpty() && Arrays.equals(balances, bank(tile))
                        && helper.getLevel().getRandom().nextLong() == unspentRandom
                        && ItemStack.matches(initial, tile.getMainInputSlot().getStack()),
                "An obsolete preview produced an item or charged essence after the main components changed");
        ((BasicInventorySlot) tile.getMainInputSlot()).setStackUnchecked(changed);
        var fresh = selection(helper, tile);
        helper.assertTrue(ItemStack.matches(nativeResult(helper, view, changed), fresh.resultPreview())
                        && !ItemStack.matches(oldSelection.resultPreview(), fresh.resultPreview()),
                "A newly selected transmutation reused the previous component preview");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void sameOutputItemWithDifferentComponentsBlocksPaymentDespiteFreeStackSpace(GameTestHelper helper) {
        RitualView view = ritual(helper, "draco_arcanus_helmet");
        ItemStack source = decoratedMain(helper, view);
        // A legal data-component fixture makes the equipment stackable so a
        // full one-item armor slot cannot hide an incorrect component merge.
        source.remove(DataComponents.MAX_DAMAGE);
        source.remove(DataComponents.DAMAGE);
        source.set(DataComponents.MAX_STACK_SIZE, 64);
        source.setCount(3);
        ItemStack expected = nativeResult(helper, view, source);
        helper.assertTrue(expected.getMaxStackSize() == 64 && !expected.has(DataComponents.MAX_DAMAGE),
                "Native transmutation did not carry the explicit component removal");
        var tile = place(helper, POS);
        configure(helper, tile, view, source);
        seed(helper, tile, HephaestusForge261Bridge.capacities(tile.getForgeTier()));
        ItemStack incompatible = expected.copyWithCount(2);
        incompatible.set(DataComponents.CUSTOM_NAME, Component.literal("Other owner's output"));
        tile.getNativeOutputSlot().setStack(incompatible);
        int[] initialBank = bank(tile);
        ItemStack initialMain = tile.getMainInputSlot().getStack().copy();
        List<ItemStack> initialAux = auxiliaries(tile);
        long initialEnergy = tile.getNativeEnergyContainer().getEnergy();
        tick(tile, 6);
        helper.assertTrue(tile.hasOutputBlocked() && !tile.hasPaidJob() && Arrays.equals(initialBank, bank(tile))
                        && ItemStack.matches(initialMain, tile.getMainInputSlot().getStack())
                        && ItemStack.listMatches(initialAux, auxiliaries(tile))
                        && tile.getNativeEnergyContainer().getEnergy() == initialEnergy,
                "Same-item/different-component output was merged or charged before payment");
        tile.getNativeOutputSlot().setStack(expected.copyWithCount(63));
        tick(tile, 1);
        helper.assertTrue(tile.hasPaidJob() && ItemStack.matches(expected, tile.getPaidOutput()),
                "Compatible output space could not start the single-unit transmutation");
        untilPending(helper, tile);
        tick(tile, 1);
        helper.assertTrue(ItemStack.matches(expected.copyWithCount(64), tile.getNativeOutputSlot().getStack())
                        && tile.getMainInputSlot().getCount() == 2,
                "Compatible delivery lost components or copied more than one transformed item");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void creativeAndSpeedUpgradesKeepTheNativeTransformAndPaidObligations(GameTestHelper helper) {
        RitualView view = ritual(helper, "tyr_boots");
        ItemStack source = decoratedMain(helper, view);
        ItemStack expected = nativeResult(helper, view, source);
        var tile = place(helper, POS);
        configure(helper, tile, view, source);
        tile.getComponent().addUpgrades(MagicUpgrades.creativeMagic(), 1);
        long energy = tile.getNativeEnergyContainer().getEnergy();
        tick(tile, 1);
        helper.assertTrue(tile.hasPaidJob() && Arrays.equals(new int[4], bank(tile))
                        && tile.getMainInputSlot().isEmpty() && auxiliaries(tile).stream().allMatch(ItemStack::isEmpty)
                        && tile.getNativeEnergyContainer().getEnergy() < energy && ItemStack.matches(expected, tile.getPaidOutput()),
                "Creative mode changed transmutation, waived materials/FE or generated essence");
        int originalDuration = tile.getProgressRequired();
        int progress = tile.getProgress();
        tile.setControlType(RedstoneControl.HIGH);
        tick(tile, 5);
        tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
        tick(tile, 2);
        helper.assertTrue(tile.getProgress() == progress && ItemStack.matches(expected, tile.getPaidOutput()),
                "A stopped/reloaded transmutation advanced or lost its fixed result");
        tile.getComponent().removeUpgrade(MagicUpgrades.creativeMagic(), true);
        tile.getComponent().addUpgrades(Upgrade.SPEED, 8);
        tile.recalculateUpgrades(Upgrade.SPEED);
        tile.setControlType(RedstoneControl.DISABLED);
        tick(tile, 1);
        helper.assertTrue(tile.getProgressRequired() < originalDuration && Arrays.equals(new int[4], bank(tile)),
                "Speed did not accelerate work or ending creative mode repaid the existing transaction");
        untilPending(helper, tile);
        helper.assertTrue(ItemStack.matches(expected, tile.getPendingOutputs().getFirst()),
                "Speed/creative transitions changed the paid native transform");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void paidTransformKeepsOriginalComponentsAcrossPauseMissingRecipeAndBlockDrop(GameTestHelper helper) {
        RitualView view = ritual(helper, "tyr_chestplate");
        ItemStack source = decoratedMain(helper, view).copyWithCount(3);
        ItemStack expected = nativeResult(helper, view, source);
        var tile = place(helper, POS);
        configure(helper, tile, view, source);
        seed(helper, tile, HephaestusForge261Bridge.capacities(tile.getForgeTier()));
        tick(tile, 1);
        int[] paidBank = bank(tile);
        int paidTier = tile.getForgeTier();
        int progress = tile.getProgress();
        tile.setControlType(RedstoneControl.HIGH);
        tick(tile, 3);
        helper.assertTrue(tile.isPaidJobPaused() && tile.getProgress() == progress && ItemStack.matches(expected, tile.getPaidOutput()),
                "Redstone pause advanced or discarded a paid component transform");
        ItemStack remaining = tile.getMainInputSlot().getStack().copy();
        remaining.set(DataComponents.CUSTOM_NAME, Component.literal("Changed remaining input"));
        remaining.setDamageValue(85);
        ((BasicInventorySlot) tile.getMainInputSlot()).setStackUnchecked(remaining);
        tile.loadAdditional(save(helper, tile), helper.getLevel().registryAccess());
        tile.setControlType(RedstoneControl.DISABLED);
        ForbiddenArcanusRecipeCatalog.clear();
        try {
            tick(tile, 4);
            helper.assertTrue(ItemStack.matches(expected, tile.getPaidOutput()) && Arrays.equals(paidBank, bank(tile))
                            && tile.getForgeTier() == paidTier,
                    "Input mutation or a missing recipe reassembled/repaid the old transmutation");
            List<ItemEntity> drops = new ArrayList<>();
            BlockPos absolute = tile.getBlockPos();
            drops.add(new ItemEntity(helper.getLevel(), absolute.getX(), absolute.getY(), absolute.getZ(),
                    new ItemStack(ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get(), 2)));
            NeoForge.EVENT_BUS.post(new BlockDropsEvent(helper.getLevel(), absolute, tile.getBlockState(), tile,
                    drops, null, ItemStack.EMPTY));
            List<ItemStack> stateful = drops.stream().map(ItemEntity::getItem)
                    .filter(stack -> stack.has(DataComponents.BLOCK_ENTITY_DATA)).toList();
            helper.assertTrue(stateful.size() == 1 && stateful.getFirst().getCount() == 1,
                    "A component-bearing paid transform was duplicated across machine drops");
            helper.setBlock(POS, Blocks.AIR);
            var restored = place(helper, TARGET);
            helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null,
                    helper.absolutePos(TARGET), stateful.getFirst()), "Dropped transmutation receipt could not be restored");
            restored.applyComponentsFromItemStack(stateful.getFirst());
            helper.assertTrue(ItemStack.matches(expected, restored.getPaidOutput())
                            && ItemStack.matches(remaining, restored.getMainInputSlot().getStack()),
                    "Placement mixed the paid unit's components with the remaining source stack");
            untilPending(helper, restored);
            tick(restored, 1);
            helper.assertTrue(ItemStack.matches(expected, restored.getNativeOutputSlot().getStack())
                            && Arrays.equals(paidBank, bank(restored))
                            && restored.getForgeTier() == paidTier,
                    "Receipt completion changed original components or repeated resource payment");
        } finally {
            ForbiddenArcanusRecipeCatalog.refresh(helper.getLevel().getServer());
        }
        helper.succeed();
    }

    private static ItemStack decoratedMain(GameTestHelper helper, RitualView view) {
        return decoratedMain(helper, view, "soulbound", "soul_binding_crystal");
    }

    private static ItemStack decoratedMain(GameTestHelper helper, RitualView view, String modifierName, String addition) {
        ItemStack source = view.mainIngredient().examples().getFirst().copyWithCount(1);
        source.setDamageValue(37);
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved " + view.id().getPath()));
        if (!modifierName.equals("eternal")) {
            source.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.UNBREAKING), 3);
        }
        source.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.PROTECTION), 4);
        source.set(DataComponents.REPAIR_COST, 12);
        CompoundTag custom = new CompoundTag();
        custom.putString("origin", view.id().toString());
        CompoundTag nested = new CompoundTag();
        nested.putInt("remaining", 137);
        custom.put("attachment", nested);
        source.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        SmithingRecipe modifier = (SmithingRecipe) helper.getLevel().getRecipeManager()
                .byKey(id("smithing/apply_" + modifierName + "_modifier")).orElseThrow().value();
        SmithingRecipeInput input = new SmithingRecipeInput(item("apply_modifier_smithing_template"), source, item(addition));
        helper.assertTrue(modifier.matches(input, helper.getLevel()), "Native modifier fixture has invalid ingredients or components: " + modifierName);
        ItemStack modified = modifier.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(!modified.isEmpty() && modified.has(ModDataComponents.ITEM_MODIFIER),
                "Native modifier fixture was not assembled: " + modifierName);
        return modified;
    }

    private static ItemStack nativeResult(GameTestHelper helper, RitualView view, ItemStack main) {
        var ritual = helper.getLevel().registryAccess().registryOrThrow(FARegistries.RITUAL).get(view.id());
        helper.assertTrue(ritual.result() instanceof TransmuteInputResult, "Fixture is not a native transmutation");
        return ritual.result().getResultItem(main.copyWithCount(1));
    }

    private static void assertPreservedComponents(GameTestHelper helper, ItemStack source, ItemStack result) {
        helper.assertTrue(source.getHoverName().equals(result.getHoverName()) && source.getDamageValue() == result.getDamageValue()
                        && source.get(DataComponents.ENCHANTMENTS).equals(result.get(DataComponents.ENCHANTMENTS))
                        && source.get(DataComponents.CUSTOM_DATA).equals(result.get(DataComponents.CUSTOM_DATA))
                        && source.get(ModDataComponents.ITEM_MODIFIER).equals(result.get(ModDataComponents.ITEM_MODIFIER)),
                "Native transmutation dropped a name, absolute wear value, enchantment, nested data or modifier holder");
    }

    private static HephaestusForgeProcessorBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        HephaestusForgeProcessorBlockEntity tile = helper.getBlockEntity(pos);
        tile.getNativeEnergyContainer().setEnergy(HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY);
        tile.setControlType(RedstoneControl.DISABLED);
        return tile;
    }

    private static void configure(GameTestHelper helper, HephaestusForgeProcessorBlockEntity tile, RitualView view, ItemStack main) {
        setTier(helper, tile, Math.max(1, view.tier()));
        ((BasicInventorySlot) tile.getMainInputSlot()).setStackUnchecked(main.copy());
        for (int index = 0; index < view.inputs().size(); index++) {
            var ingredient = view.inputs().get(index);
            ((BasicInventorySlot) tile.getAuxInputSlot(7 - index)).setStackUnchecked(
                    ingredient.ingredient().examples().getFirst().copyWithCount(ingredient.amount()));
        }
        var catalog = ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer());
        for (int index = 0; index < view.enhancers().size(); index++) {
            tile.getEnhancerSlot(index).setStack(BuiltInRegistries.ITEM.get(catalog.enhancersById()
                    .get(view.enhancers().get(index)).displayItem()).getDefaultInstance());
        }
    }

    private static HephaestusRecipeSelector.Selection selection(GameTestHelper helper, HephaestusForgeProcessorBlockEntity tile) {
        return new HephaestusRecipeSelector().select(helper.getLevel(), tile.getMainInputSlot().getStack(), auxiliaries(tile),
                tile.getForgeTier(), enhancers(tile), tile.isCreativeMagic()).orElseThrow();
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

    private static RitualView ritual(GameTestHelper helper, String path) {
        return ForbiddenArcanusRecipeCatalog.snapshot(helper.getLevel().getServer()).ritualsById().get(id(path));
    }

    private static int[] rawCosts(RitualView view) {
        var costs = view.essences();
        return new int[]{costs.aureal(), costs.souls(), costs.blood(), costs.experience()};
    }

    private static String stackState(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + " count=" + stack.getCount()
                + ", maxStack=" + stack.getMaxStackSize() + ", damage=" + stack.getDamageValue()
                + ", patch=" + stack.getComponentsPatch();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("forbidden_arcanus", path); }
    private static ItemStack item(String path) { return BuiltInRegistries.ITEM.getOptional(id(path)).orElseThrow().getDefaultInstance(); }
    private static int[] bank(HephaestusForgeProcessorBlockEntity tile) {
        return new int[]{tile.getStoredEssence(0), tile.getStoredEssence(1), tile.getStoredEssence(2), tile.getStoredEssence(3)};
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
        for (int index = 0; index < 2_000 && !tile.hasPendingOutput(); index++) {
            // A connected power source keeps speed-upgraded jobs supplied.
            if (tile.getNativeEnergyContainer().getEnergy() < HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY / 2) {
                tile.getNativeEnergyContainer().setEnergy(HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY);
            }
            tile.onUpdateServer();
        }
        helper.assertTrue(tile.hasPendingOutput(), "Transmutation did not finish its paid FE processing phase");
    }
    private static ItemStack roundTrip(GameTestHelper helper, ItemStack stack) {
        var access = helper.getLevel().registryAccess();
        ItemStack saved = ItemStack.parse(access, stack.save(access)).orElseThrow();
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), access);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, saved);
            ItemStack restored = ItemStack.STREAM_CODEC.decode(buffer);
            helper.assertTrue(ItemStack.matches(stack, restored), "Native transmutation components changed on NBT/network round trip");
            return restored;
        } finally {
            buffer.release();
        }
    }
}
