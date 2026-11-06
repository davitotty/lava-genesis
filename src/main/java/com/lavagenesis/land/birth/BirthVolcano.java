package com.lavagenesis.land.birth;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.VentAccess;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Builds each new player's birth volcano. On first join (MagmaData.started == false) a job is queued; jobs run
 * one at a time on the server tick so two players never pick the same ocean:
 * search for a deep ocean (biome noise only, spread over ticks) -> load the chunks there -> carve an artificial
 * basin if the sea is too shallow -> work out the volcano -> place it a slice per tick -> place the home vent,
 * put the player into the chamber's lava and mark them started.
 */
public final class BirthVolcano {
	private BirthVolcano() {}

	private static final int SEARCH_BUDGET = 350;     // candidate cells per tick (5 biome samples each)
	private static final int BLOCK_BUDGET = 6000;     // block sets per tick
	private static final int MIN_DEPTH = 18;
	private static final Deque<Job> QUEUE = new ArrayDeque<>();
	private static Job active;

	public static void init() {
		BirthSites.init();
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			if (!MagmaCore.get(player).started()) start(player, false);
		});
		ServerTickEvents.END_SERVER_TICK.register(BirthVolcano::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			QUEUE.clear();
			active = null;
		});
	}

	/** Queues a birth volcano for the player. {@code rebuild}: a fresh one even though they already started. */
	public static boolean start(ServerPlayer player, boolean rebuild) {
		UUID id = player.getUUID();
		if (active != null && active.owner.equals(id)) return false;
		for (Job j : QUEUE) if (j.owner.equals(id)) return false;
		QUEUE.add(new Job(id, rebuild));
		player.sendSystemMessage(Component.translatable(rebuild ? "message.lavagenesis.birth.rebuilding"
			: "message.lavagenesis.birth.stirring").withStyle(ChatFormatting.GOLD));
		return true;
	}

	public static boolean isPending(UUID player) {
		if (active != null && active.owner.equals(player)) return true;
		return QUEUE.stream().anyMatch(j -> j.owner.equals(player));
	}

	private static void tick(MinecraftServer server) {
		if (active == null) active = QUEUE.poll();
		if (active == null) return;
		try {
			if (active.step(server)) active = null;
		} catch (RuntimeException e) {
			LavaGenesis.LOGGER.error("Birth volcano build failed", e);
			ServerPlayer p = server.getPlayerList().getPlayer(active.owner);
			if (p != null) p.sendSystemMessage(Component.translatable("message.lavagenesis.birth.failed").withStyle(ChatFormatting.RED));
			active = null;
		}
	}

	private enum Phase { SEARCH, LOAD, CARVE, PLAN, BUILD }

	private static final class Job {
		final UUID owner;
		final boolean rebuild;
		final long seed;
		Phase phase = Phase.SEARCH;
		OceanFinder finder;
		BlockPos center;
		boolean carve;
		CompletableFuture<?> loading;
		int ticks, phaseTicks, rejects;
		BlockPlan basin;
		VolcanoBuilder.Volcano volcano;

		Job(UUID owner, boolean rebuild) {
			this.owner = owner;
			this.rebuild = rebuild;
			this.seed = owner.getLeastSignificantBits() ^ System.nanoTime();
		}

		/** One tick of work; true when finished (or abandoned). */
		boolean step(MinecraftServer server) {
			ServerPlayer player = server.getPlayerList().getPlayer(owner);
			if (player == null) return true; // left: a new job starts on their next join
			ServerLevel level = server.overworld();
			ticks++;
			phaseTicks++;
			if (ticks % 20 == 1 && !rebuild) {
				// keep the newborn spark safe and in the dark while its volcano forms
				player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 50, 4, false, false));
			}
			if (center != null && ticks % 100 == 0) {
				level.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(center), 3);
			}
			switch (phase) {
				case SEARCH -> search(level);
				case LOAD -> load(level);
				case CARVE -> {
					if (basin == null) basin = VolcanoBuilder.basin(level, center, seed);
					if (basin.apply(level, BLOCK_BUDGET)) next(Phase.PLAN);
				}
				case PLAN -> {
					volcano = VolcanoBuilder.volcano(level, center, seed);
					next(Phase.BUILD);
				}
				case BUILD -> {
					if (volcano.plan().apply(level, BLOCK_BUDGET)) {
						finish(server, level, player);
						return true;
					}
				}
			}
			return false;
		}

		private void next(Phase p) {
			phase = p;
			phaseTicks = 0;
		}

		private void search(ServerLevel level) {
			if (finder == null) {
				finder = new OceanFinder(level, level.getRespawnData().pos(), BirthSites.get(level).centers());
			}
			BlockPos found = finder.step(SEARCH_BUDGET);
			if (found != null) {
				center = found;
				next(Phase.LOAD);
			} else if (finder.exhausted()) {
				center = fallbackSite(level);
				carve = true;
				LavaGenesis.LOGGER.info("No deep ocean found for a birth volcano; carving a basin at {}", center);
				next(Phase.LOAD);
			}
		}

		private void load(ServerLevel level) {
			if (loading == null) {
				loading = level.getChunkSource().addTicketAndLoadWithRadius(TicketType.PORTAL, ChunkPos.containing(center), 3);
			}
			if (!loading.isDone() && phaseTicks < 600) return;
			int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, center.getX(), center.getZ());
			center = new BlockPos(center.getX(), floor, center.getZ());
			if (!carve && hasMonument(level, center) && rejects < 8 && !finder.exhausted()) {
				// an ocean monument sits here: keep searching further out
				rejects++;
				finder.reject(center);
				center = null;
				loading = null;
				next(Phase.SEARCH);
				return;
			}
			if (level.getSeaLevel() - floor < MIN_DEPTH) carve = true;
			next(carve ? Phase.CARVE : Phase.PLAN);
		}

		private static boolean hasMonument(ServerLevel level, BlockPos c) {
			int r = VolcanoBuilder.BASE_RADIUS + 6;
			for (int dx = -r; dx <= r; dx += 7) {
				for (int dz = -r; dz <= r; dz += 7) {
					int x = c.getX() + dx, z = c.getZ() + dz;
					BlockState s = level.getBlockState(new BlockPos(x, level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1, z));
					if (s.is(Blocks.PRISMARINE) || s.is(Blocks.PRISMARINE_BRICKS) || s.is(Blocks.DARK_PRISMARINE)
						|| s.is(Blocks.SEA_LANTERN)) return true;
				}
			}
			return false;
		}

		/** Somewhere a few hundred blocks from spawn and from every other birth volcano. */
		private static BlockPos fallbackSite(ServerLevel level) {
			BlockPos spawn = level.getRespawnData().pos();
			var others = BirthSites.get(level).centers();
			for (int k = 0; ; k++) {
				double a = k * 2.399963; // golden angle spiral
				int dist = 260 + 120 * k;
				BlockPos p = new BlockPos(spawn.getX() + (int) (Math.cos(a) * dist), 0, spawn.getZ() + (int) (Math.sin(a) * dist));
				boolean clear = others.stream().allMatch(o -> o.distSqr(new BlockPos(p.getX(), o.getY(), p.getZ()))
					>= (double) OceanFinder.MIN_SEPARATION * OceanFinder.MIN_SEPARATION);
				if (clear || k > 64) return p;
			}
		}

		private void finish(MinecraftServer server, ServerLevel level, ServerPlayer player) {
			BlockPos vent = volcano.vent();
			if (!VentAccess.placeVent(player, level, vent, true)) {
				LavaGenesis.LOGGER.warn("Could not place the home vent of {} at {}", player.getName().getString(), vent);
			}
			Vec3 spawn = volcano.spawn();
			player.removeEffect(MobEffects.BLINDNESS);
			player.teleportTo(level, spawn.x, spawn.y, spawn.z, Set.of(), level.getRandom().nextFloat() * 360.0F, 10.0F, true);
			player.setDeltaMovement(Vec3.ZERO);
			player.resetFallDistance();
			MagmaCore.update(player, d -> d.withStarted(true));
			BirthSites.add(level, new BirthSites.Site(owner, center, vent));

			level.sendParticles(ParticleTypes.LAVA, spawn.x, spawn.y, spawn.z, 24, 1.2, 0.6, 1.2, 0.0);
			level.sendParticles(ParticleTypes.FLAME, spawn.x, spawn.y, spawn.z, 30, 0.8, 0.8, 0.8, 0.02);
			level.playSound(null, vent, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 1.0F, 0.7F);
			level.playSound(null, vent, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.6F, 0.5F);
			player.sendSystemMessage(Component.translatable(rebuild ? "message.lavagenesis.birth.rebuilt"
				: "message.lavagenesis.birth.born").withStyle(ChatFormatting.GOLD));
			if (!rebuild) {
				player.sendSystemMessage(Component.translatable("message.lavagenesis.birth.hint").withStyle(ChatFormatting.GRAY));
			}
			LavaGenesis.LOGGER.info("Built birth volcano for {} at {} ({} blocks, carved basin: {})",
				player.getName().getString(), center, volcano.plan().size(), carve);
		}
	}
}
