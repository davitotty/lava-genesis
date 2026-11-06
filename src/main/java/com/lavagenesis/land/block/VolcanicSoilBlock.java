package com.lavagenesis.land.block;

import com.lavagenesis.land.LandBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Weathered lava: dark, crumbly and rich. With water nearby or rain falling on it, it turns into lush volcanic
 * soil; in a rainy biome it greens slowly even in dry weather.
 */
public class VolcanicSoilBlock extends Block {
	public VolcanicSoilBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!IslandFlora.canStayLush(level, pos)) return;
		boolean wet = level.isRainingAt(pos.above()) || IslandFlora.nearWater(level, pos, random);
		int chance = wet ? 6 : level.getBiome(pos).value().hasPrecipitation() ? 40 : 0;
		if (chance > 0 && random.nextInt(chance) == 0) {
			level.setBlockAndUpdate(pos, LandBlocks.LUSH_VOLCANIC_SOIL.defaultBlockState());
		}
	}
}
