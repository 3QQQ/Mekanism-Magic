package com.example.mekanismmagic.integration.occultism;

import com.example.mekanismmagic.MekanismMagic;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnchantingTableBlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.mekanismmagic.integration.occultism.MiniPentacleDeployment.*;

/** Recognizes native complete structures and transfers their materials into one blank mini. */
public final class MiniPentacleCapture {
    static final String LAYOUT = "mini_pentacle_layout";
    private static final int MAX_CELLS = 1_024;
    private static final int REMOVE_FLAGS = Block.UPDATE_CLIENTS
            | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private MiniPentacleCapture() {
    }

    public static boolean isBlank(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(MekanismMagic.MINI_RITUAL.get())
                || isDeployed(stack)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || (!data.getUnsafe().contains("ritual")
                && !data.getUnsafe().contains("pentacle")
                && !data.getUnsafe().contains(LAYOUT));
    }

    public static boolean hasCustomLayout(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.getUnsafe().contains(LAYOUT, Tag.TAG_LIST);
    }

    public static Result capture(ServerLevel level, ServerPlayer player,
                                 ItemStack miniature, BlockPos clickedPosition) {
        if (!isBlank(miniature)) {
            return new Result(Status.NOT_CONFIGURED, 0);
        }
        ItemStack expected = miniature.copy();
        if (!heldUnchanged(player, miniature, expected)) {
            return new Result(Status.CHANGED, 0);
        }
        MiniPentacleClaims claims = MiniPentacleClaims.get(level);
        if (!claims.begin()) {
            return new Result(Status.CHANGED, 0);
        }
        boolean previousCapture = level.captureBlockSnapshots;
        List<CaptureBlock> removed = new ArrayList<>();
        boolean committingItem = false;
        try {
            level.captureBlockSnapshots = false;
            Search search = find(level, clickedPosition);
            if (search.match() == null) {
                return new Result(search.unloaded() ? Status.UNLOADED : Status.NO_PENTACLE, 0);
            }
            Match match = search.match();
            if (search.unloaded()) {
                return new Result(Status.UNLOADED, 0);
            }
            if (claims.claimed(match.positions())) {
                return new Result(Status.CLAIMED, 0);
            }
            List<CaptureBlock> blocks = new ArrayList<>();
            boolean needsLayout = false;
            ListTag layout = new ListTag();
            for (Cell cell : match.template().required()) {
                BlockPos position = match.anchor().offset(cell.offset());
                if (!level.mayInteract(player, position)
                        || !player.mayUseItemAt(position, Direction.UP, miniature)) {
                    return new Result(Status.NO_PERMISSION, 0);
                }
                BlockState state = level.getBlockState(position);
                if (!state.getFluidState().isEmpty()) {
                    return new Result(Status.STRUCTURE_IN_USE, 0);
                }
                CompoundTag data = blockEntityData(level, position);
                StaticData retained = staticData(level.getBlockEntity(position), data);
                if (!retained.safe()) {
                    return new Result(Status.STRUCTURE_IN_USE, 0);
                }
                BlockState canonical = (BlockState) invoke(cell.matcher(), "getDisplayedState", 0L);
                BlockState normalized = state.rotate(inverse(match.template().rotation()));
                needsLayout |= !normalized.equals(canonical)
                        || (retained.data() != null && !retained.data().isEmpty());
                CompoundTag entry = new CompoundTag();
                entry.putLong("position", cell.offset()
                        .rotate(inverse(match.template().rotation())).asLong());
                entry.put("state", NbtUtils.writeBlockState(normalized));
                if (retained.data() != null && !retained.data().isEmpty()) {
                    entry.put("block_entity", retained.data());
                }
                layout.add(entry);
                blocks.add(new CaptureBlock(position, state, data,
                        BlockSnapshot.create(level.dimension(), level, position, Block.UPDATE_ALL)));
            }
            for (CaptureBlock block : blocks) {
                if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,
                        block.position(), block.state(), player)).isCanceled()) {
                    return new Result(Status.NO_PERMISSION, 0);
                }
            }
            if (!heldUnchanged(player, miniature, expected)
                    || !unchanged(level, blocks)
                    || !booleanValue(match.template().multiblock(), "validate",
                    level, match.anchor(), match.template().rotation())) {
                return new Result(Status.CHANGED, 0);
            }
            blocks.sort(Comparator.comparingInt((CaptureBlock block) ->
                    block.position().getY()).reversed());
            for (CaptureBlock block : blocks) {
                if (!heldUnchanged(player, miniature, expected)
                        || !level.getBlockState(block.position()).equals(block.state())
                        || !blockEntityDataMatches(level, block.position(), block.data())) {
                    rollback(level, removed);
                    return new Result(Status.CHANGED, 0);
                }
                removed.add(block);
                if (!level.setBlock(block.position(), Blocks.AIR.defaultBlockState(), REMOVE_FLAGS)) {
                    rollback(level, removed);
                    return new Result(Status.FAILED, 0);
                }
            }
            // Deferring shape updates until the whole group is gone prevents
            // a supported candle/rod from dropping while it is being packed.
            for (CaptureBlock block : blocks) {
                level.updateNeighborsAt(block.position(), block.state().getBlock());
                block.state().updateIndirectNeighbourShapes(level, block.position(), REMOVE_FLAGS);
                Blocks.AIR.defaultBlockState().updateNeighbourShapes(level, block.position(), REMOVE_FLAGS);
            }
            if (!heldUnchanged(player, miniature, expected)
                    || blocks.stream().anyMatch(block ->
                    !level.getBlockState(block.position()).isAir()
                            || level.getBlockEntity(block.position()) != null)) {
                rollback(level, removed);
                return new Result(Status.CHANGED, 0);
            }
            boolean storeLayout = needsLayout;
            committingItem = true;
            CustomData.update(DataComponents.CUSTOM_DATA, miniature, tag -> {
                tag.putString("pentacle", match.template().id().toString());
                if (storeLayout) {
                    tag.put(LAYOUT, layout);
                }
            });
            claims.retire(null, match.positions());
            return new Result(Status.CAPTURED, blocks.size());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            try {
                rollback(level, removed);
            } finally {
                if (committingItem && (player.getMainHandItem() == miniature
                        || player.getOffhandItem() == miniature)) {
                    // CUSTOM_DATA is the only component this transaction edits.
                    // Restore absence as well as values; merging components would
                    // leave the newly added pentacle behind on an originally blank item.
                    CustomData original = expected.get(DataComponents.CUSTOM_DATA);
                    if (original == null) {
                        miniature.remove(DataComponents.CUSTOM_DATA);
                    } else {
                        miniature.set(DataComponents.CUSTOM_DATA, original);
                    }
                }
            }
            return new Result(Status.FAILED, 0);
        } finally {
            level.captureBlockSnapshots = previousCapture;
            claims.end();
        }
    }

    private static Search find(ServerLevel level, BlockPos clicked)
            throws ReflectiveOperationException {
        if (!level.isInWorldBounds(clicked) || !level.hasChunkAt(clicked)) {
            return new Search(null, true);
        }
        List<Match> matches = new ArrayList<>();
        List<Template> unloaded = new ArrayList<>();
        List<ResourceLocation> pentacleIds = OccultismRecipeBridge.miniRitualPentacleIds(level);
        if (pentacleIds.isEmpty()) {
            return new Search(null, false);
        }
        Method predicateTest = Class.forName(
                "com.klikli_dev.modonomicon.api.multiblock.TriPredicate")
                .getMethod("test", Object.class, Object.class, Object.class);
        for (ResourceLocation id : pentacleIds) {
            var projection = OccultismRecipeBridge.findProjectionByPentacle(level, id);
            if (projection.isEmpty()) {
                continue;
            }
            Object multiblock = projection.get().multiblock();
            Object sizeValue = invoke(multiblock, "getSize");
            if (!(sizeValue instanceof Vec3i size) || size.getX() < 1 || size.getY() < 1
                    || size.getZ() < 1 || size.getX() > 33 || size.getY() > 8
                    || size.getZ() > 33 || (long) size.getX() * size.getY() * size.getZ() > MAX_CELLS) {
                continue;
            }
            for (Rotation rotation : Rotation.values()) {
                // Native DenseMultiblock.simulate enumerates geometry only;
                // neither its true nor its display-only cells read the world.
                Object value = invoke(multiblock, "simulate", level,
                        BlockPos.ZERO, rotation, false, false);
                if (!(value instanceof Pair<?, ?> pair)
                        || !(pair.getSecond() instanceof Collection<?> results)
                        || results.size() > MAX_CELLS) {
                    continue;
                }
                List<Cell> required = new ArrayList<>();
                List<BlockPos> bounds = new ArrayList<>();
                for (Object result : results) {
                    BlockPos offset = (BlockPos) invoke(result, "getWorldPosition");
                    Object matcher = invoke(result, "getStateMatcher");
                    bounds.add(offset);
                    if (booleanValue(matcher, "countsTowardsTotalBlocks")) {
                        required.add(new Cell(offset, matcher));
                    }
                }
                if (required.isEmpty()) {
                    continue;
                }
                Template template = new Template(id, multiblock, rotation, required, tier(id));
                Cell center = required.stream().filter(cell -> cell.offset().equals(BlockPos.ZERO))
                        .findFirst().orElse(null);
                Set<BlockPos> anchors = new LinkedHashSet<>();
                for (Cell cell : required) {
                    if (matchesCell(predicateTest, cell.matcher(), level, clicked, rotation)) {
                        anchors.add(clicked.subtract(cell.offset()));
                    }
                }
                for (BlockPos anchor : anchors) {
                    if (!level.isInWorldBounds(anchor) || !level.hasChunkAt(anchor)
                            || (center != null && !matchesCell(predicateTest,
                            center.matcher(), level, anchor, rotation))) {
                        continue;
                    }
                    if (!loaded(level, anchor, bounds)) {
                        unloaded.add(template);
                        continue;
                    }
                    if (booleanValue(multiblock, "validate", level, anchor, rotation)) {
                        Set<BlockPos> positions = new HashSet<>();
                        required.forEach(cell -> positions.add(anchor.offset(cell.offset())));
                        matches.add(new Match(template, anchor, Set.copyOf(positions)));
                    }
                }
            }
        }
        // A real containing structure wins even for data-pack IDs with no
        // known spirit tier. Never capture its valid smaller inner pentacle.
        List<Match> maximal = matches.stream().filter(candidate -> matches.stream()
                .noneMatch(other -> other.positions().size() > candidate.positions().size()
                        && other.positions().containsAll(candidate.positions()))).toList();
        Match best = maximal.stream().sorted(Comparator
                .comparingInt((Match match) -> match.template().tier()).reversed()
                .thenComparing(Comparator.comparingInt((Match match) ->
                        match.positions().size()).reversed())
                .thenComparing(match -> match.template().id().toString())
                .thenComparingLong(match -> match.anchor().asLong())
                .thenComparingInt(match -> match.template().rotation().ordinal()))
                .findFirst().orElse(null);
        boolean blockedByUnloaded = unloaded.stream().anyMatch(template -> best == null
                || template.tier() > best.template().tier()
                || template.required().size() >= best.positions().size());
        return new Search(best, blockedByUnloaded);
    }

    private static boolean matchesCell(Method test, Object matcher, ServerLevel level,
                                       BlockPos position, Rotation rotation)
            throws ReflectiveOperationException {
        Object predicate = invoke(matcher, "getStatePredicate");
        return Boolean.TRUE.equals(test.invoke(predicate, level, position,
                level.getBlockState(position).rotate(inverse(rotation))));
    }

    private static boolean loaded(ServerLevel level, BlockPos anchor, List<BlockPos> offsets) {
        for (BlockPos offset : offsets) {
            BlockPos position = anchor.offset(offset);
            // The one-block halo also covers shape/support queries during
            // validation and the eventual mutation's neighbour callbacks.
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos neighbor = position.offset(x, 0, z);
                    if (!level.isInWorldBounds(neighbor) || !level.hasChunkAt(neighbor)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static int tier(ResourceLocation id) {
        String path = id.getPath();
        if (path.contains("marid")) return 4;
        if (path.contains("afrit")) return 3;
        if (path.contains("djinni")) return 2;
        if (path.contains("foliot")) return 1;
        return 0;
    }

    static Rotation inverse(Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
    }

    static Map<BlockPos, LayoutBlock> layout(ServerLevel level, ItemStack stack,
                                            BlockPos anchor, Rotation rotation) {
        CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
        if (custom == null || !custom.getUnsafe().contains(LAYOUT)) {
            return Map.of();
        }
        ListTag entries = custom.getUnsafe().getList(LAYOUT, Tag.TAG_COMPOUND);
        if (entries.isEmpty() || entries.size() > MAX_CELLS) {
            throw new IllegalArgumentException("Invalid captured pentacle layout");
        }
        Map<BlockPos, LayoutBlock> result = new LinkedHashMap<>();
        var registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        for (Tag raw : entries) {
            CompoundTag entry = (CompoundTag) raw;
            BlockPos offset = BlockPos.of(entry.getLong("position"));
            if (Math.abs(offset.getX()) > 32 || Math.abs(offset.getY()) > 8
                    || Math.abs(offset.getZ()) > 32) {
                throw new IllegalArgumentException("Captured pentacle outside bounds");
            }
            BlockPos position = anchor.offset(offset.rotate(rotation));
            BlockState state = NbtUtils.readBlockState(registry, entry.getCompound("state"))
                    .rotate(rotation);
            CompoundTag data = entry.contains("block_entity", Tag.TAG_COMPOUND)
                    ? entry.getCompound("block_entity").copy() : null;
            if (state.isAir() || result.put(position, new LayoutBlock(state, data)) != null) {
                throw new IllegalArgumentException("Invalid captured pentacle cell");
            }
        }
        return result;
    }

    private static StaticData staticData(BlockEntity entity, CompoundTag original) {
        if (entity == null) {
            return new StaticData(true, null);
        }
        CompoundTag data = original.copy();
        if (data.contains("neoforge:attachments") || data.contains("NeoForgeData")) {
            return new StaticData(false, null);
        }
        if (isOccultismSacrificialBowl(entity)) {
            if (!safeGuardBlockEntity(entity, data)) {
                return new StaticData(false, null);
            }
            data.remove("inventory");
            data.remove("lastChangeTime");
            return new StaticData(data.isEmpty(), null);
        }
        if (entity.getClass() == BeaconBlockEntity.class) {
            data.remove("Levels");
            return new StaticData(true, data);
        }
        if (entity.getClass() == SkullBlockEntity.class
                || entity.getClass() == EnchantingTableBlockEntity.class) {
            return new StaticData(true, data);
        }
        return new StaticData(false, null);
    }

    private static boolean unchanged(ServerLevel level, List<CaptureBlock> blocks) {
        return blocks.stream().allMatch(block -> level.hasChunkAt(block.position())
                && level.getBlockState(block.position()).equals(block.state())
                && blockEntityDataMatches(level, block.position(), block.data()));
    }

    private static void rollback(ServerLevel level, List<CaptureBlock> removed) {
        boolean previousRestoring = level.restoringBlockSnapshots;
        level.restoringBlockSnapshots = true;
        try {
            for (int index = removed.size() - 1; index >= 0; index--) {
                try {
                    if (!removed.get(index).snapshot().restore(REMOVE_FLAGS)) {
                        MekanismMagic.LOGGER.error("Could not restore captured pentacle at {}",
                                removed.get(index).position());
                    }
                } catch (RuntimeException failure) {
                    MekanismMagic.LOGGER.error("Exception restoring captured pentacle at {}",
                            removed.get(index).position(), failure);
                }
            }
        } finally {
            level.restoringBlockSnapshots = previousRestoring;
        }
    }

    record LayoutBlock(BlockState state, CompoundTag data) { }
    private record StaticData(boolean safe, CompoundTag data) { }
    private record Cell(BlockPos offset, Object matcher) { }
    private record Template(ResourceLocation id, Object multiblock, Rotation rotation,
                            List<Cell> required, int tier) { }
    private record Match(Template template, BlockPos anchor, Set<BlockPos> positions) { }
    private record Search(Match match, boolean unloaded) { }
    private record CaptureBlock(BlockPos position, BlockState state, CompoundTag data,
                                BlockSnapshot snapshot) { }
}
