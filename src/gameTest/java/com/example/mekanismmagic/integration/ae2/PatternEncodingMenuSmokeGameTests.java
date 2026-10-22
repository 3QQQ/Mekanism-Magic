package com.example.mekanismmagic.integration.ae2;

import com.example.mekanismmagic.MekanismMagic;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** The JEI bridge tests must use AE's actual menu and displayed-slot API. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PatternEncodingMenuSmokeGameTests {
    private PatternEncodingMenuSmokeGameTests() {
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void realEncodingTerminalAndJeiViewsAreAvailable(GameTestHelper helper) {
        var terminal = Ae2PatternEncodingTestSupport.terminal(helper, new BlockPos(1, 2, 1));
        var views = Ae2PatternEncodingTestSupport.slots(
                Map.of("input", new ItemStack(Items.COBBLESTONE, 3),
                        "output", new ItemStack(Items.STONE)),
                Map.of("output", RecipeIngredientRole.OUTPUT));
        helper.assertTrue(terminal.menu().getProcessingInputSlots().length >= 2
                        && terminal.menu().getProcessingOutputSlots().length >= 2
                        && views.findSlotByName("input").orElseThrow()
                        .getDisplayedItemStack().orElseThrow().getCount() == 3
                        && views.getSlotViews(RecipeIngredientRole.OUTPUT).size() == 1
                        && Ae2PatternEncodingTestSupport.transferHelper()
                        .createInternalError() != null,
                "JEI/AE fixture did not expose native processing slots and current item views");
        helper.succeed();
    }
}
