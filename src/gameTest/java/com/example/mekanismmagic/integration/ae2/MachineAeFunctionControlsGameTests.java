package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.networking.EnergyCellBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.beipuo.mekenergistics.upgrade.MeUpgradeRecipeMachineAdapter;
import com.beipuo.mekenergistics.upgrade.MeUpgradeType;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.blockentity.NativeMagicMachineBlockEntity;
import com.example.mekanismmagic.integration.ModCompatibility;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.ae2.storage.MagicStorageCells;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.FeSourceConverterBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.common.network.MachineDirectOutputHooks;
import com.example.mekanismmagic.integration.forbiddenarcanus.EssenceCondenserBlockEntity;
import com.example.mekanismmagic.integration.forbiddenarcanus.ForbiddenArcanusRegistries;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusForgeProcessorBlockEntity;
import com.example.mekanismmagic.integration.mekextras.MekanismExtrasImbuementFactories;
import com.example.mekanismmagic.integration.naturesaura.AuraFuelGeneratorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import mekanism.api.inventory.IInventorySlot;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks.*;

/** Function switches keep the existing powered AE node, paid balances and crafting service. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineAeFunctionControlsGameTests {
    private static final BlockPos MACHINE = new BlockPos(1, 1, 2);
    private static final BlockPos ENERGY_POS = new BlockPos(2, 1, 2);
    private static final BlockPos DRIVE = new BlockPos(2, 2, 2);
    private static final BlockPos RESTORED = new BlockPos(4, 1, 2);
    private static final int INPUT_ALL = ENERGY | SOURCE | AURA | ESSENCE_MASK;
    private static final int OUTPUT_ALL = SOURCE | AURA | ESSENCE_MASK | OUTPUT_ITEMS | OUTPUT_FLUIDS;

    private MachineAeFunctionControlsGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void defaultsAndLegacySupplyFalseKeepIndependentOutputEnabled(GameTestHelper helper) {
        helper.setBlock(MACHINE, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        ImbuementProcessorBlockEntity machine = helper.getBlockEntity(MACHINE);
        var defaults = controls(machine);
        helper.assertTrue(defaults.inputMaster() && defaults.outputMaster()
                        && defaults.configuredInputMask() == INPUT_ALL && defaults.configuredOutputMask() == OUTPUT_ALL
                        && defaults.inputAvailableMask() == (ENERGY | SOURCE)
                        && defaults.outputAvailableMask() == OUTPUT_ITEMS,
                "New machine controls did not default all configured functions on with direction-specific availability");
        CompoundTag legacy = new CompoundTag();
        legacy.putBoolean("magic_native_ae_supply_enabled", false);
        MachineAeResourceHooks.load(machine, legacy, helper.getLevel().registryAccess());
        var migrated = controls(machine);
        helper.assertTrue(!migrated.inputMaster() && migrated.outputMaster()
                        && migrated.configuredInputMask() == INPUT_ALL && migrated.configuredOutputMask() == OUTPUT_ALL
                        && !isInputEnabled(machine, ENERGY) && !isInputEnabled(machine, SOURCE)
                        && isOutputEnabled(machine, OUTPUT_ITEMS),
                "Legacy supply=false disabled output or lost default per-function configuration");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void invalidAndUnsupportedFunctionButtonsCannotChangeConfiguration(GameTestHelper helper) {
        helper.setBlock(MACHINE, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        ImbuementProcessorBlockEntity machine = helper.getBlockEntity(MACHINE);
        var before = controls(machine);
        for (int button : new int[]{-1, 0, 9350, 9352, 9354, 9367, 9378, Integer.MAX_VALUE,
                buttonIdForInput(AURA), buttonIdForOutput(SOURCE), buttonIdForOutput(OUTPUT_FLUIDS)}) {
            helper.assertTrue(!toggleFunction(machine, button) && controls(machine).equals(before),
                    "Invalid or unsupported direction/function button mutated controls: " + button);
        }
        helper.assertTrue(buttonIdForInput(ENERGY | SOURCE) < 0 && buttonIdForOutput(ENERGY) < 0,
                "A combined/unsupported resource bit aliased a real function button");
        machine.setRemoved();
        helper.assertTrue(!toggleFunction(machine, TOGGLE_BUTTON_ID) && !toggleFunction(machine, OUTPUT_MASTER_BUTTON_ID),
                "A removed machine accepted a function-control mutation");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void separateFunctionChoicesSurviveRepeatedLoadDropAndFactoryUpgrade(GameTestHelper helper) {
        Block block = ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get();
        helper.setBlock(MACHINE, block);
        TileEntityMekanism machine = helper.getBlockEntity(MACHINE);
        flip(helper, machine, buttonIdForInput(SOURCE));
        flip(helper, machine, buttonIdForOutput(OUTPUT_ITEMS));
        flip(helper, machine, TOGGLE_BUTTON_ID);
        flip(helper, machine, OUTPUT_MASTER_BUTTON_ID);
        var expected = controls(machine);
        CompoundTag saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        machine.loadWithComponents(saved.copy(), helper.getLevel().registryAccess());
        machine.loadWithComponents(saved.copy(), helper.getLevel().registryAccess());
        assertChoices(helper, machine, expected.configuredInputMask(), expected.configuredOutputMask());
        machine.setRemoved();
        BlockPos absolute = helper.absolutePos(MACHINE);
        var drops = new ArrayList<ItemEntity>();
        drops.add(new ItemEntity(helper.getLevel(), absolute.getX(), absolute.getY(), absolute.getZ(), new ItemStack(block, 2)));
        NeoForge.EVENT_BUS.post(new BlockDropsEvent(helper.getLevel(), absolute, machine.getBlockState(), machine,
                drops, null, ItemStack.EMPTY));
        var stateful = drops.stream().map(ItemEntity::getItem).filter(stack -> stack.has(DataComponents.BLOCK_ENTITY_DATA)).toList();
        helper.assertTrue(stateful.size() == 1 && stateful.getFirst().getCount() == 1,
                "Function configuration was cloned to more than one dropped machine");
        ItemStack item = ItemStack.parse(helper.getLevel().registryAccess(),
                stateful.getFirst().save(helper.getLevel().registryAccess())).orElseThrow();
        helper.setBlock(MACHINE, Blocks.AIR);
        helper.setBlock(RESTORED, block);
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(helper.getLevel(), null, helper.absolutePos(RESTORED), item),
                "Machine item did not restore its function configuration");
        machine = helper.getBlockEntity(RESTORED);
        machine.applyComponentsFromItemStack(item);
        assertChoices(helper, machine, expected.configuredInputMask(), expected.configuredOutputMask());
        for (Block tier : List.of(ArsNouveauRegistries.BASIC_IMBUEMENT_FACTORY_BLOCK.get(),
                MekanismExtrasImbuementFactories.INFINITE_BLOCK.get())) {
            var upgrade = machine.getUpgradeData(helper.getLevel().registryAccess());
            helper.setBlock(RESTORED, Blocks.AIR);
            helper.setBlock(RESTORED, tier);
            machine = helper.getBlockEntity(RESTORED);
            machine.parseUpgradeData(helper.getLevel().registryAccess(), upgrade);
            machine.parseUpgradeData(helper.getLevel().registryAccess(), upgrade);
            assertChoices(helper, machine, expected.configuredInputMask(), expected.configuredOutputMask());
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void supplyAndReturnSwitchesDoNotRemoveOrDisableNativeCraftingPatterns(GameTestHelper helper) {
        helper.setBlock(MACHINE, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
        ImbuementProcessorBlockEntity machine = helper.getBlockEntity(MACHINE);
        var provider = Ae2ImbuementProvider.forTile(machine);
        var before = provider.getAvailablePatterns();
        helper.assertTrue(!before.isEmpty(), "Real Ars recipe catalog advertised no crafting patterns");
        var definitions = before.stream().map(pattern -> pattern.getDefinition()).toList();
        flip(helper, machine, TOGGLE_BUTTON_ID);
        flip(helper, machine, OUTPUT_MASTER_BUTTON_ID);
        flip(helper, machine, buttonIdForInput(SOURCE));
        flip(helper, machine, buttonIdForOutput(OUTPUT_ITEMS));
        var after = provider.getAvailablePatterns();
        helper.assertTrue(after.size() == before.size(), "Function controls removed native crafting patterns");
        for (int index = 0; index < after.size(); index++) {
            helper.assertTrue(definitions.get(index).equals(after.get(index).getDefinition()),
                    "Function controls rewrote an advertised processing-pattern definition");
        }
        Ae2ImbuementPattern free = after.stream().filter(pattern -> pattern instanceof Ae2ImbuementPattern nativePattern
                        && !nativePattern.requiresCatalystIdentifier()).map(pattern -> (Ae2ImbuementPattern) pattern)
                .findFirst().orElseThrow();
        GenericStack material = free.getInputs()[0].getPossibleInputs()[0];
        KeyCounter counter = new KeyCounter();
        counter.add(material.what(), free.expectedInputAmount());
        helper.assertTrue(provider.pushPattern(free, new KeyCounter[]{counter})
                        && !machine.getNativeInputSlot().isEmpty(),
                "Disabling resource supply/item return also disabled the independent crafting dispatch");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 280)
    public static void sourceAndEnergyInputKindsStopIndependentlyOnTheSameNode(GameTestHelper helper) {
        Network fixture = place(helper, ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get(), magicDisk());
        DrygmySimulatorBlockEntity machine = (DrygmySimulatorBlockEntity) fixture.machine();
        machine.getNativeEnergyContainer().setEnergy(0);
        machine.setSource(0);
        flip(helper, machine, buttonIdForInput(SOURCE));
        AtomicReference<Identity> identity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, MagicResourceKeys.source()))
                .thenExecute(() -> {
                    identity.set(identity(helper, fixture));
                    machine.getNativeEnergyContainer().setEnergy(0);
                    seed(helper, fixture, MagicResourceKeys.source(), 6000);
                }).thenWaitUntil(() -> helper.assertTrue(machine.getNativeEnergyContainer().getEnergy() > 0,
                        "Disabling Source supply also stopped independent FE supply"))
                .thenIdle(5).thenExecute(() -> {
                    helper.assertTrue(machine.getSource() == 0 && stored(fixture, MagicResourceKeys.source()) == 6000,
                            "Disabled Source input withdrew canonical Source from the disk");
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, buttonIdForInput(ENERGY));
                    machine.getNativeEnergyContainer().setEnergy(0);
                    flip(helper, machine, buttonIdForInput(SOURCE));
                }).thenWaitUntil(() -> helper.assertTrue(machine.getSource() == 6000,
                        "Reenabling Source input did not restore disk-to-machine supply"))
                .thenIdle(10).thenExecute(() -> {
                    helper.assertTrue(machine.getNativeEnergyContainer().getEnergy() == 0
                                    && stored(fixture, MagicResourceKeys.source()) == 0,
                            "Disabled FE input still refilled energy or Source was withdrawn more than once");
                    assertIdentity(helper, fixture, identity.get());
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 280)
    public static void oneEssenceInputKindOffDoesNotBlockTheOtherThree(GameTestHelper helper) {
        if (skipForbidden(helper)) return;
        Network fixture = place(helper, ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get(), magicDisk());
        HephaestusForgeProcessorBlockEntity machine = (HephaestusForgeProcessorBlockEntity) fixture.machine();
        flip(helper, machine, buttonIdForInput(ENERGY));
        flip(helper, machine, buttonIdForInput(ESSENCE_AUREAL));
        machine.getNativeEnergyContainer().setEnergy(0);
        long[] amounts = {400, 7, 600, 800};
        AtomicReference<Identity> identity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, essenceKeys()))
                .thenExecute(() -> {
                    identity.set(identity(helper, fixture));
                    for (int channel = 0; channel < 4; channel++) seed(helper, fixture, MagicResourceKeys.essence(channel), amounts[channel]);
                }).thenWaitUntil(() -> {
                    for (int channel = 1; channel < 4; channel++) helper.assertTrue(machine.getStoredEssence(channel) == amounts[channel],
                            "Unrelated Essence input was blocked by the Aureal-only switch: " + channel);
                }).thenExecute(() -> {
                    helper.assertTrue(machine.getStoredEssence(0) == 0 && stored(fixture, MagicResourceKeys.essence(0)) == 400,
                            "Disabled Aureal input debited its disk balance");
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, buttonIdForInput(ESSENCE_AUREAL));
                }).thenWaitUntil(() -> helper.assertTrue(machine.getStoredEssence(0) == 400,
                        "Reenabled Aureal input did not resume"))
                .thenExecute(() -> {
                    for (int channel = 0; channel < 4; channel++) helper.assertTrue(machine.getStoredEssence(channel)
                                    + stored(fixture, MagicResourceKeys.essence(channel)) == amounts[channel],
                            "Per-kind Essence supply lost or duplicated a typed balance: " + channel);
                    assertIdentity(helper, fixture, identity.get());
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 280)
    public static void ordinaryItemReturnSwitchesPreserveOutputAndItsExistingNode(GameTestHelper helper) {
        itemReturn(helper, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get());
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 280)
    public static void legacyDrygmyReturnSwitchesPreservePaidItemsAndItsExistingNode(GameTestHelper helper) {
        itemReturn(helper, ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get());
    }

    private static void itemReturn(GameTestHelper helper, Block block) {
        Network fixture = place(helper, block, AEItems.ITEM_CELL_1K.stack());
        NativeMagicMachineBlockEntity machine = (NativeMagicMachineBlockEntity) fixture.machine();
        flip(helper, machine, TOGGLE_BUTTON_ID);
        flip(helper, machine, buttonIdForOutput(OUTPUT_ITEMS));
        machine.getNativeEnergyContainer().setEnergy(0);
        AtomicReference<Identity> identity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenExecute(() -> {
                    identity.set(identity(helper, fixture));
                    machine.getNativeOutputSlot().setStack(new ItemStack(Items.STONE, 37));
                }).thenIdle(25).thenExecute(() -> {
                    assertItemBalance(helper, fixture, 37, 0);
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, OUTPUT_MASTER_BUTTON_ID);
                    flip(helper, machine, buttonIdForOutput(OUTPUT_ITEMS));
                }).thenIdle(15).thenExecute(() -> {
                    assertItemBalance(helper, fixture, 37, 0);
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, OUTPUT_MASTER_BUTTON_ID);
                }).thenWaitUntil(() -> assertItemBalance(helper, fixture, 0, 37))
                .thenIdle(20).thenExecute(() -> {
                    assertItemBalance(helper, fixture, 0, 37);
                    helper.assertTrue(!controls(machine).inputMaster(), "Reenabling return also enabled independent input supply");
                    assertIdentity(helper, fixture, identity.get());
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 280)
    public static void condenserDisablesOnlyTheSelectedEssenceReturnBank(GameTestHelper helper) {
        if (skipForbidden(helper)) return;
        Network fixture = place(helper, ForbiddenArcanusRegistries.ESSENCE_CONDENSER_BLOCK.get(), magicDisk());
        EssenceCondenserBlockEntity machine = (EssenceCondenserBlockEntity) fixture.machine();
        flip(helper, machine, TOGGLE_BUTTON_ID);
        flip(helper, machine, buttonIdForOutput(ESSENCE_SOULS));
        machine.getNativeEnergyContainer().setEnergy(0);
        int[] amounts = {1234, 67, 2345, 3456};
        AtomicReference<Identity> identity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, essenceKeys()))
                .thenExecute(() -> {
                    identity.set(identity(helper, fixture));
                    CompoundTag state = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
                    state.getCompound(EssenceCondenserBlockEntity.STATE_TAG).putIntArray("banks", amounts);
                    machine.loadWithComponents(state, helper.getLevel().registryAccess());
                }).thenWaitUntil(() -> {
                    for (int channel : new int[]{0, 2, 3}) helper.assertTrue(machine.getStoredEssence(channel) == 0
                                    && stored(fixture, MagicResourceKeys.essence(channel)) == amounts[channel],
                            "Disabling Soul return blocked another Essence output bank: " + channel);
                }).thenIdle(15).thenExecute(() -> {
                    helper.assertTrue(machine.getStoredEssence(1) == 67 && stored(fixture, MagicResourceKeys.essence(1)) == 0,
                            "Disabled Soul return debited its local typed bank");
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, buttonIdForOutput(ESSENCE_SOULS));
                }).thenWaitUntil(() -> {
                    for (int channel = 0; channel < 4; channel++) helper.assertTrue(machine.getStoredEssence(channel) == 0
                                    && stored(fixture, MagicResourceKeys.essence(channel)) == amounts[channel],
                            "Reenabled Essence return lost, mixed or duplicated bank " + channel);
                }).thenExecute(() -> assertIdentity(helper, fixture, identity.get())).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 280)
    public static void sourceProducerReturnStopsAndResumesWithoutImportLoopOrNewNode(GameTestHelper helper) {
        Network fixture = place(helper, ArsNouveauRegistries.SOURCE_CONVERTER_BLOCK.get(), magicDisk());
        FeSourceConverterBlockEntity machine = (FeSourceConverterBlockEntity) fixture.machine();
        flip(helper, machine, TOGGLE_BUTTON_ID);
        flip(helper, machine, buttonIdForOutput(SOURCE));
        machine.getNativeEnergyContainer().setEnergy(0);
        machine.setSource(6000);
        AtomicReference<Identity> identity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, MagicResourceKeys.source()))
                .thenExecute(() -> identity.set(identity(helper, fixture)))
                .thenIdle(25).thenExecute(() -> {
                    helper.assertTrue(machine.getSource() == 6000 && stored(fixture, MagicResourceKeys.source()) == 0,
                            "Disabled Source return still transferred the producer's stored output");
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, buttonIdForOutput(SOURCE));
                }).thenWaitUntil(() -> helper.assertTrue(machine.getSource() == 0
                                && stored(fixture, MagicResourceKeys.source()) == 6000,
                        "Reenabled Source return did not export the existing paid balance once"))
                .thenIdle(20).thenExecute(() -> {
                    helper.assertTrue(machine.getSource() == 0 && stored(fixture, MagicResourceKeys.source()) == 6000,
                            "Source producer imported its returned output or duplicated it after reenabling");
                    assertIdentity(helper, fixture, identity.get());
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 280)
    public static void auraProducerReturnStopsAndResumesTheOriginalTypedOutput(GameTestHelper helper) {
        Network fixture = place(helper, NaturesAuraRegistries.AURA_FUEL_GENERATOR_BLOCK.get(), magicDisk());
        AuraFuelGeneratorBlockEntity machine = (AuraFuelGeneratorBlockEntity) fixture.machine();
        flip(helper, machine, buttonIdForOutput(AURA));
        AEKey key = MagicResourceKeys.key(machine.getGenerationAuraType().getName());
        CompoundTag seeded = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        seeded.getCompound("nature_aura_storage").putInt("stored_aura", 6000);
        seeded.putString("nature_aura_type", machine.getGenerationAuraType().getName().toString());
        machine.loadWithComponents(seeded, helper.getLevel().registryAccess());
        helper.assertTrue(machine.getStoredAura() == 6000, "Could not restore the producer's committed Aura output");
        AtomicReference<Identity> identity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, key))
                .thenExecute(() -> identity.set(identity(helper, fixture)))
                .thenIdle(25).thenExecute(() -> {
                    helper.assertTrue(machine.getStoredAura() == 6000 && stored(fixture, key) == 0,
                            "Disabled Aura return still drained its committed output");
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, buttonIdForOutput(AURA));
                }).thenWaitUntil(() -> helper.assertTrue(machine.getStoredAura() == 0 && stored(fixture, key) == 6000,
                        "Reenabled Aura return lost or retyped its paid balance"))
                .thenIdle(20).thenExecute(() -> {
                    helper.assertTrue(machine.getStoredAura() == 0 && stored(fixture, key) == 6000,
                            "Aura return was duplicated after reenabling");
                    assertIdentity(helper, fixture, identity.get());
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void disabledNewFluidReturnStillSettlesAnAlreadyPaidDrygmyQueue(GameTestHelper helper) {
        Network fixture = place(helper, ArsNouveauRegistries.DRYGMY_SIMULATOR_BLOCK.get(),
                AEItems.FLUID_CELL_1K.stack(), AEItems.ITEM_CELL_1K.stack());
        DrygmySimulatorBlockEntity machine = (DrygmySimulatorBlockEntity) fixture.machine();
        flip(helper, machine, TOGGLE_BUTTON_ID);
        flip(helper, machine, buttonIdForOutput(OUTPUT_FLUIDS));
        machine.getNativeEnergyContainer().setEnergy(0);
        FluidStack milk = FluidUtil.getFluidContained(new ItemStack(Items.MILK_BUCKET)).orElseThrow();
        AEKey milkKey = AEFluidKey.of(milk);
        AtomicReference<Identity> identity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, milkKey, AEItemKey.of(Items.STONE)))
                .thenExecute(() -> {
                    identity.set(identity(helper, fixture));
                    helper.assertTrue(MachineDirectOutputHooks.simulateFluidAcceptance(machine, milk, 1000) == 0,
                            "Disabled fluid return admitted a new fluid-producing operation");
                    var rejected = MachineDirectOutputHooks.insertFluidDetailed(machine, milk, 1000);
                    helper.assertTrue(rejected.accepted() == 0 && !rejected.attempted() && stored(fixture, milkKey) == 0,
                            "Disabled new fluid return executed an insertion");
                    machine.getNativeOutputSlot().setStack(new ItemStack(Items.STONE, 23));
                }).thenWaitUntil(() -> assertItemBalance(helper, fixture, 0, 23))
                .thenExecute(() -> {
                    flip(helper, machine, OUTPUT_MASTER_BUTTON_ID);
                    CompoundTag saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
                    ListTag paid = new ListTag();
                    paid.add(milk.copyWithAmount(1000).save(helper.getLevel().registryAccess()));
                    saved.put("drygmy_paid_fluid_outputs", paid);
                    machine.loadWithComponents(saved, helper.getLevel().registryAccess());
                }).thenWaitUntil(() -> helper.assertTrue(stored(fixture, milkKey) == 1000
                                && machine.saveWithoutMetadata(helper.getLevel().registryAccess())
                                .getList("drygmy_paid_fluid_outputs", net.minecraft.nbt.Tag.TAG_COMPOUND).isEmpty(),
                        "Output OFF stranded or discarded an already paid fluid reservation"))
                .thenExecute(() -> {
                    helper.assertTrue(!controls(machine).outputMaster(), "Settling paid fluid reenabled new output");
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, OUTPUT_MASTER_BUTTON_ID);
                    helper.assertTrue(MachineDirectOutputHooks.simulateFluidAcceptance(machine, milk, 1000) == 0,
                            "Fluid kind OFF was ignored when only the master was reenabled");
                    flip(helper, machine, buttonIdForOutput(OUTPUT_FLUIDS));
                    helper.assertTrue(MachineDirectOutputHooks.simulateFluidAcceptance(machine, milk, 1000) == 1000
                                    && stored(fixture, milkKey) == 1000,
                            "Reenabled fluid return failed admission or inserted during simulation");
                }).thenIdle(15).thenExecute(() -> helper.assertTrue(stored(fixture, milkKey) == 1000,
                        "Already settled fluid replayed after its output switches were reenabled"))
                .thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 300)
    public static void disabledNewItemReturnSettlesEscrowBeforeHoldingFreshPhysicalOutput(GameTestHelper helper) {
        Network fixture = place(helper, ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get(), AEItems.ITEM_CELL_1K.stack());
        ImbuementProcessorBlockEntity machine = (ImbuementProcessorBlockEntity) fixture.machine();
        flip(helper, machine, TOGGLE_BUTTON_ID);
        flip(helper, machine, OUTPUT_MASTER_BUTTON_ID);
        flip(helper, machine, buttonIdForOutput(OUTPUT_ITEMS));
        AtomicReference<Identity> identity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenExecute(() -> {
                    identity.set(identity(helper, fixture));
                    machine.getNativeOutputSlot().setStack(new ItemStack(Items.STONE, 7));
                    CompoundTag saved = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
                    ListTag paid = new ListTag();
                    paid.add(GenericStack.writeTag(helper.getLevel().registryAccess(), new GenericStack(AEItemKey.of(Items.STONE), 11)));
                    saved.put(Ae2MachineOutputAccess.PENDING_TAG, paid);
                    machine.loadWithComponents(saved, helper.getLevel().registryAccess());
                }).thenWaitUntil(() -> assertItemBalance(helper, fixture, 7, 11))
                .thenIdle(15).thenExecute(() -> {
                    assertItemBalance(helper, fixture, 7, 11);
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, OUTPUT_MASTER_BUTTON_ID);
                }).thenIdle(10).thenExecute(() -> {
                    assertItemBalance(helper, fixture, 7, 11);
                    flip(helper, machine, buttonIdForOutput(OUTPUT_ITEMS));
                }).thenWaitUntil(() -> assertItemBalance(helper, fixture, 0, 18))
                .thenIdle(20).thenExecute(() -> {
                    assertItemBalance(helper, fixture, 0, 18);
                    assertIdentity(helper, fixture, identity.get());
                }).thenSucceed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft", timeoutTicks = 420)
    public static void mekEFactoryItemReturnHonorsKindAcrossNodeOwnershipChanges(GameTestHelper helper) {
        Network fixture = place(helper, NativeMekanismRegistries.BASIC_SPIRIT_FACTORY_BLOCK.get(), AEItems.ITEM_CELL_1K.stack());
        TileEntityMekanism machine = fixture.machine();
        var automation = (IMekanismMagicAutomation) machine;
        var adapter = (MeUpgradeRecipeMachineAdapter) machine;
        flip(helper, machine, TOGGLE_BUTTON_ID);
        flip(helper, machine, buttonIdForOutput(OUTPUT_ITEMS));
        automation.mekanismMagicEnergyContainer().setEnergy(0);
        helper.assertTrue(adapter.getMeUpgradeContainer().install(MeUpgradeType.PATTERN_PROVIDER).successful(),
                "Could not install real factory MekE runtime");
        adapter.onMeUpgradeStateChanged();
        adapter.createMeNodeIfActive();
        AtomicReference<Identity> identity = new AtomicReference<>();
        helper.startSequence().thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenExecute(() -> {
                    identity.set(identity(helper, fixture));
                    seedFactoryOutputs(automation);
                }).thenIdle(25).thenExecute(() -> {
                    assertItemBalance(helper, fixture, 6, 0);
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, buttonIdForOutput(OUTPUT_ITEMS));
                }).thenWaitUntil(() -> assertItemBalance(helper, fixture, 0, 6))
                .thenExecute(() -> {
                    assertIdentity(helper, fixture, identity.get());
                    flip(helper, machine, buttonIdForOutput(OUTPUT_ITEMS));
                    seedFactoryOutputs(automation);
                    helper.assertTrue(adapter.getMeUpgradeContainer().uninstall(MeUpgradeType.PATTERN_PROVIDER),
                            "Could not remove the empty factory MekE upgrade");
                    adapter.onMeUpgradeStateChanged();
                }).thenWaitUntil(() -> ready(helper, fixture, AEItemKey.of(Items.STONE)))
                .thenIdle(20).thenExecute(() -> {
                    identity.set(identity(helper, fixture));
                    assertItemBalance(helper, fixture, 6, 6);
                    helper.assertTrue(!isOutputEnabled(machine, OUTPUT_ITEMS),
                            "MekE removal reset the factory's output-kind choice");
                    flip(helper, machine, buttonIdForOutput(OUTPUT_ITEMS));
                }).thenWaitUntil(() -> assertItemBalance(helper, fixture, 0, 12))
                .thenIdle(20).thenExecute(() -> {
                    assertItemBalance(helper, fixture, 0, 12);
                    assertIdentity(helper, fixture, identity.get());
                }).thenSucceed();
    }

    private static Network place(GameTestHelper helper, Block block, ItemStack... disks) {
        helper.setBlock(MACHINE, block);
        TileEntityMekanism machine = helper.getBlockEntity(MACHINE);
        helper.setBlock(ENERGY_POS, AEBlocks.DENSE_ENERGY_CELL.block());
        EnergyCellBlockEntity power = helper.getBlockEntity(ENERGY_POS);
        double charge = power.getAEMaxPower() * 0.75;
        power.injectAEPower(charge, Actionable.MODULATE);
        helper.assertTrue(power.getAECurrentPower() >= charge * 0.99, "Could not charge finite AE fixture power");
        helper.setBlock(DRIVE, AEBlocks.DRIVE.block());
        DriveBlockEntity drive = helper.getBlockEntity(DRIVE);
        for (int slot = 0; slot < disks.length; slot++) helper.assertTrue(
                drive.getInternalInventory().insertItem(slot, disks[slot], false).isEmpty(), "Could not mount a real ME drive cell");
        return new Network(machine, drive);
    }

    private static void flip(GameTestHelper helper, BlockEntity machine, int button) {
        helper.assertTrue(toggleFunction(machine, button), "Supported function button was rejected: " + button);
    }

    private static void assertChoices(GameTestHelper helper, BlockEntity machine, int inputMask, int outputMask) {
        var current = controls(machine);
        helper.assertTrue(!current.inputMaster() && !current.outputMaster()
                        && current.configuredInputMask() == inputMask && current.configuredOutputMask() == outputMask,
                "Load/drop/upgrade changed independently configured function switches");
    }

    private static ItemStack magicDisk() { return new ItemStack(MagicStorageCells.CELLS.get(1).get()); }

    private static AEKey[] essenceKeys() {
        return new AEKey[]{MagicResourceKeys.essence(0), MagicResourceKeys.essence(1),
                MagicResourceKeys.essence(2), MagicResourceKeys.essence(3)};
    }

    private static void ready(GameTestHelper helper, Network fixture, AEKey... keys) {
        IGridNode node = node(helper);
        helper.assertTrue(node != null && node.isActive() && fixture.drive().isPowered()
                        && node.getGrid() == fixture.drive().getMainNode().getGrid(),
                "Waiting for the original machine node to join its powered drive");
        for (AEKey key : keys) helper.assertTrue(key != null && fixture.storage().insert(key, 1,
                        Actionable.SIMULATE, IActionSource.empty()) == 1,
                "Waiting for a compatible physical disk for " + key);
    }

    private static IGridNode node(GameTestHelper helper) {
        IInWorldGridNodeHost host = helper.getLevel().getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST,
                helper.absolutePos(MACHINE), (Void) null);
        return host == null ? null : host.getGridNode(Direction.EAST);
    }

    private static Identity identity(GameTestHelper helper, Network fixture) {
        return new Identity(node(helper), fixture.drive().getMainNode().getGrid());
    }

    private static void assertIdentity(GameTestHelper helper, Network fixture, Identity original) {
        var status = snapshot(fixture.machine());
        helper.assertTrue(node(helper) == original.node() && node(helper).getGrid() == original.grid()
                        && original.grid() == fixture.drive().getMainNode().getGrid() && original.grid().size() == 3
                        && status.connected() && status.active(),
                "Function controls disconnected, replaced or duplicated the existing machine/grid node");
    }

    private static void seed(GameTestHelper helper, Network fixture, AEKey key, long amount) {
        helper.assertTrue(fixture.storage().insert(key, amount, Actionable.MODULATE, IActionSource.empty()) == amount,
                "Real disk rejected the exact seeded balance");
    }

    private static long stored(Network fixture, AEKey key) { return fixture.storage().getAvailableStacks().get(key); }

    private static void assertItemBalance(GameTestHelper helper, Network fixture, int local, int network) {
        var machine = (IMekanismMagicAutomation) fixture.machine();
        long owned = machine.mekanismMagicPatternOutputs().stream().map(IInventorySlot::getStack)
                .filter(stack -> stack.is(Items.STONE)).mapToLong(ItemStack::getCount).sum();
        helper.assertTrue(owned == local && stored(fixture, AEItemKey.of(Items.STONE)) == network,
                "Item-return controls lost, duplicated or transferred an output while disabled: local=" + owned
                        + ", disk=" + stored(fixture, AEItemKey.of(Items.STONE)));
    }

    private static void seedFactoryOutputs(IMekanismMagicAutomation machine) {
        var outputs = machine.mekanismMagicPatternOutputs();
        if (outputs.size() != 3) throw new AssertionError("Basic factory fixture lost a physical output lane");
        for (int lane = 0; lane < outputs.size(); lane++) {
            ((BasicInventorySlot) outputs.get(lane)).setStackUnchecked(new ItemStack(Items.STONE, lane + 1));
        }
    }

    private static boolean skipForbidden(GameTestHelper helper) {
        if (ModCompatibility.forbiddenArcanusLoaded()) return false;
        helper.succeed();
        return true;
    }

    private record Identity(IGridNode node, IGrid grid) { }
    private record Network(TileEntityMekanism machine, DriveBlockEntity drive) {
        private MEStorage storage() { return drive.getMainNode().getGrid().getStorageService().getInventory(); }
    }
}
