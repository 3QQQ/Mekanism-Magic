package com.example.mekanismmagic.integration.ae2;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.api.IMekanismMagicAutomation;
import com.example.mekanismmagic.integration.arsnouveau.ArsNouveauRegistries;
import com.example.mekanismmagic.integration.arsnouveau.ImbuementProcessorBlockEntity;
import com.example.mekanismmagic.integration.common.network.MachineAeResourceHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** No AE/Jade/MekE symbols: known node/debt tags survive an absent-mod boot. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeAeOptionalAbsenceGameTests {
    private static final BlockPos ORIGINAL = new BlockPos(1, 2, 1);
    private static final BlockPos RESTORED = new BlockPos(3, 2, 1);
    private static final String ENABLED = "magic_native_ae_supply_enabled";
    private static final String INPUT_MASK = "magic_native_ae_input_mask";
    private static final String OUTPUT_MASK = "magic_native_ae_output_mask";
    private static final String OUTPUT_ENABLED = "magic_native_ae_output_enabled";
    private static final String REFUNDS = "magic_native_ae_pending_refunds";
    private static final String EXPORTS = "magic_native_ae_pending_exports";
    private static final String RESOURCE_NODE = "mekanism_magic_resource_ae";
    private static final String DIRECT_NODE = "mekanism_magic_direct_ae";
    private static final String IMBUEMENT_NODE = "mekanism_magic_imbuement_ae";
    private static final String[] OPAQUE_KEYS = {
            REFUNDS, RESOURCE_NODE, DIRECT_NODE, IMBUEMENT_NODE
    };

    private NativeAeOptionalAbsenceGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft",
            timeoutTicks = 130)
    public static void knownNativeAeStateSurvivesWithoutOptionalMods(
            GameTestHelper helper) {
        for (String mod : new String[]{"ae2", "mekenergistics",
                "jade", "appflux"}) {
            helper.assertTrue(!ModList.get().isLoaded(mod),
                    "AE-absent regression unexpectedly loaded " + mod);
        }
        helper.assertTrue(ModList.get().isLoaded("ars_nouveau"),
                "AE-absent fixture needs its real Ars machine");

        var block = ArsNouveauRegistries.IMBUEMENT_PROCESSOR_BLOCK.get();
        helper.setBlock(ORIGINAL, block);
        ImbuementProcessorBlockEntity original =
                helper.getBlockEntity(ORIGINAL);
        var registries = helper.getLevel().registryAccess();
        original.setSource(0);
        ((IMekanismMagicAutomation) original)
                .mekanismMagicEnergyContainer().setEnergy(0);
        CompoundTag seeded = original.saveWithoutMetadata(registries);
        seeded.putBoolean(ENABLED, false);
        seeded.putInt(INPUT_MASK, MachineAeResourceHooks.ENERGY);
        seeded.putInt(OUTPUT_MASK, MachineAeResourceHooks.OUTPUT_ITEMS);
        seeded.putBoolean(OUTPUT_ENABLED, false);
        seeded.put(REFUNDS, refunds());
        seeded.put(EXPORTS, exports());
        seeded.put(RESOURCE_NODE, opaqueNode("fallback"));
        seeded.put(DIRECT_NODE, opaqueNode("direct"));
        seeded.put(IMBUEMENT_NODE, opaqueNode("imbuement"));
        original.loadAdditional(seeded, registries);
        assertInactive(helper, original);

        CompoundTag first = original.saveWithoutMetadata(registries);
        assertKnownTags(helper, seeded, first);
        original.loadAdditional(first, registries);
        CompoundTag second = original.saveWithoutMetadata(registries);
        assertKnownTags(helper, first, second);
        assertInactive(helper, original);

        ItemStack savedItem = new ItemStack(block);
        original.saveToItem(savedItem, registries);
        CustomData itemData = savedItem.get(
                DataComponents.BLOCK_ENTITY_DATA);
        helper.assertTrue(itemData != null,
                "Machine item lost block-entity state without AE");
        assertKnownTags(helper, first, itemData.copyTag());

        helper.setBlock(RESTORED, block);
        helper.assertTrue(BlockItem.updateCustomBlockEntityTag(
                        helper.getLevel(), null,
                        helper.absolutePos(RESTORED), savedItem),
                "Machine item could not restore its known AE state");
        ImbuementProcessorBlockEntity restored =
                helper.getBlockEntity(RESTORED);
        restored.applyComponentsFromItemStack(savedItem);
        assertInactive(helper, restored);
        CompoundTag restoredTag = restored.saveWithoutMetadata(registries);
        assertKnownTags(helper, first, restoredTag);
        restored.loadAdditional(restoredTag, registries);
        assertKnownTags(helper, restoredTag,
                restored.saveWithoutMetadata(registries));

        helper.startSequence().thenIdle(40).thenExecute(() -> {
            assertInactive(helper, original);
            assertInactive(helper, restored);
            assertKnownTags(helper, first,
                    restored.saveWithoutMetadata(registries));
        }).thenSucceed();
    }

    private static CompoundTag refunds() {
        CompoundTag refunds = new CompoundTag();
        refunds.putDouble("ae", 1.5D);
        refunds.putLong("fe", 9L);
        ListTag magic = new ListTag();
        CompoundTag source = new CompoundTag();
        source.putString("id", "mekanism_magic:source");
        source.putLong("amount", 64L);
        magic.add(source);
        refunds.put("magic", magic);
        return refunds;
    }

    private static ListTag exports() {
        // This fixture deliberately avoids all AE classes and codecs. An
        // absent AE installation must retain even an unavailable key verbatim.
        ListTag entries = new ListTag();
        CompoundTag stack = new CompoundTag();
        stack.putString("key", "unavailable_magic_provider:typed_resource");
        stack.putLong("amount", (1L << 40) + 7);
        stack.putBoolean("uncertain", true);
        CompoundTag nested = new CompoundTag();
        nested.putString("owner", "retained-export-reservation");
        stack.put("opaque_key_data", nested);
        entries.add(stack);
        return entries;
    }

    private static CompoundTag opaqueNode(String marker) {
        CompoundTag node = new CompoundTag();
        node.putString("marker", marker);
        node.putLong("counter", 1_234L);
        CompoundTag nested = new CompoundTag();
        nested.putString("owner", "retained-without-ae");
        node.put("nested", nested);
        return node;
    }

    private static void assertKnownTags(GameTestHelper helper,
                                        CompoundTag expected,
                                        CompoundTag actual) {
        helper.assertTrue(actual.contains(ENABLED, Tag.TAG_BYTE)
                        && !actual.getBoolean(ENABLED),
                "Disabled AE supply switch was lost");
        helper.assertTrue(actual.contains(INPUT_MASK, Tag.TAG_INT)
                        && actual.getInt(INPUT_MASK) == expected.getInt(INPUT_MASK)
                        && actual.contains(OUTPUT_MASK, Tag.TAG_INT)
                        && actual.getInt(OUTPUT_MASK) == expected.getInt(OUTPUT_MASK)
                        && actual.contains(OUTPUT_ENABLED, Tag.TAG_BYTE)
                        && !actual.getBoolean(OUTPUT_ENABLED),
                "Independent AE function selections changed while optional AE was absent");
        for (String key : OPAQUE_KEYS) {
            helper.assertTrue(actual.contains(key, Tag.TAG_COMPOUND)
                            && expected.getCompound(key)
                            .equals(actual.getCompound(key)),
                    "Known opaque AE tag changed without AE: " + key);
        }
        helper.assertTrue(actual.contains(EXPORTS, Tag.TAG_LIST)
                        && expected.getList(EXPORTS, Tag.TAG_COMPOUND)
                        .equals(actual.getList(EXPORTS, Tag.TAG_COMPOUND)),
                "Absent AE lost, decoded or changed an owned/quarantined export reservation");
    }

    private static void assertInactive(GameTestHelper helper,
                                       ImbuementProcessorBlockEntity machine) {
        var status = MachineAeResourceHooks.snapshot(machine);
        helper.assertTrue(!status.supported()
                        && !MachineAeResourceHooks.isEnabled(machine)
                        && machine.getSource() == 0
                        && ((IMekanismMagicAutomation) machine)
                        .mekanismMagicEnergyContainer().getEnergy() == 0,
                "Absent AE created energy/Source or enabled its supply");
    }
}
