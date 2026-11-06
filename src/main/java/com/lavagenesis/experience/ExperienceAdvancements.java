package com.lavagenesis.experience;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Lava Genesis advancement tree ({@code data/lavagenesis/advancement/experience/*.json}). Every advancement there
 * has one {@code minecraft:impossible} criterion named "granted" and is awarded from here.
 * <p>
 * Ids: {@code lavagenesis:experience/<name>}. One per stage ({@code experience/<stage name>}, e.g.
 * {@code experience/seamount}) plus the fun ones listed as constants below. Other modules may call
 * {@link #grant(ServerPlayer, String)} with any of these names.
 */
public final class ExperienceAdvancements {
	private ExperienceAdvancements() {}

	public static final String ROOT = "root";
	public static final String FIRST_MELT = "first_melt";
	public static final String FIRST_SIP = "first_sip";
	public static final String FIRST_MEAL = "first_meal";
	public static final String UPWELLING = "upwelling";
	public static final String FIRST_OFFERING = "first_offering";
	public static final String LAND_FROM_FIRE = "land_from_fire";
	public static final String FIRST_ISLAND = "first_island";
	public static final String THREE_ISLANDS = "three_islands";
	public static final String SIX_ISLANDS = "six_islands";
	public static final String BIG_ISLAND = "big_island";
	public static final String LAND_BARON = "land_baron";
	public static final String CLOSE_TO_STONE = "close_to_stone";
	public static final String HEAVIER_THAN_MOUNTAINS = "heavier_than_mountains";

	public static final int BIG_ISLAND_COLUMNS = 1000;
	public static final int LAND_BARON_COLUMNS = 5000;
	public static final int HEAVIER_MASS = 10000;
	public static final float CLOSE_TO_STONE_HEAT = 0.1F;

	public static Identifier id(String name) {
		return LavaGenesis.id("experience/" + name);
	}

	/** Awards every remaining criterion of {@code lavagenesis:experience/<name>}. Returns true if it was newly completed. */
	public static boolean grant(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(id(name));
		if (holder == null) return false;
		PlayerAdvancements adv = player.getAdvancements();
		AdvancementProgress progress = adv.getOrStartProgress(holder);
		if (progress.isDone()) return false;
		List<String> remaining = new ArrayList<>();
		progress.getRemainingCriteria().forEach(remaining::add);
		boolean any = false;
		for (String c : remaining) any |= adv.award(holder, c);
		return any;
	}

	public static boolean has(ServerPlayer player, String name) {
		AdvancementHolder holder = player.level().getServer().getAdvancements().get(id(name));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** Every stage advancement up to and including {@code stage}. */
	static void grantStagesUpTo(ServerPlayer player, MagmaStage stage) {
		for (MagmaStage s : MagmaStage.values()) {
			if (s.ordinal() > stage.ordinal()) break;
			if (s != MagmaStage.EMBER) grant(player, s.getSerializedName());
		}
	}

	/** Mass-reason driven ones (called from MASS_GAINED). */
	static void onMassReason(ServerPlayer player, String reason) {
		switch (reason) {
			case "melt" -> grant(player, FIRST_MELT);
			case "eat" -> grant(player, FIRST_MEAL);
			case "upwelling" -> grant(player, UPWELLING);
			case "offering" -> grant(player, FIRST_OFFERING);
			case "eruption" -> grant(player, LAND_FROM_FIRE);
			default -> {
				if (reason.startsWith("absorb")) grant(player, FIRST_SIP);
			}
		}
	}

	/** Cheap state checks, run about once a second per player. */
	static void periodicCheck(ServerPlayer player) {
		MagmaData d = MagmaCore.get(player);
		grant(player, ROOT);
		grantStagesUpTo(player, d.stage());
		if (d.islands() >= 1) grant(player, FIRST_ISLAND);
		if (d.islands() >= 3) grant(player, THREE_ISLANDS);
		if (d.islands() >= 6) grant(player, SIX_ISLANDS);
		if (d.largestIsland() >= BIG_ISLAND_COLUMNS) grant(player, BIG_ISLAND);
		if (d.totalLand() >= LAND_BARON_COLUMNS) grant(player, LAND_BARON);
		if (d.mass() >= HEAVIER_MASS) grant(player, HEAVIER_THAN_MOUNTAINS);
		if (d.started() && d.heat() <= CLOSE_TO_STONE_HEAT && player.isAlive()) grant(player, CLOSE_TO_STONE);
	}
}
