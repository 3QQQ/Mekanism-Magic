package com.example.mekanismmagic.recipe;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.occultism.MiniPentacleDeployment;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;

import java.util.stream.Stream;

/**
 * NeoForge Ingredient that matches one exact miniature pentacle ID.
 */
public record SpecificPentacleIngredient(String pentacle)
        implements ICustomIngredient {
    public static final com.mojang.serialization.MapCodec<
            SpecificPentacleIngredient> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.fieldOf("pentacle")
                            .forGetter(SpecificPentacleIngredient::pentacle)
            ).apply(instance, SpecificPentacleIngredient::new));

    public static final StreamCodec<RegistryFriendlyByteBuf,
            SpecificPentacleIngredient> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    SpecificPentacleIngredient::pentacle,
                    SpecificPentacleIngredient::new);

    public static IngredientType<SpecificPentacleIngredient> type() {
        return MekanismMagic.SPECIFIC_PENTACLE_INGREDIENT.get();
    }

    @Override
    public boolean test(ItemStack stack) {
        if (stack.isEmpty()
                || stack.getItem() != MekanismMagic.MINI_RITUAL.get()
                || MiniPentacleDeployment.isDeployed(stack)) {
            return false;
        }
        net.minecraft.world.item.component.CustomData data =
                stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty()
                && pentacle.equals(data.getUnsafe().getString("pentacle"));
    }

    @Override
    public Stream<ItemStack> getItems() {
        ItemStack stack = new ItemStack(MekanismMagic.MINI_RITUAL.get());
        net.minecraft.world.item.component.CustomData.update(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                stack, tag -> tag.putString("pentacle", pentacle));
        return Stream.of(stack);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    @Override
    public IngredientType<?> getType() {
        return type();
    }
}
