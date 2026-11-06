package com.lavagenesis.eruption;

import com.lavagenesis.eruption.land.BlockPlacer;
import com.lavagenesis.eruption.land.FlowSim;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Powers that move the player over several ticks: Flow Surge (a dash) and Hotspot Leap (a long arc). */
final class Motion {
	private Motion() {}

	private static final class Surge {
		final Vec3 dir;
		int ticks;
		final Set<UUID> hit = new HashSet<>();
		Surge(Vec3 dir, int ticks) { this.dir = dir; this.ticks = ticks; }
	}

	private static final class Leap {
		final Vec3 from, to;
		final double peak;
		final int duration;
		int t;
		int off;      // ticks spent far from the planned path (blocked)
		int grace;    // ticks of fall protection after landing
		Leap(Vec3 from, Vec3 to, double peak, int duration) {
			this.from = from; this.to = to; this.peak = peak; this.duration = duration;
		}
		Vec3 at(int tick) {
			double s = Math.clamp(tick / (double) duration, 0.0, 1.0);
			return from.lerp(to, s).add(0, 4 * peak * s * (1 - s), 0);
		}
	}

	private static final Map<UUID, Surge> SURGES = new HashMap<>();
	private static final Map<UUID, Leap> LEAPS = new HashMap<>();

	static void startSurge(ServerPlayer p, Vec3 dir, int ticks) {
		SURGES.put(p.getUUID(), new Surge(dir, ticks));
	}

	static void startLeap(ServerPlayer p, Vec3 to, double peak, int duration) {
		LEAPS.put(p.getUUID(), new Leap(p.position(), to, peak, duration));
	}

	static boolean leaping(ServerPlayer p) {
		return LEAPS.containsKey(p.getUUID());
	}

	static void forget(UUID id) {
		SURGES.remove(id);
		LEAPS.remove(id);
	}

	static void clear() {
		SURGES.clear();
		LEAPS.clear();
	}

	private static void push(ServerPlayer p, Vec3 v) {
		p.setDeltaMovement(v);
		p.connection.send(new ClientboundSetEntityMotionPacket(p));
	}

	static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Surge>> it = SURGES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Surge> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !p.isAlive() || tickSurge(p, e.getValue())) it.remove();
		}
		for (Iterator<Map.Entry<UUID, Leap>> it = LEAPS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Leap> e = it.next();
			ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
			if (p == null || !p.isAlive() || tickLeap(p, e.getValue())) it.remove();
		}
	}

	/** Returns true when finished. */
	private static boolean tickSurge(ServerPlayer p, Surge s) {
		ServerLevel level = p.level();
		RandomSource r = level.getRandom();
		p.resetFallDistance();
		if (s.ticks-- <= 0) {
			push(p, p.getDeltaMovement().scale(0.4));
			return true;
		}
		double speed = p.isInWater() ? 0.9 : 1.25;
		Vec3 v = s.dir.scale(speed);
		if (!p.isInWater() && !p.onGround()) v = v.add(0, Math.min(p.getDeltaMovement().y, 0.1), 0);
		push(p, v);
		// the river of fire left behind
		Vec3 behind = p.position().subtract(s.dir.x * 1.6, 0, s.dir.z * 1.6);
		BlockPos at = BlockPos.containing(behind.x, p.getY() + 0.2, behind.z);
		FlowSim.Result plan = FlowSim.run(level, at, FlowSim.Params.of(2 + r.nextInt(2), 1).slopes(1.0, 1.0).noise(0.3).shore(false), r.nextLong());
		BlockPlacer.submit(level, plan, p, 1, null);
		double x = p.getX(), y = p.getY() + 0.2, z = p.getZ();
		if (p.isInWater()) {
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 6, 0.3, 0.2, 0.3, 0.2);
			level.sendParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 2, 0.2, 0.2, 0.2, 0.02);
		} else {
			level.sendParticles(ParticleTypes.FLAME, x, y, z, 8, 0.3, 0.15, 0.3, 0.03);
			level.sendParticles(ParticleTypes.LAVA, x, y, z, 2, 0.3, 0.1, 0.3, 0.0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, behind.x, y + 0.3, behind.z, 2, 0.3, 0.2, 0.3, 0.02);
		}
		if (s.ticks % 4 == 0) level.playSound(null, x, y, z, SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.6F + r.nextFloat() * 0.4F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.2),
			e -> e != p && e.isAlive() && !s.hit.contains(e.getUUID()))) {
			s.hit.add(e.getUUID());
			e.hurtServer(level, level.damageSources().onFire(), 4.0F);
			e.igniteForSeconds(4.0F);
			e.push(s.dir.x * 0.8, 0.3, s.dir.z * 0.8);
		}
		return false;
	}

	/** Returns true when finished. */
	private static boolean tickLeap(ServerPlayer p, Leap l) {
		ServerLevel level = p.level();
		p.resetFallDistance();
		if (l.grace > 0) {
			// landed (or ended): keep fall protection for a moment
			return --l.grace <= 0 || p.onGround() && l.grace < 30;
		}
		l.t++;
		// the server sees the player's position a few ticks late, so steer mostly by the planned path
		// (feed-forward) with only a gentle pull back towards it
		Vec3 step = l.at(l.t + 1).subtract(l.at(l.t));
		Vec3 error = l.at(l.t - 2).subtract(p.position());
		if (error.length() > 10) l.off++; else l.off = 0;
		if (l.t >= l.duration || l.off > 6) {
			push(p, new Vec3((l.to.x - l.from.x) * 0.004, -0.1, (l.to.z - l.from.z) * 0.004));
			l.grace = 60;
			return false;
		}
		push(p, step.add(error.scale(0.12)));
		RandomSource r = level.getRandom();
		double x = p.getX(), y = p.getY() + 0.3, z = p.getZ();
		level.sendParticles(ParticleTypes.FLAME, x, y, z, 6, 0.3, 0.3, 0.3, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 3, 0.3, 0.3, 0.3, 0.02);
		if (r.nextInt(2) == 0) level.sendParticles(ParticleTypes.LAVA, x, y, z, 1, 0.2, 0.2, 0.2, 0.0);
		if (l.t % 8 == 0) level.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 0.5F + r.nextFloat() * 0.2F);
		return false;
	}
}
