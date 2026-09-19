package com.example.mekanismmagic.gametest;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.api.RecipeEntityDisplayState;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import com.example.mekanismmagic.item.RitualSpawnEggIdentity;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.registries.MekanismDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RitualSpawnEggPersistenceGameTests {
    private static final TagKey<EntityType<?>> ANIMALS = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.parse("occultism:random_animals_common"));

    private RitualSpawnEggPersistenceGameTests() {
    }

    private static CompoundTag namedData() {
        CompoundTag data = new CompoundTag();
        data.putString("CustomName", "\"1115\"");
        return data;
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void randomEggSavesInsidePlayerAndMachineInventories(GameTestHelper helper) {
        // Establish that the exact old payload from the report violates vanilla's codec.
        CompoundTag legacy = namedData();
        legacy.putString(RitualSpawnEggItem.ENTITY_TAG, ANIMALS.location().toString());
        helper.assertTrue(CustomData.CODEC_WITH_ID.encodeStart(NbtOps.INSTANCE,
                CustomData.of(legacy)).error().isPresent(), "Crash fixture should lack an entity id");

        ItemStack egg = RitualSpawnEggItem.forTag(ANIMALS, namedData());
        egg.setCount(3);
        ItemStack loaded = roundTrip(helper, egg);
        helper.assertTrue(!loaded.has(DataComponents.ENTITY_DATA), "Random egg retained invalid entity data");
        helper.assertTrue(ANIMALS.location().equals(RitualSpawnEggItem.entityTag(loaded)), "Tag lost on save");
        helper.assertTrue(RitualSpawnEggItem.entityId(loaded) == null, "Random egg became a fixed entity");
        helper.assertTrue("\"1115\"".equals(RitualSpawnEggItem.data(loaded).getString("CustomName")),
                "Entity name lost on save");

        ItemStack machine = new ItemStack(NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_BLOCK.asItem());
        machine.set(MekanismDataComponents.ATTACHED_ITEMS, new AttachedItems(List.of(loaded)));
        roundTrip(helper, machine);

        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "EggSaveTest"));
        try {
            player.getInventory().setItem(0, loaded);
            player.getInventory().setItem(1, machine);
            // Exercise the crash's Entity -> Player -> Inventory -> ItemStack.save path.
            CompoundTag savedPlayer = player.saveWithoutId(new CompoundTag());
            helper.assertTrue(savedPlayer.getList("Inventory", 10).size() == 2, "Player inventory not saved");
        } finally {
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void deferredMetadataAndFixedEggsRemainSerializable(GameTestHelper helper) {
        CompoundTag source = namedData();
        source.putString("id", "minecraft:pig");
        ItemStack egg = RitualSpawnEggItem.forOwnedTag(ANIMALS, source);
        CustomData.update(DataComponents.CUSTOM_DATA, egg, data -> data.putString("unrelated", "keep"));
        RitualSpawnEggItem.withDeferredSpiritName(egg);
        RitualSpawnEggItem.withJobInitialization(egg, ResourceLocation.parse("occultism:spirit_lumberjack"));
        RitualSpawnEggItem.withCallingBook(egg, new ItemStack(Items.BOOK), helper.getLevel().registryAccess());
        ItemStack loaded = roundTrip(helper, egg);
        CompoundTag payload = RitualSpawnEggItem.data(loaded);
        helper.assertTrue(payload.getInt("ritual_owner_mode") == 1
                        && payload.getBoolean("ritual_generate_spirit_name")
                        && payload.contains("ritual_calling_book")
                        && payload.contains("ritual_initialize_spirit_job"), "Deferred metadata lost");
        helper.assertTrue("keep".equals(loaded.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated")),
                "Unrelated custom data overwritten");
        helper.assertTrue(source.contains("id") && !payload.contains("id"), "Tag factory mutated input or fixed its target");

        for (ItemStack fixed : List.of(RitualSpawnEggItem.forEntity(EntityType.WOLF, namedData()),
                RitualSpawnEggItem.forOwnedEntity(EntityType.WOLF, namedData()),
                RitualSpawnEggItem.forResurrectedEntity(EntityType.WOLF, namedData()))) {
            helper.assertTrue(ResourceLocation.parse("minecraft:wolf").equals(
                    RitualSpawnEggItem.entityId(roundTrip(helper, fixed))), "Fixed egg entity changed");
        }

        // Legacy reads remain supported, and any metadata update moves tag eggs to safe storage.
        ItemStack legacy = new ItemStack(MekanismMagic.RITUAL_SPAWN_EGG.get());
        CompoundTag old = namedData();
        old.putString(RitualSpawnEggItem.ENTITY_TAG, ANIMALS.location().toString());
        legacy.set(DataComponents.ENTITY_DATA, CustomData.of(old));
        helper.assertTrue(ANIMALS.location().equals(RitualSpawnEggItem.entityTag(legacy)), "Legacy tag unreadable");
        RitualSpawnEggItem.withDeferredSpiritName(legacy);
        roundTrip(helper, legacy);
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void savedRandomEggSpawnsNamedMemberOfItsTag(GameTestHelper helper) {
        ItemStack egg = roundTrip(helper, RitualSpawnEggItem.forTag(ANIMALS, namedData()));
        var display = new RecipeEntityDisplayState();
        helper.assertTrue(display.update(egg) && "\"1115\"".equals(
                display.entityData().getString("CustomName")), "Recipe preview lost deferred entity data");
        var level = helper.getLevel();
        var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "EggSpawnTest"));
        BlockPos floor = helper.absolutePos(new BlockPos(2, 6, 2));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(floor.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(floor.above(2), Blocks.AIR.defaultBlockState());
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, egg);
            InteractionResult result = egg.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
            helper.assertTrue(result == InteractionResult.CONSUME, "Saved random egg failed to spawn");
            var spawned = level.getEntities(player, new AABB(floor).inflate(4),
                    entity -> entity.hasCustomName() && "1115".equals(entity.getName().getString()));
            helper.assertTrue(spawned.size() == 1 && spawned.getFirst().getType().is(ANIMALS),
                    "Spawn did not preserve name or select from the requested tag");
            helper.assertTrue(egg.isEmpty(), "Successful spawn did not consume the egg");
            spawned.forEach(entity -> entity.discard());
        } finally {
            player.getInventory().clearContent();
        }
        helper.succeed();
    }

    private static ItemStack roundTrip(GameTestHelper helper, ItemStack stack) {
        ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(),
                stack.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(ItemStack.matches(stack, loaded), "Item changed during NBT round trip");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, loaded);
            helper.assertTrue(ItemStack.matches(loaded, ItemStack.STREAM_CODEC.decode(buffer)),
                    "Item changed during network round trip");
        } finally {
            buffer.release();
        }
        return loaded;
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 160)
    public static void realRitualOutputsHaveDistinctReadableIdentitiesWithoutChangingTheirData(GameTestHelper helper) {
        var names = new java.util.HashMap<net.minecraft.network.chat.Component, String>();
        var keys = new java.util.HashSet<String>();
        var pools = new java.util.HashSet<ResourceLocation>();
        var jobs = new java.util.HashSet<ResourceLocation>();
        int eggs = 0;
        for (var recipe : OccultismRecipeBridge.ritualJeiRecipes(helper.getLevel())) {
            ItemStack output = recipe.output();
            if (!(output.getItem() instanceof RitualSpawnEggItem)) continue;
            ItemStack before = output.copy();
            var name = output.getHoverName();
            String key = RitualSpawnEggIdentity.variantKey(output);
            helper.assertTrue(!key.isBlank() && !name.equals(net.minecraft.network.chat.Component.translatable("item.mekanism_magic.ritual_spawn_egg")),
                    "Real ritual retained a generic egg name/identity: " + recipe.recipeId());
            String previous = names.putIfAbsent(name, key);
            helper.assertTrue(previous == null || previous.equals(key), "Distinct real ritual outputs still share a display name: " + recipe.recipeId() + " / " + name.getString());
            keys.add(key);
            var job = RitualSpawnEggIdentity.job(output);
            if (job != null) jobs.add(job);
            var pool = RitualSpawnEggItem.entityTag(output);
            if (pool != null) pools.add(pool);
            java.util.List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
            output.getItem().appendHoverText(output, net.minecraft.world.item.Item.TooltipContext.of(helper.getLevel()),
                    tooltip, net.minecraft.world.item.TooltipFlag.ADVANCED);
            helper.assertTrue(!tooltip.isEmpty() && ItemStack.matches(before, output), "Name/tooltip modified a live ritual output");
            ItemStack loaded = roundTrip(helper, output);
            helper.assertTrue(key.equals(RitualSpawnEggIdentity.variantKey(loaded)) && name.equals(loaded.getHoverName()),
                    "Saved/synchronized egg lost its displayed identity: " + recipe.recipeId());
            helper.assertTrue(RitualSpawnEggIdentity.color(output, 0) == RitualSpawnEggIdentity.color(loaded, 0)
                    && RitualSpawnEggIdentity.color(output, 1) == RitualSpawnEggIdentity.color(loaded, 1), "Saved egg changed icon colors");
            eggs++;
        }
        helper.assertTrue(eggs >= 25 && jobs.size() >= 12 && pools.containsAll(List.of(
                        ResourceLocation.parse("occultism:random_animals_common"), ResourceLocation.parse("occultism:random_animals_small"),
                        ResourceLocation.parse("occultism:random_animals_special"), ResourceLocation.parse("occultism:random_animals_water"),
                        ResourceLocation.parse("occultism:random_animals_rideable"), ResourceLocation.parse("c:villagers"))),
                "Real ritual coverage unexpectedly shrank: eggs=" + eggs + ", jobs=" + jobs.size() + ", pools=" + pools.size());
        MekanismMagic.LOGGER.info("Ritual egg display: {} recipes, {} semantic variants, {} jobs, {} random pools passed naming and save/network checks",
                eggs, keys.size(), jobs.size(), pools.size());
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void playerItemNamesAndLegacyWorkerDataRemainCompatible(GameTestHelper helper) {
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("occultism:foliot"));
        CompoundTag legacyData = new CompoundTag();
        CompoundTag job = new CompoundTag();
        job.putString("factoryId", "occultism:crush_tier1");
        legacyData.put("spiritJob", job);
        legacyData.putBoolean("ritual_assign_owner", true);
        ItemStack legacy = RitualSpawnEggItem.forEntity(type, legacyData);
        ItemStack modern = RitualSpawnEggItem.withJobInitialization(RitualSpawnEggItem.forOwnedEntity(type, new CompoundTag()),
                ResourceLocation.parse("occultism:crush_tier1"));
        helper.assertTrue(legacy.getHoverName().equals(modern.getHoverName())
                && RitualSpawnEggIdentity.variantKey(legacy).equals(RitualSpawnEggIdentity.variantKey(modern)), "Existing worker egg no longer matches its current recipe variant");
        var playerName = net.minecraft.network.chat.Component.literal("My trusted crusher");
        modern.set(DataComponents.CUSTOM_NAME, playerName);
        helper.assertTrue(modern.getHoverName().equals(playerName), "Dynamic display replaced the player's anvil name");
        ItemStack saved = roundTrip(helper, modern);
        helper.assertTrue(saved.getHoverName().equals(playerName)
                && RitualSpawnEggIdentity.variantKey(saved).equals(RitualSpawnEggIdentity.variantKey(legacy)), "Renamed saved egg cannot find its original recipe");
        modern.remove(DataComponents.CUSTOM_NAME);
        modern.set(DataComponents.ITEM_NAME, playerName);
        helper.assertTrue(modern.getHoverName().equals(playerName), "Dynamic display replaced an explicit item-name component");
        helper.succeed();
    }
}
