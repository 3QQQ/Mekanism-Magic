package com.example.mekanismmagic.integration.common.recipe;

/**
 * Describes an item amount consumed from one logical machine slot.
 */
public record InputUse(int slot, int count) {
    public InputUse {
        if (slot < 0) {
            throw new IllegalArgumentException("slot must be non-negative");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }
}
