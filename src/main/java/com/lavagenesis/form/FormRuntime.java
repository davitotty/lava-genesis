package com.lavagenesis.form;

import com.lavagenesis.core.MagmaStage;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/** Per-player server-side bookkeeping of the form module (not saved; rebuilt from MagmaData on join). */
final class FormRuntime {
	private static final Map<UUID, FormRuntime> ALL = new HashMap<>();

	static FormRuntime of(ServerPlayer player) {
		return ALL.computeIfAbsent(player.getUUID(), u -> new FormRuntime());
	}

	static void forget(ServerPlayer player) {
		ALL.remove(player.getUUID());
	}

	static void clear() {
		ALL.clear();
	}

	// body size
	@Nullable MagmaStage appliedStage;      // stage whose attributes are currently applied (null = re-apply instantly)
	float growFrom = -1.0F;                 // scale at the start of the growth animation
	float growTo = -1.0F;
	int growTick = -1;                      // ticks into the growth animation, -1 when idle
	int noSuffocateUntil;                   // server tick count until which in-wall damage is ignored (after growing)

	// heat
	float heat = -1.0F;                     // precise heat; MagmaData only gets meaningful steps
	int warmthCache;                        // 0..2 how much nearby fire/lava there is (refreshed once a second)
	int lastHeatWarning = -1;               // last crust level we warned the player about
	int solidifyTimer;

	// absorbing
	int absorbCooldown;

	// passives
	@Nullable BlockPos lastWalkPos;
	boolean grantedFlight;
	int steamSoundCooldown;
}
