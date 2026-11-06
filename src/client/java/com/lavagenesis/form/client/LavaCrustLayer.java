package com.lavagenesis.form.client;

import com.lavagenesis.LavaGenesis;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * From Seamount on the body is a lava golem: the player model wears the crusted-basalt skin
 * ({@link #SKIN_TEXTURE}, swapped in by the AvatarRenderer mixin) and this layer draws its glowing cracks
 * full-bright on top, pulsing slowly and dimming as the body cools.
 */
public class LavaCrustLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	public static final Identifier SKIN_TEXTURE = LavaGenesis.id("textures/entity/form/lava_crust_skin.png");
	public static final Identifier GLOW_TEXTURE = LavaGenesis.id("textures/entity/form/lava_crust_glow.png");

	public LavaCrustLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		BodyLook look = BodyLook.get(state);
		if (look == null || !look.molten() || look.blob() || state.isInvisible) return;
		float t = state.ageInTicks + state.id * 7.3F;
		float pulse = 0.8F + 0.2F * Mth.sin(t * 0.09F) * Mth.sin(t * 0.031F + 1.0F);
		float k = Mth.clamp(look.glow() * pulse, 0.0F, 1.0F);
		int color = ARGB.colorFromFloat(1.0F, k, k, k);
		collector.order(1).submitModel(getParentModel(), state, pose, RenderTypes.eyes(GLOW_TEXTURE),
			MagmaBlobRenderer.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor);
	}
}
