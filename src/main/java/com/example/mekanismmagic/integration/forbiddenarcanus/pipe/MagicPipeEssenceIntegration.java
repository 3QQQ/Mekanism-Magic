package com.example.mekanismmagic.integration.forbiddenarcanus.pipe;

import com.example.mekanismmagic.integration.common.network.MagicPipeResourceHooks;
import mekanism.common.util.text.TextUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Optional F&A resource extension sharing the existing Source/Aura pipe topology. */
public final class MagicPipeEssenceIntegration {
    private static boolean registered;
    private MagicPipeEssenceIntegration() { }

    public static synchronized void register() {
        if (registered) return;
        MagicPipeResourceHooks.register("essence", MagicPipeEssenceTransmitter::new,
                MagicPipeEssenceChannel::new);
        MagicPipeResourceHooks.registerTooltip((tier, tooltip) -> {
            if (!com.example.mekanismmagic.integration.ModCompatibility.forbiddenArcanusIntegrationEnabled()) return;
            tooltip.add(Component.translatable("tooltip.mekanism_magic.magic_source_pipe.essence_capacity",
                    Component.literal(TextUtils.format(MagicPipeEssenceTransmitter.tierCapacity(tier)))
                            .withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.translatable("tooltip.mekanism_magic.magic_source_pipe.essence_pull_rate",
                    Component.literal(TextUtils.format(MagicPipeEssenceTransmitter.transferRate(tier)))
                            .withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_PURPLE));
            tooltip.add(Component.translatable("tooltip.mekanism_magic.magic_source_pipe.essence_transfers")
                    .withStyle(ChatFormatting.DARK_GRAY));
        });
        registered = true;
    }
}
