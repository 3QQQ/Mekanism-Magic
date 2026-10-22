package com.example.mekanismmagic.client;

/**
 * Block-space coordinates shared by the natural altar factory model effects
 * and its recipe-item renderer.
 *
 * <p>The V4 JSON uses lane centres at 4, 8 and 12 model units.
 * Keeping the conversion here prevents the animated circles and item models
 * from drifting apart when the factory is viewed from another facing.</p>
 */
final class NatureAltarFactoryVisualLayout {
    static final int VISIBLE_BAYS = 3;
    // Reaction medium top=5.1; front stop top=5.2; emitter bottom=9.4.
    static final float CIRCLE_Y = 5.14F / 16F;
    static final float ITEM_Y = 6.80F / 16F;
    static final float CENTER_Z = 6.50F / 16F;
    static final float INTAKE_Z = 9.50F / 16F;
    static final float OUTPUT_Z = 4.35F / 16F;
    static final float ITEM_SCALE = 0.10F;
    static final float ITEM_ORBIT_RADIUS = 0.18F / 16F;
    static final float CIRCLE_RADIUS = 0.92F / 16F;

    private NatureAltarFactoryVisualLayout() {
    }

    static float laneX(int index) {
        return switch (index) {
            case 0 -> 4.00F / 16F;
            case 1 -> 8.00F / 16F;
            case 2 -> 12.00F / 16F;
            default -> throw new IndexOutOfBoundsException(
                    "Natural altar visual bay " + index);
        };
    }
}
