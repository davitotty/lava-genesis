package com.lavagenesis.experience.client;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Optional rows of the Lava Genesis HUD, filled by other modules' data. Each hook may stay null: the HUD then simply
 * leaves that row out (or shows the offering line without a count). {@link HudIntegrations} wires them to the vent
 * and eruption modules' client-readable state.
 */
public final class HudExtras {
	private HudExtras() {}

	/** Offering items already given at the home vent toward the NEXT stage; return -1 if unknown. */
	public static @Nullable ToIntFunction<LocalPlayer> offered;

	/** What the selected eruption power looks like right now, or null to hide the power row. */
	public static @Nullable Function<LocalPlayer, @Nullable PowerRow> power;

	/**
	 * @param name          the power's name
	 * @param icon          an item to draw as its icon (may be empty)
	 * @param color         RGB accent
	 * @param cost          pressure per use
	 * @param affordable    enough pressure for one use
	 * @param cooldownLeft  ticks until it is ready again (0 = ready)
	 * @param cooldownTotal the power's full cooldown in ticks (for the bar)
	 * @param unlocked      icons of every unlocked power, in order (drawn as a small strip when more than one)
	 * @param selectedIndex index of the selected power in {@code unlocked}
	 * @param keyHint       e.g. "R use  V switch  G Flare", or null
	 */
	public record PowerRow(Component name, ItemStack icon, int color, int cost, boolean affordable, int cooldownLeft,
		int cooldownTotal, List<ItemStack> unlocked, int selectedIndex, @Nullable Component keyHint) {}
}
