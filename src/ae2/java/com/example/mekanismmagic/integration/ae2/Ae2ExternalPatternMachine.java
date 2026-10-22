package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.integration.arsnouveau.CatalystIdentifierSelectionHost;
import com.example.mekanismmagic.integration.common.recipe.ExternalItemPatternRouter;
import com.example.mekanismmagic.integration.naturesaura.NatureAltarAutomationHost;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordinary AE2 providers deliver a complete item pattern through one configured face. */
public final class Ae2ExternalPatternMachine implements ICraftingMachine {
    private final TileEntityMekanism tile;
    private final IMekanismMagicAutomation host;
    private final Direction face;

    private Ae2ExternalPatternMachine(TileEntityMekanism tile,
                                     IMekanismMagicAutomation host, Direction face) {
        this.tile = tile;
        this.host = host;
        this.face = face;
    }

    static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            if (BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type).getNamespace().equals(MekanismMagic.MOD_ID)) {
                registerType(event, type);
            }
        }
    }

    private static <T extends BlockEntity> void registerType(
            RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(AECapabilities.CRAFTING_MACHINE, type, (blockEntity, side) -> {
            if (side == null || !(blockEntity instanceof TileEntityMekanism machine)
                    || !(blockEntity instanceof IMekanismMagicAutomation automation)
                    || automation.mekanismMagicPatternInputs().isEmpty()) {
                return null;
            }
            List<IInventorySlot> exposed = machine.getInventorySlots(side);
            return automation.mekanismMagicPatternInputs().stream().anyMatch(exposed::contains)
                    ? new Ae2ExternalPatternMachine(machine, automation, side) : null;
        });
    }

    @Override
    public PatternContainerGroup getCraftingMachineInfo() {
        ItemStack icon = new ItemStack(tile.getBlockState().getBlock().asItem());
        AEItemKey key = AEItemKey.of(icon);
        return key == null ? PatternContainerGroup.nothing()
                : new PatternContainerGroup(key, icon.getHoverName(), List.of());
    }

    @Override
    public boolean acceptsPlans() {
        // Keep ownership of this face while busy: returning false would make
        // AE2 fall back to individual stack insertion and bypass role routing.
        return true;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] supplied, Direction side) {
        if (side != face || tile.isRemoved() || tile.getLevel() == null
                || tile.getLevel().isClientSide() || pattern == null
                || pattern.getDefinition() == null || supplied == null) return false;
        try {
            KeyCounter[] owned = validateAndCopy(pattern, supplied);
            if (owned == null || !pattern.supportsPushInputsToExternalInventory()) return false;
            List<IInventorySlot> exposed = tile.getInventorySlots(face);
            if (exposed.isEmpty()) return false;
            List<PatternStack> outputs = outputs(pattern);
            if (outputs.isEmpty()) return false;

            CatalystIdentifierSelectionHost catalystHost = null;
            String catalystId = null;
            if (pattern instanceof Ae2IdentifierImbuementPattern imbuement
                    && imbuement.requiresCatalystIdentifier()) {
                if (!(tile instanceof CatalystIdentifierSelectionHost selection)) return false;
                catalystId = selection.catalystIdentifierIdForRecipe(imbuement.recipeId());
                if (catalystId.isEmpty() || !selection.canSelectCatalystIdentifierId(catalystId)) return false;
                catalystHost = selection;
            }

            // Altar contexts have a dedicated transaction that also maintains
            // factory lane locks. The supplier remains an ordinary AE2 block.
            if (pattern instanceof Ae2NatureAltarPattern altar
                    && tile instanceof NatureAltarAutomationHost altarHost) {
                if (!exposed.containsAll(host.mekanismMagicPatternInputs())) return false;
                ItemStack reagent = altar.collectInput(owned, tile.getLevel());
                return !reagent.isEmpty() && altarHost.acceptNatureAltarPattern(altar.recipeId(), reagent);
            }

            Map<AEItemKey, Long> dispatched = new LinkedHashMap<>();
            pattern.pushInputsToExternalInventory(owned, (key, amount) -> {
                if (!(key instanceof AEItemKey item) || amount <= 0 || amount > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("External provider accepts item batches only");
                }
                dispatched.merge(item, amount, Math::addExact);
            });
            long originalAmount = amount(supplied);
            long dispatchedAmount = dispatched.values().stream().reduce(0L, Math::addExact);
            if (originalAmount <= 0 || dispatchedAmount != originalAmount) return false;
            List<PatternStack> inputs = dispatched.entrySet().stream()
                    .map(entry -> new PatternStack(entry.getKey().toStack(1), entry.getValue())).toList();
            Map<IInventorySlot, ItemStack> before = new IdentityHashMap<>();
            if (catalystHost != null) {
                host.mekanismMagicPatternInputs().forEach(slot -> before.put(slot, slot.getStack().copy()));
            }
            if (!ExternalItemPatternRouter.push(host, exposed, inputs, outputs)) return false;
            // A factory's shared catalyst must be reserved during this same
            // server-thread call, before another provider can fill a lane.
            if (catalystHost != null) {
                try {
                    if (!catalystHost.selectCatalystIdentifierId(catalystId)) {
                        before.forEach(IInventorySlot::setStack);
                        return false;
                    }
                } catch (RuntimeException | Error failure) {
                    before.forEach(IInventorySlot::setStack);
                    throw failure;
                }
            }
            return true;
        } catch (RuntimeException invalidOrUnsupportedPattern) {
            return false;
        }
    }

    private KeyCounter[] validateAndCopy(IPatternDetails pattern, KeyCounter[] supplied) {
        IPatternDetails.IInput[] declared = pattern.getInputs();
        if (declared == null || declared.length == 0 || declared.length != supplied.length) return null;
        KeyCounter[] owned = new KeyCounter[supplied.length];
        for (int index = 0; index < supplied.length; index++) {
            KeyCounter counter = supplied[index];
            if (counter == null || counter.size() != 1 || declared[index] == null) return null;
            var entry = counter.iterator().next();
            if (!(entry.getKey() instanceof AEItemKey item) || entry.getLongValue() <= 0
                    || entry.getLongValue() > Integer.MAX_VALUE
                    || !declared[index].isValid(item, tile.getLevel())) return null;
            boolean exact = false;
            for (GenericStack choice : declared[index].getPossibleInputs()) {
                if (choice != null && item.equals(choice.what()) && choice.amount() > 0
                        && Math.multiplyExact(choice.amount(), declared[index].getMultiplier()) == entry.getLongValue()) {
                    exact = true;
                    break;
                }
            }
            if (!exact) return null;
            owned[index] = new KeyCounter();
            owned[index].addAll(counter);
        }
        return owned;
    }

    private static List<PatternStack> outputs(IPatternDetails pattern) {
        List<PatternStack> outputs = new ArrayList<>();
        for (GenericStack output : pattern.getOutputs()) {
            if (output == null) continue;
            if (!(output.what() instanceof AEItemKey item) || output.amount() <= 0
                    || output.amount() > Integer.MAX_VALUE) return List.of();
            outputs.add(new PatternStack(item.toStack(1), output.amount()));
        }
        return List.copyOf(outputs);
    }

    private static long amount(KeyCounter[] supplied) {
        long total = 0;
        for (KeyCounter counter : supplied) {
            for (var entry : counter) total = Math.addExact(total, entry.getLongValue());
        }
        return total;
    }
}
