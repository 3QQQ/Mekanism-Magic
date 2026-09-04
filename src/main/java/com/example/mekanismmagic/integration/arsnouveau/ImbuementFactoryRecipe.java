package com.example.mekanismmagic.integration.arsnouveau;

import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.basic.BasicItemStackToItemStackRecipe;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;

public final class ImbuementFactoryRecipe
        extends BasicItemStackToItemStackRecipe {
    private static final ResourceLocation UNKNOWN_CATALYST_ID =
            ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "catalyst/unknown");
    private static final RecipeType<ItemStackToItemStackRecipe> TYPE =
            new RecipeType<>() {
            };
    private static final RecipeSerializer<ImbuementFactoryRecipe> SERIALIZER =
            (RecipeSerializer<ImbuementFactoryRecipe>)
                    (RecipeSerializer<?>) RecipeSerializer.SHAPELESS_RECIPE;
    private final ResourceLocation id;
    private final ResourceLocation catalystId;
    private final boolean requiresIdentifier;
    private final int duration;
    private final int sourceCost;
    private final long catalogVersion;
    private final String semanticSignature;

    public ImbuementFactoryRecipe(
                                  RecipeHolder<ImbuementRecipe> sourceRecipe,
                                  ItemStack identifier,
                                  com.example.mekanismmagic.integration.common.recipe
                                          .MachineRecipeResult result,
                                  boolean requiresIdentifier,
                                  long catalogVersion) {
        super(ItemStackIngredient.of(new SizedIngredient(
                        sourceRecipe.value().getInput(), 1)),
                result.output(), TYPE);
        this.id = result.id();
        this.catalystId = CatalystIdentifierItem.catalystId(identifier);
        this.requiresIdentifier = requiresIdentifier;
        this.duration = result.duration();
        this.sourceCost = result.resourceCost(
                ArsNouveauMachineConfig.SOURCE_RESOURCE);
        this.catalogVersion = catalogVersion;
        this.semanticSignature = ArsNouveauRecipeScanner
                .recipeSemanticSignature(sourceRecipe.value());
    }

    public ResourceLocation imbuementId() {
        return id;
    }

    public ResourceLocation catalystId() {
        return catalystId;
    }

    public int duration() {
        return duration;
    }

    public int sourceCost() {
        return sourceCost;
    }

    public boolean requiresIdentifier() {
        return requiresIdentifier;
    }

    public boolean sameIdentifier(ItemStack stack) {
        return !requiresIdentifier
                || stack.is(ArsNouveauRegistries
                .CATALYST_IDENTIFIER_ITEM.get())
                && !catalystId.equals(UNKNOWN_CATALYST_ID)
                && CatalystIdentifierItem.matchesCatalystId(
                stack, catalystId);
    }

    public boolean matchesCurrentRecipe(Level level) {
        return level != null
                && catalogVersion == ArsNouveauRecipeScanner.version(
                level.getRecipeManager())
                && semanticSignature.equals(ArsNouveauRecipeScanner
                .semanticSignature(level.getRecipeManager(), id));
    }

    @Override
    public List<ItemStack> getOutputDefinition() {
        return List.of(getOutputRaw().copy());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
