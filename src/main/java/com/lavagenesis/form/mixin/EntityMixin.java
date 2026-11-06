package com.lavagenesis.form.mixin;

import com.lavagenesis.form.FormRules;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The lava body: immune to fire (no burning, no lava/magma damage, no fire overlay) and swims in lava like in water. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "fireImmune", at = @At("HEAD"), cancellable = true)
	private void lavagenesis$moltenFireImmune(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && FormRules.isMolten(player)) cir.setReturnValue(true);
	}

	/** Vanilla treats "swimming pose outside water" as crawling (slow); swimming in lava is real swimming. */
	@Inject(method = "isVisuallyCrawling", at = @At("HEAD"), cancellable = true)
	private void lavagenesis$swimNotCrawl(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Player player && FormRules.isMolten(player) && player.isInLava()) cir.setReturnValue(false);
	}

	/** Sprinting with the head in lava starts the swimming pose, like in water. */
	@Inject(method = "updateSwimming", at = @At("TAIL"))
	private void lavagenesis$swimInLava(CallbackInfo ci) {
		if (!((Object) this instanceof Player player) || !FormRules.isMolten(player)) return;
		if (player.getAbilities().flying || player.isInWater() || !player.isInLava()) return;
		Entity self = (Entity) (Object) this;
		boolean swim = self.isSwimming()
			? self.isSprinting() && !self.isPassenger()
			: self.isSprinting() && self.isEyeInFluid(FluidTags.LAVA) && !self.isPassenger();
		self.setSwimming(swim);
	}
}
