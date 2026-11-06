package com.lavagenesis.land.block;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A small island plant (hapu'u fern, silversword) that grows on the soils named by {@code soil}. */
public class IslandPlantBlock extends VegetationBlock {
	private final VoxelShape shape;
	private final Predicate<BlockState> soil;

	public IslandPlantBlock(VoxelShape shape, Predicate<BlockState> soil, Properties properties) {
		super(properties);
		this.shape = shape;
		this.soil = soil;
	}

	@Override
	protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
		return soil.test(state);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return shape;
	}
}
