package com.example.mekanismmagic.integration.naturesaura;

import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableBoolean;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Composable Nature's Aura storage state shared by ordinary machines and
 * Mekanism factory tiles. Keeping the capability, persistence and type rules
 * here prevents the two inheritance trees from developing different Aura
 * semantics.
 */
public final class NatureAuraStorageComponent {
    private static final String AURA_DATA = "nature_aura_storage";
    private static final String AURA_DATA_VERSION =
            "nature_aura_data_version";
    private static final String AURA_TYPE = "nature_aura_type";
    private static final String AURA_SIDE_MODES = "nature_aura_side_modes";
    private static final int CURRENT_AURA_DATA_VERSION = 1;

    private final Supplier<@Nullable Level> levelSupplier;
    private final Supplier<@Nullable IAuraType> operatingTypeSupplier;
    private final BooleanSupplier creativeSupplier;
    private final Runnable changedListener;
    private final Runnable capabilityInvalidation;
    private final Runnable auraChangedListener;
    private final SafeAuraContainer storage;
    private final NatureAuraMachineBlockEntity.AuraSideMode defaultSideMode;
    private final int transferRate;
    private final EnumMap<Direction,
            NatureAuraMachineBlockEntity.AuraSideMode> sideModes =
            new EnumMap<>(Direction.class);
    private final EnumMap<Direction, IAuraContainer> sidedViews =
            new EnumMap<>(Direction.class);

    private ResourceLocation boundAuraType;
    private boolean auraTypeMismatch;
    private boolean auraDataVersionSupported = true;
    private int auraDataVersion = CURRENT_AURA_DATA_VERSION;
    private int syncedAuraColor = 0x42A6BC;
    private boolean syncedCreativeAura;
    private boolean notEnoughAura;
    private long externalBudgetTick = Long.MIN_VALUE;
    private int externalReceived;
    private int externalExtracted;

    public NatureAuraStorageComponent(
            Supplier<@Nullable Level> levelSupplier,
            BooleanSupplier creativeSupplier,
            Runnable changedListener,
            Runnable capabilityInvalidation,
            Runnable auraChangedListener,
            int capacity, int transferRate,
            NatureAuraMachineBlockEntity.AuraSideMode defaultSideMode) {
        this(levelSupplier, creativeSupplier, changedListener,
                capabilityInvalidation, auraChangedListener, capacity,
                transferRate, defaultSideMode, () -> {
                    Level level = levelSupplier.get();
                    return level == null ? null : IAuraType.forLevel(level);
                });
    }

    public NatureAuraStorageComponent(
            Supplier<@Nullable Level> levelSupplier,
            BooleanSupplier creativeSupplier,
            Runnable changedListener,
            Runnable capabilityInvalidation,
            Runnable auraChangedListener,
            int capacity, int transferRate,
            NatureAuraMachineBlockEntity.AuraSideMode defaultSideMode,
            Supplier<@Nullable IAuraType> operatingTypeSupplier) {
        this.levelSupplier = Objects.requireNonNull(
                levelSupplier, "levelSupplier");
        this.operatingTypeSupplier = Objects.requireNonNull(
                operatingTypeSupplier, "operatingTypeSupplier");
        this.creativeSupplier = Objects.requireNonNull(
                creativeSupplier, "creativeSupplier");
        this.changedListener = Objects.requireNonNull(
                changedListener, "changedListener");
        this.capabilityInvalidation = Objects.requireNonNull(
                capabilityInvalidation, "capabilityInvalidation");
        this.auraChangedListener = Objects.requireNonNull(
                auraChangedListener, "auraChangedListener");
        this.defaultSideMode = defaultSideMode == null
                ? NatureAuraMachineBlockEntity.AuraSideMode.NONE
                : defaultSideMode;
        this.transferRate = Math.max(0, transferRate);
        storage = new SafeAuraContainer(this::storedAuraType,
                Math.max(0, capacity), this.transferRate,
                this.transferRate, this::onContentsChanged);
        for (Direction direction : Direction.values()) {
            sideModes.put(direction, this.defaultSideMode);
            sidedViews.put(direction, new ExternalAuraView(direction));
        }
    }

    /** Convenience constructor for factory tiles using the composed state. */
    public NatureAuraStorageComponent(
            TileEntityMekanism owner,
            int capacity, int transferRate,
            NatureAuraMachineBlockEntity.AuraSideMode defaultSideMode,
            BooleanSupplier creativeSupplier) {
        this(owner::getLevel, creativeSupplier, owner::setChanged,
                () -> {
                    Level level = owner.getLevel();
                    if (level != null) {
                        level.invalidateCapabilities(owner.getBlockPos());
                    }
                }, () -> {
                }, capacity, transferRate, defaultSideMode);
    }

    @Nullable IAuraContainer getContainer(@Nullable Direction side) {
        if (side == null || getSideMode(side)
                == NatureAuraMachineBlockEntity.AuraSideMode.NONE) {
            return null;
        }
        return sidedViews.get(side);
    }

    public @Nullable IAuraContainer getAuraContainer(@Nullable Direction side) {
        return getContainer(side);
    }

    NatureAuraMachineBlockEntity.AuraSideMode getSideMode(Direction side) {
        return sideModes.getOrDefault(side,
                NatureAuraMachineBlockEntity.AuraSideMode.NONE);
    }

    public NatureAuraMachineBlockEntity.AuraSideMode getAuraSideMode(
            Direction side) {
        return getSideMode(side);
    }

    void cycle(int directionIndex) {
        Direction[] directions = Direction.values();
        if (directionIndex < 0 || directionIndex >= directions.length) {
            return;
        }
        Direction side = directions[directionIndex];
        setAuraSideMode(side, getSideMode(side).next());
    }

    public void cycleAuraSideMode(int directionIndex) {
        cycle(directionIndex);
    }

    public void setAuraSideMode(Direction side,
                               NatureAuraMachineBlockEntity.AuraSideMode mode) {
        setAuraSideModes(Map.of(Objects.requireNonNull(side, "side"),
                Objects.requireNonNull(mode, "mode")));
    }

    public void setAuraSideModes(Map<Direction,
            NatureAuraMachineBlockEntity.AuraSideMode> modes) {
        Objects.requireNonNull(modes, "modes");
        // Validate the whole batch before applying any side.
        modes.forEach((side, mode) -> {
            Objects.requireNonNull(side, "side");
            Objects.requireNonNull(mode, "mode");
        });
        boolean changed = false;
        for (var entry : modes.entrySet()) {
            if (getSideMode(entry.getKey()) != entry.getValue()) {
                sideModes.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }
        if (changed) {
            changedListener.run();
            capabilityInvalidation.run();
        }
    }

    public boolean isCreativeAuraActive() {
        Level level = levelSupplier.get();
        return level != null && level.isClientSide()
                ? syncedCreativeAura : creativeSupplier.getAsBoolean();
    }

    int getStored() {
        return storage.getStoredAura();
    }

    public int getStoredAura() {
        return getStored();
    }

    int capacity() {
        return storage.getMaxAura();
    }

    public int getAuraCapacity() {
        return capacity();
    }

    int color() {
        Level level = levelSupplier.get();
        return level != null && level.isClientSide()
                ? syncedAuraColor : storage.getAuraColor();
    }

    public int getAuraColor() {
        return color();
    }

    boolean mismatch() {
        return auraTypeMismatch;
    }

    public boolean hasAuraTypeMismatch() {
        return mismatch();
    }

    boolean notEnough() {
        return notEnoughAura;
    }

    public boolean hasNotEnoughAura() {
        return notEnough();
    }

    void setNotEnough(boolean notEnough) {
        notEnoughAura = notEnough;
    }

    public void setNotEnoughAura(boolean notEnough) {
        setNotEnough(notEnough);
    }

    void resetWarning() {
        notEnoughAura = false;
    }

    void beginServerTick() {
        resetWarning();
        refreshTypeState();
    }

    public boolean refreshTypeState() {
        if (!auraDataVersionSupported) {
            auraTypeMismatch = true;
            return false;
        }
        if (getStored() <= 0) {
            boundAuraType = null;
            auraTypeMismatch = false;
            return true;
        }
        IAuraType storedType = storedAuraType();
        IAuraType localType = operatingTypeSupplier.get();
        auraTypeMismatch = boundAuraType == null || storedType == null
                || localType == null || !storedType.isSimilar(localType);
        return !auraTypeMismatch;
    }

    int budgetedStore(int amount, boolean simulate) {
        resetExternalBudget();
        if (creativeSupplier.getAsBoolean() || !refreshTypeState()) {
            return 0;
        }
        int remaining = Math.max(0, transferRate - externalReceived);
        int accepted = storage.storeAura(
                Math.min(Math.max(0, amount), remaining), simulate);
        if (!simulate) {
            externalReceived += accepted;
        }
        return accepted;
    }

    int budgetedDrain(int amount, boolean simulate) {
        resetExternalBudget();
        if (!refreshTypeState()) {
            return 0;
        }
        int remaining = Math.max(0, transferRate - externalExtracted);
        int extracted = storage.drainAura(
                Math.min(Math.max(0, amount), remaining), simulate);
        if (!simulate) {
            externalExtracted += extracted;
        }
        return extracted;
    }

    boolean exactDrain(int amount, boolean simulate) {
        return refreshTypeState() && storage.drainExact(amount, simulate);
    }

    public boolean drainExact(int amount, boolean simulate) {
        return exactDrain(amount, simulate);
    }

    boolean exactStore(int amount, boolean simulate) {
        return refreshTypeState() && storage.storeExact(amount, simulate);
    }

    void addTrackers(MekanismContainer container) {
        container.track(SyncableInt.create(this::getStored,
                storage::setStoredAuraFromSync));
        container.track(SyncableInt.create(this::color,
                value -> syncedAuraColor = value));
        container.track(SyncableBoolean.create(this::mismatch,
                value -> auraTypeMismatch = value));
        container.track(SyncableBoolean.create(this::notEnough,
                value -> notEnoughAura = value));
        // Upgrade counts normally sync only while the upgrade window is open.
        container.track(SyncableBoolean.create(this::isCreativeAuraActive,
                value -> syncedCreativeAura = value));
        for (Direction direction : Direction.values()) {
            container.track(SyncableInt.create(
                    () -> getSideMode(direction).ordinal(),
                    value -> sideModes.put(direction,
                            NatureAuraMachineBlockEntity.AuraSideMode
                                    .values()[Math.max(0, Math.min(
                                    NatureAuraMachineBlockEntity.AuraSideMode
                                            .values().length - 1,
                                    value))])));
        }
    }

    public void addContainerTrackers(MekanismContainer container) {
        addTrackers(container);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(AURA_DATA, storage.serializeNBT(registries));
        tag.putInt(AURA_DATA_VERSION, auraDataVersion);
        if (boundAuraType != null) {
            tag.putString(AURA_TYPE, boundAuraType.toString());
        }
        CompoundTag savedModes = new CompoundTag();
        for (Direction direction : Direction.values()) {
            savedModes.putString(direction.getName(),
                    getSideMode(direction).name());
        }
        tag.put(AURA_SIDE_MODES, savedModes);
    }

    public void save(CompoundTag parent, String key,
              HolderLookup.Provider registries) {
        CompoundTag component = new CompoundTag();
        save(component, registries);
        parent.put(key, component);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        storage.deserializeNBT(registries, tag.getCompound(AURA_DATA));
        int loadedVersion = tag.contains(AURA_DATA_VERSION)
                ? tag.getInt(AURA_DATA_VERSION) : 0;
        applyDataVersion(loadedVersion);
        String savedAuraType = tag.getString(AURA_TYPE);
        boundAuraType = savedAuraType.isBlank() ? null
                : ResourceLocation.tryParse(savedAuraType);
        if (boundAuraType != null && boundAuraType.getPath().isBlank()) {
            boundAuraType = null;
        }
        if (getStored() <= 0) {
            boundAuraType = null;
        }
        CompoundTag savedModes = tag.getCompound(AURA_SIDE_MODES);
        for (Direction direction : Direction.values()) {
            String savedMode = savedModes.getString(direction.getName());
            try {
                sideModes.put(direction, savedMode.isEmpty()
                        ? defaultSideMode
                        : NatureAuraMachineBlockEntity.AuraSideMode
                        .valueOf(savedMode));
            } catch (IllegalArgumentException ignored) {
                sideModes.put(direction,
                        NatureAuraMachineBlockEntity.AuraSideMode.NONE);
            }
        }
        resetExternalBudgetState();
        refreshTypeState();
    }

    public void load(CompoundTag parent, String key,
              HolderLookup.Provider registries) {
        load(parent.getCompound(key), registries);
    }

    public Snapshot snapshot() {
        return new Snapshot(getStored(), auraDataVersion,
                boundAuraType, sideModes);
    }

    public void restore(Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        applyDataVersion(snapshot.dataVersion());
        boundAuraType = snapshot.boundAuraType();
        storage.setStoredAuraFromSync(snapshot.storedAura());
        if (getStored() <= 0) {
            boundAuraType = null;
        }
        for (Direction direction : Direction.values()) {
            sideModes.put(direction, snapshot.sideModes().getOrDefault(
                    direction, defaultSideMode));
        }
        resetExternalBudgetState();
        refreshTypeState();
        changedListener.run();
        capabilityInvalidation.run();
    }

    private void applyDataVersion(int loadedVersion) {
        auraDataVersionSupported = loadedVersion >= 0
                && loadedVersion <= CURRENT_AURA_DATA_VERSION;
        auraDataVersion = loadedVersion == 0
                ? CURRENT_AURA_DATA_VERSION : loadedVersion;
    }

    private IAuraType storedAuraType() {
        if (boundAuraType != null) {
            return NaturesAuraAPI.AURA_TYPES.get(boundAuraType);
        }
        return operatingTypeSupplier.get();
    }

    private void onContentsChanged() {
        if (getStored() <= 0) {
            boundAuraType = null;
        } else if (boundAuraType == null) {
            IAuraType type = operatingTypeSupplier.get();
            if (type != null) {
                boundAuraType = type.getName();
            }
        }
        refreshTypeState();
        changedListener.run();
        auraChangedListener.run();
    }

    private void resetExternalBudget() {
        Level level = levelSupplier.get();
        long gameTime = level == null ? Long.MIN_VALUE
                : level.getGameTime();
        if (gameTime != externalBudgetTick) {
            externalBudgetTick = gameTime;
            externalReceived = 0;
            externalExtracted = 0;
        }
    }

    private void resetExternalBudgetState() {
        externalBudgetTick = Long.MIN_VALUE;
        externalReceived = 0;
        externalExtracted = 0;
    }

    /** Immutable state transferred by a Mekanism tier installer. */
    public record Snapshot(
            int storedAura,
            int dataVersion,
            @Nullable ResourceLocation boundAuraType,
            Map<Direction,
                    NatureAuraMachineBlockEntity.AuraSideMode> sideModes) {
        public Snapshot {
            storedAura = Math.max(0, storedAura);
            EnumMap<Direction,
                    NatureAuraMachineBlockEntity.AuraSideMode> copy =
                    new EnumMap<>(Direction.class);
            if (sideModes != null) {
                copy.putAll(sideModes);
            }
            sideModes = Collections.unmodifiableMap(copy);
        }
    }

    private final class ExternalAuraView implements IAuraContainer {
        private final Direction side;

        private ExternalAuraView(Direction side) {
            this.side = side;
        }

        @Override
        public int storeAura(int amount, boolean simulate) {
            return getSideMode(side).allowsInput()
                    ? budgetedStore(amount, simulate) : 0;
        }

        @Override
        public int drainAura(int amount, boolean simulate) {
            return getSideMode(side).allowsOutput()
                    ? budgetedDrain(amount, simulate) : 0;
        }

        @Override
        public int getStoredAura() {
            return getStored();
        }

        @Override
        public int getMaxAura() {
            return capacity();
        }

        @Override
        public int getAuraColor() {
            return color();
        }

        @Override
        public boolean isAcceptableType(IAuraType type) {
            return getSideMode(side)
                    != NatureAuraMachineBlockEntity.AuraSideMode.NONE
                    && refreshTypeState()
                    && storage.isAcceptableType(type);
        }
    }
}
