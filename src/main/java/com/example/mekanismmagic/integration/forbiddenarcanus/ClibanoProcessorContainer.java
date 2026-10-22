package com.example.mekanismmagic.integration.forbiddenarcanus;

import mekanism.common.inventory.container.tile.MekanismTileContainer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

public final class ClibanoProcessorContainer extends MekanismTileContainer<ClibanoProcessorBlockEntity> {
    public static final int CLAIM_EXPERIENCE = 610;

    public ClibanoProcessorContainer(int id, Inventory inventory, ClibanoProcessorBlockEntity tile) {
        super(ForbiddenArcanusRegistries.CLIBANO_PROCESSOR_CONTAINER, id, inventory, tile);
    }

    @Override
    protected int getInventoryYOffset() {
        return com.example.mekanismmagic.container.MachinePanelLayout.inventoryY(
                com.example.mekanismmagic.container.MachinePanelLayout.CLIBANO_HEIGHT);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != CLAIM_EXPERIENCE) return super.clickMenuButton(player, buttonId);
        if (!(player instanceof ServerPlayer serverPlayer) || player.containerMenu != this
                || tile.getLevel() != player.level() || tile.isRemoved() || !stillValid(player)
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(tile.getBlockPos())) > 64
                || !canPlayerAccess(player) || player.level().getBlockEntity(tile.getBlockPos()) != tile) {
            return false;
        }
        tile.takeExperience(serverPlayer);
        return true;
    }
}
