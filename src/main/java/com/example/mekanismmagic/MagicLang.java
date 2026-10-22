package com.example.mekanismmagic;

import mekanism.api.text.ILangEntry;

public enum MagicLang implements ILangEntry {
    UPGRADE_CREATIVE_MAGIC(
            "upgrade.mekanism_magic.creative_magic"),
    UPGRADE_CREATIVE_MAGIC_DESCRIPTION(
            "upgrade.mekanism_magic.creative_magic.description"),
    SOURCE_AMPLIFIER("block.mekanism_magic.source_generator"),
    SOURCE_CONVERTER("block.mekanism_magic.source_converter"),
    CATALYST_IDENTIFIER_ASSEMBLER(
            "block.mekanism_magic.catalyst_identifier_assembler"),
    BASIC_IMBUEMENT_FACTORY(
            "block.mekanism_magic.basic_imbuement_factory"),
    ADVANCED_IMBUEMENT_FACTORY(
            "block.mekanism_magic.advanced_imbuement_factory"),
    ELITE_IMBUEMENT_FACTORY(
            "block.mekanism_magic.elite_imbuement_factory"),
    ULTIMATE_IMBUEMENT_FACTORY(
            "block.mekanism_magic.ultimate_imbuement_factory"),
    IMBUEMENT_PROCESSOR("block.mekanism_magic.imbuement_processor"),
    ENCHANTING_APPARATUS_PROCESSOR(
            "block.mekanism_magic.enchanting_apparatus_processor"),
    DRYGMY_SIMULATOR("block.mekanism_magic.drygmy_simulator"),
    MAGIC_SOURCE_PIPE("block.mekanism_magic.magic_source_pipe"),
    ADVANCED_MAGIC_SOURCE_PIPE(
            "block.mekanism_magic.advanced_magic_source_pipe"),
    ELITE_MAGIC_SOURCE_PIPE(
            "block.mekanism_magic.elite_magic_source_pipe"),
    ULTIMATE_MAGIC_SOURCE_PIPE(
            "block.mekanism_magic.ultimate_magic_source_pipe"),
    ABSOLUTE_MAGIC_SOURCE_PIPE(
            "block.mekanism_magic.absolute_magic_source_pipe"),
    SUPREME_MAGIC_SOURCE_PIPE(
            "block.mekanism_magic.supreme_magic_source_pipe"),
    COSMIC_MAGIC_SOURCE_PIPE(
            "block.mekanism_magic.cosmic_magic_source_pipe"),
    INFINITE_MAGIC_SOURCE_PIPE(
            "block.mekanism_magic.infinite_magic_source_pipe"),
    AURA_FIELD_INTERFACE(
            "block.mekanism_magic.aura_field_interface"),
    FE_AURA_CONVERTER("block.mekanism_magic.fe_aura_converter"),
    AURA_FUEL_GENERATOR("block.mekanism_magic.aura_fuel_generator"),
    NATURE_ALTAR_PROCESSOR(
            "block.mekanism_magic.nature_altar_processor"),
    BASIC_NATURE_ALTAR_FACTORY(
            "block.mekanism_magic.basic_nature_altar_factory"),
    ADVANCED_NATURE_ALTAR_FACTORY(
            "block.mekanism_magic.advanced_nature_altar_factory"),
    ELITE_NATURE_ALTAR_FACTORY(
            "block.mekanism_magic.elite_nature_altar_factory"),
    ULTIMATE_NATURE_ALTAR_FACTORY(
            "block.mekanism_magic.ultimate_nature_altar_factory"),
    ABSOLUTE_NATURE_ALTAR_FACTORY(
            "block.mekanism_magic.absolute_nature_altar_factory"),
    SUPREME_NATURE_ALTAR_FACTORY(
            "block.mekanism_magic.supreme_nature_altar_factory"),
    COSMIC_NATURE_ALTAR_FACTORY(
            "block.mekanism_magic.cosmic_nature_altar_factory"),
    INFINITE_NATURE_ALTAR_FACTORY(
            "block.mekanism_magic.infinite_nature_altar_factory"),
    OFFERING_PROCESSOR("block.mekanism_magic.offering_processor"),
    TREE_RITUAL_PROCESSOR("block.mekanism_magic.tree_ritual_processor"),
    ANIMAL_SPAWNER_PROCESSOR("block.mekanism_magic.animal_spawner_processor"),
    CLIBANO_PROCESSOR("block.mekanism_magic.clibano_processor"),
    HEPHAESTUS_FORGE_PROCESSOR("block.mekanism_magic.hephaestus_forge_processor"),
    ESSENCE_CONDENSER("block.mekanism_magic.essence_condenser"),
    SPIRIT_PROCESSOR("block.mekanism_magic.spirit_processor"),
    DIMENSION_MINER("block.mekanism_magic.dimension_miner"),
    RITUAL_ENGINE("block.mekanism_magic.ritual_engine"),
    MINI_RITUAL_ASSEMBLER("block.mekanism_magic.mini_ritual_assembler"),
    BASIC_SPIRIT_FACTORY("block.mekanism_magic.basic_spirit_factory"),
    ADVANCED_SPIRIT_FACTORY("block.mekanism_magic.advanced_spirit_factory"),
    ELITE_SPIRIT_FACTORY("block.mekanism_magic.elite_spirit_factory"),
    ULTIMATE_SPIRIT_FACTORY("block.mekanism_magic.ultimate_spirit_factory");

    private final String key;

    MagicLang(String key) {
        this.key = key;
    }

    @Override
    public String getTranslationKey() {
        return key;
    }
}
