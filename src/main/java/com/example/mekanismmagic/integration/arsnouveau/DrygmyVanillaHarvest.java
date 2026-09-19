package com.example.mekanismmagic.integration.arsnouveau;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.InstrumentTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Instrument;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

import java.util.List;
import java.util.Optional;

/**
 * Replays explicitly selected harvest behaviors without changing the captured
 * animals, consuming their equipment, ticking AI, or spawning world entities.
 * Rule selection, NBT predicates, output filtering and batch scaling belong to
 * the caller. In particular, a generic Color field is read only after a data
 * pack has explicitly selected a colored harvest kind for that entity type.
 */
final class DrygmyVanillaHarvest {
    private DrygmyVanillaHarvest() {
    }

    /** Stable output variant, or empty when this behavior cannot be simulated. */
    static Optional<String> variantKey(
            String kind, ServerLevel level, LivingEntity entity,
            List<LivingEntity> group) {
        if (kind == null || entity == null || !entity.isAlive()) {
            return Optional.empty();
        }
        return switch (kind) {
            case "goat_horn" -> horn(entity).flatMap(stack -> {
                Holder<Instrument> instrument = stack.get(
                        DataComponents.INSTRUMENT);
                return instrument == null ? Optional.empty()
                        : instrument.unwrapKey().map(key ->
                                key.location().toString());
            });
            case "mooshroom_stew" -> {
                if (!(entity instanceof MushroomCow cow) || cow.isBaby()) {
                    yield Optional.empty();
                }
                yield Optional.of(stewEffects(cow)
                        .map(effects -> "suspicious:" + effects.effects())
                        .orElse("mushroom_stew"));
            }
            case "frog_lights" -> entity instanceof Frog frog
                    && smallMagmaCube(group).isPresent()
                    ? frog.getVariant().unwrapKey().map(key ->
                            key.location().toString())
                    : Optional.empty();
            case "turtle_egg" -> entity instanceof Turtle
                    && hasAdultPartner(entity, group)
                    ? Optional.of("turtle_egg") : Optional.empty();
            case "sniffer_egg" -> entity instanceof Sniffer
                    && hasAdultPartner(entity, group)
                    ? Optional.of("sniffer_egg") : Optional.empty();
            case "colored_wool", "colored_dye" -> color(entity)
                    .map(value -> "color:" + value.getId());
            case "cat_gift" -> entity instanceof Cat cat
                    && cat.isTame() && !cat.isOrderedToSit()
                    ? Optional.of("morning_gift") : Optional.empty();
            case "villager_gift" -> entity instanceof Villager villager
                    ? Optional.of(villager.isBaby() ? "baby_poppy"
                            : BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                                    villager.getVillagerData().getProfession())
                                    .toString())
                    : Optional.empty();
            default -> Optional.empty();
        };
    }

    /** Returns one unscaled harvest. Every returned stack is independently owned. */
    static List<ItemStack> collect(
            String kind, ServerLevel level, LivingEntity entity,
            List<LivingEntity> group) {
        if (variantKey(kind, level, entity, group).isEmpty()) {
            return List.of();
        }
        return switch (kind) {
            case "goat_horn" -> horn(entity).map(List::of)
                    .orElseGet(List::of);
            case "mooshroom_stew" -> List.of(stew((MushroomCow) entity));
            case "frog_lights" -> frogLights(level, (Frog) entity, group);
            case "turtle_egg" -> List.of(new ItemStack(Items.TURTLE_EGG,
                    1 + level.random.nextInt(4)));
            case "sniffer_egg" -> List.of(new ItemStack(Items.SNIFFER_EGG));
            case "colored_wool" -> color(entity).flatMap(value ->
                            BuiltInRegistries.ITEM.getOptional(
                                    ResourceLocation.withDefaultNamespace(
                                            value.getName() + "_wool")))
                    .map(item -> List.of(new ItemStack(item,
                            1 + level.random.nextInt(3))))
                    .orElseGet(List::of);
            case "colored_dye" -> color(entity)
                    .map(value -> List.of(new ItemStack(DyeItem.byColor(value))))
                    .orElseGet(List::of);
            case "cat_gift" -> gift(level, entity,
                    BuiltInLootTables.CAT_MORNING_GIFT);
            case "villager_gift" -> villagerGift(level, (Villager) entity);
            default -> List.of();
        };
    }

    private static Optional<ItemStack> horn(LivingEntity entity) {
        if (!(entity instanceof Goat goat) || goat.isBaby()
                || !(goat.hasLeftHorn() || goat.hasRightHorn())) {
            return Optional.empty();
        }
        var instruments = goat.isScreamingGoat()
                ? InstrumentTags.SCREAMING_GOAT_HORNS
                : InstrumentTags.REGULAR_GOAT_HORNS;
        if (!BuiltInRegistries.INSTRUMENT.getTagOrEmpty(instruments)
                .iterator().hasNext()) {
            return Optional.empty();
        }
        // createHorn uses a separate UUID-seeded random source, leaving both
        // the entity's random stream and its actual horns untouched.
        return Optional.of(goat.createHorn());
    }

    private static ItemStack stew(MushroomCow cow) {
        Optional<SuspiciousStewEffects> effects = stewEffects(cow);
        if (effects.isEmpty()) {
            return new ItemStack(Items.MUSHROOM_STEW);
        }
        ItemStack stack = new ItemStack(Items.SUSPICIOUS_STEW);
        stack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, effects.get());
        return stack;
    }

    private static Optional<SuspiciousStewEffects> stewEffects(MushroomCow cow) {
        // Lightning changes the cow's mushroom color without clearing prepared
        // effects. Native milking checks the stored effects, not current color.
        CompoundTag snapshot = cow.saveWithoutId(new CompoundTag());
        if (!snapshot.contains("stew_effects", Tag.TAG_LIST)) {
            return Optional.empty();
        }
        return SuspiciousStewEffects.CODEC.parse(NbtOps.INSTANCE,
                        snapshot.getList("stew_effects", Tag.TAG_COMPOUND).copy())
                .result().map(effects -> new SuspiciousStewEffects(
                        List.copyOf(effects.effects())));
    }

    private static Optional<DyeColor> color(LivingEntity entity) {
        if (entity instanceof Sheep sheep) {
            return sheep.isBaby() || sheep.isSheared() ? Optional.empty()
                    : Optional.of(sheep.getColor());
        }
        CompoundTag snapshot = entity.saveWithoutId(new CompoundTag());
        if (!snapshot.contains("Color", Tag.TAG_BYTE)) {
            return Optional.empty();
        }
        int value = snapshot.getByte("Color");
        return value >= 0 && value < DyeColor.values().length
                ? Optional.of(DyeColor.byId(value)) : Optional.empty();
    }

    private static boolean hasAdultPartner(
            LivingEntity entity, List<LivingEntity> group) {
        if (entity.isBaby()) {
            return false;
        }
        return group.stream().anyMatch(candidate -> candidate != null
                && candidate.isAlive() && !candidate.isBaby()
                && candidate.getType() == entity.getType()
                && !candidate.getUUID().equals(entity.getUUID()));
    }

    private static Optional<MagmaCube> smallMagmaCube(List<LivingEntity> group) {
        return group.stream().filter(MagmaCube.class::isInstance)
                .map(MagmaCube.class::cast)
                .filter(cube -> cube.isAlive() && cube.getSize() == 1)
                .findFirst();
    }

    private static List<ItemStack> frogLights(
            ServerLevel level, Frog frog, List<LivingEntity> group) {
        Optional<MagmaCube> prey = smallMagmaCube(group);
        if (prey.isEmpty()) {
            return List.of();
        }
        MagmaCube cube = prey.get();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, cube)
                .withParameter(LootContextParams.ORIGIN, cube.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE,
                        level.damageSources().mobAttack(frog))
                .withParameter(LootContextParams.ATTACKING_ENTITY, frog)
                .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, frog)
                .create(LootContextParamSets.ENTITY);
        return level.getServer().reloadableRegistries()
                .getLootTable(EntityType.MAGMA_CUBE.getDefaultLootTable())
                .getRandomItems(params).stream()
                .filter(stack -> stack.is(Items.OCHRE_FROGLIGHT)
                        || stack.is(Items.VERDANT_FROGLIGHT)
                        || stack.is(Items.PEARLESCENT_FROGLIGHT))
                .map(ItemStack::copy).toList();
    }

    private static List<ItemStack> gift(
            ServerLevel level, LivingEntity entity,
            ResourceKey<LootTable> table) {
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, entity.position())
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .create(LootContextParamSets.GIFT);
        return level.getServer().reloadableRegistries().getLootTable(table)
                .getRandomItems(params).stream().map(ItemStack::copy).toList();
    }

    private static List<ItemStack> villagerGift(
            ServerLevel level, Villager villager) {
        if (villager.isBaby()) {
            return List.of(new ItemStack(Items.POPPY));
        }
        var mapping = BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(
                        villager.getVillagerData().getProfession())
                .getData(NeoForgeDataMaps.RAID_HERO_GIFTS);
        return mapping == null ? List.of(new ItemStack(Items.WHEAT_SEEDS))
                : gift(level, villager, mapping.lootTable());
    }
}
