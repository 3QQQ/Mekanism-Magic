package com.example.mekanismmagic.integration.forbiddenarcanus.client;

import java.util.List;
import java.util.stream.IntStream;

/** Shared coordinates keep the tested bounds identical to the actual JEI slots. */
public final class ForbiddenJeiLayout {
    public static final int WIDTH = 176;
    public static final int CLIBANO_HEIGHT = 132;
    public static final int MODIFIER_HEIGHT = 106;
    public static final int FORGE_HEIGHT = 176;
    public static final Slot CLIBANO_FIRST = new Slot("material_0", 16, 23);
    public static final Slot CLIBANO_SECOND = new Slot("material_1", 40, 23);
    public static final Slot CLIBANO_OUTPUT = new Slot("result", 138, 23);
    public static final Slot CLIBANO_FIRE = new Slot("fire_medium", 16, 62);
    public static final Slot CLIBANO_ENHANCER = new Slot("enhancer", 64, 62);
    public static final Slot CLIBANO_RESIDUE = new Slot("residue_combination", 138, 62);
    public static final Slot MODIFIER_BASE = new Slot("target", 16, 25);
    public static final Slot MODIFIER_TEMPLATE = new Slot("template", 42, 25);
    public static final Slot MODIFIER_ADDITION = new Slot("addition", 68, 25);
    public static final Slot MODIFIER_OUTPUT = new Slot("result", 138, 25);
    public static final Slot FORGE_MAIN = new Slot("main_material", 12, 22);
    public static final Slot FORGE_OUTPUT = new Slot("result", 146, 32);
    public static final List<Slot> FORGE_AUXILIARY = IntStream.range(0, 8)
            .mapToObj(index -> new Slot("auxiliary_" + index, 46 + index % 4 * 20, 22 + index / 4 * 20)).toList();
    public static final List<Slot> FORGE_ENHANCERS = IntStream.range(0, 4)
            .mapToObj(index -> new Slot("enhancer_" + index, 20 + index * 36, 82)).toList();

    private ForbiddenJeiLayout() { }

    public record Slot(String name, int x, int y) { }
}
