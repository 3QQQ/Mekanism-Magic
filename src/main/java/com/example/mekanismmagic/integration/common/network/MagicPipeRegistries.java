package com.example.mekanismmagic.integration.common.network;

import com.example.mekanismmagic.MagicLang;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.arsnouveau.AttributeMagicSourcePipeTier;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeBlock;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeItem;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeTier;
import mekanism.common.block.attribute.AttributeTier;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.content.blocktype.BlockTypeTile.BlockTileBuilder;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import java.util.List;
import java.util.function.Supplier;

/** Stable shared pipe IDs, independent of any one magic mod. */
public final class MagicPipeRegistries {
    private static PipeFactory factory = MagicSourcePipeBlockEntity::new;
    private static boolean registered;
    public static final BlockDeferredRegister BLOCKS =
            new BlockDeferredRegister(MekanismMagic.MOD_ID);
    public static final TileEntityTypeDeferredRegister TILES =
            new TileEntityTypeDeferredRegister(MekanismMagic.MOD_ID);

    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    MagicPipeRegistries::magicSourcePipeTile,
                    MagicLang.MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.BASIC);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            ADVANCED_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    MagicPipeRegistries::advancedMagicSourcePipeTile,
                    MagicLang.ADVANCED_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.ADVANCED);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            ELITE_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    MagicPipeRegistries::eliteMagicSourcePipeTile,
                    MagicLang.ELITE_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.ELITE);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            ULTIMATE_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    MagicPipeRegistries::ultimateMagicSourcePipeTile,
                    MagicLang.ULTIMATE_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.ULTIMATE);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            ABSOLUTE_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    MagicPipeRegistries::absoluteMagicSourcePipeTile,
                    MagicLang.ABSOLUTE_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.ABSOLUTE);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            SUPREME_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    MagicPipeRegistries::supremeMagicSourcePipeTile,
                    MagicLang.SUPREME_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.SUPREME);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            COSMIC_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    MagicPipeRegistries::cosmicMagicSourcePipeTile,
                    MagicLang.COSMIC_MAGIC_SOURCE_PIPE,
                    MagicSourcePipeTier.COSMIC);
    public static final BlockTypeTile<MagicSourcePipeBlockEntity>
            INFINITE_MAGIC_SOURCE_PIPE_TYPE =
            createMagicSourcePipeType(
                    MagicPipeRegistries::infiniteMagicSourcePipeTile,
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
                        (pos, state) -> factory.create(
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
                .build();
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


    private MagicPipeRegistries() { }

    public static void setFactory(PipeFactory pipeFactory) {
        factory = java.util.Objects.requireNonNull(pipeFactory);
    }

    public static void register(IEventBus bus) {
        if (registered) {
            return;
        }
        registered = true;
        BLOCKS.register(bus);
        TILES.register(bus);
        bus.addListener((net.neoforged.neoforge.registries.RegisterEvent event) -> {
            if (event.getRegistryKey().equals(net.minecraft.core.registries.Registries.BLOCK)
                    || event.getRegistryKey().equals(net.minecraft.core.registries.Registries.ITEM)
                    || event.getRegistryKey().equals(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE)) {
                for (String prefix : List.of("", "advanced_", "elite_", "ultimate_")) {
                    event.getRegistry().addAlias(
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    MekanismMagic.MOD_ID, prefix + "natural_aura_pipe"),
                            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                    MekanismMagic.MOD_ID, prefix + "magic_source_pipe"));
                }
            }
        });
    }

    public static List<TileEntityTypeRegistryObject<MagicSourcePipeBlockEntity>> allTiles() {
        return List.of(MAGIC_SOURCE_PIPE_TILE, ADVANCED_MAGIC_SOURCE_PIPE_TILE,
                ELITE_MAGIC_SOURCE_PIPE_TILE, ULTIMATE_MAGIC_SOURCE_PIPE_TILE,
                ABSOLUTE_MAGIC_SOURCE_PIPE_TILE, SUPREME_MAGIC_SOURCE_PIPE_TILE,
                COSMIC_MAGIC_SOURCE_PIPE_TILE, INFINITE_MAGIC_SOURCE_PIPE_TILE);
    }

    @FunctionalInterface
    public interface PipeFactory {
        MagicSourcePipeBlockEntity create(Holder<Block> block, BlockPos pos, BlockState state);
    }
}
