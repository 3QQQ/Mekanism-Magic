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
