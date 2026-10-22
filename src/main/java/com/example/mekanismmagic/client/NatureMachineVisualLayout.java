package com.example.mekanismmagic.client;

/**
 * V4 work areas in block space, measured from the authored model elements.
 * North (negative Z) is the front before the shared facing transform.
 */
final class NatureMachineVisualLayout {
    // blank_reaction_surface: x=4.25..11.75, z=3.75..11.75, top=5.
    static final WorkArea ALTAR = new WorkArea(
            8F / 16F, 5.04F / 16F, 7.75F / 16F,
            7.40F / 16F, 9.60F / 16F, 4.60F / 16F,
            1F / 16F, 0.20F);
    // Offering's sealed chamber starts at z=8; all items stay in its trough.
    static final WorkArea OFFERING = new WorkArea(
            8F / 16F, 5.40F / 16F, 5F / 16F,
            7.20F / 16F, 5.40F / 16F, 3.80F / 16F,
            0.45F / 16F, 0.16F);
    // planting_medium is offset to x=7.5, z=6.75; its top is y=5.25.
    static final WorkArea TREE = new WorkArea(
            7.50F / 16F, 5.30F / 16F, 6.75F / 16F,
            8.50F / 16F, 8.70F / 16F, 4.80F / 16F,
            0.85F / 16F, 0.20F);
    // Ingredients use the front of the deck, ahead of the animal's body.
    static final WorkArea ANIMAL_ITEMS = new WorkArea(
            8F / 16F, 4.19F / 16F, 2.85F / 16F,
            5.70F / 16F, 3.15F / 16F, 2.60F / 16F,
            0.15F / 16F, 0.10F);

    static final float ANIMAL_CENTER_X = 8F / 16F;
    static final float ANIMAL_CENTER_Z = 7.60F / 16F;
    static final float ANIMAL_CIRCLE_Y = 4.19F / 16F;
    static final float ANIMAL_FLOOR_Y = 4.38F / 16F;
    static final float ANIMAL_MAX_WIDTH = 4F / 16F;
    static final float ANIMAL_MAX_HEIGHT = 5.80F / 16F;
    static final float ANIMAL_MAX_SCALE = 0.38F;
    static final float ANIMAL_BOB = 0.04F / 16F;
    // Lowest fixed scanner geometry: animal_scanner_optical_slit, y=11.4.
    static final float ANIMAL_SCANNER_BOTTOM = 11.40F / 16F;
    // Full textured quads rotate: their swept half extent is radius * sqrt(2).
    static final float ALTAR_CIRCLE_RADIUS = 2.30F / 16F;
    static final float TREE_CIRCLE_RADIUS = 1.90F / 16F;
    static final float ANIMAL_CIRCLE_RADIUS = 2.10F / 16F;

    private NatureMachineVisualLayout() {
    }

    record WorkArea(float centerX, float circleY, float centerZ,
                    float itemY, float intakeZ, float outputZ,
                    float orbitRadius, float itemScale) {
    }
}
