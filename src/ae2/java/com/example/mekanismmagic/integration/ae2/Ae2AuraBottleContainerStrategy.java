package com.example.mekanismmagic.integration.ae2;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.behaviors.ContainerItemStrategy;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKey;
import com.example.mekanismmagic.integration.ae2.storage.MagicResourceKeys;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import de.ellpeck.naturesaura.api.aura.type.IAuraType;
import de.ellpeck.naturesaura.items.ItemAuraBottle;
import de.ellpeck.naturesaura.items.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Native AE held-container handling for Nature's Aura's own bottle and cork. */
public final class Ae2AuraBottleContainerStrategy implements
        ContainerItemStrategy<MagicResourceKey, Ae2AuraBottleContainerStrategy.Carried> {
    public static final long AURA_PER_BOTTLE = Ae2AuraBottlingService.AURA_PER_BOTTLE;

    public static void register(IEventBus modBus) {
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() ->
                ContainerItemStrategies.register(MagicResourceKeys.TYPE,
                        MagicResourceKey.class, new Ae2AuraBottleContainerStrategy())));
    }

    public static boolean isAura(MagicResourceKey key) {
        return key != null && NaturesAuraAPI.AURA_TYPES.containsKey(key.resource());
    }

    public static boolean isNativeBottle(ItemStack stack) {
        return stack != null && !stack.isEmpty() && (stack.is(ModItems.BOTTLE_TWO_THE_REBOTTLING)
                || stack.is(ModItems.AURA_BOTTLE));
    }

    @Override public GenericStack getContainedStack(ItemStack stack) {
        if (stack == null || !stack.is(ModItems.AURA_BOTTLE)) return null;
        IAuraType type = ItemAuraBottle.getType(stack);
        if (type == null) return null;
        var key = MagicResourceKeys.key(type.getName());
        return key instanceof MagicResourceKey aura && isAura(aura)
                ? new GenericStack(aura, AURA_PER_BOTTLE) : null;
    }

    @Override public Carried findCarriedContext(Player player, AbstractContainerMenu menu) {
        return player != null && menu != null && isNativeBottle(menu.getCarried())
                ? new Carried(player, menu) : null;
    }

    @Override public long insert(Carried context, MagicResourceKey key, long amount, Actionable action) {
        if (context == null || !isAura(key) || amount < AURA_PER_BOTTLE
                || !context.stack().is(ModItems.BOTTLE_TWO_THE_REBOTTLING)) return 0;
        IAuraType type = NaturesAuraAPI.AURA_TYPES.get(key.resource());
        if (type == null) return 0;
        if (action == Actionable.MODULATE) {
            context.replaceOne(ItemAuraBottle.setType(new ItemStack(ModItems.AURA_BOTTLE), type));
        }
        return AURA_PER_BOTTLE;
    }

    @Override public long extract(Carried context, MagicResourceKey key, long amount, Actionable action) {
        if (context == null || !isAura(key) || amount < AURA_PER_BOTTLE
                || !context.stack().is(ModItems.AURA_BOTTLE)) return 0;
        IAuraType contained = ItemAuraBottle.getType(context.stack());
        if (contained == null || !contained.getName().equals(key.resource())) return 0;
        if (action == Actionable.MODULATE) {
            context.replaceOne(new ItemStack(ModItems.BOTTLE_TWO_THE_REBOTTLING));
        }
        return AURA_PER_BOTTLE;
    }

    @Override public GenericStack getExtractableContent(Carried context) {
        return context == null ? null : getContainedStack(context.stack());
    }

    @Override public void playFillSound(Player player, MagicResourceKey key) {
        player.playSound(SoundEvents.BOTTLE_FILL, 1.0f, 1.0f);
    }

    @Override public void playEmptySound(Player player, MagicResourceKey key) {
        player.playSound(SoundEvents.BOTTLE_EMPTY, 1.0f, 1.0f);
    }

    public record Carried(Player player, AbstractContainerMenu menu) {
        public ItemStack stack() { return menu.getCarried(); }

        /** Same cursor/overflow rule as AE2's FluidContainerItemStrategy. */
        void replaceOne(ItemStack replacement) {
            ItemStack carried = menu.getCarried();
            carried.shrink(1);
            if (carried.isEmpty()) menu.setCarried(replacement);
            else player.getInventory().placeItemBackInInventory(replacement);
        }
    }
}
