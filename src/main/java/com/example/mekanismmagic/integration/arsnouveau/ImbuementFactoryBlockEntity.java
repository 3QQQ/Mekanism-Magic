package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import mekanism.api.Upgrade;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.api.source.SourceProvider;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.AutomationType;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.OneInputCachedRecipe;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.tile.factory.TileEntityItemToItemFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Set;
import java.util.EnumMap;

public final class ImbuementFactoryBlockEntity
        extends TileEntityItemToItemFactory<ImbuementFactoryRecipe>
        implements IMekanismMagicAutomation, ISourceTile, ArsSourceModeHost {
    private BasicInventorySlot lockSlot;
    private int[] requiredTicks;
    private SourceStorage sourceStorage;
    private SourceProvider sourceProvider;
    private final EnumMap<Direction, ArsSourceMachineBlockEntity.SourceMode>
            sourceModes = new EnumMap<>(Direction.class);

    public ImbuementFactoryBlockEntity(Holder<Block> block, BlockPos pos,
                                       BlockState state) {
        super(block, pos, state,
                List.of(CachedRecipe.OperationTracker.RecipeError
                                .NOT_ENOUGH_INPUT,
                        CachedRecipe.OperationTracker.RecipeError
                                .NOT_ENOUGH_OUTPUT_SPACE,
                        CachedRecipe.OperationTracker.RecipeError
                                .INPUT_DOESNT_PRODUCE_OUTPUT),
                Set.of(CachedRecipe.OperationTracker.RecipeError
                        .NOT_ENOUGH_ENERGY));
        requiredTicks = new int[tier.processes];
        for (Direction direction : Direction.values()) {
            sourceModes.put(direction,
                    ArsSourceMachineBlockEntity.SourceMode.INPUT_OUTPUT);
        }
    }

    @Override
    protected void addSlots(InventorySlotHelper helper,
                            mekanism.api.IContentsListener listener,
                            mekanism.api.IContentsListener recipeListener) {
        super.addSlots(helper, listener, recipeListener);
        lockSlot = new ManualLockSlot(
                (stack, automation) -> automation == AutomationType.MANUAL,
                (stack, automation) -> automation == AutomationType.MANUAL,
                stack -> stack.is(
                        ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get()),
                recipeListener, 7, 35);
        lockSlot.setSlotType(ContainerSlotType.EXTRA);
        helper.addSlot(lockSlot);
        requiredTicks = new int[tier.processes];
    }

    @Override
    protected IInventorySlot getExtraSlot() {
        return lockSlot;
    }

    @Override
    protected ImbuementFactoryRecipe findRecipe(
            int process, ItemStack input, IInventorySlot extra,
            IInventorySlot output) {
        if (input.isEmpty() || extra == null || extra.getStack().isEmpty()) {
            return null;
        }
        ItemStackHandler inventory = new ItemStackHandler(23);
        inventory.setStackInSlot(0, input.copy());
        return ArsNouveauRecipeBridge.findImbuementByIdentifier(
                        level, inventory, 0, extra.getStack())
                .map(result -> new ImbuementFactoryRecipe(
                        input, extra.getStack(), result))
                .orElse(null);
    }

    private SourceStorage sourceStorage() {
        if (sourceStorage == null) {
            sourceStorage = new SourceStorage(
                    ArsNouveauMachineConfig.SOURCE_CAPACITY,
                    ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE,
                    ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE) {
                @Override
                public void onContentsChanged() {
                    setChanged();
                }
            };
        }
        return sourceStorage;
    }

    public SourceStorage getSourceStorage() {
        return sourceStorage();
    }

    @Override
    public ArsSourceMachineBlockEntity.SourceMode getSourceMode(
            Direction side) {
        return sourceModes.getOrDefault(side,
                ArsSourceMachineBlockEntity.SourceMode.NONE);
    }

    @Override
    public void cycleSourceMode(int index) {
        Direction[] directions = Direction.values();
        if (index < 0 || index >= directions.length) {
            return;
        }
        Direction side = directions[index];
        sourceModes.put(side, getSourceMode(side).next());
        setChanged();
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    public SourceStorage getSourceStorage(Direction side) {
        return getSourceMode(side)
                == ArsSourceMachineBlockEntity.SourceMode.NONE
                ? null : getSourceStorage();
    }

    private boolean hasCreativeUpgrade() {
        Upgrade upgrade = ArsNouveauRegistries.creativeSourceUpgrade();
        return upgrade != null && upgradeComponent != null
                && upgradeComponent.getUpgrades(upgrade) > 0;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide() && sourceProvider == null) {
            sourceProvider = new SourceProvider(this, worldPosition) {
                @Override
                public boolean isValid() {
                    return !ImbuementFactoryBlockEntity.this.isRemoved()
                            && ImbuementFactoryBlockEntity.this.level != null
                            && ImbuementFactoryBlockEntity.this.level
                            .getBlockEntity(worldPosition)
                            == ImbuementFactoryBlockEntity.this;
                }
            };
            SourceManager.INSTANCE.addInterface(level, sourceProvider);
        }
    }

    @Override
    protected ImbuementFactoryRecipe getRecipeForInput(
            int process, ItemStack input, IInventorySlot output,
            IInventorySlot secondaryOutput, boolean recheck) {
        return findRecipe(process, input, lockSlot, output);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public IMekanismRecipeTypeProvider getRecipeType() {
        return (IMekanismRecipeTypeProvider) MekanismRecipeType.SMELTING;
    }

    @Override
    public FactoryType getFactoryType() {
        return FactoryType.SMELTING;
    }

    @Override
    protected int getNeededInput(ImbuementFactoryRecipe recipe,
                                 ItemStack input) {
        return 1;
    }

    @Override
    protected boolean isCachedRecipeValid(
            CachedRecipe<ImbuementFactoryRecipe> cached, ItemStack input) {
        return cached != null && cached.getRecipe() != null
                && cached.getRecipe().test(input)
                && lockSlot != null
                && cached.getRecipe().sameIdentifier(lockSlot.getStack());
    }

    @Override
    public boolean isItemValidForSlot(ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public boolean isValidInputItem(ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public ImbuementFactoryRecipe getRecipe(int process) {
        return process < 0 || process >= inputSlots.size() ? null
                : findRecipe(process, inputSlots.get(process).getStack(),
                        lockSlot, outputSlots.get(process));
    }

    @Override
    public CachedRecipe<ImbuementFactoryRecipe> createNewCachedRecipe(
            ImbuementFactoryRecipe recipe, int process) {
        requiredTicks[process] = Math.max(1,
                mekanism.common.util.MekanismUtils.getTicks(this,
                        recipe.duration()));
        @SuppressWarnings({"rawtypes", "unchecked"})
        CachedRecipe<ImbuementFactoryRecipe> cached =
                (CachedRecipe) OneInputCachedRecipe.itemToItem(recipe,
                                () -> true, inputHandlers[process],
                                outputHandlers[process])
                        .setCanHolderFunction(() -> lockSlot != null
                                && recipe.sameIdentifier(lockSlot.getStack())
                                && (hasCreativeUpgrade() || getSource() >= recipe
                                .sourceCost()))
                        .setActive(active -> setActiveState(active, process))
                        .setEnergyRequirements(
                                () -> mekanism.common.util.MekanismUtils
                                        .getEnergyPerTick(this,
                                                recipeEnergyUsage(recipe)),
                                energyContainer)
                        .setRequiredTicks(() -> requiredTicks[process])
                        .setOperatingTicksChanged(value ->
                                progress[process] = value)
                        .setBaselineMaxOperations(() -> 1)
                        .setOnFinish(() -> {
                            if (!hasCreativeUpgrade()
                                    && recipe.sourceCost() > 0) {
                                removeSource(recipe.sourceCost());
                            }
                        });
        return cached;
    }

    private long recipeEnergyUsage(ImbuementFactoryRecipe recipe) {
        if (!hasCreativeUpgrade() || recipe.sourceCost() <= 0) {
            return 600L;
        }
        return 600L + Math.max(1L, Math.ceilDiv(
                recipe.sourceCost() * ArsNouveauMachineConfig.FE_PER_SOURCE,
                Math.max(1, recipe.duration())));
    }

    @Override
    public double getScaledProgress(int scale, int process) {
        return process < requiredTicks.length && requiredTicks[process] > 0
                ? progress[process] * (double) scale / requiredTicks[process]
                : 0;
    }

    @Override
    public ResourceLocation mekanismMagicMachineId() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(getBlockState().getBlock());
    }

    @Override
    public List<IInventorySlot> mekanismMagicPatternInputs() {
        return inputSlots == null ? List.of() : List.copyOf(inputSlots);
    }

    @Override
    public List<IInventorySlot> mekanismMagicPatternOutputs() {
        return outputSlots == null ? List.of() : List.copyOf(outputSlots);
    }

    @Override
    public List<IInventorySlot> mekanismMagicPersistentInputs() {
        return List.of();
    }

    @Override
    public List<IInventorySlot> mekanismMagicManualOnlySlots() {
        return lockSlot == null ? List.of() : List.of(lockSlot);
    }

    @Override
    public boolean mekanismMagicIsBusy() {
        for (int value : progress) {
            if (value > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public mekanism.api.energy.IEnergyContainer
    mekanismMagicEnergyContainer() {
        return energyContainer;
    }

    @Override
    public int getTransferRate() {
        return ArsNouveauMachineConfig.SOURCE_TRANSFER_RATE;
    }

    @Override
    public boolean canAcceptSource() {
        return getSource() < getMaxSource();
    }

    @Override
    public boolean canProvideSource() {
        return getSource() > 0;
    }

    @Override
    public int getSource() {
        return sourceStorage().getSource();
    }

    @Override
    public int getMaxSource() {
        return sourceStorage().getSourceCapacity();
    }

    @Override
    public int setSource(int amount) {
        sourceStorage().setSource(amount);
        return getSource();
    }

    @Override
    public int addSource(int amount) {
        return setSource(getSource() + amount);
    }

    @Override
    public int removeSource(int amount) {
        return setSource(getSource() - amount);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        for (Direction direction : Direction.values()) {
            container.track(SyncableInt.create(
                    () -> getSourceMode(direction).ordinal(),
                    value -> sourceModes.put(direction,
                            ArsSourceMachineBlockEntity.SourceMode.values()[
                                    Math.max(0, Math.min(
                                            ArsSourceMachineBlockEntity.SourceMode
                                                    .values().length - 1,
                                            value))])));
        }
        for (int process = 0; process < tier.processes; process++) {
            int index = process;
            container.track(SyncableInt.create(
                    () -> index < requiredTicks.length
                            ? requiredTicks[index] : 0,
                    value -> {
                        if (index < requiredTicks.length) {
                            requiredTicks[index] = Math.max(1, value);
                        }
                    }));
        }
        container.track(SyncableInt.create(this::getSource, this::setSource));
    }

    @Override
    public void saveAdditional(CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ars_source", getSource());
        CompoundTag modes = new CompoundTag();
        for (Direction direction : Direction.values()) {
            modes.putInt(direction.getName(), getSourceMode(direction).ordinal());
        }
        tag.put("ars_source_modes", modes);
    }

    @Override
    public void loadAdditional(CompoundTag tag,
                               net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        setSource(tag.getInt("ars_source"));
        CompoundTag modes = tag.getCompound("ars_source_modes");
        for (Direction direction : Direction.values()) {
            if (modes.contains(direction.getName())) {
                int value = Math.max(0, Math.min(
                        ArsSourceMachineBlockEntity.SourceMode.values().length - 1,
                        modes.getInt(direction.getName())));
                sourceModes.put(direction,
                        ArsSourceMachineBlockEntity.SourceMode.values()[value]);
            }
        }
    }

    private static final class ManualLockSlot extends BasicInventorySlot {
        private ManualLockSlot(
                java.util.function.BiPredicate<ItemStack, AutomationType> insert,
                java.util.function.BiPredicate<ItemStack, AutomationType> extract,
                java.util.function.Predicate<ItemStack> validator,
                mekanism.api.IContentsListener listener, int x, int y) {
            super(insert, extract, validator, listener, x, y);
        }
    }
}
