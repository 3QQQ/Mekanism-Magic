package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.blocks.ModBlocks;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Explicit item equivalents of Nature's Aura 41.9's native generators. */
public final class NatureAuraFuelCatalog {
    public enum Kind { PROJECTILE, FLOWER, MOSS, FIREWORK, POTION, ANCIENT_LEAVES, END_FLOWER }

    /** A fresh fuel's quota, before a machine applies its flower history. */
    public record Fuel(ResourceLocation id, Kind kind, ItemStack input,
                       ItemStack secondary, ItemStack remainder, long aura,
                       boolean overworldOnly) {
    }

    private static final Map<Item, EntityType<?>> PROJECTILES = Map.ofEntries(
            Map.entry(Items.EGG, EntityType.EGG),
            Map.entry(Items.SNOWBALL, EntityType.SNOWBALL),
            Map.entry(Items.FIRE_CHARGE, EntityType.SMALL_FIREBALL),
            Map.entry(Items.ENDER_PEARL, EntityType.ENDER_PEARL),
            Map.entry(Items.EXPERIENCE_BOTTLE, EntityType.EXPERIENCE_BOTTLE),
            Map.entry(Items.ARROW, EntityType.ARROW),
            Map.entry(Items.TIPPED_ARROW, EntityType.ARROW),
            Map.entry(Items.SPECTRAL_ARROW, EntityType.SPECTRAL_ARROW),
            Map.entry(Items.TRIDENT, EntityType.TRIDENT));
    private static final float[] FIREWORK_SHAPES = {0, 1, 0.5F, 20, 0.5F};

    private NatureAuraFuelCatalog() {
    }

    public static boolean accepts(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(Items.ENDER_EYE) || lookup(stack).isPresent());
    }

    public static Optional<Fuel> lookup(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        // These native blocks contain a finite reservoir. Never recreate a
        // partly drained block's initial quota from serialized block data.
        if (stack.is(ModBlocks.END_FLOWER.asItem())) {
            return stack.has(DataComponents.BLOCK_ENTITY_DATA) ? Optional.empty()
                    : fuel(stack, Kind.END_FLOWER, 500_000, false,
                    new ItemStack(Items.ENDER_EYE), new ItemStack(Items.DEAD_BUSH));
        }
        if (stack.is(ModBlocks.ANCIENT_LEAVES.asItem())) {
            return stack.has(DataComponents.BLOCK_ENTITY_DATA) ? Optional.empty()
                    : fuel(stack, Kind.ANCIENT_LEAVES, 2_000, true,
                    ItemStack.EMPTY, new ItemStack(ModBlocks.DECAYED_LEAVES));
        }
        EntityType<?> projectile = PROJECTILES.get(stack.getItem());
        if (projectile != null) {
            return fuel(stack, Kind.PROJECTILE,
                    NaturesAuraAPI.PROJECTILE_GENERATIONS.getOrDefault(projectile, 0),
                    false, ItemStack.EMPTY, ItemStack.EMPTY);
        }
        if (stack.is(Items.FIREWORK_ROCKET)) {
            return fuel(stack, Kind.FIREWORK, fireworkAura(stack), false,
                    ItemStack.EMPTY, ItemStack.EMPTY);
        }
        if (stack.is(Items.LINGERING_POTION)) {
            return fuel(stack, Kind.POTION, potionAura(stack), false,
                    ItemStack.EMPTY, ItemStack.EMPTY);
        }
        if (stack.getItem() instanceof BlockItem blockItem) {
            var state = blockItem.getBlock().defaultBlockState();
            var clean = NaturesAuraAPI.BOTANIST_PICKAXE_CONVERSIONS.inverse().get(state);
            if (clean != null && clean.getBlock().asItem() != Items.AIR) {
                return fuel(stack, Kind.MOSS, 5_000, false,
                        ItemStack.EMPTY, new ItemStack(clean.getBlock()));
            }
            if (state.is(BlockTags.SMALL_FLOWERS)) {
                return fuel(stack, Kind.FLOWER, 25_000, true,
                        ItemStack.EMPTY, ItemStack.EMPTY);
            }
        }
        return Optional.empty();
    }

    private static Optional<Fuel> fuel(ItemStack stack, Kind kind, long aura,
                                        boolean overworldOnly, ItemStack secondary,
                                        ItemStack remainder) {
        return aura <= 0 ? Optional.empty() : Optional.of(new Fuel(
                BuiltInRegistries.ITEM.getKey(stack.getItem()), kind,
                stack.copyWithCount(1), secondary, remainder, aura, overworldOnly));
    }

    public static long fireworkAura(ItemStack stack) {
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        if (!stack.is(Items.FIREWORK_ROCKET) || fireworks == null
                || fireworks.explosions().isEmpty()) {
            return 0;
        }
        // Follow BlockEntityFireworkGenerator's float arithmetic, including
        // per-star unique main colors; fade colors do not contribute.
        float score = fireworks.flightDuration();
        for (FireworkExplosion star : fireworks.explosions()) {
            score += 1.5F;
            if (star.hasTwinkle()) score += 1;
            if (star.hasTrail()) score += 8;
            score += FIREWORK_SHAPES[star.shape().getId()];
            score += 0.75F * new HashSet<>(star.colors()).size();
        }
        return Math.max(0, Math.min(Integer.MAX_VALUE,
                (long) Math.ceil(score * 10_000F)));
    }

    public static long potionAura(ItemStack stack) {
        if (!stack.is(Items.LINGERING_POTION)) return 0;
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return 0;
        for (var effect : contents.getAllEffects()) {
            var type = effect.getEffect().value();
            if (!type.isBeneficial() || type.isInstantenous()) continue;
            if (effect.getDuration() <= 0 || effect.getAmplifier() < 0) return 0;
            // A fresh isolated vanilla lingering cloud survives nine native
            // absorption pulses: radius 3, -0.005/tick and -0.25/pulse.
            // The first eligible effect alone is used by the native ritual.
            double pulse = (7D * effect.getAmplifier() + 1)
                    * (effect.getDuration() / 25) * 100;
            // Native storage takes an int; malformed/custom extreme effects
            // must not overflow into an invented unbounded fuel reservoir.
            return pulse > Integer.MAX_VALUE ? 0 : (long) pulse * 9;
        }
        return 0;
    }

    /** Representative recipes for JEI; runtime matching always reads the live APIs. */
    public static List<Fuel> displayFuels(HolderLookup.Provider registries) {
        List<Fuel> fuels = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            lookup(new ItemStack(item)).ifPresent(fuels::add);
        }
        registries.lookupOrThrow(Registries.POTION).listElements().forEach(potion ->
                lookup(PotionContents.createItemStack(Items.LINGERING_POTION, potion))
                        .ifPresent(fuels::add));
        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);
        firework.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(
                new FireworkExplosion(FireworkExplosion.Shape.SMALL_BALL,
                        IntArrayList.of(0xFF0000), IntArrayList.of(), false, false))));
        lookup(firework).ifPresent(fuels::add);
        return List.copyOf(fuels);
    }
}
