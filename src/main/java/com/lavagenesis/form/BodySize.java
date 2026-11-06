package com.lavagenesis.form;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaStage;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Body size and the per-stage stats: permanent attribute modifiers with ids {@code lavagenesis:form/<attribute>}.
 * The scale grows smoothly over {@link #GROW_TICKS} when the stage changes; everything else switches at once.
 */
public final class BodySize {
	private BodySize() {}

	public static final int GROW_TICKS = 60;

	public static final Identifier SCALE_ID = LavaGenesis.id("form/scale");
	private static final Identifier HEALTH_ID = LavaGenesis.id("form/max_health");
	private static final Identifier SPEED_ID = LavaGenesis.id("form/movement_speed");
	private static final Identifier STEP_ID = LavaGenesis.id("form/step_height");
	private static final Identifier BLOCK_REACH_ID = LavaGenesis.id("form/block_interaction_range");
	private static final Identifier ENTITY_REACH_ID = LavaGenesis.id("form/entity_interaction_range");
	private static final Identifier ATTACK_ID = LavaGenesis.id("form/attack_damage");
	private static final Identifier KNOCKBACK_ID = LavaGenesis.id("form/knockback_resistance");
	private static final Identifier SAFE_FALL_ID = LavaGenesis.id("form/safe_fall_distance");
	private static final Identifier JUMP_ID = LavaGenesis.id("form/jump_strength");
	private static final Identifier CAMERA_ID = LavaGenesis.id("form/camera_distance");
	private static final Identifier WATER_MOVE_ID = LavaGenesis.id("form/water_movement_efficiency");
	private static final Identifier OXYGEN_ID = LavaGenesis.id("form/oxygen_bonus");

	/** Called every server tick for every player. Cheap when nothing changes. */
	static void tick(ServerPlayer player, FormRuntime rt) {
		MagmaStage stage = MagmaCore.get(player).stage();
		if (rt.appliedStage != stage) {
			boolean animate = rt.appliedStage != null && stage.ordinal() > rt.appliedStage.ordinal();
			float from = currentScale(player);
			applyStats(player, stage, rt.appliedStage);
			rt.appliedStage = stage;
			if (animate) {
				rt.growFrom = from;
				rt.growTo = stage.scale;
				rt.growTick = 0;
				rt.noSuffocateUntil = player.level().getServer().getTickCount() + GROW_TICKS + 100;
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_AMBIENT,
					SoundSource.PLAYERS, 1.0F, 0.6F);
			} else {
				rt.growTick = -1;
				setScale(player, stage.scale);
			}
		}
		if (rt.growTick >= 0) {
			rt.growTick++;
			float t = Math.min(1.0F, rt.growTick / (float) GROW_TICKS);
			float eased = t * t * (3.0F - 2.0F * t);
			setScale(player, rt.growFrom + (rt.growTo - rt.growFrom) * eased);
			if (rt.growTick % 6 == 0) {
				double h = player.getBbHeight();
				player.level().sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + h * 0.5, player.getZ(),
					2 + (int) (h * 2), player.getBbWidth() * 0.6, h * 0.4, player.getBbWidth() * 0.6, 0.0);
				player.level().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + h * 0.3, player.getZ(),
					4, player.getBbWidth(), h * 0.3, player.getBbWidth(), 0.02);
			}
			if (t >= 1.0F) rt.growTick = -1;
		}
	}

	/** Snap everything to the player's stage right now (join, respawn, dimension change). */
	static void reapply(ServerPlayer player, FormRuntime rt) {
		rt.appliedStage = null;
		rt.growTick = -1;
		tick(player, rt);
	}

	private static float currentScale(ServerPlayer player) {
		AttributeInstance inst = player.getAttribute(Attributes.SCALE);
		return inst == null ? 1.0F : (float) inst.getValue();
	}

	private static void setScale(ServerPlayer player, float scale) {
		AttributeInstance inst = player.getAttribute(Attributes.SCALE);
		if (inst == null) return;
		double amount = scale - inst.getBaseValue();
		AttributeModifier old = inst.getModifier(SCALE_ID);
		if (old != null && Math.abs(old.amount() - amount) < 1.0E-4) return;
		inst.addOrReplacePermanentModifier(new AttributeModifier(SCALE_ID, amount, AttributeModifier.Operation.ADD_VALUE));
	}

	private static void applyStats(ServerPlayer player, MagmaStage stage, MagmaStage previous) {
		FormStats s = FormStats.of(stage);
		float oldMax = player.getMaxHealth();
		setTarget(player, Attributes.MAX_HEALTH, HEALTH_ID, s.maxHealth());
		setTarget(player, Attributes.MOVEMENT_SPEED, SPEED_ID, s.speed());
		setTarget(player, Attributes.STEP_HEIGHT, STEP_ID, s.stepHeight());
		setTarget(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, s.blockReach());
		setTarget(player, Attributes.ENTITY_INTERACTION_RANGE, ENTITY_REACH_ID, s.entityReach());
		setTarget(player, Attributes.ATTACK_DAMAGE, ATTACK_ID, s.attackDamage());
		setTarget(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, s.knockbackResistance());
		setTarget(player, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL_ID, s.safeFall());
		setTarget(player, Attributes.JUMP_STRENGTH, JUMP_ID, s.jump());
		setTarget(player, Attributes.CAMERA_DISTANCE, CAMERA_ID, s.cameraDistance());
		// a seamount is at home in the sea: it swims like with depth strider and needs no air
		boolean sea = stage.atLeast(MagmaStage.SEAMOUNT);
		setTarget(player, Attributes.WATER_MOVEMENT_EFFICIENCY, WATER_MOVE_ID, sea ? 0.6F : Float.NaN);
		setTarget(player, Attributes.OXYGEN_BONUS, OXYGEN_ID, sea ? 1024.0F : Float.NaN);
		float newMax = player.getMaxHealth();
		if (previous != null && newMax > oldMax) {
			player.heal(newMax - oldMax);
		} else if (player.getHealth() > newMax) {
			player.setHealth(newMax);
		}
	}

	/** Adds or replaces a modifier so the attribute ends up at {@code target}; NaN removes the modifier. */
	private static void setTarget(ServerPlayer player, Holder<Attribute> attribute, Identifier id, float target) {
		AttributeInstance inst = player.getAttribute(attribute);
		if (inst == null) return;
		if (Float.isNaN(target)) {
			inst.removeModifier(id);
			return;
		}
		double amount = target - inst.getBaseValue();
		inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
	}
}
