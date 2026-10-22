package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceType;
import com.stal111.forbidden_arcanus.common.essence.EssenceStorage;
import com.stal111.forbidden_arcanus.common.essence.EssenceValue;
import com.stal111.forbidden_arcanus.common.item.QuantumCatcherItem;
import com.stal111.forbidden_arcanus.common.item.component.StoredEntity;
import com.stal111.forbidden_arcanus.core.init.ModDataComponents;
import com.stal111.forbidden_arcanus.core.init.ModItems;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Real 2.6.1 item/component coverage without capturing or creating entities. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForbiddenEntityContainerGameTests {
    private static final ForbiddenArcanusEntityContainerAdapter ADAPTER =
            ForbiddenArcanusEntityContainerAdapter.INSTANCE;
    private static final ResourceLocation COW = ResourceLocation.parse("minecraft:cow");

    private ForbiddenEntityContainerGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void everyNativeCatcherReadsAndSerializesDefensiveData(GameTestHelper helper) {
        List<Item> catchers = catchers(helper);
        for (Item item : catchers) {
            ItemStack stack = filled(item);
            ItemStack original = stack.copy();
            CompoundTag expected = payload();
            CapturedEntity captured = EntityContainerRegistry.capturedEntity(stack).orElseThrow();
            assertPayload(helper, captured, expected);
            CompoundTag returned = captured.entityData();
            returned.putString("id", "minecraft:pig");
            returned.getCompound("neoforge:attachments").putString("test", "changed");
            returned.getCompound("spiritJob").putString("factoryId", "changed");
            assertPayload(helper, captured, expected);
            assertPayload(helper, ADAPTER.capturedEntity(stack).orElseThrow(), expected);
            helper.assertTrue(ItemStack.matches(original, stack), "Reading mutated the native catcher");
            ItemStack restored = roundTrip(helper, stack);
            assertPayload(helper, EntityContainerRegistry.capturedEntity(restored).orElseThrow(), expected);
            helper.assertTrue(restored.get(ModDataComponents.STORED_ENTITY)
                            .getEntityType().orElseThrow() == net.minecraft.world.entity.EntityType.COW,
                    "Native StoredEntity cannot read the restored cow");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void emptyMalformedPlayerAndSpoofedPayloadsAreRejected(GameTestHelper helper) {
        for (Item item : catchers(helper)) {
            assertRejected(helper, item.getDefaultInstance(), "Unfilled catcher");
            for (String id : List.of("", "Not an entity ID!", "minecraft:missing_forbidden_test_entity",
                    "minecraft:player")) {
                ItemStack malformed = filled(item);
                CompoundTag data = payload();
                data.putString("id", id);
                malformed.set(ModDataComponents.STORED_ENTITY, new StoredEntity(CustomData.of(data)));
                assertRejected(helper, malformed, "Invalid entity ID " + id);
            }
            ItemStack noId = filled(item);
            noId.set(ModDataComponents.STORED_ENTITY, new StoredEntity(CustomData.EMPTY));
            assertRejected(helper, noId, "Stored entity without id");
            CompoundTag numericId = payload();
            numericId.putInt("id", 1);
            noId.set(ModDataComponents.STORED_ENTITY, new StoredEntity(CustomData.of(numericId)));
            assertRejected(helper, noId, "Non-string entity id");
        }
        ItemStack spoofed = new ItemStack(Items.STICK);
        spoofed.set(ModDataComponents.STORED_ENTITY, new StoredEntity(CustomData.of(payload())));
        assertRejected(helper, spoofed, "Stored entity on an unrelated item");
        assertRejected(helper, ItemStack.EMPTY, "Empty stack");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void releaseRemovesOnlyStoredEntityAndPreviewNeverMutatesInput(GameTestHelper helper) {
        for (Item item : catchers(helper)) {
            ItemStack stack = filled(item);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Named quantum catcher"));
            stack.set(DataComponents.MAX_STACK_SIZE, 1);
            stack.set(DataComponents.MAX_DAMAGE, 400);
            stack.set(DataComponents.DAMAGE, 3);
            stack.set(ModDataComponents.ESSENCE_STORAGE,
                    new EssenceStorage(EssenceValue.of(EssenceType.AUREAL, 321), 1_000, true));
            stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.UNBREAKING), 2);
            CompoundTag custom = new CompoundTag();
            custom.putString("unrelated", "must survive release");
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
            ItemStack original = stack.copy();
            ItemStack expected = original.copy();
            expected.remove(ModDataComponents.STORED_ENTITY);
            for (int index = 0; index < 5; index++) {
                ItemStack preview = EntityContainerRegistry.previewEmpty(stack).orElseThrow();
                helper.assertTrue(ItemStack.matches(expected, preview), "Preview changed unrelated components");
                helper.assertTrue(ItemStack.matches(original, stack), "Preview modified the input");
            }
            ItemStack emptied = EntityContainerRegistry.emptyCopy(stack, helper.getLevel()).orElseThrow();
            helper.assertTrue(ItemStack.matches(expected, emptied), "Actual emptying did not match native release");
            helper.assertTrue(ItemStack.matches(original, stack), "emptyCopy modified the input");
            helper.assertTrue(ItemStack.matches(expected,
                            EntityContainerRegistry.emptyCopy(stack, null).orElseThrow()),
                    "Entity-free emptying unexpectedly requires a world");
            helper.assertTrue(ItemStack.matches(expected, roundTrip(helper, emptied)),
                    "Emptied native catcher changed on serialization");
            ItemStack mutable = original.copy();
            helper.assertTrue(EntityContainerRegistry.empty(mutable)
                            && ItemStack.matches(expected, mutable),
                    "In-place emptying changed anything except STORED_ENTITY");
            helper.assertTrue(!EntityContainerRegistry.empty(mutable), "An empty catcher was consumed twice");
            ItemStack stacked = original.copyWithCount(3);
            ItemStack stackedBefore = stacked.copy();
            helper.assertTrue(ItemStack.matches(expected,
                            EntityContainerRegistry.emptyCopy(stacked, null).orElseThrow())
                            && ItemStack.matches(stackedBefore, stacked),
                    "Single-container return copied more than one vessel or changed the input stack");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void passengersStayReadableAndAllDestructivePathsRejectThem(GameTestHelper helper) {
        for (Item item : catchers(helper)) {
            for (boolean malformed : List.of(false, true)) {
                CompoundTag data = payload();
                if (malformed) {
                    data.putString("Passengers", "not a list");
                } else {
                    ListTag passengers = new ListTag();
                    CompoundTag passenger = new CompoundTag();
                    passenger.putString("id", "minecraft:pig");
                    passengers.add(passenger);
                    data.put("Passengers", passengers);
                }
                ItemStack stack = filled(item);
                stack.set(ModDataComponents.STORED_ENTITY, new StoredEntity(CustomData.of(data)));
                ItemStack original = stack.copy();
                assertPayload(helper, EntityContainerRegistry.capturedEntity(stack).orElseThrow(), data);
                helper.assertTrue(!EntityContainerRegistry.isSacrificeContainer(stack)
                                && EntityContainerRegistry.previewEmpty(stack).isEmpty()
                                && EntityContainerRegistry.emptyCopy(stack, helper.getLevel()).isEmpty()
                                && !EntityContainerRegistry.empty(stack)
                                && ADAPTER.previewEmpty(stack).isEmpty()
                                && ADAPTER.emptyCopy(stack, null).isEmpty()
                                && !ADAPTER.empty(stack),
                        "A destructive catcher operation accepted passengers");
                helper.assertTrue(ItemStack.matches(original, stack), "Passenger rejection modified the item");
            }
            CompoundTag noPassengers = payload();
            noPassengers.put("Passengers", new ListTag());
            ItemStack stack = filled(item);
            stack.set(ModDataComponents.STORED_ENTITY, new StoredEntity(CustomData.of(noPassengers)));
            helper.assertTrue(EntityContainerRegistry.isSacrificeContainer(stack),
                    "An empty passenger list incorrectly blocks sacrifice");
        }
        helper.succeed();
    }

    private static List<Item> catchers(GameTestHelper helper) {
        // Do not self-register the adapter here: the test also verifies that
        // the real version-gated content module attached the integration.
        List<Item> items = new ArrayList<>();
        items.add(ModItems.QUANTUM_CATCHER.get());
        ModItems.DYED_QUANTUM_CATCHERS.values().forEach(entry -> items.add(entry.get()));
        items.add(ModItems.BOSS_CATCHER.get());
        helper.assertTrue(items.size() == 18 && items.stream().allMatch(QuantumCatcherItem.class::isInstance),
                "Expected the 18 verified native quantum/boss catcher variants");
        return List.copyOf(items);
    }

    private static ItemStack filled(Item item) {
        ItemStack stack = item.getDefaultInstance();
        stack.set(ModDataComponents.STORED_ENTITY, new StoredEntity(CustomData.of(payload())));
        return stack;
    }

    private static CompoundTag payload() {
        CompoundTag data = new CompoundTag();
        data.putString("id", COW.toString());
        data.putString("CustomName", "\"Forbidden captured cow\"");
        data.putFloat("Health", 13.5F);
        data.putInt("Age", -1200);
        CompoundTag attachments = new CompoundTag();
        attachments.putString("test", "retain nested attachment data");
        data.put("neoforge:attachments", attachments);
        CompoundTag job = new CompoundTag();
        job.putString("factoryId", "occultism:crafting_test");
        data.put("spiritJob", job);
        return data;
    }

    private static void assertPayload(GameTestHelper helper, CapturedEntity captured, CompoundTag expected) {
        helper.assertTrue(COW.equals(captured.entityId()) && expected.equals(captured.entityData()),
                "Captured entity ID, nested data or components were lost");
    }

    private static void assertRejected(GameTestHelper helper, ItemStack stack, String description) {
        ItemStack original = stack.copy();
        helper.assertTrue(ADAPTER.capturedEntity(stack).isEmpty()
                        && EntityContainerRegistry.capturedEntity(stack).isEmpty()
                        && EntityContainerRegistry.previewEmpty(stack).isEmpty()
                        && EntityContainerRegistry.emptyCopy(stack, helper.getLevel()).isEmpty()
                        && !EntityContainerRegistry.empty(stack)
                        && !ADAPTER.empty(stack),
                description + " was accepted");
        helper.assertTrue(ItemStack.matches(original, stack), description + " rejection mutated the item");
    }

    private static ItemStack roundTrip(GameTestHelper helper, ItemStack stack) {
        ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(),
                stack.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(ItemStack.matches(stack, loaded), "Catcher changed during NBT serialization");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, loaded);
            ItemStack networked = ItemStack.STREAM_CODEC.decode(buffer);
            helper.assertTrue(ItemStack.matches(loaded, networked), "Catcher changed during network serialization");
            return networked;
        } finally {
            buffer.release();
        }
    }
}
