package com.lavagenesis.land.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

/** Small shared helpers for the hot volcanic blocks. */
public final class Heat {
	private Heat() {}

	/** Magma-block style "hot floor" burn: skipped while sneaking, server side only. */
	public static void hotFloor(Level level, Entity entity, float damage, float igniteSeconds) {
		if (!(level instanceof ServerLevel) || entity.isSteppingCarefully() || !(entity instanceof LivingEntity)) return;
		entity.hurt(level.damageSources().hotFloor(), damage);
		if (igniteSeconds > 0 && !entity.fireImmune()) entity.igniteForSeconds(igniteSeconds);
	}

	/** True when any of the six neighbours holds water (source, flowing or waterlogged). */
	public static boolean touchesWater(BlockGetter level, BlockPos pos) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (Direction d : Direction.values()) {
			if (level.getFluidState(m.setWithOffset(pos, d)).is(FluidTags.WATER)) return true;
		}
		return false;
	}

	/** How many water blocks are stacked directly above {@code pos} (stops counting at {@code max}). */
	public static int waterAbove(BlockGetter level, BlockPos pos, int max) {
		BlockPos.MutableBlockPos m = pos.mutable();
		int n = 0;
		while (n < max && level.getFluidState(m.move(Direction.UP)).is(FluidTags.WATER)) n++;
		return n;
	}
}
