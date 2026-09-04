package com.example.mekanismmagic.integration.mekenergistics;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.beipuo.mekenergistics.blockentity.support.io.MeInputLayout;
import com.beipuo.mekenergistics.blockentity.support.io.MeInputPort;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.api.IMekanismMagicAutomation.PatternStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Context-safe MekE routing for Spirit processors and factories. */
public final class SpiritPatternMeRouter {
    private static final Map<MeInputLayout, LayoutContext> SPIRIT_LAYOUTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SpiritPatternMeRouter() {
    }

    public static boolean isSpiritHost(IMekanismMagicAutomation host) {
        return host != null
                && host.mekanismMagicUsesContextualPatternValidation();
    }

    public static void protectLayout(
            MeInputLayout layout, IMekanismMagicAutomation host,
            MekEnergisticsPendingRefund refund) {
        if (layout != null && isSpiritHost(host) && refund != null) {
            SPIRIT_LAYOUTS.put(layout, new LayoutContext(
                    new WeakReference<>(host),
                    new WeakReference<>(refund)));
            BoundedMePatternRouter.protectLayout(layout);
        }
    }

    public static boolean isSpiritLayout(MeInputLayout layout) {
        return layout != null && SPIRIT_LAYOUTS.containsKey(layout);
    }

    public static IMekanismMagicAutomation owner(MeInputLayout layout) {
        LayoutContext context = layout == null
                ? null : SPIRIT_LAYOUTS.get(layout);
        return context == null ? null : context.host().get();
    }

    public static boolean matchesPattern(
            IMekanismMagicAutomation host, IPatternDetails pattern) {
        if (!isSpiritHost(host) || pattern == null
                || pattern.getDefinition() == null
                || !host.mekanismMagicCanAdvertisePatterns()) {
            return false;
        }
        Level level = level(host);
        if (level == null) {
            return false;
        }
        try {
            PatternDeclaration declaration = declaration(pattern, level);
            return declaration != null
                    && host.mekanismMagicMatchesPattern(
                    declaration.inputs(), declaration.outputs());
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    public static boolean matchesPattern(
            IMekanismMagicAutomation host, IPatternDetails pattern,
            KeyCounter[] actualInputs) {
        if (!isSpiritHost(host) || pattern == null
                || pattern.getDefinition() == null
                || !host.mekanismMagicCanAdvertisePatterns()) {
            return false;
        }
        Level level = level(host);
        if (level == null) {
            return false;
        }
        try {
            PatternDeclaration declaration = declaration(pattern, level);
            Map<AEKey, Long> actual =
                    BoundedMePatternRouter.normalizeInputs(actualInputs);
            if (declaration == null || actual == null
                    || !declaration.requests().equals(actual)) {
                return false;
            }
            List<PatternStack> routedInputs = patternStacks(actual);
            return routedInputs != null
                    && host.mekanismMagicMatchesPattern(
                    routedInputs, declaration.outputs());
        } catch (RuntimeException | LinkageError failure) {
            return false;
        }
    }

    public static boolean route(KeyCounter[] inputs,
                                List<? extends MeInputPort> ports) {
        return BoundedMePatternRouter.route(inputs, ports);
    }

    public static long maxAcceptedCopies(
            KeyCounter[] inputs, List<? extends MeInputPort> ports) {
        return BoundedMePatternRouter.maxAcceptedCopies(inputs, ports);
    }

    /**
     * New smart queues are disabled for Spirit machines. Any queue persisted
     * by an older build is returned losslessly instead of being dispatched
     * under a possibly different SpiritJob context.
     */
    public static boolean refundPending(
            MeInputLayout layout, KeyCounter[] inputs) {
        LayoutContext context = layout == null
                ? null : SPIRIT_LAYOUTS.get(layout);
        IMekanismMagicAutomation host = context == null
                ? null : context.host().get();
        MekEnergisticsPendingRefund refund = context == null
                ? null : context.refund().get();
        // Clear the definition captured by PendingPattern even though the
        // prohibited legacy batch is deliberately refunded rather than run.
        MekEnergisticsPendingPatternContext.take();
        if (!isSpiritHost(host) || refund == null) {
            return false;
        }
        Map<AEKey, Long> pending =
                BoundedMePatternRouter.normalizeInputs(inputs);
        if (pending == null) {
            return false;
        }
        pending.forEach(refund::mekanismMagic$refundPending);
        return true;
    }

    private static PatternDeclaration declaration(
            IPatternDetails pattern, Level level) {
        IPatternDetails.IInput[] declaredInputs = pattern.getInputs();
        if (declaredInputs == null || declaredInputs.length == 0) {
            return null;
        }
        List<PatternStack> inputs = new ArrayList<>();
        Map<AEKey, Long> requests = new LinkedHashMap<>();
        for (IPatternDetails.IInput declared : declaredInputs) {
            if (declared == null) {
                return null;
            }
            GenericStack[] choices = declared.getPossibleInputs();
            // Processing patterns normally have one concrete choice. Failing
            // closed avoids advertising substitutions whose different NBT or
            // item could resolve to another Spirit recipe.
            if (choices == null || choices.length != 1
                    || choices[0] == null
                    || !(choices[0].what() instanceof AEItemKey item)
                    || choices[0].amount() <= 0
                    || !declared.isValid(item, level)) {
                return null;
            }
            long amount = Math.multiplyExact(choices[0].amount(),
                    Math.max(1L, declared.getMultiplier()));
            if (amount <= 0 || amount > Integer.MAX_VALUE) {
                return null;
            }
            requests.merge(item, amount, Math::addExact);
            inputs.add(new PatternStack(item.toStack(1), amount));
        }

        List<PatternStack> outputs = new ArrayList<>();
        List<GenericStack> declaredOutputs = pattern.getOutputs();
        if (declaredOutputs == null) {
            return null;
        }
        for (GenericStack output : declaredOutputs) {
            if (output == null) {
                continue;
            }
            if (output.amount() <= 0
                    || output.amount() > Integer.MAX_VALUE
                    || !(output.what() instanceof AEItemKey item)) {
                return null;
            }
            outputs.add(new PatternStack(item.toStack(1), output.amount()));
        }
        return inputs.isEmpty() || outputs.isEmpty()
                ? null : new PatternDeclaration(List.copyOf(inputs),
                List.copyOf(outputs), Map.copyOf(requests));
    }

    private static List<PatternStack> patternStacks(
            Map<AEKey, Long> requests) {
        List<PatternStack> inputs = new ArrayList<>(requests.size());
        for (Map.Entry<AEKey, Long> entry : requests.entrySet()) {
            if (!(entry.getKey() instanceof AEItemKey item)
                    || entry.getValue() <= 0
                    || entry.getValue() > Integer.MAX_VALUE) {
                return null;
            }
            inputs.add(new PatternStack(item.toStack(1), entry.getValue()));
        }
        return List.copyOf(inputs);
    }

    private static Level level(IMekanismMagicAutomation host) {
        return host instanceof BlockEntity blockEntity
                ? blockEntity.getLevel() : null;
    }

    private record PatternDeclaration(
            List<PatternStack> inputs,
            List<PatternStack> outputs,
            Map<AEKey, Long> requests) {
    }

    private record LayoutContext(
            WeakReference<IMekanismMagicAutomation> host,
            WeakReference<MekEnergisticsPendingRefund> refund) {
    }
}
