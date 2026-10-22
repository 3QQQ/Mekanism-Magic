package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.arsnouveau.MagicSourcePipeTier;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceNetwork;
import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;

/** Aura-only capability, endpoint caches and saved share for one magic pipe. */
public final class MagicPipeAuraTransmitter
        implements MagicPipeResourceHooks.PipeExtension {
    private static final String SHARE = "magic_pipe_aura_share";
    private static final String TYPE = "magic_pipe_aura_type";
    private final MagicSourceTransmitter pipe;
    private final NaturalAuraBuffer local;
    private final EnumMap<Direction, BlockCapabilityCache<IAuraContainer,
            Direction>> endpoints = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, IAuraContainer> sides =
            new EnumMap<>(Direction.class);
    private final IAuraContainer unsided = new SidedView(null);
    private int saveShare;
    private @Nullable ResourceLocation localType;
    private @Nullable ResourceLocation saveType;
    private float clientScale;
    private int clientColor = 0x42A6BC;

    MagicPipeAuraTransmitter(MagicSourceTransmitter pipe) {
        this.pipe = pipe;
        local = new NaturalAuraBuffer(this::effectiveType,
                this::tierCapacity, this::localChanged);
        for (Direction side : Direction.values()) {
            sides.put(side, new SidedView(side));
        }
    }

    public int tierCapacity() {
        return tierCapacity(pipe.sourceTier);
    }

    public static int tierCapacity(MagicSourcePipeTier tier) {
        return (int) Math.min(Integer.MAX_VALUE, tier.capacity() * 10L);
    }

    public int transferRate() {
        return transferRate(pipe.sourceTier);
    }

    public static int transferRate(MagicSourcePipeTier tier) {
        // The original four Aura tiers use one quarter of their capacity.
        return (int) Math.min(Integer.MAX_VALUE,
                tier.capacity() * 5L / 2L);
    }

    @Nullable IAuraType localType() {
        return pipe.getLevel() == null ? null
                : IAuraType.forLevel(pipe.getLevel());
    }

    @Nullable IAuraType effectiveType() {
        return localType == null ? localType()
                : NaturesAuraAPI.AURA_TYPES.get(localType);
    }

    int localStored() { return local.getStoredAura(); }

    boolean hasForeignShare() {
        return localStored() > 0 && !NaturalAuraPipeSemantics.compatible(
                effectiveType(), localType());
    }

    void consumeLocal(int amount) {
        local.drainAura(amount, false);
    }

    private @Nullable MagicPipeAuraChannel channel() {
        return pipe.hasTransmitterNetwork() ? MagicPipeResourceHooks.networkExtension(
                pipe.getTransmitterNetwork(), MagicPipeAuraChannel.class) : null;
    }

    private IAuraContainer buffer() {
        MagicPipeAuraChannel aura = channel();
        return localStored() > 0 || aura == null ? local : aura;
    }

    private boolean enabled(@Nullable Direction side) {
        return pipe.isValid()
                && !pipe.isRedstoneActivated() && !hasForeignShare()
                && (side == null || pipe.getConnectionTypeRaw(side)
                != ConnectionType.NONE)
                && NaturalAuraPipeSemantics.compatible(
                localType(), currentType());
    }

    private @Nullable IAuraType currentType() {
        MagicPipeAuraChannel aura = channel();
        return localStored() > 0 || aura == null
                ? effectiveType() : aura.type();
    }

    public @Nullable IAuraContainer capability(@Nullable Direction side) {
        return enabled(side) ? side == null ? unsided : sides.get(side)
                : null;
    }

    private @Nullable IAuraContainer endpoint(Direction side) {
        if (!(pipe.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        var cache = endpoints.computeIfAbsent(side, direction ->
                BlockCapabilityCache.create(
                        NaturesAuraAPI.AURA_CONTAINER_BLOCK_CAPABILITY,
                        level, pipe.getBlockPos().relative(direction),
                        direction.getOpposite(),
                        () -> !pipe.getTransmitterTile().isRemoved(),
                        () -> pipe.getTransmitterTile().setForceUpdate()));
        IAuraContainer endpoint = cache.getCapability();
        // Adjacent shared pipes exchange through the topology, never through
        // their externally exposed capability (including separate networks).
        return endpoint instanceof MagicPipeAuraTransmitter.SidedView
                ? null : endpoint;
    }

    @Override
    public boolean isEndpoint(Level level, BlockPos pos, Direction side) {
        Direction ownSide = side.getOpposite();
        if (!pos.equals(pipe.getBlockPos().relative(ownSide))) {
            return false;
        }
        IAuraContainer target = endpoint(ownSide);
        IAuraType type = localType();
        return target != null && type != null
                && target.isAcceptableType(type);
    }

    @Override
    public void tickServer() {
        if (!enabled(null)) {
            return;
        }
        int pullBudget = transferRate();
        int pushBudget = transferRate();
        for (Direction side : Direction.values()) {
            ConnectionType mode = pipe.getConnectionTypeRaw(side);
            if (mode == ConnectionType.NONE) {
                continue;
            }
            IAuraContainer target = endpoint(side);
            IAuraType type = currentType();
            if (target == null || type == null
                    || !target.isAcceptableType(type)) {
                continue;
            }
            IAuraContainer own = buffer();
            if (mode == ConnectionType.PULL && pullBudget > 0) {
                int request = bounded(pullBudget,
                        own.storeAura(pullBudget, true));
                int offered = bounded(request,
                        target.drainAura(request, true));
                if (offered > 0) {
                    int extracted = bounded(offered,
                            target.drainAura(offered, false));
                    int accepted = own.storeAura(extracted, false);
                    if (accepted < extracted) {
                        target.storeAura(extracted - accepted, false);
                    }
                    pullBudget -= extracted;
                }
            } else if ((mode == ConnectionType.NORMAL
                    || mode == ConnectionType.PUSH) && pushBudget > 0) {
                int request = own.drainAura(pushBudget, true);
                int accepted = bounded(request,
                        target.storeAura(request, false));
                own.drainAura(accepted, false);
                pushBudget -= accepted;
            }
        }
    }

    @Override
    public boolean isEmpty() {
        return localStored() <= 0 && (channel() == null
                || channel().stored() == 0);
    }

    @Override
    public void sideChanged(Direction side) {
        // Existing views consult live modes, so a retained capability cannot
        // bypass a later NONE/PULL/PUSH or redstone change.
        pipe.getTransmitterTile().invalidateCapabilities();
    }

    @Override
    public void redstoneChanged(boolean powered) {
        pipe.getTransmitterTile().invalidateCapabilities();
    }

    void setSaveShare(int amount, @Nullable ResourceLocation type) {
        if (localStored() > 0) {
            return;
        }
        ResourceLocation newType = amount > 0 ? type : null;
        if (saveShare != amount || !Objects.equals(saveType, newType)) {
            saveShare = amount;
            saveType = newType;
            pipe.getTransmitterTile().markForSave();
        }
    }

    @Override
    public void takeShare() {
        MagicPipeAuraChannel aura = channel();
        if (localStored() > 0 || aura == null) {
            return;
        }
        // Aura can change after Source has already validated its shares in
        // this tick. Distribute the current Aura amount independently.
        aura.updateSaveShares();
        ResourceLocation type = saveType;
        int taken = aura.drainAura(saveShare, false);
        localType = type;
        local.setAura(taken);
    }

    @Override
    public void read(HolderLookup.Provider provider, CompoundTag tag) {
        long amount = tag.contains(SHARE, Tag.TAG_ANY_NUMERIC)
                ? tag.getLong(SHARE)
                : tag.contains("natural_aura_save_share", Tag.TAG_ANY_NUMERIC)
                ? tag.getLong("natural_aura_save_share")
                : tag.getLong("natural_aura_buffer");
        saveShare = (int) Math.max(0L, Math.min(tierCapacity(), amount));
        String savedType = tag.contains(TYPE, Tag.TAG_STRING)
                ? tag.getString(TYPE)
                : tag.getString("natural_aura_save_share_type");
        saveType = NaturalAuraPipeSemantics.parseOptionalId(savedType);
        if (!savedType.isBlank() && saveType == null) {
            // Malformed or unknown saved types stay quarantined; they must
            // not become the local dimension's Aura during a later reload.
            saveType = ResourceLocation.fromNamespaceAndPath(
                    "mekanism_magic", "invalid_aura_type");
        }
        localType = saveType;
        if (saveShare > 0 && localType == null) {
            IAuraType type = localType();
            localType = type == null ? null : type.getName();
            saveType = localType;
        }
        local.setAuraSilently(saveShare);
    }

    @Override
    public void write(HolderLookup.Provider provider, CompoundTag tag) {
        MagicPipeAuraChannel aura = channel();
        if (aura != null) {
            aura.updateSaveShares();
        }
        int amount = localStored() > 0 ? localStored()
                : aura == null ? 0 : saveShare;
        ResourceLocation type = localStored() > 0 ? localType : saveType;
        tag.remove("natural_aura_save_share");
        tag.remove("natural_aura_save_share_type");
        tag.remove("natural_aura_buffer");
        tag.putInt(SHARE, amount);
        if (amount > 0 && type != null) {
            tag.putString(TYPE, type.toString());
        } else {
            tag.remove(TYPE);
        }
    }

    @Override
    public CompoundTag writeUpgradeData() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(SHARE, localStored());
        if (localType != null && localStored() > 0) {
            tag.putString(TYPE, localType.toString());
        }
        return tag;
    }

    @Override
    public void parseUpgradeData(CompoundTag tag) {
        read(null, tag);
    }

    @Override
    public void writeUpdateTag(CompoundTag tag) {
        IAuraContainer buffer = buffer();
        tag.putInt("magic_pipe_aura", buffer.getStoredAura());
        tag.putInt("magic_pipe_aura_color", buffer.getAuraColor());
        MagicPipeAuraChannel aura = channel();
        tag.putFloat("magic_pipe_aura_scale",
                localStored() > 0 || aura == null
                        ? localStored() / (float) tierCapacity() : aura.scale());
    }

    @Override
    public void readUpdateTag(CompoundTag tag) {
        if (tag.contains("magic_pipe_aura_scale", Tag.TAG_ANY_NUMERIC)) {
            float scale = tag.getFloat("magic_pipe_aura_scale");
            clientScale = Float.isFinite(scale)
                    ? Math.max(0F, Math.min(1F, scale)) : 0F;
        }
        if (tag.contains("magic_pipe_aura_color", Tag.TAG_ANY_NUMERIC)) {
            clientColor = tag.getInt("magic_pipe_aura_color");
        }
    }

    @Override
    public float renderScale() {
        return clientScale;
    }

    @Override
    public int renderColor() {
        return clientColor;
    }

    @Override
    public MagicPipeResourceHooks.ResourceValidator createValidator() {
        return new MagicPipeResourceHooks.ResourceValidator() {
            private final Set<Object> visited = Collections.newSetFromMap(
                    new IdentityHashMap<>());
            private IAuraType type = localType();
            private long stored;

            private boolean accept(Object identity, IAuraType candidate,
                                   long amount) {
                if (visited.contains(identity)) {
                    return true;
                }
                if (amount > 0 && (candidate == null || type != null
                        && !NaturalAuraPipeSemantics.compatible(type, candidate)
                        || amount > Long.MAX_VALUE - stored)) {
                    return false;
                }
                visited.add(identity);
                if (amount > 0) {
                    type = candidate;
                    stored += amount;
                }
                return true;
            }

            @Override
            public boolean canAcceptPipe(MagicSourceTransmitter other) {
                MagicPipeAuraTransmitter aura =
                        MagicPipeAuraChannel.extension(other);
                return !aura.hasForeignShare() && accept(other,
                        aura.effectiveType(), aura.localStored());
            }

            @Override
            public boolean canAcceptNetwork(MagicSourceNetwork other) {
                MagicPipeAuraChannel aura = MagicPipeResourceHooks.networkExtension(
                        other, MagicPipeAuraChannel.class);
                if (aura == null) return stored == 0;
                return accept(other, aura.type(), aura.stored());
            }
        };
    }

    private void localChanged() {
        if (localStored() == 0) {
            localType = null;
        } else if (localType == null) {
            IAuraType type = localType();
            localType = type == null ? null : type.getName();
        }
        pipe.onContentsChanged();
    }

    private static int bounded(int requested, int reported) {
        return Math.max(0, Math.min(requested, reported));
    }

    private final class SidedView implements IAuraContainer {
        private final @Nullable Direction side;

        private SidedView(@Nullable Direction side) { this.side = side; }

        private boolean canReceive() {
            return enabled(side) && (side == null
                    || pipe.getConnectionTypeRaw(side) != ConnectionType.PUSH);
        }

        private boolean canExtract() {
            return enabled(side) && (side == null
                    || pipe.getConnectionTypeRaw(side) != ConnectionType.PULL);
        }

        @Override
        public int storeAura(int amount, boolean simulate) {
            return canReceive() ? buffer().storeAura(amount, simulate) : 0;
        }

        @Override
        public int drainAura(int amount, boolean simulate) {
            return canExtract() ? buffer().drainAura(amount, simulate) : 0;
        }

        @Override
        public int getStoredAura() { return buffer().getStoredAura(); }

        @Override
        public int getMaxAura() { return buffer().getMaxAura(); }

        @Override
        public int getAuraColor() { return buffer().getAuraColor(); }

        @Override
        public boolean isAcceptableType(IAuraType type) {
            return enabled(side) && buffer().isAcceptableType(type);
        }
    }
}
