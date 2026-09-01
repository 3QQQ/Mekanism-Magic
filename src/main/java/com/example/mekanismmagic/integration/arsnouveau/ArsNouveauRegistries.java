package com.example.mekanismmagic.integration.arsnouveau;

import com.example.mekanismmagic.MagicLang;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.NativeMekanismRegistries;
import com.example.mekanismmagic.block.NativeMachineBlock;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import com.example.mekanismmagic.container.ArsSourceMachineContainer;
import com.example.mekanismmagic.container.ImbuementFactoryContainer;
import com.example.mekanismmagic.container.CatalystIdentifierAssemblerContainer;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import mekanism.api.Upgrade;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.content.blocktype.BlockTypeTile.BlockTileBuilder;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tier.PipeTier;
import mekanism.common.block.attribute.AttributeTier;
import mekanism.common.block.attribute.AttributeUpgradeable;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.capabilities.proxy.ProxyConfigurable;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.function.Supplier;

/**
 * Registries loaded only when Ars Nouveau is present.
 */
public final class ArsNouveauRegistries {
    private static final Upgrade MEKANISM_EXTRAS_CREATIVE_UPGRADE =
            optionalCreativeSourceUpgrade();
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MekanismMagic.MOD_ID);
    public static final DeferredHolder<Item, CatalystIdentifierItem>
            CATALYST_IDENTIFIER_ITEM =
            ITEMS.register("catalyst_identifier",
                    () -> new CatalystIdentifierItem(
                            new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, SourceLinkToolItem>
            SOURCE_LINK_TOOL_ITEM =
            ITEMS.register("source_link_tool",
                    () -> new SourceLinkToolItem(
                            new Item.Properties().stacksTo(1)));
    public static final BlockDeferredRegister BLOCKS =
            new BlockDeferredRegister(MekanismMagic.MOD_ID);
    public static final TileEntityTypeDeferredRegister TILES =
            new TileEntityTypeDeferredRegister(MekanismMagic.MOD_ID);
    public static final ContainerTypeDeferredRegister CONTAINERS =
            new ContainerTypeDeferredRegister(MekanismMagic.MOD_ID);

    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    ArsNouveauRegistries::magicSourcePipeTile,
                    MagicLang.MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.BASIC);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            ADVANCED_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    ArsNouveauRegistries::advancedMagicSourcePipeTile,
                    MagicLang.ADVANCED_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.ADVANCED);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            ELITE_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    ArsNouveauRegistries::eliteMagicSourcePipeTile,
                    MagicLang.ELITE_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.ELITE);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            ULTIMATE_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    ArsNouveauRegistries::ultimateMagicSourcePipeTile,
                    MagicLang.ULTIMATE_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.ULTIMATE);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            ABSOLUTE_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    ArsNouveauRegistries::absoluteMagicSourcePipeTile,
                    MagicLang.ABSOLUTE_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.ABSOLUTE);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            SUPREME_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    ArsNouveauRegistries::supremeMagicSourcePipeTile,
                    MagicLang.SUPREME_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.SUPREME);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            COSMIC_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    ArsNouveauRegistries::cosmicMagicSourcePipeTile,
                    MagicLang.COSMIC_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.COSMIC);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            INFINITE_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    ArsNouveauRegistries::infiniteMagicSourcePipeTile,
                    MagicLang.INFINITE_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.INFINITE);

    public static final BlockRegistryObject<MagicSourcePipeBlock,
            MagicSourcePipeItem>
            MAGIC_SOURCE_PIPE_BLOCK =
            registerMagicSourcePipeBlock("magic_source_pipe",
                    MAGIC_SOURCE_PIPE_TYPE, MagicSourcePipeTier.BASIC);
    public static final BlockRegistryObject<MagicSourcePipeBlock,
            MagicSourcePipeItem>
            ADVANCED_MAGIC_SOURCE_PIPE_BLOCK =
            registerMagicSourcePipeBlock("advanced_magic_source_pipe",
                    ADVANCED_MAGIC_SOURCE_PIPE_TYPE,
                    MagicSourcePipeTier.ADVANCED);
    public static final BlockRegistryObject<MagicSourcePipeBlock,
            MagicSourcePipeItem>
            ELITE_MAGIC_SOURCE_PIPE_BLOCK =
            registerMagicSourcePipeBlock("elite_magic_source_pipe",
                    ELITE_MAGIC_SOURCE_PIPE_TYPE, MagicSourcePipeTier.ELITE);
    public static final BlockRegistryObject<MagicSourcePipeBlock,
            MagicSourcePipeItem>
            ULTIMATE_MAGIC_SOURCE_PIPE_BLOCK =
            registerMagicSourcePipeBlock("ultimate_magic_source_pipe",
                    ULTIMATE_MAGIC_SOURCE_PIPE_TYPE,
                    MagicSourcePipeTier.ULTIMATE);
    public static final BlockRegistryObject<MagicSourcePipeBlock,
            MagicSourcePipeItem>
            ABSOLUTE_MAGIC_SOURCE_PIPE_BLOCK =
            registerMagicSourcePipeBlock("absolute_magic_source_pipe",
                    ABSOLUTE_MAGIC_SOURCE_PIPE_TYPE,
                    MagicSourcePipeTier.ABSOLUTE);
    public static final BlockRegistryObject<MagicSourcePipeBlock,
            MagicSourcePipeItem>
            SUPREME_MAGIC_SOURCE_PIPE_BLOCK =
            registerMagicSourcePipeBlock("supreme_magic_source_pipe",
                    SUPREME_MAGIC_SOURCE_PIPE_TYPE,
                    MagicSourcePipeTier.SUPREME);
    public static final BlockRegistryObject<MagicSourcePipeBlock,
            MagicSourcePipeItem>
            COSMIC_MAGIC_SOURCE_PIPE_BLOCK =
            registerMagicSourcePipeBlock("cosmic_magic_source_pipe",
                    COSMIC_MAGIC_SOURCE_PIPE_TYPE, MagicSourcePipeTier.COSMIC);
    public static final BlockRegistryObject<MagicSourcePipeBlock,
            MagicSourcePipeItem>
            INFINITE_MAGIC_SOURCE_PIPE_BLOCK =
            registerMagicSourcePipeBlock("infinite_magic_source_pipe",
                    INFINITE_MAGIC_SOURCE_PIPE_TYPE,
                    MagicSourcePipeTier.INFINITE);

    public static final ContainerTypeRegistryObject<
            ArsSourceMachineContainer<SourceAmplifierBlockEntity>>
            SOURCE_AMPLIFIER_CONTAINER =
            CONTAINERS.register("source_generator",
                    SourceAmplifierBlockEntity.class,
                    (id, inventory, tile) ->
                            new ArsSourceMachineContainer<>(id, inventory, tile));
    public static final ContainerTypeRegistryObject<
            ArsSourceMachineContainer<FeSourceConverterBlockEntity>>
            SOURCE_CONVERTER_CONTAINER =
            CONTAINERS.register("source_converter",
                    FeSourceConverterBlockEntity.class,
                    (id, inventory, tile) ->
                            new ArsSourceMachineContainer<>(id, inventory, tile));
    public static final ContainerTypeRegistryObject<
            ArsSourceMachineContainer<ImbuementProcessorBlockEntity>>
            IMBUEMENT_PROCESSOR_CONTAINER =
            CONTAINERS.register("imbuement_processor",
                    ImbuementProcessorBlockEntity.class,
                    (id, inventory, tile) ->
                            new ArsSourceMachineContainer<>(id, inventory, tile));
    public static final ContainerTypeRegistryObject<
            ArsSourceMachineContainer<
                    EnchantingApparatusProcessorBlockEntity>>
            ENCHANTING_APPARATUS_PROCESSOR_CONTAINER =
            CONTAINERS.register("enchanting_apparatus_processor",
                    EnchantingApparatusProcessorBlockEntity.class,
                    (id, inventory, tile) ->
                            new ArsSourceMachineContainer<>(id, inventory, tile));
    public static final ContainerTypeRegistryObject<
            ArsSourceMachineContainer<DrygmySimulatorBlockEntity>>
            DRYGMY_SIMULATOR_CONTAINER =
            CONTAINERS.register("drygmy_simulator",
                    DrygmySimulatorBlockEntity.class,
                    (id, inventory, tile) ->
                            new ArsSourceMachineContainer<>(id, inventory, tile));
    public static final ContainerTypeRegistryObject<
            CatalystIdentifierAssemblerContainer>
            CATALYST_IDENTIFIER_ASSEMBLER_CONTAINER =
            CONTAINERS.register("catalyst_identifier_assembler",
                    CatalystIdentifierAssemblerBlockEntity.class,
                    CatalystIdentifierAssemblerContainer::new);
    public static final ContainerTypeRegistryObject<
            ImbuementFactoryContainer> IMBUEMENT_FACTORY_CONTAINER =
            CONTAINERS.register("imbuement_factory",
                    ImbuementFactoryBlockEntity.class,
                    ImbuementFactoryContainer::new);

    public static final Machine<SourceAmplifierBlockEntity>
            SOURCE_AMPLIFIER_TYPE =
            Machine.MachineBuilder.createMachine(
                            ArsNouveauRegistries::sourceGeneratorTile,
                            MagicLang.SOURCE_AMPLIFIER)
                    .withGui(() -> SOURCE_AMPLIFIER_CONTAINER)
                    .withEnergyConfig(() -> 500L, () -> 4_000_000L)
                    .withSideConfig(TransmissionType.ITEM,
                            TransmissionType.ENERGY)
                    .withSupportedUpgrades(sourceAmplifierSupportedUpgrades())
                    .build();
    public static final Machine<FeSourceConverterBlockEntity>
            SOURCE_CONVERTER_TYPE =
            Machine.MachineBuilder.createMachine(
                            ArsNouveauRegistries::sourceConverterTile,
                            MagicLang.SOURCE_CONVERTER)
                    .withGui(() -> SOURCE_CONVERTER_CONTAINER)
                    .withEnergyConfig(
                            () -> (long) ArsNouveauMachineConfig
                                    .SOURCE_CONVERTER_FE_PER_TICK,
                            () -> ArsNouveauMachineConfig
                                    .SOURCE_CONVERTER_ENERGY_CAPACITY)
                    .withSideConfig(TransmissionType.ITEM,
                            TransmissionType.ENERGY)
                    .withSupportedUpgrades(
                            sourceConverterSupportedUpgrades())
                    .build();
    public static final Machine<CatalystIdentifierAssemblerBlockEntity>
            CATALYST_IDENTIFIER_ASSEMBLER_TYPE =
            Machine.MachineBuilder.createMachine(
                            ArsNouveauRegistries::catalystIdentifierAssemblerTile,
                            MagicLang.CATALYST_IDENTIFIER_ASSEMBLER)
                    .withGui(() -> CATALYST_IDENTIFIER_ASSEMBLER_CONTAINER)
                    .withEnergyConfig(() -> 300L, () -> 2_000_000L)
                    .withSideConfig(TransmissionType.ITEM,
                            TransmissionType.ENERGY)
                    .withSupportedUpgrades(arsUtilitySupportedUpgrades())
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
                    .withSupportedUpgrades(arsSupportedUpgrades())
                    .with(new AttributeUpgradeable(
                            ArsNouveauRegistries::
                                    basicImbuementFactoryBlock))
                    .build();
    public static final Machine.FactoryMachine<ImbuementFactoryBlockEntity>
            BASIC_IMBUEMENT_FACTORY_TYPE = createImbuementFactory(
                    FactoryTier.BASIC, MagicLang.BASIC_IMBUEMENT_FACTORY,
                    ArsNouveauRegistries::basicImbuementFactoryTile,
                    ArsNouveauRegistries::advancedImbuementFactoryBlock);
    public static final Machine.FactoryMachine<ImbuementFactoryBlockEntity>
            ADVANCED_IMBUEMENT_FACTORY_TYPE = createImbuementFactory(
                    FactoryTier.ADVANCED, MagicLang.ADVANCED_IMBUEMENT_FACTORY,
                    ArsNouveauRegistries::advancedImbuementFactoryTile,
                    ArsNouveauRegistries::eliteImbuementFactoryBlock);
    public static final Machine.FactoryMachine<ImbuementFactoryBlockEntity>
            ELITE_IMBUEMENT_FACTORY_TYPE = createImbuementFactory(
                    FactoryTier.ELITE, MagicLang.ELITE_IMBUEMENT_FACTORY,
                    ArsNouveauRegistries::eliteImbuementFactoryTile,
                    ArsNouveauRegistries::ultimateImbuementFactoryBlock);
    public static final Machine.FactoryMachine<ImbuementFactoryBlockEntity>
            ULTIMATE_IMBUEMENT_FACTORY_TYPE = createImbuementFactory(
                    FactoryTier.ULTIMATE, MagicLang.ULTIMATE_IMBUEMENT_FACTORY,
                    ArsNouveauRegistries::ultimateImbuementFactoryTile,
                    null);
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
                    .withSupportedUpgrades(arsSupportedUpgrades())
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
                    .withSupportedUpgrades(drygmySupportedUpgrades())
                    .build();

    public static final BlockRegistryObject<
            NativeMachineBlock<SourceAmplifierBlockEntity>, BlockItem>
            SOURCE_AMPLIFIER_BLOCK =
            BLOCKS.register("source_generator",
                    () -> new NativeMachineBlock<>(SOURCE_AMPLIFIER_TYPE,
                            BlockBehaviour.Properties.of().strength(4.0F)
                                    .requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<
            NativeMachineBlock<FeSourceConverterBlockEntity>, BlockItem>
            SOURCE_CONVERTER_BLOCK =
            BLOCKS.register("source_converter",
                    () -> new NativeMachineBlock<>(SOURCE_CONVERTER_TYPE,
                            BlockBehaviour.Properties.of().strength(4.0F)
                                    .requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<
            NativeMachineBlock<CatalystIdentifierAssemblerBlockEntity>,
            BlockItem> CATALYST_IDENTIFIER_ASSEMBLER_BLOCK =
            BLOCKS.register("catalyst_identifier_assembler",
                    () -> new NativeMachineBlock<>(
                            CATALYST_IDENTIFIER_ASSEMBLER_TYPE,
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
            NativeMachineBlock<ImbuementFactoryBlockEntity>, BlockItem>
            BASIC_IMBUEMENT_FACTORY_BLOCK =
            registerImbuementFactoryBlock("basic_imbuement_factory",
                    BASIC_IMBUEMENT_FACTORY_TYPE);
    public static final BlockRegistryObject<
            NativeMachineBlock<ImbuementFactoryBlockEntity>, BlockItem>
            ADVANCED_IMBUEMENT_FACTORY_BLOCK =
            registerImbuementFactoryBlock("advanced_imbuement_factory",
                    ADVANCED_IMBUEMENT_FACTORY_TYPE);
    public static final BlockRegistryObject<
            NativeMachineBlock<ImbuementFactoryBlockEntity>, BlockItem>
            ELITE_IMBUEMENT_FACTORY_BLOCK =
            registerImbuementFactoryBlock("elite_imbuement_factory",
                    ELITE_IMBUEMENT_FACTORY_TYPE);
    public static final BlockRegistryObject<
            NativeMachineBlock<ImbuementFactoryBlockEntity>, BlockItem>
            ULTIMATE_IMBUEMENT_FACTORY_BLOCK =
            registerImbuementFactoryBlock("ultimate_imbuement_factory",
                    ULTIMATE_IMBUEMENT_FACTORY_TYPE);
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
            FeSourceConverterBlockEntity> SOURCE_CONVERTER_TILE =
            TILES.mekBuilder(SOURCE_CONVERTER_BLOCK,
                            FeSourceConverterBlockEntity::new)
                    .serverTicker((level, pos, state, tile) ->
                            mekanism.common.tile.base.TileEntityMekanism
                                    .tickServer(level, pos, state, tile))
                    .build();
    public static final TileEntityTypeRegistryObject<
            CatalystIdentifierAssemblerBlockEntity>
            CATALYST_IDENTIFIER_ASSEMBLER_TILE =
            TILES.mekBuilder(CATALYST_IDENTIFIER_ASSEMBLER_BLOCK,
                            CatalystIdentifierAssemblerBlockEntity::new)
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
            ImbuementFactoryBlockEntity> BASIC_IMBUEMENT_FACTORY_TILE =
            factoryTile(BASIC_IMBUEMENT_FACTORY_BLOCK);
    public static final TileEntityTypeRegistryObject<
            ImbuementFactoryBlockEntity> ADVANCED_IMBUEMENT_FACTORY_TILE =
            factoryTile(ADVANCED_IMBUEMENT_FACTORY_BLOCK);
    public static final TileEntityTypeRegistryObject<
            ImbuementFactoryBlockEntity> ELITE_IMBUEMENT_FACTORY_TILE =
            factoryTile(ELITE_IMBUEMENT_FACTORY_BLOCK);
    public static final TileEntityTypeRegistryObject<
            ImbuementFactoryBlockEntity> ULTIMATE_IMBUEMENT_FACTORY_TILE =
            factoryTile(ULTIMATE_IMBUEMENT_FACTORY_BLOCK);
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
    public static final TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> MAGIC_SOURCE_PIPE_TILE =
            registerMagicSourcePipeTile(MAGIC_SOURCE_PIPE_BLOCK);
    public static final TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> ADVANCED_MAGIC_SOURCE_PIPE_TILE =
            registerMagicSourcePipeTile(ADVANCED_MAGIC_SOURCE_PIPE_BLOCK);
    public static final TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> ELITE_MAGIC_SOURCE_PIPE_TILE =
            registerMagicSourcePipeTile(ELITE_MAGIC_SOURCE_PIPE_BLOCK);
    public static final TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> ULTIMATE_MAGIC_SOURCE_PIPE_TILE =
            registerMagicSourcePipeTile(ULTIMATE_MAGIC_SOURCE_PIPE_BLOCK);
    public static final TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> ABSOLUTE_MAGIC_SOURCE_PIPE_TILE =
            registerMagicSourcePipeTile(ABSOLUTE_MAGIC_SOURCE_PIPE_BLOCK);
    public static final TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> SUPREME_MAGIC_SOURCE_PIPE_TILE =
            registerMagicSourcePipeTile(SUPREME_MAGIC_SOURCE_PIPE_BLOCK);
    public static final TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> COSMIC_MAGIC_SOURCE_PIPE_TILE =
            registerMagicSourcePipeTile(COSMIC_MAGIC_SOURCE_PIPE_BLOCK);
    public static final TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> INFINITE_MAGIC_SOURCE_PIPE_TILE =
            registerMagicSourcePipeTile(INFINITE_MAGIC_SOURCE_PIPE_BLOCK);

    private ArsNouveauRegistries() {
    }

    private static BlockTypeTile<MagicSourcePipeBlockEntity>
    createMagicSourcePipeType(
            Supplier<TileEntityTypeRegistryObject<
                    MagicSourcePipeBlockEntity>> tile,
            MagicLang lang, MagicSourcePipeTier tier) {
        return BlockTileBuilder.createBlock(tile, lang)
                .with(new AttributeTier<>(tier.mekanismTier()),
                        new AttributeMagicSourcePipeTier(tier))
                .build();
    }

    private static BlockRegistryObject<MagicSourcePipeBlock,
            MagicSourcePipeItem>
    registerMagicSourcePipeBlock(
            String name,
            BlockTypeTile<MagicSourcePipeBlockEntity> type,
            MagicSourcePipeTier tier) {
        return BLOCKS.register(name, () -> new MagicSourcePipeBlock(type),
                (block, properties) -> new MagicSourcePipeItem(
                        block, tier, properties));
    }

    private static TileEntityTypeRegistryObject<MagicSourcePipeBlockEntity>
    registerMagicSourcePipeTile(
            BlockRegistryObject<MagicSourcePipeBlock,
                    MagicSourcePipeItem> block) {
        return TILES.builder(block,
                        (pos, state) -> new MagicSourcePipeBlockEntity(
                                block.get().builtInRegistryHolder(),
                                pos, state))
                .serverTicker((level, pos, state, tile) ->
                        mekanism.common.tile.transmitter
                                .TileEntityTransmitter.tickServer(
                                        level, pos, state, tile))
                .withSimple(Capabilities.ALLOY_INTERACTION)
                .with(Capabilities.CONFIGURABLE,
                        mekanism.common.tile.transmitter
                                .TileEntityTransmitter
                                .CONFIGURABLE_PROVIDER)
                .with(CapabilityRegistry.SOURCE_CAPABILITY,
                        (tile, side) -> tile.getSourceStorage(side))
                .build();
    }

    private static TileEntityTypeRegistryObject<SourceAmplifierBlockEntity>
    sourceGeneratorTile() {
        return SOURCE_AMPLIFIER_TILE;
    }

    private static TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> magicSourcePipeTile() {
        return MAGIC_SOURCE_PIPE_TILE;
    }

    private static TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> advancedMagicSourcePipeTile() {
        return ADVANCED_MAGIC_SOURCE_PIPE_TILE;
    }

    private static TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> eliteMagicSourcePipeTile() {
        return ELITE_MAGIC_SOURCE_PIPE_TILE;
    }

    private static TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> ultimateMagicSourcePipeTile() {
        return ULTIMATE_MAGIC_SOURCE_PIPE_TILE;
    }

    private static TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> absoluteMagicSourcePipeTile() {
        return ABSOLUTE_MAGIC_SOURCE_PIPE_TILE;
    }

    private static TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> supremeMagicSourcePipeTile() {
        return SUPREME_MAGIC_SOURCE_PIPE_TILE;
    }

    private static TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> cosmicMagicSourcePipeTile() {
        return COSMIC_MAGIC_SOURCE_PIPE_TILE;
    }

    private static TileEntityTypeRegistryObject<
            MagicSourcePipeBlockEntity> infiniteMagicSourcePipeTile() {
        return INFINITE_MAGIC_SOURCE_PIPE_TILE;
    }

    private static TileEntityTypeRegistryObject<FeSourceConverterBlockEntity>
    sourceConverterTile() {
        return SOURCE_CONVERTER_TILE;
    }

    private static TileEntityTypeRegistryObject<
            CatalystIdentifierAssemblerBlockEntity>
    catalystIdentifierAssemblerTile() {
        return CATALYST_IDENTIFIER_ASSEMBLER_TILE;
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

    private static Machine.FactoryMachine<ImbuementFactoryBlockEntity>
    createImbuementFactory(FactoryTier tier, MagicLang lang,
                           java.util.function.Supplier<
                                   TileEntityTypeRegistryObject<
                                           ImbuementFactoryBlockEntity>> tile,
                           Supplier<BlockRegistryObject<?, ?>> next) {
        Machine.MachineBuilder<
                Machine.FactoryMachine<ImbuementFactoryBlockEntity>,
                ImbuementFactoryBlockEntity, ?> builder =
                Machine.MachineBuilder.createFactoryMachine(tile,
                        lang, FactoryType.SMELTING)
                .withGui(() -> IMBUEMENT_FACTORY_CONTAINER)
                .withEnergyConfig(() -> 600L * tier.processes,
                        () -> 4_000_000L)
                .withSideConfig(TransmissionType.ITEM,
                        TransmissionType.ENERGY)
                .withSupportedUpgrades(arsSupportedUpgrades())
                .with(new AttributeTier<>(tier));
        if (next != null) {
            builder.with(new AttributeUpgradeable(next));
        } else {
            Attribute extras = optionalExtrasImbuementUpgrade();
            if (extras != null) {
                builder.with(extras);
            }
        }
        return builder.build();
    }

    private static BlockRegistryObject<
            NativeMachineBlock<ImbuementFactoryBlockEntity>, BlockItem>
    registerImbuementFactoryBlock(
            String name,
            Machine.FactoryMachine<ImbuementFactoryBlockEntity> type) {
        return BLOCKS.register(name,
                () -> new NativeMachineBlock<>(type,
                        BlockBehaviour.Properties.of().strength(4.0F)
                                .requiresCorrectToolForDrops()));
    }

    private static TileEntityTypeRegistryObject<
            ImbuementFactoryBlockEntity> factoryTile(
            BlockRegistryObject<
                    NativeMachineBlock<ImbuementFactoryBlockEntity>,
                    BlockItem> block) {
        return TILES.mekBuilder(block,
                        (pos, state) -> new ImbuementFactoryBlockEntity(
                                block.get().builtInRegistryHolder(), pos, state))
                .serverTicker((level, pos, state, tile) ->
                        mekanism.common.tile.base.TileEntityMekanism
                                .tickServer(level, pos, state, tile))
                .build();
    }

    private static TileEntityTypeRegistryObject<
            ImbuementFactoryBlockEntity> basicImbuementFactoryTile() {
        return BASIC_IMBUEMENT_FACTORY_TILE;
    }

    private static TileEntityTypeRegistryObject<
            ImbuementFactoryBlockEntity> advancedImbuementFactoryTile() {
        return ADVANCED_IMBUEMENT_FACTORY_TILE;
    }

    private static TileEntityTypeRegistryObject<
            ImbuementFactoryBlockEntity> eliteImbuementFactoryTile() {
        return ELITE_IMBUEMENT_FACTORY_TILE;
    }

    private static TileEntityTypeRegistryObject<
            ImbuementFactoryBlockEntity> ultimateImbuementFactoryTile() {
        return ULTIMATE_IMBUEMENT_FACTORY_TILE;
    }

    private static BlockRegistryObject<?, ?>
    basicImbuementFactoryBlock() {
        return BASIC_IMBUEMENT_FACTORY_BLOCK;
    }

    private static BlockRegistryObject<?, ?>
    advancedImbuementFactoryBlock() {
        return ADVANCED_IMBUEMENT_FACTORY_BLOCK;
    }

    private static BlockRegistryObject<?, ?>
    eliteImbuementFactoryBlock() {
        return ELITE_IMBUEMENT_FACTORY_BLOCK;
    }

    private static BlockRegistryObject<?, ?>
    ultimateImbuementFactoryBlock() {
        return ULTIMATE_IMBUEMENT_FACTORY_BLOCK;
    }

    private static Attribute optionalExtrasImbuementUpgrade() {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism_extras")) {
            return null;
        }
        try {
            Class<?> attributeClass = Class.forName(
                    "com.jerry.mekextras.common.block.attribute."
                            + "ExtraAttributeUpgradeable");
            Constructor<?> constructor = attributeClass.getConstructor(
                    Supplier.class);
            Supplier<BlockRegistryObject<?, ?>> target = () -> {
                try {
                    Class<?> integration = Class.forName(
                            "com.example.mekanismmagic.integration.mekextras."
                                    + "MekanismExtrasImbuementFactories");
                    Field field = integration.getField("ABSOLUTE_BLOCK");
                    return (BlockRegistryObject<?, ?>) field.get(null);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
            };
            return (Attribute) constructor.newInstance(target);
        } catch (ReflectiveOperationException | LinkageError failure) {
            MekanismMagic.LOGGER.warn(
                    "Mekanism Extras imbuement factory upgrade is unavailable",
                    failure);
            return null;
        }
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        TILES.register(modBus);
        CONTAINERS.register(modBus);
        modBus.addListener(ArsNouveauRegistries::registerCapabilities);
    }

    public static ContainerTypeRegistryObject<?> containerFor(
            ArsSourceMachineBlockEntity tile) {
        if (tile instanceof SourceAmplifierBlockEntity) {
            return SOURCE_AMPLIFIER_CONTAINER;
        }
        if (tile instanceof FeSourceConverterBlockEntity) {
            return SOURCE_CONVERTER_CONTAINER;
        }
        if (tile instanceof ImbuementProcessorBlockEntity) {
            return IMBUEMENT_PROCESSOR_CONTAINER;
        }
        if (tile instanceof EnchantingApparatusProcessorBlockEntity) {
            return ENCHANTING_APPARATUS_PROCESSOR_CONTAINER;
        }
        if (tile instanceof DrygmySimulatorBlockEntity) {
            return DRYGMY_SIMULATOR_CONTAINER;
        }
        throw new IllegalArgumentException("Unknown Ars Source machine");
    }

    private static Upgrade[] arsSupportedUpgrades() {
        return MEKANISM_EXTRAS_CREATIVE_UPGRADE == null
                ? new Upgrade[]{Upgrade.SPEED, Upgrade.ENERGY,
                        MagicUpgrades.creativeMagic()}
                : new Upgrade[]{Upgrade.SPEED, Upgrade.ENERGY,
                        MagicUpgrades.creativeMagic(),
                        MEKANISM_EXTRAS_CREATIVE_UPGRADE};
    }

    private static Upgrade[] sourceAmplifierSupportedUpgrades() {
        // This machine has no internal Source tank, so a creative Source
        // upgrade has no meaningful state to provide.
        return new Upgrade[]{Upgrade.SPEED, Upgrade.ENERGY};
    }

    private static Upgrade[] arsUtilitySupportedUpgrades() {
        // Utility machines that do not spend Source have nothing for the
        // creative-magic upgrade to bypass. Preserve Mekanism Extras'
        // independent creative-energy support when present.
        return MEKANISM_EXTRAS_CREATIVE_UPGRADE == null
                ? new Upgrade[]{Upgrade.SPEED, Upgrade.ENERGY}
                : new Upgrade[]{Upgrade.SPEED, Upgrade.ENERGY,
                        MEKANISM_EXTRAS_CREATIVE_UPGRADE};
    }

    private static Upgrade[] drygmySupportedUpgrades() {
        java.util.List<Upgrade> upgrades = new java.util.ArrayList<>(
                java.util.List.of(arsSupportedUpgrades()));
        Upgrade stackUpgrade = NativeMekanismRegistries
                .dimensionMinerStackUpgrade();
        if (stackUpgrade != null && !upgrades.contains(stackUpgrade)) {
            upgrades.add(stackUpgrade);
        }
        return upgrades.toArray(Upgrade[]::new);
    }

    private static Upgrade[] sourceConverterSupportedUpgrades() {
        java.util.List<Upgrade> upgrades = new java.util.ArrayList<>(
                java.util.List.of(arsUtilitySupportedUpgrades()));
        Upgrade stackUpgrade = NativeMekanismRegistries
                .dimensionMinerStackUpgrade();
        if (stackUpgrade != null && !upgrades.contains(stackUpgrade)) {
            upgrades.add(stackUpgrade);
        }
        return upgrades.toArray(Upgrade[]::new);
    }

    private static Upgrade optionalCreativeSourceUpgrade() {
        if (!net.neoforged.fml.ModList.get().isLoaded("mekanism_extras")) {
            return null;
        }
        try {
            Class<?> extraUpgrade = Class.forName(
                    "com.jerry.mekextras.api.ExtraUpgrade");
            Upgrade creative = (Upgrade) extraUpgrade
                    .getField("CREATIVE").get(null);
            MekanismMagic.LOGGER.info(
                    "Enabled Mekanism Extras creative upgrade support "
                            + "for Ars Nouveau Source machines");
            return creative;
        } catch (ReflectiveOperationException | LinkageError failure) {
            MekanismMagic.LOGGER.warn(
                    "Mekanism Extras creative upgrade is unavailable; "
                            + "Ars machines will require Source normally",
                    failure);
            return null;
        }
    }

    private static void registerCapabilities(
            RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                SOURCE_CONVERTER_TILE.get(),
                (tile, side) -> side == null
                        ? tile.getSourceStorage()
                        : tile.getSourceStorage(side));
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                IMBUEMENT_PROCESSOR_TILE.get(),
                (tile, side) -> side == null
                        ? tile.getSourceStorage()
                        : tile.getSourceStorage(side));
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                BASIC_IMBUEMENT_FACTORY_TILE.get(),
                (tile, side) -> side == null
                        ? tile.getSourceStorage()
                        : tile.getSourceStorage(side));
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                ADVANCED_IMBUEMENT_FACTORY_TILE.get(),
                (tile, side) -> side == null
                        ? tile.getSourceStorage()
                        : tile.getSourceStorage(side));
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                ELITE_IMBUEMENT_FACTORY_TILE.get(),
                (tile, side) -> side == null
                        ? tile.getSourceStorage()
                        : tile.getSourceStorage(side));
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                ULTIMATE_IMBUEMENT_FACTORY_TILE.get(),
                (tile, side) -> side == null
                        ? tile.getSourceStorage()
                        : tile.getSourceStorage(side));
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                ENCHANTING_APPARATUS_PROCESSOR_TILE.get(),
                (tile, side) -> side == null
                        ? tile.getSourceStorage()
                        : tile.getSourceStorage(side));
        event.registerBlockEntity(
                CapabilityRegistry.SOURCE_CAPABILITY,
                DRYGMY_SIMULATOR_TILE.get(),
                (tile, side) -> side == null
                        ? tile.getSourceStorage()
                        : tile.getSourceStorage(side));
    }
}
