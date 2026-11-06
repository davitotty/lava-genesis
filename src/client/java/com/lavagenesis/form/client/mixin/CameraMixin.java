package com.lavagenesis.form.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The tiny camera: an Ember is 0.06 blocks wide, but the near clip plane sits 0.05 blocks in front of the eye, so
 * walls next to it would be cut open. Pull the near plane in for small bodies (never below 0.02 to keep depth
 * precision for the far terrain).
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	public abstract Entity entity();

	@ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"), index = 0)
	private float lavagenesis$tinyNearPlane(float zNear) {
		if (entity() instanceof LivingEntity living) {
			float scale = living.getScale();
			if (scale < 0.4F) return Math.max(0.02F, zNear * scale * 2.5F);
		}
		return zNear;
	}
}
