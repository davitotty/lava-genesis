package com.lavagenesis.form;

import com.lavagenesis.core.MagmaStage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Movement rules of the lava body, called from the mixins (both sides). */
public final class FormMovement {
	private FormMovement() {}

	/**
	 * Replaces vanilla's sticky lava movement (0.02 acceleration, halved every tick) with water-like swimming.
	 * Sneaking sinks; jumping rises (see the jumpInLiquid mixin); sprinting swims along the look direction.
	 */
	public static void travelInLava(Player player, Vec3 input, boolean falling, double gravity) {
		MagmaStage stage = FormRules.stage(player);
		FormStats stats = FormStats.of(stage);
		boolean sprint = player.isSprinting();
		float accel = (0.018F + 0.14F * player.getSpeed()) * stats.lavaSwim();
		if (sprint) accel *= 1.15F;
		float drag = sprint ? 0.9F : 0.82F;

		player.moveRelative(accel, input);
		player.move(MoverType.SELF, player.getDeltaMovement());
		Vec3 v = player.getDeltaMovement();
		if (player.horizontalCollision && player.onClimbable()) v = new Vec3(v.x, 0.2, v.z);
		// Climb out of a pool. A small lava body sinks to the bottom, and vanilla's jumpOutOfFluid only lifts over
		// edges up to 0.6 blocks above the body - so an Ember was trapped in any pool whose rim stood above the
		// lava. Pushing into a wall while in lava now creeps up it (lava climbing the side), and once the space
		// over the rim is free (1.3 blocks up, a little forward) the body hops out onto it.
		if (player.horizontalCollision && input.lengthSqr() > 1.0E-4) {
			Vec3 look = player.getLookAngle();
			Vec3 push = new Vec3(look.x, 0.0, look.z);
			push = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(0.3);
			if (player.level().noCollision(player, player.getBoundingBox().move(push.x, 1.3, push.z))) {
				if (v.y < 0.42) v = new Vec3(v.x + push.x * 0.25, 0.42, v.z + push.z * 0.25);
			} else if (v.y < 0.25) {
				v = new Vec3(v.x, 0.25, v.z);
			}
		}
		v = v.multiply(drag, 0.8, drag);
		if (player.isShiftKeyDown() && !player.onGround()) v = v.add(0.0, -0.03, 0.0);
		player.setDeltaMovement(player.getFluidFallingAdjustedMovement(gravity, falling, v));
	}

	/** Island Chain: a burst of steam throws the body out of the sea. */
	public static void steamJump(Player player) {
		MagmaStage stage = FormRules.stage(player);
		double up = switch (stage) {
			case ISLAND_CHAIN -> 0.8;
			case ARCHIPELAGO -> 0.95;
			default -> 1.1;
		};
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x * 1.5, up, v.z * 1.5);
		player.resetFallDistance();
		if (player.level().isClientSide()) {
			var rnd = player.getRandom();
			double w = player.getBbWidth();
			for (int i = 0; i < 24; i++) {
				player.level().addParticle(i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_SMOKE,
					player.getX() + (rnd.nextDouble() - 0.5) * w * 1.6, player.getY() + rnd.nextDouble() * 0.4,
					player.getZ() + (rnd.nextDouble() - 0.5) * w * 1.6,
					(rnd.nextDouble() - 0.5) * 0.1, 0.15 + rnd.nextDouble() * 0.2, (rnd.nextDouble() - 0.5) * 0.1);
			}
			player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_EXTINGUISH,
				SoundSource.PLAYERS, 0.7F, 0.8F + rnd.nextFloat() * 0.3F, false);
		}
	}
}
