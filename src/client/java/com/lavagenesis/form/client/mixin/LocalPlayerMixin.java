package com.lavagenesis.form.client.mixin;

import com.lavagenesis.form.FormRules;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Swim-sprinting is only kept up "in water" in vanilla; for a lava body lava counts too. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@ModifyExpressionValue(method = "shouldStopSwimSprinting",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isInWater()Z"))
	private boolean lavagenesis$lavaCountsAsWater(boolean inWater) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		return inWater || (FormRules.isMolten(self) && self.isInLava());
	}
}
