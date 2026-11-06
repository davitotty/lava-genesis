package com.lavagenesis.core;

import com.lavagenesis.LavaGenesis;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

/**
 * How other modules create Hotspot Vents without depending on the vent module's classes.
 * The vent module installs the real implementation in {@link #install} during its init; until then (or if the
 * vent module is broken) the fallback just places the block.
 */
public final class VentAccess {
	private VentAccess() {}

	public interface Impl {
		/**
		 * Places a Hotspot Vent at {@code pos} owned by {@code owner}.
		 * {@code home == true}: it becomes the player's home vent (offerings, respawn, finale) - used by the birth volcano.
		 * {@code home == false}: an island vent seeding a new island (eruption powers use this).
		 * Returns false if a vent could not be placed there.
		 */
		boolean placeVent(ServerPlayer owner, ServerLevel level, BlockPos pos, boolean home);
	}

	private static Impl impl = (owner, level, pos, home) -> {
		Block vent = LavaIds.block(LavaIds.HOTSPOT_VENT);
		if (vent == null) return false;
		LavaGenesis.LOGGER.warn("VentAccess fallback used - vent module did not install its implementation");
		return level.setBlockAndUpdate(pos, vent.defaultBlockState());
	};

	/** Vent module only. */
	public static void install(Impl implementation) {
		impl = implementation;
	}

	public static boolean placeVent(ServerPlayer owner, ServerLevel level, BlockPos pos, boolean home) {
		return impl.placeVent(owner, level, pos, home);
	}
}
