package com.example.mekanismmagic.integration.mekextras;

import com.example.mekanismmagic.MagicLang;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.block.NativeMachineBlock;
import com.example.mekanismmagic.integration.naturesaura.NatureAuraMachineConfig;
import com.example.mekanismmagic.integration.naturesaura.NaturesAuraRegistries;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import com.jerry.mekextras.common.block.attribute.ExtraAttributeTier;
import com.jerry.mekextras.common.block.attribute.ExtraAttributeUpgradeable;
import com.jerry.mekextras.common.tier.ExtraFactoryTier;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import mekanism.api.Upgrade;
import mekanism.common.content.blocktype.FactoryType;
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
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.List;
import java.util.function.Supplier;

/** Loaded only when both Nature's Aura and Mekanism Extras are available. */
public final class MekanismExtrasNatureAltarFactories {
    private static final BlockDeferredRegister BLOCKS =
            new BlockDeferredRegister(MekanismMagic.MOD_ID);
    private static final TileEntityTypeDeferredRegister TILES =
            new TileEntityTypeDeferredRegister(MekanismMagic.MOD_ID);
    private static final ContainerTypeDeferredRegister CONTAINERS =
            new ContainerTypeDeferredRegister(MekanismMagic.MOD_ID);

    public static final ContainerTypeRegistryObject<ExtraNatureAltarFactoryContainer>
            NATURE_ALTAR_FACTORY_CONTAINER = CONTAINERS.register(
            "extra_nature_altar_factory", ExtraNatureAltarFactoryBlockEntity.class,
            ExtraNatureAltarFactoryContainer::new);

    public static final Machine.FactoryMachine<ExtraNatureAltarFactoryBlockEntity>
            ABSOLUTE_TYPE = createType(() -> MekanismExtrasNatureAltarFactories.ABSOLUTE_TILE,
            MagicLang.ABSOLUTE_NATURE_ALTAR_FACTORY, ExtraFactoryTier.ABSOLUTE,
            () -> MekanismExtrasNatureAltarFactories.SUPREME_BLOCK);
    public static final Machine.FactoryMachine<ExtraNatureAltarFactoryBlockEntity>
            SUPREME_TYPE = createType(() -> MekanismExtrasNatureAltarFactories.SUPREME_TILE,
            MagicLang.SUPREME_NATURE_ALTAR_FACTORY, ExtraFactoryTier.SUPREME,
            () -> MekanismExtrasNatureAltarFactories.COSMIC_BLOCK);
    public static final Machine.FactoryMachine<ExtraNatureAltarFactoryBlockEntity>
            COSMIC_TYPE = createType(() -> MekanismExtrasNatureAltarFactories.COSMIC_TILE,
            MagicLang.COSMIC_NATURE_ALTAR_FACTORY, ExtraFactoryTier.COSMIC,
            () -> MekanismExtrasNatureAltarFactories.INFINITE_BLOCK);
    public static final Machine.FactoryMachine<ExtraNatureAltarFactoryBlockEntity>
            INFINITE_TYPE = createType(() -> MekanismExtrasNatureAltarFactories.INFINITE_TILE,
            MagicLang.INFINITE_NATURE_ALTAR_FACTORY, ExtraFactoryTier.INFINITE,
            null);

    public static final BlockRegistryObject<NativeMachineBlock<ExtraNatureAltarFactoryBlockEntity>, BlockItem>
            ABSOLUTE_BLOCK = register("absolute_nature_altar_factory", ABSOLUTE_TYPE);
    public static final BlockRegistryObject<NativeMachineBlock<ExtraNatureAltarFactoryBlockEntity>, BlockItem>
            SUPREME_BLOCK = register("supreme_nature_altar_factory", SUPREME_TYPE);
    public static final BlockRegistryObject<NativeMachineBlock<ExtraNatureAltarFactoryBlockEntity>, BlockItem>
            COSMIC_BLOCK = register("cosmic_nature_altar_factory", COSMIC_TYPE);
    public static final BlockRegistryObject<NativeMachineBlock<ExtraNatureAltarFactoryBlockEntity>, BlockItem>
            INFINITE_BLOCK = register("infinite_nature_altar_factory", INFINITE_TYPE);

    public static final TileEntityTypeRegistryObject<ExtraNatureAltarFactoryBlockEntity>
            ABSOLUTE_TILE = tile(ABSOLUTE_BLOCK);
    public static final TileEntityTypeRegistryObject<ExtraNatureAltarFactoryBlockEntity>
            SUPREME_TILE = tile(SUPREME_BLOCK);
    public static final TileEntityTypeRegistryObject<ExtraNatureAltarFactoryBlockEntity>
            COSMIC_TILE = tile(COSMIC_BLOCK);
    public static final TileEntityTypeRegistryObject<ExtraNatureAltarFactoryBlockEntity>
            INFINITE_TILE = tile(INFINITE_BLOCK);

    private MekanismExtrasNatureAltarFactories() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TILES.register(bus);
        CONTAINERS.register(bus);
        bus.addListener(MekanismExtrasNatureAltarFactories::registerCapabilities);
        // Attach after both registries initialize, avoiding a circular class
        // initialization and leaving the base tier untouched without Extras.
        NaturesAuraRegistries.ULTIMATE_NATURE_ALTAR_FACTORY_TYPE.add(
                new ExtraAttributeUpgradeable(() -> ABSOLUTE_BLOCK));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var tile : List.of(ABSOLUTE_TILE, SUPREME_TILE,
                COSMIC_TILE, INFINITE_TILE)) {
            event.registerBlockEntity(NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                    tile.get(), (machine, side) -> machine.getAuraContainer(side));
        }
    }

    private static Machine.FactoryMachine<ExtraNatureAltarFactoryBlockEntity>
    createType(Supplier<TileEntityTypeRegistryObject<ExtraNatureAltarFactoryBlockEntity>> tile,
               MagicLang lang, ExtraFactoryTier tier,
               Supplier<BlockRegistryObject<?, ?>> next) {
        // FactoryMachine provides Mekanism's factory identity without invoking
        // the stock Factory constructor that Extras patches to vanilla targets.
        var builder = Machine.MachineBuilder.createFactoryMachine(
                        tile, lang, FactoryType.SMELTING)
                .withGui(() -> NATURE_ALTAR_FACTORY_CONTAINER)
                .withEnergyConfig(() -> (long) NatureAuraMachineConfig
                                .ALTAR_BASE_ENERGY_PER_TICK,
                        () -> NatureAuraMachineConfig
                                .altarFactoryEnergyCapacity(tier.processes))
                .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
                .withSupportedUpgrades(Upgrade.SPEED, Upgrade.ENERGY,
                        MagicUpgrades.creativeMagic())
                .with(new ExtraAttributeTier<>(tier));
        if (next != null) {
            builder.with(new ExtraAttributeUpgradeable(next));
        }
        return builder.build();
    }

    private static BlockRegistryObject<NativeMachineBlock<ExtraNatureAltarFactoryBlockEntity>, BlockItem>
    register(String name, Machine.FactoryMachine<ExtraNatureAltarFactoryBlockEntity> type) {
        return BLOCKS.register(name, () -> new NativeMachineBlock<>(type,
                BlockBehaviour.Properties.of().strength(4.0F)
                        .requiresCorrectToolForDrops().noOcclusion()));
    }

    private static TileEntityTypeRegistryObject<ExtraNatureAltarFactoryBlockEntity>
    tile(BlockRegistryObject<?, ?> block) {
        return TILES.mekBuilder(block, (pos, state) ->
                        new ExtraNatureAltarFactoryBlockEntity(
                                block.get().builtInRegistryHolder(), pos, state))
                .serverTicker((level, pos, state, tile) ->
                        mekanism.common.tile.base.TileEntityMekanism
                                .tickServer(level, pos, state, tile))
                .build();
    }
}
