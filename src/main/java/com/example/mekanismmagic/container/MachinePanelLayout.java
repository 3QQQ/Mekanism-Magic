package com.example.mekanismmagic.container;

/** Shared compact information rows, with actual menu inventory offsets derived from the same screen heights. */
public final class MachinePanelLayout {
    public static final int SIMPLE_HEIGHT = 176;
    public static final int FACTORY_HEIGHT = 171;
    public static final int FACTORY_INPUT_Y = 16;
    public static final int NATURE_COMPACT_HEIGHT = 176;
    public static final int NATURE_FACTORY_HEIGHT = 183;
    public static final int OFFERING_HEIGHT = 184;
    public static final int TREE_HEIGHT = 176;
    public static final Box TREE_ENERGY = new Box(8, 25, 16, 16);
    public static final int CLIBANO_HEIGHT = 190;
    public static final int FORGE_HEIGHT = 202;
    public static final int ANIMAL_HEIGHT = 185;
    public static final int ESSENCE_CONDENSER_WIDTH = 176;
    public static final int ESSENCE_CONDENSER_HEIGHT = 200;
    public static final Box ESSENCE_CONDENSER_ENERGY = new Box(8, 25, 16, 16);
    public static final int MINER_HEIGHT = 194;
    public static final int RITUAL_HEIGHT = 200;
    public static final int ARS_THREE_BY_THREE_HEIGHT = 194;
    public static final int DRYGMY_HEIGHT = 198;
    public static final int DRYGMY_GRID_SHIFT_X = 4;
    public static final Box DRYGMY_ENERGY = new Box(8, 44, 16, 16);
    /** A 4×4 ritual grid needs only a modest step up from the 176px ordinary deck. */
    public static final int MINI_RITUAL_WIDTH = 194;
    public static final int MINI_RITUAL_HEIGHT = 196;
    public static final int MINI_RITUAL_INPUT_X = 51;
    public static final int MINI_RITUAL_INPUT_Y = 21;
    public static final int MINI_RITUAL_OUTPUT_X = 158;
    public static final int MINI_RITUAL_OUTPUT_Y = 48;
    public static final int MINI_RITUAL_PROGRESS_X = 126;
    public static final int MINI_RITUAL_PROGRESS_Y = 52;
    private MachinePanelLayout() { }
    /** Item coordinate for a centered nine-column player inventory (18px visual slot borders). */
    public static int inventoryX(int imageWidth) { return (imageWidth - 9 * 18) / 2 + 1; }
    public static int inventoryY(int imageHeight) { return imageHeight - 81; }
    public static int inventoryLabelY(int imageHeight) { return imageHeight - 94; }
    public static Box forgeEssence(int channel) {
        if (channel < 0 || channel >= 4) throw new IndexOutOfBoundsException(channel);
        return new Box(25 + channel * 36, 79, 36, 26);
    }
    public static final Box FORGE_TIER = new Box(28, 19, 44, 24);
    public static final Box FORGE_MAIN = new Box(86, 25, 16, 16);
    public static final Box FORGE_OUTPUT = new Box(142, 53, 16, 16);
    public static final int FORGE_PROGRESS_X = 110;
    public static final int FORGE_PROGRESS_Y = 57;
    /** Client-only presentation; the server retains its original physical slot coordinates. */
    public static int forgeAuxiliaryX(int index) {
        if (index < 0 || index >= 8) throw new IndexOutOfBoundsException(index);
        return 32 + index % 4 * 18;
    }
    public static int forgeAuxiliaryY(int index) {
        if (index < 0 || index >= 8) throw new IndexOutOfBoundsException(index);
        return 44 + index / 4 * 18;
    }
    public static int forgeVesselX(int index) {
        if (index < 0 || index >= 3) throw new IndexOutOfBoundsException(index);
        return 8;
    }
    public static int forgeVesselY(int index) {
        if (index < 0 || index >= 3) throw new IndexOutOfBoundsException(index);
        return 44 + index * 18;
    }
    public static Box essenceCondenserBank(int channel) {
        if (channel < 0 || channel >= 4) throw new IndexOutOfBoundsException(channel);
        return new Box(23 + channel * 36, 54, 36, 26);
    }
    public static Box essenceCondenserProgress(int channel) {
        if (channel < 0 || channel >= 4) throw new IndexOutOfBoundsException(channel);
        return new Box(27 + channel * 36, 49, 28, 2);
    }
    public static int essenceCondenserSampleX(int channel) {
        if (channel < 0 || channel >= 4) throw new IndexOutOfBoundsException(channel);
        return 32 + channel * 36;
    }
    public static int essenceCondenserSampleY(int channel) {
        if (channel < 0 || channel >= 4) throw new IndexOutOfBoundsException(channel);
        return 28;
    }
    public static int essenceCondenserVesselInputX(int channel) {
        if (channel < 0 || channel >= 4) throw new IndexOutOfBoundsException(channel);
        return 24 + channel * 36;
    }
    public static int essenceCondenserVesselInputY(int channel) {
        if (channel < 0 || channel >= 4) throw new IndexOutOfBoundsException(channel);
        return 86;
    }
    public static int essenceCondenserVesselOutputX(int channel) {
        if (channel < 0 || channel >= 4) throw new IndexOutOfBoundsException(channel);
        return 42 + channel * 36;
    }
    public static int essenceCondenserVesselOutputY(int channel) {
        if (channel < 0 || channel >= 4) throw new IndexOutOfBoundsException(channel);
        return 86;
    }
    public static final Box AURA_AMOUNT = new Box(52, 56, 98, 17);
    public static final Box CONVERSION_RATE = new Box(58, 23, 92, 18);
    public static final Box FIELD_AMOUNT = new Box(58, 45, 92, 18);
    public static final Box NATURE_COMPACT_FOOTER = new Box(8, 67, 142, 12);
    public static final Box FUEL_AMOUNT = new Box(8, 54, 144, 12);
    public static final Box FUEL_FOOTER = new Box(8, 67, 144, 12);
    public static final Box ANIMAL_TARGET = new Box(44, 62, 106, 12);
    public static final Box ANIMAL_AURA = new Box(44, 76, 106, 12);
    public static final Box OFFERING_BONUS = new Box(8, 73, 160, 13);
    public static final Box CLIBANO_FIRE = new Box(8, 81, 160, 12);
    public static Box natureFactoryFooter(int imageWidth) { return new Box(28, 74, imageWidth - 54, 12); }

    public static int height(String id) {
        if (id.endsWith("_nature_altar_factory")) return NATURE_FACTORY_HEIGHT;
        if (id.endsWith("_spirit_factory") || id.endsWith("_imbuement_factory")) return FACTORY_HEIGHT;
        return switch (id) {
            case "nature_altar_processor", "fe_aura_converter", "aura_fuel_generator", "aura_field_interface" -> NATURE_COMPACT_HEIGHT;
            case "offering_processor" -> OFFERING_HEIGHT;
            case "tree_ritual_processor" -> TREE_HEIGHT;
            case "clibano_processor" -> CLIBANO_HEIGHT;
            case "hephaestus_forge_processor" -> FORGE_HEIGHT;
            case "animal_spawner_processor" -> ANIMAL_HEIGHT;
            case "essence_condenser" -> ESSENCE_CONDENSER_HEIGHT;
            case "dimension_miner" -> MINER_HEIGHT;
            case "ritual_engine" -> RITUAL_HEIGHT;
            case "enchanting_apparatus_processor", "catalyst_identifier_assembler" -> ARS_THREE_BY_THREE_HEIGHT;
            case "drygmy_simulator" -> DRYGMY_HEIGHT;
            default -> SIMPLE_HEIGHT;
        };
    }

    public static int labelY(String id) {
        return inventoryLabelY(height(id));
    }

    /** Shared rectangles used by actual widgets and real-menu overlap regression tests. */
    public static java.util.List<Box> information(String id, int imageWidth) {
        if (id.endsWith("_nature_altar_factory")) return java.util.List.of(natureFactoryFooter(imageWidth));
        java.util.List<Box> boxes = new java.util.ArrayList<>();
        switch (id) {
            case "nature_altar_processor" -> boxes.add(AURA_AMOUNT);
            case "fe_aura_converter", "aura_field_interface" -> {
                boxes.add(CONVERSION_RATE); boxes.add(FIELD_AMOUNT); boxes.add(NATURE_COMPACT_FOOTER);
            }
            case "aura_fuel_generator" -> { boxes.add(FUEL_AMOUNT); boxes.add(FUEL_FOOTER); }
            case "animal_spawner_processor" -> { boxes.add(ANIMAL_TARGET); boxes.add(ANIMAL_AURA); }
            case "offering_processor" -> { return java.util.List.of(OFFERING_BONUS); }
            case "clibano_processor" -> { boxes.add(CLIBANO_FIRE); boxes.add(new Box(84, 62, 44, 14)); }
            case "hephaestus_forge_processor" -> {
                boxes.add(FORGE_TIER);
                for (int channel = 0; channel < 4; channel++) boxes.add(forgeEssence(channel));
            }
            case "essence_condenser" -> {
                for (int channel = 0; channel < 4; channel++) boxes.add(essenceCondenserBank(channel));
            }
        }
        return java.util.List.copyOf(boxes);
    }
    public record Box(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean overlaps(Box other) {
            return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
        }
    }
}
