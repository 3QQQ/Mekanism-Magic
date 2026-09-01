package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.integration.common.recipe.MachineRecipeResult;
import mekanism.api.IContentsListener;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.upgrade.IUpgradeData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import mekanism.common.tile.component.config.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mekanism implementation of Ars Nouveau imbuement recipes.
 */
public final class ImbuementProcessorBlockEntity
        extends ArsSourceMachineBlockEntity
        implements CatalystIdentifierSelectionHost {
    public static final int REAGENT_SLOT = 0;
    private List<BasicInventorySlot> catalystLibrarySlots;
    private boolean catalystLibraryOpen;
    private int selectedCatalystIndex = -1;
    private int catalystPage;
    private boolean virtualCatalystSelected;
    private int virtualCatalystIndex = -1;
    private String virtualCatalystId = "";
    private ItemStack cachedVirtualCatalyst = ItemStack.EMPTY;
    private String cachedVirtualCatalystId = "";
    private int cachedVirtualCatalystIndex = Integer.MIN_VALUE;
    private long cachedCatalystCatalogVersion = Long.MIN_VALUE;

    public ImbuementProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get()
                .builtInRegistryHolder(), pos, state);
    }

    @Override
    protected int sourceInteractionRadius() {
        return ArsNouveauMachineConfig
                .IMBUEMENT_SOURCE_INTERACTION_RADIUS;
    }

    @Override
    protected void createMachineSlots(InventorySlotHelper helper,
                                      IContentsListener listener) {
        inputSlot = registerLogicalSlot(helper, REAGENT_SLOT,
                InputInventorySlot.at(listener, 64, 17));
        catalystLibrarySlots = new ArrayList<>();
        IContentsListener libraryListener = () -> {
            listener.onContentsChanged();
            setChanged();
        };
        for (int index = 0; index < CATALYST_LIBRARY_SLOT_COUNT; index++) {
            int pageSlot = index % 16;
            catalystLibrarySlots.add(registerLogicalSlot(helper,
                    CATALYST_LIBRARY_SLOT_START + index,
                    new CatalystLibrarySlot(this, index / 16,
                            libraryListener,
                            CatalystLibraryLayout.slotX(176, pageSlot),
                            CatalystLibraryLayout.slotY(pageSlot))));
        }
        outputSlot = registerLogicalSlot(helper, OUTPUT_SLOT,
                OutputInventorySlot.at(listener, 116, 35));
        var itemConfig = setupArsItemIO(
                List.of(inputSlot), List.of(outputSlot), List.of());
    }

    @Override
    protected Optional<MachineRecipeResult> findRecipe(
            ItemStackHandler inventory) {
        ResourceLocation patternRecipe = ArsNouveauRecipeBridge
                .patternRecipe(inventory.getStackInSlot(REAGENT_SLOT));
        if (patternRecipe != null) {
            selectCatalystIdentifierForRecipe(patternRecipe);
        }
        ItemStack selected = selectedCatalystIdentifier();
        return ArsNouveauRecipeBridge.findImbuementByIdentifier(
                level, inventory, REAGENT_SLOT, selected);
    }

    @Override
    protected int baseEnergyPerTick() {
        return 600;
    }

    @Override
    protected int energySlotX() {
        return 39;
    }

    @Override
    protected int energySlotY() {
        return 35;
    }

    void seedDevelopmentTest(boolean electric) {
        inputSlot.setStack(new net.minecraft.world.item.ItemStack(
                Items.AMETHYST_SHARD));
        setSource(electric && hasCreativeSourceUpgrade() ? 0 : getMaxSource());
        if (energyContainer != null) {
            energyContainer.setEnergy(energyContainer.getMaxEnergy());
        }
    }

    @Override
    public List<IInventorySlot> mekanismMagicPatternInputs() {
        return inputSlot == null ? List.of() : List.of(inputSlot);
    }

    @Override
    public List<IInventorySlot> mekanismMagicPersistentInputs() {
        return catalystLibrarySlots == null
                ? List.of() : List.copyOf(catalystLibrarySlots);
    }

    @Override
    public List<IInventorySlot> mekanismMagicManualOnlySlots() {
        return catalystLibrarySlots == null
                ? List.of() : List.copyOf(catalystLibrarySlots);
    }

    @Override
    public IUpgradeData getUpgradeData(
            net.minecraft.core.HolderLookup.Provider registries) {
        List<ItemStack> catalysts = catalystLibrarySlots == null
                ? List.of() : catalystLibrarySlots.stream()
                .map(IInventorySlot::getStack)
                .map(ItemStack::copy)
                .toList();
        return new ImbuementFactoryUpgradeData(registries, redstone,
                getControlType(), energyContainer, new int[]{progress},
                energySlot,
                inputSlot == null ? List.of() : List.of(inputSlot),
                outputSlot == null ? List.of() : List.of(outputSlot),
                false, getComponents(), getSource(),
                selectedCatalystIdentifier(), catalysts,
                selectedCatalystIndex, virtualCatalystSelected,
                virtualCatalystId, catalystLibraryOpen, catalystPage,
                new int[]{Math.max(1, progressRequired)},
                sourceModesForUpgrade(), sourceLinksForUpgrade());
    }

    public ItemStack selectedCatalystIdentifier() {
        if (virtualCatalystSelected && level != null) {
            long version = ArsNouveauRecipeScanner.version(
                    level.getRecipeManager());
            if (version != cachedCatalystCatalogVersion
                    || level.isClientSide()
                    && virtualCatalystIndex != cachedVirtualCatalystIndex
                    || !level.isClientSide()
                    && !virtualCatalystId.equals(cachedVirtualCatalystId)) {
                String requestedId = level.isClientSide()
                        ? catalystIdAtVirtualIndex() : virtualCatalystId;
                cachedVirtualCatalyst =
                        ArsNouveauRecipeBridge.catalystIdentifierJeiStack(
                                level, requestedId);
                cachedVirtualCatalystId = requestedId;
                cachedVirtualCatalystIndex = virtualCatalystIndex;
                cachedCatalystCatalogVersion = version;
            }
            return cachedVirtualCatalyst;
        }
        if (catalystLibrarySlots == null
                || selectedCatalystIndex < 0
                || selectedCatalystIndex >= catalystLibrarySlots.size()) {
            return ItemStack.EMPTY;
        }
        return catalystLibrarySlots.get(selectedCatalystIndex).getStack();
    }

    public int selectedCatalystIndex() {
        return selectedCatalystIndex;
    }

    public int catalystPage() {
        return catalystPage;
    }

    public int catalystPageCount() {
        return Math.max(1, Math.ceilDiv(
                CATALYST_LIBRARY_SLOT_COUNT, 16));
    }

    public ItemStack catalystPageStack(int pageSlot) {
        int index = catalystPage * 16 + pageSlot;
        return index >= 0 && index < catalystLibrarySlots.size()
                ? catalystLibrarySlots.get(index).getStack()
                : ItemStack.EMPTY;
    }

    public void cycleCatalystPage(int delta) {
        catalystPage = Math.floorMod(catalystPage + delta,
                catalystPageCount());
        setChanged();
    }

    public void selectCatalystIdentifier(int index) {
        if (catalystLibrarySlots == null || index < 0
                || index >= catalystLibrarySlots.size()) {
            return;
        }
        if (!catalystLibrarySlots.get(index).getStack().isEmpty()) {
            selectedCatalystIndex = index;
            virtualCatalystSelected = false;
            virtualCatalystIndex = -1;
            virtualCatalystId = "";
            cachedVirtualCatalyst = ItemStack.EMPTY;
            setChanged();
        }
    }

    public boolean selectCatalystIdentifierId(String id) {
        int index = ArsNouveauRecipeBridge.catalystIdentifierJeiIndex(
                level, id);
        if (index >= 0) {
            ItemStack canonical = ArsNouveauRecipeBridge
                    .catalystIdentifierJeiStack(level, index);
            String canonicalId = canonical.isEmpty() ? id
                    : CatalystIdentifierItem.catalystId(
                            canonical).toString();
            if (virtualCatalystSelected
                    && canonicalId.equals(virtualCatalystId)) {
                return true;
            }
            virtualCatalystSelected = true;
            virtualCatalystIndex = index;
            virtualCatalystId = canonicalId;
            cachedVirtualCatalyst = ItemStack.EMPTY;
            cachedCatalystCatalogVersion = Long.MIN_VALUE;
            setChanged();
            return true;
        }
        return false;
    }

    public void selectCatalystIdentifierRecipe(int index) {
        ItemStack identifier = ArsNouveauRecipeBridge
                .catalystIdentifierJeiStack(level, index);
        if (!identifier.isEmpty()) {
            selectCatalystIdentifierId(CatalystIdentifierItem
                    .catalystId(identifier).toString());
        }
    }

    public void clearCatalystIdentifierSelection() {
        virtualCatalystSelected = false;
        virtualCatalystIndex = -1;
        virtualCatalystId = "";
        selectedCatalystIndex = -1;
        cachedVirtualCatalyst = ItemStack.EMPTY;
        cachedCatalystCatalogVersion = Long.MIN_VALUE;
        setChanged();
    }

    public int catalystIdentifierRecipeCount() {
        return level == null ? 0 : ArsNouveauRecipeBridge
                .catalystIdentifierJeiRecipes(level).size();
    }

    private String catalystIdAtVirtualIndex() {
        return ArsNouveauRecipeBridge.catalystIdentifierJeiId(
                level, virtualCatalystIndex);
    }

    private int trackedVirtualCatalystIndex() {
        if (level != null && !level.isClientSide()
                && virtualCatalystSelected
                && !virtualCatalystId.isBlank()) {
            virtualCatalystIndex = ArsNouveauRecipeBridge
                    .catalystIdentifierJeiIndex(level, virtualCatalystId);
        }
        return virtualCatalystIndex;
    }

    public int catalystIdentifierIndex(String id) {
        if (catalystLibrarySlots == null || id == null || id.isEmpty()) {
            return -1;
        }
        for (int index = 0; index < catalystLibrarySlots.size(); index++) {
            var data = catalystLibrarySlots.get(index).getStack()
                    .get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            if (data != null && id.equals(data.getUnsafe()
                    .getString("catalyst_id"))) {
                return index;
            }
        }
        return -1;
    }

    public void setCatalystLibraryOpen(boolean open) {
        catalystLibraryOpen = open;
    }

    public boolean isCatalystLibrarySlotActive() {
        return level == null || !level.isClientSide() || catalystLibraryOpen;
    }

    @Override
    public void addContainerTrackers(mekanism.common.inventory.container.MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(mekanism.common.inventory.container.sync.SyncableInt.create(
                () -> selectedCatalystIndex,
                value -> selectedCatalystIndex = Math.max(-1, value)));
        container.track(mekanism.common.inventory.container.sync.SyncableInt.create(
                () -> catalystPage,
                value -> catalystPage = Math.max(0, Math.min(
                        catalystPageCount() - 1, value))));
        container.track(mekanism.common.inventory.container.sync.SyncableBoolean
                .create(() -> virtualCatalystSelected,
                        value -> virtualCatalystSelected = value));
        container.track(mekanism.common.inventory.container.sync.SyncableInt
                .create(this::trackedVirtualCatalystIndex,
                        value -> virtualCatalystIndex = Math.max(-1, value)));
    }

    @Override
    protected void saveArsMachineData(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        tag.putInt("catalyst_selected_index", selectedCatalystIndex);
        tag.putInt("catalyst_page", catalystPage);
        tag.putBoolean("virtual_catalyst_selected",
                virtualCatalystSelected);
        tag.putString("virtual_catalyst_id", virtualCatalystId);
    }

    @Override
    protected void loadArsMachineData(
            CompoundTag tag,
            net.minecraft.core.HolderLookup.Provider registries) {
        selectedCatalystIndex = tag.contains("catalyst_selected_index")
                ? Math.max(-1, tag.getInt("catalyst_selected_index")) : -1;
        catalystPage = Math.max(0, Math.min(
                catalystPageCount() - 1, tag.getInt("catalyst_page")));
        virtualCatalystSelected = tag.getBoolean(
                "virtual_catalyst_selected");
        virtualCatalystId = tag.getString("virtual_catalyst_id");
        if (virtualCatalystId.isBlank()) {
            virtualCatalystSelected = false;
        }
        virtualCatalystIndex = -1;
        cachedVirtualCatalyst = ItemStack.EMPTY;
        cachedCatalystCatalogVersion = Long.MIN_VALUE;
    }

    private static final class CatalystLibrarySlot
            extends BasicInventorySlot {
        private final ImbuementProcessorBlockEntity tile;
        private final int page;
        private final int x;
        private final int y;

        private CatalystLibrarySlot(ImbuementProcessorBlockEntity tile,
                                    int page, IContentsListener listener,
                                    int x, int y) {
            super((stack, automation) -> true,
                    (stack, automation) -> true,
                    stack -> stack.is(
                            ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get()),
                    listener, x, y);
            this.tile = tile;
            this.page = page;
            this.x = x;
            this.y = y;
            setSlotType(ContainerSlotType.INPUT);
        }

        @Override
        public InventoryContainerSlot createContainerSlot() {
            return new InventoryContainerSlot(this, x, y, getSlotType(),
                    getSlotOverlay(), warning -> {
                    }, this::setStackUnchecked) {
                @Override
                public boolean isActive() {
                    return tile.isCatalystLibrarySlotActive()
                            && tile.catalystPage() == page;
                }
            };
        }
    }
}
