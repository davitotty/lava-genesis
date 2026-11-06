package com.lavagenesis.land;

import com.lavagenesis.LavaGenesis;
import net.minecraft.resources.Identifier;

/**
 * Extra land-module ids that are not in core {@code LavaIds} but are useful to other modules (e.g. the eruption
 * module placing pillow lava or ash). Plain identifiers only: look the block up at runtime with
 * {@code LavaIds.block(LandIds.PILLOW_LAVA)}, never in a static initialiser.
 */
public final class LandIds {
	private LandIds() {}

	/** Rounded underwater-cooled lava; what a seamount is built of. Plain block. */
	public static final Identifier PILLOW_LAVA = LavaGenesis.id("pillow_lava");
	/** Grey falling ash (like sand). Weathers into volcanic soil above sea level. */
	public static final Identifier VOLCANIC_ASH = LavaGenesis.id("volcanic_ash");
	/** Glossy quenched basalt; drops Volcanic Glass. */
	public static final Identifier GLASSY_BASALT = LavaGenesis.id("glassy_basalt");
	/** Steaming gas vent; grows sulfur crystal clusters on its top face. */
	public static final Identifier FUMAROLE = LavaGenesis.id("fumarole");
	/** Sulfur crystals on a fumarole: properties age (0..2) and waterlogged. No block item. */
	public static final Identifier SULFUR_CLUSTER = LavaGenesis.id("sulfur_cluster");
	/** Olivine-bearing basalt; drops Olivine. */
	public static final Identifier OLIVINE_ORE = LavaGenesis.id("olivine_ore");
	public static final Identifier BASALT_BRICKS = LavaGenesis.id("basalt_bricks");
	public static final Identifier BLACK_SAND_GLASS = LavaGenesis.id("black_sand_glass");
	public static final Identifier HAPUU_FERN = LavaGenesis.id("hapuu_fern");
	public static final Identifier SILVERSWORD = LavaGenesis.id("silversword");
	public static final Identifier OHIA_SAPLING = LavaGenesis.id("ohia_sapling");
	public static final Identifier PALM_SAPLING = LavaGenesis.id("palm_sapling");
	public static final Identifier OHIA_LOG = LavaGenesis.id("ohia_log");
	public static final Identifier PALM_LOG = LavaGenesis.id("palm_log");
	public static final Identifier OHIA_LEAVES = LavaGenesis.id("ohia_leaves");
	public static final Identifier PALM_FRONDS = LavaGenesis.id("palm_fronds");
	public static final Identifier OHIA_PLANKS = LavaGenesis.id("ohia_planks");
	public static final Identifier PALM_PLANKS = LavaGenesis.id("palm_planks");
}
