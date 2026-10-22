package com.example.mekanismmagic.item;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.block.NativeMachineBlock;
import mekanism.api.functions.ConstantPredicates;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.attachments.containers.creator.IContainerCreator;
import mekanism.common.attachments.containers.item.AttachedItems;
import mekanism.common.attachments.containers.item.ComponentBackedInventorySlot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Makes preserved machine inventories readable by Mekanism's ProjectE processor. */
public final class MachineItemContainers {
    private MachineItemContainers() {
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // Registry entries and Mekanism's own attachment creators are ready here.
        // Use the main thread because ContainerType's creator map is not concurrent.
        event.enqueueWork(() -> {
            for (var item : BuiltInRegistries.ITEM) {
                if (MekanismMagic.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())
                        && item instanceof BlockItem blockItem
                        && blockItem.getBlock() instanceof NativeMachineBlock<?>
                        && !ContainerType.ITEM.supports(new ItemStack(item))) {
                    // A null event bus registers attachments without an item capability.
                    ContainerType.ITEM.addDefaultCreators(null, item, SavedInventoryCreator::new);
                }
            }
        });
    }

    private static final class SavedInventoryCreator
            implements IContainerCreator<ComponentBackedInventorySlot, AttachedItems> {
        @Override
        public int totalContainers() {
            // Fresh block items have no inventory. For a saved item Mekanism uses
            // AttachedItems.size(), preserving the serialized order and old layouts.
            return 0;
        }

        @Override
        public AttachedItems initStorage(int containers) {
            return AttachedItems.create(containers);
        }

        @Override
        public ComponentBackedInventorySlot create(
                ContainerType<? super ComponentBackedInventorySlot, ?, ?> type,
                ItemStack attachedTo, int containerIndex) {
            // These slots describe saved contents, not the placed machine's I/O.
            return new ComponentBackedInventorySlot(attachedTo, containerIndex,
                    ConstantPredicates.alwaysFalseBi(), ConstantPredicates.alwaysFalseBi(),
                    ConstantPredicates.alwaysTrue());
        }
    }
}
