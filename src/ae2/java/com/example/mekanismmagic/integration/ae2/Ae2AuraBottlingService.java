package com.example.mekanismmagic.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import de.ellpeck.naturesaura.items.ItemAuraBottle;
import de.ellpeck.naturesaura.items.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Exact AE-stock conversion: one native empty bottle + 20k typed Aura. */
public final class Ae2AuraBottlingService {
    public static final long AURA_PER_BOTTLE = 20_000L;
    public static final int MAX_BOTTLES_PER_REQUEST = 64;
    private Ae2AuraBottlingService() { }

    private static AEItemKey emptyBottle() {
        return ModItems.BOTTLE_TWO_THE_REBOTTLING == null ? null
                : AEItemKey.of(new ItemStack(
                        ModItems.BOTTLE_TWO_THE_REBOTTLING));
    }

    public enum Status { SUCCESS, INVALID_TYPE, UNAVAILABLE, PENDING }

    public record Result(int bottled, Status status) { }

    /** Caller authenticates the terminal, player, grid, and anchor position. */
    public static Result bottleFromNetwork(ServerLevel level,
                                           BlockPos anchorPos,
                                           IGrid grid,
                                           IActionSource actor,
                                           AEKey selectedAuraKey,
                                           int requestedCount) {
        IAuraType type = typeForKey(selectedAuraKey);
        if (type == null) return new Result(0, Status.INVALID_TYPE);
        AEItemKey emptyBottle = emptyBottle();
        if (level == null || anchorPos == null || grid == null || actor == null
                || requestedCount <= 0 || emptyBottle == null) {
            return new Result(0, Status.UNAVAILABLE);
        }
        Ledger ledger = Ledger.get(level);
        long anchor = anchorPos.asLong();
        UUID owner = ownerOf(actor);
        if (!ledger.begin(anchor)) return new Result(0, Status.PENDING);
        try {
            if (!flushOwned(ledger, anchor, owner, grid, actor)) {
                return new Result(0, Status.PENDING);
            }
            int count = Math.min(requestedCount, MAX_BOTTLES_PER_REQUEST);
            AEItemKey output = filledBottle(type);
            if (output == null) return new Result(0, Status.UNAVAILABLE);
            MEStorage inventory = grid.getStorageService().getInventory();
            var energy = grid.getEnergyService();
            long possibleOutput = StorageHelper.poweredInsert(energy,
                    inventory, output, count, actor, Actionable.SIMULATE);
            long possibleBottles = StorageHelper.poweredExtraction(energy,
                    inventory, emptyBottle, count, actor,
                    Actionable.SIMULATE);
            long possibleAura = StorageHelper.poweredExtraction(energy,
                    inventory, selectedAuraKey, count * AURA_PER_BOTTLE,
                    actor, Actionable.SIMULATE);
            int planned = (int) Math.min(count, Math.min(
                    Math.max(0L, possibleOutput), Math.min(
                            Math.max(0L, possibleBottles),
                            Math.max(0L, possibleAura) / AURA_PER_BOTTLE)));
            if (planned <= 0) return new Result(0, Status.UNAVAILABLE);

            long bottles = Math.max(0L, Math.min(planned,
                    StorageHelper.poweredExtraction(energy, inventory,
                            emptyBottle, planned, actor,
                            Actionable.MODULATE)));
            if (bottles <= 0) return new Result(0, Status.UNAVAILABLE);
            // Record a refundable liability before the second withdrawal.
            ledger.add(anchor, type.getName(), owner,
                    0L, bottles, 0L);
            long aura = Math.max(0L, Math.min(
                    bottles * AURA_PER_BOTTLE,
                    StorageHelper.poweredExtraction(energy, inventory,
                            selectedAuraKey, bottles * AURA_PER_BOTTLE,
                            actor, Actionable.MODULATE)));
            ledger.add(anchor, type.getName(), owner,
                    aura, 0L, 0L);
            int paid = (int) Math.min(bottles, aura / AURA_PER_BOTTLE);
            // Convert the paid share to finished output; leave any excess
            // input as a durable refund debt if AE storage became full.
            ledger.add(anchor, type.getName(), owner,
                    -paid * AURA_PER_BOTTLE, -paid, paid);
            long inserted = insertFilled(grid, actor, output, paid);
            ledger.add(anchor, type.getName(), owner,
                    0L, 0L, -inserted);
            boolean clear = flushOwned(ledger, anchor, owner,
                    grid, actor);
            return new Result((int) inserted,
                    clear ? inserted > 0L ? Status.SUCCESS
                            : Status.UNAVAILABLE : Status.PENDING);
        } finally {
            ledger.end(anchor);
        }
    }

    /** AE's crafting CPU has already debited the delivered ingredients. */
    static boolean acceptCraftingInputs(ServerLevel level,
                                        BlockPos anchorPos,
                                        IGrid grid,
                                        IActionSource actor,
                                        AEKey selectedAuraKey,
                                        KeyCounter[] inputItems) {
        IAuraType type = typeForKey(selectedAuraKey);
        if (type == null || level == null || anchorPos == null || grid == null
                || actor == null || !exactCraftingInputs(
                        selectedAuraKey, inputItems)) return false;
        Ledger ledger = Ledger.get(level);
        long anchor = anchorPos.asLong();
        UUID owner = ownerOf(actor);
        if (!ledger.begin(anchor)) return false;
        try {
            if (!flushOwned(ledger, anchor, owner, grid, actor))
                return false;
            AEItemKey output = filledBottle(type);
            if (output == null) return false;
            // A receipt is accepted only after the paid output is recorded.
            // Do not insert it synchronously here: AE registers the CPU's
            // expected output only after pushPattern returns true. Delivering
            // now would bypass that CPU and strand its crafting job.
            ledger.add(anchor, type.getName(), owner,
                    0L, 0L, 1L);
            return true;
        } finally {
            ledger.end(anchor);
        }
    }

    static boolean flushPending(ServerLevel level, BlockPos anchorPos,
                                IGrid grid, IActionSource actor) {
        if (level == null || anchorPos == null || grid == null || actor == null)
            return false;
        Ledger ledger = Ledger.get(level);
        long anchor = anchorPos.asLong();
        if (!ledger.begin(anchor)) return false;
        try {
            return flushOwned(ledger, anchor, ownerOf(actor),
                    grid, actor);
        } finally {
            ledger.end(anchor);
        }
    }

    private static boolean exactCraftingInputs(AEKey auraKey,
                                               KeyCounter[] counters) {
        AEItemKey emptyBottle = emptyBottle();
        if (counters == null || emptyBottle == null) return false;
        long bottles = 0L;
        long aura = 0L;
        for (KeyCounter counter : counters) {
            if (counter == null) return false;
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (amount < 0L) return false;
                if (emptyBottle.equals(entry.getKey())) bottles += amount;
                else if (auraKey.equals(entry.getKey())) aura += amount;
                else if (amount > 0L) return false;
                if (bottles > 1L || aura > AURA_PER_BOTTLE) return false;
            }
        }
        return bottles == 1L && aura == AURA_PER_BOTTLE;
    }

    static IAuraType typeForKey(AEKey key) {
        ResourceLocation id = MagicResourceKeys.resourceId(key);
        return id == null ? null : NaturesAuraAPI.AURA_TYPES.get(id);
    }

    private static UUID ownerOf(IActionSource actor) {
        if (actor == null) return null;
        var player = actor.player();
        if (player.isPresent()) return player.get().getUUID();
        return actor.machine().map(host -> host.getActionableNode())
                .map(IGridNode::getOwningPlayerProfileId).orElse(null);
    }

    static AEItemKey filledBottle(IAuraType type) {
        return type == null ? null : AEItemKey.of(ItemAuraBottle.setType(
                new ItemStack(ModItems.AURA_BOTTLE), type));
    }

    private static long insertFilled(IGrid grid, IActionSource actor,
                                     AEItemKey output, long count) {
        return count <= 0L ? 0L : Math.max(0L, Math.min(count,
                StorageHelper.poweredInsert(grid.getEnergyService(),
                        grid.getStorageService().getInventory(), output,
                        count, actor)));
    }

    private static boolean flushOwned(Ledger ledger, long anchor,
                                      UUID owner, IGrid grid,
                                      IActionSource actor) {
        Debt current = ledger.debts.get(anchor);
        if (current != null && current.owner != null
                && !current.owner.equals(owner)) return false;
        if (!flush(ledger, anchor, grid, actor)) return false;
        if (owner == null) return true;
        // A terminal or generator can be replaced: same-owner debts are
        // recoverable through a newly authenticated node in this dimension.
        for (long prior : java.util.List.copyOf(ledger.debts.keySet())) {
            Debt debt = ledger.debts.get(prior);
            if (prior == anchor || debt == null
                    || !owner.equals(debt.owner)) continue;
            if (!ledger.begin(prior)) return false;
            try {
                if (!flush(ledger, prior, grid, actor)) return false;
            } finally {
                ledger.end(prior);
            }
        }
        return true;
    }

    private static boolean flush(Ledger ledger, long anchor,
                                 IGrid grid, IActionSource actor) {
        Debt debt = ledger.debts.get(anchor);
        if (debt == null) return true;
        IAuraType type = NaturesAuraAPI.AURA_TYPES.get(debt.type);
        AEKey aura = MagicResourceKeys.key(debt.type);
        AEItemKey output = filledBottle(type);
        if (type == null || aura == null || output == null) return false;
        MEStorage inventory = grid.getStorageService().getInventory();
        AEItemKey emptyBottle = emptyBottle();
        if (emptyBottle == null) return false;
        if (debt.auraRefund > 0L) {
            long restored = Math.max(0L, Math.min(debt.auraRefund,
                    inventory.insert(aura, debt.auraRefund,
                            Actionable.MODULATE, actor)));
            ledger.add(anchor, debt.type, debt.owner,
                    -restored, 0L, 0L);
        }
        if (debt.emptyRefund > 0L) {
            long restored = Math.max(0L, Math.min(debt.emptyRefund,
                    inventory.insert(emptyBottle, debt.emptyRefund,
                            Actionable.MODULATE, actor)));
            ledger.add(anchor, debt.type, debt.owner,
                    0L, -restored, 0L);
        }
        if (debt.filledOutput > 0L) {
            long inserted = insertFilled(grid, actor, output,
                    debt.filledOutput);
            ledger.add(anchor, debt.type, debt.owner,
                    0L, 0L, -inserted);
        }
        return !ledger.debts.containsKey(anchor);
    }

    /** Dimension-local durable liabilities, attached to the verified node position. */
    private static final class Ledger extends SavedData {
        private static final String DATA_NAME =
                "mekanism_magic_ae_aura_bottling_debts";
        private static final Factory<Ledger> FACTORY = new Factory<>(
                Ledger::new, Ledger::load, null);
        private final Map<Long, Debt> debts = new HashMap<>();
        private final java.util.Set<Long> active = new java.util.HashSet<>();

        static Ledger get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        }

        boolean begin(long anchor) { return active.add(anchor); }

        void end(long anchor) { active.remove(anchor); }

        void add(long anchor, ResourceLocation type, UUID owner, long aura,
                 long empty, long filled) {
            Debt debt = debts.computeIfAbsent(anchor,
                    ignored -> new Debt(type, owner));
            if (!debt.type.equals(type)) {
                throw new IllegalStateException(
                        "Aura bottling debt type changed before repayment");
            }
            debt.auraRefund = Math.addExact(debt.auraRefund, aura);
            debt.emptyRefund = Math.addExact(debt.emptyRefund, empty);
            debt.filledOutput = Math.addExact(debt.filledOutput, filled);
            if (debt.auraRefund < 0L || debt.emptyRefund < 0L
                    || debt.filledOutput < 0L) {
                throw new IllegalStateException("Negative Aura bottling debt");
            }
            if (debt.empty()) debts.remove(anchor);
            setDirty();
        }

        static Ledger load(CompoundTag tag,
                           HolderLookup.Provider registries) {
            Ledger ledger = new Ledger();
            for (Tag element : tag.getList("debts", Tag.TAG_COMPOUND)) {
                CompoundTag saved = (CompoundTag) element;
                ResourceLocation type = ResourceLocation.tryParse(
                        saved.getString("type"));
                if (type == null) continue;
                Debt debt = new Debt(type,
                        saved.hasUUID("owner")
                                ? saved.getUUID("owner") : null);
                debt.auraRefund = Math.max(0L, saved.getLong("aura"));
                debt.emptyRefund = Math.max(0L, saved.getLong("empty"));
                debt.filledOutput = Math.max(0L, saved.getLong("filled"));
                if (!debt.empty()) ledger.debts.put(
                        saved.getLong("anchor"), debt);
            }
            return ledger;
        }

        @Override
        public CompoundTag save(CompoundTag tag,
                                HolderLookup.Provider registries) {
            ListTag saved = new ListTag();
            debts.forEach((anchor, debt) -> {
                CompoundTag entry = new CompoundTag();
                entry.putLong("anchor", anchor);
                entry.putString("type", debt.type.toString());
                if (debt.owner != null) entry.putUUID("owner", debt.owner);
                entry.putLong("aura", debt.auraRefund);
                entry.putLong("empty", debt.emptyRefund);
                entry.putLong("filled", debt.filledOutput);
                saved.add(entry);
            });
            tag.put("debts", saved);
            return tag;
        }
    }

    private static final class Debt {
        private final ResourceLocation type;
        private final UUID owner;
        private long auraRefund;
        private long emptyRefund;
        private long filledOutput;

        private Debt(ResourceLocation type, UUID owner) {
            this.type = type;
            this.owner = owner;
        }

        private boolean empty() {
            return auraRefund == 0L && emptyRefund == 0L
                    && filledOutput == 0L;
        }
    }
}
