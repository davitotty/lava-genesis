package com.lavagenesis.eruption.fx;

import com.lavagenesis.eruption.net.ShakePayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Server-side effect helpers and long-running effects (fountains, ash columns, bomb volleys). */
public final class Fx {
	private Fx() {}

	/** A running effect; return false when finished. */
	@FunctionalInterface
	public interface Ticker {
		boolean tick(ServerLevel level, int age);
	}

	private static final class Running {
		final Ticker ticker;
		int age;
		Running(Ticker t) { ticker = t; }
	}

	private static final Map<ResourceKey<Level>, List<Running>> RUNNING = new HashMap<>();

	public static void start(ServerLevel level, Ticker ticker) {
		RUNNING.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(new Running(ticker));
	}

	public static void tick(ServerLevel level) {
		List<Running> list = RUNNING.get(level.dimension());
		if (list == null || list.isEmpty()) return;
		List<Running> copy = new ArrayList<>(list);
		for (Running r : copy) {
			boolean alive;
			try {
				alive = r.ticker.tick(level, r.age++);
			} catch (RuntimeException e) {
				com.lavagenesis.LavaGenesis.LOGGER.error("Eruption effect failed", e);
				alive = false;
			}
			if (!alive) list.remove(r);
		}
	}

	public static void clear() {
		RUNNING.clear();
	}

	/** Particles moving with velocity (vx, vy, vz) - the count-0 form of sendParticles. */
	public static void jet(ServerLevel level, ParticleOptions type, double x, double y, double z, double vx, double vy, double vz) {
		level.sendParticles(type, x, y, z, 0, vx, vy, vz, 1.0);
	}

	/** Particles visible from far away (ash columns). */
	public static void far(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
		double dx, double dy, double dz, double speed) {
		level.sendParticles(type, true, true, x, y, z, count, dx, dy, dz, speed);
	}

	/** A fiery line, e.g. the Flare beam from the eye to the melted block. */
	public static void beam(ServerLevel level, Vec3 from, Vec3 to, int points) {
		RandomSource r = level.getRandom();
		for (int i = 1; i <= points; i++) {
			Vec3 p = from.lerp(to, i / (double) (points + 1));
			level.sendParticles(i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, p.x, p.y, p.z, 1,
				0.02, 0.02, 0.02, 0.005 + r.nextDouble() * 0.01);
		}
	}

	/** Shakes the camera of every player within {@code radius}, fading with distance. */
	public static void shake(ServerLevel level, Vec3 center, double radius, float strength, int ticks) {
		for (ServerPlayer p : level.players()) {
			double d = p.position().distanceTo(center);
			if (d > radius) continue;
			float s = (float) (strength * (1.0 - d / radius * 0.7));
			if (ServerPlayNetworking.canSend(p, ShakePayload.TYPE)) {
				ServerPlayNetworking.send(p, new ShakePayload(s, ticks));
			}
		}
	}
}
