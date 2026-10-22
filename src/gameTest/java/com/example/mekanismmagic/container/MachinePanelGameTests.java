package com.example.mekanismmagic.container;

import com.example.mekanismmagic.MekanismMagic;
import com.example.mekanismmagic.integration.forbiddenarcanus.EssenceCondenserBlockEntity;
import com.example.mekanismmagic.integration.forbiddenarcanus.HephaestusForgeProcessorBlockEntity;
import com.example.mekanismmagic.integration.naturesaura.TreeRitualProcessorBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.DrygmySimulatorBlockEntity;
import com.example.mekanismmagic.integration.arsnouveau.CatalystLibraryWindowSlot;
import mekanism.common.inventory.container.slot.InventoryContainerSlot;
import mekanism.common.inventory.slot.FactoryInputInventorySlot;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.block.attribute.Attribute;
import mekanism.common.block.attribute.AttributeGui;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Checks the real registered menus against the same rectangles that their new client data cards use. */
@GameTestHolder(MekanismMagic.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachinePanelGameTests {
    private MachinePanelGameTests() { }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void wideNativeMenusCenterInventoryWithoutChangingBackingSlots(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(2, 2, 2);
        for (String path : List.of("mini_ritual_assembler", "ritual_engine", "dimension_miner",
                "catalyst_identifier_assembler", "enchanting_apparatus_processor", "drygmy_simulator")) {
            var id = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, path);
            if (!BuiltInRegistries.BLOCK.containsKey(id)) continue;
            helper.setBlock(pos, Blocks.AIR);
            helper.setBlock(pos, BuiltInRegistries.BLOCK.get(id));
            TileEntityMekanism tile = helper.getBlockEntity(pos);
            var attribute = Attribute.get(tile.getBlockState().getBlock(), AttributeGui.class);
            var menu = (MekanismTileContainer<?>) attribute.getProvider(tile, false)
                    .createMenu(82, player.getInventory(), player);
            int width = path.equals("mini_ritual_assembler")
                    ? MachinePanelLayout.MINI_RITUAL_WIDTH : 210;
            int height = switch (path) {
                case "mini_ritual_assembler" -> MachinePanelLayout.MINI_RITUAL_HEIGHT;
                case "ritual_engine" -> MachinePanelLayout.RITUAL_HEIGHT;
                case "dimension_miner" -> MachinePanelLayout.MINER_HEIGHT;
                case "drygmy_simulator" -> MachinePanelLayout.DRYGMY_HEIGHT;
                default -> MachinePanelLayout.ARS_THREE_BY_THREE_HEIGHT;
            };
            int inventoryX = MachinePanelLayout.inventoryX(width);
            int inventoryY = MachinePanelLayout.inventoryY(height);
            helper.assertTrue(menu.getMainInventorySlots().size() == 27
                            && menu.getHotBarSlots().size() == 9,
                    "Wide native screen lost a player slot: " + path);
            for (int index = 0; index < 27; index++) {
                var slot = menu.getMainInventorySlots().get(index);
                helper.assertTrue(slot.x == inventoryX + index % 9 * 18
                                && slot.y == inventoryY + index / 9 * 18
                                && slot.container == player.getInventory()
                                && slot.getContainerSlot() == index + 9,
                        "Wide native main inventory coordinate/backing drifted: "
                                + path + " slot=" + index);
            }
            for (int index = 0; index < 9; index++) {
                var slot = menu.getHotBarSlots().get(index);
                helper.assertTrue(slot.x == inventoryX + index * 18
                                && slot.y == inventoryY + 58
                                && slot.container == player.getInventory()
                                && slot.getContainerSlot() == index,
                        "Wide native hotbar coordinate/backing drifted: "
                                + path + " slot=" + index);
            }
            if (tile instanceof DrygmySimulatorBlockEntity drygmy) {
                for (int role = 0; role < 37; role++) {
                    var inventory = role == 36 ? drygmy.getNativeEnergySlot() : drygmy.getLogicalSlot(role);
                    var matches = menu.getInventoryContainerSlots().stream()
                            .filter(slot -> slot.getInventorySlot() == inventory).toList();
                    helper.assertTrue(matches.size() == 1, "Drygmy lost a unique original physical role: " + role);
                    var slot = matches.getFirst();
                    int originalX = role == 36 ? 188 : 24 + role % 9 * 18;
                    int originalY = role == 36 ? 106 : role < 9 ? 16 : 46 + (role - 9) / 9 * 18;
                    helper.assertTrue(slot.x == originalX && slot.y == originalY,
                            "Drygmy changed an authoritative charging/sample/output coordinate: " + role);
                    int clientX = role == 36 ? MachinePanelLayout.DRYGMY_ENERGY.x()
                            : originalX + MachinePanelLayout.DRYGMY_GRID_SHIFT_X;
                    int clientY = role == 36 ? MachinePanelLayout.DRYGMY_ENERGY.y() : originalY;
                    helper.assertTrue(clientX >= 8 && clientX + 17 <= 190
                                    && clientY - 1 >= 15 && clientY + 17 <= MachinePanelLayout.inventoryLabelY(height)
                                    - MachineFrameLayout.WORK_INVENTORY_GAP,
                            "Drygmy client projection covers gauges or inventory caption: " + role);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "igloo/top", templateNamespace = "minecraft")
    public static void natureAndForbiddenInformationNeverCoversARealSlotOrPlayerInventory(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = new BlockPos(2, 2, 2);
        List<String> ids = new ArrayList<>(List.of("nature_altar_processor", "offering_processor", "tree_ritual_processor",
                "animal_spawner_processor", "aura_field_interface", "aura_fuel_generator", "fe_aura_converter",
                "clibano_processor", "hephaestus_forge_processor",
                "essence_condenser"));
        for (String tier : List.of("basic", "advanced", "elite", "ultimate", "absolute", "supreme", "cosmic", "infinite")) ids.add(tier + "_nature_altar_factory");
        int count = 0;
        for (String path : ids) {
            var id = ResourceLocation.fromNamespaceAndPath(MekanismMagic.MOD_ID, path);
            if (!BuiltInRegistries.BLOCK.containsKey(id)) continue;
            helper.setBlock(pos, Blocks.AIR);
            helper.setBlock(pos, BuiltInRegistries.BLOCK.get(id));
            TileEntityMekanism tile = helper.getBlockEntity(pos);
            var attribute = Attribute.get(tile.getBlockState().getBlock(), AttributeGui.class);
            var menu = (MekanismTileContainer<?>) attribute.getProvider(tile, false).createMenu(81, player.getInventory(), player);
            int width = width(path);
            var information = MachinePanelLayout.information(path, width);
            var retained = RetainedSlotProfiles.entries(tile);
            for (var slot : menu.getInventoryContainerSlots()) {
                // Mekanism upgrade windows and optional ME pattern windows reserve out-of-band placeholders at 0,0.
                if (slot.x == 0 && slot.y == 0 || slot.getInventorySlot() instanceof CatalystLibraryWindowSlot
                        || retained.stream().anyMatch(entry -> entry.inventory() == slot.getInventorySlot())) continue;
                var slotBox = presentedSlotBox(helper, tile, slot);
                helper.assertTrue(slotBox.y() >= 15 && slotBox.bottom() <= MachinePanelLayout.labelY(path)
                                - MachineFrameLayout.WORK_INVENTORY_GAP,
                        "A visible physical role leaves no inventory-caption breathing room: " + path + " slot=" + slot.index
                                + " class=" + slot.getInventorySlot().getClass().getName() + " server=" + slot.x + "," + slot.y
                                + " projected=" + slotBox);
                for (var box : information) helper.assertTrue(!box.overlaps(slotBox), "New information covers a real slot: " + path + " slot="
                        + slot.index + " at " + slot.x + "," + slot.y + " card=" + box);
            }
            helper.assertTrue(menu.getMainInventorySlots().size() == 27 && menu.getHotBarSlots().size() == 9
                    && menu.getMainInventorySlots().getFirst().y == MachinePanelLayout.inventoryY(MachinePanelLayout.height(path)),
                    "Screen/menu inventory heights disagree: " + path);
            for (int index = 0; index < information.size(); index++) {
                var box = information.get(index);
                helper.assertTrue(box.x() >= 0 && box.right() <= width && box.y() >= 16
                        && box.bottom() <= MachinePanelLayout.labelY(path) - MachineFrameLayout.WORK_INVENTORY_GAP,
                        "Information exceeds the work region or leaves no inventory-caption breathing room: " + path);
                for (int other = 0; other < index; other++) helper.assertTrue(!box.overlaps(information.get(other)), "Information cards/actions overlap: " + path);
                for (var slot : menu.getMainInventorySlots()) helper.assertTrue(!box.overlaps(
                        new MachinePanelLayout.Box(slot.x - 1, slot.y - 1, 18, 18)), "Information obscures player inventory: " + path);
            }
            count++;
        }
        helper.assertTrue(count >= 7, "Integrated GUI regression skipped its machine families");
        MekanismMagic.LOGGER.info("Machine panel layout: {} real nature/Forbidden menus passed card, slot, action and inventory separation checks", count);
        helper.succeed();
    }

    /** Server coordinates stay authoritative and unchanged; only the client work deck is regrouped. */
    private static MachinePanelLayout.Box presentedSlotBox(GameTestHelper helper, TileEntityMekanism tile,
                                                          InventoryContainerSlot slot) {
        int x = slot.x;
        int y = slot.y;
        var backing = slot.getInventorySlot();
        if (isFactoryRecipeInput(backing)) {
            helper.assertTrue(slot.y == 13, "A factory changed its authoritative input-row coordinate");
        }
        if ((isFactoryRecipeInput(backing) || backing instanceof EnergyInventorySlot) && slot.y == 13) {
            y = MachinePanelLayout.FACTORY_INPUT_Y;
        } else if (tile instanceof TreeRitualProcessorBlockEntity tree && slot.getInventorySlot() == tree.getNativeEnergySlot()) {
            helper.assertTrue(slot.x == 108 && slot.y == 71,
                    "Tree ritual changed its authoritative charging-slot coordinates");
            x = MachinePanelLayout.TREE_ENERGY.x();
            y = MachinePanelLayout.TREE_ENERGY.y();
        } else if (tile instanceof HephaestusForgeProcessorBlockEntity forge) {
            var inventory = slot.getInventorySlot();
            if (inventory == forge.getMainInputSlot()) {
                helper.assertTrue(x == 80 && y == 25, "Forge changed the server's main-input coordinates");
                x = MachinePanelLayout.FORGE_MAIN.x(); y = MachinePanelLayout.FORGE_MAIN.y();
            } else if (inventory == forge.getNativeOutputSlot()) {
                helper.assertTrue(x == 134 && y == 51, "Forge changed the server's product coordinates");
                x = MachinePanelLayout.FORGE_OUTPUT.x(); y = MachinePanelLayout.FORGE_OUTPUT.y();
            } else {
                for (int index = 0; index < 8; index++) {
                    if (inventory != forge.getAuxInputSlot(index)) continue;
                    helper.assertTrue(x == 26 + index % 4 * 18 && y == 51 + index / 4 * 18,
                            "Forge changed an authoritative auxiliary slot");
                    x = MachinePanelLayout.forgeAuxiliaryX(index); y = MachinePanelLayout.forgeAuxiliaryY(index);
                    break;
                }
                var vessels = List.of(forge.getEssenceInputSlot(), forge.getWorkingEssenceSlot(), forge.getReturnSlot());
                for (int index = 0; index < vessels.size(); index++) {
                    if (inventory != vessels.get(index)) continue;
                    helper.assertTrue(slot.x == 116 + index * 18 && slot.y == 98,
                            "Forge changed an authoritative vessel slot");
                    x = MachinePanelLayout.forgeVesselX(index); y = MachinePanelLayout.forgeVesselY(index);
                    break;
                }
            }
        } else if (tile instanceof EssenceCondenserBlockEntity condenser) {
            var inventory = slot.getInventorySlot();
            if (inventory == condenser.getNativeEnergySlot()) {
                helper.assertTrue(slot.x == 8 && slot.y == 45,
                        "Essence condenser changed its authoritative energy slot");
                x = MachinePanelLayout.ESSENCE_CONDENSER_ENERGY.x();
                y = MachinePanelLayout.ESSENCE_CONDENSER_ENERGY.y();
            } else {
                for (int channel = 0; channel < EssenceCondenserBlockEntity.CHANNEL_COUNT; channel++) {
                    if (inventory == condenser.getSampleSlot(channel)) {
                        helper.assertTrue(slot.x == 31 + channel * 50 && slot.y == 39,
                                "Essence condenser changed an authoritative sample slot");
                        x = MachinePanelLayout.essenceCondenserSampleX(channel);
                        y = MachinePanelLayout.essenceCondenserSampleY(channel);
                        break;
                    } else if (inventory == condenser.getVesselInputSlot(channel)) {
                        helper.assertTrue(slot.x == 31 + channel * 50 && slot.y == 112,
                                "Essence condenser changed an authoritative vessel-input slot");
                        x = MachinePanelLayout.essenceCondenserVesselInputX(channel);
                        y = MachinePanelLayout.essenceCondenserVesselInputY(channel);
                        break;
                    } else if (inventory == condenser.getVesselOutputSlot(channel)) {
                        helper.assertTrue(slot.x == 31 + channel * 50 && slot.y == 132,
                                "Essence condenser changed an authoritative vessel-output slot");
                        x = MachinePanelLayout.essenceCondenserVesselOutputX(channel);
                        y = MachinePanelLayout.essenceCondenserVesselOutputY(channel);
                        break;
                    }
                }
            }
        }
        return new MachinePanelLayout.Box(x - 1, y - 1, 18, 18);
    }

    private static int width(String id) {
        if (id.equals("essence_condenser")) return MachinePanelLayout.ESSENCE_CONDENSER_WIDTH;
        if (!id.endsWith("_nature_altar_factory")) return 176;
        if (id.startsWith("ultimate_")) return 218;
        if (id.startsWith("absolute_")) return 256;
        if (id.startsWith("supreme_")) return 294;
        if (id.startsWith("cosmic_")) return 332;
        if (id.startsWith("infinite_")) return 370;
        return 184;
    }

    /** Independent ABI-role oracle; do not infer factory inputs from their coordinates. */
    private static boolean isFactoryRecipeInput(mekanism.api.inventory.IInventorySlot slot) {
        return slot instanceof FactoryInputInventorySlot || slot.getClass().getName()
                .equals("com.jerry.mekextras.common.inventory.slot.ExtraFactoryInputInventorySlot");
    }
}
