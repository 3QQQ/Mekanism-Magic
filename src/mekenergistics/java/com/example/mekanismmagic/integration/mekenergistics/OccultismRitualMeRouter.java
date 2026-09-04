package com.example.mekanismmagic.integration.mekenergistics;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import com.beipuo.mekenergistics.blockentity.support.io.MeInputLayout;
import com.beipuo.mekenergistics.blockentity.support.io.MeInputPort;
import com.example.mekanismmagic.blockentity.NativeRitualEngineBlockEntity;
import com.example.mekanismmagic.integration.common.recipe
        .BoundedRequestRouter;
import com.example.mekanismmagic.integration.occultism
        .OccultismRecipeBridge;
import net.minecraft.world.item.ItemStack;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Routes a ritual pattern into activation, sacrifice and material ports. */
public final class OccultismRitualMeRouter {
    private static final int ACTIVATION_PORT = 0;
    private static final int SACRIFICE_PORT = 1;
    private static final int MATERIAL_PORT_START = 2;
    private static final Map<MeInputLayout, LayoutContext> RITUAL_LAYOUTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private OccultismRitualMeRouter() {
    }

    public static void protectLayout(
            MeInputLayout layout,
            NativeRitualEngineBlockEntity tile,
            MekEnergisticsPendingRefund refund) {
        if (layout != null && tile != null && refund != null) {
            RITUAL_LAYOUTS.put(layout, new LayoutContext(
                    new WeakReference<>(tile),
                    new WeakReference<>(refund)));
        }
    }

    public static boolean isRitualLayout(MeInputLayout layout) {
        return layout != null && RITUAL_LAYOUTS.containsKey(layout);
    }

    public static NativeRitualEngineBlockEntity owner(
            MeInputLayout layout) {
        LayoutContext context = layout == null
                ? null : RITUAL_LAYOUTS.get(layout);
        return context == null ? null : context.tile().get();
    }

    public static boolean routePending(
            MeInputLayout layout, KeyCounter[] inputs) {
        LayoutContext context = layout == null
                ? null : RITUAL_LAYOUTS.get(layout);
        NativeRitualEngineBlockEntity tile = context == null
                ? null : context.tile().get();
        MekEnergisticsPendingRefund refund = context == null
                ? null : context.refund().get();
        AEKey definition = MekEnergisticsPendingPatternContext.take();
        if (tile == null || refund == null) {
            return false;
        }
        IPatternDetails pattern = null;
        if (definition instanceof AEItemKey encoded
                && tile.getLevel() != null) {
            try {
                pattern = PatternDetailsHelper.decodePattern(
                        encoded, tile.getLevel());
            } catch (RuntimeException | LinkageError ignored) {
                // Invalid legacy definitions are refunded below.
            }
        }
        if (pattern != null && matchesPattern(tile, pattern, inputs)) {
            return route(tile, inputs, layout.ports());
        }
        Map<AEKey, Long> pending =
                BoundedMePatternRouter.normalizeInputs(inputs);
        if (pending == null) {
            return false;
        }
        pending.forEach(refund::mekanismMagic$refundPending);
        return true;
    }

    public static boolean matchesPattern(
            NativeRitualEngineBlockEntity tile,
            IPatternDetails pattern, KeyCounter[] inputs) {
        if (pattern == null) {
            return false;
        }
        Optional<ResolvedPlan> resolved = resolvePlan(tile, inputs);
        if (resolved.isEmpty()) {
            return false;
        }
        if (resolved.get().plan().copies() != 1) {
            // A processing pattern is one logical ritual. Historical smart
            // pending batches may still contain several copies, but a newly
            // submitted pattern must not hide multiple executions behind one
            // declared output.
            return false;
        }
        ItemStack expected = resolved.get().plan().output();
        if (expected.isEmpty()) {
            return false;
        }
        List<GenericStack> outputs = pattern.getOutputs().stream()
                .filter(output -> output != null && output.amount() > 0)
                .toList();
        if (outputs.size() != 1
                || !(outputs.getFirst().what()
                instanceof AEItemKey outputKey)) {
            return false;
        }
        return AEItemKey.of(expected).equals(outputKey)
                && expected.getCount() == outputs.getFirst().amount();
    }

    public static boolean route(
            NativeRitualEngineBlockEntity tile,
            KeyCounter[] inputs,
            List<? extends MeInputPort> ports) {
        Optional<PreparedRoute> prepared = prepare(tile, inputs, ports);
        return prepared.isPresent()
                && BoundedMePatternRouter.executePlan(
                prepared.get().allocations(), ports);
    }

    public static long maxAcceptedCopies(
            NativeRitualEngineBlockEntity tile,
            KeyCounter[] inputs,
            List<? extends MeInputPort> ports) {
        // Occultism dynamic outputs and semantic roles are committed one
        // recipe at a time. MekE smart multiplication is disabled for this
        // machine, so advertising more than one copy would be misleading.
        return prepare(tile, inputs, ports).isPresent() ? 1 : 0;
    }

    private static Optional<PreparedRoute> prepare(
            NativeRitualEngineBlockEntity tile,
            KeyCounter[] inputs,
            List<? extends MeInputPort> ports) {
        if (tile == null || ports == null
                || ports.size() <= MATERIAL_PORT_START) {
            return Optional.empty();
        }
        Optional<ResolvedPlan> resolved = resolvePlan(tile, inputs);
        if (resolved.isEmpty()) {
            return Optional.empty();
        }
        Map<AEKey, Long> requests = resolved.get().requests();
        List<Map.Entry<AEKey, Long>> entries =
                resolved.get().entries();
        OccultismRecipeBridge.RitualAutomationPlan plan =
                resolved.get().plan();

        Map<AEKey, Long> activation = new LinkedHashMap<>();
        Map<AEKey, Long> sacrifice = new LinkedHashMap<>();
        Map<AEKey, Long> materials = new LinkedHashMap<>();
        try {
            for (OccultismRecipeBridge.RitualAutomationAllocation allocation
                    : plan.allocations()) {
                if (allocation.inputIndex() < 0
                        || allocation.inputIndex() >= entries.size()
                        || allocation.amount() <= 0) {
                    return Optional.empty();
                }
                AEKey key = entries.get(allocation.inputIndex()).getKey();
                Map<AEKey, Long> target = switch (allocation.role()) {
                    case ACTIVATION -> activation;
                    case SACRIFICE -> sacrifice;
                    case MATERIAL -> materials;
                };
                target.merge(key, (long) allocation.amount(),
                        Math::addExact);
            }
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
        if (!sameRequests(requests, activation, sacrifice, materials)) {
            return Optional.empty();
        }

        List<BoundedRequestRouter.Allocation<AEKey, MeInputPort>>
                allocations = new ArrayList<>();
        if (!appendPlan(allocations, activation,
                List.of(ports.get(ACTIVATION_PORT)))) {
            return Optional.empty();
        }
        if (!appendPlan(allocations, sacrifice,
                List.of(ports.get(SACRIFICE_PORT)))) {
            return Optional.empty();
        }
        if (!appendPlan(allocations, materials,
                ports.subList(MATERIAL_PORT_START, ports.size()))) {
            return Optional.empty();
        }
        return Optional.of(new PreparedRoute(List.copyOf(allocations)));
    }

    private static Optional<ResolvedPlan> resolvePlan(
            NativeRitualEngineBlockEntity tile,
            KeyCounter[] inputs) {
        if (tile == null) {
            return Optional.empty();
        }
        Map<AEKey, Long> requests =
                BoundedMePatternRouter.normalizeInputs(inputs);
        if (requests == null) {
            return Optional.empty();
        }
        List<Map.Entry<AEKey, Long>> entries =
                new ArrayList<>(requests.entrySet());
        List<OccultismRecipeBridge.RitualAutomationInput>
                machineInputs = new ArrayList<>(entries.size());
        for (Map.Entry<AEKey, Long> entry : entries) {
            if (!(entry.getKey() instanceof AEItemKey item)
                    || entry.getValue() <= 0
                    || entry.getValue() > Integer.MAX_VALUE) {
                return Optional.empty();
            }
            machineInputs.add(new OccultismRecipeBridge
                    .RitualAutomationInput(item.toStack(1),
                    entry.getValue().intValue()));
        }
        return tile.mekanismMagicPlanRitualAutomation(machineInputs)
                .map(plan -> new ResolvedPlan(requests,
                        List.copyOf(entries), plan));
    }

    private static boolean appendPlan(
            List<BoundedRequestRouter.Allocation<AEKey, MeInputPort>> target,
            Map<AEKey, Long> requests,
            List<? extends MeInputPort> ports) {
        if (requests.isEmpty()) {
            return true;
        }
        Optional<List<BoundedRequestRouter.Allocation<AEKey, MeInputPort>>>
                plan = BoundedMePatternRouter.planRequests(
                requests, ports);
        if (plan.isEmpty()) {
            return false;
        }
        target.addAll(plan.get());
        return true;
    }

    @SafeVarargs
    private static boolean sameRequests(
            Map<AEKey, Long> expected,
            Map<AEKey, Long>... roleRequests) {
        Map<AEKey, Long> merged = new LinkedHashMap<>();
        try {
            for (Map<AEKey, Long> role : roleRequests) {
                role.forEach((key, amount) -> merged.merge(
                        key, amount, Math::addExact));
            }
        } catch (ArithmeticException overflow) {
            return false;
        }
        return expected.equals(merged);
    }

    private record PreparedRoute(
            List<BoundedRequestRouter.Allocation<AEKey, MeInputPort>>
                    allocations) {
    }

    private record ResolvedPlan(
            Map<AEKey, Long> requests,
            List<Map.Entry<AEKey, Long>> entries,
            OccultismRecipeBridge.RitualAutomationPlan plan) {
    }

    private record LayoutContext(
            WeakReference<NativeRitualEngineBlockEntity> tile,
            WeakReference<MekEnergisticsPendingRefund> refund) {
    }
}
