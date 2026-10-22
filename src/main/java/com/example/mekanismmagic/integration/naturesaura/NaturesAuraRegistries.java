package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.MagicLang;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.block.NativeMachineBlock;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import mekanism.api.Upgrade;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeTier;
import mekanism.common.block.attribute.AttributeUpgradeable;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tier.FactoryTier;
import mekanism.common.tier.PipeTier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** Registries loaded only when Nature's Aura is present. */
public final class NaturesAuraRegistries {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MekanismMagic.MOD_ID);
    public static final DeferredHolder<Item, com.example.mekanismmagic.item.RitualSpawnEggItem> ANIMAL_SPAWN_EGG =
            ITEMS.register("animal_spawn_egg", () -> new com.example.mekanismmagic.item.RitualSpawnEggItem(new Item.Properties().stacksTo(64)));
    public static final DeferredHolder<Item, NatureAltarContextItem>
            NATURE_ALTAR_CONTEXT_ITEM = ITEMS.register(
                    "nature_altar_context",
                    () -> new NatureAltarContextItem(
                            new Item.Properties().stacksTo(1)));
    public static final BlockDeferredRegister BLOCKS =
            new BlockDeferredRegister(MekanismMagic.MOD_ID);
    public static final TileEntityTypeDeferredRegister TILES =
            new TileEntityTypeDeferredRegister(MekanismMagic.MOD_ID);
    public static final ContainerTypeDeferredRegister CONTAINERS =
            new ContainerTypeDeferredRegister(MekanismMagic.MOD_ID);

    public static final ContainerTypeRegistryObject<FeAuraConverterContainer> FE_AURA_CONVERTER_CONTAINER =
            CONTAINERS.register("fe_aura_converter", FeAuraConverterBlockEntity.class, FeAuraConverterContainer::new);
    public static final ContainerTypeRegistryObject<AuraFuelGeneratorContainer> AURA_FUEL_GENERATOR_CONTAINER =
            CONTAINERS.register("aura_fuel_generator", AuraFuelGeneratorBlockEntity.class, AuraFuelGeneratorContainer::new);
    public static final Machine<FeAuraConverterBlockEntity> FE_AURA_CONVERTER_TYPE =
            Machine.MachineBuilder.createMachine(NaturesAuraRegistries::feAuraConverterTile, MagicLang.FE_AURA_CONVERTER)
                    .withGui(() -> FE_AURA_CONVERTER_CONTAINER)
                    .withEnergyConfig(FeAuraConverterBlockEntity::baseEnergyUsage,
                            () -> NatureAuraMachineConfig.FE_CONVERTER_ENERGY_CAPACITY)
                    .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY).build();
    public static final Machine<AuraFuelGeneratorBlockEntity> AURA_FUEL_GENERATOR_TYPE =
            Machine.MachineBuilder.createMachine(NaturesAuraRegistries::auraFuelGeneratorTile, MagicLang.AURA_FUEL_GENERATOR)
                    .withGui(() -> AURA_FUEL_GENERATOR_CONTAINER)
                    .withSideConfig(TransmissionType.ITEM)
                    .withSupportedUpgrades(Upgrade.SPEED).build();
    public static final BlockRegistryObject<NativeMachineBlock<FeAuraConverterBlockEntity>, BlockItem> FE_AURA_CONVERTER_BLOCK =
            BLOCKS.register("fe_aura_converter", () -> new NativeMachineBlock<>(FE_AURA_CONVERTER_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F).requiresCorrectToolForDrops().noOcclusion()));
    public static final BlockRegistryObject<NativeMachineBlock<AuraFuelGeneratorBlockEntity>, BlockItem> AURA_FUEL_GENERATOR_BLOCK =
            BLOCKS.register("aura_fuel_generator", () -> new NativeMachineBlock<>(AURA_FUEL_GENERATOR_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F).requiresCorrectToolForDrops().noOcclusion()));
    public static final TileEntityTypeRegistryObject<FeAuraConverterBlockEntity> FE_AURA_CONVERTER_TILE =
            TILES.mekBuilder(FE_AURA_CONVERTER_BLOCK, FeAuraConverterBlockEntity::new)
                    .serverTicker((level, pos, state, tile) -> mekanism.common.tile.base.TileEntityMekanism
                            .tickServer(level, pos, state, tile)).build();
    public static final TileEntityTypeRegistryObject<AuraFuelGeneratorBlockEntity> AURA_FUEL_GENERATOR_TILE =
            TILES.mekBuilder(AURA_FUEL_GENERATOR_BLOCK, AuraFuelGeneratorBlockEntity::new)
                    .serverTicker((level, pos, state, tile) -> mekanism.common.tile.base.TileEntityMekanism
                            .tickServer(level, pos, state, tile)).build();

    private static TileEntityTypeRegistryObject<FeAuraConverterBlockEntity> feAuraConverterTile() {
        return FE_AURA_CONVERTER_TILE;
    }
    private static TileEntityTypeRegistryObject<AuraFuelGeneratorBlockEntity> auraFuelGeneratorTile() {
        return AURA_FUEL_GENERATOR_TILE;
    }

    public static final ContainerTypeRegistryObject<TreeRitualProcessorContainer> TREE_RITUAL_PROCESSOR_CONTAINER =
            CONTAINERS.register("tree_ritual_processor", TreeRitualProcessorBlockEntity.class, TreeRitualProcessorContainer::new);
    public static final Machine<TreeRitualProcessorBlockEntity> TREE_RITUAL_PROCESSOR_TYPE =
            Machine.MachineBuilder.createMachine(NaturesAuraRegistries::tree_ritual_processorTile, MagicLang.TREE_RITUAL_PROCESSOR)
                    .withGui(() -> TREE_RITUAL_PROCESSOR_CONTAINER)
                    .withEnergyConfig(() -> (long) NatureAuraMachineConfig.TREE_RITUAL_BASE_ENERGY_PER_TICK,
                            () -> 2_000_000L)
                    .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY)
                    .build();
    public static final BlockRegistryObject<NativeMachineBlock<TreeRitualProcessorBlockEntity>, BlockItem> TREE_RITUAL_PROCESSOR_BLOCK =
            BLOCKS.register("tree_ritual_processor", () -> new NativeMachineBlock<>(TREE_RITUAL_PROCESSOR_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F).requiresCorrectToolForDrops().noOcclusion()));
    public static final TileEntityTypeRegistryObject<TreeRitualProcessorBlockEntity> TREE_RITUAL_PROCESSOR_TILE =
            TILES.mekBuilder(TREE_RITUAL_PROCESSOR_BLOCK, TreeRitualProcessorBlockEntity::new)
                    .serverTicker((level, pos, state, tile) -> mekanism.common.tile.base.TileEntityMekanism
                            .tickServer(level, pos, state, tile)).build();

    private static TileEntityTypeRegistryObject<TreeRitualProcessorBlockEntity> tree_ritual_processorTile() {
        return TREE_RITUAL_PROCESSOR_TILE;
    }

    public static final ContainerTypeRegistryObject<AnimalSpawnerProcessorContainer> ANIMAL_SPAWNER_PROCESSOR_CONTAINER =
            CONTAINERS.register("animal_spawner_processor", AnimalSpawnerProcessorBlockEntity.class, AnimalSpawnerProcessorContainer::new);
    public static final Machine<AnimalSpawnerProcessorBlockEntity> ANIMAL_SPAWNER_PROCESSOR_TYPE =
            Machine.MachineBuilder.createMachine(NaturesAuraRegistries::animal_spawner_processorTile, MagicLang.ANIMAL_SPAWNER_PROCESSOR)
                    .withGui(() -> ANIMAL_SPAWNER_PROCESSOR_CONTAINER)
                    .withEnergyConfig(() -> (long) AnimalSpawnerProcessorBlockEntity.BASE_ENERGY_PER_TICK,
                            () -> 2_000_000L)
                    .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY, MagicUpgrades.creativeMagic())
                    .build();
    public static final BlockRegistryObject<NativeMachineBlock<AnimalSpawnerProcessorBlockEntity>, BlockItem> ANIMAL_SPAWNER_PROCESSOR_BLOCK =
            BLOCKS.register("animal_spawner_processor", () -> new NativeMachineBlock<>(ANIMAL_SPAWNER_PROCESSOR_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F).requiresCorrectToolForDrops().noOcclusion()));
    public static final TileEntityTypeRegistryObject<AnimalSpawnerProcessorBlockEntity> ANIMAL_SPAWNER_PROCESSOR_TILE =
            TILES.mekBuilder(ANIMAL_SPAWNER_PROCESSOR_BLOCK, AnimalSpawnerProcessorBlockEntity::new)
                    .serverTicker((level, pos, state, tile) -> mekanism.common.tile.base.TileEntityMekanism
                            .tickServer(level, pos, state, tile)).build();

    private static TileEntityTypeRegistryObject<AnimalSpawnerProcessorBlockEntity> animal_spawner_processorTile() {
        return ANIMAL_SPAWNER_PROCESSOR_TILE;
    }

    public static final ContainerTypeRegistryObject<OfferingProcessorContainer>
            OFFERING_PROCESSOR_CONTAINER = CONTAINERS.register("offering_processor",
            OfferingProcessorBlockEntity.class, OfferingProcessorContainer::new);
    public static final Machine<OfferingProcessorBlockEntity> OFFERING_PROCESSOR_TYPE =
            Machine.MachineBuilder.createMachine(NaturesAuraRegistries::offeringProcessorTile,
                            MagicLang.OFFERING_PROCESSOR)
                    .withGui(() -> OFFERING_PROCESSOR_CONTAINER)
                    .withEnergyConfig(() -> (long) NatureAuraMachineConfig.OFFERING_BASE_ENERGY_PER_TICK,
                            () -> NatureAuraMachineConfig.OFFERING_ENERGY_CAPACITY)
                    .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY)
                    .build();
    public static final BlockRegistryObject<NativeMachineBlock<OfferingProcessorBlockEntity>, BlockItem>
            OFFERING_PROCESSOR_BLOCK = BLOCKS.register("offering_processor",
            () -> new NativeMachineBlock<>(OFFERING_PROCESSOR_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F).requiresCorrectToolForDrops().noOcclusion()));
    public static final TileEntityTypeRegistryObject<OfferingProcessorBlockEntity>
            OFFERING_PROCESSOR_TILE = TILES.mekBuilder(OFFERING_PROCESSOR_BLOCK,
            OfferingProcessorBlockEntity::new)
            .serverTicker((level, pos, state, tile) -> mekanism.common.tile.base.TileEntityMekanism
                    .tickServer(level, pos, state, tile)).build();

    private static TileEntityTypeRegistryObject<OfferingProcessorBlockEntity> offeringProcessorTile() {
        return OFFERING_PROCESSOR_TILE;
    }

    public static final ContainerTypeRegistryObject<
            AuraFieldInterfaceContainer> AURA_FIELD_INTERFACE_CONTAINER =
            CONTAINERS.register("aura_field_interface",
                    AuraFieldInterfaceBlockEntity.class,
                    AuraFieldInterfaceContainer::new);
    public static final ContainerTypeRegistryObject<
            NatureAltarProcessorContainer>
            NATURE_ALTAR_PROCESSOR_CONTAINER =
            CONTAINERS.register("nature_altar_processor",
                    NatureAltarProcessorBlockEntity.class,
                    NatureAltarProcessorContainer::new);
    public static final ContainerTypeRegistryObject<
            NatureAltarFactoryContainer> NATURE_ALTAR_FACTORY_CONTAINER =
            CONTAINERS.register("nature_altar_factory",
                    NatureAltarFactoryBlockEntity.class,
                    NatureAltarFactoryContainer::new);

    public static final Machine<AuraFieldInterfaceBlockEntity>
            AURA_FIELD_INTERFACE_TYPE =
            Machine.MachineBuilder.createMachine(
                            NaturesAuraRegistries::auraFieldInterfaceTile,
                            MagicLang.AURA_FIELD_INTERFACE)
                    .withGui(() -> AURA_FIELD_INTERFACE_CONTAINER)
                    .withEnergyConfig(
                            AuraFieldInterfaceBlockEntity::baseEnergyUsage,
                            () -> NatureAuraMachineConfig.ENERGY_CAPACITY)
                    .withSideConfig(TransmissionType.ITEM,
                            TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.ENERGY)
                    .build();
    public static final Machine<NatureAltarProcessorBlockEntity>
            NATURE_ALTAR_PROCESSOR_TYPE =
            Machine.MachineBuilder.createMachine(
                            NaturesAuraRegistries::natureAltarProcessorTile,
                            MagicLang.NATURE_ALTAR_PROCESSOR)
                    .withGui(() -> NATURE_ALTAR_PROCESSOR_CONTAINER)
                    .withEnergyConfig(
                            () -> (long) NatureAuraMachineConfig
                                    .ALTAR_BASE_ENERGY_PER_TICK,
                            () -> NatureAuraMachineConfig
                                    .ALTAR_ENERGY_CAPACITY)
                    .withSideConfig(TransmissionType.ITEM,
                            TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY,
                            MagicUpgrades.creativeMagic())
                    .with(new AttributeUpgradeable(
                            NaturesAuraRegistries::
                                    basicNatureAltarFactoryBlock))
                    .build();
    public static final Machine.FactoryMachine<
            NatureAltarFactoryBlockEntity> BASIC_NATURE_ALTAR_FACTORY_TYPE =
            createNatureAltarFactory(FactoryTier.BASIC,
                    MagicLang.BASIC_NATURE_ALTAR_FACTORY,
                    NaturesAuraRegistries::basicNatureAltarFactoryTile,
                    NaturesAuraRegistries::advancedNatureAltarFactoryBlock);
    public static final Machine.FactoryMachine<
            NatureAltarFactoryBlockEntity>
            ADVANCED_NATURE_ALTAR_FACTORY_TYPE =
            createNatureAltarFactory(FactoryTier.ADVANCED,
                    MagicLang.ADVANCED_NATURE_ALTAR_FACTORY,
                    NaturesAuraRegistries::advancedNatureAltarFactoryTile,
                    NaturesAuraRegistries::eliteNatureAltarFactoryBlock);
    public static final Machine.FactoryMachine<
            NatureAltarFactoryBlockEntity> ELITE_NATURE_ALTAR_FACTORY_TYPE =
            createNatureAltarFactory(FactoryTier.ELITE,
                    MagicLang.ELITE_NATURE_ALTAR_FACTORY,
                    NaturesAuraRegistries::eliteNatureAltarFactoryTile,
                    NaturesAuraRegistries::ultimateNatureAltarFactoryBlock);
    public static final Machine.FactoryMachine<
            NatureAltarFactoryBlockEntity>
            ULTIMATE_NATURE_ALTAR_FACTORY_TYPE =
            createNatureAltarFactory(FactoryTier.ULTIMATE,
                    MagicLang.ULTIMATE_NATURE_ALTAR_FACTORY,
                    NaturesAuraRegistries::ultimateNatureAltarFactoryTile,
                    null);

    public static final BlockRegistryObject<
            NativeMachineBlock<AuraFieldInterfaceBlockEntity>, BlockItem>
            AURA_FIELD_INTERFACE_BLOCK =
            BLOCKS.register("aura_field_interface",
                    () -> new NativeMachineBlock<>(
                            AURA_FIELD_INTERFACE_TYPE,
                            BlockBehaviour.Properties.of().strength(4.0F)
                                    .requiresCorrectToolForDrops()
                                    .noOcclusion()));
    public static final BlockRegistryObject<
            NativeMachineBlock<NatureAltarProcessorBlockEntity>, BlockItem>
            NATURE_ALTAR_PROCESSOR_BLOCK =
            BLOCKS.register("nature_altar_processor",
                    () -> new NativeMachineBlock<>(
                            NATURE_ALTAR_PROCESSOR_TYPE,
                            BlockBehaviour.Properties.of().strength(4.0F)
                                    .requiresCorrectToolForDrops()
                                    .noOcclusion()));
    public static final BlockRegistryObject<
            NativeMachineBlock<NatureAltarFactoryBlockEntity>, BlockItem>
            BASIC_NATURE_ALTAR_FACTORY_BLOCK =
            BLOCKS.register("basic_nature_altar_factory",
                    () -> natureAltarFactoryBlock(
                            BASIC_NATURE_ALTAR_FACTORY_TYPE));
    public static final BlockRegistryObject<
            NativeMachineBlock<NatureAltarFactoryBlockEntity>, BlockItem>
            ADVANCED_NATURE_ALTAR_FACTORY_BLOCK =
            BLOCKS.register("advanced_nature_altar_factory",
                    () -> natureAltarFactoryBlock(
                            ADVANCED_NATURE_ALTAR_FACTORY_TYPE));
    public static final BlockRegistryObject<
            NativeMachineBlock<NatureAltarFactoryBlockEntity>, BlockItem>
            ELITE_NATURE_ALTAR_FACTORY_BLOCK =
            BLOCKS.register("elite_nature_altar_factory",
                    () -> natureAltarFactoryBlock(
                            ELITE_NATURE_ALTAR_FACTORY_TYPE));
    public static final BlockRegistryObject<
            NativeMachineBlock<NatureAltarFactoryBlockEntity>, BlockItem>
            ULTIMATE_NATURE_ALTAR_FACTORY_BLOCK =
            BLOCKS.register("ultimate_nature_altar_factory",
                    () -> natureAltarFactoryBlock(
                            ULTIMATE_NATURE_ALTAR_FACTORY_TYPE));

    public static final TileEntityTypeRegistryObject<
            AuraFieldInterfaceBlockEntity> AURA_FIELD_INTERFACE_TILE =
            TILES.mekBuilder(AURA_FIELD_INTERFACE_BLOCK,
                            AuraFieldInterfaceBlockEntity::new)
                    .serverTicker((level, pos, state, tile) ->
                            mekanism.common.tile.base.TileEntityMekanism
                                    .tickServer(level, pos, state, tile))
                    .build();
    public static final TileEntityTypeRegistryObject<
            NatureAltarProcessorBlockEntity> NATURE_ALTAR_PROCESSOR_TILE =
            TILES.mekBuilder(NATURE_ALTAR_PROCESSOR_BLOCK,
                            NatureAltarProcessorBlockEntity::new)
                    .serverTicker((level, pos, state, tile) ->
                            mekanism.common.tile.base.TileEntityMekanism
                                    .tickServer(level, pos, state, tile))
                    .build();
    public static final TileEntityTypeRegistryObject<
            NatureAltarFactoryBlockEntity> BASIC_NATURE_ALTAR_FACTORY_TILE =
            factoryTile(BASIC_NATURE_ALTAR_FACTORY_BLOCK);
    public static final TileEntityTypeRegistryObject<
            NatureAltarFactoryBlockEntity>
            ADVANCED_NATURE_ALTAR_FACTORY_TILE =
            factoryTile(ADVANCED_NATURE_ALTAR_FACTORY_BLOCK);
    public static final TileEntityTypeRegistryObject<
            NatureAltarFactoryBlockEntity> ELITE_NATURE_ALTAR_FACTORY_TILE =
            factoryTile(ELITE_NATURE_ALTAR_FACTORY_BLOCK);
    public static final TileEntityTypeRegistryObject<
            NatureAltarFactoryBlockEntity>
            ULTIMATE_NATURE_ALTAR_FACTORY_TILE =
            factoryTile(ULTIMATE_NATURE_ALTAR_FACTORY_BLOCK);

    private NaturesAuraRegistries() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        TILES.register(modBus);
        CONTAINERS.register(modBus);
        modBus.addListener(NaturesAuraRegistries::registerCapabilities);
    }

    private static void registerCapabilities(
            RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                FE_AURA_CONVERTER_TILE.get(), (tile, side) -> tile.getAuraContainer(side));
        event.registerBlockEntity(NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                AURA_FUEL_GENERATOR_TILE.get(), (tile, side) -> tile.getAuraContainer(side));
        event.registerBlockEntity(NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                ANIMAL_SPAWNER_PROCESSOR_TILE.get(), (tile, side) -> tile.getAuraContainer(side));
        event.registerBlockEntity(
                NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                AURA_FIELD_INTERFACE_TILE.get(),
                (tile, side) -> tile.getAuraContainer(side));
        event.registerBlockEntity(
                NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                NATURE_ALTAR_PROCESSOR_TILE.get(),
                (tile, side) -> tile.getAuraContainer(side));
        event.registerBlockEntity(
                NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                BASIC_NATURE_ALTAR_FACTORY_TILE.get(),
                (tile, side) -> tile.getAuraContainer(side));
        event.registerBlockEntity(
                NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                ADVANCED_NATURE_ALTAR_FACTORY_TILE.get(),
                (tile, side) -> tile.getAuraContainer(side));
        event.registerBlockEntity(
                NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                ELITE_NATURE_ALTAR_FACTORY_TILE.get(),
                (tile, side) -> tile.getAuraContainer(side));
        event.registerBlockEntity(
                NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                ULTIMATE_NATURE_ALTAR_FACTORY_TILE.get(),
                (tile, side) -> tile.getAuraContainer(side));
    }

    private static TileEntityTypeRegistryObject<
            AuraFieldInterfaceBlockEntity> auraFieldInterfaceTile() {
        return AURA_FIELD_INTERFACE_TILE;
    }

    private static TileEntityTypeRegistryObject<
            NatureAltarProcessorBlockEntity> natureAltarProcessorTile() {
        return NATURE_ALTAR_PROCESSOR_TILE;
    }

    private static Machine.FactoryMachine<NatureAltarFactoryBlockEntity>
    createNatureAltarFactory(
            FactoryTier tier, MagicLang lang,
            Supplier<TileEntityTypeRegistryObject<
                    NatureAltarFactoryBlockEntity>> tile,
            Supplier<BlockRegistryObject<?, ?>> next) {
        Machine.MachineBuilder<
                Machine.FactoryMachine<NatureAltarFactoryBlockEntity>,
                NatureAltarFactoryBlockEntity, ?> builder =
                Machine.MachineBuilder.createFactoryMachine(
                                tile, lang, FactoryType.SMELTING)
                        .withGui(() -> NATURE_ALTAR_FACTORY_CONTAINER)
                        .withEnergyConfig(
                                () -> (long) NatureAuraMachineConfig
                                        .ALTAR_BASE_ENERGY_PER_TICK,
                                () -> NatureAuraMachineConfig
                                        .altarFactoryEnergyCapacity(tier))
                        .withSideConfig(TransmissionType.ITEM,
                                TransmissionType.ENERGY)
                        .withSupportedUpgrades(Upgrade.SPEED,
                                Upgrade.ENERGY,
                                MagicUpgrades.creativeMagic())
                        .with(new AttributeTier<>(tier));
        if (next != null) {
            builder.with(new AttributeUpgradeable(next));
        }
        return builder.build();
    }

    private static NativeMachineBlock<NatureAltarFactoryBlockEntity>
    natureAltarFactoryBlock(
            Machine.FactoryMachine<NatureAltarFactoryBlockEntity> type) {
        return new NativeMachineBlock<>(type,
                BlockBehaviour.Properties.of().strength(4.0F)
                        .requiresCorrectToolForDrops().noOcclusion());
    }

    private static TileEntityTypeRegistryObject<
            NatureAltarFactoryBlockEntity> factoryTile(
            BlockRegistryObject<
                    NativeMachineBlock<NatureAltarFactoryBlockEntity>,
                    BlockItem> block) {
        return TILES.mekBuilder(block,
                        (pos, state) -> new NatureAltarFactoryBlockEntity(
                                block.get().builtInRegistryHolder(),
                                pos, state))
                .serverTicker((level, pos, state, tile) ->
                        mekanism.common.tile.base.TileEntityMekanism
                                .tickServer(level, pos, state, tile))
                .build();
    }

    private static BlockRegistryObject<?, ?>
    basicNatureAltarFactoryBlock() {
        return BASIC_NATURE_ALTAR_FACTORY_BLOCK;
    }

    private static BlockRegistryObject<?, ?>
    advancedNatureAltarFactoryBlock() {
        return ADVANCED_NATURE_ALTAR_FACTORY_BLOCK;
    }

    private static BlockRegistryObject<?, ?>
    eliteNatureAltarFactoryBlock() {
        return ELITE_NATURE_ALTAR_FACTORY_BLOCK;
    }

    private static BlockRegistryObject<?, ?>
    ultimateNatureAltarFactoryBlock() {
        return ULTIMATE_NATURE_ALTAR_FACTORY_BLOCK;
    }

    private static TileEntityTypeRegistryObject<
            NatureAltarFactoryBlockEntity> basicNatureAltarFactoryTile() {
        return BASIC_NATURE_ALTAR_FACTORY_TILE;
    }

    private static TileEntityTypeRegistryObject<
            NatureAltarFactoryBlockEntity> advancedNatureAltarFactoryTile() {
        return ADVANCED_NATURE_ALTAR_FACTORY_TILE;
    }

    private static TileEntityTypeRegistryObject<
            NatureAltarFactoryBlockEntity> eliteNatureAltarFactoryTile() {
        return ELITE_NATURE_ALTAR_FACTORY_TILE;
    }

    private static TileEntityTypeRegistryObject<
            NatureAltarFactoryBlockEntity> ultimateNatureAltarFactoryTile() {
        return ULTIMATE_NATURE_ALTAR_FACTORY_TILE;
    }
}
