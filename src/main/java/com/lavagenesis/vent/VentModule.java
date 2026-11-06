package com.lavagenesis.vent;

import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.VentAccess;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * Entry point of the vent module (see DESIGN.md). Called from LavaGenesis.onInitialize.
 * <ul>
 *   <li>{@link VentBlocks} - the Hotspot Vent block, item and block entity</li>
 *   <li>{@link VentRegistry} - ownership, home vent, {@code VentAccess} implementation, island totals</li>
 *   <li>{@link IslandSurveyor} - incremental island flood fills, results kept in {@link VentRecords}</li>
 *   <li>{@link VentProgression} - offerings, requirement checklist, stage advancement, hotspot upwelling</li>
 *   <li>{@link VentProgress} - public read API (both sides) for the HUD</li>
 *   <li>{@link VentRespawn} - respawning at the home vent</li>
 *   <li>{@link VentFinale} - the Great Volcano</li>
 * </ul>
 */
public final class VentModule {
	private VentModule() {}

	public static void init() {
		VentBlocks.init();
		VentProgress.init();
		VentAccess.install(VentRegistry::placeVent);

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			IslandSurveyor.tick(server);
			VentProgression.tick(server);
			VentFinale.tick(server);
		});
		ServerPlayerEvents.JOIN.register(player -> {
			VentRegistry.validateHome(player);
			VentRegistry.updateIslands(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register(VentRespawn::afterRespawn);
		MagmaCore.STAGE_CHANGED.register((player, from, to) -> {
			VentProgress.reset(player);
			VentFinale.onStageChanged(player, from, to);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			IslandSurveyor.clear();
			VentFinale.clear();
		});
	}
}
