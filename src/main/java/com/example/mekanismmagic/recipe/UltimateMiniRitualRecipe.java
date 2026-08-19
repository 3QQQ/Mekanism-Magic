package com.example.mekanismmagic.recipe;

import com.example.mekanismmagic.MekanismMagic;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Strict four-pentacle recipe. Vanilla component predicates are intentionally
 * not trusted here because older 1.21.1 recipe loaders can reduce them to an
 * item-only Ingredient.
 */
public final class UltimateMiniRitualRecipe extends CustomRecipe {
    public static final RecipeSerializer<UltimateMiniRitualRecipe> SERIALIZER =
            new RecipeSerializer<>() {
                private final com.mojang.serialization.MapCodec<
                        UltimateMiniRitualRecipe> codec =
                        com.mojang.serialization.MapCodec.unit(
                                UltimateMiniRitualRecipe::new);
                private final StreamCodec<RegistryFriendlyByteBuf,
                        UltimateMiniRitualRecipe> streamCodec =
                        StreamCodec.unit(new UltimateMiniRitualRecipe());

                @Override
                public com.mojang.serialization.MapCodec<
                        UltimateMiniRitualRecipe> codec() {
                    return codec;
                }

                @Override
                public StreamCodec<RegistryFriendlyByteBuf,
                        UltimateMiniRitualRecipe> streamCodec() {
                    return streamCodec;
                }
            };

    private static final String[] REQUIRED_PENTACLES = {
            "occultism:craft_marid",
            "occultism:possess_marid",
            "occultism:summon_marid",
            "occultism:summon_unbound_marid"
    };

    public UltimateMiniRitualRecipe() {
        super(CraftingBookCategory.MISC);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }
        return isPentacle(input.getItem(0, 0), REQUIRED_PENTACLES[0])
                && isPentacle(input.getItem(2, 0), REQUIRED_PENTACLES[1])
                && isPentacle(input.getItem(0, 2), REQUIRED_PENTACLES[2])
                && isPentacle(input.getItem(2, 2), REQUIRED_PENTACLES[3])
                && isItem(input.getItem(1, 0),
                "mekanism:alloy_atomic")
                && isItem(input.getItem(0, 1),
                "occultism:spirit_attuned_crystal")
                && isItem(input.getItem(2, 1),
                "occultism:spirit_attuned_crystal")
                && isItem(input.getItem(1, 1), "minecraft:nether_star")
                && isItem(input.getItem(1, 2),
                "mekanism:ultimate_control_circuit");
    }

    @Override
    public ItemStack assemble(CraftingInput input,
                              HolderLookup.Provider registries) {
        return new ItemStack(MekanismMagic.ULTIMATE_MINI_RITUAL.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(MekanismMagic.ULTIMATE_MINI_RITUAL.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }

    private static boolean isPentacle(ItemStack stack, String required) {
        if (stack.isEmpty()
                || stack.getItem() != MekanismMagic.MINI_RITUAL.get()) {
            return false;
        }
        net.minecraft.world.item.component.CustomData data =
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty()
                && required.equals(data.copyTag().getString("pentacle"));
    }

    private static boolean isItem(ItemStack stack, String id) {
        return !stack.isEmpty()
                && id.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).toString());
    }
}
