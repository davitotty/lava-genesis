package com.lavagenesis.vent;

import com.lavagenesis.core.LavaIds;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Measures the island around each vent: a flood fill over land columns (top solid non-fluid block at or above sea
 * level that is in {@code #lavagenesis:volcanic_land}). Jobs are queued and run with a per-tick column budget, never
 * load chunks, and write their result into {@link VentRecords}.
 */
public final class IslandSurveyor {
	private IslandSurveyor() {}

	/** Columns examined per server tick over all jobs. */
	private static final int BUDGET_PER_TICK = 3072;
	/** Largest island one fill will count (a full Great Volcano is well under this). */
	public static final int MAX_COLUMNS = 24000;
	/** Land columns within this distance of the vent start the fill (the vent itself often sits in a crater). */
	private static final int SEED_RADIUS = 14;
	/** A fill that reaches within this distance of another of the owner's vents links the two. */
	private static final int LINK_RADIUS = 4;

	private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
	private static final Set<GlobalPos> QUEUED = new HashSet<>();

	/** Ask for a fresh measurement of the vent at {@code pos} (ignored if one is already queued). */
	public static void request(ServerLevel level, BlockPos pos, UUID owner) {
		GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
		if (QUEUED.add(gp)) QUEUE.add(new Job(level, gp, owner));
	}

	static void forget(ServerLevel level, BlockPos pos) {
		GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
		if (QUEUED.remove(gp)) QUEUE.removeIf(j -> j.gp.equals(gp));
	}

	static void clear() {
		QUEUE.clear();
		QUEUED.clear();
	}

	static void tick(MinecraftServer server) {
		int budget = BUDGET_PER_TICK;
		while (budget > 0 && !QUEUE.isEmpty()) {
			Job job = QUEUE.peek();
			budget = job.run(budget);
			if (job.done) {
				QUEUE.poll();
				QUEUED.remove(job.gp);
				job.finish(server);
			}
		}
	}

	static long col(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	static int colX(long c) { return (int) (c >> 32); }
	static int colZ(long c) { return (int) c; }

	/** 1 = land, 0 = not land, -1 = chunk not loaded. */
	static int landAt(ServerLevel level, int x, int z) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
		if (chunk == null) return -1;
		int y = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
		if (y < level.getSeaLevel()) return 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
		BlockState state = chunk.getBlockState(p);
		// look through trees and plants growing on the island
		for (int i = 0; i < 24 && (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || !state.getFluidState().isEmpty()); i++) {
			p.move(0, -1, 0);
			if (p.getY() < level.getSeaLevel()) return 0;
			state = chunk.getBlockState(p);
		}
		if (!state.getFluidState().isEmpty()) return 0;
		return state.is(LavaIds.VOLCANIC_LAND) || state.is(VentBlocks.HOTSPOT_VENT) ? 1 : 0;
	}

	private static final class Job {
		final ServerLevel level;
		final GlobalPos gp;
		final UUID owner;
		final LongOpenHashSet visited = new LongOpenHashSet();
		final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
		final List<BlockPos> others = new ArrayList<>();
		final Set<Long> links = new HashSet<>();
		int seedIndex;
		boolean seeded;
		boolean unloaded;
		boolean done;
		int count;
		long minKey = Long.MAX_VALUE;

		Job(ServerLevel level, GlobalPos gp, UUID owner) {
			this.level = level;
			this.gp = gp;
			this.owner = owner;
			for (Map.Entry<GlobalPos, VentRecords.Vent> e : VentRecords.get(level.getServer()).ventsOf(owner).entrySet()) {
				if (e.getKey().dimension().equals(gp.dimension()) && !e.getKey().equals(gp)) others.add(e.getKey().pos());
			}
		}

		int run(int budget) {
			BlockPos c = gp.pos();
			int side = SEED_RADIUS * 2 + 1;
			while (!seeded && budget > 0) {
				int dx = seedIndex % side - SEED_RADIUS, dz = seedIndex / side - SEED_RADIUS;
				seedIndex++;
				if (seedIndex >= side * side) seeded = true;
				budget--;
				if (dx * dx + dz * dz > SEED_RADIUS * SEED_RADIUS) continue;
				visit(c.getX() + dx, c.getZ() + dz);
			}
			while (seeded && budget > 0 && !queue.isEmpty() && count < MAX_COLUMNS) {
				long cur = queue.dequeueLong();
				int x = colX(cur), z = colZ(cur);
				visit(x + 1, z);
				visit(x - 1, z);
				visit(x, z + 1);
				visit(x, z - 1);
				budget -= 4;
			}
			if (seeded && (queue.isEmpty() || count >= MAX_COLUMNS)) done = true;
			return budget;
		}

		private void visit(int x, int z) {
			long key = col(x, z);
			if (!visited.add(key)) return;
			int land = landAt(level, x, z);
			if (land < 0) {
				unloaded = true;
				return;
			}
			if (land == 0) return;
			count++;
			if (key < minKey) minKey = key;
			queue.enqueue(key);
			for (BlockPos o : others) {
				if (Math.abs(o.getX() - x) <= LINK_RADIUS && Math.abs(o.getZ() - z) <= LINK_RADIUS) links.add(o.asLong());
			}
		}

		void finish(MinecraftServer server) {
			VentRecords records = VentRecords.get(server);
			VentRecords.Vent rec = records.get(gp);
			if (rec == null) return; // the vent was removed meanwhile
			int size = count;
			long key = count > 0 ? minKey : Long.MIN_VALUE;
			List<Long> linkList = new ArrayList<>(links);
			if (unloaded && rec.size() > size) {
				// part of the island is in unloaded chunks: keep the last full answer instead of shrinking
				size = rec.size();
				key = rec.key();
				linkList = rec.links();
			}
			if (rec.size() != size || rec.key() != key || !rec.links().equals(linkList)) {
				records.put(gp, rec.withMeasure(size, key, linkList));
			}
			HotspotVentBlockEntity be = VentRegistry.loadedVent(server, gp);
			if (be != null) be.setLastIsland(size);
			ServerPlayer p = server.getPlayerList().getPlayer(owner);
			if (p != null) VentRegistry.updateIslands(p);
		}
	}
}
