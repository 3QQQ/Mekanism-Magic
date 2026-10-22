package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.ClibanoRecipeView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.FireType;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.Snapshot;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/** Per-machine single-workstation selector; no GUI calls or native registry discovery. */
public final class ClibanoRecipeSelector {
    private Snapshot previousCatalog;
    private ItemStack previousFirst = ItemStack.EMPTY;
    private ItemStack previousSecond = ItemStack.EMPTY;
    private ItemStack previousEnhancer = ItemStack.EMPTY;
    private ItemStack previousMedium = ItemStack.EMPTY;
    private FireType previousFire;
    private Selection selected;
    private long candidateScans;

    /**
     * effectiveFire is the already-active fire, or the prospective medium's fire
     * when there is no active soul duration. The caller owns that fuel decision.
     * A medium change invalidates lookup, but cannot upgrade an already-active fire.
     */
    public Optional<Selection> select(ServerLevel level, ItemStack slot0, ItemStack slot1,
                                      FireType effectiveFire, ItemStack enhancer, ItemStack fireMedium) {
        Snapshot catalog = ForbiddenArcanusRecipeCatalog.snapshot(level.getServer());
        boolean unchanged = previousCatalog == catalog && previousFire == effectiveFire
                && ItemStack.matches(previousFirst, slot0) && ItemStack.matches(previousSecond, slot1)
                && ItemStack.matches(previousEnhancer, enhancer) && ItemStack.matches(previousMedium, fireMedium);
        if (unchanged && (selected == null || stillMatches(level, selected, slot0, slot1,
                effectiveFire, enhancer))) {
            return Optional.ofNullable(selected);
        }
        previousCatalog = catalog;
        previousFirst = slot0.copy();
        previousSecond = slot1.copy();
        previousEnhancer = enhancer.copy();
        previousMedium = fireMedium.copy();
        previousFire = effectiveFire;
        selected = scan(level, catalog, slot0, slot1, effectiveFire, enhancer);
        return Optional.ofNullable(selected);
    }

    /** O(1) native recipe check for a commit; does not choose another recipe. */
    public boolean stillMatches(ServerLevel level, Selection selection, ItemStack slot0, ItemStack slot1,
                                FireType fire, ItemStack enhancer) {
        Snapshot catalog = ForbiddenArcanusRecipeCatalog.snapshot(level.getServer());
        if (catalog.clibanoById().get(selection.id()) != selection.view()) {
            return false;
        }
        ItemStack first = at(selection.firstInputSlot(), slot0, slot1);
        ItemStack second = selection.secondInputSlot() < 0 ? ItemStack.EMPTY
                : at(selection.secondInputSlot(), slot0, slot1);
        return ForbiddenArcanusRecipeCatalog.matchesClibano(level, selection.id(), first, second, fire, enhancer);
    }

    public void invalidate() {
        previousCatalog = null;
        previousFirst = ItemStack.EMPTY;
        previousSecond = ItemStack.EMPTY;
        previousEnhancer = ItemStack.EMPTY;
        previousMedium = ItemStack.EMPTY;
        previousFire = null;
        selected = null;
    }

    private Selection scan(ServerLevel level, Snapshot catalog, ItemStack slot0, ItemStack slot1,
                            FireType fire, ItemStack enhancer) {
        candidateScans++;
        // Iterate the published index directly; never reconstruct its whole recipe list on a tick.
        if (!slot0.isEmpty() && !slot1.isEmpty()) {
            for (ClibanoRecipeView view : catalog.clibanoById().values()) {
                if (view.doubleInput() && ForbiddenArcanusRecipeCatalog.matchesClibano(level, view.id(),
                        slot0, slot1, fire, enhancer)) {
                    return selection(level, view, 0, 1, slot0, slot1, fire, enhancer);
                }
            }
        }
        for (int slot = 0; slot < 2; slot++) {
            ItemStack input = at(slot, slot0, slot1);
            if (!input.isEmpty()) {
                for (ClibanoRecipeView view : catalog.clibanoById().values()) {
                    if (!view.doubleInput() && ForbiddenArcanusRecipeCatalog.matchesClibano(level, view.id(),
                            input, ItemStack.EMPTY, fire, enhancer)) {
                        return selection(level, view, slot, -1, input, ItemStack.EMPTY, fire, enhancer);
                    }
                }
            }
        }
        return null;
    }

    private static Selection selection(ServerLevel level, ClibanoRecipeView view, int firstSlot,
                                        int secondSlot, ItemStack first, ItemStack second,
                                        FireType fire, ItemStack enhancer) {
        return new Selection(view.id(), view, firstSlot, secondSlot, 1, secondSlot < 0 ? 0 : 1,
                signature(level, view, firstSlot, secondSlot, first, second, fire, enhancer));
    }

    /**
     * Stable across saves and stock-count changes. Revision is handled separately by
     * the machine; fire and the consumed items' full components identify this operation.
     * This fingerprint is a progress key, never a substitute for native commit validation.
     */
    private static String signature(ServerLevel level, ClibanoRecipeView view, int firstSlot, int secondSlot,
                                     ItemStack first, ItemStack second, FireType fire, ItemStack enhancer) {
        JsonObject value = new JsonObject();
        value.addProperty("recipe", view.id().toString());
        value.addProperty("first_slot", firstSlot);
        value.addProperty("second_slot", secondSlot);
        value.addProperty("fire", fire.name());
        value.addProperty("required_fire", view.requiredFire().name());
        view.requiredEnhancer().ifPresent(id -> value.addProperty("required_enhancer", id.toString()));
        value.addProperty("fire_time", view.cookingTimes().fire());
        value.addProperty("soul_time", view.cookingTimes().soulFire());
        value.addProperty("enchanted_time", view.cookingTimes().enchantedFire());
        value.addProperty("experience", view.experience());
        view.residue().ifPresent(residue -> {
            value.addProperty("residue", residue.type().toString());
            value.addProperty("residue_chance", residue.chance());
        });
        var ops = RegistryOps.create(JsonOps.INSTANCE, level.registryAccess());
        value.add("first", encode(ops, first.copyWithCount(1)));
        value.add("second", encode(ops, second.copyWithCount(1)));
        value.add("enhancer", encode(ops, enhancer.copyWithCount(1)));
        value.add("output", encode(ops, view.output()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    canonical(value).toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static JsonElement encode(RegistryOps<JsonElement> ops, ItemStack stack) {
        return ItemStack.OPTIONAL_CODEC.encodeStart(ops, stack).getOrThrow();
    }

    private static JsonElement canonical(JsonElement value) {
        if (value.isJsonObject()) {
            JsonObject sorted = new JsonObject();
            value.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sorted.add(entry.getKey(), canonical(entry.getValue())));
            return sorted;
        }
        if (value.isJsonArray()) {
            JsonArray array = new JsonArray();
            value.getAsJsonArray().forEach(element -> array.add(canonical(element)));
            return array;
        }
        return value;
    }

    private static ItemStack at(int slot, ItemStack slot0, ItemStack slot1) {
        return slot == 0 ? slot0 : slot1;
    }

    long candidateScanCount() {
        return candidateScans;
    }

    public record Selection(ResourceLocation id, ClibanoRecipeView view, int firstInputSlot,
                            int secondInputSlot, int firstAmount, int secondAmount, String signature) {
    }

}
