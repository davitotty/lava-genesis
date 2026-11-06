package com.lavagenesis.vent.client;

import com.lavagenesis.vent.VentBlocks;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Client entry point of the vent module (see DESIGN.md). Called from LavaGenesisClient. */
public final class VentClient {
	private VentClient() {}

	public static void init() {
		BlockEntityRenderers.register(VentBlocks.HOTSPOT_VENT_BE, HotspotVentRenderer::new);
	}
}
