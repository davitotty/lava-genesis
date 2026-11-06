package com.lavagenesis.eruption;

import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Pressure (0..100, {@link MagmaData#pressure()}): the energy spent by active powers. It refills on its own,
 * faster at higher stages, much faster in lava and near the home vent. Written only when the whole number changes.
 */
public final class Pressure {
	private Pressure() {}

	public static final int MAX = 100;
	/** Refill runs every this many ticks. */
	private static final int INTERVAL = 10;
	public static final double VENT_RADIUS = 16.0;

	private static final Map<UUID, Double> FRACTION = new HashMap<>();

	/** Pressure per second for a player right now (also useful for a HUD hint). */
	public static double ratePerSecond(ServerPlayer p) {
		MagmaData d = MagmaCore.get(p);
		double rate = 0.8 + 0.3 * d.stage().ordinal();
		if (p.isInLava()) rate *= 2.5;
		if (nearHomeVent(p, d.homeVent(), VENT_RADIUS)) rate += 1.5 + 0.25 * d.stage().ordinal();
		return rate;
	}

	static boolean nearHomeVent(ServerPlayer p, Optional<GlobalPos> vent, double radius) {
		return vent.isPresent() && vent.get().dimension().equals(p.level().dimension())
			&& vent.get().pos().distToCenterSqr(p.position()) <= radius * radius;
	}

	static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!p.isAlive() || p.isSpectator()) continue;
			MagmaData d = MagmaCore.get(p);
			if (d.pressure() >= MAX) {
				FRACTION.remove(p.getUUID());
				continue;
			}
			double acc = FRACTION.getOrDefault(p.getUUID(), 0.0) + ratePerSecond(p) * INTERVAL / 20.0;
			int whole = (int) acc;
			FRACTION.put(p.getUUID(), acc - whole);
			if (whole > 0) {
				int next = Math.min(MAX, d.pressure() + whole);
				if (next != d.pressure()) MagmaCore.update(p, m -> m.withPressure(next));
			}
		}
	}

	/** Spends pressure; false (nothing spent) if there is not enough. Creative players spend nothing. */
	static boolean spend(ServerPlayer p, int amount) {
		if (p.hasInfiniteMaterials() || amount <= 0) return true;
		MagmaData d = MagmaCore.get(p);
		if (d.pressure() < amount) return false;
		MagmaCore.update(p, m -> m.withPressure(m.pressure() - amount));
		return true;
	}

	static void forget(UUID id) {
		FRACTION.remove(id);
	}
}
