package com.example.mekanismmagic.gametest;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.hollingsworth.arsnouveau.common.items.data.MobJarData;
import io.netty.buffer.Unpooled;
import mekanism.api.Action;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Real-mod integration tests. All four optional mods are mandatory in this
 * dedicated run; missing items/classes must fail rather than skip coverage.
 */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntityContainerIntegrationGameTests {
    private static final String LEAD_CLASS =
            "dev.shadowsoffire.apothic_enchanting.objects.EnderLeadItem";
    private static final String CATCHER_CLASS =
            "com.direwolf20.justdirethings.common.items.CreatureCatcher";
    private static final String CATCHER_ENTITY_CLASS =
            "com.direwolf20.justdirethings.common.entities.CreatureCatcherEntity";
    private static final String SOUL_CLASS = "com.enderio.enderio.api.soul.Soul";
    private static final String VIAL_CLASS =
            "com.enderio.enderio.content.tools.vials.SoulVialItem";
    private static final ResourceLocation COW = ResourceLocation.parse("minecraft:cow");
    private static final String COW_NAME = "Container integration cow";
    private static final String IF_DATA = "industrialforegoing:mob_imprisonment";
    private static final String JDT_TYPE = "justdirethings:entitytype";
    private static final String LEAD_TYPE = "apothic_enchanting:leashed_entity_type";
    private static final String SOUL_DATA = "enderio:soul";
    private static final int SACRIFICE = NativeMagicMachineBlockEntity.SACRIFICE_SLOT;
    private static final int REMAINDER = NativeMagicMachineBlockEntity.RITUAL_REMAINDER_SLOT;

    private enum Container {
        FLIMSY("apothic_enchanting:flimsy_ender_lead"),
        LEAD("apothic_enchanting:ender_lead"),
        OCCULT("apothic_enchanting:occult_ender_lead"),
        IMPRISONMENT("industrialforegoing:mob_imprisonment_tool"),
        CATCHER("justdirethings:creaturecatcher"),
        VIAL("enderio:soul_vial");

        private final ResourceLocation id;

        Container(String id) {
            this.id = ResourceLocation.parse(id);
        }

        private boolean isLead() {
            return id.getNamespace().equals("apothic_enchanting");
        }
    }

    private EntityContainerIntegrationGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void realContainersReadAndSerializeDefensiveEntityCopies(GameTestHelper helper) {
        requireMods(helper);
        for (Container kind : Container.values()) {
            ItemStack stack = filled(helper, kind);
            ItemStack original = stack.copy();
            helper.assertTrue(nativeContains(kind, stack), kind + " native item did not capture the cow");
            CapturedEntity captured = EntityContainerRegistry.capturedEntity(stack).orElseThrow();
            assertCow(helper, captured, kind.toString());
            CompoundTag returnedData = captured.entityData();
            returnedData.putString("CustomName", "\"mutated copy\"");
            returnedData.putString("id", "minecraft:pig");
            assertCow(helper, captured, kind + " repeated accessor");
            assertCow(helper, EntityContainerRegistry.capturedEntity(stack).orElseThrow(),
                    kind + " repeated registry lookup");
            helper.assertTrue(ItemStack.matches(original, stack), kind + " read mutated the original item");
            ItemStack restored = roundTrip(helper, stack);
            assertCow(helper, EntityContainerRegistry.capturedEntity(restored).orElseThrow(),
                    kind + " persisted cow");
            helper.assertTrue(nativeContains(kind, restored), kind + " native item cannot read saved filled stack");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void emptyMalformedAndSpoofedItemsAreRejected(GameTestHelper helper) {
        requireMods(helper);
        for (Container kind : Container.values()) {
            ItemStack empty = empty(kind);
            assertRejected(helper, empty, kind + " empty item");
            helper.assertTrue(!nativeContains(kind, empty), kind + " default item is unexpectedly filled");

            ItemStack valid = filled(helper, kind);
            ItemStack spoofed = new ItemStack(Items.STICK);
            spoofed.applyComponents(valid.getComponentsPatch());
            assertRejected(helper, spoofed, kind + " components on non-whitelisted stick");

            ItemStack missingType = valid.copy();
            missingType.remove(component(kind.isLead() ? LEAD_TYPE : switch (kind) {
                case IMPRISONMENT -> IF_DATA;
                case CATCHER -> JDT_TYPE;
                case VIAL -> SOUL_DATA;
                default -> throw new AssertionError(kind);
            }));
            assertRejected(helper, missingType, kind + " missing authoritative component");
            if (kind.isLead()) {
                ItemStack missingData = valid.copy();
                missingData.remove(DataComponents.ENTITY_DATA);
                assertRejected(helper, missingData, kind + " missing captured entity data");
            }
        }
        for (String badId : List.of("minecraft:missing_entity_for_container_test", "Not an entity ID!", "minecraft:player")) {
            ItemStack imprisonment = filled(helper, Container.IMPRISONMENT);
            CompoundTag badData = ((CompoundTag) imprisonment.get(component(IF_DATA))).copy();
            badData.putString("entity", badId);
            imprisonment.set(component(IF_DATA), badData);
            assertRejected(helper, imprisonment, "IF invalid entity ID " + badId);
            ItemStack catcher = filled(helper, Container.CATCHER);
            catcher.set(component(JDT_TYPE), badId);
            assertRejected(helper, catcher, "JDT invalid entity ID " + badId);

            // These older adapters predate the component integrations. The
            // shared registry must also reject their invalid or player types.
            CompoundTag legacyData = new CompoundTag();
            legacyData.putString("id", badId);
            ItemStack soulGem = BuiltInRegistries.ITEM.getOptional(
                    ResourceLocation.parse("occultism:soul_gem")).orElseThrow().getDefaultInstance();
            soulGem.set(DataComponents.ENTITY_DATA, CustomData.of(legacyData));
            assertRejected(helper, soulGem, "Legacy Occultism invalid entity ID " + badId);
            ItemStack mobJar = BuiltInRegistries.ITEM.getOptional(
                    ResourceLocation.parse("ars_nouveau:mob_jar")).orElseThrow().getDefaultInstance();
            mobJar.set(component("ars_nouveau:mob_jar"), new MobJarData(legacyData.copy(), null));
            assertRejected(helper, mobJar, "Legacy Ars invalid entity ID " + badId);
        }
        // EntityType-valued components cannot encode arbitrary invalid IDs;
        // their real codec must reject one before it can enter an ItemStack.
        CompoundTag invalidSoul = new CompoundTag();
        invalidSoul.putString("entity_type", "minecraft:missing_entity_for_container_test");
        invalidSoul.put("entity_tag", new CompoundTag());
        helper.assertTrue(component(SOUL_DATA).codec().parse(NbtOps.INSTANCE, invalidSoul).error().isPresent(),
                "EnderIO's native Soul codec accepted an unregistered entity type");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void emptyCopiesMatchNativeComponentAndSerializationRules(GameTestHelper helper) {
        requireMods(helper);
        for (Container kind : Container.values()) {
            ItemStack full = filled(helper, kind);
            full.set(DataComponents.CUSTOM_NAME, Component.literal("Keep only when native release does"));
            CustomData.update(DataComponents.CUSTOM_DATA, full, tag -> tag.putString("unrelated", "preserved"));
            ItemStack original = full.copy();
            ItemStack preview = EntityContainerRegistry.previewEmpty(full).orElseThrow();
            helper.assertTrue(ItemStack.matches(original, full), kind + " preview mutated input");
            helper.assertTrue(!EntityContainerRegistry.isFilled(preview) && !nativeContains(kind, preview),
                    kind + " preview is not natively empty");
            ItemStack result = EntityContainerRegistry.emptyCopy(full, helper.getLevel()).orElseThrow();
            helper.assertTrue(ItemStack.matches(original, full), kind + " emptyCopy mutated input");
            helper.assertTrue(result.isEmpty() || !nativeContains(kind, result), kind + " result remains natively filled");
            if (result.isEmpty()) {
                helper.assertTrue(kind.isLead() && full.getMaxDamage() == 1, kind + " unexpectedly destroyed container");
                continue;
            }
            helper.assertTrue(result.getCount() == 1 && !EntityContainerRegistry.isFilled(result),
                    kind + " emptied copy has incorrect count or captured entity");
            if (kind == Container.CATCHER || kind == Container.VIAL) {
                helper.assertTrue(ItemStack.matches(empty(kind), result),
                        kind + " native release must restore the complete default item");
            } else {
                helper.assertTrue(full.get(DataComponents.CUSTOM_NAME).equals(result.get(DataComponents.CUSTOM_NAME))
                                && "preserved".equals(result.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated")),
                        kind + " native release must retain unrelated components");
            }
            if (kind == Container.VIAL) {
                helper.assertTrue(!result.has(component("enderio:entity_max_health")),
                        "Empty soul vial retained captured entity health");
            }
            ItemStack restored = roundTrip(helper, result);
            helper.assertTrue(!nativeContains(kind, restored), kind + " saved empty item became natively filled");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void enderLeadPreviewDoesNotDamageAndReleaseHonorsBreakage(GameTestHelper helper) {
        requireMods(helper);
        for (Container kind : List.of(Container.FLIMSY, Container.LEAD, Container.OCCULT)) {
            ItemStack full = filled(helper, kind);
            int durability = full.getMaxDamage();
            helper.assertTrue(durability > 0, kind + " real lead has no durability component");
            ItemStack original = full.copy();
            for (int previewIndex = 0; previewIndex < 8; previewIndex++) {
                ItemStack preview = EntityContainerRegistry.previewEmpty(full).orElseThrow();
                helper.assertTrue(ItemStack.matches(original, full), kind + " repeated preview wore out the input");
                helper.assertTrue(!preview.isEmpty() && preview.getDamageValue() == full.getDamageValue(),
                        kind + " preview applied release durability damage");
            }
            if (kind == Container.OCCULT) {
                helper.assertTrue(full.has(DataComponents.UNBREAKABLE),
                        "Native occult ender lead lost its default UNBREAKABLE component");
                ItemStack nativeReleased = EntityContainerRegistry.emptyCopy(full, helper.getLevel()).orElseThrow();
                helper.assertTrue(!nativeReleased.isEmpty()
                                && nativeReleased.getDamageValue() == full.getDamageValue()
                                && nativeReleased.has(DataComponents.UNBREAKABLE)
                                && !nativeContains(kind, nativeReleased),
                        "Default occult ender lead was damaged or destroyed on release");
                helper.assertTrue(ItemStack.matches(nativeReleased,
                                EntityContainerRegistry.emptyCopy(full, null).orElseThrow()),
                        "Default unbreakable occult lead cannot be emptied without server durability context");
                ItemStackHandler nativeNoLevel = inventory();
                nativeNoLevel.setStackInSlot(SACRIFICE, full.copy());
                helper.assertTrue(OccultismRecipeBridge.consumeSacrifice(nativeNoLevel, SACRIFICE)
                                && nativeNoLevel.getStackInSlot(SACRIFICE).isEmpty()
                                && ItemStack.matches(nativeReleased, nativeNoLevel.getStackInSlot(REMAINDER)),
                        "Default occult lead failed no-level sacrifice recovery");
                helper.assertTrue(ItemStack.matches(original, full),
                        "Default occult lead release mutated the source stack");

                // The native occult item is unbreakable. Durability assertions
                // below deliberately exercise a component-modified copy.
                full = full.copy();
                full.remove(DataComponents.UNBREAKABLE);
                original = full.copy();
            }
            ItemStack released = EntityContainerRegistry.emptyCopy(full, helper.getLevel()).orElseThrow();
            helper.assertTrue(durability == 1 ? released.isEmpty()
                            : !released.isEmpty() && released.getDamageValue() == 1 && !nativeContains(kind, released),
                    kind + " release did not apply exactly one durability point");
            helper.assertTrue(ItemStack.matches(original, full), kind + " release changed source stack");

            ItemStackHandler missingServerContext = inventory();
            missingServerContext.setStackInSlot(SACRIFICE, original.copy());
            List<ItemStack> missingContextSnapshot = inventorySnapshot(missingServerContext);
            helper.assertTrue(!OccultismRecipeBridge.consumeSacrifice(missingServerContext, SACRIFICE),
                    kind + " legacy no-level path waived release durability");
            assertInventory(helper, missingServerContext, missingContextSnapshot, kind + " no-level release");

            ItemStack lastUse = full.copy();
            lastUse.setDamageValue(durability - 1);
            ItemStack lastUseOriginal = lastUse.copy();
            helper.assertTrue(EntityContainerRegistry.emptyCopy(lastUse, helper.getLevel()).orElseThrow().isEmpty(),
                    kind + " final release did not break lead");
            helper.assertTrue(ItemStack.matches(lastUseOriginal, lastUse), kind + " final release mutated input");
            ItemStackHandler sacrifice = inventory();
            sacrifice.setStackInSlot(SACRIFICE, lastUse.copy());
            helper.assertTrue(OccultismRecipeBridge.consumeSacrifice(sacrifice, SACRIFICE, helper.getLevel())
                            && sacrifice.getStackInSlot(SACRIFICE).isEmpty()
                            && sacrifice.getStackInSlot(REMAINDER).isEmpty(),
                    kind + " broken sacrificial lead left a ghost remainder or failed consumption");

            lastUse.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            ItemStack unbreakableOriginal = lastUse.copy();
            ItemStack unbreakable = EntityContainerRegistry.emptyCopy(lastUse, helper.getLevel()).orElseThrow();
            helper.assertTrue(!unbreakable.isEmpty() && unbreakable.getDamageValue() == durability - 1
                            && unbreakable.has(DataComponents.UNBREAKABLE) && !nativeContains(kind, unbreakable),
                    kind + " release ignored UNBREAKABLE");
            helper.assertTrue(ItemStack.matches(unbreakableOriginal, lastUse), kind + " UNBREAKABLE source changed");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void sacrificeReturnsContainersAndBlockedRemaindersDoNotConsume(GameTestHelper helper) {
        requireMods(helper);
        for (Container kind : Container.values()) {
            ItemStack full = filled(helper, kind);
            // Exercise recovery for every lead even if its configured durability is one.
            if (kind.isLead()) {
                full.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            }
            ItemStack original = full.copy();
            helper.assertTrue(OccultismRecipeBridge.isSacrificeItem(full), kind + " absent from sacrifice acceptance");
            ItemStackHandler inventory = inventory();
            inventory.setStackInSlot(SACRIFICE, full);
            ItemStack expectedRemainder = EntityContainerRegistry.emptyCopy(full, helper.getLevel()).orElseThrow();
            helper.assertTrue(OccultismRecipeBridge.consumeSacrifice(inventory, SACRIFICE, helper.getLevel()),
                    kind + " sacrifice failed with free recovery slot");
            helper.assertTrue(inventory.getStackInSlot(SACRIFICE).isEmpty()
                            && ItemStack.matches(expectedRemainder, inventory.getStackInSlot(REMAINDER))
                            && !nativeContains(kind, inventory.getStackInSlot(REMAINDER)),
                    kind + " sacrifice did not return the native empty container");

            inventory.setStackInSlot(SACRIFICE, original.copy());
            inventory.setStackInSlot(REMAINDER, new ItemStack(Items.COBBLESTONE, 64));
            List<ItemStack> blockedSnapshot = inventorySnapshot(inventory);
            helper.assertTrue(!OccultismRecipeBridge.consumeSacrifice(inventory, SACRIFICE, helper.getLevel()),
                    kind + " sacrifice succeeded despite blocked recovery slot");
            assertInventory(helper, inventory, blockedSnapshot, kind + " blocked sacrifice");

            inventory.setStackInSlot(SACRIFICE, empty(kind));
            inventory.setStackInSlot(REMAINDER, ItemStack.EMPTY);
            List<ItemStack> emptySnapshot = inventorySnapshot(inventory);
            helper.assertTrue(!OccultismRecipeBridge.consumeSacrifice(inventory, SACRIFICE, helper.getLevel()),
                    kind + " empty container was accepted as a sacrifice");
            assertInventory(helper, inventory, emptySnapshot, kind + " empty-container sacrifice");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void passengerPayloadsRemainReadableButCannotBeSacrificed(GameTestHelper helper) {
        requireMods(helper);
        for (Container kind : Container.values()) {
            ItemStack stack = filled(helper, kind);
            addPassenger(stack, kind);
            ItemStack original = stack.copy();
            CapturedEntity root = EntityContainerRegistry.capturedEntity(stack).orElseThrow();
            helper.assertTrue(COW.equals(root.entityId())
                            && root.entityData().getList("Passengers", 10).size() == 1,
                    kind + " passenger payload lost its readable root cow");
            helper.assertTrue(EntityContainerRegistry.isFilled(stack)
                            && !EntityContainerRegistry.isSacrificeContainer(stack)
                            && !OccultismRecipeBridge.isSacrificeItem(stack),
                    kind + " passenger payload is not restricted to read-only use");
            helper.assertTrue(EntityContainerRegistry.previewEmpty(stack).isEmpty()
                            && EntityContainerRegistry.emptyCopy(stack, helper.getLevel()).isEmpty(),
                    kind + " passenger payload allowed destructive emptying");
            ItemStackHandler inventory = inventory();
            inventory.setStackInSlot(SACRIFICE, stack);
            List<ItemStack> before = inventorySnapshot(inventory);
            helper.assertTrue(!OccultismRecipeBridge.consumeSacrifice(inventory, SACRIFICE, helper.getLevel()),
                    kind + " sacrifice silently consumed passengers");
            assertInventory(helper, inventory, before, kind + " passenger sacrifice");
            helper.assertTrue(ItemStack.matches(original, stack), kind + " read-only passenger handling mutated source");
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 200)
    public static void drygmyUsesRealContainersAndKeepsAllCapturedEntities(GameTestHelper helper) {
        requireMods(helper);
        helper.setBlock(new BlockPos(1, 1, 1), ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        DrygmySimulatorBlockEntity machine = helper.getBlockEntity(new BlockPos(1, 1, 1));
        List<Container> kinds = List.of(Container.LEAD, Container.IMPRISONMENT, Container.CATCHER, Container.VIAL);
        List<IInventorySlot> persistent = machine.mekanismMagicPersistentInputs();
        List<ItemStack> expected = new ArrayList<>();
        helper.assertTrue(persistent.size() >= kinds.size(), "Drygmy does not expose enough persistent slots");
        for (int index = 0; index < kinds.size(); index++) {
            ItemStack captured = filled(helper, kinds.get(index));
            if (index == 0) {
                // A passenger is forbidden for sacrifice, but Drygmy may read
                // this same root entity while preserving its entire container.
                addPassenger(captured, kinds.get(index));
            }
            expected.add(captured.copy());
            int slot = unsidedIndex(machine, persistent.get(index));
            helper.assertTrue(slot >= 0 && machine.isItemValid(slot, captured, null),
                    kinds.get(index) + " rejected by real Drygmy insertion predicate");
            helper.assertTrue(machine.insertItem(slot, captured, null, Action.EXECUTE).isEmpty(),
                    kinds.get(index) + " failed actual automated Drygmy insertion");
        }
        // Do not call seedDevelopmentTest: it replaces the first input with an
        // Ars mob jar and would mask whether these real optional items work.
        String signature = (String) invoke(DrygmySimulatorBlockEntity.class, "jarSignature", machine, new Class<?>[0]);
        for (int operation = 0; operation < 3; operation++) {
            invoke(DrygmySimulatorBlockEntity.class, "prepareOutputs", machine,
                    new Class<?>[]{ServerLevel.class, String.class}, helper.getLevel(), signature);
            List<?> entities = (List<?>) field(machine, "cachedLootEntities");
            helper.assertTrue(entities.size() == kinds.size(), "Drygmy did not cache exactly four root cows");
            for (Object entity : entities) {
                helper.assertTrue(entity instanceof Cow cow && cow.getName().getString().equals(COW_NAME),
                        "Drygmy failed to instantiate the captured type and name");
            }
            List<?> outputs = (List<?>) field(machine, "pendingOutputs");
            helper.assertTrue(outputs.stream().anyMatch(output -> output instanceof ItemStack stack
                            && !stack.isEmpty() && (stack.is(Items.BEEF) || stack.is(Items.LEATHER))),
                    "Drygmy did not prepare native cow loot from the four real containers");
            for (int index = 0; index < kinds.size(); index++) {
                ItemStack retained = persistent.get(index).getStack();
                helper.assertTrue(ItemStack.matches(expected.get(index), retained)
                                && nativeContains(kinds.get(index), retained),
                        kinds.get(index) + " lost components, count or durability while Drygmy prepared loot");
            }
        }
        helper.succeed();
    }

    private static void requireMods(GameTestHelper helper) {
        for (String mod : List.of("apothic_enchanting", "industrialforegoing", "justdirethings", "enderio", "ars_nouveau", "occultism")) {
            helper.assertTrue(ModList.get().isLoaded(mod), "Mandatory real-mod fixture is missing: " + mod);
        }
        for (Container kind : Container.values()) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(kind.id), "Mandatory real item is missing: " + kind.id);
        }
    }

    private static ItemStack empty(Container kind) {
        return BuiltInRegistries.ITEM.getOptional(kind.id).orElseThrow().getDefaultInstance();
    }

    /** Construct through the installed mods' actual capture/factory methods. */
    private static ItemStack filled(GameTestHelper helper, Container kind) {
        Cow cow = EntityType.COW.create(helper.getLevel());
        helper.assertTrue(cow != null, "Unable to create fixture cow");
        cow.setCustomName(Component.literal(COW_NAME));
        cow.setHealth(7.0F);
        try {
            ItemStack stack;
            if (kind.isLead() || kind == Container.IMPRISONMENT) {
                stack = empty(kind);
                var player = FakePlayerFactory.getMinecraft(helper.getLevel());
                ItemStack previousHand = player.getItemInHand(InteractionHand.MAIN_HAND).copy();
                try {
                    helper.assertTrue(stack.getItem().interactLivingEntity(stack, player, cow,
                                    InteractionHand.MAIN_HAND).consumesAction(),
                            kind + " native capture rejected a live cow");
                } finally {
                    player.setItemInHand(InteractionHand.MAIN_HAND, previousHand);
                }
            } else if (kind == Container.CATCHER) {
                Class<?> projectileClass = Class.forName(CATCHER_ENTITY_CLASS);
                Entity projectile = (Entity) projectileClass.getConstructor(Level.class, LivingEntity.class)
                        .newInstance(helper.getLevel(), FakePlayerFactory.getMinecraft(helper.getLevel()));
                try {
                    stack = (ItemStack) invoke(projectileClass, "createItemStack", projectile,
                            new Class<?>[]{Mob.class}, cow);
                } finally {
                    projectile.discard();
                }
            } else {
                Class<?> soulClass = Class.forName(SOUL_CLASS);
                Object soul = invoke(soulClass, "of", null, new Class<?>[]{LivingEntity.class}, cow);
                stack = (ItemStack) invoke(Class.forName(VIAL_CLASS), "forSoul", null,
                        new Class<?>[]{soulClass}, soul);
                stack.set(component("enderio:entity_max_health"), cow.getMaxHealth());
            }
            helper.assertTrue(stack.is(BuiltInRegistries.ITEM.get(kind.id)), kind + " native factory returned wrong item");
            return stack;
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Installed native capture API changed for " + kind, error);
        } finally {
            cow.discard();
        }
    }

    private static boolean nativeContains(Container kind, ItemStack stack) {
        try {
            if (kind.isLead()) {
                return (boolean) invoke(Class.forName(LEAD_CLASS), "containsEntity", null,
                        new Class<?>[]{ItemStack.class}, stack);
            }
            return switch (kind) {
                case IMPRISONMENT -> (boolean) invoke(empty(kind).getItem().getClass(), "containsEntity",
                        empty(kind).getItem(), new Class<?>[]{ItemStack.class}, stack);
                case CATCHER -> (boolean) invoke(Class.forName(CATCHER_CLASS), "hasEntity", null,
                        new Class<?>[]{ItemStack.class}, stack);
                // SoulVialItem.isFoil calls the real Soul.hasEntity method,
                // using Soul.EMPTY when the component is absent.
                case VIAL -> empty(kind).getItem().isFoil(stack);
                default -> throw new AssertionError(kind);
            };
        } catch (ClassNotFoundException error) {
            throw new AssertionError("Mandatory native container class missing for " + kind, error);
        }
    }

    private static void addPassenger(ItemStack stack, Container kind) {
        CompoundTag passenger = new CompoundTag();
        passenger.putString("id", "minecraft:pig");
        ListTag passengers = new ListTag();
        passengers.add(passenger);
        if (kind == Container.IMPRISONMENT) {
            CompoundTag data = ((CompoundTag) stack.get(component(IF_DATA))).copy();
            data.put("Passengers", passengers);
            stack.set(component(IF_DATA), data);
        } else if (kind == Container.VIAL) {
            DataComponentType<Object> type = component(SOUL_DATA);
            CompoundTag encoded = (CompoundTag) type.codec().encodeStart(NbtOps.INSTANCE, stack.get(type)).getOrThrow();
            encoded.getCompound("entity_tag").put("Passengers", passengers);
            stack.set(type, type.codec().parse(NbtOps.INSTANCE, encoded).getOrThrow());
        } else {
            CustomData.update(DataComponents.ENTITY_DATA, stack, data -> data.put("Passengers", passengers));
        }
    }

    private static void assertCow(GameTestHelper helper, CapturedEntity captured, String description) {
        CompoundTag data = captured.entityData();
        helper.assertTrue(COW.equals(captured.entityId()) && "minecraft:cow".equals(data.getString("id"))
                        && data.getString("CustomName").contains(COW_NAME) && data.getFloat("Health") == 7.0F,
                description + " lost the captured cow type, name or health NBT");
    }

    private static void assertRejected(GameTestHelper helper, ItemStack stack, String description) {
        ItemStack original = stack.copy();
        helper.assertTrue(!EntityContainerRegistry.isFilled(stack)
                        && !EntityContainerRegistry.isSacrificeContainer(stack)
                        && !OccultismRecipeBridge.isSacrificeItem(stack)
                        && EntityContainerRegistry.previewEmpty(stack).isEmpty()
                        && EntityContainerRegistry.emptyCopy(stack, helper.getLevel()).isEmpty(),
                description + " was accepted as a valid captured entity");
        helper.assertTrue(ItemStack.matches(original, stack), description + " rejection mutated the item");
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Object> component(String id) {
        return (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .getOptional(ResourceLocation.parse(id)).orElseThrow();
    }

    private static ItemStack roundTrip(GameTestHelper helper, ItemStack stack) {
        ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(),
                stack.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(ItemStack.matches(stack, loaded), "Real container changed during NBT serialization");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, loaded);
            ItemStack networked = ItemStack.STREAM_CODEC.decode(buffer);
            helper.assertTrue(ItemStack.matches(loaded, networked), "Real container changed during network serialization");
            return networked;
        } finally {
            buffer.release();
        }
    }

    private static ItemStackHandler inventory() {
        return new ItemStackHandler(NativeMagicMachineBlockEntity.MACHINE_INVENTORY_SIZE);
    }

    private static List<ItemStack> inventorySnapshot(ItemStackHandler inventory) {
        List<ItemStack> snapshot = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            snapshot.add(inventory.getStackInSlot(slot).copy());
        }
        return snapshot;
    }

    private static void assertInventory(GameTestHelper helper, ItemStackHandler inventory,
                                        List<ItemStack> expected, String description) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            helper.assertTrue(ItemStack.matches(expected.get(slot), inventory.getStackInSlot(slot)),
                    description + " changed inventory slot " + slot);
        }
    }

    private static int unsidedIndex(DrygmySimulatorBlockEntity machine, IInventorySlot target) {
        List<IInventorySlot> slots = machine.getInventorySlots(null);
        for (int index = 0; index < slots.size(); index++) {
            if (slots.get(index) == target) {
                return index;
            }
        }
        return -1;
    }

    private static Object invoke(Class<?> owner, String name, Object receiver, Class<?>[] parameters,
                                 Object... arguments) {
        try {
            Method method = owner.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method.invoke(receiver, arguments);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Required runtime method failed: " + owner.getName() + "." + name, error);
        }
    }

    private static Object field(DrygmySimulatorBlockEntity machine, String name) {
        try {
            Field field = DrygmySimulatorBlockEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(machine);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Required Drygmy state is unavailable: " + name, error);
        }
    }
}
