package com.lavagenesis.land.block;

import com.lavagenesis.land.LandBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Loose grey volcanic ash: falls like sand, and above sea level weathers quickly into fertile volcanic soil. */
public class VolcanicAshBlock extends ColoredFallingBlock {
	public VolcanicAshBlock(ColorRGBA dust, Properties properties) {
		super(dust, properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(10) == 0 && IslandFlora.canWeather(level, pos)) {
			level.setBlockAndUpdate(pos, LandBlocks.VOLCANIC_SOIL.defaultBlockState());
		}
	}
}
