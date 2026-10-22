package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.event.MachineDropPreserver;
import com.example.mekanismmagic.integration.common.entity.EntityContainerRegistry;
import com.example.mekanismmagic.integration.common.entity.SimulationEntitySamples;
import com.example.mekanismmagic.item.RitualSpawnEggItem;
import com.hollingsworth.arsnouveau.common.items.data.MobJarData;
import com.hollingsworth.arsnouveau.common.lib.EntityTags;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.common.tile.interfaces.IRedstoneControl.RedstoneControl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Spawn eggs are retained specimens for Drygmy only, not globally consumable sacrifices. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DrygmySpawnEggGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static final BlockPos RESTORED = new BlockPos(4, 2, 2);

    private DrygmySpawnEggGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void vanillaAndRegisteredModEggsUsePersistentSlotPermissionsWithoutGlobalSacrificeSupport(GameTestHelper helper) {
        var tile = place(helper, POS);
        ItemStack egg = new ItemStack(Items.COW_SPAWN_EGG, 64);
        ItemStack before = egg.copy();
        var slot = tile.mekanismMagicPersistentInputs().getFirst();
        helper.assertTrue(slot.isItemValid(egg) && slot.insertItem(egg, Action.SIMULATE, AutomationType.EXTERNAL).isEmpty()
                        && slot.isEmpty() && ItemStack.matches(egg, before), "Egg preview changed the caller or failed valid external insertion");
        helper.assertTrue(slot.insertItem(egg, Action.EXECUTE, AutomationType.EXTERNAL).isEmpty()
                        && ItemStack.matches(slot.getStack(), before), "Drygmy failed to retain the complete inserted egg stack");
        helper.assertTrue(slot.extractItem(1, Action.EXECUTE, AutomationType.EXTERNAL).isEmpty(),
                "Automation extracted a retained spawn-egg sample");
        int unsided = tile.getInventorySlots(null).indexOf(slot);
        helper.assertTrue(unsided >= 0 && tile.extractItem(unsided, 1, null, Action.EXECUTE).isEmpty(),
                "Unsided access widened spawn-egg sample extraction");
        helper.assertTrue(slot.extractItem(1, Action.SIMULATE, AutomationType.MANUAL).is(Items.COW_SPAWN_EGG)
                        && tile.getSampleEntity(0).orElseThrow().entityId().equals(id("cow")), "Manual access or read-only sample getter failed");
        helper.assertTrue(EntityContainerRegistry.capturedEntity(egg).isEmpty() && EntityContainerRegistry.previewEmpty(egg).isEmpty(),
                "Spawn eggs were accidentally added to global sacrifice-container behavior");
        ItemStack modEgg = BuiltInRegistries.ITEM.stream()
                .filter(item -> item instanceof SpawnEggItem && !BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft"))
                .map(ItemStack::new).filter(SimulationEntitySamples::isSample).findFirst().orElseThrow();
        helper.assertTrue(tile.mekanismMagicPersistentInputs().get(1).isItemValid(modEgg), "A registered mod spawn egg was excluded by an item-ID whitelist");
        tile.mekanismMagicPersistentInputs().get(1).setStack(modEgg);
        EntityType<?> expected = ((SpawnEggItem) modEgg.getItem()).getType(modEgg);
        helper.assertTrue(tile.getSampleEntity(1).orElseThrow().entityId().equals(BuiltInRegistries.ENTITY_TYPE.getKey(expected)),
                "A mod egg's polymorphic native entity type was not honored");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void filledJarsAndEggStacksShareOneSpecimenPerSlotAndUniqueSpeciesCounting(GameTestHelper helper) {
        var tile = place(helper, POS);
        ItemStack cowEgg = new ItemStack(Items.COW_SPAWN_EGG, 64);
        ItemStack cowJar = jar("cow");
        ItemStack sheepEgg = new ItemStack(Items.SHEEP_SPAWN_EGG, 64);
        List<ItemStack> originals = List.of(cowEgg.copy(), cowJar.copy(), sheepEgg.copy());
        for (int slot = 0; slot < originals.size(); slot++) tile.mekanismMagicPersistentInputs().get(slot).setStack(originals.get(slot).copy());
        var legacy = EntityContainerRegistry.capturedEntity(cowJar).orElseThrow();
        var shared = tile.getSampleEntity(1).orElseThrow();
        helper.assertTrue(legacy.equals(shared), "Existing captured-jar data changed through the shared sample reader");
        CompoundTag copy = shared.entityData();
        copy.putString("id", "minecraft:pig");
        helper.assertTrue(tile.getSampleEntity(1).orElseThrow().equals(legacy), "Mutating a getter result changed the stored jar");
        tick(tile, 1);
        helper.assertTrue(tile.developmentCachedSampleCount() == 3 && tile.developmentCachedUniqueSampleTypes() == 2,
                "Stack quantities multiplied specimen count, or egg/jar versions of a cow counted as distinct species");
        helper.assertTrue(tile.developmentPendingOperationMultiplier() == 1 && !tile.developmentPendingOutputs().isEmpty(),
                "A sample stack implicitly enabled parallel operation upgrades or yielded no actual loot");
        helper.assertTrue(ItemStack.listMatches(originals, samples(tile).subList(0, originals.size())),
                "Reading mixed eggs and captured jars consumed or rewrote a sample");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void entityDataOverridesReachTheVirtualLootEntityWithoutWorldSpawningOrInputMutation(GameTestHelper helper) {
        var tile = place(helper, POS);
        CompoundTag data = new CompoundTag();
        data.putString("id", "minecraft:sheep");
        data.putByte("Color", (byte) 14);
        data.putInt("Age", -24_000);
        data.putString("CustomName", "\"Retained red lamb\"");
        ItemStack egg = new ItemStack(Items.COW_SPAWN_EGG, 32);
        egg.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        ItemStack before = egg.copy();
        Set<UUID> worldBefore = livingInArea(helper, tile);
        tile.mekanismMagicPersistentInputs().getFirst().setStack(egg);
        tick(tile, 1);
        helper.assertTrue(tile.getSampleEntity(0).orElseThrow().entityId().equals(id("sheep"))
                        && tile.developmentCachedSampleCount() == 1, "An explicit egg entity override fell back to its cow item type");
        CompoundTag virtual = tile.developmentCachedSampleData().getFirst();
        helper.assertTrue(virtual.getString("id").equals("minecraft:sheep") && virtual.getByte("Color") == 14
                        && virtual.getInt("Age") == -24_000 && virtual.getString("CustomName").contains("Retained red lamb"),
                "Virtual harvesting ignored the sample's variant, age or entity name");
        virtual.putInt("Age", 0);
        helper.assertTrue(tile.developmentCachedSampleData().getFirst().getInt("Age") == -24_000,
                "Diagnostic snapshots leaked a live mutable entity reference");
        helper.assertTrue(ItemStack.matches(before, tile.mekanismMagicPersistentInputs().getFirst().getStack())
                        && livingInArea(helper, tile).equals(worldBefore), "Virtual egg harvesting consumed a sample or spawned a live world entity");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void invalidEggOverridesAndPassengersAreRejectedAndTheOriginalDrygmyBlacklistStillApplies(GameTestHelper helper) {
        var tile = place(helper, POS);
        List<ItemStack> invalid = new ArrayList<>();
        for (String type : List.of("missing_mod:missing_creature", "not a resource id", "minecraft:player", "minecraft:item")) {
            CompoundTag data = new CompoundTag(); data.putString("id", type); invalid.add(egg(data));
        }
        CompoundTag malformedId = new CompoundTag(); malformedId.putInt("id", 1); invalid.add(egg(malformedId));
        CompoundTag passengers = new CompoundTag();
        passengers.putString("id", "minecraft:cow");
        ListTag nested = new ListTag(); CompoundTag pig = new CompoundTag(); pig.putString("id", "minecraft:pig"); nested.add(pig);
        passengers.put("Passengers", nested); invalid.add(egg(passengers));
        CompoundTag badPassengers = new CompoundTag(); badPassengers.putString("id", "minecraft:cow"); badPassengers.putString("Passengers", "bad");
        invalid.add(egg(badPassengers));
        ItemStack fakeEgg = new ItemStack(Items.DIRT); CompoundTag cow = new CompoundTag(); cow.putString("id", "minecraft:cow");
        fakeEgg.set(DataComponents.ENTITY_DATA, CustomData.of(cow)); invalid.add(fakeEgg);
        for (ItemStack sample : invalid) {
            ItemStack before = sample.copy();
            helper.assertTrue(!tile.mekanismMagicPersistentInputs().getFirst().isItemValid(sample)
                            && !SimulationEntitySamples.isSample(sample) && ItemStack.matches(before, sample),
                    "Invalid specimen was accepted or mutated: " + sample);
        }
        EntityType<?> blocked = BuiltInRegistries.ENTITY_TYPE.stream()
                .filter(type -> type.is(EntityTags.DRYGMY_BLACKLIST) && type != EntityType.PLAYER && DefaultAttributes.hasSupplier(type))
                .findFirst().orElseThrow();
        CompoundTag blockedData = new CompoundTag(); blockedData.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(blocked).toString());
        ItemStack blockedEgg = egg(blockedData);
        helper.assertTrue(SimulationEntitySamples.isSample(blockedEgg), "Blacklist fixture is not a valid entity specimen");
        tile.mekanismMagicPersistentInputs().getFirst().setStack(blockedEgg);
        tick(tile, 1);
        helper.assertTrue(tile.developmentCachedSampleCount() == 0 && tile.developmentPendingOutputs().isEmpty()
                        && tile.developmentPendingFluids().isEmpty(), "An egg bypassed Ars Nouveau's Drygmy entity blacklist");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void poweredHarvestPaysOnceAndKeepsTheEntireSpawnEggSample(GameTestHelper helper) {
        var tile = place(helper, POS);
        ItemStack egg = new ItemStack(Items.COW_SPAWN_EGG, 64);
        tile.mekanismMagicPersistentInputs().getFirst().setStack(egg.copy());
        tick(tile, 1);
        List<ItemStack> fixed = tile.developmentPendingOutputs();
        helper.assertTrue(!fixed.isEmpty(), "Cow egg did not prepare its actual native loot");
        int sourceCost = tile.developmentSourceCost();
        long rate = tile.developmentPendingEnergyPerTick();
        int required = tile.getProgressRequired();
        tile.setSource(sourceCost);
        tile.getNativeEnergyContainer().setEnergy(tile.getNativeEnergyContainer().getMaxEnergy());
        long energy = tile.getNativeEnergyContainer().getEnergy();
        tick(tile, required);
        tile.setControlType(RedstoneControl.HIGH);
        helper.assertTrue(tile.developmentPendingOutputs().isEmpty() && tile.getSource() == 0
                        && energy - tile.getNativeEnergyContainer().getEnergy() == rate * required,
                "Egg production changed the original per-operation Source/FE payment");
        helper.assertTrue(ItemStack.matches(egg, tile.mekanismMagicPersistentInputs().getFirst().getStack())
                        && itemAmounts(fixed).equals(itemAmounts(visible(tile))), "Harvest consumed the egg or changed its prepared fixed output");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void preparedEggHarvestSurvivesStatefulDropAndChangingEggInvalidatesItsSnapshot(GameTestHelper helper) {
        var tile = place(helper, POS);
        ItemStack sample = new ItemStack(Items.COW_SPAWN_EGG, 64);
        sample.set(DataComponents.CUSTOM_NAME, Component.literal("Persistent cow specimen"));
        tile.mekanismMagicPersistentInputs().getFirst().setStack(sample);
        tick(tile, 1);
        List<ItemStack> fixed = tile.developmentPendingOutputs();
        List<FluidStack> fluids = tile.developmentPendingFluids();
        String signature = tile.developmentPendingSignature();
        int cost = tile.developmentSourceCost();
        long rate = tile.developmentPendingEnergyPerTick();
        int required = tile.getProgressRequired();
        helper.assertTrue(!fixed.isEmpty(), "Egg persistence fixture did not prepare loot");
        CompoundTag state = tile.saveWithoutMetadata(helper.getLevel().registryAccess());
        state.putInt("magic_progress", required - 1);
        tile.loadAdditional(state, helper.getLevel().registryAccess());
        tile.setControlType(RedstoneControl.HIGH);
        tick(tile, 1);
        List<ItemEntity> drops = new ArrayList<>();
        drops.add(new ItemEntity(helper.getLevel(), tile.getBlockPos().getX(), tile.getBlockPos().getY(), tile.getBlockPos().getZ(),
                new ItemStack(ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get(), 2)));
        MachineDropPreserver.onBlockDrops(new BlockDropsEvent(helper.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile,
                drops, null, ItemStack.EMPTY));
        List<ItemStack> stateful = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.has(DataComponents.BLOCK_ENTITY_DATA)).toList();
        helper.assertTrue(stateful.size() == 1 && stateful.getFirst().getCount() == 1, "Egg sample/harvest was duplicated into multiple drops");
        helper.setBlock(POS, Blocks.AIR);
        var restored = place(helper, RESTORED);
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null, restored.getBlockPos(), stateful.getFirst()),
                "Stateful egg-sample machine could not be restored");
        restored.applyComponentsFromItemStack(stateful.getFirst());
        tick(restored, 2);
        helper.assertTrue(ItemStack.matches(sample, restored.mekanismMagicPersistentInputs().getFirst().getStack())
                        && ItemStack.listMatches(fixed, restored.developmentPendingOutputs())
                        && sameFluids(fluids, restored.developmentPendingFluids())
                        && signature.equals(restored.developmentPendingSignature()) && restored.getProgress() == required - 1
                        && restored.developmentSourceCost() == cost && restored.developmentPendingEnergyPerTick() == rate,
                "Reload reinterpreted the egg, rerolled its harvest, or lost saved payment/progress context");
        restored.setControlType(RedstoneControl.DISABLED);
        restored.setSource(cost);
        restored.getNativeEnergyContainer().setEnergy(restored.getNativeEnergyContainer().getMaxEnergy());
        tick(restored, 1);
        helper.assertTrue(restored.getSource() == 0 && itemAmounts(fixed).equals(itemAmounts(visible(restored))),
                "Restored egg harvest did not deliver its one saved result");
        restored.mekanismMagicPersistentInputs().getFirst().setStack(new ItemStack(Items.SHEEP_SPAWN_EGG, 64));
        helper.assertTrue(restored.developmentPendingOutputs().isEmpty() && restored.developmentCachedSampleCount() == 0,
                "Replacing a sample retained the old loot entity/cache");
        tick(restored, 1);
        String sheepSignature = restored.developmentPendingSignature();
        helper.assertTrue(!sheepSignature.equals(signature) && restored.getSampleEntity(0).orElseThrow().entityId().equals(id("sheep"))
                        && restored.developmentCachedSampleData().getFirst().getString("id").equals("minecraft:sheep"),
                "Replacing the egg did not invalidate its entity-specific fingerprint");
        ItemStack changed = new ItemStack(Items.SHEEP_SPAWN_EGG, 64);
        CompoundTag red = new CompoundTag(); red.putString("id", "minecraft:sheep"); red.putByte("Color", (byte) 14);
        changed.set(DataComponents.ENTITY_DATA, CustomData.of(red));
        restored.mekanismMagicPersistentInputs().getFirst().setStack(changed);
        tick(restored, 1);
        helper.assertTrue(!sheepSignature.equals(restored.developmentPendingSignature())
                        && restored.developmentCachedSampleData().getFirst().getByte("Color") == 14,
                "Same egg item with changed entity components reused stale loot context");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void fixedRitualEggsAreReadableButRandomTagAndChickenFallbackEggsAreNotRerolled(GameTestHelper helper) {
        var tile = place(helper, POS);
        ItemStack fixed = RitualSpawnEggItem.forEntity(EntityType.COW, new CompoundTag());
        fixed.setCount(16);
        helper.assertTrue(tile.mekanismMagicPersistentInputs().getFirst().isItemValid(fixed), "Fixed ritual spawn egg was rejected");
        tile.mekanismMagicPersistentInputs().getFirst().setStack(fixed.copy());
        tick(tile, 1);
        helper.assertTrue(tile.getSampleEntity(0).orElseThrow().entityId().equals(id("cow"))
                        && ItemStack.matches(fixed, tile.mekanismMagicPersistentInputs().getFirst().getStack()),
                "Fixed ritual egg was consumed or changed into a random fallback");
        ItemStack pool = RitualSpawnEggItem.forTag(TagKey.create(Registries.ENTITY_TYPE, id("skeletons")), new CompoundTag());
        ItemStack chance = RitualSpawnEggItem.forOwnedEntityWithChickenFallback(EntityType.COW, new CompoundTag(), 2);
        helper.assertTrue(!tile.mekanismMagicPersistentInputs().getFirst().isItemValid(pool)
                        && !tile.mekanismMagicPersistentInputs().getFirst().isItemValid(chance),
                "An unresolved random pool/fallback egg became an endlessly rerollable sample");
        helper.assertTrue(EntityContainerRegistry.capturedEntity(fixed).isEmpty() && EntityContainerRegistry.previewEmpty(fixed).isEmpty(),
                "Ritual eggs acquired global consumable-container semantics");
        helper.succeed();
    }

    private static DrygmySimulatorBlockEntity place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
        DrygmySimulatorBlockEntity tile = helper.getBlockEntity(pos);
        tile.setControlType(RedstoneControl.DISABLED);
        tile.setSource(0);
        tile.getNativeEnergyContainer().setEnergy(0);
        return tile;
    }

    private static ItemStack jar(String entity) {
        CompoundTag data = new CompoundTag(); data.putString("id", id(entity).toString());
        ItemStack result = new ItemStack(BlockRegistry.MOB_JAR.get());
        result.set(DataComponentRegistry.MOB_JAR.get(), new MobJarData(data, null));
        return result;
    }

    private static ItemStack egg(CompoundTag data) {
        ItemStack egg = new ItemStack(Items.COW_SPAWN_EGG);
        egg.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        return egg;
    }

    private static List<ItemStack> samples(DrygmySimulatorBlockEntity tile) {
        return tile.mekanismMagicPersistentInputs().stream().map(slot -> slot.getStack().copy()).toList();
    }

    private static List<ItemStack> visible(DrygmySimulatorBlockEntity tile) {
        return tile.mekanismMagicPatternOutputs().stream().map(slot -> slot.getStack().copy()).toList();
    }

    private static Map<String, Long> itemAmounts(List<ItemStack> stacks) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (ItemStack stack : stacks) if (!stack.isEmpty()) {
            result.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + stack.getComponentsPatch(), (long) stack.getCount(), Long::sum);
        }
        return result;
    }

    private static boolean sameFluids(List<FluidStack> expected, List<FluidStack> actual) {
        if (expected.size() != actual.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            if (expected.get(index).getAmount() != actual.get(index).getAmount()
                    || !FluidStack.isSameFluidSameComponents(expected.get(index), actual.get(index))) return false;
        }
        return true;
    }

    private static Set<UUID> livingInArea(GameTestHelper helper, DrygmySimulatorBlockEntity tile) {
        return helper.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(tile.getBlockPos()).inflate(3))
                .stream().map(LivingEntity::getUUID).collect(Collectors.toSet());
    }

    private static ResourceLocation id(String path) { return ResourceLocation.withDefaultNamespace(path); }
    private static void tick(DrygmySimulatorBlockEntity tile, int count) { for (int tick = 0; tick < count; tick++) tile.onUpdateServer(); }
}
