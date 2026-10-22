package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A paid fuel is converted incrementally; stopping or reloading cannot repay it. */
public final class AuraFuelGeneratorBlockEntity extends NatureAuraGeneratorBlockEntity {
    public static final int BASE_AURA_PER_TICK = 1_000;
    private static final String REMAINING = "nature_aura_fuel_remaining";
    private static final String TOTAL = "nature_aura_fuel_total";
    private static final String TYPE = "nature_aura_fuel_type";
    private static final String FLOWERS = "nature_aura_fuel_flowers";

    // Slots are constructed during the superclass constructor.
    private List<IInventorySlot> fuelSlots;
    private final Map<ResourceLocation, Integer> flowerFatigue = new HashMap<>();
    private long remainingFuelAura;
    private long fuelTotalAura;
    private ResourceLocation fuelAuraType;
    private boolean syncedFuelTypeMismatch;
    private boolean converting;

    public AuraFuelGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected EnumSet<TransmissionType> nativeTransmissions() {
        return EnumSet.of(TransmissionType.ITEM);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper, IContentsListener listener) {
        inputSlot = registerLogicalSlot(helper, 0,
                InputInventorySlot.at(NatureAuraFuelCatalog::accepts, listener, 46, 35));
        IInventorySlot second = registerLogicalSlot(helper, 1,
                InputInventorySlot.at(NatureAuraFuelCatalog::accepts, listener, 64, 35));
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 116, 35));
        fuelSlots = List.of(inputSlot, second);
        setupNativeItemIO(fuelSlots, List.of(outputSlot), List.of());
    }

    @Override
    protected int baseEnergyPerTick() {
        return 0;
    }

    @Override
    protected int generateAura(ServerLevel server) {
        if (converting) return 0;
        converting = true;
        try {
            IAuraType localType = getGenerationAuraType();
            if (localType == null) return 0;
            if (remainingFuelAura > 0
                    && !localType.getName().equals(fuelAuraType)) return 0;
            if (!storeAuraExact(1, true)) return 0;
            if (remainingFuelAura <= 0 && !consumeFuel(localType)) {
                setInputDoesntProduceOutputWarning(true);
                return 0;
            }
            int rate = (int) Math.min(64_000L,
                    BASE_AURA_PER_TICK * 20L / Math.max(1, MekanismUtils.getTicks(this, 20)));
            int offered = (int) Math.min(Math.min(remainingFuelAura, rate),
                    getAuraCapacity() - getStoredAura());
            if (offered <= 0) return 0;
            // Commit the debit before storage listeners can observe a credit.
            remainingFuelAura -= offered;
            int stored = storeAuraExact(offered, false) ? offered : 0;
            remainingFuelAura += offered - stored;
            setChanged();
            return stored;
        } finally {
            converting = false;
        }
    }

    private boolean consumeFuel(IAuraType localType) {
        for (int index = 0; index < fuelSlots.size(); index++) {
            IInventorySlot source = fuelSlots.get(index);
            var candidate = NatureAuraFuelCatalog.lookup(source.getStack());
            if (candidate.isEmpty()) continue;
            var fuel = candidate.get();
            if (fuel.overworldOnly() && !localType.isSimilar(NaturesAuraAPI.TYPE_OVERWORLD)) continue;
            IInventorySlot second = fuelSlots.get(1 - index);
            if (!fuel.secondary().isEmpty()
                    && (!second.getStack().is(fuel.secondary().getItem())
                    || second.getStack().getCount() < fuel.secondary().getCount())) continue;
            long quota = fuel.kind() == NatureAuraFuelCatalog.Kind.FLOWER
                    ? Math.max(0, fuel.aura() - 100L * flowerFatigue.getOrDefault(fuel.id(), 0))
                    : fuel.aura();
            if (quota <= 0 || !canFitRemainder(fuel.remainder())) continue;

            ItemStack nextSource = source.getStack().copy();
            nextSource.shrink(1);
            ItemStack nextSecondary = second.getStack().copy();
            if (!fuel.secondary().isEmpty()) nextSecondary.shrink(1);
            ItemStack nextOutput = outputSlot.getStack().copy();
            if (!fuel.remainder().isEmpty()) {
                if (nextOutput.isEmpty()) nextOutput = fuel.remainder().copy();
                else nextOutput.grow(fuel.remainder().getCount());
            }
            // Quota, inputs, and residuals are all internal state. The shared
            // generator only invokes neighboring Aura outputs after this returns.
            remainingFuelAura = quota;
            fuelTotalAura = quota;
            fuelAuraType = localType.getName();
            if (fuel.kind() == NatureAuraFuelCatalog.Kind.FLOWER) {
                flowerFatigue.replaceAll((flower, fatigue) ->
                        flower.equals(fuel.id()) ? fatigue : Math.max(0, fatigue - 1));
                flowerFatigue.values().removeIf(fatigue -> fatigue == 0);
                flowerFatigue.put(fuel.id(), Math.min(250,
                        flowerFatigue.getOrDefault(fuel.id(), 0) + 5));
            }
            source.setStack(nextSource);
            if (!fuel.secondary().isEmpty()) second.setStack(nextSecondary);
            if (!fuel.remainder().isEmpty()) outputSlot.setStack(nextOutput);
            setChanged();
            return true;
        }
        return false;
    }

    private boolean canFitRemainder(ItemStack remainder) {
        if (remainder.isEmpty()) return true;
        ItemStack current = outputSlot.getStack();
        return (current.isEmpty() || ItemStack.isSameItemSameComponents(current, remainder))
                && (long) current.getCount() + remainder.getCount()
                <= Math.min(outputSlot.getLimit(remainder), remainder.getMaxStackSize());
    }

    public long getRemainingFuelAura() {
        return remainingFuelAura;
    }

    public long getFuelTotalAura() {
        return fuelTotalAura;
    }

    @Override
    protected boolean hasPendingGeneration() {
        return remainingFuelAura > 0;
    }

    public boolean hasFuelTypeMismatch() {
        if (level != null && level.isClientSide()) return syncedFuelTypeMismatch;
        if (remainingFuelAura <= 0 || level == null) return false;
        IAuraType localType = getGenerationAuraType();
        return localType == null || !localType.getName().equals(fuelAuraType);
    }

    public int getFlowerFatigue(ResourceLocation flower) {
        return flowerFatigue.getOrDefault(flower, 0);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableLong.create(this::getRemainingFuelAura,
                value -> remainingFuelAura = Math.max(0, value)));
        container.track(SyncableLong.create(this::getFuelTotalAura,
                value -> fuelTotalAura = Math.max(0, value)));
        container.track(SyncableBoolean.create(this::hasFuelTypeMismatch,
                value -> syncedFuelTypeMismatch = value));
    }

    @Override
    protected void saveNatureAuraMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveNatureAuraMachineData(tag, registries);
        tag.putLong(REMAINING, remainingFuelAura);
        tag.putLong(TOTAL, fuelTotalAura);
        if (fuelAuraType != null) tag.putString(TYPE, fuelAuraType.toString());
        CompoundTag flowers = new CompoundTag();
        flowerFatigue.forEach((flower, fatigue) -> flowers.putInt(flower.toString(), fatigue));
        tag.put(FLOWERS, flowers);
    }

    @Override
    protected void loadNatureAuraMachineData(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadNatureAuraMachineData(tag, registries);
        fuelTotalAura = Math.max(0, tag.getLong(TOTAL));
        remainingFuelAura = Math.max(0, Math.min(fuelTotalAura, tag.getLong(REMAINING)));
        fuelAuraType = ResourceLocation.tryParse(tag.getString(TYPE));
        if (fuelAuraType == null) remainingFuelAura = 0;
        flowerFatigue.clear();
        CompoundTag flowers = tag.getCompound(FLOWERS);
        for (String key : flowers.getAllKeys()) {
            ResourceLocation flower = ResourceLocation.tryParse(key);
            int fatigue = Math.max(0, Math.min(250, flowers.getInt(key)));
            if (flower != null && fatigue > 0) {
                var fuel = NatureAuraFuelCatalog.lookup(new ItemStack(BuiltInRegistries.ITEM.get(flower)));
                if (fuel.isPresent() && fuel.get().kind() == NatureAuraFuelCatalog.Kind.FLOWER) {
                    flowerFatigue.put(flower, fatigue);
                }
            }
        }
    }
}
