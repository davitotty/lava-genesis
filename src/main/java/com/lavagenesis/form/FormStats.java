package com.lavagenesis.form;

import com.lavagenesis.core.MagmaStage;

/**
 * The tuning table of the lava body, one row per {@link MagmaStage}. Absolute target values; the form module turns
 * them into permanent attribute modifiers relative to the player's base values.
 * <p>
 * Heat rates are per second. {@code waterDrain} is what makes an Ember stay in its chamber (about 3 s from full heat
 * to stone) while a Droplet can dart through water for a few seconds and a Seamount barely notices the sea.
 */
public record FormStats(
	float maxHealth,
	float speed,
	float stepHeight,
	float blockReach,
	float entityReach,
	float attackDamage,
	float knockbackResistance,
	float safeFall,
	float jump,
	/** vanilla multiplies the third-person distance by the scale; this keeps the tiny camera usable */
	float cameraDistance,
	/** multiplier on lava-swimming acceleration */
	float lavaSwim,
	float waterDrain,
	float rainDrain,
	float coldDrain,
	/** dry air slowly warms the body back up to this */
	float ambientHeat) {

	private static final FormStats[] TABLE = {
		//            hp     speed   step  block  entity atk   kb    fall  jump   cam   lava  water   rain   cold   ambient
		new FormStats(6F,   0.095F, 0.6F, 3.0F,  2.0F,  0.5F, 0.0F, 10F,  0.42F, 14F,  1.00F, 0.30F,  0.05F,  0.020F, 0.50F), // ember
		new FormStats(8F,   0.105F, 0.6F, 3.5F,  2.25F, 1.0F, 0.0F,  9F,  0.44F, 10F,  1.40F, 0.14F,  0.035F, 0.015F, 0.55F), // magma droplet
		new FormStats(12F,  0.110F, 0.6F, 4.0F,  2.5F,  1.5F, 0.05F, 8F,  0.45F,  8F,  1.30F, 0.06F,  0.02F,  0.012F, 0.60F), // lava flow
		new FormStats(16F,  0.105F, 0.7F, 4.5F,  3.0F,  2.0F, 0.10F, 6F,  0.45F,  6F,  1.15F, 0.004F, 0.006F, 0.008F, 0.70F), // seamount
		new FormStats(20F,  0.100F, 0.8F, 4.75F, 3.0F,  3.0F, 0.20F, 5F,  0.46F,  4.8F, 1.10F, 0.003F, 0.005F, 0.006F, 0.75F), // islet
		new FormStats(26F,  0.100F, 1.0F, 5.0F,  3.5F,  4.0F, 0.35F, 6F,  0.48F,  4.5F, 1.10F, 0.0025F, 0.004F, 0.005F, 0.80F), // shield island
		new FormStats(32F,  0.105F, 1.25F, 6.0F, 4.25F, 5.5F, 0.50F, 8F,  0.52F,  4.3F, 1.10F, 0.002F, 0.003F, 0.004F, 0.85F), // island chain
		new FormStats(40F,  0.110F, 1.6F, 7.5F,  5.25F, 7.0F, 0.70F, 11F, 0.56F,  4.0F, 1.15F, 0.0015F, 0.002F, 0.003F, 0.90F), // archipelago
		new FormStats(60F,  0.120F, 2.2F, 10.0F, 7.5F, 10.0F, 0.90F, 16F, 0.62F,  4.0F, 1.20F, 0.001F, 0.001F, 0.002F, 0.95F), // great volcano
	};

	public static FormStats of(MagmaStage stage) {
		return TABLE[Math.min(stage.ordinal(), TABLE.length - 1)];
	}
}
