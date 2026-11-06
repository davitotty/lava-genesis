package com.lavagenesis.vent;

import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** A player with a bound home vent respawns at it (in its lava while still small) instead of at world spawn. */
public final class VentRespawn {
	private VentRespawn() {}

	static void afterRespawn(ServerPlayer oldPlayer, ServerPlayer player, boolean alive) {
		if (alive) return;                                  // leaving the End, not a death
		if (player.getRespawnConfig() != null) return;      // a bed or respawn anchor wins
		MagmaData d = MagmaCore.get(player);
		if (d.homeVent().isEmpty() || !VentRegistry.hasHomeVent(player)) return;
		sendHome(player, d.homeVent().get(), d.stage());
	}

	/** Moves the player next to their home vent. Returns false if the vent's dimension is gone. */
	public static boolean sendHome(ServerPlayer player, GlobalPos home, MagmaStage stage) {
		ServerLevel level = player.level().getServer().getLevel(home.dimension());
		if (level == null) return false;
		BlockPos vent = home.pos();
		level.getChunk(vent.getX() >> 4, vent.getZ() >> 4); // load it (respawn is rare)
		BlockPos spot = findSpot(level, vent, stage, player.getBbHeight());
		if (spot == null) spot = vent.above();
		player.teleportTo(level, spot.getX() + 0.5, spot.getY() + 0.05, spot.getZ() + 0.5, Set.of(), player.getYRot(), 20.0F, true);
		level.sendParticles(ParticleTypes.LAVA, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.1);
		level.sendParticles(ParticleTypes.FLAME, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 20, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, spot, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}

	/**
	 * Early stages wake right above the vent, in its lava. Later (bigger) bodies get the first spot above or around
	 * the vent with enough free height; fluids count as free - the player is molten rock.
	 */
	static @Nullable BlockPos findSpot(ServerLevel level, BlockPos vent, MagmaStage stage, float bodyHeight) {
		int need = Math.max(1, (int) Math.ceil(Math.max(bodyHeight, 1.8F * stage.scale)));
		if (!stage.isAboveWater() && free(level, vent.above(), 1)) return vent.above();
		int maxY = level.getMaxY() - need;
		for (int r = 0; r <= 4; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
					for (int y = vent.getY() + 1; y <= Math.min(maxY, vent.getY() + 48); y++) {
						BlockPos p = new BlockPos(vent.getX() + dx, y, vent.getZ() + dz);
						if (free(level, p, need) && !free(level, p.below(), 1)) return p;
					}
				}
			}
		}
		return null;
	}

	private static boolean free(ServerLevel level, BlockPos p, int height) {
		for (int i = 0; i < height; i++) {
			BlockPos q = p.above(i);
			BlockState s = level.getBlockState(q);
			if (!s.getCollisionShape(level, q).isEmpty()) return false;
		}
		return true;
	}
}
