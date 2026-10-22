package com.example.mekanismmagic.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigInventory;
import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceMarkerItem;
import com.hollingsworth.arsnouveau.api.source.ISourceCap;
import com.hollingsworth.arsnouveau.setup.registry.CapabilityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.concurrent.atomic.AtomicInteger;

/** Covers both the native ArsEng key and the addon's standalone Source key. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Ae2SourceInterfaceGameTests {
    private Ae2SourceInterfaceGameTests() {}

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void sourceInterfaceTransfersPreserveOtherSlots(
            GameTestHelper helper) {
        AEKey key = MagicResourceKeys.source();
        helper.assertTrue(key != null, "Source resource key is unavailable");
        AtomicInteger changed = new AtomicInteger();
        ConfigInventory inventory = ConfigInventory.storage(4)
                .supportedTypes(key.getType(), AEKeyType.items())
                .changeListener(changed::incrementAndGet).build();
        inventory.setCapacity(key.getType(), 100L);
        inventory.setCapacity(AEKeyType.items(), 64L);
        inventory.setStack(0, new GenericStack(AEItemKey.of(Items.STONE), 64));
        inventory.setStack(1, new GenericStack(key, 80));
        inventory.setStack(2, new GenericStack(key, 100));
        changed.set(0);
        ISourceCap source = Ae2SourceInterfaceStorage.create(inventory);
        helper.assertTrue(source != null && source.getSource() == 180
                        && source.getSourceCapacity() == 300,
                "Source view counted the unrelated item slot");
        helper.assertTrue(source.receiveSource(150, true) == 120
                        && source.getSource() == 180 && changed.get() == 0,
                "Simulated Source insertion changed interface contents");
        helper.assertTrue(source.receiveSource(150, false) == 120
                        && source.getSource() == 300 && changed.get() == 1,
                "Multi-slot Source insert lost resources or notified repeatedly");
        helper.assertTrue(source.extractSource(215, true) == 215
                        && source.getSource() == 300 && changed.get() == 1,
                "Simulated Source extraction changed interface contents");
        helper.assertTrue(source.extractSource(215, false) == 215
                        && source.getSource() == 85 && changed.get() == 2,
                "Multi-slot Source extract lost resources or notified repeatedly");
        helper.assertTrue(source.receiveSource(-1, false) == 0
                        && source.extractSource(-1, false) == 0
                        && inventory.getAmount(0) == 64,
                "Source adapter modified an unrelated resource or negative request");
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void sourceMarkerConfiguresRealInterfaceWithoutCreatingSource(
            GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 2);
        helper.setBlock(pos, AEBlocks.INTERFACE.block());
        var tile = (InterfaceBlockEntity) helper.getBlockEntity(pos);
        AEKey key = MagicResourceKeys.source();
        ItemStack marker = new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID,
                        "magic_resource_marker")));
        helper.assertFalse(marker.isEmpty(), "Resource marker is unregistered");
        MagicResourceMarkerItem.select(marker, MagicResourceKeys.resourceId(key));
        GenericStack ghost = GenericStack.unwrapItemStack(marker);
        helper.assertTrue(ghost != null && key.equals(ghost.what())
                        && ghost.amount() == 0,
                "Source marker unwraps as an item or carries real Source");

        var config = tile.getInterfaceLogic().getConfig();
        var storage = tile.getInterfaceLogic().getStorage();
        FakeSlot slot = new FakeSlot(config.createMenuWrapper(), 0);
        helper.assertTrue(slot.canSetFilterTo(marker),
                "Native AE fake slot rejected the Source marker");
        slot.set(marker);
        helper.assertTrue(key.equals(config.getKey(0))
                        && config.getAmount(0) == 1,
                "AE did not turn the empty Source marker into a stock request");
        // The stock-amount menu updates this same config inventory; positive
        // ghost amounts are requests and never interface storage contents.
        slot.set(GenericStack.wrapInItemStack(key, 4_096));
        helper.assertTrue(key.equals(config.getKey(0))
                        && config.getAmount(0) == 4_096,
                "The Source stock request could not change its amount");
        var realSlots = storage.createMenuWrapper();
        helper.assertFalse(realSlots.isItemValid(0, marker),
                "Physical interface slots accept and destroy a reusable Source selector");
        helper.assertTrue(ItemStack.isSameItemSameComponents(marker,
                        realSlots.insertItem(0, marker.copy(), false)),
                "Shift/automation insertion consumed the Source selector");
        realSlots.setItemDirect(0, marker);
        helper.assertTrue(storage.isEmpty(),
                "A zero-amount Source marker created actual stored Source");

        var level = helper.getLevel();
        var absolute = helper.absolutePos(pos);
        ISourceCap source = level.getCapability(
                CapabilityRegistry.SOURCE_CAPABILITY, absolute, Direction.UP);
        helper.assertTrue(source != null,
                "A real AE interface is missing the Source capability");
        helper.assertTrue(source.receiveSource(500, false) == 500
                        && source.getSource() == 500,
                "Source capability did not write the real AE interface inventory");
        var generic = level.getCapability(AECapabilities.GENERIC_INTERNAL_INV,
                absolute, Direction.UP);
        helper.assertTrue(generic != null && generic.getAmount(0) == 500
                        && key.equals(generic.getKey(0)),
                "Source capability used a second tank instead of AE storage");
        realSlots.setItemDirect(0, marker);
        helper.assertTrue(source.getSource() == 500,
                "Direct selector placement erased existing Source");
        helper.succeed();
    }
}
