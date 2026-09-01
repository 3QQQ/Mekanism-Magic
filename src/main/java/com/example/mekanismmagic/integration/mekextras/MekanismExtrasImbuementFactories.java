package com.example.mekanismmagic.integration.mekextras;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.upgrade.MagicUpgrades;
import com.jerry.mekextras.api.ExtraUpgrade;
import com.jerry.mekextras.common.block.attribute.ExtraAttributeUpgradeable;
import com.jerry.mekextras.common.content.blocktype.ExtraFactory;
import com.jerry.mekextras.common.tier.ExtraFactoryTier;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.block.attribute.AttributeGui;
import mekanism.common.block.attribute.AttributeUpgradeSupport;
import mekanism.api.Upgrade;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import java.util.function.Supplier;

public final class MekanismExtrasImbuementFactories {
    private static final BlockDeferredRegister BLOCKS =
            new BlockDeferredRegister(MekanismMagic.MOD_ID);
    private static final TileEntityTypeDeferredRegister TILES =
            new TileEntityTypeDeferredRegister(MekanismMagic.MOD_ID);
    private static final ContainerTypeDeferredRegister CONTAINERS =
            new ContainerTypeDeferredRegister(MekanismMagic.MOD_ID);

    public static final ContainerTypeRegistryObject<
            ExtraImbuementFactoryContainer> IMBUEMENT_FACTORY_CONTAINER =
            CONTAINERS.register("extra_imbuement_factory",
                    ExtraImbuementFactoryBlockEntity.class,
                    ExtraImbuementFactoryContainer::new);

    public static final ExtraFactory<ExtraImbuementFactoryBlockEntity>
            ABSOLUTE_TYPE = createType(
                    MekanismExtrasImbuementFactories::absoluteTile,
                    ExtraFactoryTier.ABSOLUTE,
                    MekanismExtrasImbuementFactories::supremeBlock);
    public static final ExtraFactory<ExtraImbuementFactoryBlockEntity>
            SUPREME_TYPE = createType(
                    MekanismExtrasImbuementFactories::supremeTile,
                    ExtraFactoryTier.SUPREME,
                    MekanismExtrasImbuementFactories::cosmicBlock);
    public static final ExtraFactory<ExtraImbuementFactoryBlockEntity>
            COSMIC_TYPE = createType(
                    MekanismExtrasImbuementFactories::cosmicTile,
                    ExtraFactoryTier.COSMIC,
                    MekanismExtrasImbuementFactories::infiniteBlock);
    public static final ExtraFactory<ExtraImbuementFactoryBlockEntity>
            INFINITE_TYPE = createType(
                    MekanismExtrasImbuementFactories::infiniteTile,
                    ExtraFactoryTier.INFINITE, null);

    public static final BlockRegistryObject<
            ArsExtraFactoryBlock, BlockItem>
            ABSOLUTE_BLOCK = register("absolute_imbuement_factory",
                    ABSOLUTE_TYPE);
    public static final BlockRegistryObject<
            ArsExtraFactoryBlock, BlockItem>
            SUPREME_BLOCK = register("supreme_imbuement_factory",
                    SUPREME_TYPE);
    public static final BlockRegistryObject<
            ArsExtraFactoryBlock, BlockItem>
            COSMIC_BLOCK = register("cosmic_imbuement_factory",
                    COSMIC_TYPE);
    public static final BlockRegistryObject<
            ArsExtraFactoryBlock, BlockItem>
            INFINITE_BLOCK = register("infinite_imbuement_factory",
                    INFINITE_TYPE);

    public static final TileEntityTypeRegistryObject<
            ExtraImbuementFactoryBlockEntity> ABSOLUTE_TILE = tile(
            ABSOLUTE_BLOCK);
    public static final TileEntityTypeRegistryObject<
            ExtraImbuementFactoryBlockEntity> SUPREME_TILE = tile(
            SUPREME_BLOCK);
    public static final TileEntityTypeRegistryObject<
            ExtraImbuementFactoryBlockEntity> COSMIC_TILE = tile(
            COSMIC_BLOCK);
    public static final TileEntityTypeRegistryObject<
            ExtraImbuementFactoryBlockEntity> INFINITE_TILE = tile(
            INFINITE_BLOCK);

    private MekanismExtrasImbuementFactories() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TILES.register(bus);
        CONTAINERS.register(bus);
        bus.addListener(MekanismExtrasImbuementFactories::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(CapabilityRegistry.SOURCE_CAPABILITY,
                ABSOLUTE_TILE.get(), (tile, side) -> side == null
                        ? tile.getSourceStorage() : tile.getSourceStorage(side));
        event.registerBlockEntity(CapabilityRegistry.SOURCE_CAPABILITY,
                SUPREME_TILE.get(), (tile, side) -> side == null
                        ? tile.getSourceStorage() : tile.getSourceStorage(side));
        event.registerBlockEntity(CapabilityRegistry.SOURCE_CAPABILITY,
                COSMIC_TILE.get(), (tile, side) -> side == null
                        ? tile.getSourceStorage() : tile.getSourceStorage(side));
        event.registerBlockEntity(CapabilityRegistry.SOURCE_CAPABILITY,
                INFINITE_TILE.get(), (tile, side) -> side == null
                        ? tile.getSourceStorage() : tile.getSourceStorage(side));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ExtraFactory<ExtraImbuementFactoryBlockEntity> createType(
            Supplier<?> tile, ExtraFactoryTier tier,
            Supplier<BlockRegistryObject<?, ?>> next) {
        ExtraFactory.ExtraFactoryBuilder builder =
                ExtraFactory.ExtraFactoryBuilder.createFactory(
                        tile, FactoryType.SMELTING, tier);
        builder.replace(new AttributeGui(
                () -> IMBUEMENT_FACTORY_CONTAINER, null));
        builder.replace(AttributeUpgradeSupport.create(
                Upgrade.SPEED, Upgrade.ENERGY, Upgrade.MUFFLING,
                ExtraUpgrade.STACK, ExtraUpgrade.CREATIVE,
                MagicUpgrades.creativeMagic()));
        if (next != null) {
            builder.replace(new ExtraAttributeUpgradeable(next));
        }
        return (ExtraFactory<ExtraImbuementFactoryBlockEntity>)
                builder.build();
    }

    private static BlockRegistryObject<
            ArsExtraFactoryBlock, BlockItem> register(
            String name,
            ExtraFactory<ExtraImbuementFactoryBlockEntity> type) {
        return BLOCKS.register(name,
                () -> new ArsExtraFactoryBlock(type));
    }

    private static TileEntityTypeRegistryObject<
            ExtraImbuementFactoryBlockEntity> tile(
            BlockRegistryObject<?, ?> block) {
        return TILES.mekBuilder(block, (pos, state) ->
                        new ExtraImbuementFactoryBlockEntity(
                                block.get().builtInRegistryHolder(), pos, state))
                .serverTicker((level, pos, state, tile) ->
                        mekanism.common.tile.base.TileEntityMekanism
                                .tickServer(level, pos, state, tile))
                .build();
    }

    private static TileEntityTypeRegistryObject<
            ExtraImbuementFactoryBlockEntity> absoluteTile() {
        return ABSOLUTE_TILE;
    }

    private static TileEntityTypeRegistryObject<
            ExtraImbuementFactoryBlockEntity> supremeTile() {
        return SUPREME_TILE;
    }

    private static TileEntityTypeRegistryObject<
            ExtraImbuementFactoryBlockEntity> cosmicTile() {
        return COSMIC_TILE;
    }

    private static TileEntityTypeRegistryObject<
            ExtraImbuementFactoryBlockEntity> infiniteTile() {
        return INFINITE_TILE;
    }

    private static BlockRegistryObject<?, ?> supremeBlock() {
        return SUPREME_BLOCK;
    }

    private static BlockRegistryObject<?, ?> cosmicBlock() {
        return COSMIC_BLOCK;
    }

    private static BlockRegistryObject<?, ?> infiniteBlock() {
        return INFINITE_BLOCK;
    }
}
