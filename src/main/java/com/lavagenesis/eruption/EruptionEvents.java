package com.lavagenesis.eruption;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Server-side events of the eruption module, e.g. for advancements and sounds in the experience module. */
public final class EruptionEvents {
	private EruptionEvents() {}

	@FunctionalInterface
	public interface PowerUsed {
		/** After a power was used successfully (pressure spent, cooldown started). */
		void onPowerUsed(ServerPlayer player, Power power);
	}

	public static final Event<PowerUsed> POWER_USED = EventFactory.createArrayBacked(PowerUsed.class,
		listeners -> (player, power) -> {
			for (PowerUsed l : listeners) l.onPowerUsed(player, power);
		});

	@FunctionalInterface
	public interface IslandSeeded {
		/** After Hotspot Leap raised a seed island and its island vent was placed at {@code ventPos}. */
		void onIslandSeeded(ServerPlayer player, ServerLevel level, BlockPos ventPos);
	}

	public static final Event<IslandSeeded> ISLAND_SEEDED = EventFactory.createArrayBacked(IslandSeeded.class,
		listeners -> (player, level, pos) -> {
			for (IslandSeeded l : listeners) l.onIslandSeeded(player, level, pos);
		});
}
