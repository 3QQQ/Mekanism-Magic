package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.blockentity.NativeMachineUpgradeData;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.tile.component.ITileComponent;
import mekanism.common.tile.interfaces.IRedstoneControl;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Upgrade payload shared by the compact altar processor and every base
 * Mekanism altar-factory tier.
 */
public final class NatureAltarFactoryUpgradeData extends NativeMachineUpgradeData {
    private final NatureAuraStorageComponent.Snapshot auraSnapshot;
    private final List<ItemStack> catalysts;
    private final ResourceLocation preferredRecipe;
    private final ResourceLocation[] lockedRecipes;
    private final String[] lockedRecipeSignatures;
    private final int[] contextRemaining;
    private final int[] requiredTicks;

    public NatureAltarFactoryUpgradeData(
            HolderLookup.Provider registries,
            boolean redstone,
            IRedstoneControl.RedstoneControl controlType,
            IEnergyContainer energyContainer,
            int[] progress,
            EnergyInventorySlot energySlot,
            List<IInventorySlot> inputSlots,
            List<IInventorySlot> outputSlots,
            boolean sorting,
            List<ITileComponent> components,
            NatureAuraStorageComponent.Snapshot auraSnapshot,
            List<ItemStack> catalysts,
            ResourceLocation preferredRecipe,
            ResourceLocation[] lockedRecipes,
            String[] lockedRecipeSignatures,
            int[] contextRemaining,
            int[] requiredTicks) {
        super(registries, redstone, controlType, energyContainer, progress,
                energySlot, inputSlots, outputSlots, sorting, components);
        this.auraSnapshot = Objects.requireNonNull(
                auraSnapshot, "auraSnapshot");
        this.catalysts = catalysts == null ? List.of()
                : catalysts.stream().map(stack -> stack == null
                        ? ItemStack.EMPTY : stack.copy()).toList();
        this.preferredRecipe = preferredRecipe;
        this.lockedRecipes = lockedRecipes == null
                ? new ResourceLocation[0]
                : Arrays.copyOf(lockedRecipes, lockedRecipes.length);
        this.lockedRecipeSignatures = lockedRecipeSignatures == null
                ? new String[0]
                : Arrays.copyOf(lockedRecipeSignatures,
                lockedRecipeSignatures.length);
        for (int index = 0; index < this.lockedRecipeSignatures.length;
                index++) {
            if (this.lockedRecipeSignatures[index] == null) {
                this.lockedRecipeSignatures[index] = "";
            }
        }
        this.contextRemaining = contextRemaining == null
                ? new int[0]
                : Arrays.copyOf(contextRemaining,
                contextRemaining.length);
        for (int index = 0; index < this.contextRemaining.length; index++) {
            this.contextRemaining[index] = Math.max(0,
                    this.contextRemaining[index]);
        }
        this.requiredTicks = requiredTicks == null
                ? new int[0]
                : Arrays.copyOf(requiredTicks, requiredTicks.length);
    }

    public NatureAuraStorageComponent.Snapshot auraSnapshot() {
        return auraSnapshot;
    }

    public NatureAuraStorageComponent.Snapshot aura() {
        return auraSnapshot;
    }

    public ItemStack catalyst() {
        return catalyst(0);
    }

    public ItemStack catalyst(int index) {
        return index < 0 || index >= catalysts.size()
                ? ItemStack.EMPTY : catalysts.get(index).copy();
    }

    public List<ItemStack> catalysts() {
        return catalysts.stream().map(ItemStack::copy).toList();
    }

    public @Nullable ResourceLocation preferredRecipe() {
        return preferredRecipe;
    }

    public @Nullable ResourceLocation lockedRecipe(int process) {
        return process < 0 || process >= lockedRecipes.length
                ? null : lockedRecipes[process];
    }

    public ResourceLocation[] lockedRecipes() {
        return Arrays.copyOf(lockedRecipes, lockedRecipes.length);
    }

    public String lockedRecipeSignature(int process) {
        return process < 0 || process >= lockedRecipeSignatures.length
                || lockedRecipeSignatures[process] == null
                ? "" : lockedRecipeSignatures[process];
    }

    public String[] lockedRecipeSignatures() {
        return Arrays.copyOf(lockedRecipeSignatures,
                lockedRecipeSignatures.length);
    }

    public int contextRemaining(int process) {
        return process < 0 || process >= contextRemaining.length
                ? 0 : Math.max(0, contextRemaining[process]);
    }

    public int[] contextRemaining() {
        return Arrays.copyOf(contextRemaining,
                contextRemaining.length);
    }

    public int requiredTicks(int process) {
        return process < 0 || process >= requiredTicks.length
                ? 1 : Math.max(1, requiredTicks[process]);
    }

    public int[] requiredTicks() {
        return Arrays.copyOf(requiredTicks, requiredTicks.length);
    }
}
