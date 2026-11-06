package com.lavagenesis.eruption.land;

import com.lavagenesis.core.LavaIds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The blocks eruptions build with. The land module's blocks are looked up at runtime through {@link LavaIds};
 * if one is missing (lookup gives air) a vanilla block stands in, so the eruption module works on its own.
 */
public final class Palette {
	private Palette() {}

	/** Glowing fresh lava rock that cools by itself ({@code age} 0 when it has one). */
	public static BlockState coolingLava() {
		return ageZero(lookup(LavaIds.COOLING_LAVA, Blocks.MAGMA_BLOCK));
	}

	/** Liquid-hot rock for craters. */
	public static BlockState moltenRock() {
		return ageZero(lookup(LavaIds.MOLTEN_ROCK, Blocks.MAGMA_BLOCK));
	}

	/** Already-cooled rock for the cores of big flows. */
	public static BlockState freshBasalt() {
		return lookup(LavaIds.FRESH_BASALT, Blocks.BASALT);
	}

	public static BlockState blackSand() {
		return lookup(LavaIds.BLACK_SAND, Blocks.SMOOTH_BASALT);
	}

	public static boolean isVent(BlockState state) {
		Block vent = LavaIds.block(LavaIds.HOTSPOT_VENT);
		return vent != Blocks.AIR && state.is(vent);
	}

	private static BlockState lookup(Identifier id, Block fallback) {
		Block b = LavaIds.block(id);
		return b == null || b == Blocks.AIR ? fallback.defaultBlockState() : b.defaultBlockState();
	}

	private static BlockState ageZero(BlockState state) {
		Property<?> p = state.getBlock().getStateDefinition().getProperty("age");
		if (p instanceof IntegerProperty age) {
			int min = age.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(0);
			return state.setValue(age, min);
		}
		return state;
	}

	/**
	 * Whether lava may flow into / new rock may be placed at this cell: air, water, lava, plants and other soft
	 * replaceable things. Never bedrock, block entities or vents.
	 */
	public static boolean isFree(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.isAir()) return true;
		if (state.hasBlockEntity() || state.is(Blocks.BEDROCK) || isVent(state)) return false;
		if (state.canBeReplaced()) return true;
		if (state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT) || state.is(Blocks.FIRE)) return true;
		// plants, flowers, torches: no collision and break instantly
		return state.getCollisionShape(level, pos).isEmpty() && state.getDestroySpeed(level, pos) == 0.0F;
	}

	/** New land only counts for mass when it replaced air, water or plants (not lava, not rebuilt rock). */
	public static boolean countsForMass(BlockState replaced) {
		if (replaced.getFluidState().is(FluidTags.LAVA)) return false;
		return true;
	}
}
