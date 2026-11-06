package com.lavagenesis.land.block;

import com.lavagenesis.land.LandBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fresh basalt. Above sea level and open to the sky, wind and rain slowly break it down into volcanic soil
 * (on average about half an hour of random ticks), the first step of island plant succession.
 */
public class WeatheringBasaltBlock extends Block {
	private final int chance;

	public WeatheringBasaltBlock(int chance, Properties properties) {
		super(properties);
		this.chance = chance;
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(chance) != 0) return;
		if (IslandFlora.canWeather(level, pos)) {
			level.setBlockAndUpdate(pos, LandBlocks.VOLCANIC_SOIL.defaultBlockState());
		}
	}
}
