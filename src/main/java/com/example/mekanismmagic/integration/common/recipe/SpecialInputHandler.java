package com.example.mekanismmagic.integration.common.recipe;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Validation and consumption rules for a recipe's optional special input.
 *
 * <p>This is used by non-standard inputs such as reusable entity containers.
 * Each optional integration can provide its own implementation without
 * coupling the common machine loop to a specific mod.</p>
 */
public interface SpecialInputHandler {
    SpecialInputHandler NONE = new SpecialInputHandler() {
        @Override
        public boolean matches(ItemStack stack) {
            return false;
        }

        @Override
        public boolean consume(ItemStackHandler inventory, int slot) {
            return false;
        }
    };

    boolean matches(ItemStack stack);

    boolean consume(ItemStackHandler inventory, int slot);
}
