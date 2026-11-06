package com.lavagenesis.land.block;

import com.lavagenesis.land.LandBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Rules of island plant succession, shared by the soil, plant and sapling blocks. All cheap, random-tick driven. */
public final class IslandFlora {
	private IslandFlora() {}

	/** Bare rock or ash breaks down into soil only above sea level, dry and open to the sky. */
	public static boolean canWeather(ServerLevel level, BlockPos pos) {
		if (pos.getY() < level.getSeaLevel()) return false;
		BlockPos above = pos.above();
		BlockState up = level.getBlockState(above);
		return !up.isSolidRender() && up.getFluidState().isEmpty() && level.canSeeSky(above);
	}

	/** Soil can be (or stay) green when it is not under water and not buried under a full block. */
	public static boolean canStayLush(Level level, BlockPos pos) {
		if (pos.getY() < level.getSeaLevel() - 1) return false;
		BlockState up = level.getBlockState(pos.above());
		return !up.isSolidRender() && up.getFluidState().isEmpty();
	}

	/** A few random samples within 4 blocks - cheap and good enough at random-tick rates. */
	public static boolean nearWater(Level level, BlockPos pos, RandomSource random) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int i = 0; i < 6; i++) {
			m.setWithOffset(pos, random.nextInt(9) - 4, random.nextInt(3) - 1, random.nextInt(9) - 4);
			if (level.getFluidState(m).is(FluidTags.WATER)) return true;
		}
		return false;
	}

	public static boolean isIslandSoil(BlockState state) {
		return state.is(LandBlocks.LUSH_VOLCANIC_SOIL) || state.is(LandBlocks.VOLCANIC_SOIL) || state.is(BlockTags.DIRT);
	}

	/** Silverswords cling to raw cinder slopes as well as soil. */
	public static boolean isCinderOrSoil(BlockState state) {
		return isIslandSoil(state) || state.is(LandBlocks.FRESH_BASALT) || state.is(LandBlocks.VOLCANIC_ASH)
			|| state.is(LandBlocks.BLACK_SAND);
	}

	/** Palms grow on beaches. */
	public static boolean isBeachOrSoil(BlockState state) {
		return isIslandSoil(state) || state.is(BlockTags.SAND);
	}

	/**
	 * Picks a plant for this spot: silverswords on high slopes, palms on the coast, ferns, grass and ohia
	 * saplings elsewhere. Keeps plants from crowding and saplings from growing into a dense forest.
	 */
	public static void sprout(ServerLevel level, BlockPos at, RandomSource random, boolean bonemeal) {
		if (!bonemeal && countPlants(level, at) >= 4) return;
		int sea = level.getSeaLevel();
		int height = at.getY() - sea;
		float r = random.nextFloat();
		BlockState plant;
		if (height >= 26) {
			plant = r < 0.55F ? LandBlocks.SILVERSWORD.defaultBlockState()
				: r < 0.85F ? LandBlocks.HAPUU_FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState();
		} else if (height <= 5 && nearSand(level, at)) {
			plant = r < 0.30F ? LandBlocks.PALM_SAPLING.defaultBlockState()
				: r < 0.65F ? Blocks.SHORT_GRASS.defaultBlockState() : LandBlocks.HAPUU_FERN.defaultBlockState();
		} else {
			plant = r < 0.45F ? LandBlocks.HAPUU_FERN.defaultBlockState()
				: r < 0.72F ? Blocks.SHORT_GRASS.defaultBlockState()
				: r < 0.88F ? LandBlocks.OHIA_SAPLING.defaultBlockState()
				: height <= 10 ? LandBlocks.PALM_SAPLING.defaultBlockState()
				: LandBlocks.HAPUU_FERN.defaultBlockState();
		}
		if (bonemeal && plant.getBlock() instanceof IslandSaplingBlock && random.nextInt(3) != 0) {
			plant = LandBlocks.HAPUU_FERN.defaultBlockState();
		}
		if (plant.getBlock() instanceof IslandSaplingBlock && treeNearby(level, at)) {
			plant = LandBlocks.HAPUU_FERN.defaultBlockState();
		}
		if (plant.canSurvive(level, at)) level.setBlockAndUpdate(at, plant);
	}

	private static int countPlants(BlockGetter level, BlockPos at) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -1, -2), at.offset(2, 1, 2))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof IslandPlantBlock || s.getBlock() instanceof IslandSaplingBlock || s.is(Blocks.SHORT_GRASS)) n++;
		}
		return n;
	}

	private static boolean nearSand(BlockGetter level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-3, -2, -3), at.offset(3, 0, 3))) {
			if (level.getBlockState(p).is(BlockTags.SAND)) return true;
		}
		return false;
	}

	/** Leaves room between trees: no sapling within 4 blocks of a trunk. */
	private static boolean treeNearby(BlockGetter level, BlockPos at) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-4, -1, -4), at.offset(4, 3, 4))) {
			BlockState s = level.getBlockState(p);
			if (s.is(LandBlocks.OHIA_LOG) || s.is(LandBlocks.PALM_LOG) || s.getBlock() instanceof IslandSaplingBlock) return true;
		}
		return false;
	}
}
