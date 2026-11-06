package com.lavagenesis.land.birth;

import com.lavagenesis.land.LandBlocks;
import com.lavagenesis.land.block.SulfurClusterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Works out the blocks of a birth volcano: a submerged cone on the sea floor (pillow lava flanks, basalt, tuff,
 * olivine around the base, a shallow crater glowing with molten rock, fumaroles on the slopes), a lava-filled magma
 * chamber with glowing molten-rock and magma walls, the home vent on the chamber floor and a plugged vent pipe up
 * to the crater that the player melts open later. Also the artificial ocean basin used when no deep ocean is found.
 */
final class VolcanoBuilder {
	private VolcanoBuilder() {}

	static final int BASE_RADIUS = 23;
	private static final double RIM_D = 5.5;
	private static final int CRATER_DEPTH = 3;
	private static final double FLANK_EXP = 1.45;
	private static final int APRON = 9;

	/** The result: the blocks plus where the vent goes and where the player is placed. */
	record Volcano(BlockPlan plan, BlockPos vent, Vec3 spawn) {}

	static Volcano volcano(ServerLevel level, BlockPos center, long seed) {
		BlockPlan plan = new BlockPlan();
		int cx = center.getX(), cz = center.getZ();
		int sea = level.getSeaLevel();
		int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, cx, cz); // first water block above the sea floor
		int rimY = Math.min(floor + 25, sea - 5);
		double height = (rimY - (floor - 1)) / Math.pow(1 - RIM_D / BASE_RADIUS, FLANK_EXP);

		int span = BASE_RADIUS + APRON;
		int size = span * 2 + 1;
		int[] tops = new int[size * size];
		java.util.Arrays.fill(tops, Integer.MIN_VALUE);

		// ---- the cone and the sea floor around it ----
		for (int dx = -span; dx <= span; dx++) {
			for (int dz = -span; dz <= span; dz++) {
				double d = Math.sqrt(dx * dx + dz * dz);
				if (d > span) continue;
				int x = cx + dx, z = cz + dz;
				int floorTop = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
				double dEff = d * (1 + 0.11 * Noise.value2(x / 9.0, z / 9.0, seed));
				int topY = Integer.MIN_VALUE;
				if (dEff <= BASE_RADIUS) {
					double top = dEff < RIM_D
						? rimY - CRATER_DEPTH * (1 - (dEff / RIM_D) * (dEff / RIM_D))
						: (floor - 1) + height * Math.pow(1 - dEff / BASE_RADIUS, FLANK_EXP);
					top += Noise.value2(x / 3.5, z / 3.5, seed + 7) * 0.9;
					topY = Math.min(Mth.floor(top), sea - 3);
				}
				if (topY > floorTop) {
					tops[(dx + span) * size + dz + span] = topY;
					for (int y = floorTop; y <= topY; y++) plan.put(x, y, z, cone(x, y, z, topY, dEff, seed));
					clearWaterPlants(level, plan, x, z, topY + 1, sea);
				} else if (d > BASE_RADIUS - 3) {
					seaFloor(level, plan, x, floorTop, z, seed);
				}
			}
		}

		// guaranteed olivine ring on the lower flanks, so the Seamount offering is always reachable
		for (int k = 0; k < 10; k++) {
			double a = k * Math.PI / 5 + Noise.hash01(k, 1, 2, seed) * 0.4;
			int dx = (int) Math.round(Math.cos(a) * (BASE_RADIUS - 4)), dz = (int) Math.round(Math.sin(a) * (BASE_RADIUS - 4));
			int t = tops[(dx + span) * size + dz + span];
			if (t != Integer.MIN_VALUE) plan.put(cx + dx, t, cz + dz, LandBlocks.OLIVINE_ORE.defaultBlockState());
		}

		// ---- the magma chamber ----
		int cy = floor + 2;
		double rx = 8.0, ry = 5.5;
		// a pool in the lower part of the chamber (~220 sources), a dark glowing cavern above it
		int poolTop = cy - 2;
		BlockState lava = Blocks.LAVA.defaultBlockState();
		for (int dx = -11; dx <= 11; dx++) {
			for (int dz = -11; dz <= 11; dz++) {
				int colTop = tops[(dx + span) * size + dz + span];
				for (int dy = -9; dy <= 9; dy++) {
					int x = cx + dx, y = cy + dy, z = cz + dz;
					double e = (dx / rx) * (dx / rx) + (dy / ry) * (dy / ry) + (dz / rx) * (dz / rx);
					e *= 1 - 0.16 * Noise.value3(x / 4.0, y / 4.0, z / 4.0, seed + 3);
					if (e >= 1.5) continue;
					// never break out of the cone: keep at least two blocks of rock below its surface
					boolean sealed = colTop == Integer.MIN_VALUE ? y < floor - 2 : y < colTop - 1;
					if (e < 1.0 && sealed) {
						plan.put(x, y, z, y <= poolTop ? lava : Blocks.CAVE_AIR.defaultBlockState());
					} else {
						plan.put(x, y, z, chamberWall(x, y, z, e, seed));
					}
				}
			}
		}

		// home vent on the chamber floor, on a glowing hearth of molten rock
		int ventY = poolTop;
		while (ventY > cy - 9 && lava.equals(plan.get(cx, ventY - 1, cz))) ventY--;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
				plan.put(cx + dx, ventY - 1, cz + dz, LandBlocks.MOLTEN_ROCK.defaultBlockState());
			}
		}
		BlockPos vent = new BlockPos(cx, ventY, cz);

		// ---- the plugged vent pipe from the chamber roof to the crater floor ----
		int roof = cy;
		while (roof < cy + 9 && isOpen(plan.get(cx, roof + 1, cz))) roof++;
		int craterFloor = tops[span * size + span];
		if (craterFloor != Integer.MIN_VALUE) {
			for (int y = roof + 1; y < craterFloor; y++) {
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						int r2 = dx * dx + dz * dz, x = cx + dx, z = cz + dz;
						if (r2 <= 2) {
							boolean pocket = dx == 0 && dz == 0 && (y - roof) % 4 == 2 && y < craterFloor - 1;
							plan.put(x, y, z, pocket ? lava : LandBlocks.FRESH_BASALT.defaultBlockState());
						} else if (r2 <= 5) {
							double r = Noise.hash01(x, y, z, seed + 31);
							plan.put(x, y, z, r < 0.35 ? LandBlocks.MOLTEN_ROCK.defaultBlockState()
								: r < 0.5 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
						}
					}
				}
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) plan.put(cx + dx, craterFloor, cz + dz, LandBlocks.MOLTEN_ROCK.defaultBlockState());
			}
		}

		// ---- fumaroles steaming on the flanks ----
		for (int k = 0; k < 4; k++) {
			double a = k * Math.PI / 2 + 0.6 + Noise.hash01(k, 5, 9, seed) * 0.9;
			double dist = 10 + Noise.hash01(k, 6, 9, seed) * 5;
			int dx = (int) Math.round(Math.cos(a) * dist), dz = (int) Math.round(Math.sin(a) * dist);
			int t = tops[(dx + span) * size + dz + span];
			if (t == Integer.MIN_VALUE) continue;
			plan.put(cx + dx, t, cz + dz, LandBlocks.FUMAROLE.defaultBlockState());
			if (k % 2 == 0) {
				plan.put(cx + dx, t + 1, cz + dz, LandBlocks.SULFUR_CLUSTER.defaultBlockState()
					.setValue(SulfurClusterBlock.AGE, 1).setValue(SulfurClusterBlock.WATERLOGGED, true));
			}
		}

		Vec3 spawn = new Vec3(cx + 0.5, ventY + 1.05, cz + 0.5);
		return new Volcano(plan, vent, spawn);
	}

	private static boolean isOpen(BlockState s) {
		return s != null && (s.is(Blocks.LAVA) || s.is(Blocks.CAVE_AIR));
	}

	/** Cone rock: pillow lava on the lower flanks, basalt and tuff higher up, molten rock glowing in the crater. */
	private static BlockState cone(int x, int y, int z, int topY, double dEff, long seed) {
		double r = Noise.hash01(x, y, z, seed + 11);
		if (y == topY) {
			if (dEff < RIM_D - 1.0) {
				return r < 0.55 ? LandBlocks.MOLTEN_ROCK.defaultBlockState()
					: r < 0.7 ? LandBlocks.GLASSY_BASALT.defaultBlockState() : LandBlocks.FRESH_BASALT.defaultBlockState();
			}
			double u = dEff / BASE_RADIUS;
			if (u > 0.72) {
				if (r < 0.015) return LandBlocks.OLIVINE_ORE.defaultBlockState();
				return r < 0.62 ? LandBlocks.PILLOW_LAVA.defaultBlockState()
					: r < 0.82 ? LandBlocks.FRESH_BASALT.defaultBlockState()
					: r < 0.92 ? Blocks.TUFF.defaultBlockState() : Blocks.BASALT.defaultBlockState();
			}
			if (r < 0.03) return LandBlocks.MOLTEN_ROCK.defaultBlockState();
			if (r < 0.05) return LandBlocks.GLASSY_BASALT.defaultBlockState();
			return r < 0.55 ? LandBlocks.FRESH_BASALT.defaultBlockState()
				: r < 0.72 ? LandBlocks.PILLOW_LAVA.defaultBlockState()
				: r < 0.82 ? Blocks.BASALT.defaultBlockState()
				: r < 0.91 ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState();
		}
		return r < 0.55 ? LandBlocks.FRESH_BASALT.defaultBlockState()
			: r < 0.70 ? LandBlocks.PILLOW_LAVA.defaultBlockState()
			: r < 0.82 ? Blocks.BASALT.defaultBlockState()
			: r < 0.92 ? Blocks.TUFF.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
	}

	/** Chamber walls: a glowing mix of molten rock and magma with a few lava pockets to absorb. */
	private static BlockState chamberWall(int x, int y, int z, double e, long seed) {
		double r = Noise.hash01(x, y, z, seed + 17);
		if (r < 0.12) return LandBlocks.MOLTEN_ROCK.defaultBlockState();
		if (r < 0.22) return Blocks.MAGMA_BLOCK.defaultBlockState();
		if (r < 0.45) return Blocks.BLACKSTONE.defaultBlockState();
		if (r < 0.75) return LandBlocks.FRESH_BASALT.defaultBlockState();
		if (r < 0.95 || e > 1.25) return Blocks.BASALT.defaultBlockState();
		return Blocks.LAVA.defaultBlockState();
	}

	/** The apron around the cone: black sand, scattered olivine, pillow lava lumps and rare glowing seeps. */
	private static void seaFloor(ServerLevel level, BlockPlan plan, int x, int floorTop, int z, long seed) {
		double r = Noise.hash01(x, 0, z, seed + 21);
		if (r < 0.012) plan.put(x, floorTop, z, LandBlocks.OLIVINE_ORE.defaultBlockState());
		else if (r < 0.018) plan.put(x, floorTop, z, LandBlocks.MOLTEN_ROCK.defaultBlockState());
		else if (r < 0.2) plan.put(x, floorTop, z, LandBlocks.BLACK_SAND.defaultBlockState());
		else if (r < 0.26 && level.getFluidState(new BlockPos(x, floorTop + 1, z)).is(FluidTags.WATER)) {
			plan.put(x, floorTop + 1, z, LandBlocks.PILLOW_LAVA.defaultBlockState());
		}
	}

	/** Kelp and seagrass would float once the floor under them turns into the cone: replace them with water. */
	private static void clearWaterPlants(ServerLevel level, BlockPlan plan, int x, int z, int fromY, int sea) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = fromY; y < sea; y++) {
			BlockState s = level.getBlockState(m.set(x, y, z));
			if (!s.is(Blocks.WATER) && s.getFluidState().is(FluidTags.WATER)) plan.put(x, y, z, Blocks.WATER.defaultBlockState());
		}
	}

	/**
	 * An artificial ocean basin for worlds without a deep ocean nearby: a round bowl about 30 blocks deep filled
	 * with sea water, ringed by a black sand shore.
	 */
	static BlockPlan basin(ServerLevel level, BlockPos center, long seed) {
		BlockPlan plan = new BlockPlan();
		int sea = level.getSeaLevel();
		int target = Math.max(level.getMinY() + 12, sea - 30);
		int radius = 34, shore = 7;
		BlockState water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		for (int dx = -radius - shore; dx <= radius + shore; dx++) {
			for (int dz = -radius - shore; dz <= radius + shore; dz++) {
				int x = center.getX() + dx, z = center.getZ() + dz;
				double d = Math.sqrt(dx * dx + dz * dz) * (1 + 0.08 * Noise.value2(x / 10.0, z / 10.0, seed));
				if (d > radius + shore) continue;
				int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
				double r = Noise.hash01(x, 1, z, seed + 41);
				if (d <= radius) {
					int floorY = target + (int) ((d / radius) * (d / radius) * (sea - 3 - target));
					for (int y = floorY; y <= Math.max(surface, sea - 1); y++) plan.put(x, y, z, y < sea ? water : air);
					plan.put(x, floorY - 1, z, r < 0.45 ? Blocks.GRAVEL.defaultBlockState()
						: r < 0.75 ? LandBlocks.BLACK_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState());
					for (int y = floorY - 4; y < floorY - 1; y++) plan.put(x, y, z, Blocks.STONE.defaultBlockState());
				} else {
					int beach = sea + (int) ((d - radius) * 0.8);
					for (int y = beach + 1; y <= surface; y++) plan.put(x, y, z, air);
					for (int y = sea - 4; y <= beach; y++) {
						plan.put(x, y, z, y >= beach - 1 ? LandBlocks.BLACK_SAND.defaultBlockState() : Blocks.STONE.defaultBlockState());
					}
				}
			}
		}
		return plan;
	}

	/** Tiny deterministic hash noise, enough for natural-looking lumps without pulling in a noise library. */
	static final class Noise {
		private Noise() {}

		static double hash01(int x, int y, int z, long seed) {
			long h = seed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L;
			h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
			h = (h ^ (h >>> 29)) * 0x94D049BB133111EBL;
			h ^= h >>> 32;
			return (h >>> 11) * 0x1.0p-53;
		}

		private static double smooth(double t) {
			return t * t * (3 - 2 * t);
		}

		/** Value noise in -1..1. */
		static double value2(double x, double z, long seed) {
			int x0 = Mth.floor(x), z0 = Mth.floor(z);
			double fx = smooth(x - x0), fz = smooth(z - z0);
			double a = hash01(x0, 0, z0, seed), b = hash01(x0 + 1, 0, z0, seed);
			double c = hash01(x0, 0, z0 + 1, seed), d = hash01(x0 + 1, 0, z0 + 1, seed);
			return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d)) * 2 - 1;
		}

		static double value3(double x, double y, double z, long seed) {
			int x0 = Mth.floor(x), y0 = Mth.floor(y), z0 = Mth.floor(z);
			double fx = smooth(x - x0), fy = smooth(y - y0), fz = smooth(z - z0);
			double v0 = Mth.lerp(fz,
				Mth.lerp(fx, hash01(x0, y0, z0, seed), hash01(x0 + 1, y0, z0, seed)),
				Mth.lerp(fx, hash01(x0, y0, z0 + 1, seed), hash01(x0 + 1, y0, z0 + 1, seed)));
			double v1 = Mth.lerp(fz,
				Mth.lerp(fx, hash01(x0, y0 + 1, z0, seed), hash01(x0 + 1, y0 + 1, z0, seed)),
				Mth.lerp(fx, hash01(x0, y0 + 1, z0 + 1, seed), hash01(x0 + 1, y0 + 1, z0 + 1, seed)));
			return Mth.lerp(fy, v0, v1) * 2 - 1;
		}
	}
}
