# Mek Energistics integration hook

Mekanism Magic's core machine classes do not link against AE2 or Mek
Energistics. Optional source-set adapters consume
`com.example.mekanismmagic.api.IMekanismMagicAutomation`, which exposes live
Mekanism slots through immutable lists.

The current contract version is `IMekanismMagicAutomation.API_VERSION == 2`.

An external adapter should:

1. Detect `tile instanceof IMekanismMagicAutomation`.
2. Ignore pattern input on machines where
   `mekanismMagicSupportsPatternAutomation()` is false. Native direct-output
   bridges use `mekanismMagicSupportsDirectNetworkOutput()` independently.
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
  crafting patterns; its 27 output slots support native AE2 output.
- Drygmy Simulator: entity jars are persistent, crafting patterns are
  disabled, and its 27 output slots support native AE2 output.

Native AE2 output on the Dimensional Miner and Drygmy Simulator coalesces
identical keys in a persistent long buffer and submits one batch every 20
server ticks. A rejected batch applies backpressure; an offline grid falls
back to visible slots and third-party item transports.
