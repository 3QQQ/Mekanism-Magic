package com.example.mekanismmagic.integration.occultism;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Atomic unfold/retract support for a configured miniature pentacle. */
public final class MiniPentacleDeployment {
    private static final String DEPLOYED_DIMENSION =
            "mini_pentacle_deployed_dimension";
    private static final String DEPLOYED_ANCHOR =
            "mini_pentacle_deployed_anchor";
    private static final String DEPLOYED_ROTATION =
            "mini_pentacle_deployed_rotation";
    private static final String DEPLOYED_BLOCKS =
            "mini_pentacle_deployed_blocks";
    private static final String POSITION = "position";
    private static final String PREVIOUS_STATE = "previous_state";
    private static final String PLACED_STATE = "placed_state";
    private static final String PLACED_BLOCK_ENTITY =
            "placed_block_entity";
    private static final String OWNED_BY_MINIATURE =
            "owned_by_miniature";
    private static final int MAX_DEPLOYED_BLOCKS = 1_024;
    private static final int MAX_ANCHOR_DISTANCE_SQUARED = 48 * 48;

    private MiniPentacleDeployment() {
    }

    public enum Status {
        PLACED(true),
        RECOVERED(true),
        NOT_CONFIGURED(false),
        ALREADY_DEPLOYED(false),
        NOT_DEPLOYED(false),
        WRONG_DIMENSION(false),
        WRONG_TARGET(false),
        TOP_FACE_REQUIRED(false),
        STRUCTURE_ALREADY_PRESENT(false),
        OBSTRUCTED(false),
        CHANGED(false),
        NO_PERMISSION(false),
        UNLOADED(false),
        INVALID_STRUCTURE(false),
        FAILED(false);

        private final boolean success;

        Status(boolean success) {
            this.success = success;
        }

        public boolean success() {
            return success;
        }

        public String translationKey() {
            return "item.mekanism_magic.mini_ritual.deployment."
                    + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Result(Status status, int changedBlocks) {
        public boolean success() {
            return status.success();
        }
    }

    public record DeploymentLocation(ResourceLocation dimension,
                                     BlockPos anchor) {
    }

    public static boolean isDeployed(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty()
                && data.getUnsafe().contains(
                DEPLOYED_BLOCKS, Tag.TAG_LIST)
                && data.getUnsafe().contains(DEPLOYED_DIMENSION);
    }

    public static Optional<DeploymentLocation> location(ItemStack stack) {
        if (!isDeployed(stack)) {
            return Optional.empty();
        }
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        ResourceLocation dimension = ResourceLocation.tryParse(
                tag.getString(DEPLOYED_DIMENSION));
        return dimension == null ? Optional.empty()
                : Optional.of(new DeploymentLocation(dimension,
                BlockPos.of(tag.getLong(DEPLOYED_ANCHOR))));
    }

    public static Result deploy(
            ServerLevel level, ServerPlayer player,
            ItemStack miniature, BlockPos anchor,
            Rotation rotation) {
        if (isDeployed(miniature)) {
            return new Result(Status.ALREADY_DEPLOYED, 0);
        }
        Optional<OccultismRecipeBridge.RitualProjection> projection =
                projection(level, miniature);
        if (projection.isEmpty()) {
            return new Result(Status.NOT_CONFIGURED, 0);
        }
        if (!level.isInWorldBounds(anchor)
                || !level.hasChunkAt(anchor)) {
            return new Result(Status.UNLOADED, 0);
        }

        List<Mutation> applied = new ArrayList<>();
        try {
            Object simulated = invoke(projection.get().multiblock(),
                    "simulate", level, anchor, rotation, false, false);
            if (!(simulated instanceof Pair<?, ?> pair)
                    || !(pair.getSecond() instanceof Collection<?> results)) {
                return new Result(Status.INVALID_STRUCTURE, 0);
            }
            if (results.size() > MAX_DEPLOYED_BLOCKS) {
                return new Result(Status.INVALID_STRUCTURE, 0);
            }
            Map<BlockPos, Mutation> mutations = new LinkedHashMap<>();
            for (Object result : results) {
                Object positionValue = invoke(result,
                        "getWorldPosition");
                Object matcher = invoke(result, "getStateMatcher");
                if (!(positionValue instanceof BlockPos position)
                        || matcher == null) {
                    return new Result(Status.INVALID_STRUCTURE, 0);
                }
                if (!booleanValue(matcher, "countsTowardsTotalBlocks")) {
                    continue;
                }
                if (!level.isInWorldBounds(position)
                        || !level.hasChunkAt(position)) {
                    return new Result(Status.UNLOADED, 0);
                }
                if (!level.mayInteract(player, position)
                        || !player.mayUseItemAt(position,
                        Direction.UP, miniature)) {
                    return new Result(Status.NO_PERMISSION, 0);
                }
                BlockState previous = level.getBlockState(position);
                if (booleanValue(result, "test", level, rotation)) {
                    CompoundTag guardedBlockEntity = blockEntityData(
                            level, position);
                    if (!safeGuardBlockEntity(
                            level.getBlockEntity(position),
                            guardedBlockEntity)) {
                        return new Result(Status.CHANGED, 0);
                    }
                    mutations.put(position.immutable(), new Mutation(
                            position.immutable(), previous, previous,
                            result, null, guardedBlockEntity, false));
                    continue;
                }
                // Correct pre-existing structure blocks were recorded above
                // as guards and will never be owned or removed by this item.
                // Never overwrite grass, fluids, the wrong glyph color, or
                // any other player/world block merely because it is marked
                // replaceable.
                if (!previous.isAir()) {
                    return new Result(Status.OBSTRUCTED, 0);
                }
                BlockState placed = displayedState(
                        matcher, rotation);
                if (placed == null) {
                    return new Result(Status.INVALID_STRUCTURE, 0);
                }
                if (!level.isUnobstructed(placed, position,
                        CollisionContext.empty())) {
                    return new Result(Status.OBSTRUCTED, 0);
                }
                BlockSnapshot snapshot = BlockSnapshot.create(
                        level.dimension(), level, position,
                        Block.UPDATE_ALL);
                mutations.put(position.immutable(), new Mutation(
                        position.immutable(), previous, placed, result,
                        snapshot, null, true));
                if (mutations.size() > MAX_DEPLOYED_BLOCKS) {
                    return new Result(Status.INVALID_STRUCTURE, 0);
                }
            }
            if (mutations.isEmpty()
                    || mutations.values().stream()
                    .noneMatch(Mutation::ownedByMiniature)) {
                return new Result(Status.STRUCTURE_ALREADY_PRESENT, 0);
            }

            List<Mutation> ordered = new ArrayList<>(mutations.values());
            ordered.sort(Comparator
                    .comparingInt((Mutation mutation) ->
                            mutation.position().getY())
                    .thenComparingInt(mutation ->
                            mutation.position().getX())
                    .thenComparingInt(mutation ->
                            mutation.position().getZ()));
            applied = new ArrayList<>(ordered.size());
            for (Mutation mutation : ordered) {
                if (!mutation.ownedByMiniature()) {
                    continue;
                }
                // Register before the world call: a block callback may throw
                // after the chunk state has already changed.
                applied.add(mutation);
                if (!level.setBlock(mutation.position(),
                        mutation.placed(), Block.UPDATE_ALL)) {
                    rollbackPlacement(level, applied);
                    return new Result(Status.FAILED, 0);
                }
                if (!mutation.placed().canSurvive(
                        level, mutation.position())
                        || !booleanValue(mutation.simulationResult(),
                        "test", level, rotation)) {
                    rollbackPlacement(level, applied);
                    return new Result(Status.FAILED, 0);
                }
            }
            if (!booleanValue(projection.get().multiblock(),
                    "validate", level, anchor, rotation)) {
                rollbackPlacement(level, applied);
                return new Result(Status.FAILED, 0);
            }
            List<Mutation> recorded = ordered.stream()
                    .map(mutation -> new Mutation(
                             mutation.position(), mutation.previous(),
                             level.getBlockState(mutation.position()), null,
                             mutation.snapshot(), blockEntityData(
                             level, mutation.position()),
                             mutation.ownedByMiniature()))
                     .toList();
            saveDeployment(miniature, level, anchor, rotation, recorded);
            return new Result(Status.PLACED, applied.size());
        } catch (ReflectiveOperationException | RuntimeException failure) {
            rollbackPlacement(level, applied);
            return new Result(Status.FAILED, 0);
        }
    }

    public static Result recover(
            ServerLevel level, ServerPlayer player,
            ItemStack miniature, BlockPos clickedPosition) {
        CustomData customData = miniature.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()
                || !isDeployed(miniature)) {
            return new Result(Status.NOT_DEPLOYED, 0);
        }
        CompoundTag root = customData.copyTag();
        ResourceLocation storedDimension = ResourceLocation.tryParse(
                root.getString(DEPLOYED_DIMENSION));
        if (storedDimension == null
                || !storedDimension.equals(level.dimension().location())) {
            return new Result(Status.WRONG_DIMENSION, 0);
        }
        BlockPos anchor = BlockPos.of(root.getLong(DEPLOYED_ANCHOR));
        ListTag storedBlocks = root.getList(
                DEPLOYED_BLOCKS, Tag.TAG_COMPOUND);
        if (storedBlocks.isEmpty()
                || storedBlocks.size() > MAX_DEPLOYED_BLOCKS) {
            return new Result(Status.INVALID_STRUCTURE, 0);
        }

        List<Mutation> mutations = new ArrayList<>(storedBlocks.size());
        boolean targetMatches = clickedPosition.equals(anchor);
        var blockRegistry = level.registryAccess()
                .lookupOrThrow(Registries.BLOCK);
        for (Tag raw : storedBlocks) {
            if (!(raw instanceof CompoundTag entry)) {
                return new Result(Status.INVALID_STRUCTURE, 0);
            }
            BlockPos position = BlockPos.of(entry.getLong(POSITION));
            if (position.distSqr(anchor) > MAX_ANCHOR_DISTANCE_SQUARED
                    || !level.isInWorldBounds(position)
                    || !level.hasChunkAt(position)) {
                return new Result(Status.UNLOADED, 0);
            }
            targetMatches |= clickedPosition.equals(position);
            if (!level.mayInteract(player, position)
                    || !player.mayUseItemAt(position,
                    Direction.UP, miniature)) {
                return new Result(Status.NO_PERMISSION, 0);
            }
            BlockState previous = NbtUtils.readBlockState(
                    blockRegistry,
                    entry.getCompound(PREVIOUS_STATE));
            BlockState placed = NbtUtils.readBlockState(
                    blockRegistry,
                    entry.getCompound(PLACED_STATE));
            boolean ownedByMiniature = !entry.contains(
                    OWNED_BY_MINIATURE, Tag.TAG_BYTE)
                    || entry.getBoolean(OWNED_BY_MINIATURE);
            if (!level.getBlockState(position).equals(placed)) {
                return new Result(Status.CHANGED, 0);
            }
            CompoundTag expectedBlockEntity = entry.contains(
                    PLACED_BLOCK_ENTITY, Tag.TAG_COMPOUND)
                    ? entry.getCompound(PLACED_BLOCK_ENTITY) : null;
            if (!blockEntityDataMatches(
                    level, position, expectedBlockEntity)) {
                return new Result(Status.CHANGED, 0);
            }
            BlockSnapshot snapshot = ownedByMiniature
                    ? BlockSnapshot.create(level.dimension(), level,
                    position, Block.UPDATE_ALL) : null;
            mutations.add(new Mutation(position, previous,
                    placed, null, snapshot, expectedBlockEntity,
                    ownedByMiniature));
        }
        if (!targetMatches) {
            return new Result(Status.WRONG_TARGET, 0);
        }
        for (Mutation mutation : mutations) {
            if (!mutation.ownedByMiniature()) {
                continue;
            }
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(
                    level, mutation.position(), mutation.placed(), player);
            if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                return new Result(Status.NO_PERMISSION, 0);
            }
        }
        for (Mutation mutation : mutations) {
            if (!level.getBlockState(mutation.position())
                    .equals(mutation.placed())
                    || !blockEntityDataMatches(level,
                    mutation.position(),
                    mutation.placedBlockEntity())) {
                return new Result(Status.CHANGED, 0);
            }
        }

        List<Mutation> ownedMutations = mutations.stream()
                .filter(Mutation::ownedByMiniature)
                .collect(java.util.stream.Collectors.toCollection(
                        ArrayList::new));
        if (ownedMutations.isEmpty()) {
            return new Result(Status.INVALID_STRUCTURE, 0);
        }
        ownedMutations.sort(Comparator
                .comparingInt((Mutation mutation) ->
                        mutation.position().getY()).reversed());
        List<Mutation> restored = new ArrayList<>(
                ownedMutations.size());
        boolean previousCapture = level.captureBlockSnapshots;
        try {
            // CommonHooks wraps Item#useOn in placement snapshot capture. A
            // retraction is a sequence of authorized breaks, not a placement;
            // keep it out of that outer list so protection mods see each
            // BreakEvent above exactly once and no synthetic PlaceEvent.
            level.captureBlockSnapshots = false;
            for (Mutation mutation : ownedMutations) {
                // Register before the world call for exception-safe rollback.
                restored.add(mutation);
                if (!level.setBlock(mutation.position(),
                        mutation.previous(), Block.UPDATE_ALL)) {
                    rollbackRecovery(level, restored);
                    return new Result(Status.FAILED, 0);
                }
            }
            CustomData.update(DataComponents.CUSTOM_DATA, miniature, tag -> {
                tag.remove(DEPLOYED_DIMENSION);
                tag.remove(DEPLOYED_ANCHOR);
                tag.remove(DEPLOYED_ROTATION);
                tag.remove(DEPLOYED_BLOCKS);
            });
            return new Result(Status.RECOVERED,
                    ownedMutations.size());
        } catch (RuntimeException failure) {
            try {
                rollbackRecovery(level, restored);
            } finally {
                miniature.set(DataComponents.CUSTOM_DATA, customData);
            }
            return new Result(Status.FAILED, 0);
        } finally {
            level.captureBlockSnapshots = previousCapture;
        }
    }

    public static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static Optional<OccultismRecipeBridge.RitualProjection>
    projection(ServerLevel level, ItemStack miniature) {
        CustomData data = miniature.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation ritual = ResourceLocation.tryParse(
                data.getUnsafe().getString("ritual"));
        if (ritual != null) {
            Optional<OccultismRecipeBridge.RitualProjection> projection =
                    OccultismRecipeBridge.findProjection(level, ritual);
            if (projection.isPresent()) {
                return projection;
            }
        }
        ResourceLocation pentacle = ResourceLocation.tryParse(
                data.getUnsafe().getString("pentacle"));
        return pentacle == null ? Optional.empty()
                : OccultismRecipeBridge.findProjectionByPentacle(
                level, pentacle);
    }

    private static BlockState displayedState(
            Object matcher, Rotation rotation)
            throws ReflectiveOperationException {
        Object value = invoke(matcher, "getDisplayedState", 0L);
        if (!(value instanceof BlockState state)) {
            return null;
        }
        return state.rotate(rotation);
    }

    private static void saveDeployment(
            ItemStack miniature, ServerLevel level,
            BlockPos anchor, Rotation rotation,
            List<Mutation> mutations) {
        ListTag blocks = new ListTag();
        for (Mutation mutation : mutations) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(POSITION, mutation.position().asLong());
            entry.put(PREVIOUS_STATE,
                    NbtUtils.writeBlockState(mutation.previous()));
            entry.put(PLACED_STATE,
                    NbtUtils.writeBlockState(mutation.placed()));
            if (mutation.placedBlockEntity() != null) {
                entry.put(PLACED_BLOCK_ENTITY,
                        mutation.placedBlockEntity().copy());
            }
            entry.putBoolean(OWNED_BY_MINIATURE,
                    mutation.ownedByMiniature());
            blocks.add(entry);
        }
        CustomData.update(DataComponents.CUSTOM_DATA, miniature, tag -> {
            tag.putString(DEPLOYED_DIMENSION,
                    level.dimension().location().toString());
            tag.putLong(DEPLOYED_ANCHOR, anchor.asLong());
            tag.putString(DEPLOYED_ROTATION, rotation.name());
            tag.put(DEPLOYED_BLOCKS, blocks);
        });
    }

    private static void rollbackPlacement(
            ServerLevel level, List<Mutation> applied) {
        restoreSnapshots(level, applied, "placement");
    }

    private static void rollbackRecovery(
            ServerLevel level, List<Mutation> restored) {
        restoreSnapshots(level, restored, "recovery");
    }

    private static void restoreSnapshots(
            ServerLevel level, List<Mutation> mutations,
            String operation) {
        boolean previousCapture = level.captureBlockSnapshots;
        boolean previousRestoring = level.restoringBlockSnapshots;
        level.captureBlockSnapshots = false;
        level.restoringBlockSnapshots = true;
        try {
            for (int index = mutations.size() - 1;
                 index >= 0; index--) {
                Mutation mutation = mutations.get(index);
                if (mutation.snapshot() == null) {
                    continue;
                }
                try {
                    if (!mutation.snapshot().restore(Block.UPDATE_ALL)) {
                        com.example.mekanismmagic.MekanismMagic.LOGGER.error(
                                "Failed to roll back miniature pentacle {} at {}",
                                operation, mutation.position());
                    }
                } catch (RuntimeException failure) {
                    com.example.mekanismmagic.MekanismMagic.LOGGER.error(
                            "Exception rolling back miniature pentacle {} at {}",
                            operation, mutation.position(), failure);
                }
            }
        } finally {
            level.restoringBlockSnapshots = previousRestoring;
            level.captureBlockSnapshots = previousCapture;
        }
    }

    private static boolean safeGuardBlockEntity(
            BlockEntity blockEntity, CompoundTag data) {
        if (data == null || !isOccultismSacrificialBowl(blockEntity)) {
            return true;
        }
        CompoundTag inventory = data.getCompound("inventory");
        return inventory.getList("Items", Tag.TAG_COMPOUND).isEmpty()
                && !data.contains("currentRitual")
                && !data.getBoolean("ritualActive")
                && !data.contains("consumedIngredients");
    }

    private static CompoundTag blockEntityData(
            ServerLevel level, BlockPos position) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        return blockEntity == null ? null
                : blockEntity.saveWithoutMetadata(
                level.registryAccess());
    }

    private static boolean blockEntityDataMatches(
            ServerLevel level, BlockPos position,
            CompoundTag expected) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        CompoundTag current = blockEntity == null ? null
                : blockEntity.saveWithoutMetadata(level.registryAccess());
        if (expected == null || current == null) {
            return expected == null && current == null;
        }
        CompoundTag normalizedExpected = expected.copy();
        CompoundTag normalizedCurrent = current.copy();
        if (isOccultismSacrificialBowl(blockEntity)) {
            // Occultism updates this render/synchronization timestamp whenever
            // an item enters or leaves the bowl. It has no ritual state, and
            // retaining it in the comparison would make a clean, idle
            // pentacle impossible to retract after one normal use. Inventory,
            // currentRitual, ritualActive and attachments remain exact-match.
            normalizedExpected.remove("lastChangeTime");
            normalizedCurrent.remove("lastChangeTime");
        }
        return normalizedExpected.equals(normalizedCurrent);
    }

    private static boolean isOccultismSacrificialBowl(
            BlockEntity blockEntity) {
        for (Class<?> type = blockEntity == null ? null
                : blockEntity.getClass(); type != null;
             type = type.getSuperclass()) {
            if (type.getName().equals("com.klikli_dev.occultism.common."
                    + "blockentity.SacrificialBowlBlockEntity")) {
                return true;
            }
        }
        return false;
    }

    private static Object invoke(
            Object target, String method, Object... arguments)
            throws ReflectiveOperationException {
        if (target == null) {
            return null;
        }
        for (Method candidate : target.getClass().getMethods()) {
            if (candidate.getName().equals(method)
                    && candidate.getParameterCount()
                    == arguments.length
                    && parametersAccept(candidate.getParameterTypes(),
                    arguments)) {
                return candidate.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(target.getClass().getName()
                + "#" + method);
    }

    private static boolean parametersAccept(
            Class<?>[] parameters, Object[] arguments) {
        for (int index = 0; index < parameters.length; index++) {
            if (arguments[index] == null) {
                if (parameters[index].isPrimitive()) {
                    return false;
                }
                continue;
            }
            Class<?> parameter = parameters[index].isPrimitive()
                    ? primitiveWrapper(parameters[index])
                    : parameters[index];
            if (!parameter.isInstance(arguments[index])) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> primitiveWrapper(Class<?> primitive) {
        if (primitive == boolean.class) {
            return Boolean.class;
        } else if (primitive == byte.class) {
            return Byte.class;
        } else if (primitive == short.class) {
            return Short.class;
        } else if (primitive == int.class) {
            return Integer.class;
        } else if (primitive == long.class) {
            return Long.class;
        } else if (primitive == float.class) {
            return Float.class;
        } else if (primitive == double.class) {
            return Double.class;
        } else if (primitive == char.class) {
            return Character.class;
        }
        return primitive;
    }

    private static boolean booleanValue(
            Object target, String method, Object... arguments)
            throws ReflectiveOperationException {
        return invoke(target, method, arguments) instanceof Boolean value
                && value;
    }

    private record Mutation(BlockPos position,
                            BlockState previous,
                            BlockState placed,
                            Object simulationResult,
                            BlockSnapshot snapshot,
                            CompoundTag placedBlockEntity,
                            boolean ownedByMiniature) {
    }
}
