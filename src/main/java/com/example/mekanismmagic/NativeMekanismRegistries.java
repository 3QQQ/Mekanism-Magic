package com.example.mekanismmagic;

import com.example.mekanismmagic.block.NativeMachineBlock;
import com.example.mekanismmagic.blockentity.NativeRitualEngineBlockEntity;
import com.example.mekanismmagic.blockentity.NativeMiniRitualAssemblerBlockEntity;
import com.example.mekanismmagic.blockentity.NativeDimensionMinerBlockEntity;
import com.example.mekanismmagic.blockentity.NativeSpiritFactoryBlockEntity;
import com.example.mekanismmagic.blockentity.NativeSpiritProcessorBlockEntity;
import com.example.mekanismmagic.container.NativeSpiritFactoryContainer;
import com.example.mekanismmagic.container.NativeMiniRitualAssemblerContainer;
import com.example.mekanismmagic.integration.ModCompatibility;
import mekanism.api.Upgrade;
import mekanism.common.MekanismLang;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeTier;
import mekanism.common.block.attribute.AttributeUpgradeable;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tier.FactoryTier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Native Mekanism registration layer. It is kept separate while the legacy
 * prototype is migrated, so all future machines can share these definitions.
 */
public final class NativeMekanismRegistries {
    public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(MekanismMagic.MOD_ID);
    public static final TileEntityTypeDeferredRegister TILES =
            new TileEntityTypeDeferredRegister(MekanismMagic.MOD_ID);
    public static final ContainerTypeDeferredRegister CONTAINERS =
            new ContainerTypeDeferredRegister(MekanismMagic.MOD_ID);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<NativeSpiritProcessorBlockEntity>>
            SPIRIT_CONTAINER = CONTAINERS.custom("spirit_processor",
            NativeSpiritProcessorBlockEntity.class).build();
    public static final ContainerTypeRegistryObject<MekanismTileContainer<NativeDimensionMinerBlockEntity>>
            DIMENSION_MINER_CONTAINER = CONTAINERS.custom("dimension_miner",
            NativeDimensionMinerBlockEntity.class).build();
    public static final ContainerTypeRegistryObject<MekanismTileContainer<NativeRitualEngineBlockEntity>>
            RITUAL_CONTAINER = CONTAINERS.custom("ritual_engine",
            NativeRitualEngineBlockEntity.class).offset(17, 42).build();
    public static final ContainerTypeRegistryObject<
            NativeMiniRitualAssemblerContainer>
            MINI_RITUAL_ASSEMBLER_CONTAINER = CONTAINERS.register(
            "mini_ritual_assembler", NativeMiniRitualAssemblerBlockEntity.class,
            NativeMiniRitualAssemblerContainer::new);
    public static final ContainerTypeRegistryObject<NativeSpiritFactoryContainer> SPIRIT_FACTORY_CONTAINER =
            CONTAINERS.register("spirit_factory", NativeSpiritFactoryBlockEntity.class,
                    (id, inventory, tile) -> new NativeSpiritFactoryContainer(id, inventory, tile));

    public static final Machine<NativeSpiritProcessorBlockEntity> SPIRIT_TYPE =
            Machine.MachineBuilder.createMachine(() -> NativeMekanismRegistries.SPIRIT_TILE,
                            MekanismLang.DESCRIPTION_CRUSHER)
                    .withGui(() -> SPIRIT_CONTAINER)
                    .withEnergyConfig(() -> mekanism.api.math.FloatingLong.create(400),
                            () -> mekanism.api.math.FloatingLong.create(1_000_000))
                    .withSupportedUpgrades(Set.of(Upgrade.SPEED, Upgrade.ENERGY))
                    .with(new AttributeUpgradeable(
                            NativeMekanismRegistries::basicSpiritFactoryBlock))
                    .build();
    public static final Machine<NativeDimensionMinerBlockEntity> DIMENSION_MINER_TYPE =
            Machine.MachineBuilder.createMachine(() -> NativeMekanismRegistries.DIMENSION_MINER_TILE,
                            MekanismLang.DESCRIPTION_DIGITAL_MINER)
                    .withGui(() -> DIMENSION_MINER_CONTAINER)
                    .withEnergyConfig(() -> mekanism.api.math.FloatingLong.create(800),
                            () -> mekanism.api.math.FloatingLong.create(4_000_000))
                    .withSupportedUpgrades(Set.of(Upgrade.SPEED, Upgrade.ENERGY))
                    .build();
    public static final Machine<NativeRitualEngineBlockEntity> RITUAL_TYPE =
            Machine.MachineBuilder.createMachine(() -> NativeMekanismRegistries.RITUAL_TILE,
                            MekanismLang.DESCRIPTION_FACTORY)
                    .withGui(() -> RITUAL_CONTAINER)
                    .withEnergyConfig(() -> mekanism.api.math.FloatingLong.create(1_200),
                            () -> mekanism.api.math.FloatingLong.create(4_000_000))
                    .withSupportedUpgrades(Set.of(Upgrade.SPEED, Upgrade.ENERGY))
                    .build();
    public static final Machine<NativeMiniRitualAssemblerBlockEntity>
            MINI_RITUAL_ASSEMBLER_TYPE =
            Machine.MachineBuilder.createMachine(
                            () -> NativeMekanismRegistries.MINI_RITUAL_ASSEMBLER_TILE,
                            MekanismLang.DESCRIPTION_FORMULAIC_ASSEMBLICATOR)
                    .withGui(() -> MINI_RITUAL_ASSEMBLER_CONTAINER)
                    .withEnergyConfig(() -> mekanism.api.math.FloatingLong.create(300),
                            () -> mekanism.api.math.FloatingLong.create(1_000_000))
                    .withSupportedUpgrades(Set.of(Upgrade.SPEED, Upgrade.ENERGY))
                    .build();

    public static final Machine.FactoryMachine<NativeSpiritFactoryBlockEntity>
            BASIC_SPIRIT_FACTORY_TYPE =
            Machine.MachineBuilder.createFactoryMachine(
                            NativeMekanismRegistries::basicSpiritFactoryTile,
                            MekanismLang.DESCRIPTION_FACTORY,
                            FactoryType.CRUSHING)
                    .withGui(() -> SPIRIT_FACTORY_CONTAINER)
                    .withEnergyConfig(() -> mekanism.api.math.FloatingLong.create(1_200),
                            () -> mekanism.api.math.FloatingLong.create(3_000_000))
                    .withSupportedUpgrades(Set.of(Upgrade.SPEED, Upgrade.ENERGY))
                    .with(new AttributeTier<>(FactoryTier.BASIC),
                            new AttributeUpgradeable(
                                    NativeMekanismRegistries::advancedSpiritFactoryBlock))
                    .build();
    public static final Machine.FactoryMachine<NativeSpiritFactoryBlockEntity>
            ADVANCED_SPIRIT_FACTORY_TYPE =
            Machine.MachineBuilder.createFactoryMachine(
                            NativeMekanismRegistries::advancedSpiritFactoryTile,
                            MekanismLang.DESCRIPTION_FACTORY,
                            FactoryType.CRUSHING)
                    .withGui(() -> SPIRIT_FACTORY_CONTAINER)
                    .withEnergyConfig(() -> mekanism.api.math.FloatingLong.create(2_000),
                            () -> mekanism.api.math.FloatingLong.create(5_000_000))
                    .withSupportedUpgrades(Set.of(Upgrade.SPEED, Upgrade.ENERGY))
                    .with(new AttributeTier<>(FactoryTier.ADVANCED),
                            new AttributeUpgradeable(
                                    NativeMekanismRegistries::eliteSpiritFactoryBlock))
                    .build();
    public static final Machine.FactoryMachine<NativeSpiritFactoryBlockEntity>
            ELITE_SPIRIT_FACTORY_TYPE =
            Machine.MachineBuilder.createFactoryMachine(
                            NativeMekanismRegistries::eliteSpiritFactoryTile,
                            MekanismLang.DESCRIPTION_FACTORY,
                            FactoryType.CRUSHING)
                    .withGui(() -> SPIRIT_FACTORY_CONTAINER)
                    .withEnergyConfig(() -> mekanism.api.math.FloatingLong.create(2_800),
                            () -> mekanism.api.math.FloatingLong.create(7_000_000))
                    .withSupportedUpgrades(Set.of(Upgrade.SPEED, Upgrade.ENERGY))
                    .with(new AttributeTier<>(FactoryTier.ELITE),
                            new AttributeUpgradeable(
                                    NativeMekanismRegistries::ultimateSpiritFactoryBlock))
                    .build();
    public static final Machine.FactoryMachine<NativeSpiritFactoryBlockEntity>
            ULTIMATE_SPIRIT_FACTORY_TYPE =
            createUltimateSpiritFactoryType();

    public static final BlockRegistryObject<NativeMachineBlock<NativeSpiritProcessorBlockEntity>, BlockItem>
            SPIRIT_BLOCK = BLOCKS.register("spirit_processor",
            () -> new NativeMachineBlock<>(SPIRIT_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<NativeMachineBlock<NativeDimensionMinerBlockEntity>, BlockItem>
            DIMENSION_MINER_BLOCK = BLOCKS.register("dimension_miner",
            () -> new NativeMachineBlock<>(DIMENSION_MINER_TYPE,
                    BlockBehaviour.Properties.of().strength(5.0F).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<NativeMachineBlock<NativeSpiritFactoryBlockEntity>, BlockItem>
            BASIC_SPIRIT_FACTORY_BLOCK = BLOCKS.register("basic_spirit_factory",
            () -> new NativeMachineBlock<>(BASIC_SPIRIT_FACTORY_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<NativeMachineBlock<NativeSpiritFactoryBlockEntity>, BlockItem>
            ADVANCED_SPIRIT_FACTORY_BLOCK = BLOCKS.register("advanced_spirit_factory",
            () -> new NativeMachineBlock<>(ADVANCED_SPIRIT_FACTORY_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<NativeMachineBlock<NativeSpiritFactoryBlockEntity>, BlockItem>
            ELITE_SPIRIT_FACTORY_BLOCK = BLOCKS.register("elite_spirit_factory",
            () -> new NativeMachineBlock<>(ELITE_SPIRIT_FACTORY_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<NativeMachineBlock<NativeSpiritFactoryBlockEntity>, BlockItem>
            ULTIMATE_SPIRIT_FACTORY_BLOCK = BLOCKS.register("ultimate_spirit_factory",
            () -> new NativeMachineBlock<>(ULTIMATE_SPIRIT_FACTORY_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<NativeMachineBlock<NativeRitualEngineBlockEntity>, BlockItem>
            RITUAL_BLOCK = BLOCKS.register("ritual_engine",
            () -> new NativeMachineBlock<>(RITUAL_TYPE,
                    BlockBehaviour.Properties.of().strength(5.0F).requiresCorrectToolForDrops()));
    public static final BlockRegistryObject<
            NativeMachineBlock<NativeMiniRitualAssemblerBlockEntity>, BlockItem>
            MINI_RITUAL_ASSEMBLER_BLOCK = BLOCKS.register("mini_ritual_assembler",
            () -> new NativeMachineBlock<>(MINI_RITUAL_ASSEMBLER_TYPE,
                    BlockBehaviour.Properties.of().strength(4.0F)
                            .requiresCorrectToolForDrops()));

    public static final TileEntityTypeRegistryObject<NativeSpiritProcessorBlockEntity> SPIRIT_TILE =
            TILES.register(SPIRIT_BLOCK, NativeSpiritProcessorBlockEntity::new);
    public static final TileEntityTypeRegistryObject<NativeDimensionMinerBlockEntity>
            DIMENSION_MINER_TILE = TILES.register(DIMENSION_MINER_BLOCK,
                    NativeDimensionMinerBlockEntity::new);
    public static final TileEntityTypeRegistryObject<NativeSpiritFactoryBlockEntity>
            BASIC_SPIRIT_FACTORY_TILE = TILES.register(BASIC_SPIRIT_FACTORY_BLOCK,
                    (pos, state) -> new NativeSpiritFactoryBlockEntity(
                            BASIC_SPIRIT_FACTORY_BLOCK, pos, state));
    public static final TileEntityTypeRegistryObject<NativeSpiritFactoryBlockEntity>
            ADVANCED_SPIRIT_FACTORY_TILE = TILES.register(ADVANCED_SPIRIT_FACTORY_BLOCK,
                    (pos, state) -> new NativeSpiritFactoryBlockEntity(
                            ADVANCED_SPIRIT_FACTORY_BLOCK, pos, state));
    public static final TileEntityTypeRegistryObject<NativeSpiritFactoryBlockEntity>
            ELITE_SPIRIT_FACTORY_TILE = TILES.register(ELITE_SPIRIT_FACTORY_BLOCK,
                    (pos, state) -> new NativeSpiritFactoryBlockEntity(
                            ELITE_SPIRIT_FACTORY_BLOCK, pos, state));
    public static final TileEntityTypeRegistryObject<NativeSpiritFactoryBlockEntity>
            ULTIMATE_SPIRIT_FACTORY_TILE = TILES.register(ULTIMATE_SPIRIT_FACTORY_BLOCK,
                    (pos, state) -> new NativeSpiritFactoryBlockEntity(
                            ULTIMATE_SPIRIT_FACTORY_BLOCK, pos, state));
    public static final TileEntityTypeRegistryObject<NativeRitualEngineBlockEntity> RITUAL_TILE =
            TILES.register(RITUAL_BLOCK, NativeRitualEngineBlockEntity::new);
    public static final TileEntityTypeRegistryObject<
            NativeMiniRitualAssemblerBlockEntity> MINI_RITUAL_ASSEMBLER_TILE =
            TILES.register(MINI_RITUAL_ASSEMBLER_BLOCK,
                    NativeMiniRitualAssemblerBlockEntity::new);

    private NativeMekanismRegistries() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TILES.register(bus);
        CONTAINERS.register(bus);
    }

    private static BlockRegistryObject<?, ?> basicSpiritFactoryBlock() {
        return BASIC_SPIRIT_FACTORY_BLOCK;
    }

    private static BlockRegistryObject<?, ?> advancedSpiritFactoryBlock() {
        return ADVANCED_SPIRIT_FACTORY_BLOCK;
    }

    private static BlockRegistryObject<?, ?> eliteSpiritFactoryBlock() {
        return ELITE_SPIRIT_FACTORY_BLOCK;
    }

    private static BlockRegistryObject<?, ?> ultimateSpiritFactoryBlock() {
        return ULTIMATE_SPIRIT_FACTORY_BLOCK;
    }

    private static TileEntityTypeRegistryObject<NativeSpiritFactoryBlockEntity>
    basicSpiritFactoryTile() {
        return BASIC_SPIRIT_FACTORY_TILE;
    }

    private static TileEntityTypeRegistryObject<NativeSpiritFactoryBlockEntity>
    advancedSpiritFactoryTile() {
        return ADVANCED_SPIRIT_FACTORY_TILE;
    }

    private static TileEntityTypeRegistryObject<NativeSpiritFactoryBlockEntity>
    eliteSpiritFactoryTile() {
        return ELITE_SPIRIT_FACTORY_TILE;
    }

    private static TileEntityTypeRegistryObject<NativeSpiritFactoryBlockEntity>
    ultimateSpiritFactoryTile() {
        return ULTIMATE_SPIRIT_FACTORY_TILE;
    }

    private static Machine.FactoryMachine<NativeSpiritFactoryBlockEntity>
    createUltimateSpiritFactoryType() {
        Machine.MachineBuilder<
                Machine.FactoryMachine<NativeSpiritFactoryBlockEntity>,
                NativeSpiritFactoryBlockEntity, ?> builder =
                Machine.MachineBuilder.createFactoryMachine(
                                NativeMekanismRegistries::ultimateSpiritFactoryTile,
                                MekanismLang.DESCRIPTION_FACTORY,
                                FactoryType.CRUSHING)
                        .withGui(() -> SPIRIT_FACTORY_CONTAINER)
                        .withEnergyConfig(
                                () -> mekanism.api.math.FloatingLong.create(3_600),
                                () -> mekanism.api.math.FloatingLong.create(9_000_000))
                        .withSupportedUpgrades(
                                Set.of(Upgrade.SPEED, Upgrade.ENERGY))
                        .with(new AttributeTier<>(FactoryTier.ULTIMATE));
        Attribute extraUpgrade = optionalExtraFactoryUpgrade();
        if (extraUpgrade != null) {
            builder.with(extraUpgrade);
        }
        return builder.build();
    }

    private static Attribute optionalExtraFactoryUpgrade() {
        if (!ModCompatibility.loaded(ModCompatibility.MEKANISM_EXTRAS)) {
            return null;
        }
        try {
            Class<?> attributeClass = Class.forName(
                    "com.jerry.mekanism_extras.common.block.attribute."
                            + "ExtraAttributeUpgradeable");
            Constructor<?> constructor = attributeClass.getConstructor(
                    Supplier.class);
            Supplier<BlockRegistryObject<?, ?>> target = () -> {
                try {
                    Class<?> integration = Class.forName(
                            "com.example.mekanismmagic.integration.mekextras."
                                    + "MekanismExtrasSpiritFactories");
                    Field field = integration.getField("ABSOLUTE_BLOCK");
                    return (BlockRegistryObject<?, ?>) field.get(null);
                } catch (ReflectiveOperationException failure) {
                    throw new IllegalStateException(failure);
                }
            };
            return (Attribute) constructor.newInstance(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(
                    "Unable to attach Mekanism Extras factory upgrade", failure);
        }
    }

}

