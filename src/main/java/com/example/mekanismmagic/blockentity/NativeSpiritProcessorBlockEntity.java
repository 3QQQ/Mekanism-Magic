package com.example.mekanismmagic.blockentity;

import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import com.example.mekanismmagic.api.RecipeItemDisplayState;
import com.example.mekanismmagic.integration.common.network.PatternAutomationRefreshHooks;
import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import com.example.mekanismmagic.integration.occultism.OccultismRecipeBridge;
import com.example.mekanismmagic.integration.occultism.OccultismSpiritPatternValidator;
import mekanism.api.IContentsListener;
import mekanism.api.AutomationType;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

public final class NativeSpiritProcessorBlockEntity extends NativeMagicMachineBlockEntity {
    private static final String TRADE_NONCE = "spirit_trade_nonce";
    private static final String TRADE_SALT = "spirit_trade_salt";
    private long spiritTradeNonce;
    private long spiritTradeSalt;
    private long observedSpiritProcessingRevision = Long.MIN_VALUE;
    private boolean activeRandomTrade;

    public NativeSpiritProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(NativeMekanismRegistries.SPIRIT_BLOCK.get().builtInRegistryHolder(), pos, state);
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper, IContentsListener listener) {
        inputSlot = registerLogicalSlot(helper, 0, InputInventorySlot.at(listener, 64, 17));
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 116, 35));
        IContentsListener spiritListener = () -> {
            listener.onContentsChanged();
            com.example.mekanismmagic.integration.common.network
                    .PatternAutomationRefreshHooks.request(this);
        };
        containmentSlot = registerLogicalSlot(helper, CONTAINMENT_SLOT,
                BasicInventorySlot.at(
                        (stack, automation) ->
                                automation == AutomationType.MANUAL
                                        && canRemoveSpiritSource(),
                        (stack, automation) -> true,
                        OccultismRecipeBridge::isSpiritSource,
                        spiritListener, 64, 53));
        var itemConfig = setupNativeItemIO(
                List.of(inputSlot), List.of(outputSlot), List.of());
        addNativeItemSlotInfo(itemConfig, DataType.EXTRA,
                true, false, List.of(containmentSlot));
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        Optional<OccultismRecipeBridge.SpiritMachineRecipe> found =
                OccultismRecipeBridge.findSpiritMachineRecipe(level, inventory,
                inventory.getStackInSlot(CONTAINMENT_SLOT),
                spiritSelectionSeed());
        activeRandomTrade = found.map(
                OccultismRecipeBridge.SpiritMachineRecipe::randomTrade)
                .orElse(false);
        return found.map(OccultismRecipeBridge.SpiritMachineRecipe::recipe);
    }

    /** A spirit source on its own is a configured idle state, not an error. */
    @Override
    protected boolean hasAnyRecipeInput() {
        return inputSlot != null && !inputSlot.getStack().isEmpty()
                && containmentSlot != null
                && !containmentSlot.getStack().isEmpty();
    }

    @Override
    protected int baseEnergyPerTick() {
        return 400;
    }

    @Override
    protected boolean onUpdateServer() {
        refreshSpiritProcessingRevision();
        return super.onUpdateServer();
    }

    @Override
    protected long recipeLookupRevision() {
        return OccultismRecipeBridge.spiritProcessingRevision();
    }

    @Override
    protected ItemStack recipeEntityDisplaySource(
            MachineRecipeResult recipe) {
        return containmentSlot == null
                ? ItemStack.EMPTY : containmentSlot.getStack();
    }

    @Override
    protected int energySlotX() {
        return 39;
    }

    @Override
    protected int energySlotY() {
        return 35;
    }

    @Override
    protected ItemStack getSpiritSourceForUpgrade() {
        return containmentSlot == null ? ItemStack.EMPTY : containmentSlot.getStack();
    }

    @Override
    protected void setSpiritSourceFromUpgrade(ItemStack source) {
        if (containmentSlot != null) {
            containmentSlot.setStack(source.copy());
        }
    }

    @Override
    protected long getSpiritTradeNonceForUpgrade() {
        return spiritTradeNonce;
    }

    @Override
    protected void setSpiritTradeNonceFromUpgrade(long nonce) {
        spiritTradeNonce = nonce;
    }

    @Override
    protected long getSpiritTradeSaltForUpgrade() {
        return spiritTradeSalt;
    }

    @Override
    protected void setSpiritTradeSaltFromUpgrade(long salt) {
        spiritTradeSalt = salt;
    }

    @Override
    public List<mekanism.api.inventory.IInventorySlot>
    mekanismMagicPersistentInputs() {
        return containmentSlot == null ? List.of()
                : List.of(containmentSlot);
    }

    @Override
    public boolean mekanismMagicSupportsPatternAutomation() {
        // Static support owns the ME node and output return path. A gambler
        // only disables deterministic crafting offers, never the node itself.
        return true;
    }

    @Override
    public boolean mekanismMagicCanAdvertisePatterns() {
        return containmentSlot != null
                && !containmentSlot.getStack().isEmpty();
    }

    @Override
    public boolean mekanismMagicUsesContextualPatternValidation() {
        return true;
    }

    @Override
    public boolean mekanismMagicMatchesPattern(
            List<PatternStack> inputs, List<PatternStack> outputs) {
        return containmentSlot != null
                && OccultismSpiritPatternValidator.matches(level,
                containmentSlot.getStack(), inputs, outputs);
    }

    @Override
    public boolean mekanismMagicIsBusy() {
        return super.mekanismMagicIsBusy();
    }

    @Override
    protected void onRecipeFinished(MachineRecipeResult recipe) {
        if (!activeRandomTrade) {
            return;
        }
        spiritTradeNonce = spiritTradeNonce == Long.MAX_VALUE
                ? 0L : spiritTradeNonce + 1L;
        activeRandomTrade = false;
        setChanged();
    }

    @Override
    protected boolean updateRecipeItemDisplay(
            ItemStackHandler inventory, MachineRecipeResult recipe) {
        if (!activeRandomTrade) {
            return super.updateRecipeItemDisplay(inventory, recipe);
        }
        // The weighted result is already selected server-side so the output
        // slot can be validated atomically. Do not transmit that result to the
        // client before completion; only the offered item remains visible.
        ItemStack input = inventory.getStackInSlot(0);
        return mekanismMagicRecipeItemDisplay().update(input.isEmpty()
                ? List.of()
                : List.of(new RecipeItemDisplayState.Entry(
                0, input, ItemStack.EMPTY)));
    }

    @Override
    protected void saveNativeMachineData(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.saveNativeMachineData(tag, registries);
        tag.putLong(TRADE_NONCE, spiritTradeNonce);
        tag.putLong(TRADE_SALT, spiritTradeSalt);
    }

    @Override
    protected void loadNativeMachineData(
            net.minecraft.nbt.CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.loadNativeMachineData(tag, registries);
        spiritTradeNonce = tag.getLong(TRADE_NONCE);
        spiritTradeSalt = tag.getLong(TRADE_SALT);
    }

    private long spiritSelectionSeed() {
        return ensureSpiritTradeSalt()
                ^ Long.rotateLeft(spiritTradeNonce, 29);
    }

    private boolean canRemoveSpiritSource() {
        return progress <= 0
                && (inputSlot == null || inputSlot.getStack().isEmpty())
                && !PatternAutomationRefreshHooks
                .hasPendingPatternWork(this);
    }

    private void refreshSpiritProcessingRevision() {
        long revision = OccultismRecipeBridge.spiritProcessingRevision();
        if (observedSpiritProcessingRevision == revision) {
            return;
        }
        observedSpiritProcessingRevision = revision;
        PatternAutomationRefreshHooks.request(this);
    }

    @Override
    protected boolean permitsExternalStackReplacement(
            mekanism.api.inventory.IInventorySlot slot,
            ItemStack replacement) {
        return slot != containmentSlot
                || PersistentInputMutationGuard.permits(
                slot, replacement);
    }

    private long ensureSpiritTradeSalt() {
        if (spiritTradeSalt != 0L) {
            return spiritTradeSalt;
        }
        spiritTradeSalt = OccultismRecipeBridge.createSpiritTradeSalt();
        setChanged();
        return spiritTradeSalt;
    }
}
