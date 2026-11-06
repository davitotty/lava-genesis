package com.lavagenesis.land.block;

import com.lavagenesis.land.LandBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The green end of island succession: mossy, fern-covered volcanic soil. It spreads to nearby volcanic soil,
 * sprouts island plants (ferns, silverswords up high, ohia and palm saplings) and dies back to volcanic soil when
 * covered. Bone meal makes plants spring up around it.
 */
public class LushVolcanicSoilBlock extends Block implements BonemealableBlock {
	public LushVolcanicSoilBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!IslandFlora.canStayLush(level, pos)) {
			level.setBlockAndUpdate(pos, LandBlocks.VOLCANIC_SOIL.defaultBlockState());
			return;
		}
		if (level.getMaxLocalRawBrightness(pos.above()) < 9) return;
		if (random.nextInt(3) == 0) {
			BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(4) - 2, random.nextInt(3) - 1);
			if (level.getBlockState(target).is(LandBlocks.VOLCANIC_SOIL) && IslandFlora.canStayLush(level, target)) {
				level.setBlockAndUpdate(target, defaultBlockState());
			}
		}
		if (random.nextInt(14) == 0 && level.getBlockState(pos.above()).isAir()) {
			IslandFlora.sprout(level, pos.above(), random, false);
		}
	}

	@Override
	public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
		return level.getBlockState(pos.above()).isAir();
	}

	@Override
	public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		return true;
	}

	@Override
	public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
		for (int i = 0; i < 12; i++) {
			BlockPos ground = pos.offset(random.nextInt(7) - 3, random.nextInt(3) - 1, random.nextInt(7) - 3);
			BlockState g = level.getBlockState(ground);
			if (g.is(LandBlocks.VOLCANIC_SOIL) && IslandFlora.canStayLush(level, ground)) {
				level.setBlockAndUpdate(ground, defaultBlockState());
				g = level.getBlockState(ground);
			}
			if (g.is(this) && level.getBlockState(ground.above()).isAir()) {
				IslandFlora.sprout(level, ground.above(), random, true);
			}
		}
	}
}
