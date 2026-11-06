package com.lavagenesis.vent.client;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.vent.HotspotVentBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Something alive on every vent: a slowly turning, pulsing magma core hovering over the crater and a shimmer of
 * glowing bits rising from it. Both grow with the owner's stage; a Great Volcano build makes the core swell.
 */
public class HotspotVentRenderer implements BlockEntityRenderer<HotspotVentBlockEntity, HotspotVentRenderer.State> {
	private static final Identifier CORE = LavaGenesis.id("textures/entity/hotspot_vent_core.png");
	private static final int FULL_BRIGHT = 15728880;

	public static class State extends BlockEntityRenderState {
		float time;
		int stage;
		boolean home;
		boolean open;
		boolean finale;
		boolean owned;
		long seed;
	}

	public HotspotVentRenderer(BlockEntityRendererProvider.Context ctx) {
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(HotspotVentBlockEntity be, State s, float partialTicks, Vec3 camera,
		ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
		BlockEntityRenderer.super.extractRenderState(be, s, partialTicks, camera, breaking);
		Level level = be.getLevel();
		s.time = level == null ? 0.0F : (float) (level.getGameTime() % 240000L) + partialTicks;
		s.stage = be.ownerStage();
		s.home = be.isHome();
		s.finale = be.finale();
		s.owned = be.owner() != null;
		s.seed = be.getBlockPos().asLong();
		boolean open = true;
		if (level != null) {
			BlockState above = level.getBlockState(be.getBlockPos().above());
			open = !above.isSolidRender();
		}
		s.open = open;
	}

	@Override
	public void submit(State s, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!s.open) return;
		RenderType type = RenderTypes.entityTranslucentEmissive(CORE);
		float t = s.time + (s.seed & 255);
		float grow = s.stage / 8.0F;                                   // 0 at Ember, 1 at Great Volcano
		float size = (0.16F + 0.30F * grow) * (s.home ? 1.0F : 0.75F) * (s.finale ? 1.6F : 1.0F) * (s.owned ? 1.0F : 0.6F);
		float pulse = 1.0F + (s.finale ? 0.16F : 0.08F) * Mth.sin(t * (s.finale ? 0.35F : 0.14F));
		float bob = 0.05F * Mth.sin(t * 0.05F);

		// the core: a turning gem of magma, an inner hot heart inside a translucent glowing shell
		pose.pushPose();
		pose.translate(0.5F, 1.06F + size * 0.75F + bob, 0.5F);
		pose.rotateDegrees(Axis.YP, t * 1.2F);
		pose.rotateDegrees(Axis.XP, 35.0F);
		pose.rotateDegrees(Axis.ZP, 35.0F);
		float inner = size * 0.5F * pulse;
		collector.submitCustomGeometry(pose, type, (p, buf) -> {
			cube(p, buf, inner, argb(255, 255, 238, 170));
			cube(p, buf, inner * 1.45F, argb(120, 255, 110, 20));
		});
		pose.popPose();

		// heat shimmer: glowing bits rising from the crater, cooling from white-yellow to deep red as they go
		int bits = 5 + s.stage * 2 + (s.finale ? 10 : 0);
		float height = 0.9F + 0.35F * s.stage;
		for (int i = 0; i < bits; i++) {
			float period = 46.0F + (i * 37 % 23);
			float phase = frac(t / period + i * 0.618F);
			float angle = i * 2.39996F + t * 0.02F;
			float spread = 0.12F + phase * (0.22F + 0.05F * s.stage);
			float x = 0.5F + Mth.cos(angle) * spread;
			float z = 0.5F + Mth.sin(angle) * spread;
			float y = 1.02F + phase * height;
			float bit = (0.035F + 0.006F * s.stage) * (1.0F - phase * 0.6F);
			int r = 255;
			int g = (int) Mth.lerp(phase, 235, 60);
			int b = (int) Mth.lerp(phase, 150, 10);
			int a = (int) (255 * (1.0F - phase) * Math.min(1.0F, phase * 6.0F));
			if (a <= 4) continue;
			int color = argb(a, r, g, b);
			pose.pushPose();
			pose.translate(x, y, z);
			pose.rotateDegrees(Axis.YP, t * 3.0F + i * 40.0F);
			collector.submitCustomGeometry(pose, type, (p, buf) -> cube(p, buf, bit, color));
			pose.popPose();
		}
	}

	@Override
	public int getViewDistance() {
		return 96;
	}

	private static float frac(float v) {
		return v - Mth.floor(v);
	}

	private static int argb(int a, int r, int g, int b) {
		return (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
	}

	/** An axis-aligned cube of half size {@code h} around the current origin. */
	private static void cube(PoseStack.Pose p, VertexConsumer buf, float h, int color) {
		// +Y
		quad(p, buf, color, 0, 1, 0, -h, h, -h, -h, h, h, h, h, h, h, h, -h);
		// -Y
		quad(p, buf, color, 0, -1, 0, -h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h);
		// +Z
		quad(p, buf, color, 0, 0, 1, -h, -h, h, h, -h, h, h, h, h, -h, h, h);
		// -Z
		quad(p, buf, color, 0, 0, -1, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h);
		// +X
		quad(p, buf, color, 1, 0, 0, h, -h, h, h, -h, -h, h, h, -h, h, h, h);
		// -X
		quad(p, buf, color, -1, 0, 0, -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h);
	}

	private static void quad(PoseStack.Pose p, VertexConsumer buf, int color, float nx, float ny, float nz,
		float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3) {
		vertex(p, buf, color, x0, y0, z0, 0.0F, 1.0F, nx, ny, nz);
		vertex(p, buf, color, x1, y1, z1, 1.0F, 1.0F, nx, ny, nz);
		vertex(p, buf, color, x2, y2, z2, 1.0F, 0.0F, nx, ny, nz);
		vertex(p, buf, color, x3, y3, z3, 0.0F, 0.0F, nx, ny, nz);
	}

	private static void vertex(PoseStack.Pose p, VertexConsumer buf, int color, float x, float y, float z, float u, float v,
		float nx, float ny, float nz) {
		buf.addVertex(p, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, nx, ny, nz);
	}
}
