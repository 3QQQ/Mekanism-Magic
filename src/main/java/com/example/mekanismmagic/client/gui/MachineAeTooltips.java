package com.example.mekanismmagic.client.gui;

import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks.Snapshot;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Resource-transfer details shared by the unified settings module. No separate control or settings entry. */
public final class MachineAeTooltips {
    private static final int[] ESSENCE_BITS = {
            MachineAeResourceHooks.ESSENCE_AUREAL,
            MachineAeResourceHooks.ESSENCE_SOULS,
            MachineAeResourceHooks.ESSENCE_BLOOD,
            MachineAeResourceHooks.ESSENCE_EXPERIENCE
    };
    private static final String[] ESSENCE_NAMES = {"aureal", "souls", "blood", "experience"};
    private MachineAeTooltips() { }

    /** Pure presentation contract used by headless layout and tooltip tests. */
    public static List<Component> tooltipLines(Snapshot snapshot) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.mekanism_magic.ae_config.title"));
        lines.add(Component.translatable("gui.mekanism_magic.ae_resource.supply",
                Component.translatable(snapshot.enabled()
                        ? "gui.mekanism_magic.ae_resource.enabled"
                        : "gui.mekanism_magic.ae_resource.disabled")));
        lines.add(Component.translatable("gui.mekanism_magic.ae_resource.network",
                Component.translatable(snapshot.connected()
                        ? "gui.mekanism_magic.ae_resource.connected"
                        : "gui.mekanism_magic.ae_resource.disconnected")));
        lines.add(Component.translatable("gui.mekanism_magic.ae_resource.channel",
                Component.translatable(snapshot.active()
                        ? "gui.mekanism_magic.ae_resource.channel_active"
                        : "gui.mekanism_magic.ae_resource.channel_inactive")));
        addResourceLine(lines, snapshot, MachineAeResourceHooks.ENERGY,
                "gui.mekanism_magic.ae_resource.energy", snapshot.lastEnergyJ(), true);
        addResourceLine(lines, snapshot, MachineAeResourceHooks.SOURCE,
                "gui.mekanism_magic.ae_resource.source", snapshot.lastSource(), false);
        addResourceLine(lines, snapshot, MachineAeResourceHooks.AURA,
                "gui.mekanism_magic.ae_resource.aura", snapshot.lastAura(), false);
        for (int channel = 0; channel < ESSENCE_BITS.length; channel++) {
            addResourceLine(lines, snapshot, ESSENCE_BITS[channel],
                    "gui.mekanism_magic.ae_resource.essence." + ESSENCE_NAMES[channel],
                    snapshot.lastEssence(channel), false);
        }
        return List.copyOf(lines);
    }

    private static void addResourceLine(List<Component> lines, Snapshot snapshot,
                                        int mask, String key, long amount,
                                        boolean joules) {
        if ((snapshot.resourceMask() & mask) == 0) {
            return;
        }
        Component state = (snapshot.enabledMask() & mask) == 0
                ? Component.translatable("gui.mekanism_magic.ae_resource.disabled")
                : !snapshot.active()
                ? Component.translatable("gui.mekanism_magic.ae_resource.waiting")
                : (snapshot.transferMask() & mask) == 0
                ? Component.translatable("gui.mekanism_magic.ae_resource.standby")
                : Component.translatable(joules
                        ? "gui.mekanism_magic.ae_resource.recent_joules"
                        : (mask & MachineAeResourceHooks.ESSENCE_MASK) != 0
                        ? "gui.mekanism_magic.ae_resource.recent_essence"
                        : "gui.mekanism_magic.ae_resource.recent", amount);
        lines.add(Component.translatable(key, state));
    }

    public static Component tooltip(Snapshot snapshot) {
        var result = Component.empty();
        boolean first = true;
        for (Component line : tooltipLines(snapshot)) {
            if (!first) {
                result.append("\n");
            }
            result.append(line);
            first = false;
        }
        return result;
    }
}
