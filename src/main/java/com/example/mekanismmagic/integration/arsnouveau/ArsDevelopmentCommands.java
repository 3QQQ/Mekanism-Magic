package com.example.mekanismmagic.integration.arsnouveau;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

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
                                                context, "pos"))))));
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
}
