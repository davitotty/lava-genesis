package com.lavagenesis.form.client.mixin;

import com.lavagenesis.form.FormRules;
import com.lavagenesis.form.client.BodyLook;
import com.lavagenesis.form.client.LavaCrustLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts the lava body look on every player's render state; crusted skin from Seamount on; no arm for a blob. */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void lavagenesis$extractBodyLook(Avatar avatar, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		BodyLook.set(state, avatar instanceof Player player ? BodyLook.of(player) : BodyLook.NONE);
	}

	/** A blob has no armour, held items, cape or arrows sticking out of it. */
	@Inject(method = "shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Z",
		at = @At("HEAD"), cancellable = true)
	private void lavagenesis$noLayersOnBlob(AvatarRenderState state, CallbackInfoReturnable<Boolean> cir) {
		if (BodyLook.isBlob(state)) cir.setReturnValue(false);
	}

	@Inject(method = "getTextureLocation(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)Lnet/minecraft/resources/Identifier;",
		at = @At("HEAD"), cancellable = true)
	private void lavagenesis$crustSkin(AvatarRenderState state, CallbackInfoReturnable<Identifier> cir) {
		BodyLook look = BodyLook.get(state);
		if (look != null && look.molten()) cir.setReturnValue(LavaCrustLayer.SKIN_TEXTURE);
	}

	/** First-person arm: crusted lava arm, or nothing at all for a blob. */
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void lavagenesis$blobHasNoHand(PoseStack pose, SubmitNodeCollector collector, int light, Identifier skin, ModelPart arm,
		boolean sleeve, CallbackInfo ci) {
		Player player = Minecraft.getInstance().player;
		if (player != null && FormRules.isMolten(player) && FormRules.isBlob(FormRules.stage(player))) ci.cancel();
	}

	@ModifyVariable(method = "renderHand", at = @At("HEAD"), argsOnly = true)
	private Identifier lavagenesis$crustArm(Identifier skin) {
		Player player = Minecraft.getInstance().player;
		return player != null && FormRules.isMolten(player) ? LavaCrustLayer.SKIN_TEXTURE : skin;
	}
}
