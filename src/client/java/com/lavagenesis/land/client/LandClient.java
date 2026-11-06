package com.lavagenesis.land.client;

/**
 * Client entry point of the land module (see DESIGN.md). Called from LavaGenesisClient.
 * <p>
 * The land module needs no client registrations in 26.3: cutout/translucent layers for the plants, leaves and
 * black sand glass are inferred from their textures' alpha, the glowing lava blocks use emissive rendering set in
 * their block properties, all ambient particles and sounds (lava pops, steam, sulfur smoke, bubbles) come from the
 * blocks' own {@code animateTick}, and item descriptions are a server-side lore component.
 */
public final class LandClient {
	private LandClient() {}

	public static void init() {
		// intentionally empty - see the class comment
	}
}
