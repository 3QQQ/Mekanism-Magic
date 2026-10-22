package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.integration.common.entity.CapturedEntity;
import com.example.mekanismmagic.integration.common.entity.EntityContainerAdapter;
import com.stal111.forbidden_arcanus.common.item.component.StoredEntity;
import com.stal111.forbidden_arcanus.core.init.ModDataComponents;
import com.stal111.forbidden_arcanus.core.init.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Quantum and boss catcher boundary for the verified Forbidden & Arcanus 2.6.1.
 * Loaded only by the version-gated content module. Reading never creates an
 * entity or calls StoredEntity.of, which changes riding state during capture.
 */
public final class ForbiddenArcanusEntityContainerAdapter
        implements EntityContainerAdapter {
    public static final ForbiddenArcanusEntityContainerAdapter INSTANCE =
            new ForbiddenArcanusEntityContainerAdapter();

    private ForbiddenArcanusEntityContainerAdapter() {
    }

    @Override
    public String modId() {
        return "forbidden_arcanus";
    }

    @Override
    public Optional<CapturedEntity> capturedEntity(ItemStack stack) {
        if (!isCatcher(stack)) {
            return Optional.empty();
        }
        StoredEntity stored = stack.get(ModDataComponents.STORED_ENTITY);
        if (stored == null || stored.data() == null) {
            return Optional.empty();
        }
        // Keep every saved field, including nested components and passengers.
        // CapturedEntity also copies on construction and on every accessor.
        CompoundTag data = stored.data().copyTag();
        if (!data.contains("id", Tag.TAG_STRING)) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(data.getString("id"));
        // ENTITY_TYPE is defaulted: get(unknown) alone would accept a pig.
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)
                || BuiltInRegistries.ENTITY_TYPE.get(id) == EntityType.PLAYER) {
            return Optional.empty();
        }
        return Optional.of(new CapturedEntity(id, data));
    }

    @Override
    public boolean empty(ItemStack stack) {
        Optional<CapturedEntity> captured = capturedEntity(stack);
        if (captured.isEmpty()) {
            return false;
        }
        CompoundTag data = captured.get().entityData();
        // A sacrifice pays for one entity, so an unexpected passenger payload
        // may be inspected but must never be silently destroyed by any API.
        if (data.contains("Passengers")
                && (!(data.get("Passengers") instanceof ListTag passengers)
                || !passengers.isEmpty())) {
            return false;
        }
        // Native summonEntity removes precisely this component. Release has
        // no durability, item or Aureal cost; all unrelated components stay.
        return stack.remove(ModDataComponents.STORED_ENTITY) != null;
    }

    private static boolean isCatcher(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModItems.QUANTUM_CATCHER.get())
                || stack.is(ModItems.BOSS_CATCHER.get())) {
            return true;
        }
        return ModItems.DYED_QUANTUM_CATCHERS.values().stream()
                .anyMatch(entry -> stack.is(entry.get()));
    }
}
