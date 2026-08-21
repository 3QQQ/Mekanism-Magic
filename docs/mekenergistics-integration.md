# Mek Energistics integration hook

Mekanism Magic machines implement
`com.example.mekanismmagic.api.IMekanismMagicAutomation`.

The interface exposes pattern inputs, outputs, persistent inputs, manual-only
slots, energy, busy state, and whether pattern automation is supported. It has
no AE2 or Mek Energistics dependency, allowing an external adapter to remain
optional.

The initial contract version is `IMekanismMagicAutomation.API_VERSION == 1`.
