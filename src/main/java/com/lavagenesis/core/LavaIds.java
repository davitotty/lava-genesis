package com.lavagenesis.core;

import com.lavagenesis.LavaGenesis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Ids shared between modules. A module that needs another module's block or item looks it up here AT RUNTIME
 * (never in a static initialiser), so every module compiles and loads on its own. The owning module is noted.
 */
public final class LavaIds {
	private LavaIds() {}

	// vent module
	public static final Identifier HOTSPOT_VENT = LavaGenesis.id("hotspot_vent");          // block + block item

	// land module - stage offerings
	public static final Identifier MAGMA_SHARD = LavaGenesis.id("magma_shard");
	public static final Identifier OLIVINE = LavaGenesis.id("olivine");
	public static final Identifier PELES_TEAR = LavaGenesis.id("peles_tear");
	public static final Identifier SULFUR_CRYSTAL = LavaGenesis.id("sulfur_crystal");
	public static final Identifier VOLCANIC_GLASS = LavaGenesis.id("volcanic_glass");
	public static final Identifier HOTSPOT_HEART = LavaGenesis.id("hotspot_heart");

	// land module - the land itself
	/** Glowing fresh lava rock that cools step by step (random ticks) into basalt. What eruptions place. */
	public static final Identifier COOLING_LAVA = LavaGenesis.id("cooling_lava");
	/** Lava that stays liquid-hot: a non-flowing, light-emitting "molten" block used for vents and craters. */
	public static final Identifier MOLTEN_ROCK = LavaGenesis.id("molten_rock");
	public static final Identifier FRESH_BASALT = LavaGenesis.id("fresh_basalt");
	public static final Identifier BLACK_SAND = LavaGenesis.id("black_sand");
	public static final Identifier VOLCANIC_SOIL = LavaGenesis.id("volcanic_soil");
	/** Lush green-topped volcanic soil: the last step of island plant succession (still counts as volcanic land). */
	public static final Identifier LUSH_VOLCANIC_SOIL = LavaGenesis.id("lush_volcanic_soil");

	// experience module
	public static final Identifier LAVA_JOURNAL = LavaGenesis.id("lava_journal");         // the guide book item

	/**
	 * Block tag (land module): every block that counts as land of the player's islands when it is the top solid
	 * block of a column at or above sea level. The vent module counts island size with it.
	 */
	public static final TagKey<Block> VOLCANIC_LAND = TagKey.create(Registries.BLOCK, LavaGenesis.id("volcanic_land"));

	public static Item item(Identifier id) {
		return BuiltInRegistries.ITEM.getValue(id);
	}

	public static Block block(Identifier id) {
		return BuiltInRegistries.BLOCK.getValue(id);
	}
}
