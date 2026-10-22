package com.example.mekanismmagic.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraGeneratorBlockEntity;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import de.ellpeck.naturesaura.items.ItemAuraBottle;
import de.ellpeck.naturesaura.items.ModItems;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.List;

/** Virtual AE recipes on a generator's existing node, not on every storage cell. */
public final class Ae2AuraBottlingProvider implements ICraftingProvider {
    private final WeakReference<NatureAuraGeneratorBlockEntity> tileReference;
    private final Ae2MachineResourceProvider host;

    private Ae2AuraBottlingProvider(NatureAuraGeneratorBlockEntity tile,
                                   Ae2MachineResourceProvider host) {
        this.tileReference = new WeakReference<>(tile);
        this.host = host;
    }

    /** Called reflectively only when Nature's Aura is present. */
    public static void register() {
        Ae2MachineResourceProvider.registerOptionalCraftingService(
                new Ae2MachineResourceProvider.OptionalCraftingService() {
                    @Override
                    public void attach(IManagedGridNode node,
                                       TileEntityMekanism tile,
                                       Ae2MachineResourceProvider host) {
                        if (tile instanceof NatureAuraGeneratorBlockEntity
                                generator) {
                            node.addService(ICraftingProvider.class,
                                    new Ae2AuraBottlingProvider(generator,
                                            host));
                        }
                    }

                    @Override
                    public void tick(net.minecraft.world.level.block.entity.BlockEntity tile,
                                     IGrid grid, IActionSource actor) {
                        if (tile instanceof NatureAuraGeneratorBlockEntity
                                generator
                                && generator.getLevel() instanceof ServerLevel
                                level) {
                            Ae2AuraBottlingService.flushPending(level,
                                    generator.getBlockPos(), grid, actor);
                        }
                    }
                });
    }

    private NatureAuraGeneratorBlockEntity tile() {
        return tileReference.get();
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        NatureAuraGeneratorBlockEntity tile = tile();
        if (tile == null || tile.isRemoved() || tile.getLevel() == null)
            return List.of();
        return NaturesAuraAPI.AURA_TYPES.values().stream()
                .sorted(Comparator.comparing(type ->
                        type.getName().toString()))
                .map(type -> new BottlePattern(this, type))
                .filter(BottlePattern::valid)
                .map(pattern -> (IPatternDetails) pattern)
                .toList();
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern,
                               KeyCounter[] inputItems) {
        NatureAuraGeneratorBlockEntity tile = tile();
        if (tile == null || tile.isRemoved()
                || !(tile.getLevel() instanceof ServerLevel level)
                || !(pattern instanceof BottlePattern bottle)
                || bottle.owner != null && bottle.owner != this)
            return false;
        IGridNode node = host.getActionableNode();
        if (node == null || !node.isActive()
                || node.getGrid() == null) return false;
        return Ae2AuraBottlingService.acceptCraftingInputs(level,
                tile.getBlockPos(), node.getGrid(),
                IActionSource.ofMachine(host), bottle.auraKey,
                inputItems);
    }

    @Override
    public boolean isBusy() {
        NatureAuraGeneratorBlockEntity tile = tile();
        return tile == null || tile.isRemoved()
                || !(tile.getLevel() instanceof ServerLevel);
    }

    /** AE CPU persisted-pattern decode after a world restart. */
    public static IPatternDetails tryDecode(AEItemKey definition) {
        if (!containsPatternMarker(definition)) return null;
        ItemStack stack = definition.toStack();
        if (!stack.is(ModItems.AURA_BOTTLE)) return null;
        IAuraType type = ItemAuraBottle.getType(stack);
        if (type == null) return null;
        BottlePattern pattern = new BottlePattern(null, type);
        return pattern.valid() && definition.equals(pattern.definition)
                ? pattern : null;
    }

    public static boolean containsPatternMarker(AEItemKey definition) {
        return definition != null && definition.toStack()
                .getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(
                        "mekanism_magic_virtual_aura_bottling");
    }

    private static final class BottlePattern implements IPatternDetails {
        private final Ae2AuraBottlingProvider owner;
        private final AEKey auraKey;
        private final AEItemKey definition;
        private final GenericStack output;
        private final IInput[] inputs;

        private BottlePattern(Ae2AuraBottlingProvider owner,
                              IAuraType type) {
            this.owner = owner;
            auraKey = MagicResourceKeys.key(type.getName());
            ItemStack filled = ItemAuraBottle.setType(
                    new ItemStack(ModItems.AURA_BOTTLE), type);
            output = GenericStack.fromItemStack(filled);
            CustomData.update(DataComponents.CUSTOM_DATA, filled,
                    tag -> tag.putBoolean(
                            "mekanism_magic_virtual_aura_bottling", true));
            definition = AEItemKey.of(filled);
            AEItemKey empty = AEItemKey.of(new ItemStack(
                    ModItems.BOTTLE_TWO_THE_REBOTTLING));
            inputs = new IInput[]{new ExactInput(empty, 1L),
                    new ExactInput(auraKey,
                            Ae2AuraBottlingService.AURA_PER_BOTTLE)};
        }

        private boolean valid() {
            return auraKey != null && definition != null
                    && output != null && inputs[0].getPossibleInputs()[0]
                    .what() != null;
        }

        @Override
        public AEItemKey getDefinition() { return definition; }

        @Override
        public IInput[] getInputs() { return inputs.clone(); }

        @Override
        public List<GenericStack> getOutputs() { return List.of(output); }

        @Override
        public boolean equals(Object other) {
            return other instanceof BottlePattern pattern
                    && definition != null
                    && definition.equals(pattern.definition);
        }

        @Override
        public int hashCode() {
            return definition == null ? 0 : definition.hashCode();
        }
    }

    private record ExactInput(AEKey key, long multiplier)
            implements IPatternDetails.IInput {
        @Override
        public GenericStack[] getPossibleInputs() {
            return new GenericStack[]{new GenericStack(key, 1L)};
        }

        @Override
        public long getMultiplier() { return multiplier; }

        @Override
        public boolean isValid(AEKey candidate, Level level) {
            return key != null && key.equals(candidate);
        }

        @Override
        public AEKey getRemainingKey(AEKey consumed) { return null; }
    }
}
