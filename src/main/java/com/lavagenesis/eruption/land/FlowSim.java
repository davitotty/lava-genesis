package com.lavagenesis.eruption.land;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Plans where an outpouring of lava ends up, block by block, without touching the world.
 * <p>
 * Lava is poured at an origin column and always settles into the reachable column with the lowest
 * "flow height" {@code g(h) + distance + noise}, where {@code g} turns height into distance through the flank
 * slope (steep below sea level where lava quenches into pillows, gentle above it where it runs as pahoehoe).
 * The result follows the terrain downhill, fills hollows and shallow sea first and piles up into a shield-shaped
 * cone around the vent; repeated outpourings at one place keep raising it. The placements come out in
 * fill order, so the placer shows the flow spreading and rising.
 */
public final class FlowSim {
	private FlowSim() {}

	public enum Style { FLOW, PILLOW }

	/** Tunables for one outpouring. */
	public static final class Params {
		int volume = 40;
		int maxRadius = 8;
		double slopeBelow = 1.0;
		double slopeAbove = 0.5;
		int capY = Integer.MAX_VALUE;   // exclusive: never place at or above this y
		int stopY = Integer.MAX_VALUE;  // stop once the origin column's top reaches this y
		double noise = 1.0;
		int crater = -1;                // radius of a crater with molten rock at the summit, -1 = none
		boolean shore = true;           // black sand where the new land meets the sea
		Style style = Style.FLOW;

		public static Params of(int volume, int maxRadius) {
			Params p = new Params();
			p.volume = volume;
			p.maxRadius = maxRadius;
			return p;
		}

		public Params slopes(double below, double above) { slopeBelow = below; slopeAbove = above; return this; }
		public Params cap(int y) { capY = y; return this; }
		public Params stopAt(int y) { stopY = y; return this; }
		public Params noise(double n) { noise = n; return this; }
		public Params crater(int r) { crater = r; return this; }
		public Params shore(boolean b) { shore = b; return this; }
		public Params style(Style s) { style = s; return this; }
	}

	/** One planned block. */
	public record Placement(long pos, BlockState state) {}

	/** The plan: blocks in fill order, and the final top y of the origin column (or {@code Integer.MIN_VALUE}). */
	public record Result(List<Placement> placements, int originX, int originZ, int originTop) {
		public boolean isEmpty() { return placements.isEmpty(); }
		/** Final top y of the origin column, or {@code fallback} if nothing was built there. */
		public int topOr(int fallback) { return originTop == NO ? fallback : originTop; }
	}

	private static final int NO = Integer.MIN_VALUE;
	private static final int MAX_DROP = 96;

	private static final class Col {
		final int x, z;
		final double dist, noise;
		final int startH;
		int h;              // first free y (where the next block goes)
		boolean capped;     // ceiling / cap / protected: cannot rise any more
		Col(int x, int z, int h, double dist, double noise) {
			this.x = x; this.z = z; this.h = h; this.startH = h; this.dist = dist; this.noise = noise;
		}
		int top() { return h - 1; }
		boolean grew() { return h > startH; }
	}

	private record Entry(double key, Col col, int h) {}

	public static Result run(ServerLevel level, BlockPos origin, Params p, long seed) {
		int sea = level.getSeaLevel();
		int ox = origin.getX(), oz = origin.getZ();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		Long2ObjectOpenHashMap<Col> cols = new Long2ObjectOpenHashMap<>();
		List<int[]> vents = new ArrayList<>(); // {x, y, z}
		List<Placement> out = new ArrayList<>();
		List<Col> orderCol = new ArrayList<>();

		int h0 = surface(level, m, ox, oz, origin.getY(), 2);
		if (h0 == NO) return new Result(List.of(), ox, oz, NO);
		Col start = new Col(ox, oz, h0, 0.0, 0.0);
		cols.put(colKey(ox, oz), start);
		scanVent(level, m, start, vents);

		PriorityQueue<Entry> pq = new PriorityQueue<>((a, b) -> Double.compare(a.key, b.key));
		pq.add(new Entry(key(start, start.h, sea, p), start, start.h));
		int placed = 0;
		int r2 = p.maxRadius * p.maxRadius;
		int minY = level.getMinY();
		int maxY = level.getMaxY();

		while (placed < p.volume && !pq.isEmpty()) {
			Entry e = pq.poll();
			Col c = e.col;
			if (c.capped || e.h != c.h) continue;
			int y = c.h;
			if (y >= p.capY || y >= maxY || y <= minY || protectedAt(vents, c.x, y, c.z)) { c.capped = true; continue; }
			m.set(c.x, y, c.z);
			if (!Palette.isFree(level, m, level.getBlockState(m))) { c.capped = true; continue; }
			out.add(new Placement(m.asLong(), null));
			orderCol.add(c);
			c.h++;
			placed++;
			if (c == start && c.h > p.stopY) break;
			pq.add(new Entry(key(c, c.h, sea, p), c, c.h));
			// the new top block lets lava run sideways at its level
			for (int i = 0; i < 4; i++) {
				int nx = c.x + (i == 0 ? 1 : i == 1 ? -1 : 0);
				int nz = c.z + (i == 2 ? 1 : i == 3 ? -1 : 0);
				int dx = nx - ox, dz = nz - oz;
				if (dx * dx + dz * dz > r2) continue;
				long k = colKey(nx, nz);
				Col n = cols.get(k);
				if (n == null) {
					int nh = surface(level, m, nx, nz, y, 1);
					if (nh == NO) continue; // a wall for now; tried again when this column rises
					n = new Col(nx, nz, nh, Math.sqrt(dx * dx + dz * dz), valueNoise(nx, nz, seed) * p.noise);
					cols.put(k, n);
					scanVent(level, m, n, vents);
					pq.add(new Entry(key(n, n.h, sea, p), n, n.h));
				}
			}
		}

		// ---- materials ----
		BlockState cooling = Palette.coolingLava();
		BlockState basalt = Palette.freshBasalt();
		BlockState sand = Palette.blackSand();
		BlockState molten = Palette.moltenRock();
		LongOpenHashSet skip = new LongOpenHashSet();

		// crater: lower the summit by a block and fill the bowl with molten rock
		if (p.crater >= 0 && start.grew()) {
			for (Col c : cols.values()) {
				if (!c.grew() || c.dist > p.crater + 0.01) continue;
				if (c.h - c.startH >= 3) {
					skip.add(BlockPos.asLong(c.x, c.top(), c.z));
					c.h--;
				}
			}
		}

		List<Placement> result = new ArrayList<>(out.size());
		for (int i = 0; i < out.size(); i++) {
			Placement pl = out.get(i);
			if (skip.contains(pl.pos)) continue;
			Col c = orderCol.get(i);
			int x = BlockPos.getX(pl.pos), y = BlockPos.getY(pl.pos), z = BlockPos.getZ(pl.pos);
			if (ventGuard(vents, x, y, z)) continue;
			int depth = c.top() - y;
			BlockState s;
			if (p.style == Style.PILLOW) {
				s = depth >= 3 ? basalt : (blob(x, y, z, seed) > 0.05 ? cooling : basalt);
			} else {
				s = depth >= 2 ? basalt : cooling;
				if (depth == 0 && p.crater >= 0 && c.dist <= p.crater + 0.01 && start.grew()) {
					s = molten;
				} else if (depth == 0 && p.shore && (y == sea - 1 || y == sea) && isShore(cols, c, sea)
					&& hash(x, z, seed ^ 0x5EED) % 10 < 7) {
					s = sand;
				}
			}
			result.add(new Placement(pl.pos, s));
		}
		return new Result(result, ox, oz, start.grew() ? start.top() : NO);
	}

	/** Flow height: lower settles first. */
	private static double key(Col c, int h, int sea, Params p) {
		double g = h <= sea ? h / p.slopeBelow : sea / p.slopeBelow + (h - sea) / p.slopeAbove;
		return g + c.dist + c.noise;
	}

	/**
	 * First free y of column (x, z) seen from {@code refY}: falls down through air/water to the floor, or climbs
	 * at most {@code climb} blocks if refY is inside rock. NO if unloaded or blocked.
	 */
	static int surface(ServerLevel level, BlockPos.MutableBlockPos m, int x, int z, int refY, int climb) {
		m.set(x, refY, z);
		if (!level.isLoaded(m)) return NO;
		int minY = level.getMinY();
		if (Palette.isFree(level, m, level.getBlockState(m))) {
			for (int y = refY; y > refY - MAX_DROP && y > minY; y--) {
				m.set(x, y - 1, z);
				if (!Palette.isFree(level, m, level.getBlockState(m))) return y;
			}
			return NO;
		}
		for (int up = 1; up <= climb; up++) {
			m.set(x, refY + up, z);
			if (Palette.isFree(level, m, level.getBlockState(m))) return refY + up;
		}
		return NO;
	}

	private static void scanVent(ServerLevel level, BlockPos.MutableBlockPos m, Col c, List<int[]> vents) {
		for (int dy = 1; dy <= 3; dy++) {
			m.set(c.x, c.h - dy, c.z);
			if (Palette.isVent(level.getBlockState(m))) {
				vents.add(new int[]{c.x, c.h - dy, c.z});
				return;
			}
		}
	}

	/** Keep a small clear space around every vent: nothing is built within 2 blocks sideways, from the vent up. */
	private static boolean protectedAt(List<int[]> vents, int x, int y, int z) {
		return ventGuard(vents, x, y, z);
	}

	private static boolean ventGuard(List<int[]> vents, int x, int y, int z) {
		for (int[] v : vents) {
			if (Math.abs(v[0] - x) <= 2 && Math.abs(v[2] - z) <= 2 && y >= v[1] - 1 && y <= v[1] + 12) return true;
		}
		return false;
	}

	private static boolean isShore(Long2ObjectOpenHashMap<Col> cols, Col c, int sea) {
		for (int i = 0; i < 4; i++) {
			int nx = c.x + (i == 0 ? 1 : i == 1 ? -1 : 0);
			int nz = c.z + (i == 2 ? 1 : i == 3 ? -1 : 0);
			Col n = cols.get(colKey(nx, nz));
			if (n == null || n.top() < sea - 1) return true;
		}
		return false;
	}

	private static long colKey(int x, int z) {
		return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
	}

	static int hash(int x, int z, long seed) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h ^= (h >>> 31);
		h *= 0xBF58476D1CE4E5B9L;
		h ^= (h >>> 29);
		return (int) (h & 0x7FFFFFFF);
	}

	private static double rnd(int x, int z, long seed) {
		return hash(x, z, seed) / (double) 0x7FFFFFFF;
	}

	/** Smooth value noise in -1..1 at a scale of ~5 blocks, for lobed flow edges. */
	static double valueNoise(int x, int z, long seed) {
		double fx = x / 5.0, fz = z / 5.0;
		int x0 = (int) Math.floor(fx), z0 = (int) Math.floor(fz);
		double tx = fx - x0, tz = fz - z0;
		tx = tx * tx * (3 - 2 * tx);
		tz = tz * tz * (3 - 2 * tz);
		double a = rnd(x0, z0, seed), b = rnd(x0 + 1, z0, seed), c = rnd(x0, z0 + 1, seed), d = rnd(x0 + 1, z0 + 1, seed);
		double v = (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
		return v * 2 - 1 + (rnd(x, z, seed ^ 0xABCDL) - 0.5) * 0.4;
	}

	/** Pillow-lava lobes: 3D blobs about 2 blocks across. */
	private static double blob(int x, int y, int z, long seed) {
		return valueNoise(x * 2 + y * 7, z * 2 - y * 5, seed ^ 0x9111L);
	}
}
