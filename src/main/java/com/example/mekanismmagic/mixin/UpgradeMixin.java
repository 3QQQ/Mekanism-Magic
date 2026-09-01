package com.example.mekanismmagic.mixin;

import com.example.mekanismmagic.MagicLang;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.IntFunction;
import mekanism.api.Upgrade;
import mekanism.api.text.EnumColor;
import mekanism.api.text.ILangEntry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the creative-magic plugin as a real Mekanism upgrade type. */
@Mixin(value = Upgrade.class, remap = false)
public abstract class UpgradeMixin {
    @Shadow
    @Final
    @Mutable
    private static Upgrade[] $VALUES;

    @Shadow
    @Final
    @Mutable
    public static Codec<Upgrade> CODEC;

    @Shadow
    @Final
    @Mutable
    public static IntFunction<Upgrade> BY_ID;

    @Shadow
    @Final
    @Mutable
    public static StreamCodec<ByteBuf, Upgrade> STREAM_CODEC;

    @Invoker("<init>")
    public static Upgrade mekanismMagic$createUpgrade(
            String internalName, int internalId, String serializedName,
            ILangEntry name, ILangEntry description, int max,
            EnumColor color) {
        throw new AssertionError();
    }

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void mekanismMagic$addCreativeMagicUpgrade(
            CallbackInfo callback) {
        MagicUpgrades.CREATIVE_MAGIC = mekanismMagic$addVariant(
                "CREATIVE_MAGIC", "creative_magic",
                MagicLang.UPGRADE_CREATIVE_MAGIC,
                MagicLang.UPGRADE_CREATIVE_MAGIC_DESCRIPTION,
                1, EnumColor.PURPLE);
        mekanismMagic$rebuildSerialization();
    }

    @Unique
    private static Upgrade mekanismMagic$addVariant(
            String internalName, String serializedName,
            ILangEntry name, ILangEntry description, int max,
            EnumColor color) {
        ArrayList<Upgrade> values = new ArrayList<>(
                Arrays.asList($VALUES));
        Upgrade upgrade = mekanismMagic$createUpgrade(
                internalName, values.getLast().ordinal() + 1,
                serializedName, name, description, max, color);
        values.add(upgrade);
        $VALUES = values.toArray(Upgrade[]::new);
        return upgrade;
    }

    @Unique
    private static void mekanismMagic$rebuildSerialization() {
        Upgrade[] values = $VALUES;
        Function<String, Upgrade> nameLookup =
                StringRepresentable.createNameLookup(
                        values, Function.identity());
        Function<String, Upgrade> remapper = name -> "gas".equals(name)
                ? Upgrade.CHEMICAL : nameLookup.apply(name);
        CODEC = new StringRepresentable.EnumCodec<>(values, remapper);
        BY_ID = ByIdMap.continuous(Upgrade::ordinal, values,
                ByIdMap.OutOfBoundsStrategy.WRAP);
        STREAM_CODEC = ByteBufCodecs.idMapper(BY_ID, Upgrade::ordinal);
    }
}
