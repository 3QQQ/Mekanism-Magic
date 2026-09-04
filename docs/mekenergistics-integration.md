# Mek Energistics integration hook

Mekanism Magic's core machine classes do not link against AE2 or Mek
Energistics. Optional source-set adapters consume
`com.example.mekanismmagic.api.IMekanismMagicAutomation`, which exposes live
Mekanism slots through immutable lists.

The current contract version is `IMekanismMagicAutomation.API_VERSION == 5`.

An external adapter should:

1. Detect `tile instanceof IMekanismMagicAutomation`.
2. Ignore pattern input on machines where
   `mekanismMagicSupportsPatternAutomation()` is false. Native direct-output
   bridges use `mekanismMagicSupportsDirectNetworkOutput()` independently.
   Providers must also re-check `mekanismMagicCanAdvertisePatterns()` whenever
   a persistent context item changes; this is the dynamic safety gate.
   If `mekanismMagicUsesContextualPatternValidation()` is true, every
   advertised and submitted pattern must additionally pass
   `mekanismMagicMatchesPattern(inputs, outputs)`. An adapter that cannot carry
   the complete input/output declaration must fail closed for that machine.
3. Map `mekanismMagicPatternInputs()` to consumed item input ports.
4. Map `mekanismMagicPatternOutputs()` to item output ports.
5. Treat `mekanismMagicPersistentInputs()` as machine setup, not pattern input.
6. Never insert into or encode `mekanismMagicManualOnlySlots()`.
7. Use `mekanismMagicEnergyContainer()` only when network-backed energy is
   enabled by the external mod.
8. When `mekanismMagicGroupParallelItemInputs()` is true, equivalent factory
   lanes may be exposed as one grouped input port. This must not be used for
   heterogeneous ritual ingredient slots.

Mek Energistics 3.0.6 uses a permutation router for unordered multi-key
patterns. The bundled adapter replaces that path for the Ritual Engine and
Mini Ritual Assembler with a bounded transactional router, disables new smart
multiplication batches for those two machines, and safely drains old pending
batches through the same bounded path.

Current slot policy:

- Mini Ritual Assembler: formation materials are pattern inputs; chalk is
  manual-only.
- Ritual Engine: formation materials, activation, and sacrifice are pattern
  inputs; the ritual selector and Dictionary of Spirits are manual-only.
- Spirit Processor and Spirit Factories: recipe items are pattern inputs; the
  spirit source is persistent. Patterns are advertised only while a
  deterministic, non-empty spirit source is installed. The gambler's weighted
  output is intentionally excluded from AE crafting while its actual results
  may still be exported to network storage. Installed patterns are resolved
  again against the current SpiritJob both while advertising and while
  accepting a push. Context-free Data Energistics/passive/smart-queue helper
  routes are disabled for these machines; old smart batches are refunded and
  their recovery buffer is drained in normal pattern mode.
- Dimensional Miner: the miner is persistent and the machine does not accept
  crafting patterns; its 27 output slots support native AE2 output.
- Drygmy Simulator: entity jars are persistent, crafting patterns are
  disabled, and its 27 output slots support native AE2 output.
Native AE2 output on the Dimensional Miner and Drygmy Simulator coalesces
identical keys in a persistent long buffer and submits one batch every 20
server ticks. A rejected batch applies backpressure; an offline grid falls
back to visible slots and third-party item transports.
