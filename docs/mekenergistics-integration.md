# Mek Energistics integration hook

Mekanism Magic does not link against AE2 or Mek Energistics. Its machines
implement `com.example.mekanismmagic.api.IMekanismMagicAutomation`, which
exposes live Mekanism slots through immutable lists.

The initial contract version is `IMekanismMagicAutomation.API_VERSION == 1`.

An external adapter should:

1. Detect `tile instanceof IMekanismMagicAutomation`.
2. Ignore machines where `mekanismMagicSupportsPatternAutomation()` is false.
3. Map `mekanismMagicPatternInputs()` to consumed item input ports.
4. Map `mekanismMagicPatternOutputs()` to item output ports.
5. Treat `mekanismMagicPersistentInputs()` as machine setup, not pattern input.
6. Never insert into or encode `mekanismMagicManualOnlySlots()`.
7. Use `mekanismMagicEnergyContainer()` only when network-backed energy is
   enabled by the external mod.

Current slot policy:

- Mini Ritual Assembler: formation materials are pattern inputs; chalk is
  manual-only.
- Ritual Engine: formation materials, activation, and sacrifice are pattern
  inputs; the ritual selector and Dictionary of Spirits are manual-only.
- Spirit Processor and Spirit Factories: recipe items are pattern inputs; the
  spirit source is persistent.
- Dimensional Miner: the miner is persistent and the machine does not accept
  crafting patterns.
