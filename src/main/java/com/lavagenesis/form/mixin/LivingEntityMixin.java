package com.lavagenesis.form.mixin;

import com.lavagenesis.core.MagmaStage;
import com.lavagenesis.form.FormMovement;
import com.lavagenesis.form.FormRules;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lava movement of the lava body (runs where the player is simulated, i.e. on the owning client and the server):
 * no lava slowdown - it swims through lava like water, faster for the nimble Droplet; and the Island Chain steam jump.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Unique private int lavagenesis$lastSteamJump = -100;

	@Shadow
	private void jumpOutOfFluid(double oldY) {
		throw new AssertionError();
	}

	@Inject(method = "travelInLava", at = @At("HEAD"), cancellable = true)
	private void lavagenesis$swimThroughLava(Vec3 input, double oldY, boolean falling, double gravity, CallbackInfo ci) {
		if (!((Object) this instanceof Player player) || !FormRules.isMolten(player)) return;
		FormMovement.travelInLava(player, input, falling, gravity);
		jumpOutOfFluid(oldY);
		ci.cancel();
	}

	@Inject(method = "jumpInLiquid", at = @At("HEAD"), cancellable = true)
	private void lavagenesis$jumpInLiquid(TagKey<Fluid> fluid, CallbackInfo ci) {
		if (!((Object) this instanceof Player player) || !FormRules.isMolten(player)) return;
		if (fluid == FluidTags.LAVA) {
			// rise through lava quickly
			player.setDeltaMovement(player.getDeltaMovement().add(0.0, 0.065, 0.0));
			ci.cancel();
		} else if (fluid == FluidTags.WATER && FormRules.stage(player).atLeast(MagmaStage.ISLAND_CHAIN)
			&& !player.isUnderWater() && player.tickCount - lavagenesis$lastSteamJump > 16) {
			lavagenesis$lastSteamJump = player.tickCount;
			FormMovement.steamJump(player);
			ci.cancel();
		}
	}
}
