package com.lavagenesis.eruption.client;

import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import com.lavagenesis.eruption.EruptionState;
import com.lavagenesis.eruption.Power;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Read-only view of the local player's eruption powers for HUDs (experience module) and anything else on the
 * client. All methods are safe to call every frame and when no world is loaded.
 * <ul>
 *   <li>{@link #selected()} - the selected power (what the "use power" key fires)</li>
 *   <li>{@link #unlockedPowers()} / {@link #unlocked(Power)} - powers available at the player's stage</li>
 *   <li>{@link #cooldownFraction(Power)} - 1 right after use, falling to 0 when ready; {@link #cooldownTicksLeft(Power)}</li>
 *   <li>{@link #pressure()} / {@link #canAfford(Power)} / {@link #ready(Power)} (unlocked, cooled down, affordable)</li>
 *   <li>names, descriptions, icons and colours live on {@link Power}: {@code displayName()}, {@code description()},
 *       {@code icon()}, {@code color}, {@code cost}, {@code cooldownTicks}, {@code stage}</li>
 *   <li>{@link #useKey()}, {@link #cycleKey()}, {@link #flareKey()} - the key mappings, e.g. for key hints</li>
 * </ul>
 */
public final class EruptionClientState {
	private EruptionClientState() {}

	private static @Nullable Power predictedSelection;
	private static long predictedAt;

	private static @Nullable LocalPlayer player() {
		return Minecraft.getInstance().player;
	}

	public static MagmaStage stage() {
		LocalPlayer p = player();
		return p == null ? MagmaStage.EMBER : MagmaCore.get(p).stage();
	}

	public static int pressure() {
		LocalPlayer p = player();
		return p == null ? 0 : MagmaCore.get(p).pressure();
	}

	public static Power selected() {
		LocalPlayer p = player();
		if (p == null) return Power.FLARE;
		Power synced = EruptionState.get(p).selectedPower();
		if (predictedSelection != null) {
			// shown at once after the key press, until the server's answer arrives
			if (predictedSelection == synced || p.level().getGameTime() - predictedAt > 20) predictedSelection = null;
			else return predictedSelection;
		}
		return synced.unlockedAt(stage()) ? synced : Power.FLARE;
	}

	static void predictSelection(Power p) {
		LocalPlayer pl = player();
		predictedSelection = p;
		predictedAt = pl == null ? 0 : pl.level().getGameTime();
	}

	public static boolean unlocked(Power power) {
		return power.unlockedAt(stage());
	}

	public static List<Power> unlockedPowers() {
		List<Power> list = new ArrayList<>();
		MagmaStage s = stage();
		for (Power p : Power.values()) if (p.unlockedAt(s)) list.add(p);
		return list;
	}

	public static int cooldownTicksLeft(Power power) {
		LocalPlayer p = player();
		if (p == null) return 0;
		long left = EruptionState.get(p).readyAt(power) - p.level().getGameTime();
		return (int) Math.max(0, Math.min(left, power.cooldownTicks));
	}

	/** 1.0 just after use, falling to 0.0 when the power is ready again. */
	public static float cooldownFraction(Power power) {
		return power.cooldownTicks <= 0 ? 0.0F : cooldownTicksLeft(power) / (float) power.cooldownTicks;
	}

	public static boolean canAfford(Power power) {
		LocalPlayer p = player();
		if (p == null) return false;
		MagmaData d = MagmaCore.get(p);
		return p.hasInfiniteMaterials() || d.pressure() >= power.cost;
	}

	/** Unlocked, cooled down and affordable. */
	public static boolean ready(Power power) {
		return unlocked(power) && cooldownTicksLeft(power) == 0 && canAfford(power);
	}

	public static Component name(Power power) {
		return power.displayName();
	}

	public static ItemStack icon(Power power) {
		return power.icon();
	}

	public static KeyMapping useKey() {
		return EruptionClient.USE;
	}

	public static KeyMapping cycleKey() {
		return EruptionClient.CYCLE;
	}

	public static KeyMapping flareKey() {
		return EruptionClient.FLARE;
	}
}
