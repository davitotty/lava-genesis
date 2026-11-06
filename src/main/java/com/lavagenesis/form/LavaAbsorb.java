package com.lavagenesis.form;

import com.lavagenesis.core.LavaIds;
import com.lavagenesis.core.MagmaCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sneaking while in or next to lava draws it into the body, one block every {@link #INTERVAL} ticks:
 * a lava source block gives {@value #LAVA_MASS} mass, a magma block {@value #MAGMA_MASS} (it is left behind as basalt).
 * Molten rock is never absorbed, nor anything within {@value #VENT_GUARD} blocks of a Hotspot Vent, so the
 * birth chamber stays playable.
 */
public final class LavaAbsorb {
	private LavaAbsorb() {}

	public static final int INTERVAL = 8;
	public static final int LAVA_MASS = 3;
	public static final int MAGMA_MASS = 4;
	public static final int VENT_GUARD = 2;

	static void tick(ServerPlayer player, FormRuntime rt) {
		if (rt.absorbCooldown > 0) {
			rt.absorbCooldown--;
			return;
		}
		if (!player.isShiftKeyDown() || player.isPassenger()) return;
		ServerLevel level = player.level();
		BlockPos target = findTarget(level, player);
		if (target == null) {
			rt.absorbCooldown = 4;
			return;
		}
		rt.absorbCooldown = INTERVAL;
		BlockState state = level.getBlockState(target);
		boolean magma = state.is(Blocks.MAGMA_BLOCK);
		level.setBlock(target, magma ? Blocks.BASALT.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		effects(level, player, target);
		MagmaCore.addMass(player, magma ? MAGMA_MASS : LAVA_MASS, "absorb_lava");
		HeatSystem.addHeat(player, 0.04F);
	}

	private static @Nullable BlockPos findTarget(ServerLevel level, ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(1.0, 0.75, 1.0);
		BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
		BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
		Vec3 center = player.getBoundingBox().getCenter();
		Block molten = LavaIds.block(LavaIds.MOLTEN_ROCK);
		Block vent = LavaIds.block(LavaIds.HOTSPOT_VENT);
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			BlockState s = level.getBlockState(p);
			boolean lava = s.is(Blocks.LAVA) && s.getFluidState().isSource() && s.getFluidState().is(FluidTags.LAVA);
			boolean magma = s.is(Blocks.MAGMA_BLOCK);
			if (!lava && !magma) continue;
			if (molten != Blocks.AIR && s.is(molten)) continue;
			double d = center.distanceToSqr(Vec3.atCenterOf(p));
			if (d >= bestDist) continue;
			if (vent != Blocks.AIR && nearBlock(level, p, vent, VENT_GUARD)) continue;
			best = p.immutable();
			bestDist = d;
		}
		return best;
	}

	private static boolean nearBlock(ServerLevel level, BlockPos pos, Block block, int r) {
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -r, -r), pos.offset(r, r, r))) {
			if (level.getBlockState(p).is(block)) return true;
		}
		return false;
	}

	private static void effects(ServerLevel level, ServerPlayer player, BlockPos from) {
		Vec3 a = Vec3.atCenterOf(from);
		Vec3 b = player.getBoundingBox().getCenter();
		var rnd = player.getRandom();
		level.playSound(null, a.x, a.y, a.z, SoundEvents.BUCKET_FILL_LAVA, SoundSource.PLAYERS, 0.6F, 1.1F + rnd.nextFloat() * 0.3F);
		level.playSound(null, a.x, a.y, a.z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.5F, 0.8F + rnd.nextFloat() * 0.4F);
		level.sendParticles(ParticleTypes.LAVA, a.x, a.y, a.z, 3, 0.25, 0.25, 0.25, 0.0);
		for (int i = 1; i <= 6; i++) {
			double t = i / 7.0;
			level.sendParticles(ParticleTypes.SMALL_FLAME, Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y), Mth.lerp(t, a.z, b.z),
				1, 0.03, 0.03, 0.03, 0.005);
		}
		level.sendParticles(ParticleTypes.FALLING_LAVA, b.x, b.y, b.z, 2, player.getBbWidth() * 0.4, player.getBbHeight() * 0.3,
			player.getBbWidth() * 0.4, 0.0);
	}
}
