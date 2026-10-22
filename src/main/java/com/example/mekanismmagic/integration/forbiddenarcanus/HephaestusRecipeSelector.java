package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.integration.common.recipe.InputUse;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.RitualView;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRecipeCatalog.Snapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/** Per-machine unpaid selection cache. Paid results and context checks belong to the machine. */
public final class HephaestusRecipeSelector {
    private Snapshot previousCatalog;
    private ItemStack previousMain = ItemStack.EMPTY;
    private int previousTier;
    private List<ItemStack> previousAuxiliary = List.of();
    private List<ItemStack> previousEnhancers = List.of();
    private boolean previousCreative;
    private Selection selected;
    private long candidateScans;

    public Optional<Selection> select(ServerLevel level, ItemStack main, List<ItemStack> auxiliary,
                                      int tier, List<ItemStack> enhancers,
                                      boolean creativeMagic) {
        Snapshot catalog = ForbiddenArcanusRecipeCatalog.snapshot(level.getServer());
        if (previousCatalog == catalog && previousCreative == creativeMagic
                && ItemStack.matches(previousMain, main) && previousTier == tier
                && matches(previousAuxiliary, auxiliary)
                && matches(previousEnhancers, enhancers)) {
            return Optional.ofNullable(selected);
        }
        previousCatalog = catalog;
        previousMain = main.copy();
        previousTier = tier;
        previousAuxiliary = copies(auxiliary);
        previousEnhancers = copies(enhancers);
        previousCreative = creativeMagic;
        selected = null;
        candidateScans++;
        if (tier < 1 || tier > 5) {
            return Optional.empty();
        }
        for (var recipe : HephaestusForge261Bridge.itemRecipes(level.getServer())) {
            var candidate = HephaestusForge261Bridge.select(level, recipe, main, auxiliary, tier,
                    enhancers, creativeMagic);
            if (candidate.isPresent()) {
                selected = candidate.get();
                break;
            }
        }
        if (selected == null) {
            for (var recipe : HephaestusForge261Bridge.upgradeRecipes(level.getServer())) {
                var candidate = HephaestusForge261Bridge.select(level, recipe, main, auxiliary, tier,
                        enhancers, creativeMagic);
                if (candidate.isPresent()) {
                    selected = candidate.get();
                    break;
                }
            }
        }
        return Optional.ofNullable(selected);
    }

    public void invalidate() {
        previousCatalog = null;
        previousMain = ItemStack.EMPTY;
        previousTier = 0;
        previousAuxiliary = List.of();
        previousEnhancers = List.of();
        selected = null;
    }

    private static List<ItemStack> copies(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static boolean matches(List<ItemStack> first, List<ItemStack> second) {
        if (first.size() != second.size()) {
            return false;
        }
        for (int slot = 0; slot < first.size(); slot++) {
            if (!ItemStack.matches(first.get(slot), second.get(slot))) {
                return false;
            }
        }
        return true;
    }

    long candidateScanCount() { return candidateScans; }

    /** InputUse uses actual machine logical slots: main 0, auxiliaries 1 through 8. */
    public record Selection(ResourceLocation id, RitualView view, List<InputUse> inputs,
                            ItemStack resultPreview, int[] gateCosts, int[] rawCosts,
                            String contextSignature, String signature, int upgradeTier) {
        public Selection {
            inputs = List.copyOf(inputs);
            resultPreview = resultPreview.copy();
            gateCosts = gateCosts.clone();
            rawCosts = rawCosts.clone();
        }

        @Override public ItemStack resultPreview() { return resultPreview.copy(); }
        @Override public int[] gateCosts() { return gateCosts.clone(); }
        @Override public int[] rawCosts() { return rawCosts.clone(); }
        public boolean upgradesForge() { return upgradeTier > 0; }
    }
}
