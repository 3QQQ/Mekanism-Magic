package com.example.mekanismmagic.integration.common.entity;

import com.example.mekanismmagic.MekanismMagic;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SimulationEntitySamplesGameTests {
    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void registeredVanillaAndModEggsUseTheirActualPolymorphicEntityType(GameTestHelper helper) {
        int tested = 0, modded = 0, deferred = 0;
        for (var item : BuiltInRegistries.ITEM) {
            if (!(item instanceof SpawnEggItem egg)) continue;
            ItemStack stack = item.getDefaultInstance();
            EntityType<?> type = egg.getType(stack);
            if (type == null || type == EntityType.PLAYER || !DefaultAttributes.hasSupplier(type)) continue;
            var sample = SimulationEntitySamples.read(stack).orElseThrow();
            helper.assertTrue(sample.entityId().equals(BuiltInRegistries.ENTITY_TYPE.getKey(type))
                    && sample.entityData().getString("id").equals(sample.entityId().toString()), "Egg default/supplier type was guessed from item ID");
            if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) modded++;
            if (item instanceof DeferredSpawnEggItem) deferred++;
            tested++;
        }
        helper.assertTrue(tested > 10 && modded > 0 && deferred > 0, "Fixture did not cover real vanilla, modded and DeferredSpawnEgg items");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void validEntityDataOverrideAndVariantsAreCopiedWithoutMutatingTheEgg(GameTestHelper helper) {
        ItemStack egg = new ItemStack(Items.COW_SPAWN_EGG, 64);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:sheep");
        tag.putByte("Color", (byte) 11);
        tag.putString("CustomName", "\"Blue sample\"");
        egg.set(DataComponents.ENTITY_DATA, CustomData.of(tag));
        ItemStack before = egg.copy();
        var read = SimulationEntitySamples.read(egg).orElseThrow();
        helper.assertTrue(read.entityId().equals(ResourceLocation.withDefaultNamespace("sheep"))
                        && read.entityData().getByte("Color") == 11, "Valid spawn-egg variant/override data was discarded");
        read.entityData().putByte("Color", (byte) 0);
        helper.assertTrue(ItemStack.matches(before, egg) && read.entityData().getByte("Color") == 11,
                "Reading/altering the sample changed its retained egg or cached data");
        helper.assertTrue(SimulationEntitySamples.read(egg.copyWithCount(1)).orElseThrow().equals(read),
                "Stack size altered the sample entity data");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void invalidOverridesDoNotFallBackToTheDefaultMobOrEnableSacrifices(GameTestHelper helper) {
        for (String id : new String[]{"", "Bad Entity!", "minecraft:not_an_entity", "minecraft:player", "minecraft:item"}) {
            ItemStack egg = new ItemStack(Items.COW_SPAWN_EGG);
            CompoundTag data = new CompoundTag();
            data.putString("id", id);
            egg.set(DataComponents.ENTITY_DATA, CustomData.of(data));
            helper.assertTrue(SimulationEntitySamples.read(egg).isEmpty(), "Bad explicit ID silently fell back to cow: " + id);
        }
        ItemStack egg = new ItemStack(Items.COW_SPAWN_EGG);
        CompoundTag data = new CompoundTag();
        data.putInt("id", 7);
        egg.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        helper.assertTrue(SimulationEntitySamples.read(egg).isEmpty(), "Non-string entity ID was accepted");
        egg = new ItemStack(Items.COW_SPAWN_EGG);
        helper.assertTrue(SimulationEntitySamples.isSample(egg) && EntityContainerRegistry.capturedEntity(egg).isEmpty()
                        && EntityContainerRegistry.previewEmpty(egg).isEmpty(),
                "Simulator support expanded the global sacrifice/release-container contract");
        ItemStack stick = new ItemStack(Items.STICK);
        data.putString("id", "minecraft:cow");
        stick.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        helper.assertTrue(SimulationEntitySamples.read(stick).isEmpty(), "ENTITY_DATA alone made an unrelated item a spawn egg");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void passengerPayloadsAreRejectedButOrdinaryDefaultsRemainReadable(GameTestHelper helper) {
        ItemStack egg = new ItemStack(Items.COW_SPAWN_EGG);
        CompoundTag data = new CompoundTag();
        data.putString("id", "minecraft:cow");
        ListTag passengers = new ListTag();
        CompoundTag passenger = new CompoundTag();
        passenger.putString("id", "minecraft:zombie");
        passengers.add(passenger);
        data.put("Passengers", passengers);
        egg.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        helper.assertTrue(SimulationEntitySamples.read(egg).isEmpty(), "An egg bundled multiple entity specimens");
        data.putString("Passengers", "bad list");
        egg.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        helper.assertTrue(SimulationEntitySamples.read(egg).isEmpty(), "Malformed passenger payload was accepted");
        data.remove("Passengers");
        data.remove("id");
        data.putFloat("Health", 1);
        egg.set(DataComponents.ENTITY_DATA, CustomData.of(data));
        helper.assertTrue(SimulationEntitySamples.read(egg).orElseThrow().entityId()
                .equals(ResourceLocation.withDefaultNamespace("cow")), "Omitted ID no longer uses the native default entity type");
        helper.succeed();
    }
}
