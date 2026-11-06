package com.lavagenesis.eruption.land;

import com.lavagenesis.core.MagmaCore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Places planned eruption blocks a few hundred per tick per level, so big cones and islands grow over a couple of
 * seconds instead of freezing the server. Awards the owner mass for new land as it appears
 * (+1 per block at/above sea level, +1 per 4 blocks below it, only for blocks that replaced air/water/plants,
 * capped per job), with steam where rock meets water.
 */
public final class BlockPlacer {
	private BlockPlacer() {}

	/** Blocks placed per level per tick, shared by all jobs in that level. */
	public static final int BUDGET_PER_TICK = 240;

	private static final Map<ResourceKey<Level>, ArrayDeque<Job>> QUEUES = new HashMap<>();

	public static final class Job {
		final List<FlowSim.Placement> list;
		final @Nullable UUID owner;
		final int massCap;
		final @Nullable Runnable onDone;
		final int perTick;
		int index;
		int aboveSea, belowSea, awarded;

		Job(List<FlowSim.Placement> list, @Nullable UUID owner, int massCap, int perTick, @Nullable Runnable onDone) {
			this.list = list;
			this.perTick = Math.max(1, perTick);
			this.owner = owner;
			this.massCap = massCap;
			this.onDone = onDone;
		}

		int mass() {
			return Math.min(massCap, aboveSea + belowSea / 4);
		}
	}

	/** Queues a plan at full speed. {@code onDone} runs on the server thread when its last block is placed. */
	public static void submit(ServerLevel level, FlowSim.Result plan, @Nullable ServerPlayer owner, int massCap,
		@Nullable Runnable onDone) {
		submit(level, plan, owner, massCap, BUDGET_PER_TICK, onDone);
	}

	/** Queues a plan placing at most {@code perTick} blocks per tick (slower = the flow visibly spreads). */
	public static void submit(ServerLevel level, FlowSim.Result plan, @Nullable ServerPlayer owner, int massCap,
		int perTick, @Nullable Runnable onDone) {
		if (plan.isEmpty()) {
			if (onDone != null) onDone.run();
			return;
		}
		QUEUES.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>())
			.add(new Job(plan.placements(), owner != null ? owner.getUUID() : null, massCap, perTick, onDone));
	}

	public static boolean busy(ServerLevel level) {
		ArrayDeque<Job> q = QUEUES.get(level.dimension());
		return q != null && !q.isEmpty();
	}

	public static void clear() {
		QUEUES.clear();
	}

	public static void tick(ServerLevel level) {
		ArrayDeque<Job> q = QUEUES.get(level.dimension());
		if (q == null || q.isEmpty()) return;
		int budget = BUDGET_PER_TICK;
		// share the budget round-robin so a small splash is not stuck behind a whole island
		List<Job> finished = new ArrayList<>();
		int share = Math.max(16, budget / q.size());
		for (Iterator<Job> it = q.iterator(); it.hasNext() && budget > 0; ) {
			Job job = it.next();
			int n = Math.min(Math.min(share, job.perTick), budget);
			budget -= Math.max(place(level, job, n), 1);
			if (job.index >= job.list.size()) {
				it.remove();
				finished.add(job);
			}
		}
		for (Job job : finished) {
			award(level, job);
			if (job.onDone != null) job.onDone.run();
		}
		for (Job job : q) award(level, job);
	}

	private static void award(ServerLevel level, Job job) {
		if (job.owner == null) return;
		int m = job.mass();
		if (m <= job.awarded) return;
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(job.owner);
		if (p == null) return;
		MagmaCore.addMass(p, m - job.awarded, "eruption");
		job.awarded = m;
	}

	/** Places up to {@code max} blocks of a job; returns how many were attempted. */
	private static int place(ServerLevel level, Job job, int max) {
		int sea = level.getSeaLevel();
		RandomSource rnd = level.getRandom();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		List<ServerPlayer> players = level.players();
		int attempted = 0, steam = 0, sounds = 0;
		while (attempted < max && job.index < job.list.size()) {
			FlowSim.Placement pl = job.list.get(job.index++);
			attempted++;
			m.set(pl.pos());
			if (!level.isLoaded(m)) continue;
			BlockState old = level.getBlockState(m);
			if (!Palette.isFree(level, m, old)) continue;
			if (!makeRoomForPlayers(level, players, m)) continue;
			boolean water = old.getFluidState().is(FluidTags.WATER);
			if (!level.setBlock(m, pl.state(), Block.UPDATE_ALL)) continue;
			if (Palette.countsForMass(old)) {
				if (m.getY() >= sea) job.aboveSea++; else job.belowSea++;
			}
			double x = m.getX() + 0.5, y = m.getY() + 1.0, z = m.getZ() + 0.5;
			if (water && steam < 6 && rnd.nextInt(3) == 0) {
				steam++;
				boolean surface = m.getY() >= sea - 2;
				level.sendParticles(surface ? ParticleTypes.CLOUD : ParticleTypes.BUBBLE_COLUMN_UP, x, y, z,
					surface ? 3 : 4, 0.4, 0.3, 0.4, surface ? 0.05 : 0.2);
				if (surface) level.sendParticles(ParticleTypes.WHITE_SMOKE, x, y + 0.3, z, 2, 0.3, 0.4, 0.3, 0.02);
				if (sounds < 2 && rnd.nextInt(4) == 0) {
					sounds++;
					level.playSound(null, m, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 0.8F + rnd.nextFloat() * 0.4F);
				}
			} else if (!water && rnd.nextInt(12) == 0) {
				level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.3, 0.1, 0.3, 0.0);
				if (rnd.nextInt(3) == 0) level.sendParticles(ParticleTypes.SMOKE, x, y, z, 2, 0.3, 0.2, 0.3, 0.01);
				if (sounds < 2 && rnd.nextInt(6) == 0) {
					sounds++;
					level.playSound(null, m, rnd.nextBoolean() ? SoundEvents.LAVA_POP : SoundEvents.BASALT_PLACE,
						SoundSource.BLOCKS, 0.6F, 0.7F + rnd.nextFloat() * 0.5F);
				}
			}
		}
		return attempted;
	}

	/**
	 * Rock rising under a player lifts them instead of burying them. Returns false (skip this block) when a
	 * player is in the way and cannot be lifted.
	 */
	private static boolean makeRoomForPlayers(ServerLevel level, List<ServerPlayer> players, BlockPos pos) {
		AABB box = new AABB(pos);
		for (Player p : players) {
			if (p.isSpectator() || !p.getBoundingBox().intersects(box)) continue;
			if (pos.getY() > p.getY() + 0.1) return false; // at head height: leave the gap
			BlockPos above = pos.above();
			if (!Palette.isFree(level, above, level.getBlockState(above))
				|| !Palette.isFree(level, above.above(), level.getBlockState(above.above()))) return false;
			p.teleportTo(p.getX(), pos.getY() + 1.0, p.getZ());
		}
		return true;
	}
}
