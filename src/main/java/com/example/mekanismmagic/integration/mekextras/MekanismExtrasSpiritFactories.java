package com.example.mekanismmagic.integration.mekextras;

import com.example.mekanismmagic.MekanismMagic;
import com.jerry.mekextras.common.block.attribute.ExtraAttributeUpgradeable;
import com.jerry.mekextras.common.block.prefab.BlockExtraFactoryMachine;
import com.jerry.mekextras.common.content.blocktype.ExtraFactory;
import com.jerry.mekextras.common.tier.ExtraFactoryTier;
import mekanism.common.content.blocktype.FactoryType;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;

import java.util.function.Supplier;

/**
 * Optional registration boundary for Mekanism Extras. This class is loaded
 * reflectively only when mekanism_extras is installed.
 */
public final class MekanismExtrasSpiritFactories {
    private static final BlockDeferredRegister BLOCKS =
            new BlockDeferredRegister(MekanismMagic.MOD_ID);
    private static final TileEntityTypeDeferredRegister TILES =
            new TileEntityTypeDeferredRegister(MekanismMagic.MOD_ID);

    public static final ExtraFactory<ExtraSpiritFactoryBlockEntity> ABSOLUTE_TYPE =
            createType(MekanismExtrasSpiritFactories::absoluteTile,
                    ExtraFactoryTier.ABSOLUTE,
                    MekanismExtrasSpiritFactories::supremeBlock);
    public static final ExtraFactory<ExtraSpiritFactoryBlockEntity> SUPREME_TYPE =
            createType(MekanismExtrasSpiritFactories::supremeTile,
                    ExtraFactoryTier.SUPREME,
                    MekanismExtrasSpiritFactories::cosmicBlock);
    public static final ExtraFactory<ExtraSpiritFactoryBlockEntity> COSMIC_TYPE =
            createType(MekanismExtrasSpiritFactories::cosmicTile,
                    ExtraFactoryTier.COSMIC,
                    MekanismExtrasSpiritFactories::infiniteBlock);
    public static final ExtraFactory<ExtraSpiritFactoryBlockEntity> INFINITE_TYPE =
            createType(MekanismExtrasSpiritFactories::infiniteTile,
                    ExtraFactoryTier.INFINITE, null);

    public static final BlockRegistryObject<
            BlockExtraFactoryMachine.BlockExtraFactory<ExtraSpiritFactoryBlockEntity>,
            BlockItem> ABSOLUTE_BLOCK =
            BLOCKS.register("absolute_spirit_factory",
                    () -> new BlockExtraFactoryMachine.BlockExtraFactory<>(ABSOLUTE_TYPE));
    public static final BlockRegistryObject<
            BlockExtraFactoryMachine.BlockExtraFactory<ExtraSpiritFactoryBlockEntity>,
            BlockItem> SUPREME_BLOCK =
            BLOCKS.register("supreme_spirit_factory",
                    () -> new BlockExtraFactoryMachine.BlockExtraFactory<>(SUPREME_TYPE));
    public static final BlockRegistryObject<
            BlockExtraFactoryMachine.BlockExtraFactory<ExtraSpiritFactoryBlockEntity>,
            BlockItem> COSMIC_BLOCK =
            BLOCKS.register("cosmic_spirit_factory",
                    () -> new BlockExtraFactoryMachine.BlockExtraFactory<>(COSMIC_TYPE));
    public static final BlockRegistryObject<
            BlockExtraFactoryMachine.BlockExtraFactory<ExtraSpiritFactoryBlockEntity>,
            BlockItem> INFINITE_BLOCK =
            BLOCKS.register("infinite_spirit_factory",
                    () -> new BlockExtraFactoryMachine.BlockExtraFactory<>(INFINITE_TYPE));

    public static final TileEntityTypeRegistryObject<ExtraSpiritFactoryBlockEntity>
            ABSOLUTE_TILE = tile(ABSOLUTE_BLOCK);
    public static final TileEntityTypeRegistryObject<ExtraSpiritFactoryBlockEntity>
            SUPREME_TILE = tile(SUPREME_BLOCK);
    public static final TileEntityTypeRegistryObject<ExtraSpiritFactoryBlockEntity>
            COSMIC_TILE = tile(COSMIC_BLOCK);
    public static final TileEntityTypeRegistryObject<ExtraSpiritFactoryBlockEntity>
            INFINITE_TILE = tile(INFINITE_BLOCK);

    private MekanismExtrasSpiritFactories() {
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        TILES.register(bus);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ExtraFactory<ExtraSpiritFactoryBlockEntity> createType(
            Supplier<?> tile, ExtraFactoryTier tier,
            Supplier<BlockRegistryObject<?, ?>> next) {
        ExtraFactory.ExtraFactoryBuilder builder =
                ExtraFactory.ExtraFactoryBuilder.createFactory(
                        tile, FactoryType.CRUSHING, tier);
        if (next != null) {
            builder.replace(new ExtraAttributeUpgradeable(next));
        }
        return (ExtraFactory<ExtraSpiritFactoryBlockEntity>) builder.build();
    }

    private static TileEntityTypeRegistryObject<ExtraSpiritFactoryBlockEntity> tile(
            BlockRegistryObject<?, ?> block) {
        return TILES.mekBuilder(block, (pos, state) ->
                        new ExtraSpiritFactoryBlockEntity(
                                block.get().builtInRegistryHolder(), pos, state))
                .serverTicker((level, pos, state, tile) ->
                        mekanism.common.tile.base.TileEntityMekanism.tickServer(
                                level, pos, state, tile))
                .build();
    }

    private static TileEntityTypeRegistryObject<ExtraSpiritFactoryBlockEntity>
    absoluteTile() {
        return ABSOLUTE_TILE;
    }

    private static TileEntityTypeRegistryObject<ExtraSpiritFactoryBlockEntity>
    supremeTile() {
        return SUPREME_TILE;
    }

    private static TileEntityTypeRegistryObject<ExtraSpiritFactoryBlockEntity>
    cosmicTile() {
        return COSMIC_TILE;
    }

    private static TileEntityTypeRegistryObject<ExtraSpiritFactoryBlockEntity>
    infiniteTile() {
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
