package com.example.mekanismmagic.integration.mekextras;

import com.jerry.mekextras.common.block.prefab.BlockExtraFactoryMachine;
import com.jerry.mekextras.common.content.blocktype.ExtraFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Uses the Ars factory renderer while suppressing all random work particles. */
public final class ArsExtraFactoryBlock extends
        BlockExtraFactoryMachine.BlockExtraFactory<ExtraImbuementFactoryBlockEntity> {
    public ArsExtraFactoryBlock(
            ExtraFactory<ExtraImbuementFactoryBlockEntity> type) {
        super(type);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
            RandomSource random) {
        // The animated model carries the work state; world particles are disabled.
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level,
            BlockPos pos, CollisionContext context) {
        // ExtraFactoryBuilder copies Mekanism's segmented factory shape.
        // It is useful for collision but produces a noisy selection outline.
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state,
            BlockGetter level, BlockPos pos, CollisionContext context) {
        // Preserve the detailed collision shape while selection stays simple.
        return super.getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state,
            BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }
}
