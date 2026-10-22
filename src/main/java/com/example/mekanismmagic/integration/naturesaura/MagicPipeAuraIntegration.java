package com.example.mekanismmagic.integration.naturesaura;

import com.example.mekanismmagic.integration.arsnouveau.MagicSourceTransmitter;
import com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks;
import de.ellpeck.naturesaura.api.aura.container.IAuraContainer;
import net.minecraft.core.Direction;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import mekanism.common.util.text.TextUtils;
import org.jetbrains.annotations.Nullable;

/** Loaded only by the Nature's Aura content module. */
public final class MagicPipeAuraIntegration {
    private MagicPipeAuraIntegration() {}

    public static void register() {
        MagicPipeResourceHooks.register("aura", MagicPipeAuraTransmitter::new,
                MagicPipeAuraChannel::new);
        MagicPipeResourceHooks.registerTooltip((tier, tooltip) -> {
            tooltip.add(stat("aura_capacity",
                    MagicPipeAuraTransmitter.tierCapacity(tier)));
            tooltip.add(stat("aura_pull_rate",
                    MagicPipeAuraTransmitter.transferRate(tier)));
            tooltip.add(Component.translatable(
                    "tooltip.mekanism_magic.magic_source_pipe.aura_transfers")
                    .withStyle(ChatFormatting.DARK_GRAY));
        });
    }

    public static @Nullable IAuraContainer capability(
            MagicSourceTransmitter pipe, @Nullable Direction side) {
        MagicPipeAuraTransmitter aura = MagicPipeResourceHooks.pipeExtension(pipe, MagicPipeAuraTransmitter.class);
        return aura == null ? null : aura.capability(side);
    }

    private static Component stat(String name, int amount) {
        return Component.translatable(
                "tooltip.mekanism_magic.magic_source_pipe." + name,
                Component.literal(TextUtils.format(amount))
                        .withStyle(ChatFormatting.GRAY))
                .withStyle(ChatFormatting.DARK_AQUA);
    }
}
