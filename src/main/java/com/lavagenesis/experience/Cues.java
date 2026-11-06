package com.lavagenesis.experience;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Small toolkit for audiovisual moments: timelines of actions run on later server ticks, plus sound, particle and
 * title helpers. Everything here is server side and cheap (a handful of packets per tick at most).
 */
final class Cues {
	private Cues() {}

	/** A list of actions at tick offsets, played for one player. */
	static final class Timeline {
		private record Step(int tick, Consumer<ServerPlayer> action) {}

		private final List<Step> steps = new ArrayList<>();
		private int length;

		Timeline at(int tick, Consumer<ServerPlayer> action) {
			steps.add(new Step(tick, action));
			length = Math.max(length, tick);
			return this;
		}

		/** Runs {@code action} every {@code every} ticks from {@code from} to {@code to} (inclusive). */
		Timeline every(int from, int to, int every, Consumer<ServerPlayer> action) {
			for (int t = from; t <= to; t += every) at(t, action);
			return this;
		}

		void play(ServerPlayer player) {
			RUNNING.add(new Running(player.getUUID(), this));
		}
	}

	private static final class Running {
		final UUID player;
		final Timeline timeline;
		int age;

		Running(UUID player, Timeline timeline) {
			this.player = player;
			this.timeline = timeline;
		}
	}

	private static final List<Running> RUNNING = new ArrayList<>();

	static void tick(MinecraftServer server) {
		if (RUNNING.isEmpty()) return;
		// copy: an action may start another timeline
		List<Running> now = new ArrayList<>(RUNNING);
		for (Running r : now) {
			ServerPlayer p = server.getPlayerList().getPlayer(r.player);
			if (p == null || p.isRemoved()) {
				RUNNING.remove(r);
				continue;
			}
			for (Timeline.Step s : r.timeline.steps) {
				if (s.tick() == r.age) {
					try {
						s.action().accept(p);
					} catch (RuntimeException e) {
						com.lavagenesis.LavaGenesis.LOGGER.warn("Lava Genesis cue failed", e);
					}
				}
			}
			r.age++;
			if (r.age > r.timeline.length) RUNNING.remove(r);
		}
	}

	static void clear() {
		RUNNING.clear();
	}

	// ---------------------------------------------------------------- sounds

	static void sound(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	static void sound(ServerPlayer p, Holder<SoundEvent> sound, float volume, float pitch) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Pitch of a note {@code semitones} away from F#3 (note block pitch 1.0 = F#4; 0.5 = F#3). */
	static float note(int semitones) {
		return (float) Math.pow(2.0, (semitones - 12) / 12.0);
	}

	// ---------------------------------------------------------------- particles

	static double centerY(ServerPlayer p) {
		return p.getY() + p.getBbHeight() * 0.5;
	}

	/** A cloud of particles around the body; spread grows with body size. */
	static void burst(ServerPlayer p, ParticleOptions type, int count, double spread, double speed) {
		ServerLevel level = p.level();
		level.sendParticles(type, p.getX(), centerY(p), p.getZ(), count, spread, spread, spread, speed);
	}

	/** A flat ring of particles flying outward from the body. */
	static void ring(ServerPlayer p, ParticleOptions type, int points, double radius, double y, double speed) {
		ServerLevel level = p.level();
		for (int i = 0; i < points; i++) {
			double a = Mth.TWO_PI * i / points;
			double cx = Math.cos(a), cz = Math.sin(a);
			// count 0: the offset is the particle's velocity
			level.sendParticles(type, p.getX() + cx * radius, p.getY() + y, p.getZ() + cz * radius, 0, cx, 0.08, cz, speed);
		}
	}

	/** A column of particles shooting straight up from the body (lava fountain / ash column). */
	static void column(ServerPlayer p, ParticleOptions type, int count, double height, double width, double speed) {
		ServerLevel level = p.level();
		RandomSource r = level.getRandom();
		for (int i = 0; i < count; i++) {
			double h = r.nextDouble() * height;
			double ox = (r.nextDouble() - 0.5) * width, oz = (r.nextDouble() - 0.5) * width;
			level.sendParticles(type, p.getX() + ox, p.getY() + p.getBbHeight() + h, p.getZ() + oz, 0,
				ox * 0.2, 1.0, oz * 0.2, speed);
		}
	}

	// ---------------------------------------------------------------- text

	static void title(ServerPlayer p, Component title, Component subtitle, int fadeIn, int stay, int fadeOut) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut));
		p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
		p.connection.send(new ClientboundSetTitleTextPacket(title));
	}
}
