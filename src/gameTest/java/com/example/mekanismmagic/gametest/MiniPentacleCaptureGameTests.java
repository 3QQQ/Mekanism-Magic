package com.example.mekanismmagic.gametest;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.occultism.MiniPentacleCapture;
import com.example.mekanismmagic.integration.occultism.MiniPentacleClaims;
import com.example.mekanismmagic.integration.occultism.MiniPentacleDeployment;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.klikli_dev.modonomicon.api.multiblock.Multiblock;
import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.block.ChalkGlyphBlock;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Capture tests use the actual loaded Occultism definitions and native validation. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiniPentacleCaptureGameTests {
    private static final ResourceLocation FOLIOT = occultism("craft_foliot");
    private static final ResourceLocation DJINNI = occultism("craft_djinni");

    private MiniPentacleCaptureGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 500)
    public static void nativeRainbowChalkDrawingCapturesAndRoundTrips(GameTestHelper helper) {
        universalChalkRoundTrip(helper, "rainbow");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 500)
    public static void nativeVoidChalkDrawingCapturesAndRoundTrips(GameTestHelper helper) {
        universalChalkRoundTrip(helper, "void");
        helper.succeed();
    }

    private static void universalChalkRoundTrip(GameTestHelper helper, String color) {
        for (Rotation rotation : Rotation.values()) {
            for (boolean clickGlyph : new boolean[]{false, true}) {
                try (Fixture fixture = new Fixture(helper, 16, DJINNI)) {
                    fixture.build(rotation);
                    fixture.redrawWithNativeChalk(color, rotation);
                    ItemStack blank = fixture.holdBlank();
                    BlockPos clicked = clickGlyph ? fixture.glyphEdge() : fixture.anchor;
                    var captured = MiniPentacleCapture.capture(fixture.level, fixture.actor, blank, clicked);
                    helper.assertTrue(captured.status() == MiniPentacleDeployment.Status.CAPTURED,
                            "Native-valid " + color + " drawing could not be captured: " + captured.status()
                                    + ", rotation=" + rotation + ", glyph click=" + clickGlyph);
                    helper.assertTrue(OccultismRecipeBridge.miniRitualPentacle(blank).filter(DJINNI::equals).isPresent(),
                            "Universal chalk selected a different pentacle");
                    helper.assertTrue(MiniPentacleCapture.hasCustomLayout(blank),
                            "Universal glyph states were not retained");
                    var portable = blank.copy();
                    fixture.assertRemoved();
                    fixture.assertNoDrops();
                    var placed = MiniPentacleDeployment.deploy(fixture.level, fixture.actor, blank, fixture.anchor, rotation);
                    helper.assertTrue(placed.status() == MiniPentacleDeployment.Status.PLACED,
                            "Captured " + color + " drawing could not unfold: " + placed.status());
                    // Universal colors can make several rotations indistinguishable
                    // to native validation; capture chooses a canonical rotation.
                    fixture.assertSameDrawingUpToRotation(fixture.built);
                    helper.assertTrue(fixture.multiblock.validate(fixture.level, fixture.anchor, rotation),
                            "Restored universal drawing no longer matched its native pentacle");
                    var recovered = MiniPentacleDeployment.recover(fixture.level, fixture.actor, blank, fixture.anchor);
                    helper.assertTrue(recovered.status() == MiniPentacleDeployment.Status.RECOVERED,
                            "Unfolded " + color + " drawing could not be recovered: " + recovered.status());
                    fixture.assertSameStack(blank, portable, "Round trip altered the retained universal drawing");
                    fixture.assertRemoved();
                    fixture.assertNoDrops();
                }
            }
        }
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 1000)
    public static void everyNativePentacleWithUniversalChalkCapturesThroughItemClick(GameTestHelper helper) {
        for (var pentacle : OccultismRecipeBridge.miniRitualPentacleIds(helper.getLevel())) {
            for (String color : List.of("rainbow", "void")) {
                for (boolean clickGlyph : new boolean[]{false, true}) {
                    try (Fixture fixture = new Fixture(helper, 17, pentacle)) {
                        fixture.build(Rotation.NONE);
                        fixture.redrawWithNativeChalk(color, Rotation.NONE);
                        var blank = fixture.holdBlank();
                        var clicked = clickGlyph ? fixture.glyphEdge() : fixture.anchor;
                        var context = new UseOnContext(fixture.actor, InteractionHand.MAIN_HAND,
                                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false));
                        var result = blank.getItem().onItemUseFirst(blank, context);
                        helper.assertTrue(result == InteractionResult.CONSUME && !MiniPentacleCapture.isBlank(blank),
                                "Universal drawing item click did not capture: " + pentacle + "/" + color + "/glyph=" + clickGlyph
                                        + ", result=" + result);
                        fixture.assertRemoved();
                        fixture.assertNoDrops();
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void handBuiltPentacleBecomesOneStandardMiniature(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 0, FOLIOT)) {
            fixture.build(Rotation.NONE);
            ItemStack blank = fixture.holdBlank();
            UseOnContext context = new UseOnContext(fixture.actor, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(fixture.anchor), Direction.UP,
                            fixture.anchor, false));
            InteractionResult result = blank.getItem().onItemUseFirst(blank, context);
            helper.assertTrue(result == InteractionResult.CONSUME,
                    "Blank mini did not consume the click before the sacrificial bowl could take it");
            fixture.assertStandardMiniature(blank, FOLIOT);
            fixture.assertRemoved();
            fixture.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 180)
    public static void allRotationsChooseLargestNestedPentacleFromCenterAndEdge(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 1, DJINNI)) {
            Multiblock lower = multiblock(helper, FOLIOT);
            for (Rotation rotation : Rotation.values()) {
                for (boolean edge : new boolean[]{false, true}) {
                    fixture.build(rotation);
                    helper.assertTrue(lower.validate(fixture.level, fixture.anchor, rotation),
                            "Native lower pentacle did not match inside the higher fixture: " + rotation);
                    ItemStack blank = fixture.holdBlank();
                    BlockPos clicked = edge ? fixture.edge() : fixture.anchor;
                    var result = MiniPentacleCapture.capture(fixture.level, fixture.actor, blank, clicked);
                    helper.assertTrue(result.status() == MiniPentacleDeployment.Status.CAPTURED,
                            "Nested capture failed at " + (edge ? "edge" : "center")
                                    + " for " + rotation + ": " + result.status());
                    helper.assertTrue(result.changedBlocks() == fixture.built.size(),
                            "Capture did not consume exactly the higher pentacle footprint");
                    helper.assertTrue(blank.getCount() == 1
                                    && OccultismRecipeBridge.miniRitualPentacle(blank).filter(DJINNI::equals).isPresent()
                                    && !blank.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                                    .getUnsafe().contains("ritual")
                                    && !MiniPentacleDeployment.isDeployed(blank),
                            "Nested capture did not produce one higher pentacle-only miniature");
                    fixture.assertRemoved();
                    fixture.assertNoDrops();
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void incompletePentaclePreservesBlankAndRemainingWorld(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 2, FOLIOT)) {
            fixture.build(Rotation.NONE);
            fixture.level.setBlock(fixture.anchor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            ItemStack blank = fixture.holdBlank();
            ItemStack before = blank.copy();
            Map<BlockPos, BlockState> remaining = fixture.currentStates();
            var result = MiniPentacleCapture.capture(fixture.level, fixture.actor, blank, fixture.edge());
            helper.assertFalse(result.success(), "Incomplete pentacle was captured");
            fixture.assertSameStack(blank, before, "Failed capture changed the blank miniature");
            fixture.assertStates(remaining);
            fixture.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void occupiedAndRunningBowlsCannotBeCaptured(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 3, FOLIOT)) {
            fixture.build(Rotation.NONE);
            GoldenSacrificialBowlBlockEntity bowl = fixture.bowl();
            ItemStack blank = fixture.holdBlank();
            bowl.itemStackHandler.setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
            fixture.assertBusyCaptureUnchanged(blank, bowl, "occupied bowl");
            bowl.itemStackHandler.setStackInSlot(0, ItemStack.EMPTY);

            var projection = OccultismRecipeBridge.findProjectionByPentacle(fixture.level, FOLIOT)
                    .orElseThrow(() -> new IllegalStateException("Missing native foliot ritual"));
            var holder = fixture.level.getRecipeManager().byKey(projection.recipeId())
                    .orElseThrow(() -> new IllegalStateException("Missing native ritual recipe"));
            helper.assertTrue(holder.value() instanceof RitualRecipe, "Fixture recipe is not an Occultism ritual");
            bowl.currentRitualRecipe = new RecipeHolder<>(holder.id(), (RitualRecipe) holder.value());
            bowl.currentRitualRecipeId = holder.id();
            bowl.ritualActive = true;
            bowl.consumedIngredients = new ArrayList<>(List.of(new ItemStack(Items.GOLD_INGOT)));
            try {
                fixture.assertBusyCaptureUnchanged(blank, bowl, "running ritual");
            } finally {
                // No ticks run with an artificial partial ritual; do not leave a ticking invalid fixture.
                bowl.currentRitualRecipe = null;
                bowl.currentRitualRecipeId = null;
                bowl.ritualActive = false;
                bowl.consumedIngredients.clear();
            }
            var result = MiniPentacleCapture.capture(fixture.level, fixture.actor, blank, fixture.anchor);
            helper.assertTrue(result.success(), "An emptied, idle bowl stayed permanently uncollectable");
            fixture.assertStandardMiniature(blank, FOLIOT);
            fixture.assertRemoved();
            fixture.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void canceledBreakEventPreservesWholePentacleAndBlank(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 4, FOLIOT)) {
            fixture.build(Rotation.NONE);
            ItemStack blank = fixture.holdBlank();
            ItemStack before = blank.copy();
            CompoundTag bowlBefore = fixture.bowl().saveWithoutMetadata(fixture.level.registryAccess());
            AtomicInteger events = new AtomicInteger();
            Consumer<BlockEvent.BreakEvent> cancel = event -> {
                if (event.getLevel() == fixture.level && fixture.built.containsKey(event.getPos())
                        && events.incrementAndGet() == 3) {
                    event.setCanceled(true);
                }
            };
            NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, cancel);
            try {
                var result = MiniPentacleCapture.capture(fixture.level, fixture.actor, blank, fixture.anchor);
                helper.assertTrue(result.status() == MiniPentacleDeployment.Status.NO_PERMISSION,
                        "Canceled third BreakEvent was ignored: " + result.status());
            } finally {
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(events.get() >= 3, "Capture did not consult protection for each block");
            fixture.assertSameStack(blank, before, "Canceled break configured or spent the blank");
            fixture.assertStates(fixture.built);
            helper.assertTrue(bowlBefore.equals(fixture.bowl().saveWithoutMetadata(fixture.level.registryAccess())),
                    "Canceled capture changed the bowl block entity");
            fixture.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 180)
    public static void repeatedClicksAndPersistedDeploymentCannotCreateSecondCredential(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 5, FOLIOT)) {
            fixture.build(Rotation.NONE);
            ItemStack owner = fixture.holdBlank();
            helper.assertTrue(MiniPentacleCapture.capture(fixture.level, fixture.actor, owner, fixture.anchor).success(),
                    "Initial capture failed");
            ItemStack canonical = owner.copy();
            helper.assertFalse(MiniPentacleCapture.capture(fixture.level, fixture.actor, owner, fixture.anchor).success(),
                    "Repeated capture accepted an already configured miniature");
            fixture.assertSameStack(owner, canonical, "Repeated click modified the configured item");

            for (int cycle = 0; cycle < 2; cycle++) {
                fixture.actor.setItemInHand(InteractionHand.MAIN_HAND, owner);
                helper.assertTrue(MiniPentacleDeployment.deploy(fixture.level, fixture.actor, owner,
                                fixture.anchor, Rotation.NONE).success(), "Captured miniature would not unfold");
                owner = ItemStack.parse(fixture.level.registryAccess(), owner.save(fixture.level.registryAccess()))
                        .orElseThrow(() -> new IllegalStateException("Deployed miniature failed save/load"));
                reloadClaims(fixture.level);
                ItemStack intruder = fixture.holdBlank();
                fixture.actor.setItemInHand(InteractionHand.OFF_HAND, owner);
                ItemStack intruderBefore = intruder.copy();
                var captured = MiniPentacleCapture.capture(fixture.level, fixture.actor, intruder, fixture.edge());
                helper.assertTrue(captured.status() == MiniPentacleDeployment.Status.CLAIMED,
                        "Blank captured another miniature's persisted deployment: " + captured.status());
                fixture.assertSameStack(intruder, intruderBefore, "Rejected capture spent the other blank");
                fixture.assertStates(fixture.built);
                fixture.actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                fixture.actor.setItemInHand(InteractionHand.MAIN_HAND, owner);
                var recovered = MiniPentacleDeployment.recover(fixture.level, fixture.actor, owner, fixture.edge());
                helper.assertTrue(recovered.success(), "Original owner could not retract after rejected capture");
                fixture.assertStandardMiniature(owner, FOLIOT);
                fixture.assertRemoved();
                helper.assertFalse(MiniPentacleDeployment.recover(fixture.level, fixture.actor,
                        owner, fixture.anchor).success(), "Repeated recovery minted another structure");
                fixture.assertSameStack(owner, canonical, "Unfold/retract cycle changed the one canonical miniature");
                fixture.assertNoDrops();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 180)
    public static void legacyCredentialStaysInvalidAfterCaptureReloadAndRebuild(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 6, FOLIOT)) {
            fixture.build(Rotation.NONE);
            ItemStack legacy = fixture.legacyDeployment();
            helper.assertTrue(MiniPentacleDeployment.isDeployed(legacy), "Historical deployment fixture was malformed");
            ItemStack captured = fixture.holdBlank();
            helper.assertTrue(MiniPentacleCapture.capture(fixture.level, fixture.actor, captured, fixture.anchor).success(),
                    "Unclaimed pre-upgrade pentacle could not be migrated through capture");
            fixture.assertStandardMiniature(captured, FOLIOT);
            fixture.assertRemoved();
            reloadClaims(fixture.level);
            fixture.actor.setItemInHand(InteractionHand.MAIN_HAND, legacy);
            ItemStack legacyBefore = legacy.copy();
            helper.assertFalse(MiniPentacleDeployment.recover(fixture.level, fixture.actor,
                    legacy, fixture.anchor).success(), "Legacy credential remained usable after capture");
            fixture.assertSameStack(legacy, legacyBefore, "Rejected legacy recovery changed its receipt");
            fixture.build(Rotation.NONE);
            var recovered = MiniPentacleDeployment.recover(fixture.level, fixture.actor, legacy, fixture.anchor);
            helper.assertTrue(recovered.status() == MiniPentacleDeployment.Status.CLAIMED,
                    "Rebuilt blocks resurrected a captured legacy credential: " + recovered.status());
            fixture.assertSameStack(legacy, legacyBefore, "Rebuilt legacy recovery changed its retired receipt");
            fixture.assertStates(fixture.built);
            fixture.assertStandardMiniature(captured, FOLIOT);
            fixture.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void partlyDeployedPentacleGuardsCannotBeCapturedByAnotherBlank(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 7, DJINNI)) {
            fixture.build(Rotation.NONE);
            BlockPos missing = fixture.edge();
            fixture.level.setBlock(missing, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            ItemStack owner = OccultismRecipeBridge.createPentacleMiniRitual(DJINNI);
            fixture.actor.setItemInHand(InteractionHand.MAIN_HAND, owner);
            var placed = MiniPentacleDeployment.deploy(fixture.level, fixture.actor, owner, fixture.anchor, Rotation.NONE);
            helper.assertTrue(placed.success() && placed.changedBlocks() == 1,
                    "Partial deployment did not own exactly its one missing block: " + placed.status());
            fixture.actor.setItemInHand(InteractionHand.OFF_HAND, owner);
            ItemStack blank = fixture.holdBlank();
            var result = MiniPentacleCapture.capture(fixture.level, fixture.actor, blank, fixture.anchor);
            helper.assertTrue(result.status() == MiniPentacleDeployment.Status.CLAIMED,
                    "Capture ignored ownership when clicking a pre-existing guard block: " + result.status());
            helper.assertTrue(MiniPentacleCapture.isBlank(blank) && blank.getCount() == 1,
                    "Guard rejection configured or spent the blank");
            fixture.assertStates(fixture.built);
            fixture.actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            fixture.actor.setItemInHand(InteractionHand.MAIN_HAND, owner);
            var recovered = MiniPentacleDeployment.recover(fixture.level, fixture.actor, owner, fixture.anchor);
            helper.assertTrue(recovered.success() && recovered.changedBlocks() == 1,
                    "Partial deployment recovery removed pre-existing blocks");
            Map<BlockPos, BlockState> remaining = new LinkedHashMap<>(fixture.built);
            remaining.put(missing, Blocks.AIR.defaultBlockState());
            fixture.assertStates(remaining);
            fixture.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void protectionCallbackHandSwapAbortsCaptureWithoutMutatingWorld(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 8, FOLIOT)) {
            fixture.build(Rotation.NONE);
            ItemStack blank = fixture.holdBlank();
            ItemStack before = blank.copy();
            Consumer<BlockEvent.BreakEvent> swap = event -> {
                if (event.getLevel() == fixture.level && fixture.built.containsKey(event.getPos())) {
                    fixture.actor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
                }
            };
            NeoForge.EVENT_BUS.addListener(BlockEvent.BreakEvent.class, swap);
            try {
                helper.assertFalse(MiniPentacleCapture.capture(fixture.level, fixture.actor, blank,
                                fixture.anchor).success(), "Capture committed after callback replaced its input");
            } finally {
                NeoForge.EVENT_BUS.unregister(swap);
            }
            fixture.assertSameStack(blank, before, "Detached blank was changed after hand swap");
            helper.assertTrue(fixture.actor.getMainHandItem().is(Items.DIAMOND),
                    "Capture overwrote the replacement hand item");
            fixture.assertStates(fixture.built);
            fixture.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 180)
    public static void expensiveBowlAndFourCandlesSurviveCaptureAndRelocation(GameTestHelper helper) {
        try (Fixture original = new Fixture(helper, 9, FOLIOT);
             Fixture relocated = new Fixture(helper, 10, FOLIOT)) {
            original.build(Rotation.NONE);
            original.useValuableVariants();
            ItemStack captured = original.holdBlank();
            helper.assertTrue(MiniPentacleCapture.capture(original.level, original.actor, captured,
                    original.anchor).success(), "Valuable native block variants could not be captured");
            original.assertRemoved();
            ItemStack portable = captured.copy();
            helper.assertTrue(captured.getCount() == 1
                    && OccultismRecipeBridge.miniRitualPentacle(captured).filter(FOLIOT::equals).isPresent(),
                    "Variant capture did not produce one pentacle miniature");
            helper.assertFalse(MiniPentacleDeployment.isDeployed(captured),
                    "Collected variants retained an active deployment receipt");

            relocated.build(Rotation.NONE);
            relocated.useValuableVariants();
            ItemStack identical = relocated.holdBlank();
            helper.assertTrue(MiniPentacleCapture.capture(relocated.level, relocated.actor, identical,
                    relocated.anchor).success(), "Identical variant structure could not be captured elsewhere");
            relocated.assertSameStack(identical, portable,
                    "Identical captured materials retained their source coordinates or a random claim token");

            relocated.actor.setItemInHand(InteractionHand.MAIN_HAND, captured);
            var placed = MiniPentacleDeployment.deploy(relocated.level, relocated.actor, captured,
                    relocated.anchor, Rotation.NONE);
            helper.assertTrue(placed.success(), "Captured variants could not unfold at another position: " + placed.status());
            relocated.assertStates(relocated.built);
            var recovered = MiniPentacleDeployment.recover(relocated.level, relocated.actor, captured, relocated.anchor);
            helper.assertTrue(recovered.success(), "Relocated variants could not retract: " + recovered.status());
            relocated.assertSameStack(captured, portable,
                    "Variant unfolding/retraction lost materials or retained absolute deployment coordinates");
            relocated.assertRemoved();
            original.assertNoDrops();
            relocated.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 180)
    public static void survivalProjectionBindingCannotCreateOrUpgradePentacleMaterials(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 11, FOLIOT)) {
            fixture.actor.getAbilities().instabuild = false;
            ItemStack samePentacleReference = projectionDummy(helper, FOLIOT);
            ItemStack higherPentacleReference = projectionDummy(helper, DJINNI);
            ItemStack blank = fixture.holdBlank();
            ItemStack blankBefore = blank.copy();
            fixture.actor.setItemInHand(InteractionHand.OFF_HAND, samePentacleReference);
            var copied = blank.getItem().use(fixture.level, fixture.actor, InteractionHand.MAIN_HAND);
            helper.assertTrue(copied.getResult() == InteractionResult.FAIL,
                    "Survival player copied an unpaid pentacle from a ritual projection");
            fixture.assertSameStack(blank, blankBefore, "Projection copy changed the unspent blank");

            fixture.build(Rotation.NONE);
            helper.assertTrue(MiniPentacleCapture.capture(fixture.level, fixture.actor, blank,
                            fixture.anchor).success(), "Real pentacle capture failed before recipe binding");
            var bound = blank.getItem().use(fixture.level, fixture.actor, InteractionHand.MAIN_HAND);
            helper.assertTrue(bound.getResult().consumesAction(),
                    "Captured pentacle could not select one of its own ritual recipes");
            var expectedProjection = OccultismRecipeBridge.findProjection(fixture.level, samePentacleReference)
                    .orElseThrow();
            helper.assertTrue(blank.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                            .getUnsafe().getString("ritual").equals(expectedProjection.recipeId().toString()),
                    "Same-pentacle binding did not select the exact referenced ritual");
            ItemStack paidPentacle = blank.copy();
            fixture.actor.setItemInHand(InteractionHand.OFF_HAND, higherPentacleReference);
            var upgraded = blank.getItem().use(fixture.level, fixture.actor, InteractionHand.MAIN_HAND);
            helper.assertTrue(upgraded.getResult() == InteractionResult.FAIL,
                    "A lower pentacle was upgraded for free using a higher ritual projection");
            fixture.assertSameStack(blank, paidPentacle,
                    "Rejected projection upgrade changed the captured materials or selected recipe");
            fixture.assertRemoved();
            fixture.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void fourPaperAndAmethystCraftExactlyOneBlankCarrier(GameTestHelper helper) {
        List<ItemStack> grid = new ArrayList<>(List.of(
                ItemStack.EMPTY, new ItemStack(Items.PAPER), ItemStack.EMPTY,
                new ItemStack(Items.PAPER), new ItemStack(Items.AMETHYST_SHARD), new ItemStack(Items.PAPER),
                ItemStack.EMPTY, new ItemStack(Items.PAPER), ItemStack.EMPTY));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .orElseThrow(() -> new IllegalStateException("Native recipe manager did not match the blank carrier grid"));
        helper.assertTrue(recipe.id().equals(ResourceLocation.fromNamespaceAndPath(
                        MekanismMagic.MOD_ID, "mini_ritual_blank")),
                "Blank carrier grid matched an unexpected native recipe: " + recipe.id());
        ItemStack output = recipe.value().assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(output.getCount() == 1
                        && ItemStack.isSameItemSameComponents(output, new ItemStack(MekanismMagic.MINI_RITUAL.get()))
                        && MiniPentacleCapture.isBlank(output),
                "Blank carrier recipe output was not exactly one completely unconfigured mini");
        CompoundTag data = output.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        helper.assertFalse(data.contains("ritual") || data.contains("pentacle") || data.contains("mini_pentacle_layout"),
                "Cheap blank recipe supplied paid pentacle material data");
        grid.set(1, ItemStack.EMPTY);
        helper.assertFalse(recipe.value().matches(CraftingInput.of(3, 3, grid), helper.getLevel()),
                "Blank carrier recipe accepted fewer than four pieces of paper");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 120)
    public static void waterloggedPentaclePreservesFluidBlocksAndBlank(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 12, FOLIOT)) {
            fixture.build(Rotation.NONE);
            BlockPos candle = fixture.built.entrySet().stream()
                    .filter(entry -> entry.getValue().getBlock() instanceof CandleBlock)
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            BlockState submerged = Blocks.CANDLE.defaultBlockState()
                    .setValue(CandleBlock.WATERLOGGED, true).setValue(CandleBlock.LIT, false);
            fixture.set(candle, submerged);
            fixture.built.put(candle, submerged);
            helper.assertTrue(fixture.multiblock.validate(fixture.level, fixture.anchor, Rotation.NONE),
                    "Waterlogged fixture stopped matching the native pentacle before safety validation");
            helper.assertFalse(fixture.level.getFluidState(candle).isEmpty(), "Fixture candle did not contain water");
            ItemStack blank = fixture.holdBlank();
            ItemStack blankBefore = blank.copy();
            var result = MiniPentacleCapture.capture(fixture.level, fixture.actor, blank, fixture.anchor);
            helper.assertTrue(result.status() == MiniPentacleDeployment.Status.STRUCTURE_IN_USE,
                    "Capture removed a fluid-bearing native pentacle block: " + result.status());
            fixture.assertSameStack(blank, blankBefore, "Fluid rejection consumed or configured the blank");
            fixture.assertStates(fixture.built);
            helper.assertFalse(fixture.level.getFluidState(candle).isEmpty(), "Rejected capture discarded stored water");
            fixture.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 180)
    public static void canceledMultiPlaceEventLeavesNoWorldOrOwnershipReservation(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper, 13, FOLIOT)) {
            fixture.build(Rotation.NONE);
            ItemStack miniature = fixture.holdBlank();
            helper.assertTrue(MiniPentacleCapture.capture(fixture.level, fixture.actor, miniature,
                            fixture.anchor).success(), "Initial capture failed before placement cancellation test");
            ItemStack portable = miniature.copy();
            AtomicInteger events = new AtomicInteger();
            Consumer<BlockEvent.EntityMultiPlaceEvent> cancel = event -> {
                if (event.getLevel() == fixture.level && event.getEntity() == fixture.actor) {
                    events.incrementAndGet();
                    event.setCanceled(true);
                }
            };
            boolean previousCapture = fixture.level.captureBlockSnapshots;
            int outerSnapshots = fixture.level.capturedBlockSnapshots.size();
            NeoForge.EVENT_BUS.addListener(BlockEvent.EntityMultiPlaceEvent.class, cancel);
            try {
                // NeoForge's item use wrapper enables this before the miniature handles placement.
                fixture.level.captureBlockSnapshots = true;
                var placed = MiniPentacleDeployment.deploy(fixture.level, fixture.actor, miniature,
                        fixture.anchor, Rotation.NONE);
                helper.assertTrue(placed.status() == MiniPentacleDeployment.Status.NO_PERMISSION,
                        "Canceled native placement event was ignored: " + placed.status());
                helper.assertTrue(fixture.level.captureBlockSnapshots,
                        "Placement cancellation failed to restore the outer snapshot capture flag");
            } finally {
                fixture.level.captureBlockSnapshots = previousCapture;
                NeoForge.EVENT_BUS.unregister(cancel);
            }
            helper.assertTrue(events.get() == 1, "One unfolding emitted duplicate or missing placement events");
            helper.assertTrue(fixture.level.capturedBlockSnapshots.size() == outerSnapshots,
                    "Miniature leaked its canceled transaction into the outer placement snapshots");
            fixture.assertSameStack(miniature, portable, "Canceled placement left a deployed receipt on the miniature");
            helper.assertFalse(MiniPentacleDeployment.isDeployed(miniature),
                    "Canceled placement committed an active deployment receipt");
            fixture.assertRemoved();
            fixture.assertNoDrops();
            reloadClaims(fixture.level);

            var retried = MiniPentacleDeployment.deploy(fixture.level, fixture.actor, miniature,
                    fixture.anchor, Rotation.NONE);
            helper.assertTrue(retried.status() == MiniPentacleDeployment.Status.PLACED,
                    "Canceled placement left an ownership reservation blocking retry: " + retried.status());
            helper.assertTrue(retried.changedBlocks() == fixture.built.size(),
                    "Retry did not place the complete pentacle exactly once");
            fixture.assertStates(fixture.built);
            var recovered = MiniPentacleDeployment.recover(fixture.level, fixture.actor, miniature, fixture.anchor);
            helper.assertTrue(recovered.status() == MiniPentacleDeployment.Status.RECOVERED,
                    "Successfully retried deployment could not retract: " + recovered.status());
            fixture.assertSameStack(miniature, portable, "Canceled placement/retry cycle changed the portable materials");
            fixture.assertRemoved();
            fixture.assertNoDrops();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 500)
    public static void everyNativePentacleCapturesUnfoldsAndRetractsWithoutMaterialLoss(GameTestHelper helper) {
        List<ResourceLocation> pentacles = OccultismRecipeBridge.miniRitualPentacleIds(helper.getLevel());
        helper.assertTrue(pentacles.size() == 18, "Expected all eighteen native collectible pentacles, got " + pentacles.size());
        for (ResourceLocation pentacle : pentacles) {
            try (Fixture fixture = new Fixture(helper, 14, pentacle)) {
                fixture.build(Rotation.NONE);
                ItemStack miniature = fixture.holdBlank();
                var capture = MiniPentacleCapture.capture(fixture.level, fixture.actor, miniature, fixture.anchor);
                helper.assertTrue(capture.status() == MiniPentacleDeployment.Status.CAPTURED,
                        "Native pentacle could not be captured: " + pentacle + " / " + capture.status());
                helper.assertTrue(capture.changedBlocks() == fixture.built.size(),
                        "Capture removed the wrong number of native blocks for " + pentacle);
                helper.assertTrue(miniature.getCount() == 1
                                && OccultismRecipeBridge.miniRitualPentacle(miniature).filter(pentacle::equals).isPresent(),
                        "Native pentacle capture selected a different pentacle: " + pentacle);
                ItemStack portable = miniature.copy();
                fixture.assertRemoved();
                fixture.assertNoDrops();

                var placed = MiniPentacleDeployment.deploy(fixture.level, fixture.actor, miniature,
                        fixture.anchor, Rotation.NONE);
                helper.assertTrue(placed.status() == MiniPentacleDeployment.Status.PLACED,
                        "Captured native pentacle could not unfold: " + pentacle + " / " + placed.status());
                helper.assertTrue(placed.changedBlocks() == fixture.built.size()
                                && fixture.multiblock.validate(fixture.level, fixture.anchor, Rotation.NONE),
                        "Unfolded native pentacle was incomplete: " + pentacle);
                fixture.assertStates(fixture.built);
                fixture.assertNoDrops();
                var recovered = MiniPentacleDeployment.recover(fixture.level, fixture.actor, miniature, fixture.anchor);
                helper.assertTrue(recovered.status() == MiniPentacleDeployment.Status.RECOVERED
                                && recovered.changedBlocks() == fixture.built.size(),
                        "Unfolded native pentacle could not fully retract: " + pentacle + " / " + recovered.status());
                fixture.assertSameStack(miniature, portable,
                        "Capture/unfold/retract changed portable materials for " + pentacle);
                fixture.assertRemoved();
                fixture.assertNoDrops();
            }
        }
        helper.succeed();
    }

    private static ItemStack projectionDummy(GameTestHelper helper, ResourceLocation pentacle) {
        for (var item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (OccultismRecipeBridge.isRitualProjectionItem(stack)
                    && OccultismRecipeBridge.findProjection(helper.getLevel(), stack)
                    .filter(projection -> projection.pentacleId().equals(pentacle)).isPresent()) {
                return stack;
            }
        }
        throw new IllegalStateException("No native ritual projection item for " + pentacle);
    }

    private static void reloadClaims(ServerLevel level) {
        CompoundTag saved = MiniPentacleClaims.get(level).save(new CompoundTag(), level.registryAccess());
        level.getDataStorage().set(MiniPentacleClaims.DATA_NAME,
                MiniPentacleClaims.load(saved, level.registryAccess()));
    }

    private static Multiblock multiblock(GameTestHelper helper, ResourceLocation pentacle) {
        Object value = OccultismRecipeBridge.findProjectionByPentacle(helper.getLevel(), pentacle)
                .orElseThrow(() -> new IllegalStateException("Missing native pentacle " + pentacle)).multiblock();
        if (!(value instanceof Multiblock multiblock)) {
            throw new IllegalStateException("Projection was not the native Modonomicon multiblock");
        }
        return multiblock;
    }

    private static ResourceLocation occultism(String path) {
        return ResourceLocation.fromNamespaceAndPath("occultism", path);
    }

    /** Each synchronous test owns a separate height above the vanilla template. */
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final ServerLevel level;
        final ServerPlayer actor;
        final BlockPos anchor;
        final Multiblock multiblock;
        final ResourceLocation pentacle;
        final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        final Map<BlockPos, BlockState> built = new LinkedHashMap<>();
        final List<BlockPos> floor = new ArrayList<>();
        final AABB bounds;

        Fixture(GameTestHelper helper, int fixtureIndex, ResourceLocation pentacle) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.pentacle = pentacle;
            this.multiblock = multiblock(helper, pentacle);
            this.anchor = helper.absolutePos(new BlockPos(4, 60 + fixtureIndex * 6, 4));
            this.bounds = new AABB(Vec3.atLowerCornerOf(anchor.offset(-12, -1, -12)),
                    Vec3.atLowerCornerOf(anchor.offset(13, 3, 13)));
            this.actor = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "MiniCaptureTest"));
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            actor.setShiftKeyDown(false);
            actor.moveTo(anchor.getX() + 0.5, anchor.getY() + 2, anchor.getZ() + 0.5, 0, 0);
            // Display-only support matchers are intentionally not used to construct the floor.
            // Building from only counted matchers without this plane silently breaks real glyphs.
            for (int x = -12; x <= 12; x++) {
                for (int z = -12; z <= 12; z++) {
                    BlockPos support = anchor.offset(x, -1, z);
                    floor.add(support);
                    set(support, Blocks.STONE.defaultBlockState());
                    for (int y = 0; y <= 2; y++) {
                        set(anchor.offset(x, y, z), Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }

        void build(Rotation rotation) {
            built.clear();
            for (var result : multiblock.simulate(level, anchor, rotation, false, false).getSecond()) {
                if (result.getStateMatcher().countsTowardsTotalBlocks()) {
                    BlockPos position = result.getWorldPosition().immutable();
                    BlockState state = result.getStateMatcher().getDisplayedState(0).rotate(rotation);
                    helper.assertTrue(position.getY() == anchor.getY(), "Fixture expected a single-layer pentacle");
                    built.put(position, state);
                }
            }
            helper.assertFalse(built.isEmpty(), "Native fixture had no counted pentacle blocks");
            built.entrySet().stream().sorted(Map.Entry.comparingByKey(
                            Comparator.comparingInt((BlockPos position) -> position.getY())))
                    .forEach(entry -> set(entry.getKey(), entry.getValue()));
            helper.assertTrue(multiblock.validate(level, anchor, rotation),
                    "Hand-built native pentacle was invalid: " + pentacle + " / " + rotation);
            for (var entry : built.entrySet()) {
                helper.assertTrue(entry.getValue().canSurvive(level, entry.getKey()),
                        "Native fixture block had no support at " + entry.getKey());
            }
            // Native neighbour updates connect glass panes/bars while building.
            // Preserve the resulting real states after native validation succeeds.
            built.replaceAll((position, displayed) -> level.getBlockState(position));
            assertStates(built);
        }

        void set(BlockPos position, BlockState state) {
            originals.putIfAbsent(position.immutable(), level.getBlockState(position));
            level.setBlock(position, state, Block.UPDATE_ALL);
        }

        void redrawWithNativeChalk(String color, Rotation rotation) {
            ItemStack chalk = new ItemStack(BuiltInRegistries.ITEM.get(occultism("chalk_" + color)));
            helper.assertFalse(chalk.isEmpty(), "Native universal chalk is absent: " + color);
            actor.setItemInHand(InteractionHand.MAIN_HAND, chalk);
            actor.getAbilities().instabuild = true;
            try {
                for (var entry : built.entrySet()) {
                    if (!(entry.getValue().getBlock() instanceof ChalkGlyphBlock)) continue;
                    var context = new UseOnContext(actor, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(entry.getKey()), Direction.UP, entry.getKey(), false));
                    helper.assertTrue(chalk.getItem().useOn(context).consumesAction(), "Native chalk drawing failed");
                    helper.assertTrue(BuiltInRegistries.BLOCK.getKey(level.getBlockState(entry.getKey()).getBlock())
                                    .equals(occultism("chalk_glyph_" + color)),
                            "Drawing did not replace the existing colored glyph with " + color);
                }
            } finally {
                actor.getAbilities().instabuild = false;
            }
            built.replaceAll((position, previous) -> level.getBlockState(position));
            helper.assertTrue(multiblock.validate(level, anchor, rotation),
                    "Universal chalk fixture does not satisfy native validation: " + color);
        }

        BlockPos glyphEdge() {
            return built.entrySet().stream().filter(entry -> entry.getValue().getBlock() instanceof ChalkGlyphBlock)
                    .map(Map.Entry::getKey).max(Comparator.comparingDouble(position -> position.distSqr(anchor))).orElseThrow();
        }

        ItemStack holdBlank() {
            ItemStack blank = new ItemStack(MekanismMagic.MINI_RITUAL.get());
            actor.setItemInHand(InteractionHand.MAIN_HAND, blank);
            return blank;
        }

        BlockPos edge() {
            return built.keySet().stream().max(Comparator.comparingDouble(position -> position.distSqr(anchor)))
                    .orElseThrow();
        }

        GoldenSacrificialBowlBlockEntity bowl() {
            BlockEntity entity = level.getBlockEntity(anchor);
            if (!(entity instanceof GoldenSacrificialBowlBlockEntity bowl)) {
                throw new IllegalStateException("Native golden sacrificial bowl was absent at " + anchor);
            }
            return bowl;
        }

        Map<BlockPos, BlockState> currentStates() {
            Map<BlockPos, BlockState> states = new LinkedHashMap<>();
            built.keySet().forEach(position -> states.put(position, level.getBlockState(position)));
            return states;
        }

        void assertStates(Map<BlockPos, BlockState> expected) {
            for (var entry : expected.entrySet()) {
                helper.assertTrue(level.getBlockState(entry.getKey()).equals(entry.getValue()),
                        "Pentacle state changed unexpectedly at " + entry.getKey());
            }
            floor.forEach(position -> helper.assertTrue(level.getBlockState(position).is(Blocks.STONE),
                    "Capture removed an uncounted support block at " + position));
        }

        void assertSameDrawingUpToRotation(Map<BlockPos, BlockState> expected) {
            boolean matches = java.util.Arrays.stream(Rotation.values()).anyMatch(rotation ->
                    expected.entrySet().stream().allMatch(entry -> level.getBlockState(
                            anchor.offset(entry.getKey().subtract(anchor).rotate(rotation)))
                            .equals(entry.getValue().rotate(rotation))));
            helper.assertTrue(matches, "Universal drawing changed beyond a rigid canonical rotation: " + pentacle);
            floor.forEach(position -> helper.assertTrue(level.getBlockState(position).is(Blocks.STONE),
                    "Universal drawing round trip consumed the supporting floor"));
        }

        void assertRemoved() {
            built.keySet().forEach(position -> helper.assertTrue(level.getBlockState(position).isAir(),
                    "Captured block remained in world at " + position));
            floor.forEach(position -> helper.assertTrue(level.getBlockState(position).is(Blocks.STONE),
                    "Capture consumed the ordinary supporting floor"));
        }

        void assertStandardMiniature(ItemStack stack, ResourceLocation expected) {
            assertSameStack(stack, OccultismRecipeBridge.createPentacleMiniRitual(expected),
                    "Capture did not produce exactly one standard pentacle-only miniature: " + expected);
            helper.assertFalse(MiniPentacleDeployment.isDeployed(stack), "Captured miniature retained a deployment receipt");
        }

        void assertSameStack(ItemStack actual, ItemStack expected, String message) {
            helper.assertTrue(actual.getCount() == expected.getCount()
                    && ItemStack.isSameItemSameComponents(actual, expected), message);
        }

        void assertNoDrops() {
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, bounds).isEmpty(),
                    "Capture/deployment dropped items in addition to its miniature output");
        }

        void assertBusyCaptureUnchanged(ItemStack blank, GoldenSacrificialBowlBlockEntity bowl, String description) {
            ItemStack before = blank.copy();
            CompoundTag contents = bowl.saveWithoutMetadata(level.registryAccess());
            var result = MiniPentacleCapture.capture(level, actor, blank, anchor);
            helper.assertTrue(result.status() == MiniPentacleDeployment.Status.STRUCTURE_IN_USE,
                    "Capture accepted " + description + ": " + result.status());
            assertSameStack(blank, before, "Rejected " + description + " changed the blank");
            assertStates(built);
            helper.assertTrue(contents.equals(bowl.saveWithoutMetadata(level.registryAccess())),
                    "Rejected " + description + " changed its inventory or ritual state");
        }

        void useValuableVariants() {
            Block valuableBowl = BuiltInRegistries.BLOCK.get(occultism("eldritch_chalice"));
            helper.assertFalse(valuableBowl == Blocks.AIR, "Native eldritch chalice was not registered");
            set(anchor, valuableBowl.defaultBlockState());
            built.put(anchor, level.getBlockState(anchor));
            BlockPos candle = built.entrySet().stream()
                    .filter(entry -> entry.getValue().getBlock() instanceof CandleBlock)
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            BlockState fourCandles = Blocks.RED_CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 4);
            set(candle, fourCandles);
            built.put(candle, fourCandles);
            helper.assertTrue(multiblock.validate(level, anchor, Rotation.NONE),
                    "Native pentacle rejected an expensive center bowl or four colored candles");
            assertStates(built);
        }

        ItemStack legacyDeployment() {
            ItemStack legacy = OccultismRecipeBridge.createPentacleMiniRitual(pentacle);
            ListTag entries = new ListTag();
            for (var entry : built.entrySet()) {
                CompoundTag block = new CompoundTag();
                block.putLong("position", entry.getKey().asLong());
                block.put("previous_state", NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
                block.put("placed_state", NbtUtils.writeBlockState(entry.getValue()));
                BlockEntity entity = level.getBlockEntity(entry.getKey());
                if (entity != null) {
                    block.put("placed_block_entity", entity.saveWithoutMetadata(level.registryAccess()));
                }
                // Original deployment format had neither a claim UUID nor owned_by_miniature.
                entries.add(block);
            }
            CustomData.update(DataComponents.CUSTOM_DATA, legacy, tag -> {
                tag.putString("mini_pentacle_deployed_dimension", level.dimension().location().toString());
                tag.putLong("mini_pentacle_deployed_anchor", anchor.asLong());
                tag.putString("mini_pentacle_deployed_rotation", Rotation.NONE.name());
                tag.put("mini_pentacle_deployed_blocks", entries);
            });
            return ItemStack.parse(level.registryAccess(), legacy.save(level.registryAccess())).orElseThrow();
        }

        @Override
        public void close() {
            actor.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            actor.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            List<Map.Entry<BlockPos, BlockState>> restore = new ArrayList<>(originals.entrySet());
            restore.sort(Map.Entry.comparingByKey(
                    Comparator.comparingInt((BlockPos position) -> position.getY()).reversed()));
            for (var entry : restore) {
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
            }
            level.getEntitiesOfClass(ItemEntity.class, bounds).forEach(ItemEntity::discard);
        }
    }
}
