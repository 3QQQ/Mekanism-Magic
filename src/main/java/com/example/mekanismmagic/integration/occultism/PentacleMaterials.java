package com.example.mekanismmagic.integration.occultism;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Material rules are derived from native matchers, never a live book preview. */
final class PentacleMaterials {
    private PentacleMaterials() {
    }

    record Material(List<ItemStack> choices, int count) {
        Material {
            choices = choices.stream().map(stack -> stack.copyWithCount(1)).toList();
            if (count < 1 || choices.isEmpty()) {
                throw new IllegalArgumentException("Empty pentacle material");
            }
        }

        ItemStack representative() {
            return choices.getFirst().copyWithCount(count);
        }

        List<ItemStack> displayedChoices() {
            return choices.stream().map(stack -> stack.copyWithCount(count)).toList();
        }

        boolean matches(ItemStack stack) {
            // Do not destroy contents/custom state carried by a block item.
            return choices.stream().anyMatch(choice ->
                    ItemStack.isSameItemSameComponents(choice, stack));
        }
    }

    record Scan(List<Material> materials, Set<String> chalkColors) {
    }

    private record Cell(BlockPos position, Object matcher) {
    }

    static Scan scan(Object multiblock) throws ReflectiveOperationException {
        Object simulated = MiniPentacleDeployment.invoke(multiblock, "simulate",
                null, BlockPos.ZERO, Rotation.NONE, false, true);
        if (!(simulated instanceof Pair<?, ?> pair)
                || !(pair.getSecond() instanceof Collection<?> results) || results.isEmpty()) {
            throw new IllegalArgumentException("Multiblock has no native matchers");
        }
        List<Cell> cells = new ArrayList<>();
        for (Object result : results) {
            cells.add(new Cell((BlockPos) MiniPentacleDeployment.invoke(result, "getWorldPosition"),
                    MiniPentacleDeployment.invoke(result, "getStateMatcher")));
        }
        cells.sort(Comparator.comparingInt((Cell cell) -> cell.position().getY())
                .thenComparingInt(cell -> cell.position().getZ())
                .thenComparingInt(cell -> cell.position().getX()));
        Map<Object, List<ItemStack>> choicesByMatcher = new IdentityHashMap<>();
        Map<List<Item>, Integer> counts = new LinkedHashMap<>();
        Map<List<Item>, List<ItemStack>> examples = new LinkedHashMap<>();
        Set<String> colors = new LinkedHashSet<>();
        // Invoke the public interface rather than a package-private lambda class.
        Method test = Class.forName("com.klikli_dev.modonomicon.api.multiblock.TriPredicate")
                .getMethod("test", Object.class, Object.class, Object.class);
        for (Cell cell : cells) {
            Object matcher = cell.matcher();
            if (!Boolean.TRUE.equals(MiniPentacleDeployment.invoke(matcher, "countsTowardsTotalBlocks"))) {
                continue; // Air and display-only foundations are not consumed.
            }
            BlockState state = (BlockState) MiniPentacleDeployment.invoke(matcher, "getDisplayedState", 0L);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if ("occultism".equals(id.getNamespace()) && id.getPath().startsWith("chalk_glyph_")) {
                colors.add(id.getPath().substring("chalk_glyph_".length()));
                continue;
            }
            if (state.isAir()) {
                continue;
            }
            if (state.getBlock().asItem() == Items.AIR) {
                throw new IllegalArgumentException("Required block has no material item: " + id);
            }
            List<ItemStack> choices = choicesByMatcher.get(matcher);
            if (choices == null) {
                choices = choices(matcher, state, test);
                choicesByMatcher.put(matcher, choices);
            }
            List<Item> key = choices.stream().map(ItemStack::getItem).toList();
            counts.merge(key, 1, Integer::sum);
            examples.putIfAbsent(key, choices);
        }
        List<Material> materials = new ArrayList<>();
        for (var entry : counts.entrySet()) {
            List<ItemStack> choices = examples.get(entry.getKey());
            int maximum = choices.stream().mapToInt(ItemStack::getMaxStackSize).min().orElse(1);
            for (int remaining = entry.getValue(); remaining > 0; remaining -= maximum) {
                materials.add(new Material(choices, Math.min(remaining, maximum)));
            }
        }
        return new Scan(List.copyOf(materials), java.util.Collections.unmodifiableSet(colors));
    }

    private static List<ItemStack> choices(Object matcher, BlockState canonical, Method test)
            throws ReflectiveOperationException {
        ResourceLocation type = (ResourceLocation) MiniPentacleDeployment.invoke(matcher, "getType");
        Object predicate = MiniPentacleDeployment.invoke(matcher, "getStatePredicate");
        List<Item> candidates = "modonomicon".equals(type.getNamespace()) && "tag".equals(type.getPath())
                ? BuiltInRegistries.ITEM.stream().filter(item -> item instanceof BlockItem)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList()
                : List.of(canonical.getBlock().asItem());
        List<ItemStack> accepted = new ArrayList<>();
        for (Item item : candidates) {
            if (!(item instanceof BlockItem block)) {
                continue;
            }
            // Required block properties (lit, facing, etc.) describe placement,
            // not a different consumed item. Respect the full native predicate.
            for (BlockState state : block.getBlock().getStateDefinition().getPossibleStates()) {
                if (Boolean.TRUE.equals(test.invoke(predicate, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, state))) {
                    accepted.add(item.getDefaultInstance());
                    break;
                }
            }
        }
        if (accepted.isEmpty()) {
            throw new IllegalArgumentException("No placeable material satisfies " + type);
        }
        // Keep the explicit native example first, but never ask for current
        // preview time or modify the multiblock's shared level binding.
        Item preferred = canonical.getBlock().asItem();
        accepted.sort(Comparator.comparingInt(stack -> stack.is(preferred) ? 0 : 1));
        return List.copyOf(accepted);
    }
}
