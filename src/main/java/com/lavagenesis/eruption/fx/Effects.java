package com.lavagenesis.eruption.fx;

import com.lavagenesis.eruption.entity.LavaBomb;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The long-running spectacles of the eruption powers. */
public final class Effects {
	private Effects() {}

	private static boolean underwater(ServerLevel level, Vec3 p) {
		return level.getFluidState(BlockPos.containing(p)).is(FluidTags.WATER);
	}

	/** Where the steam of an underwater eruption breaks the surface. */
	private static double surfaceY(ServerLevel level, Vec3 p) {
		return Math.max(p.y, level.getSeaLevel());
	}

	/** Hurls a bomb from {@code from} to a random spot {@code minR..maxR} blocks around {@code center}. */
	public static void flingBomb(ServerLevel level, Vec3 from, Vec3 center, double minR, double maxR, ServerPlayer owner,
		int splash, float damage, float radius, boolean big) {
		RandomSource r = level.getRandom();
		double a = r.nextDouble() * Mth.TWO_PI;
		double d = minR + r.nextDouble() * (maxR - minR);
		Vec3 to = new Vec3(center.x + Math.cos(a) * d, center.y, center.z + Math.sin(a) * d);
		double reach = Math.sqrt((to.x - from.x) * (to.x - from.x) + (to.z - from.z) * (to.z - from.z));
		int ticks = 20 + r.nextInt(12) + (int) (reach * 0.6);
		LavaBomb.lob(level, from, to, ticks, owner, big ? Items.MAGMA_BLOCK : Items.FIRE_CHARGE, splash, damage, radius);
	}

	/** Eruption: a lava fountain over the new crater for a few seconds, flinging bombs, smoke and steam. */
	public static Fx.Ticker fountain(ServerPlayer owner, Vec3 crater, int duration, int bombs) {
		return (level, age) -> {
			RandomSource r = level.getRandom();
			boolean wet = underwater(level, crater);
			double x = crater.x, y = crater.y, z = crater.z;
			if (age == 0) {
				level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0F, 0.6F);
				level.playSound(null, x, y, z, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 1.5F, 0.5F);
				level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0, 0, 0, 0);
				Fx.shake(level, crater, 40, 1.2F, 25);
			}
			float fade = 1.0F - age / (float) duration;
			if (wet) {
				double sy = surfaceY(level, crater);
				level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 10, 0.8, 0.5, 0.8, 0.4);
				Fx.far(level, ParticleTypes.CLOUD, x, sy + 0.5, z, 6, 1.5, 0.6, 1.5, 0.08);
				Fx.far(level, ParticleTypes.WHITE_SMOKE, x, sy + 1.0, z, 4, 1.2, 1.0, 1.2, 0.05);
				if (age % 8 == 0) level.playSound(null, x, sy, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.5F, 0.6F);
			} else {
				for (int i = 0; i < 6 * fade + 2; i++) {
					Fx.jet(level, ParticleTypes.FLAME, x + r.nextGaussian() * 0.3, y, z + r.nextGaussian() * 0.3,
						r.nextGaussian() * 0.08, 0.5 + r.nextDouble() * 0.5, r.nextGaussian() * 0.08);
				}
				level.sendParticles(ParticleTypes.LAVA, x, y, z, (int) (6 * fade) + 1, 0.6, 0.3, 0.6, 0.0);
				Fx.far(level, ParticleTypes.LARGE_SMOKE, x, y + 1.5, z, 3, 0.8, 0.8, 0.8, 0.06);
				Fx.far(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y + 1.0, z, 1, 0.6, 0.3, 0.6, 0.01);
				if (age % 3 == 0) Fx.far(level, ParticleTypes.ASH, x, y + 6, z, 12, 4, 3, 4, 0.02);
				if (age % 10 == 0) level.playSound(null, x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.5F, 0.5F + r.nextFloat() * 0.3F);
				if (age % 14 == 0) level.playSound(null, x, y, z, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 2.0F, 0.7F);
			}
			if (bombs > 0 && age % Math.max(1, duration / bombs) == 0 && age < duration - 5) {
				flingBomb(level, crater.add(0, 0.6, 0), crater, 3, 11, owner, 4, 4.0F, 1.6F, r.nextInt(3) == 0);
			}
			return age < duration;
		};
	}

	/** Great Eruption: a towering ash column with lightning, a rain of bombs and the ground shaking. */
	public static Fx.Ticker ashColumn(ServerPlayer owner, Vec3 crater, int duration) {
		return (level, age) -> {
			RandomSource r = level.getRandom();
			double x = crater.x, y = crater.y, z = crater.z;
			if (age == 0) {
				level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 6.0F, 0.4F);
				level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8.0F, 0.5F);
				Fx.far(level, ParticleTypes.EXPLOSION_EMITTER, x, y + 1, z, 4, 2, 1, 2, 0);
			}
			if (age % 20 == 0) Fx.shake(level, crater, 110, age < 40 ? 3.0F : 1.6F * (1 - age / (float) duration) + 0.3F, 24);
			float fade = Math.max(0.25F, 1.0F - age / (float) duration);
			// the eruption column: signal smoke shoots high, big smoke billows, a lava fountain at the vent
			Fx.far(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, x, y + 1, z, (int) (5 * fade) + 1, 1.5, 0.5, 1.5, 0.04);
			for (int i = 0; i < 4; i++) {
				Fx.jet(level, ParticleTypes.LARGE_SMOKE, x + r.nextGaussian(), y + 2, z + r.nextGaussian(),
					r.nextGaussian() * 0.05, 0.6 + r.nextDouble() * 0.8, r.nextGaussian() * 0.05);
				Fx.jet(level, ParticleTypes.FLAME, x + r.nextGaussian() * 0.6, y + 0.5, z + r.nextGaussian() * 0.6,
					r.nextGaussian() * 0.12, 0.8 + r.nextDouble() * 0.7, r.nextGaussian() * 0.12);
			}
			level.sendParticles(ParticleTypes.LAVA, x, y + 0.5, z, (int) (10 * fade) + 2, 1.5, 0.5, 1.5, 0.0);
			// the ash cloud spreading at the top of the column, ash falling around it
			double cloud = y + 28 + r.nextDouble() * 18;
			Fx.far(level, ParticleTypes.LARGE_SMOKE, x, cloud, z, 10, 10, 4, 10, 0.02);
			Fx.far(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, x, cloud, z, 4, 12, 3, 12, 0.005);
			Fx.far(level, ParticleTypes.ASH, x, y + 14, z, 40, 18, 12, 18, 0.02);
			Fx.far(level, ParticleTypes.WHITE_ASH, x, y + 10, z, 20, 14, 8, 14, 0.02);
			// lightning crackling inside the ash cloud
			if (age > 20 && r.nextInt(14) == 0) {
				LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.setVisualOnly(true);
					bolt.setPos(x + r.nextGaussian() * 9, y + 24 + r.nextDouble() * 12, z + r.nextGaussian() * 9);
					level.addFreshEntity(bolt);
				}
			}
			if (age % 5 == 0 && age < duration - 20) {
				flingBomb(level, crater.add(0, 1.0, 0), crater, 6, 26, owner, 7, 7.0F, 2.2F, true);
			}
			if (age % 12 == 0) level.playSound(null, x, y, z, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 4.0F, 0.5F);
			if (age % 25 == 0) level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 3.0F, 0.5F + r.nextFloat() * 0.2F);
			return age < duration;
		};
	}

	/** Bomb Rain: a volley of big bombs hurled from the player over a wide area around the target. */
	public static Fx.Ticker bombVolley(ServerPlayer owner, Vec3 target, int bombs, double spread) {
		return (level, age) -> {
			if (owner.isRemoved()) return false;
			RandomSource r = level.getRandom();
			Vec3 from = owner.getEyePosition().add(0, 0.6, 0);
			if (age % 3 == 0) {
				flingBomb(level, from, target, 0, spread, owner, 10 + r.nextInt(6), 6.0F, 2.5F, true);
				Fx.jet(level, ParticleTypes.FLAME, from.x, from.y, from.z, 0, 0.3, 0);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 4, 0.3, 0.3, 0.3, 0.05);
				level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.5F + r.nextFloat() * 0.3F);
				if (age % 9 == 0) Fx.shake(level, owner.position(), 24, 0.6F, 6);
			}
			return age < bombs * 3;
		};
	}
}
