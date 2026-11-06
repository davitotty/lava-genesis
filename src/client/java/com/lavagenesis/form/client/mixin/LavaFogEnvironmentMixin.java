package com.lavagenesis.form.client.mixin;

import com.lavagenesis.form.FormRules;
import com.lavagenesis.form.client.FormClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla lava fog ends after 1 block (5 with fire resistance). A lava body sees through its own element:
 * 10 blocks as an Ember, growing with every stage to half the render distance from Archipelago on.
 */
@Mixin(LavaFogEnvironment.class)
public abstract class LavaFogEnvironmentMixin {
	@Inject(method = "setupFog", at = @At("TAIL"))
	private void lavagenesis$lavaSight(FogData fog, Camera camera, ClientLevel level, float renderDistance, DeltaTracker delta,
		CallbackInfo ci) {
		if (!(camera.entity() instanceof Player player) || !FormRules.isMolten(player)) return;
		float end = FormClient.lavaSightDistance(FormRules.stage(player), renderDistance);
		fog.environmentalStart = -4.0F;
		fog.environmentalEnd = end;
		fog.skyEnd = end;
		fog.cloudEnd = end;
	}
}
