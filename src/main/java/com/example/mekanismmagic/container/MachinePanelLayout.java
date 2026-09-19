package com.example.mekanismmagic.container;

/** Shared compact information rows, with actual menu inventory offsets derived from the same screen heights. */
public final class MachinePanelLayout {
    public static final int SIMPLE_HEIGHT = 186;
    public static final int TREE_HEIGHT = 206;
    public static final int CLIBANO_HEIGHT = 216;
    public static final int FORGE_HEIGHT = 266;
    public static final int EXPERIENCE_HEIGHT = 246;
    public static final int ANIMAL_HEIGHT = 196;
    public static final int SOUL_HEIGHT = 196;
    private MachinePanelLayout() { }
    public static int inventoryY(int imageHeight) { return imageHeight - 81; }
    public static int inventoryLabelY(int imageHeight) { return imageHeight - 94; }
    public static Box forgeEssence(int channel) {
        if (channel < 0 || channel >= 4) throw new IndexOutOfBoundsException(channel);
        return new Box(10 + channel * 40, 119, 36, 28);
    }
    public static final Box EXPERIENCE_LEVEL = new Box(48, 20, 102, 28);
    public static final Box FORGE_TIER = new Box(108, 21, 52, 27);
    public static final Box AURA_AMOUNT = new Box(52, 56, 98, 17);
    public static final Box CONVERSION_RATE = new Box(52, 23, 98, 20);
    public static final Box FIELD_AMOUNT = new Box(52, 49, 98, 20);
    public static final Box FUEL_AMOUNT = new Box(8, 56, 144, 17);
    public static final Box ANIMAL_TARGET = new Box(44, 68, 106, 16);
    public static final Box OFFERING_BONUS = new Box(8, 74, 160, 14);
    public static final Box CLIBANO_FIRE = new Box(8, 84, 160, 16);
    public static final Box BLOOD_YIELD = new Box(48, 18, 102, 14);
    public static final Box BLOOD_STORAGE = new Box(8, 56, 144, 16);

    public static int height(String id) {
        return switch (id) {
            case "tree_ritual_processor" -> TREE_HEIGHT;
            case "clibano_processor" -> CLIBANO_HEIGHT;
            case "hephaestus_forge_processor" -> FORGE_HEIGHT;
            case "experience_bottler" -> EXPERIENCE_HEIGHT;
            case "animal_spawner_processor" -> ANIMAL_HEIGHT;
            case "soul_extractor_processor" -> SOUL_HEIGHT;
            default -> SIMPLE_HEIGHT;
        };
    }

    public static int labelY(String id) {
        boolean nature = id.equals("nature_altar_processor") || id.equals("fe_aura_converter") || id.equals("aura_fuel_generator")
                || id.equals("aura_field_interface") || id.equals("animal_spawner_processor") || id.endsWith("_nature_altar_factory");
        return inventoryY(height(id)) - (nature ? 10 : 13);
    }

    /** Shared rectangles used by actual widgets and real-menu overlap regression tests. */
    public static java.util.List<Box> information(String id, int imageWidth) {
        java.util.List<Box> boxes = new java.util.ArrayList<>();
        switch (id) {
            case "nature_altar_processor" -> boxes.add(AURA_AMOUNT);
            case "fe_aura_converter", "aura_field_interface" -> { boxes.add(CONVERSION_RATE); boxes.add(FIELD_AMOUNT); }
            case "aura_fuel_generator" -> boxes.add(FUEL_AMOUNT);
            case "animal_spawner_processor" -> { boxes.add(ANIMAL_TARGET); boxes.add(new Box(102, 23, 47, 17)); }
            case "offering_processor" -> { return java.util.List.of(OFFERING_BONUS); }
            case "clibano_processor" -> { boxes.add(CLIBANO_FIRE); boxes.add(new Box(84, 62, 44, 18)); }
            case "simulated_blood_extractor" -> { boxes.add(BLOOD_YIELD); boxes.add(BLOOD_STORAGE); }
            case "hephaestus_forge_processor" -> {
                boxes.add(FORGE_TIER);
                for (int channel = 0; channel < 4; channel++) boxes.add(forgeEssence(channel));
            }
            case "experience_bottler" -> {
                boxes.add(EXPERIENCE_LEVEL);
                for (int column = 0; column < 3; column++) {
                    boxes.add(new Box(15 + column * 50, 86, 44, 18));
                    boxes.add(new Box(15 + column * 50, 108, 44, 18));
                }
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
