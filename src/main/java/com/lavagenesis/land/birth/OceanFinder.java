package com.lavagenesis.land.birth;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.BiomeResolver;
import org.jspecify.annotations.Nullable;

/**
 * Searches outwards from a point, ring by ring, for the middle of a deep ocean, using only the biome noise
 * (no chunks are loaded). Runs a bounded number of samples per call so it can be spread over ticks.
 */
final class OceanFinder {
	static final int STEP = 48;
	static final int MAX_RING = 56;          // ~2700 blocks out
	static final int MIN_SEPARATION = 320;   // between two players' birth volcanoes
	private static final int MARGIN = 44;    // deep ocean must reach this far around the centre

	private final BiomeResolver biomes;
	private final int originX, originZ, sampleY;
	private final List<BlockPos> avoid;
	private final Set<Long> rejected = new HashSet<>();
	private int ring, index;

	OceanFinder(ServerLevel level, BlockPos origin, List<BlockPos> avoid) {
		this.biomes = level.getChunkSource().getGenerator().getBiomeSource()
			.createCachingResolver(level.getChunkSource().randomState());
		this.originX = origin.getX();
		this.originZ = origin.getZ();
		this.sampleY = QuartPos.fromBlock(level.getSeaLevel() - 8);
		this.avoid = avoid;
	}

	boolean exhausted() {
		return ring > MAX_RING;
	}

	/** Marks a candidate as unusable (e.g. an ocean monument sits there) so the search continues past it. */
	void reject(BlockPos pos) {
		rejected.add(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
	}

	/** Tests up to {@code budget} candidate cells; returns a site (y = 0) or null if none found yet. */
	@Nullable BlockPos step(int budget) {
		for (int n = 0; n < budget && !exhausted(); n++) {
			int dx, dz;
			if (ring == 0) {
				dx = 0;
				dz = 0;
				ring = 1;
				index = 0;
			} else {
				int side = index / (2 * ring), t = index % (2 * ring);
				switch (side) {
					case 0 -> { dx = -ring + t; dz = -ring; }
					case 1 -> { dx = ring; dz = -ring + t; }
					case 2 -> { dx = ring - t; dz = ring; }
					default -> { dx = -ring; dz = ring - t; }
				}
				if (++index >= 8 * ring) {
					ring++;
					index = 0;
				}
			}
			int x = originX + dx * STEP, z = originZ + dz * STEP;
			if (good(x, z)) return new BlockPos(x, 0, z);
		}
		return null;
	}

	private boolean good(int x, int z) {
		if (rejected.contains(BlockPos.asLong(x, 0, z))) return false;
		for (BlockPos a : avoid) {
			long ddx = a.getX() - x, ddz = a.getZ() - z;
			if (ddx * ddx + ddz * ddz < (long) MIN_SEPARATION * MIN_SEPARATION) return false;
		}
		return deep(x, z) && deep(x + MARGIN, z) && deep(x - MARGIN, z) && deep(x, z + MARGIN) && deep(x, z - MARGIN);
	}

	private boolean deep(int x, int z) {
		return biomes.getNoiseBiome(QuartPos.fromBlock(x), sampleY, QuartPos.fromBlock(z)).is(BiomeTags.IS_DEEP_OCEAN);
	}
}
