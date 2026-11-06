package com.lavagenesis.form.client.mixin;

import com.lavagenesis.form.client.BodyLook;
import com.lavagenesis.form.client.MagmaBlobRenderer;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** For blob stages: draw the magma blob and skip the player model (name tag, shadow and leash still render). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Unique
	private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;"
		+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
		+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

	@Inject(method = SUBMIT, at = @At("HEAD"))
	private void lavagenesis$submitBlob(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
		CameraRenderState camera, CallbackInfo ci) {
		if (state instanceof AvatarRenderState avatar) {
			BodyLook look = BodyLook.get(avatar);
			if (look != null && look.blob()) MagmaBlobRenderer.submit(avatar, look, pose, collector);
		}
	}

	@WrapWithCondition(method = SUBMIT, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;"
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"))
	private boolean lavagenesis$hideModelForBlob(SubmitNodeCollector collector, Model<?> model, Object modelState, PoseStack pose,
		RenderType type, int light, int overlay, int color, UvMapping uv, int outline) {
		return !(modelState instanceof AvatarRenderState avatar && BodyLook.isBlob(avatar));
	}
}
