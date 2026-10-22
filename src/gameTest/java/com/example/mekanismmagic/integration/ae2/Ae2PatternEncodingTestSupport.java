package com.example.mekanismmagic.integration.ae2;

import appeng.api.parts.PartHelper;
import appeng.core.definitions.AEParts;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.PatternEncodingTerminalPart;
import com.mojang.authlib.GameProfile;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** Real AE terminal menu plus JEI data-only views; no client packet/render calls. */
public final class Ae2PatternEncodingTestSupport {
    private Ae2PatternEncodingTestSupport() {
    }

    public static Terminal terminal(GameTestHelper helper, BlockPos relativePos) {
        // A real ServerPlayer/menu without a synthetic login handshake that
        // optional mods would attempt to send data-pack packets through.
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "pattern-test"));
        BlockPos position = helper.absolutePos(relativePos);
        player.setPos(position.getX() + 0.5, position.getY(), position.getZ() - 2);
        helper.setBlock(relativePos, Blocks.AIR);
        PatternEncodingTerminalPart part = PartHelper.setPart(
                helper.getLevel(), position, Direction.NORTH, player,
                AEParts.PATTERN_ENCODING_TERMINAL.get());
        helper.assertTrue(part != null, "Real AE pattern terminal part was not placed");
        PatternEncodingTermMenu menu = new PatternEncodingTermMenu(
                71, player.getInventory(), part);
        player.containerMenu = menu;
        helper.assertTrue(menu.getProcessingInputSlots().length > 0
                        && menu.getProcessingOutputSlots().length > 0,
                "Real AE terminal menu has no processing slots");
        return new Terminal(player, part, menu);
    }

    /** Each view exposes only the currently visible item, not cycling alternatives. */
    public static IRecipeSlotsView slots(Map<String, ItemStack> stacks,
                                         Map<String, RecipeIngredientRole> roles) {
        List<IRecipeSlotView> views = new ArrayList<>();
        stacks.forEach((name, stack) -> views.add(new Slot(name,
                roles.getOrDefault(name, RecipeIngredientRole.INPUT), stack)));
        List<IRecipeSlotView> immutable = List.copyOf(views);
        return () -> immutable;
    }

    public static IRecipeSlotView slot(String name,
                                       RecipeIngredientRole role,
                                       ItemStack stack) {
        return new Slot(name, role, stack);
    }

    public static IRecipeTransferHandlerHelper transferHelper() {
        return (IRecipeTransferHandlerHelper) Proxy.newProxyInstance(
                Ae2PatternEncodingTestSupport.class.getClassLoader(),
                new Class<?>[]{IRecipeTransferHandlerHelper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createInternalError" -> new TransferError(
                            IRecipeTransferError.Type.INTERNAL, List.of());
                    case "createUserErrorWithTooltip", "createUserErrorForMissingSlots" ->
                            new TransferError(IRecipeTransferError.Type.USER_FACING,
                                    List.of((Component) args[0]));
                    case "createRecipeSlotsView" -> viewList(args[0]);
                    case "recipeTransferHasServerSupport" -> true;
                    case "toString" -> "AE encoding test JEI helper";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @SuppressWarnings("unchecked")
    private static IRecipeSlotsView viewList(Object value) {
        List<IRecipeSlotView> views = List.copyOf((List<IRecipeSlotView>) value);
        return () -> views;
    }

    public record Terminal(ServerPlayer player, PatternEncodingTerminalPart part,
                           PatternEncodingTermMenu menu) {
    }

    private record TransferError(IRecipeTransferError.Type type,
                                 List<Component> tooltip) implements IRecipeTransferError {
        @Override public Type getType() { return type; }
        @Override public List<Component> getTooltip() { return tooltip; }
    }

    private record StackIngredient(ItemStack ingredient)
            implements ITypedIngredient<ItemStack> {
        @Override public IIngredientType<ItemStack> getType() { return VanillaTypes.ITEM_STACK; }
        @Override public ItemStack getIngredient() { return ingredient.copy(); }
    }

    private static final class Slot implements IRecipeSlotView {
        private final String name;
        private final RecipeIngredientRole role;
        private final ItemStack stack;

        private Slot(String name, RecipeIngredientRole role, ItemStack stack) {
            this.name = name;
            this.role = role;
            this.stack = stack == null ? ItemStack.EMPTY : stack.copy();
        }

        @Override public Stream<ITypedIngredient<?>> getAllIngredients() {
            return getAllIngredientsList().stream();
        }

        public List<ITypedIngredient<?>> getAllIngredientsList() {
            return stack.isEmpty() ? List.of() : List.of(new StackIngredient(stack));
        }

        @Override public Optional<ITypedIngredient<?>> getDisplayedIngredient() {
            return getAllIngredientsList().stream().findFirst();
        }

        @Override public RecipeIngredientRole getRole() { return role; }
        @Override public Optional<String> getSlotName() { return Optional.ofNullable(name); }

        @Override public void drawHighlight(GuiGraphics graphics, int color) {
            throw new AssertionError("A server-side JEI transfer test attempted to render");
        }
    }
}
