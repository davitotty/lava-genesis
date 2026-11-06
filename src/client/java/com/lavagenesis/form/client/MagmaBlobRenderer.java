package com.lavagenesis.form.client;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.MagmaStage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Draws an Ember / Magma Droplet / Lava Flow player as a wobbling, full-bright magma blob instead of a tiny Steve:
 * a textured core sphere plus a soft translucent glow shell. Ember is a round spark, a Droplet a teardrop and a
 * Lava Flow a broad, flowing puddle-blob. Crust (low heat) darkens the core and dims the halo.
 */
public final class MagmaBlobRenderer {
	private MagmaBlobRenderer() {}

	public static final Identifier CORE_TEXTURE = LavaGenesis.id("textures/entity/form/magma_blob.png");
	public static final Identifier HALO_TEXTURE = LavaGenesis.id("textures/entity/form/magma_halo.png");
	public static final int FULL_BRIGHT = 15728880;
	private static final int LAT = 10;
	private static final int LON = 16;

	public static void submit(AvatarRenderState state, BodyLook look, PoseStack pose, SubmitNodeCollector collector) {
		if (state.isInvisible) return;
		float height = 1.8F * state.scale;
		float r = height * 0.34F;
		float t = state.ageInTicks + state.id * 13.7F;

		float sx;
		float sy;
		float taper;
		float wobble;
		if (look.stage() == MagmaStage.EMBER) {
			sx = 1.0F; sy = 1.0F; taper = 0.0F; wobble = 0.08F;
		} else if (look.stage() == MagmaStage.MAGMA_DROPLET) {
			sx = 0.95F; sy = 1.15F; taper = 0.7F; wobble = 0.06F;
		} else {
			sx = 1.35F; sy = 0.66F; taper = 0.0F; wobble = 0.11F;
		}

		// squash & stretch: bounce with walking, flatten when crouching
		float walk = Math.min(1.0F, state.walkAnimationSpeed);
		float bounce = 1.0F + 0.14F * walk * Mth.sin(state.walkAnimationPos * 1.3F);
		if (state.isCrouching) bounce *= 0.8F;
		float breathe = 1.0F + 0.03F * Mth.sin(t * 0.11F);
		float stretchY = sy * bounce * breathe;
		float stretchXZ = sx / Mth.sqrt(bounce) * breathe;

		pose.pushPose();
		pose.translate(0.0F, r * stretchY + height * 0.01F, 0.0F);
		pose.rotateDegrees(Axis.YP, 180.0F - state.bodyRot);
		pose.scale(r * stretchXZ, r * stretchY, r * stretchXZ);

		float g = look.glow();
		float pulse = 0.9F + 0.1F * Mth.sin(t * 0.23F);
		float k = Mth.lerp(g, 0.32F, 1.0F) * pulse;
		int core = ARGB.colorFromFloat(1.0F, Math.min(1.0F, k * 1.05F), k * Mth.lerp(g, 0.75F, 1.0F), k * Mth.lerp(g, 0.7F, 1.0F));
		int light = look.crust() >= 2 ? state.lightCoords : FULL_BRIGHT;
		float tt = t;
		collector.submitCustomGeometry(pose, RenderTypes.entityCutout(CORE_TEXTURE),
			(p, buf) -> sphere(p, buf, 1.0F, tt, wobble, taper, core, light));

		int halo = ARGB.colorFromFloat(0.45F * g * pulse, 1.0F, 0.75F, 0.35F);
		if (g > 0.25F) {
			collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(HALO_TEXTURE),
				(p, buf) -> sphere(p, buf, 1.28F, tt * 1.4F + 40.0F, wobble * 1.6F, taper * 0.8F, halo, FULL_BRIGHT));
		}
		pose.popPose();
	}

	private static void sphere(PoseStack.Pose p, VertexConsumer buf, float radius, float t, float wobble, float taper, int color, int light) {
		float[][] xs = new float[LAT + 1][LON + 1];
		float[][] ys = new float[LAT + 1][LON + 1];
		float[][] zs = new float[LAT + 1][LON + 1];
		for (int i = 0; i <= LAT; i++) {
			float th = Mth.PI * i / LAT;
			float st = Mth.sin(th);
			float ct = Mth.cos(th);
			for (int j = 0; j <= LON; j++) {
				float ph = Mth.TWO_PI * (j % LON) / LON;
				float n = 1.0F
					+ wobble * Mth.sin(3.0F * ph + t * 0.13F) * Mth.sin(2.0F * th + t * 0.21F)
					+ wobble * 0.5F * Mth.sin(5.0F * ph - t * 0.17F + th * 3.0F);
				float x = st * Mth.cos(ph) * n;
				float y = ct * n;
				float z = st * Mth.sin(ph) * n;
				if (taper > 0.0F && y > 0.0F) {
					float pinch = 1.0F - taper * y * y;
					x *= pinch;
					z *= pinch;
					y *= 1.0F + taper * 0.45F;
				}
				xs[i][j] = x * radius;
				ys[i][j] = y * radius;
				zs[i][j] = z * radius;
			}
		}
		for (int i = 0; i < LAT; i++) {
			for (int j = 0; j < LON; j++) {
				vertex(p, buf, xs, ys, zs, i, j, color, light);
				vertex(p, buf, xs, ys, zs, i + 1, j, color, light);
				vertex(p, buf, xs, ys, zs, i + 1, j + 1, color, light);
				vertex(p, buf, xs, ys, zs, i, j + 1, color, light);
			}
		}
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer buf, float[][] xs, float[][] ys, float[][] zs, int i, int j,
		int color, int light) {
		float x = xs[i][j];
		float y = ys[i][j];
		float z = zs[i][j];
		float len = Mth.sqrt(x * x + y * y + z * z);
		if (len < 1.0E-5F) len = 1.0F;
		buf.addVertex(p, x, y, z)
			.setColor(color)
			.setUv(j / (float) LON, i / (float) LAT)
			.setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(light)
			.setNormal(p, x / len, y / len, z / len);
	}
}
