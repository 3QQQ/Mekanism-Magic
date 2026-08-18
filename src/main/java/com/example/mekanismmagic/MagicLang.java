package com.example.mekanismmagic;

import mekanism.api.text.ILangEntry;

public enum MagicLang implements ILangEntry {
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
