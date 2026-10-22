package com.example.mekanismmagic.integration.common.recipe;

import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.ItemInputGroup;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.inventory.IInventorySlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** All-or-nothing item delivery shared by ordinary external pattern providers. */
public final class ExternalItemPatternRouter {
    private ExternalItemPatternRouter() { }

    public static boolean push(IMekanismMagicAutomation host,
                               List<IInventorySlot> exposedSlots,
                               List<PatternStack> inputs,
                               List<PatternStack> outputs) {
        if (host == null || inputs == null || inputs.isEmpty()
                || outputs == null || outputs.isEmpty()
                || exposedSlots == null || exposedSlots.isEmpty()) {
            return false;
        }
        // Separate single-machine batches, including finished outputs still
        // waiting to return. Factories may fill other compatible input lanes.
        if (!host.mekanismMagicGroupParallelItemInputs()
                && (host.mekanismMagicIsBusy()
                || host.mekanismMagicPatternInputs().stream().anyMatch(slot -> !slot.isEmpty())
                || host.mekanismMagicPatternOutputs().stream().anyMatch(slot -> !slot.isEmpty()))) {
            return false;
        }
        Optional<List<ItemInputGroup>> groups = host.mekanismMagicPlanExternalItemInputs(inputs, outputs);
        if (groups.isEmpty() || groups.get().isEmpty()) {
            return false;
        }
        List<PatternStack> groupedInputs = groups.get().stream()
                .flatMap(group -> group.inputs().stream()).toList();
        if (!sameRequests(inputs, groupedInputs)) {
            return false;
        }
        Set<IInventorySlot> allowed = Collections.newSetFromMap(new IdentityHashMap<>());
        allowed.addAll(exposedSlots);
        allowed.removeAll(host.mekanismMagicManualOnlySlots());
        allowed.removeAll(host.mekanismMagicPersistentInputs());
        Set<IInventorySlot> usedPorts = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Insertion> insertions = new ArrayList<>();
        for (ItemInputGroup group : groups.get()) {
            if (group.inputs().isEmpty()) continue;
            if (group.slots().isEmpty() || !allowed.containsAll(group.slots())) return false;
            for (IInventorySlot slot : group.slots()) {
                if (!usedPorts.add(slot)) return false;
            }
            Optional<List<Insertion>> planned = plan(group);
            if (planned.isEmpty()) return false;
            insertions.addAll(planned.get());
        }
        return execute(insertions);
    }

    private static Optional<List<Insertion>> plan(ItemInputGroup group) {
        Map<Integer, Long> requests = new LinkedHashMap<>();
        for (int index = 0; index < group.inputs().size(); index++) {
            PatternStack input = group.inputs().get(index);
            if (input == null || input.stack().isEmpty()
                    || input.amount() <= 0 || input.amount() > Integer.MAX_VALUE) {
                return Optional.empty();
            }
            requests.put(index, input.amount());
        }
        return BoundedRequestRouter.plan(requests, group.slots(),
                (slot, index, planned, requested) -> {
                    long total = planned + requested;
                    if (total > Integer.MAX_VALUE) return 0;
                    ItemStack offered = group.inputs().get(index).stack().copyWithCount((int) total);
                    ItemStack remainder = slot.insertItem(offered, Action.SIMULATE, AutomationType.EXTERNAL);
                    return Math.max(0, total - remainder.getCount() - planned);
                }).map(plan -> plan.stream().map(allocation -> new Insertion(
                        allocation.port(), group.inputs().get(allocation.key()).stack(),
                        Math.toIntExact(allocation.amount()))).toList());
    }

    private static boolean execute(List<Insertion> insertions) {
        if (insertions.isEmpty()) return false;
        Map<IInventorySlot, ItemStack> before = new IdentityHashMap<>();
        insertions.forEach(insertion -> before.putIfAbsent(insertion.slot(), insertion.slot().getStack().copy()));
        try {
            for (Insertion insertion : insertions) {
                ItemStack remainder = insertion.slot().insertItem(
                        insertion.stack().copyWithCount(insertion.amount()),
                        Action.EXECUTE, AutomationType.EXTERNAL);
                if (!remainder.isEmpty()) {
                    before.forEach(IInventorySlot::setStack);
                    return false;
                }
            }
            return true;
        } catch (RuntimeException | Error failure) {
            before.forEach(IInventorySlot::setStack);
            throw failure;
        }
    }

    private static boolean sameRequests(List<PatternStack> expected, List<PatternStack> actual) {
        List<PatternStack> remaining = new ArrayList<>(actual);
        for (PatternStack input : expected) {
            if (input == null || input.stack().isEmpty() || input.amount() <= 0
                    || input.amount() > Integer.MAX_VALUE) return false;
            long amount = input.amount();
            for (int index = 0; index < remaining.size() && amount > 0; index++) {
                PatternStack candidate = remaining.get(index);
                if (candidate == null || candidate.amount() <= 0) continue;
                if (ItemStack.isSameItemSameComponents(input.stack(), candidate.stack())) {
                    long taken = Math.min(amount, candidate.amount());
                    amount -= taken;
                    remaining.set(index, new PatternStack(candidate.stack(), candidate.amount() - taken));
                }
            }
            if (amount != 0) return false;
        }
        return remaining.stream().allMatch(stack -> stack != null && stack.amount() == 0);
    }

    private record Insertion(IInventorySlot slot, ItemStack stack, int amount) { }
}
