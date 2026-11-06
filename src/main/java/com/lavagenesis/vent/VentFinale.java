package com.lavagenesis.vent;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.LavaIds;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The Great Volcano finale: the player's home island rises into a giant shield volcano centred on the home vent,
 * layer by layer from the sea floor (at most {@link #MAX_CHANGES} block changes per tick), with a summit caldera and
 * lava lake, cooling lava streams down the flanks and lush soil on the lower slopes. The home vent moves to the
 * bottom of the summit lava lake when it is done. Progress is saved in {@link VentRecords}, so a build resumes after
 * a restart; its chunks stay force-loaded while it runs.
 * <p>
 * Testing: {@link #start(ServerPlayer)} starts it for any player at any stage.
 */
public final class VentFinale {
	private VentFinale() {}

	// ---------------------------------------------------------------- shape
	static final int R_CALDERA = 9;      // flat caldera floor
	static final int R_RIM = 13;         // crater rim
	static final int R_SEA = 58;         // shoreline (before noise)
	static final double FLANK = 1.5;     // underwater drop per block outward
	static final int RIM_Y = 176;        // rim height (clamped below the build limit)
	static final int CALDERA_DEPTH = 11; // floor below rim
	static final int LAKE_DEPTH = 5;     // molten rock above the caldera floor
	static final int CHANNELS = 7;       // lava streams down the flanks

	// ---------------------------------------------------------------- pace
	static final int MAX_CHANGES = 400;
	static final int MAX_ITERATIONS = 9000;

	private static final int PHASE_BUILD = 0, PHASE_FINISH = 1;
	private static final byte F_CALDERA = 1, F_CHANNEL = 2, F_LAKE = 4;

	/** One running build (mutable; saved through {@link #CODEC}). */
	public static final class Job {
		public static final Codec<Job> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(j -> j.owner),
			GlobalPos.CODEC.fieldOf("center").forGetter(j -> j.center),
			Codec.BOOL.optionalFieldOf("had_vent", false).forGetter(j -> j.hadVent),
			Codec.LONG.fieldOf("seed").forGetter(j -> j.seed),
			Codec.INT.fieldOf("y").forGetter(j -> j.y),
			Codec.INT.optionalFieldOf("idx", 0).forGetter(j -> j.idx),
			Codec.INT.optionalFieldOf("phase", PHASE_BUILD).forGetter(j -> j.phase),
			Codec.INT.optionalFieldOf("ticks", 0).forGetter(j -> j.ticks)
		).apply(i, Job::new));

		final UUID owner;
		/** The old home vent (or the player's position when they had none); its x/z is the volcano's centre. */
		final GlobalPos center;
		final boolean hadVent;
		final long seed;
		int y, idx, phase, ticks;
		transient @Nullable Shape shape;

		Job(UUID owner, GlobalPos center, boolean hadVent, long seed, int y, int idx, int phase, int ticks) {
			this.owner = owner;
			this.center = center;
			this.hadVent = hadVent;
			this.seed = seed;
			this.y = y;
			this.idx = idx;
			this.phase = phase;
			this.ticks = ticks;
		}
	}

	// ---------------------------------------------------------------- API

	/** Starts (or restarts) the Great Volcano build for this player. Returns false if one is already running. */
	public static boolean start(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		VentRecords records = VentRecords.get(server);
		for (Job j : records.finales) if (j.owner.equals(player.getUUID())) return false;
		Optional<GlobalPos> home = MagmaCore.get(player).homeVent();
		boolean hadVent = home.isPresent() && VentRegistry.hasHomeVent(player) && server.getLevel(home.get().dimension()) != null;
		GlobalPos center = hadVent ? home.get() : GlobalPos.of(player.level().dimension(), player.blockPosition());
		ServerLevel level = server.getLevel(center.dimension());
		if (level == null) return false;
		long seed = center.pos().asLong() * 0x9E3779B97F4A7C15L ^ player.getUUID().getLeastSignificantBits();
		Job job = new Job(player.getUUID(), center, hadVent, seed, 0, 0, PHASE_BUILD, 0);
		Shape shape = shape(level, job);
		job.y = shape.startY;
		job.shape = shape;
		records.finales.add(job);
		records.markFinaleChanged();
		forceChunks(level, job, shape, true);
		player.sendSystemMessage(Component.translatable("message.lavagenesis.finale.start").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		level.playSound(null, center.pos(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 8.0F, 0.4F);
		LavaGenesis.LOGGER.info("Great Volcano rising for {} at {}", player.getName().getString(), center);
		return true;
	}

	/** True while a Great Volcano is being built around the vent at {@code gp}. */
	public static boolean isBuildingAt(GlobalPos gp) {
		return ACTIVE.contains(gp);
	}

	/** Progress 0..1 of the player's running build, or -1 when none runs. */
	public static float progress(MinecraftServer server, UUID owner) {
		for (Job j : VentRecords.get(server).finales) {
			if (!j.owner.equals(owner)) continue;
			ServerLevel level = server.getLevel(j.center.dimension());
			if (level == null || j.phase != PHASE_BUILD) return 1.0F;
			Shape s = j.shape != null ? j.shape : (j.shape = shape(level, j));
			return Mth.clamp((j.y - s.startY) / (float) Math.max(1, s.topY - s.startY), 0.0F, 1.0F);
		}
		return -1.0F;
	}

	static void onStageChanged(ServerPlayer player, MagmaStage from, MagmaStage to) {
		if (to == MagmaStage.GREAT_VOLCANO && !VentRecords.get(player.level().getServer()).finaleDone.contains(player.getUUID())) {
			start(player);
		}
	}

	// ---------------------------------------------------------------- ticking

	private static final Set<GlobalPos> ACTIVE = new java.util.HashSet<>();
	private static final Map<UUID, Integer> SHAKE = new HashMap<>();
	private static final Map<UUID, float[]> SHAKE_OFFSET = new HashMap<>();

	static void clear() {
		ACTIVE.clear();
		SHAKE.clear();
		SHAKE_OFFSET.clear();
	}

	static void tick(MinecraftServer server) {
		VentRecords records = VentRecords.get(server);
		ACTIVE.clear();
		tickShake(server);
		if (records.finales.isEmpty()) return;
		Iterator<Job> it = records.finales.iterator();
		while (it.hasNext()) {
			Job job = it.next();
			ACTIVE.add(job.center);
			ServerLevel level = server.getLevel(job.center.dimension());
			if (level == null) continue;
			if (job.shape == null) {
				job.shape = shape(level, job);
				forceChunks(level, job, job.shape, true); // after a restart
			}
			job.ticks++;
			if (job.phase == PHASE_BUILD) {
				build(level, job, job.shape);
				effects(level, job, job.shape);
				if (job.ticks % 5 == 0) rescuePlayers(level, job.shape);
				if (job.y > job.shape.topY) job.phase = PHASE_FINISH;
				if (job.ticks % 20 == 0) records.markFinaleChanged();
			} else if (finish(server, level, job, job.shape)) {
				forceChunks(level, job, job.shape, false);
				records.finaleDone.add(job.owner);
				it.remove();
				records.markFinaleChanged();
			}
		}
	}

	private static void build(ServerLevel level, Job job, Shape s) {
		int changes = 0, iterations = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int cx = s.cx, cz = s.cz;
		while (changes < MAX_CHANGES && iterations < MAX_ITERATIONS && job.y <= s.topY) {
			int n = s.countAtLeast(job.y);
			if (job.idx >= n) {
				job.y++;
				job.idx = 0;
				continue;
			}
			int c = s.order[job.idx];
			int x = cx + s.dx[c], z = cz + s.dz[c];
			LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
			if (chunk == null) return; // force-loaded chunks are still coming in
			iterations++;
			job.idx++;
			BlockState want = s.desired(c, x, job.y, z);
			if (want == null) continue;
			pos.set(x, job.y, z);
			BlockState cur = chunk.getBlockState(pos);
			if (cur == want || cur.is(VentBlocks.HOTSPOT_VENT) || cur.hasBlockEntity() || cur.is(Blocks.BEDROCK)) continue;
			boolean paint = s.surf[c] - job.y <= 3; // the top few blocks (and the lake above the surface) are always set
			if (!paint && !(cur.isAir() || !cur.getFluidState().isEmpty() || cur.canBeReplaced())) continue;
			level.setBlock(pos, want, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			changes++;
		}
	}

	private static void effects(ServerLevel level, Job job, Shape s) {
		RandomSource r = level.getRandom();
		double x = s.cx + 0.5, z = s.cz + 0.5;
		double top = Math.min(job.y, s.topY) + 1.0;
		boolean underwater = job.y < level.getSeaLevel();
		if (job.ticks % 3 == 0) {
			// lava fountain from the growing summit
			for (int i = 0; i < 10; i++) {
				send(level, ParticleTypes.FLAME, x + r.nextGaussian(), top, z + r.nextGaussian(), 0,
					r.nextGaussian() * 0.15, 0.9 + r.nextDouble() * 0.9, r.nextGaussian() * 0.15, 1.0);
			}
			send(level, ParticleTypes.LAVA, x, top, z, 14, 2.0, 0.5, 2.0, 0.5);
			if (underwater) {
				send(level, ParticleTypes.BUBBLE_COLUMN_UP, x, top, z, 30, 3.0, 1.0, 3.0, 0.3);
				send(level, ParticleTypes.CLOUD, x, level.getSeaLevel() + 0.5, z, 20, 6.0, 0.5, 6.0, 0.05);
			}
		}
		if (job.ticks % 2 == 0) {
			send(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x + r.nextGaussian() * 2, top + 2, z + r.nextGaussian() * 2, 0, 0, 0.25, 0, 1.0);
			send(level, ParticleTypes.LARGE_SMOKE, x, top + 4, z, 6, 2.5, 3.0, 2.5, 0.05);
		}
		if (job.ticks % 4 == 0) {
			// ash falls over the whole mountain
			send(level, ParticleTypes.ASH, x, top + 8, z, 60, s.radius * 0.45, 10.0, s.radius * 0.45, 0.0);
			send(level, ParticleTypes.WHITE_ASH, x, top + 6, z, 40, s.radius * 0.35, 8.0, s.radius * 0.35, 0.0);
		}
		if (job.ticks % 7 == 0) level.playSound(null, x, top, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 3.0F, 0.5F + r.nextFloat() * 0.4F);
		if (job.ticks % 23 == 0) level.playSound(null, x, top, z, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 3.0F, 0.4F);
		if (job.ticks % 50 == 0 || r.nextInt(120) == 0) {
			// rumble + shake for everyone close by
			level.playSound(null, x, top, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 10.0F, 0.35F + r.nextFloat() * 0.2F);
			level.playSound(null, x, top, z, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD, SoundSource.AMBIENT, 6.0F, 0.5F);
			send(level, ParticleTypes.EXPLOSION, x, top + 1, z, 4, 2.0, 1.0, 2.0, 0.0);
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(x, p.getY(), z) < 160 * 160) SHAKE.put(p.getUUID(), 10);
			}
		}
	}

	private static void send(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
		double dx, double dy, double dz, double speed) {
		level.sendParticles(type, true, true, x, y, z, count, dx, dy, dz, speed);
	}

	/** A shake: tiny alternating view nudges, each undone on the next tick so the view does not drift. */
	private static void tickShake(MinecraftServer server) {
		if (SHAKE.isEmpty() && SHAKE_OFFSET.isEmpty()) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			UUID id = p.getUUID();
			if (!p.isAlive() || p.isSleeping() || p.isPassenger()) {
				SHAKE.remove(id);
				SHAKE_OFFSET.remove(id);
				continue;
			}
			float[] last = SHAKE_OFFSET.remove(id);
			Integer left = SHAKE.get(id);
			float yaw = last != null ? -last[0] : 0.0F, pitch = last != null ? -last[1] : 0.0F;
			if (left != null) {
				float a = 0.25F + 0.07F * left;
				float oy = (p.getRandom().nextFloat() - 0.5F) * 2.0F * a, op = (p.getRandom().nextFloat() - 0.5F) * 2.0F * a;
				yaw += oy;
				pitch += op;
				SHAKE_OFFSET.put(id, new float[] {oy, op});
				if (left <= 1) SHAKE.remove(id); else SHAKE.put(id, left - 1);
			}
			if (yaw != 0.0F || pitch != 0.0F) {
				p.teleportTo(p.level(), 0.0, 0.0, 0.0, Relative.ALL, yaw, pitch, false);
			}
		}
		SHAKE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}

	/** Anyone the rising rock closes over is lifted to the new surface. */
	private static void rescuePlayers(ServerLevel level, Shape s) {
		for (ServerPlayer p : level.players()) {
			double ddx = p.getX() - s.cx, ddz = p.getZ() - s.cz;
			if (ddx * ddx + ddz * ddz > (double) s.radius * s.radius || p.isSpectator()) continue;
			BlockPos feet = p.blockPosition();
			int need = Math.max(1, Mth.ceil(p.getBbHeight()));
			if (free(level, feet, need)) continue;
			for (int y = feet.getY() + 1; y < level.getMaxY() - need; y++) {
				BlockPos q = new BlockPos(feet.getX(), y, feet.getZ());
				if (free(level, q, need)) {
					p.teleportTo(level, p.getX(), y + 0.05, p.getZ(), Set.of(), p.getYRot(), p.getXRot(), false);
					break;
				}
			}
		}
	}

	private static boolean free(ServerLevel level, BlockPos p, int height) {
		for (int i = 0; i < height; i++) {
			BlockPos q = p.above(i);
			if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) return false;
		}
		return true;
	}

	/** Moves the home vent to the summit lake. Waits until the owner is online. Returns true when done. */
	private static boolean finish(MinecraftServer server, ServerLevel level, Job job, Shape s) {
		ServerPlayer owner = server.getPlayerList().getPlayer(job.owner);
		if (owner == null) return false;
		BlockPos ventPos = new BlockPos(s.cx, s.surf[s.centerIndex], s.cz);
		BlockPos old = job.center.pos();
		boolean placed = VentRegistry.placeVent(owner, level, ventPos, true);
		if (placed && job.hadVent && !old.equals(ventPos) && level.getBlockState(old).is(VentBlocks.HOTSPOT_VENT)) {
			// the old vent is buried deep inside the mountain now: it becomes part of the magma conduit
			level.setBlock(old, s.pal.molten, Block.UPDATE_ALL);
		}
		double x = ventPos.getX() + 0.5, y = ventPos.getY() + 1.0, z = ventPos.getZ() + 0.5;
		send(level, ParticleTypes.EXPLOSION_EMITTER, x, y + LAKE_DEPTH, z, 3, 3.0, 1.0, 3.0, 0.0);
		send(level, ParticleTypes.LAVA, x, y + LAKE_DEPTH, z, 200, 6.0, 2.0, 6.0, 0.8);
		send(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + LAKE_DEPTH + 2, z, 60, 4.0, 4.0, 4.0, 0.05);
		level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 12.0F, 0.3F);
		level.playSound(null, x, y, z, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 6.0F, 0.5F);
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(x, p.getY(), z) < 200 * 200) SHAKE.put(p.getUUID(), 20);
		}
		MagmaData d = MagmaCore.get(owner);
		if (d.homeVent().isPresent()) VentRespawn.sendHome(owner, d.homeVent().get(), d.stage());
		owner.sendSystemMessage(Component.translatable("message.lavagenesis.finale.done").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		IslandSurveyor.request(level, ventPos, owner.getUUID());
		LavaGenesis.LOGGER.info("Great Volcano of {} complete, summit vent at {}", owner.getName().getString(), ventPos);
		return true;
	}

	private static void forceChunks(ServerLevel level, Job job, Shape s, boolean force) {
		int r = s.radius + 1;
		for (int x = (s.cx - r) >> 4; x <= (s.cx + r) >> 4; x++) {
			for (int z = (s.cz - r) >> 4; z <= (s.cz + r) >> 4; z++) {
				level.setChunkForced(x, z, force);
			}
		}
	}

	// ---------------------------------------------------------------- the mountain's shape

	/** Blocks used for the build, looked up at runtime with vanilla fallbacks when the land module is missing. */
	static final class Palette {
		final BlockState molten, cooling, freshBasalt, blackSand, soil, lush;
		final BlockState basalt = Blocks.BASALT.defaultBlockState();
		final BlockState smoothBasalt = Blocks.SMOOTH_BASALT.defaultBlockState();
		final BlockState blackstone = Blocks.BLACKSTONE.defaultBlockState();
		final BlockState tuff = Blocks.TUFF.defaultBlockState();
		final BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();

		Palette() {
			molten = lookup(LavaIds.MOLTEN_ROCK, Blocks.LAVA);
			cooling = lookup(LavaIds.COOLING_LAVA, Blocks.MAGMA_BLOCK);
			freshBasalt = lookup(LavaIds.FRESH_BASALT, Blocks.BASALT);
			blackSand = lookup(LavaIds.BLACK_SAND, Blocks.BLACKSTONE);
			soil = lookup(LavaIds.VOLCANIC_SOIL, Blocks.COARSE_DIRT);
			lush = lookup(LavaIds.LUSH_VOLCANIC_SOIL, Blocks.GRASS_BLOCK);
		}

		private static BlockState lookup(Identifier id, Block fallback) {
			Block b = LavaIds.block(id);
			return b == null || b == Blocks.AIR ? fallback.defaultBlockState() : b.defaultBlockState();
		}
	}

	static Shape shape(ServerLevel level, Job job) {
		return new Shape(level, job.center.pos().getX(), job.center.pos().getZ(), job.seed);
	}

	static final class Shape {
		final int cx, cz, sea, rimY, floorY, lakeY, startY, topY, radius, side;
		final long seed;
		final int[] dx, dz, surf, maxY, order, sortedMax;
		final byte[] flags;
		final int centerIndex;
		final Palette pal = new Palette();
		private final double[] channelAngle = new double[CHANNELS];
		private final double[] channelLength = new double[CHANNELS];

		Shape(ServerLevel level, int cx, int cz, long seed) {
			this.cx = cx;
			this.cz = cz;
			this.seed = seed;
			sea = level.getSeaLevel();
			rimY = Math.max(sea + 40, Math.min(RIM_Y, level.getMaxY() - 24));
			floorY = rimY - CALDERA_DEPTH;
			lakeY = floorY + LAKE_DEPTH;
			startY = Math.max(level.getMinY() + 1, sea - 42);
			radius = R_SEA + (int) Math.ceil((sea - startY) / FLANK) + 8;
			side = radius * 2 + 1;
			for (int k = 0; k < CHANNELS; k++) {
				channelAngle[k] = (k + 0.35 * (hash(k, 7, seed) - 0.5)) * (Math.PI * 2.0 / CHANNELS);
				channelLength[k] = R_RIM + (R_SEA + 4 - R_RIM) * (0.45 + 0.55 * hash(k, 11, seed));
			}
			int n = side * side;
			int[] tdx = new int[n], tdz = new int[n], ts = new int[n], tm = new int[n];
			byte[] tf = new byte[n];
			int count = 0, center = 0;
			for (int ix = -radius; ix <= radius; ix++) {
				for (int iz = -radius; iz <= radius; iz++) {
					if (ix * ix + iz * iz > radius * radius) continue;
					byte f = 0;
					int s = surface(ix, iz);
					if (s == Integer.MIN_VALUE) {
						f |= F_CALDERA;
						s = calderaFloor(ix, iz);
					} else if (channel(ix, iz)) {
						f |= F_CHANNEL;
					}
					if (s < lakeY && Math.sqrt(ix * ix + iz * iz) < R_RIM + 3) f |= F_LAKE; // caldera floor and low inner walls hold the lake
					int top = (f & F_LAKE) != 0 ? lakeY : s;
					if (top < startY) continue;
					if (ix == 0 && iz == 0) center = count;
					tdx[count] = ix;
					tdz[count] = iz;
					ts[count] = s;
					tm[count] = top;
					tf[count] = f;
					count++;
				}
			}
			dx = Arrays.copyOf(tdx, count);
			dz = Arrays.copyOf(tdz, count);
			surf = Arrays.copyOf(ts, count);
			maxY = Arrays.copyOf(tm, count);
			flags = Arrays.copyOf(tf, count);
			centerIndex = center;
			// order columns by how high they reach (highest first): layer y only visits the prefix that reaches y
			long[] keyed = new long[count];
			for (int i = 0; i < count; i++) keyed[i] = ((long) (4096 - maxY[i]) << 32) | i;
			Arrays.sort(keyed);
			order = new int[count];
			sortedMax = new int[count];
			int top = startY;
			for (int i = 0; i < count; i++) {
				order[i] = (int) keyed[i];
				sortedMax[i] = maxY[order[i]];
				top = Math.max(top, sortedMax[i]);
			}
			topY = top;
		}

		/** How many columns reach up to at least {@code y}. */
		int countAtLeast(int y) {
			int lo = 0, hi = sortedMax.length;
			while (lo < hi) {
				int mid = (lo + hi) >>> 1;
				if (sortedMax[mid] >= y) lo = mid + 1; else hi = mid;
			}
			return lo;
		}

		/** Radius scaled by a wobbly coastline so the mountain is not a perfect cone. */
		private double effectiveRadius(int ix, int iz) {
			double r = Math.sqrt(ix * ix + iz * iz);
			if (r < 0.5) return 0.0;
			double cos = ix / r, sin = iz / r;
			double warp = (noise(cos * 2.2 + 10, sin * 2.2 + 10, 1) - 0.5) * 0.24
				+ (noise((cx + ix) / 13.0, (cz + iz) / 13.0, 2) - 0.5) * 0.12 * Math.min(1.0, r / 20.0);
			return r / (1.0 + warp);
		}

		/** Top block y of the column, or MIN_VALUE inside the caldera. */
		private int surface(int ix, int iz) {
			double rr = effectiveRadius(ix, iz);
			double rim = R_RIM + (noise(ix / 5.0, iz / 5.0, 3) - 0.5) * 2.0;
			if (rr < R_CALDERA + (rim - R_RIM)) return Integer.MIN_VALUE;
			if (rr < rim) {
				double t = (rr - (R_CALDERA + rim - R_RIM)) / (R_RIM - R_CALDERA);
				return floorY + (int) Math.round(Math.pow(Mth.clamp(t, 0.0, 1.0), 0.6) * (rimY - floorY));
			}
			double bumps = (noise((cx + ix) / 9.0, (cz + iz) / 9.0, 4) - 0.5) * 5.0 + (noise((cx + ix) / 3.5, (cz + iz) / 3.5, 5) - 0.5) * 1.6;
			if (rr <= R_SEA) {
				double u = (rr - rim) / (R_SEA - rim);
				double e = (rimY - sea) * (0.7 * Math.pow(1.0 - u, 1.6) + 0.3 * (1.0 - Math.pow(u, 1.5)));
				return sea + (int) Math.round(e + bumps * (0.3 + 0.7 * (1.0 - u)) + 0.6);
			}
			return sea - (int) Math.round((rr - R_SEA) * FLANK - bumps * 0.5);
		}

		private int calderaFloor(int ix, int iz) {
			return floorY + (int) Math.round((noise(ix / 3.0, iz / 3.0, 6) - 0.5) * 2.0);
		}

		private boolean channel(int ix, int iz) {
			double r = Math.sqrt(ix * ix + iz * iz);
			if (r < R_RIM - 1) return false;
			double theta = Math.atan2(iz, ix);
			for (int k = 0; k < CHANNELS; k++) {
				if (r > channelLength[k]) continue;
				double wobble = (noise(r / 11.0, k * 3.7, 8) - 0.5) * 0.9 * Math.min(1.0, r / 25.0);
				double d = Mth.wrapDegrees(Math.toDegrees(theta - channelAngle[k] - wobble)) * Math.PI / 180.0;
				double width = 0.9 + 1.4 * (r / R_SEA) * (1.0 - 0.5 * r / channelLength[k]);
				if (Math.abs(d) * r < width) return true;
			}
			return false;
		}

		/** The block this build wants at (x, y, z) of column {@code c}, or null to leave it alone. */
		@Nullable BlockState desired(int c, int x, int y, int z) {
			int s = surf[c];
			if (y > s) return (flags[c] & F_LAKE) != 0 && y <= lakeY ? pal.molten : null;
			if ((flags[c] & F_CALDERA) != 0) {
				if (y == s) return hash(x, z, seed + 31) < 0.35 ? pal.magma : (hash(x, z, seed + 32) < 0.5 ? pal.blackstone : pal.basalt);
				return interior(x, y, z);
			}
			int d = s - y;
			if (d > 3) return interior(x, y, z);
			int e = y - sea;
			int surfaceE = s - sea;
			double n = noise(x / 6.0, z / 6.0, 9);
			if ((flags[c] & F_CHANNEL) != 0 && surfaceE >= -2) {
				if (d == 0) {
					double r = Math.sqrt(dx[c] * dx[c] + dz[c] * dz[c]);
					return r < R_RIM + 7 ? pal.molten : pal.cooling;
				}
				return d == 1 ? pal.magma : pal.basalt;
			}
			if (surfaceE < -3) {
				// submarine flank: pillow basalt and blackstone
				if (d == 0) return n < 0.3 ? pal.blackstone : (n > 0.8 ? pal.smoothBasalt : pal.basalt);
				return pal.basalt;
			}
			if (surfaceE <= 2) return d <= 2 ? pal.blackSand : pal.basalt;            // black sand beaches
			int lushTop = 24 + (int) (noise(x / 20.0, z / 20.0, 10) * 14);
			if (surfaceE <= lushTop) {
				// lower slopes: green volcanic soil
				if (d == 0) return n < 0.75 ? pal.lush : pal.soil;
				return d <= 2 ? pal.soil : pal.basalt;
			}
			if (surfaceE <= 62) {
				if (d == 0) return n < 0.22 ? pal.soil : (n < 0.6 ? pal.freshBasalt : pal.basalt);
				return pal.basalt;
			}
			// high slopes: bare rock and ash
			if (d == 0) {
				double h = hash(x, z, seed + 77);
				if (surfaceE > rimY - sea - 14 && h < 0.08) return pal.magma;
				return n < 0.4 ? pal.tuff : (n < 0.65 ? pal.blackstone : pal.basalt);
			}
			return e > rimY - sea - 30 && n < 0.4 ? pal.tuff : pal.basalt;
		}

		/** Layered inside of the mountain: basalt with bands of tuff (ash beds) and blackstone. */
		private BlockState interior(int x, int y, int z) {
			int band = Math.floorMod(y + (int) (noise(x / 16.0, z / 16.0, 12) * 4.0), 11);
			if (band <= 1) return pal.tuff;
			if (band == 6) return pal.blackstone;
			return hash(x, z * 31 + y, seed + 5) < 0.12 ? pal.smoothBasalt : pal.basalt;
		}

		// smooth value noise in [0, 1)
		private double noise(double x, double z, int salt) {
			int x0 = Mth.floor(x), z0 = Mth.floor(z);
			double fx = x - x0, fz = z - z0;
			fx = fx * fx * (3 - 2 * fx);
			fz = fz * fz * (3 - 2 * fz);
			double a = hash(x0, z0, seed + salt), b = hash(x0 + 1, z0, seed + salt);
			double c = hash(x0, z0 + 1, seed + salt), d = hash(x0 + 1, z0 + 1, seed + salt);
			return Mth.lerp(fz, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
		}
	}

	static double hash(int x, int z, long seed) {
		long h = seed ^ (x * 0x632BE59BD9B4E019L) ^ (z * 0x85157AF5L);
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 33;
		return (h >>> 11) * 0x1.0p-53;
	}
}
