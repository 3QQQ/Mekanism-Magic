package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.integration.ModCompatibility;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Safe to load without F&A; keeps recipes/loot in step with exact-version registries. */
public record ForbiddenArcanusEnabledCondition() implements ICondition {
    public static final MapCodec<ForbiddenArcanusEnabledCondition> CODEC = MapCodec.unit(new ForbiddenArcanusEnabledCondition());

    @Override
    public boolean test(IContext context) {
        return ModCompatibility.forbiddenArcanusIntegrationEnabled();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
