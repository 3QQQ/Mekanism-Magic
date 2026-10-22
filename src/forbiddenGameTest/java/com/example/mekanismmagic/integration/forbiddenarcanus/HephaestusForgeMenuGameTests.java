package com.example.mekanismmagic.integration.forbiddenarcanus;

import com.example.mekanismmagic.MekanismMagic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Old physical save indices remain, but the machine has no clickable core/hammer slot. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HephaestusForgeMenuGameTests {
    private HephaestusForgeMenuGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void legacyCoreAndHammerNeverEnterRegisteredMenu(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ForbiddenArcanusRegistries.HEPHAESTUS_FORGE_PROCESSOR_BLOCK.get());
        HephaestusForgeProcessorBlockEntity tile = helper.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = new HephaestusForgeProcessorContainer(73, player.getInventory(), tile);
        var oldCore = tile.getLogicalSlot(HephaestusForgeProcessorBlockEntity.LEGACY_CORE_SLOT);
        var oldStarter = tile.getLogicalSlot(HephaestusForgeProcessorBlockEntity.LEGACY_STARTER_SLOT);
        helper.assertTrue(oldCore != null && oldStarter != null,
                "Historic core/hammer storage indices were reassigned before migration");
        helper.assertTrue(menu.getInventoryContainerSlots().stream().noneMatch(slot ->
                        slot.getInventorySlot() == oldCore || slot.getInventorySlot() == oldStarter
                                || slot.x == 26 && slot.y == 25
                                || slot.x == 44 && slot.y == 25),
                "Retired core/hammer appeared as a real clickable menu slot");
        helper.assertTrue(menu.getInventoryContainerSlots().stream().anyMatch(slot ->
                        slot.getInventorySlot() == tile.getMainInputSlot()),
                "Removing the legacy controls also hid the real main-material slot");
        helper.succeed();
    }
}
