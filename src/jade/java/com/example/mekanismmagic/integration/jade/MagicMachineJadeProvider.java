package com.example.mekanismmagic.integration.jade;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import mekanism.common.lib.security.BlockSecurityUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Adds only magic-specific status; Jade's native capability provider owns FE. */
public enum MagicMachineJadeProvider
        implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            MekanismMagic.MOD_ID, "machine");

    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        return accessor.getBlockEntity() instanceof IMekanismMagicAutomation;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        data.remove(MachineJadeData.KEY);
        var tile = accessor.getBlockEntity();
        if (!(tile instanceof IMekanismMagicAutomation)
                || accessor.getPlayer() == null
                || !BlockSecurityUtils.get().canAccess(accessor.getPlayer(),
                accessor.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile)) {
            return;
        }
        CompoundTag status = MachineJadeData.capture(tile);
        if (!status.isEmpty()) {
            data.put(MachineJadeData.KEY, status);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor,
                              IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof IMekanismMagicAutomation)
                || !accessor.getServerData().contains(MachineJadeData.KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag data = accessor.getServerData().getCompound(MachineJadeData.KEY);
        tooltip.add(Component.translatable("jade.mekanism_magic.state",
                Component.translatable(data.getBoolean("active")
                        ? "jade.mekanism_magic.state.active" : "jade.mekanism_magic.state.idle")));
        if (data.contains("lanes", Tag.TAG_INT)) {
            tooltip.add(Component.translatable("jade.mekanism_magic.lanes",
                    data.getInt("workingLanes"), data.getInt("lanes"), data.getInt("progress")));
        } else if (data.getInt("progress") > 0) {
            tooltip.add(Component.translatable("jade.mekanism_magic.progress", data.getInt("progress")));
        }
        if (data.contains("source", Tag.TAG_INT)) {
            tooltip.add(Component.translatable("jade.mekanism_magic.source",
                    data.getInt("source"), data.getInt("sourceCapacity")));
        }
        if (data.contains("aura", Tag.TAG_INT)) {
            tooltip.add(data.getBoolean("creativeAura")
                    ? Component.translatable("jade.mekanism_magic.aura_creative")
                    : Component.translatable("jade.mekanism_magic.aura",
                            data.getInt("aura"), data.getInt("auraCapacity")));
        }
        String[] essenceNames = {"aureal", "souls", "blood", "experience"};
        for (int channel = 0; channel < essenceNames.length; channel++) {
            if (data.contains("essence" + channel, Tag.TAG_INT)) {
                tooltip.add(Component.translatable("gui.mekanism_magic.essence_condenser.bank." + essenceNames[channel])
                        .append(": ").append(Component.translatable("gui.mekanism_magic.essence_condenser.balance",
                                data.getInt("essence" + channel), data.getInt("essenceCapacity" + channel))));
            }
        }
        if (data.getBoolean("ae")) {
            // Same status words as the machine's original AE popover.
            String state = data.getBoolean("online") ? "online"
                    : data.getBoolean("connected") ? "offline" : "disconnected";
            tooltip.add(Component.translatable("jade.mekanism_magic.ae",
                    Component.translatable("gui.mekanism_magic.ae_resource." + state)));
            if (!data.getBoolean("supply")) {
                tooltip.add(Component.translatable("gui.mekanism_magic.ae_resource.supply",
                        Component.translatable("gui.mekanism_magic.ae_resource.disabled")));
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
