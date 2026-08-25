package com.example.mekanismmagic.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.me.ManagedGridNode;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRecipeBridge;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierItem;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import com.hollingsworth.arsnouveau.setup.registry.RecipeRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Optional native AE2 provider. Each Ars imbuement recipe becomes an AE
 * pattern whose catalyst_id is stored in the pattern object, not as an item
 * input. The only routed input is the consumed reagent.
 */
public final class Ae2ImbuementProvider
        implements IInWorldGridNodeHost, ICraftingProvider {
    private static final Map<ImbuementProcessorBlockEntity,
            Ae2ImbuementProvider> INSTANCES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final ImbuementProcessorBlockEntity tile;
    private final IManagedGridNode node;

    private Ae2ImbuementProvider(ImbuementProcessorBlockEntity tile) {
        this.tile = tile;
        this.node = new ManagedGridNode(this, new Listener())
                .setInWorldNode(true)
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setVisualRepresentation(
                        ArsNouveauRecipeBridge.createUnboundIdentifier());
    }

    public static Ae2ImbuementProvider forTile(
            ImbuementProcessorBlockEntity tile) {
        return INSTANCES.computeIfAbsent(tile, Ae2ImbuementProvider::new);
    }

    @Override
    public IGridNode getGridNode(Direction direction) {
        if (tile.getLevel() != null && !node.isReady()) {
            node.create(tile.getLevel(), tile.getBlockPos());
        }
        return node.getNode();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        Level level = tile.getLevel();
        if (level == null) {
            return List.of();
        }
        return level.getRecipeManager().getAllRecipesFor(
                        RecipeRegistry.IMBUEMENT_TYPE.get()).stream()
                .map(holder -> new Ae2ImbuementPattern(tile, holder))
                .map(pattern -> (IPatternDetails) pattern)
                .toList();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern,
                               appeng.api.stacks.KeyCounter[] inputItems) {
        if (!(pattern instanceof Ae2ImbuementPattern imbuement)
                || imbuement.tile() != tile
                || tile.mekanismMagicIsBusy()) {
            return false;
        }
        if (!tile.selectCatalystIdentifierId(
                imbuement.catalystId().toString())) {
            return false;
        }
        if (inputItems == null || inputItems.length == 0) {
            return false;
        }
        boolean inserted = false;
        for (var counter : inputItems) {
            if (counter == null) {
                continue;
            }
            for (var entry : counter) {
                AEKey key = entry.getKey();
                if (!(key instanceof AEItemKey itemKey)) {
                    continue;
                }
                ItemStack remainder = tile.getNativeInputSlot().insertItem(
                        itemKey.toStack((int) entry.getLongValue()),
                        Action.EXECUTE, AutomationType.EXTERNAL);
                if (!remainder.isEmpty()) {
                    return false;
                }
                inserted = true;
            }
        }
        return inserted;
    }

    @Override
    public boolean isBusy() {
        return tile.mekanismMagicIsBusy();
    }

    @Override
    public int getPatternPriority() {
        return 0;
    }

    private static final class Listener
            implements IGridNodeListener<Ae2ImbuementProvider> {
        @Override
        public void onSaveChanges(Ae2ImbuementProvider owner,
                                  IGridNode node) {
            owner.tile.setChanged();
        }
    }
}
