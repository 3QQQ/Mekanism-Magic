package com.example.mekanismmagic.integration.naturesaura;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;

import java.util.EnumMap;

/** Server-authoritative menu actions shared by every Aura machine screen. */
public final class AuraSideConfigControls {
    public static final int FORWARD_BUTTON_START = 510;
    public static final int REVERSE_BUTTON_START = 520;
    public static final int DISABLE_ALL_BUTTON = 530;
    public static final int NEXT_ALL_BUTTON = 531;

    private AuraSideConfigControls() {
    }

    public static boolean isAuraSideButton(int buttonId) {
        return isSideButton(buttonId, FORWARD_BUTTON_START)
                || isSideButton(buttonId, REVERSE_BUTTON_START)
                || buttonId == DISABLE_ALL_BUTTON
                || buttonId == NEXT_ALL_BUTTON;
    }

    public static boolean canConfigure(MekanismTileContainer<?> menu,
                                       Player player) {
        var tile = menu.getTileEntity();
        var level = tile.getLevel();
        return !player.level().isClientSide()
                && player.containerMenu == menu
                && level == player.level()
                && !tile.isRemoved()
                && player.distanceToSqr(net.minecraft.world.phys.Vec3
                .atCenterOf(tile.getBlockPos())) <= 64.0
                && menu.stillValid(player)
                && level.getBlockEntity(tile.getBlockPos()) == tile
                && menu.canPlayerAccess(player);
    }

    public static boolean handleButton(MekanismTileContainer<?> menu,
                                       Player player, int buttonId) {
        if (!isAuraSideButton(buttonId) || !canConfigure(menu, player)
                || !(menu.getTileEntity() instanceof AuraSideModeHost host)) {
            return false;
        }
        if (isSideButton(buttonId, FORWARD_BUTTON_START)) {
            Direction side = Direction.values()[buttonId - FORWARD_BUTTON_START];
            host.setAuraSideMode(side, host.getAuraSideMode(side).next());
        } else if (isSideButton(buttonId, REVERSE_BUTTON_START)) {
            Direction side = Direction.values()[buttonId - REVERSE_BUTTON_START];
            host.setAuraSideMode(side, host.getAuraSideMode(side).previous());
        } else {
            var target = buttonId == DISABLE_ALL_BUTTON
                    ? NatureAuraMachineBlockEntity.AuraSideMode.NONE
                    : nextAllMode(host);
            var modes = new EnumMap<Direction,
                    NatureAuraMachineBlockEntity.AuraSideMode>(Direction.class);
            for (Direction side : Direction.values()) {
                modes.put(side, target);
            }
            host.setAuraSideModes(modes);
        }
        return true;
    }

    /** Mixed sides reset to NONE; uniform sides advance together. */
    public static NatureAuraMachineBlockEntity.AuraSideMode nextAllMode(
            AuraSideModeHost host) {
        var first = host.getAuraSideMode(Direction.DOWN);
        for (Direction side : Direction.values()) {
            if (host.getAuraSideMode(side) != first) {
                return NatureAuraMachineBlockEntity.AuraSideMode.NONE;
            }
        }
        return first.next();
    }

    private static boolean isSideButton(int buttonId, int first) {
        return buttonId >= first && buttonId < first + Direction.values().length;
    }
}
