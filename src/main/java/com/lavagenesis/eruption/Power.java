package com.lavagenesis.eruption;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.LavaIds;
import com.lavagenesis.core.MagmaStage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * The active eruption powers, one unlocked per stage (DESIGN.md powers table). Shared by both sides: the server
 * validates and runs them, the client shows them (HUD, key hints). The ordinal is the network id.
 */
public enum Power {
	//            id                 unlocked at                 cost  cooldown repeat  colour     icon (lavagenesis id, vanilla fallback)
	FLARE("flare",                   MagmaStage.EMBER,            1,    5,  true,  0xFFD25A, null, Items.FIRE_CHARGE),
	LAVA_SPIT("lava_spit",           MagmaStage.MAGMA_DROPLET,    6,   16,  true,  0xFF9A2A, null, Items.MAGMA_CREAM),
	FLOW_SURGE("flow_surge",         MagmaStage.LAVA_FLOW,       18,   70,  false, 0xFF7414, null, Items.LAVA_BUCKET),
	PILLOW_MOUND("pillow_mound",     MagmaStage.SEAMOUNT,        28,  110,  false, 0xD2501E, LavaIds.FRESH_BASALT, Items.BASALT),
	ERUPTION("eruption",             MagmaStage.ISLET,           40,  180,  false, 0xFF5A0A, LavaIds.COOLING_LAVA, Items.MAGMA_BLOCK),
	SHIELD_FLOW("shield_flow",       MagmaStage.SHIELD_ISLAND,   50,  240,  false, 0xE8641E, LavaIds.BLACK_SAND, Items.SMOOTH_BASALT),
	HOTSPOT_LEAP("hotspot_leap",     MagmaStage.SHIELD_ISLAND,   70,  600,  false, 0xFFB43C, LavaIds.HOTSPOT_VENT, Items.FIREWORK_ROCKET),
	BOMB_RAIN("bomb_rain",           MagmaStage.ARCHIPELAGO,     60,  320,  false, 0xFF6A00, LavaIds.MOLTEN_ROCK, Items.FIRE_CHARGE),
	GREAT_ERUPTION("great_eruption", MagmaStage.GREAT_VOLCANO,  100, 1200,  false, 0xFF3300, LavaIds.HOTSPOT_HEART, Items.NETHER_STAR);

	private static final Power[] VALUES = values();

	public final String id;
	/** The stage at which the power unlocks. */
	public final MagmaStage stage;
	/** Pressure spent per use (0..100). */
	public final int cost;
	/** Cooldown after a use, in ticks. */
	public final int cooldownTicks;
	/** Holding the key keeps using the power whenever it is ready (Flare, Lava Spit). */
	public final boolean repeatable;
	/** RGB colour for HUD accents. */
	public final int color;
	private final @Nullable Identifier iconId;
	private final Item iconFallback;

	Power(String id, MagmaStage stage, int cost, int cooldownTicks, boolean repeatable, int color,
		@Nullable Identifier iconId, Item iconFallback) {
		this.id = id;
		this.stage = stage;
		this.cost = cost;
		this.cooldownTicks = cooldownTicks;
		this.repeatable = repeatable;
		this.color = color;
		this.iconId = iconId;
		this.iconFallback = iconFallback;
	}

	public static @Nullable Power byId(int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
	}

	public static int count() {
		return VALUES.length;
	}

	public boolean unlockedAt(MagmaStage s) {
		return s.atLeast(stage);
	}

	/** Translation key "power.lavagenesis.&lt;id&gt;". */
	public String translationKey() {
		return "power." + LavaGenesis.MOD_ID + "." + id;
	}

	public Component displayName() {
		return Component.translatable(translationKey());
	}

	/** Translation key "power.lavagenesis.&lt;id&gt;.desc": one line about what the power does. */
	public Component description() {
		return Component.translatable(translationKey() + ".desc");
	}

	/** An item to draw as the power's icon (a Lava Genesis item when the land/vent module provides it, else vanilla). */
	public ItemStack icon() {
		if (iconId != null) {
			Item item = LavaIds.item(iconId);
			if (item != null && item != Items.AIR) return new ItemStack(item);
		}
		return new ItemStack(iconFallback);
	}

	/** The next power after this one that is unlocked at {@code s} (wraps around; Flare is always unlocked). */
	public Power next(MagmaStage s, int dir) {
		int n = VALUES.length;
		for (int i = 1; i <= n; i++) {
			Power p = VALUES[Math.floorMod(ordinal() + i * dir, n)];
			if (p.unlockedAt(s)) return p;
		}
		return FLARE;
	}
}
