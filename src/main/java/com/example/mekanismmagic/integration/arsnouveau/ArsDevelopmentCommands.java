package com.example.mekanismmagic.integration.arsnouveau;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/**
 * Development-only commands for validating optional Ars machine content.
 */
final class ArsDevelopmentCommands {
    private ArsDevelopmentCommands() {
    }

    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher =
                event.getDispatcher();
        dispatcher.register(Commands.literal("mekanism_magic")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("drygmy_test")
                        .then(Commands.argument("pos",
                                        BlockPosArgument.blockPos())
                                .then(Commands.argument("entity",
                                                ResourceLocationArgument.id())
                                        .executes(context -> seedDrygmy(
                                                context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(
                                                        context, "pos"),
                                                ResourceLocationArgument.getId(
                                                        context,
                                                        "entity"))))))
                .then(Commands.literal("drygmy_status")
                        .then(Commands.argument("pos",
                                        BlockPosArgument.blockPos())
                                .executes(context -> drygmyStatus(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(
                                                context, "pos")))))
                .then(Commands.literal("source_amplifier_test")
                        .then(Commands.argument("pos",
                                        BlockPosArgument.blockPos())
                                .executes(context -> sourceAmplifierTest(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(
                                                context, "pos")))))
                .then(Commands.literal("imbuement_test")
                        .then(Commands.argument("pos",
                                        BlockPosArgument.blockPos())
                                .executes(context -> imbuementTest(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(
                                                context, "pos"), false))))
                .then(Commands.literal("imbuement_electric_test")
                        .then(Commands.argument("pos",
                                        BlockPosArgument.blockPos())
                                .executes(context -> imbuementTest(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(
                                                context, "pos"), true))))
                .then(Commands.literal("apparatus_test")
                        .then(Commands.argument("pos",
                                        BlockPosArgument.blockPos())
                                .executes(context -> apparatusTest(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(
                                                context, "pos")))))
                .then(Commands.literal("ars_status")
                        .then(Commands.argument("pos",
                                        BlockPosArgument.blockPos())
                                .executes(context -> arsStatus(
                                        context.getSource(),
                                        BlockPosArgument.getLoadedBlockPos(
                                                context, "pos")))))
                .then(Commands.literal("catalyst_identifier")
                        .then(Commands.argument("recipe",
                                        ResourceLocationArgument.id())
                                .executes(context -> giveCatalystIdentifier(
                                        context.getSource(),
                                        ResourceLocationArgument.getId(
                                                context, "recipe")))))
                .then(Commands.literal("catalyst_identifier_all")
                        .executes(context -> giveAllCatalystIdentifiers(
                                context.getSource())))
                .then(Commands.literal("catalyst_assembler_test")
                        .then(Commands.argument("pos",
                                        BlockPosArgument.blockPos())
                                .then(Commands.argument("recipe",
                                                ResourceLocationArgument.id())
                                        .executes(context ->
                                                catalystAssemblerTest(
                                                        context.getSource(),
                                                        BlockPosArgument
                                                                .getLoadedBlockPos(
                                                                        context,
                                                                        "pos"),
                                                        ResourceLocationArgument
                                                                .getId(context,
                                                                        "recipe")))))));
    }

    private static int seedDrygmy(CommandSourceStack source,
                                  BlockPos position,
                                  ResourceLocation entityId) {
        if (!(source.getLevel().getBlockEntity(position)
                instanceof DrygmySimulatorBlockEntity simulator)) {
            source.sendFailure(Component.literal(
                    "Target is not a Drygmy simulator"));
            return 0;
        }
        int prepared = simulator.seedDevelopmentTest(
                source.getLevel(), entityId);
        if (prepared <= 0) {
            source.sendFailure(Component.literal(
                    "Entity produced no valid Drygmy loot"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Prepared " + prepared
                        + " Drygmy output items for " + entityId), false);
        return prepared;
    }

    private static int drygmyStatus(CommandSourceStack source,
                                    BlockPos position) {
        if (!(source.getLevel().getBlockEntity(position)
                instanceof DrygmySimulatorBlockEntity simulator)) {
            source.sendFailure(Component.literal(
                    "Target is not a Drygmy simulator"));
            return 0;
        }
        int output = simulator.developmentOutputCount();
        source.sendSuccess(() -> Component.literal(
                "Drygmy outputs=" + output
                        + ", source=" + simulator.getSource()
                        + ", progress=" + simulator.getProgress()
                        + "/" + simulator.getProgressRequired()), false);
        return output;
    }

    private static int sourceAmplifierTest(
            CommandSourceStack source, BlockPos position) {
        if (!(source.getLevel().getBlockEntity(position)
                instanceof SourceAmplifierBlockEntity amplifier)
                || !amplifier.seedDevelopmentTest()) {
            source.sendFailure(Component.literal(
                    "No Source Amplifier or vanilla sourcelink in range"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Source Amplifier test prepared"), false);
        return 1;
    }

    private static int imbuementTest(
            CommandSourceStack source, BlockPos position,
            boolean electric) {
        if (!(source.getLevel().getBlockEntity(position)
                instanceof ImbuementProcessorBlockEntity processor)) {
            source.sendFailure(Component.literal(
                    "Target is not an Imbuement Processor"));
            return 0;
        }
        processor.seedDevelopmentTest(electric);
        source.sendSuccess(() -> Component.literal(
                electric
                        ? "Electric imbuement test prepared"
                        : "Source imbuement test prepared"), false);
        return 1;
    }

    private static int apparatusTest(
            CommandSourceStack source, BlockPos position) {
        if (!(source.getLevel().getBlockEntity(position)
                instanceof EnchantingApparatusProcessorBlockEntity
                processor)) {
            source.sendFailure(Component.literal(
                    "Target is not an Apparatus Processor"));
            return 0;
        }
        processor.seedDevelopmentTest();
        source.sendSuccess(() -> Component.literal(
                "Apparatus test prepared"), false);
        return 1;
    }

    private static int arsStatus(CommandSourceStack source,
                                 BlockPos position) {
        if (!(source.getLevel().getBlockEntity(position)
                instanceof ArsSourceMachineBlockEntity machine)) {
            source.sendFailure(Component.literal(
                    "Target is not an Ars Source machine"));
            return 0;
        }
        String output = machine.getNativeOutputSlot() == null
                ? "none"
                : machine.getNativeOutputSlot().getStack().toString();
        source.sendSuccess(() -> Component.literal(
                "output=" + output
                        + ", source=" + machine.getSource()
                        + ", progress=" + machine.getProgress()
                        + "/" + machine.getProgressRequired()), false);
        return machine.getProgress();
    }

    private static int giveCatalystIdentifier(
            CommandSourceStack source, ResourceLocation recipeId) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(
                    "Only players can receive catalyst identifiers"));
            return 0;
        }
        ItemStack identifier = ArsNouveauRecipeBridge
                .createIdentifierForRecipe(source.getLevel(), recipeId);
        if (!identifier.is(ArsNouveauRegistries.CATALYST_IDENTIFIER_ITEM.get())
                || !identifier.has(
                net.minecraft.core.component.DataComponents.CUSTOM_DATA)) {
            source.sendFailure(Component.literal(
                    "Recipe has no valid catalyst identifier"));
            return 0;
        }
        if (!player.getInventory().add(identifier)) {
            player.drop(identifier, false);
        }
        source.sendSuccess(() -> Component.literal(
                "Generated catalyst identifier for " + recipeId), false);
        return 1;
    }

    private static int giveAllCatalystIdentifiers(
            CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal(
                    "Only players can receive catalyst identifiers"));
            return 0;
        }
        List<ItemStack> identifiers =
                ArsNouveauRecipeBridge.catalystIdentifierJeiStacks(
                        source.getLevel());
        for (ItemStack identifier : identifiers) {
            if (!player.getInventory().add(identifier.copy())) {
                player.drop(identifier.copy(), false);
            }
        }
        int count = identifiers.size();
        source.sendSuccess(() -> Component.literal(
                "Generated " + count + " catalyst identifiers"), false);
        return count;
    }

    private static int catalystAssemblerTest(
            CommandSourceStack source, BlockPos position,
            ResourceLocation recipeId) {
        if (!(source.getLevel().getBlockEntity(position)
                instanceof CatalystIdentifierAssemblerBlockEntity assembler)) {
            source.sendFailure(Component.literal(
                    "Target is not a Catalyst Identifier Assembler"));
            return 0;
        }
        if (assembler.seedDevelopmentTest(recipeId) <= 0) {
            source.sendFailure(Component.literal(
                    "Recipe not found or has no three catalyst ingredients"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Catalyst identifier test prepared for " + recipeId), false);
        return 1;
    }
}
