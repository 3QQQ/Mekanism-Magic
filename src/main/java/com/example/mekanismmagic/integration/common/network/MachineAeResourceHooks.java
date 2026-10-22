package com.example.mekanismmagic.integration.common.network;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Dependency-free, per-machine controls and display bridge for optional AE supply. */
public final class MachineAeResourceHooks {
    public static final int TOGGLE_BUTTON_ID = 9351;
    public static final int OUTPUT_MASTER_BUTTON_ID = 9353;
    public static final int ENERGY = 1;
    public static final int SOURCE = 2;
    public static final int AURA = 4;
    public static final int ESSENCE_AUREAL = 8;
    public static final int ESSENCE_SOULS = 16;
    public static final int ESSENCE_BLOOD = 32;
    public static final int ESSENCE_EXPERIENCE = 64;
    public static final int ESSENCE_MASK = ESSENCE_AUREAL | ESSENCE_SOULS
            | ESSENCE_BLOOD | ESSENCE_EXPERIENCE;
    private static final int ALL_RESOURCE_MASK = ENERGY | SOURCE | AURA | ESSENCE_MASK;
    public static final int ALL_INPUT_MASK = ALL_RESOURCE_MASK;
    public static final int OUTPUT_ITEMS = 128;
    public static final int OUTPUT_FLUIDS = 256;
    public static final int ALL_OUTPUT_MASK = SOURCE | AURA | ESSENCE_MASK | OUTPUT_ITEMS | OUTPUT_FLUIDS;
    private static final Map<Integer, Integer> INPUT_BUTTONS = Map.of(
            9360, ENERGY, 9361, SOURCE, 9362, AURA, 9363, ESSENCE_AUREAL,
            9364, ESSENCE_SOULS, 9365, ESSENCE_BLOOD, 9366, ESSENCE_EXPERIENCE);
    private static final Map<Integer, Integer> OUTPUT_BUTTONS = Map.of(
            9370, SOURCE, 9371, AURA, 9372, ESSENCE_AUREAL, 9373, ESSENCE_SOULS,
            9374, ESSENCE_BLOOD, 9375, ESSENCE_EXPERIENCE, 9376, OUTPUT_ITEMS, 9377, OUTPUT_FLUIDS);
    private static final int[] ESSENCE_BITS = {ESSENCE_AUREAL, ESSENCE_SOULS,
            ESSENCE_BLOOD, ESSENCE_EXPERIENCE};
    private static final String ENABLED_TAG = "magic_native_ae_supply_enabled";
    private static final String INPUT_MASK_TAG = "magic_native_ae_input_mask";
    private static final String OUTPUT_MASK_TAG = "magic_native_ae_output_mask";
    private static final String OUTPUT_ENABLED_TAG = "magic_native_ae_output_enabled";
    private static final List<String> OPAQUE_AE_TAGS = List.of(
            "magic_native_ae_pending_refunds",
            "magic_native_ae_pending_exports",
            "mekanism_magic_resource_ae",
            "mekanism_magic_direct_ae",
            "mekanism_magic_imbuement_ae");
    private static final long RECENT_TRANSFER_TICKS = 40;
    private static final Map<BlockEntity, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Handler handler;

    private MachineAeResourceHooks() {
    }

    public static void register(Handler candidate) {
        if (candidate != null) handler = candidate;
    }

    public static boolean isEnabled(BlockEntity tile) {
        return tile != null && state(tile).enabled;
    }

    public static boolean isInputEnabled(BlockEntity tile, int kind) {
        if (!(tile instanceof IMekanismMagicAutomation)
                || Integer.bitCount(kind) != 1 || (kind & ALL_INPUT_MASK) == 0) return false;
        State state = state(tile);
        return state.enabled && (state.inputMask & kind) != 0;
    }

    public static boolean isOutputEnabled(BlockEntity tile, int kind) {
        if (!(tile instanceof IMekanismMagicAutomation)
                || Integer.bitCount(kind) != 1 || (kind & ALL_OUTPUT_MASK) == 0) return false;
        State state = state(tile);
        return state.outputEnabled && (state.outputMask & kind) != 0;
    }

    public static int inputSupportMask(BlockEntity tile) {
        if (!(tile instanceof IMekanismMagicAutomation)) return 0;
        if (tile.getLevel() != null && tile.getLevel().isClientSide()) {
            return state(tile).clientInputAvailableMask;
        }
        Handler current = handler;
        return current == null ? 0 : current.inputSupportMask(tile) & ALL_INPUT_MASK;
    }

    public static int outputSupportMask(BlockEntity tile) {
        if (!(tile instanceof IMekanismMagicAutomation)) return 0;
        if (tile.getLevel() != null && tile.getLevel().isClientSide()) {
            return state(tile).clientOutputAvailableMask;
        }
        Handler current = handler;
        return current == null ? 0 : current.outputSupportMask(tile) & ALL_OUTPUT_MASK;
    }

    public static ControlSnapshot controls(BlockEntity tile) {
        if (!(tile instanceof IMekanismMagicAutomation)) return ControlSnapshot.UNSUPPORTED;
        State state = state(tile);
        Handler current = handler;
        boolean supported = tile.getLevel() != null && (tile.getLevel().isClientSide()
                ? (state.clientPacked & 1) != 0
                : current != null && current.connection(tile).supported());
        return new ControlSnapshot(supported, state.enabled, state.outputEnabled,
                inputSupportMask(tile), outputSupportMask(tile), state.inputMask, state.outputMask);
    }

    /** Whitelisted menu IDs only: never interpret arbitrary client-controlled shift counts. */
    public static boolean isConfigurationButton(int buttonId) {
        return buttonId == TOGGLE_BUTTON_ID || buttonId == OUTPUT_MASTER_BUTTON_ID
                || INPUT_BUTTONS.containsKey(buttonId) || OUTPUT_BUTTONS.containsKey(buttonId);
    }

    public static int buttonIdForInput(int kind) {
        return INPUT_BUTTONS.entrySet().stream().filter(entry -> entry.getValue() == kind)
                .mapToInt(Map.Entry::getKey).findFirst().orElse(-1);
    }

    public static int buttonIdForOutput(int kind) {
        return OUTPUT_BUTTONS.entrySet().stream().filter(entry -> entry.getValue() == kind)
                .mapToInt(Map.Entry::getKey).findFirst().orElse(-1);
    }

    /** Called only by the server menu action after its player/menu checks. */
    public static boolean toggleSupply(BlockEntity tile) {
        return toggleFunction(tile, TOGGLE_BUTTON_ID);
    }

    /** Called only after authenticated menu access; does not touch nodes, debts or crafting jobs. */
    public static boolean toggleFunction(BlockEntity tile, int buttonId) {
        if (!isConfigurationButton(buttonId)
                || !(tile instanceof IMekanismMagicAutomation)
                || tile.getLevel() == null || tile.getLevel().isClientSide()
                || tile.isRemoved() || tile.getLevel().getBlockEntity(tile.getBlockPos()) != tile
                || handler == null) return false;
        ControlSnapshot controls = controls(tile);
        if (!controls.supported()) return false;
        State state = state(tile);
        if (buttonId == TOGGLE_BUTTON_ID) {
            // The legacy supply switch was writable on output-only machines
            // too. Keep that saved preference even though it enables no input.
            state.enabled = !state.enabled;
        } else if (buttonId == OUTPUT_MASTER_BUTTON_ID) {
            if (controls.outputAvailableMask() == 0) return false;
            state.outputEnabled = !state.outputEnabled;
        } else if (INPUT_BUTTONS.containsKey(buttonId)) {
            int bit = INPUT_BUTTONS.get(buttonId);
            if ((controls.inputAvailableMask() & bit) == 0) return false;
            state.inputMask ^= bit;
        } else {
            int bit = OUTPUT_BUTTONS.get(buttonId);
            if ((controls.outputAvailableMask() & bit) == 0) return false;
            state.outputMask ^= bit;
        }
        tile.setChanged();
        if (tile instanceof TileEntityMekanism machine) {
            machine.sendUpdatePacket();
        }
        return true;
    }

    public static Snapshot snapshot(BlockEntity tile) {
        if (!(tile instanceof IMekanismMagicAutomation)
                || tile.getLevel() == null) return Snapshot.UNSUPPORTED;
        State state = state(tile);
        if (tile.getLevel().isClientSide()) {
            return new Snapshot((state.clientPacked & 1) != 0,
                    (state.clientPacked & 2) != 0,
                    (state.clientPacked & 4) != 0,
                    (state.clientPacked & 8) != 0,
                    (state.clientPacked >>> 4) & ALL_RESOURCE_MASK,
                    (state.clientPacked >>> 12) & ALL_RESOURCE_MASK,
                    state.clientLastEnergyJ, state.clientLastSource,
                    state.clientLastAura, state.clientLastEssence[0],
                    state.clientLastEssence[1], state.clientLastEssence[2],
                    state.clientLastEssence[3], state.enabled
                    ? state.inputMask & state.clientInputAvailableMask : 0);
        }
        Handler current = handler;
        if (current == null) return Snapshot.UNSUPPORTED;
        Connection connection = current.connection(tile);
        long gameTime = tile.getLevel().getGameTime();
        int transferred = 0;
        if (recent(gameTime, state.lastEnergyTime)) transferred |= ENERGY;
        if (recent(gameTime, state.lastSourceTime)) transferred |= SOURCE;
        if (recent(gameTime, state.lastAuraTime)) transferred |= AURA;
        for (int channel = 0; channel < ESSENCE_BITS.length; channel++) {
            if (recent(gameTime, state.lastEssenceTime[channel])) {
                transferred |= ESSENCE_BITS[channel];
            }
        }
        return new Snapshot(connection.supported(), state.enabled,
                connection.connected(), connection.active(),
                connection.resourceMask(), transferred,
                state.lastEnergyJ, state.lastSource, state.lastAura,
                state.lastEssence[0], state.lastEssence[1],
                state.lastEssence[2], state.lastEssence[3], state.enabled
                ? state.inputMask & inputSupportMask(tile) : 0);
    }

    private static boolean recent(long now, long then) {
        return then != Long.MIN_VALUE && now >= then
                && now - then <= RECENT_TRANSFER_TICKS;
    }

    public static void recordTransfer(BlockEntity tile, int kind,
                                      long amount) {
        if (tile == null || tile.getLevel() == null
                || tile.getLevel().isClientSide() || amount <= 0) return;
        State state = state(tile);
        long gameTime = tile.getLevel().getGameTime();
        if (kind == ENERGY) {
            state.lastEnergyJ = amount;
            state.lastEnergyTime = gameTime;
        } else if (kind == SOURCE) {
            state.lastSource = (int) Math.min(Integer.MAX_VALUE, amount);
            state.lastSourceTime = gameTime;
        } else if (kind == AURA) {
            state.lastAura = (int) Math.min(Integer.MAX_VALUE, amount);
            state.lastAuraTime = gameTime;
        } else {
            for (int channel = 0; channel < ESSENCE_BITS.length; channel++) {
                if (kind == ESSENCE_BITS[channel]) {
                    state.lastEssence[channel] = (int) Math.min(Integer.MAX_VALUE, amount);
                    state.lastEssenceTime[channel] = gameTime;
                    break;
                }
            }
        }
    }

    public static void tick(BlockEntity tile) {
        if (tile instanceof IMekanismMagicAutomation
                && tile.getLevel() != null
                && !tile.getLevel().isClientSide()
                && handler != null) {
            handler.tick(tile);
        }
    }

    public static void onLoad(BlockEntity tile) {
        if (tile instanceof IMekanismMagicAutomation && handler != null) {
            handler.onLoad(tile);
        }
    }

    public static void onRemoved(BlockEntity tile) {
        if (tile instanceof IMekanismMagicAutomation && handler != null) {
            handler.onRemoved(tile);
        }
    }

    public static void onRevived(BlockEntity tile) {
        if (tile instanceof IMekanismMagicAutomation && handler != null) {
            handler.onRevived(tile);
        }
    }

    /** Fast ownership handoff before/after an optional upgrade creates its node. */
    public static void refreshOwnership(BlockEntity tile) {
        if (tile instanceof IMekanismMagicAutomation && handler != null) {
            handler.onOwnershipChanged(tile);
        }
    }

    public static void beforeExternalNodeCreate(BlockEntity tile,
                                                 boolean active) {
        if (tile instanceof IMekanismMagicAutomation && handler != null) {
            handler.beforeExternalNodeCreate(tile, active);
        }
    }

    public static void afterExternalNodeDestroy(BlockEntity tile) {
        if (tile instanceof IMekanismMagicAutomation && handler != null) {
            handler.afterExternalNodeDestroy(tile);
        }
    }

    public static void onChunkUnloaded(BlockEntity tile) {
        if (tile instanceof IMekanismMagicAutomation && handler != null) {
            handler.onChunkUnloaded(tile);
        }
    }

    public static void onBlockRemoved(BlockEntity tile) {
        if (tile instanceof IMekanismMagicAutomation && handler != null) {
            handler.onBlockRemoved(tile);
        }
        // Keep the weak-key state through BlockDropsEvent#saveToItem so a
        // disabled supply switch survives breaking and replacing the machine.
    }

    public static void save(BlockEntity tile, CompoundTag tag,
                            HolderLookup.Provider registries) {
        if (!(tile instanceof IMekanismMagicAutomation)) return;
        State state = state(tile);
        tag.putBoolean(ENABLED_TAG, state.enabled);
        tag.putInt(INPUT_MASK_TAG, state.inputMask);
        tag.putInt(OUTPUT_MASK_TAG, state.outputMask);
        tag.putBoolean(OUTPUT_ENABLED_TAG, state.outputEnabled);
        if (handler != null) {
            handler.save(tile, tag, registries);
        } else {
            CompoundTag opaque = state(tile).opaqueAeTags;
            if (opaque != null) {
                for (String key : OPAQUE_AE_TAGS) {
                    if (opaque.contains(key)) {
                        tag.put(key, opaque.get(key).copy());
                    }
                }
            }
        }
    }

    public static void load(BlockEntity tile, CompoundTag tag,
                            HolderLookup.Provider registries) {
        if (!(tile instanceof IMekanismMagicAutomation)) return;
        State state = state(tile);
        // Old false means input supply off only. New feature selections and
        // output master default on, even when AE is absent for this launch.
        state.enabled = !tag.contains(ENABLED_TAG) || tag.getBoolean(ENABLED_TAG);
        state.inputMask = tag.contains(INPUT_MASK_TAG)
                ? tag.getInt(INPUT_MASK_TAG) & ALL_INPUT_MASK : ALL_INPUT_MASK;
        state.outputMask = tag.contains(OUTPUT_MASK_TAG)
                ? tag.getInt(OUTPUT_MASK_TAG) & ALL_OUTPUT_MASK : ALL_OUTPUT_MASK;
        state.outputEnabled = !tag.contains(OUTPUT_ENABLED_TAG)
                || tag.getBoolean(OUTPUT_ENABLED_TAG);
        if (handler != null) {
            state(tile).opaqueAeTags = null;
            handler.load(tile, tag, registries);
        } else {
            CompoundTag opaque = new CompoundTag();
            for (String key : OPAQUE_AE_TAGS) {
                if (tag.contains(key)) {
                    opaque.put(key, tag.get(key).copy());
                }
            }
            state(tile).opaqueAeTags = opaque.isEmpty() ? null : opaque;
        }
    }

    public static void addContainerTrackers(BlockEntity tile,
                                            MekanismContainer container) {
        if (!(tile instanceof IMekanismMagicAutomation)) return;
        container.track(SyncableInt.create(
                () -> snapshot(tile).packedFlags(),
                value -> state(tile).clientPacked = value));
        container.track(SyncableInt.create(
                () -> (state(tile).enabled ? 1 : 0) | (state(tile).outputEnabled ? 2 : 0),
                value -> {
                    state(tile).enabled = (value & 1) != 0;
                    state(tile).outputEnabled = (value & 2) != 0;
                }));
        container.track(SyncableInt.create(() -> state(tile).inputMask,
                value -> state(tile).inputMask = value & ALL_INPUT_MASK));
        container.track(SyncableInt.create(() -> state(tile).outputMask,
                value -> state(tile).outputMask = value & ALL_OUTPUT_MASK));
        container.track(SyncableInt.create(() -> inputSupportMask(tile),
                value -> state(tile).clientInputAvailableMask = value & ALL_INPUT_MASK));
        container.track(SyncableInt.create(() -> outputSupportMask(tile),
                value -> state(tile).clientOutputAvailableMask = value & ALL_OUTPUT_MASK));
        container.track(SyncableLong.create(
                () -> snapshot(tile).lastEnergyJ(),
                value -> state(tile).clientLastEnergyJ = value));
        container.track(SyncableInt.create(
                () -> snapshot(tile).lastSource(),
                value -> state(tile).clientLastSource = value));
        container.track(SyncableInt.create(
                () -> snapshot(tile).lastAura(),
                value -> state(tile).clientLastAura = value));
        for (int channel = 0; channel < ESSENCE_BITS.length; channel++) {
            final int fixed = channel;
            container.track(SyncableInt.create(
                    () -> snapshot(tile).lastEssence(fixed),
                    value -> state(tile).clientLastEssence[fixed] = Math.max(0, value)));
        }
    }

    private static State state(BlockEntity tile) {
        return STATES.computeIfAbsent(tile, ignored -> new State());
    }

    private static final class State {
        private boolean enabled = true;
        private boolean outputEnabled = true;
        private int inputMask = ALL_INPUT_MASK;
        private int outputMask = ALL_OUTPUT_MASK;
        private int clientInputAvailableMask;
        private int clientOutputAvailableMask;
        private long lastEnergyJ;
        private int lastSource;
        private int lastAura;
        private long lastEnergyTime = Long.MIN_VALUE;
        private long lastSourceTime = Long.MIN_VALUE;
        private long lastAuraTime = Long.MIN_VALUE;
        private final int[] lastEssence = new int[4];
        private final long[] lastEssenceTime = new long[4];
        private int clientPacked;
        private long clientLastEnergyJ;
        private int clientLastSource;
        private int clientLastAura;
        private final int[] clientLastEssence = new int[4];
        private CompoundTag opaqueAeTags;

        private State() {
            Arrays.fill(lastEssenceTime, Long.MIN_VALUE);
        }
    }

    public record Snapshot(boolean supported, boolean enabled,
                           boolean connected, boolean active,
                           int resourceMask, int transferMask,
                           long lastEnergyJ, int lastSource, int lastAura,
                           int lastEssenceAureal, int lastEssenceSouls,
                           int lastEssenceBlood, int lastEssenceExperience,
                           int enabledInputMask) {
        /** Existing four-essence call sites retain their constructor descriptor. */
        public Snapshot(boolean supported, boolean enabled,
                        boolean connected, boolean active,
                        int resourceMask, int transferMask,
                        long lastEnergyJ, int lastSource, int lastAura,
                        int lastEssenceAureal, int lastEssenceSouls,
                        int lastEssenceBlood, int lastEssenceExperience) {
            this(supported, enabled, connected, active, resourceMask, transferMask,
                    lastEnergyJ, lastSource, lastAura, lastEssenceAureal,
                    lastEssenceSouls, lastEssenceBlood, lastEssenceExperience,
                    enabled ? resourceMask & ALL_INPUT_MASK : 0);
        }

        public Snapshot {
            enabledInputMask = supported && enabled ? enabledInputMask & ALL_INPUT_MASK : 0;
        }
        /** Existing three-resource call sites remain binary/source compatible. */
        public Snapshot(boolean supported, boolean enabled,
                        boolean connected, boolean active,
                        int resourceMask, int transferMask,
                        long lastEnergyJ, int lastSource, int lastAura) {
            this(supported, enabled, connected, active, resourceMask,
                    transferMask, lastEnergyJ, lastSource, lastAura,
                    0, 0, 0, 0);
        }

        public int lastEssence(int channel) {
            return switch (channel) {
                case 0 -> lastEssenceAureal;
                case 1 -> lastEssenceSouls;
                case 2 -> lastEssenceBlood;
                case 3 -> lastEssenceExperience;
                default -> 0;
            };
        }
        public static final Snapshot UNSUPPORTED = new Snapshot(false,
                false, false, false, 0, 0, 0, 0, 0);

        /** Jade compatibility: disabled machines advertise no native supply. */
        public int enabledMask() {
            return enabledInputMask;
        }

        public int packedFlags() {
            return (supported ? 1 : 0) | (enabled ? 2 : 0)
                    | (connected ? 4 : 0) | (active ? 8 : 0)
                    | ((resourceMask & ALL_RESOURCE_MASK) << 4)
                    | ((transferMask & ALL_RESOURCE_MASK) << 12);
        }
    }

    public record Connection(boolean supported, boolean connected,
                             boolean active, int resourceMask) {
        public static final Connection UNSUPPORTED =
                new Connection(false, false, false, 0);
    }

    public record ControlSnapshot(boolean supported, boolean inputMaster,
                                  boolean outputMaster, int inputAvailableMask,
                                  int outputAvailableMask, int configuredInputMask,
                                  int configuredOutputMask) {
        public static final ControlSnapshot UNSUPPORTED = new ControlSnapshot(
                false, false, false, 0, 0, ALL_INPUT_MASK, ALL_OUTPUT_MASK);

        public int enabledInputMask() {
            return inputMaster ? configuredInputMask & inputAvailableMask : 0;
        }

        public int enabledOutputMask() {
            return outputMaster ? configuredOutputMask & outputAvailableMask : 0;
        }
    }

    public interface Handler {
        Connection connection(BlockEntity tile);

        default int inputSupportMask(BlockEntity tile) { return 0; }

        default int outputSupportMask(BlockEntity tile) { return 0; }

        void tick(BlockEntity tile);

        default void onLoad(BlockEntity tile) { }

        default void onRemoved(BlockEntity tile) { }

        default void onRevived(BlockEntity tile) { }

        default void onChunkUnloaded(BlockEntity tile) { }

        default void onBlockRemoved(BlockEntity tile) { }

        default void onOwnershipChanged(BlockEntity tile) { }

        default void beforeExternalNodeCreate(BlockEntity tile,
                                              boolean active) { }

        default void afterExternalNodeDestroy(BlockEntity tile) { }

        default void save(BlockEntity tile, CompoundTag tag,
                          HolderLookup.Provider registries) { }

        default void load(BlockEntity tile, CompoundTag tag,
                          HolderLookup.Provider registries) { }
    }
}
