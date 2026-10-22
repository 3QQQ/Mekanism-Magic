package com.example.mekanismmagic.integration.ae2.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;

/** A magic resource identity, never an item/fluid stand-in or a second balance. */
public final class MagicResourceKey extends AEKey {
    // AE KeyCounter groups primary keys by reference, not equals(). Keep identity
    // stable across independently decoded keys without retaining unused IDs forever.
    private static final Interner<ResourceLocation> RESOURCE_IDS = Interners.newWeakInterner();
    public static final MapCodec<MagicResourceKey> CODEC = ResourceLocation.CODEC
            .fieldOf("resource").xmap(MagicResourceKey::new, MagicResourceKey::resource);
    private final ResourceLocation resource;

    public MagicResourceKey(ResourceLocation resource) {
        this.resource = RESOURCE_IDS.intern(Objects.requireNonNull(resource));
    }

    public ResourceLocation resource() { return resource; }
    @Override public AEKeyType getType() { return MagicResourceKeys.TYPE; }
    @Override public AEKey dropSecondary() { return this; }
    @Override public Object getPrimaryKey() { return resource; }
    @Override public ResourceLocation getId() { return resource; }
    @Override public boolean hasComponents() { return false; }
    @Override public Component computeDisplayName() { return MagicResourceKeys.displayName(resource); }
    @Override public void writeToPacket(RegistryFriendlyByteBuf buffer) { buffer.writeResourceLocation(resource); }
    @Override public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("resource", resource.toString());
        return tag;
    }
    // Energy has no dropped item representation; cells preserve it in their own data.
    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { }
    @Override public boolean equals(Object other) {
        return this == other || other instanceof MagicResourceKey key && resource.equals(key.resource);
    }
    @Override public int hashCode() { return resource.hashCode(); }
}
