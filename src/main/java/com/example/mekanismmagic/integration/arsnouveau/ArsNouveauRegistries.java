package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MagicLang;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.block.NativeMachineBlock;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import mekanism.api.Upgrade;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registries loaded only when Ars Nouveau is present.
 */
public final class ArsNouveauRegistries {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MekanismMagic.MOD_ID);
    public static final BlockDeferredRegister BLOCKS =
            new BlockDeferredRegister(MekanismMagic.MOD_ID);
    public static final TileEntityTypeDeferredRegister TILES =
            new TileEntityTypeDeferredRegister(MekanismMagic.MOD_ID);
    public static final ContainerTypeDeferredRegister CONTAINERS =
            new ContainerTypeDeferredRegister(MekanismMagic.MOD_ID);

    public static final DeferredHolder<Item, Item> SOURCE_CONVERSION_MODULE =
            ITEMS.register("source_conversion_module",
                    () -> new SourceConversionModuleItem(
                            new Item.Properties().stacksTo(1)));

    public static final ContainerTypeRegistryObject<
            MekanismTileContainer<SourceAmplifierBlockEntity>>
            SOURCE_AMPLIFIER_CONTAINER =
            CONTAINERS.custom("source_generator",
                    SourceAmplifierBlockEntity.class).build();
    public static final ContainerTypeRegistryObject<
            MekanismTileContainer<ImbuementProcessorBlockEntity>>
            IMBUEMENT_PROCESSOR_CONTAINER =
            CONTAINERS.custom("imbuement_processor",
                    ImbuementProcessorBlockEntity.class).build();
    public static final ContainerTypeRegistryObject<
            MekanismTileContainer<EnchantingApparatusProcessorBlockEntity>>
            ENCHANTING_APPARATUS_PROCESSOR_CONTAINER =
            CONTAINERS.custom("enchanting_apparatus_processor",
                    EnchantingApparatusProcessorBlockEntity.class)
                    .offset(0, 18).build();
    public static final ContainerTypeRegistryObject<
            MekanismTileContainer<DrygmySimulatorBlockEntity>>
            DRYGMY_SIMULATOR_CONTAINER =
            CONTAINERS.custom("drygmy_simulator",
                    DrygmySimulatorBlockEntity.class)
                    .offset(17, 42).build();

    public static final Machine<SourceAmplifierBlockEntity>
            SOURCE_AMPLIFIER_TYPE =
            Machine.MachineBuilder.createMachine(
                            ArsNouveauRegistries::sourceGeneratorTile,
                            MagicLang.SOURCE_AMPLIFIER)
                    .withGui(() -> SOURCE_AMPLIFIER_CONTAINER)
                    .withEnergyConfig(() -> 500L, () -> 4_000_000L)
                    .withSideConfig(TransmissionType.ITEM,
                            TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY)
                    .build();
    public static final Machine<ImbuementProcessorBlockEntity>
            IMBUEMENT_PROCESSOR_TYPE =
            Machine.MachineBuilder.createMachine(
                            ArsNouveauRegistries::imbuementProcessorTile,
                            MagicLang.IMBUEMENT_PROCESSOR)
                    .withGui(() -> IMBUEMENT_PROCESSOR_CONTAINER)
                    .withEnergyConfig(() -> 600L, () -> 2_000_000L)
                    .withSideConfig(TransmissionType.ITEM,
                            TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY)
                    .build();
    public static final Machine<EnchantingApparatusProcessorBlockEntity>
            ENCHANTING_APPARATUS_PROCESSOR_TYPE =
            Machine.MachineBuilder.createMachine(
                            ArsNouveauRegistries::
                                    enchantingApparatusProcessorTile,
                            MagicLang.ENCHANTING_APPARATUS_PROCESSOR)
                    .withGui(() -> ENCHANTING_APPARATUS_PROCESSOR_CONTAINER)
                    .withEnergyConfig(() -> 1_200L, () -> 4_000_000L)
                    .withSideConfig(TransmissionType.ITEM,
                            TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY)
                    .build();
    public static final Machine<DrygmySimulatorBlockEntity>
            DRYGMY_SIMULATOR_TYPE =
            Machine.MachineBuilder.createMachine(
                            ArsNouveauRegistries::drygmySimulatorTile,
                            MagicLang.DRYGMY_SIMULATOR)
                    .withGui(() -> DRYGMY_SIMULATOR_CONTAINER)
                    .withEnergyConfig(() -> 800L, () -> 4_000_000L)
                    .withSideConfig(TransmissionType.ITEM,
                            TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY)
                    .build();

    public static final BlockRegistryObject<
            NativeMachineBlock<SourceAmplifierBlockEntity>, BlockItem>
            SOURCE_AMPLIFIER_BLOCK =
            BLOCKS.register("source_generator",
                    () -> new NativeMachineBlock<>(SOURCE_AMPLIFIER_TYPE,
                            BlockBehaviour.Properties.of().strength(4.0F)
                                    .requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<
            NativeMachineBlock<ImbuementProcessorBlockEntity>, BlockItem>
            IMBUEMENT_PROCESSOR_BLOCK =
            BLOCKS.register("imbuement_processor",
                    () -> new NativeMachineBlock<>(IMBUEMENT_PROCESSOR_TYPE,
                            BlockBehaviour.Properties.of().strength(4.0F)
                                    .requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<
            NativeMachineBlock<EnchantingApparatusProcessorBlockEntity>,
            BlockItem> ENCHANTING_APPARATUS_PROCESSOR_BLOCK =
            BLOCKS.register("enchanting_apparatus_processor",
                    () -> new NativeMachineBlock<>(
                            ENCHANTING_APPARATUS_PROCESSOR_TYPE,
                            BlockBehaviour.Properties.of().strength(5.0F)
                                    .requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<
            NativeMachineBlock<DrygmySimulatorBlockEntity>, BlockItem>
            DRYGMY_SIMULATOR_BLOCK =
            BLOCKS.register("drygmy_simulator",
                    () -> new NativeMachineBlock<>(
                            DRYGMY_SIMULATOR_TYPE,
                            BlockBehaviour.Properties.of().strength(5.0F)
                                    .requiresCorrectToolForDrops()));

    public static final TileEntityTypeRegistryObject<
            SourceAmplifierBlockEntity> SOURCE_AMPLIFIER_TILE =
            TILES.mekBuilder(SOURCE_AMPLIFIER_BLOCK,
                            SourceAmplifierBlockEntity::new)
                    .serverTicker((level, pos, state, tile) ->
                            mekanism.common.tile.base.TileEntityMekanism
                                    .tickServer(level, pos, state, tile))
                    .build();
    public static final TileEntityTypeRegistryObject<
            ImbuementProcessorBlockEntity> IMBUEMENT_PROCESSOR_TILE =
            TILES.mekBuilder(IMBUEMENT_PROCESSOR_BLOCK,
                            ImbuementProcessorBlockEntity::new)
                    .serverTicker((level, pos, state, tile) ->
                            mekanism.common.tile.base.TileEntityMekanism
                                    .tickServer(level, pos, state, tile))
                    .build();
    public static final TileEntityTypeRegistryObject<
            EnchantingApparatusProcessorBlockEntity>
            ENCHANTING_APPARATUS_PROCESSOR_TILE =
            TILES.mekBuilder(ENCHANTING_APPARATUS_PROCESSOR_BLOCK,
                            EnchantingApparatusProcessorBlockEntity::new)
                    .serverTicker((level, pos, state, tile) ->
                            mekanism.common.tile.base.TileEntityMekanism
                                    .tickServer(level, pos, state, tile))
                    .build();
    public static final TileEntityTypeRegistryObject<
            DrygmySimulatorBlockEntity> DRYGMY_SIMULATOR_TILE =
            TILES.mekBuilder(DRYGMY_SIMULATOR_BLOCK,
                            DrygmySimulatorBlockEntity::new)
                    .serverTicker((level, pos, state, tile) ->
                            mekanism.common.tile.base.TileEntityMekanism
                                    .tickServer(level, pos, state, tile))
                    .build();

    private ArsNouveauRegistries() {
    }

    private static TileEntityTypeRegistryObject<SourceAmplifierBlockEntity>
    sourceGeneratorTile() {
        return SOURCE_AMPLIFIER_TILE;
    }

    private static TileEntityTypeRegistryObject<ImbuementProcessorBlockEntity>
    imbuementProcessorTile() {
        return IMBUEMENT_PROCESSOR_TILE;
    }

    private static TileEntityTypeRegistryObject<
            EnchantingApparatusProcessorBlockEntity>
    enchantingApparatusProcessorTile() {
        return ENCHANTING_APPARATUS_PROCESSOR_TILE;
    }

    private static TileEntityTypeRegistryObject<
            DrygmySimulatorBlockEntity> drygmySimulatorTile() {
        return DRYGMY_SIMULATOR_TILE;
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        TILES.register(modBus);
        CONTAINERS.register(modBus);
        modBus.addListener(ArsNouveauRegistries::registerCapabilities);
    }

    private static void registerCapabilities(
            RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                SOURCE_AMPLIFIER_TILE.get(),
                (tile, side) -> tile.getSourceStorage());
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                IMBUEMENT_PROCESSOR_TILE.get(),
                (tile, side) -> tile.getSourceStorage());
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                ENCHANTING_APPARATUS_PROCESSOR_TILE.get(),
                (tile, side) -> tile.getSourceStorage());
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                DRYGMY_SIMULATOR_TILE.get(),
                (tile, side) -> tile.getSourceStorage());
    }
}
