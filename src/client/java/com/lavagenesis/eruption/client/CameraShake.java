package com.lavagenesis.eruption.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/** A rumbling camera for big eruptions: jitters the view a little each tick, fading out, and leaves aim unchanged. */
final class CameraShake {
	private CameraShake() {}

	private static final RandomSource RANDOM = RandomSource.create();
	private static float strength;
	private static int total, left;
	private static float yawOff, pitchOff;

	static void start(float s, int ticks) {
		float current = left > 0 ? strength * left / (float) total : 0.0F;
		if (s < current) return;
		strength = Math.min(s, 4.0F);
		total = left = Math.max(1, ticks);
	}

	static void tick(@Nullable LocalPlayer player) {
		if (player == null) {
			left = 0;
			yawOff = pitchOff = 0;
			return;
		}
		if (yawOff == 0 && pitchOff == 0 && left <= 0) return;
		// undo last tick's offset, then apply a new one
		player.setYRot(player.getYRot() - yawOff);
		player.setXRot(player.getXRot() - pitchOff);
		if (left > 0) {
			float f = strength * left / (float) total;
			yawOff = (RANDOM.nextFloat() * 2 - 1) * f;
			pitchOff = (RANDOM.nextFloat() * 2 - 1) * f * 0.6F;
			left--;
		} else {
			yawOff = pitchOff = 0;
		}
		player.setYRot(player.getYRot() + yawOff);
		player.setXRot(Math.clamp(player.getXRot() + pitchOff, -90.0F, 90.0F));
	}
}
