package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MagicLang;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.block.NativeMachineBlock;
import mekanism.api.Upgrade;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;

/** Loaded only after the exact F&A and Valhelsia version gate succeeds. */
public final class ForbiddenArcanusRegistries {
    public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(MekanismMagic.MOD_ID);
    public static final TileEntityTypeDeferredRegister TILES = new TileEntityTypeDeferredRegister(MekanismMagic.MOD_ID);
    public static final ContainerTypeDeferredRegister CONTAINERS = new ContainerTypeDeferredRegister(MekanismMagic.MOD_ID);
    public static final ContainerTypeRegistryObject<ClibanoProcessorContainer> CLIBANO_PROCESSOR_CONTAINER =
            CONTAINERS.register("clibano_processor", ClibanoProcessorBlockEntity.class, ClibanoProcessorContainer::new);
    public static final Machine<ClibanoProcessorBlockEntity> CLIBANO_PROCESSOR_TYPE =
            Machine.MachineBuilder.createMachine(ForbiddenArcanusRegistries::clibanoTile, MagicLang.CLIBANO_PROCESSOR)
                    .withGui(() -> CLIBANO_PROCESSOR_CONTAINER)
                    .withEnergyConfig(() -> (long) ClibanoProcessorBlockEntity.BASE_ENERGY_PER_TICK,
                            () -> ClibanoProcessorBlockEntity.ENERGY_CAPACITY)
                    .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY).build();
    public static final BlockRegistryObject<NativeMachineBlock<ClibanoProcessorBlockEntity>, BlockItem> CLIBANO_PROCESSOR_BLOCK =
            BLOCKS.register("clibano_processor", () -> new NativeMachineBlock<>(CLIBANO_PROCESSOR_TYPE,
                    BlockBehaviour.Properties.of().strength(4F).requiresCorrectToolForDrops().noOcclusion()));
    public static final TileEntityTypeRegistryObject<ClibanoProcessorBlockEntity> CLIBANO_PROCESSOR_TILE =
            TILES.mekBuilder(CLIBANO_PROCESSOR_BLOCK, ClibanoProcessorBlockEntity::new)
                    .serverTicker((level, pos, state, tile) -> mekanism.common.tile.base.TileEntityMekanism
                            .tickServer(level, pos, state, tile)).build();

    public static final ContainerTypeRegistryObject<HephaestusForgeProcessorContainer> HEPHAESTUS_FORGE_PROCESSOR_CONTAINER =
            CONTAINERS.register("hephaestus_forge_processor", HephaestusForgeProcessorBlockEntity.class, HephaestusForgeProcessorContainer::new);
    public static final Machine<HephaestusForgeProcessorBlockEntity> HEPHAESTUS_FORGE_PROCESSOR_TYPE =
            Machine.MachineBuilder.createMachine(ForbiddenArcanusRegistries::hephaestusTile, MagicLang.HEPHAESTUS_FORGE_PROCESSOR)
                    .withGui(() -> HEPHAESTUS_FORGE_PROCESSOR_CONTAINER)
                    .withEnergyConfig(() -> (long) HephaestusForgeProcessorBlockEntity.BASE_ENERGY_PER_TICK,
                            () -> HephaestusForgeProcessorBlockEntity.ENERGY_CAPACITY)
                    .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.FLUID)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY,
                            com.example.mekanismmagic.upgrade.MagicUpgrades.creativeMagic()).build();
    public static final BlockRegistryObject<NativeMachineBlock<HephaestusForgeProcessorBlockEntity>, BlockItem> HEPHAESTUS_FORGE_PROCESSOR_BLOCK =
            BLOCKS.register("hephaestus_forge_processor", () -> new NativeMachineBlock<>(HEPHAESTUS_FORGE_PROCESSOR_TYPE,
                    BlockBehaviour.Properties.of().strength(4F).requiresCorrectToolForDrops().noOcclusion()));
    public static final TileEntityTypeRegistryObject<HephaestusForgeProcessorBlockEntity> HEPHAESTUS_FORGE_PROCESSOR_TILE =
            TILES.mekBuilder(HEPHAESTUS_FORGE_PROCESSOR_BLOCK, HephaestusForgeProcessorBlockEntity::new)
                    .serverTicker((level, pos, state, tile) -> mekanism.common.tile.base.TileEntityMekanism
                            .tickServer(level, pos, state, tile)).build();

    public static final ContainerTypeRegistryObject<EssenceCondenserContainer> ESSENCE_CONDENSER_CONTAINER =
            CONTAINERS.register("essence_condenser", EssenceCondenserBlockEntity.class, EssenceCondenserContainer::new);
    public static final Machine<EssenceCondenserBlockEntity> ESSENCE_CONDENSER_TYPE =
            Machine.MachineBuilder.createMachine(ForbiddenArcanusRegistries::essenceCondenserTile,
                            MagicLang.ESSENCE_CONDENSER)
                    .withGui(() -> ESSENCE_CONDENSER_CONTAINER)
                    .withEnergyConfig(() -> (long) EssenceCondenserBlockEntity.BASE_ENERGY_PER_TICK,
                            () -> EssenceCondenserBlockEntity.ENERGY_CAPACITY)
                    .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.FLUID)
                    .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY).build();
    public static final BlockRegistryObject<NativeMachineBlock<EssenceCondenserBlockEntity>, BlockItem> ESSENCE_CONDENSER_BLOCK =
            BLOCKS.register("essence_condenser", () -> new NativeMachineBlock<>(ESSENCE_CONDENSER_TYPE,
                    BlockBehaviour.Properties.of().strength(4F).requiresCorrectToolForDrops().noOcclusion()));
    public static final TileEntityTypeRegistryObject<EssenceCondenserBlockEntity> ESSENCE_CONDENSER_TILE =
            TILES.mekBuilder(ESSENCE_CONDENSER_BLOCK, EssenceCondenserBlockEntity::new)
                    .serverTicker((level, pos, state, tile) -> mekanism.common.tile.base.TileEntityMekanism
                            .tickServer(level, pos, state, tile)).build();

    private ForbiddenArcanusRegistries() {
    }

    private static TileEntityTypeRegistryObject<ClibanoProcessorBlockEntity> clibanoTile() {
        return CLIBANO_PROCESSOR_TILE;
    }

    private static TileEntityTypeRegistryObject<HephaestusForgeProcessorBlockEntity> hephaestusTile() {
        return HEPHAESTUS_FORGE_PROCESSOR_TILE;
    }

    private static TileEntityTypeRegistryObject<EssenceCondenserBlockEntity> essenceCondenserTile() {
        return ESSENCE_CONDENSER_TILE;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        TILES.register(modBus);
        CONTAINERS.register(modBus);
    }
}
