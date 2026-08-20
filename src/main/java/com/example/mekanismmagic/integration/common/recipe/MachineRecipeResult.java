package com.example.mekanismmagic.integration.common.recipe;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mod-independent result consumed by the shared Mekanism machine loop.
 */
public record MachineRecipeResult(
        ResourceLocation id,
        ItemStack output,
        int duration,
        List<InputUse> inputs,
        int activationSlot,
        int specialInputSlot,
        RecipeCompletion completion,
        SpecialInputHandler specialInputHandler,
        Map<ResourceLocation, Integer> resourceCosts) {

    public MachineRecipeResult {
        Objects.requireNonNull(id, "id");
        output = output == null ? ItemStack.EMPTY : output.copy();
        duration = Math.max(1, duration);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        completion = completion == null ? RecipeCompletion.NONE : completion;
        specialInputHandler = specialInputHandler == null
                ? SpecialInputHandler.NONE : specialInputHandler;
        resourceCosts = resourceCosts == null ? Map.of()
                : Map.copyOf(resourceCosts);
    }

    public MachineRecipeResult(ResourceLocation id, ItemStack output, int duration,
                               List<InputUse> inputs, int activationSlot) {
        this(id, output, duration, inputs, activationSlot, -1,
                RecipeCompletion.NONE, SpecialInputHandler.NONE, Map.of());
    }

    public MachineRecipeResult(ResourceLocation id, ItemStack output, int duration,
                               List<InputUse> inputs, int activationSlot,
                               int specialInputSlot,
                               RecipeCompletion completion,
                               SpecialInputHandler specialInputHandler) {
        this(id, output, duration, inputs, activationSlot, specialInputSlot,
                completion, specialInputHandler, Map.of());
    }

    public int resourceCost(ResourceLocation resource) {
        return Math.max(0, resourceCosts.getOrDefault(resource, 0));
    }

    public boolean complete(ServerLevel level, BlockPos position) {
        return completion.complete(level, position);
    }

    public boolean matchesSpecialInput(ItemStack stack) {
        return specialInputSlot < 0 || specialInputHandler.matches(stack);
    }

    public boolean consumeSpecialInput(ItemStackHandler inventory) {
        return specialInputSlot < 0
                || specialInputHandler.consume(inventory, specialInputSlot);
    }
}
