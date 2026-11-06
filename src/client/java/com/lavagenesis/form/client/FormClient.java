package com.lavagenesis.form.client;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaStage;
import com.lavagenesis.form.FormRules;
import com.lavagenesis.form.HeatSystem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRenderEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Client side of the form module: body particles for every visible lava player (embers, drips, small flames,
 * glowing footprints, bubbles), the crusted-cracks render layer, the crust screen overlay at low heat, and the
 * lava-sight distance used by the lava fog mixin.
 */
public final class FormClient {
	private FormClient() {}

	public static final Identifier CRUST_OVERLAY = LavaGenesis.id("textures/misc/form_crust_overlay.png");

	private static final int[] EMBER_COLORS = {0xFFF2A0, 0xFFC040, 0xFF8A1E, 0xFF5A0A};
	private static float overlayAlpha;

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void init() {
		ClientTickEvents.END_CLIENT_TICK.register(FormClient::tick);

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?>) {
				helper.register(new LavaCrustLayer((RenderLayerParent<AvatarRenderState, PlayerModel>) (RenderLayerParent) renderer));
			}
		});
		LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER.register(state -> {
			BodyLook look = BodyLook.get(state);
			return look == null || !look.molten();
		});

		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, LavaGenesis.id("form_crust_overlay"),
			FormClient::extractCrustOverlay);
	}

	/** How far a lava body sees inside lava (blocks). */
	public static float lavaSightDistance(MagmaStage stage, float renderDistance) {
		return switch (stage) {
			case EMBER -> 10.0F;
			case MAGMA_DROPLET -> 12.0F;
			case LAVA_FLOW -> 16.0F;
			case SEAMOUNT -> 20.0F;
			case ISLET -> 24.0F;
			case SHIELD_ISLAND -> 28.0F;
			case ISLAND_CHAIN -> 34.0F;
			default -> Math.max(48.0F, renderDistance * 0.5F);
		};
	}

	// --- crust overlay ------------------------------------------------------------------------------------

	private static void extractCrustOverlay(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || !FormRules.isMolten(player)) return;
		float heat = MagmaCore.get(player).heat();
		float target = heat >= HeatSystem.CRUSTING ? 0.0F : 0.25F + 0.75F * (1.0F - heat / HeatSystem.CRUSTING);
		if (heat <= 0.0F) target = 0.85F + 0.15F * Mth.sin(player.tickCount * 0.25F);
		overlayAlpha = Mth.lerp(0.05F, overlayAlpha, target);
		if (overlayAlpha < 0.01F) return;
		g.blit(RenderPipelines.GUI_TEXTURED, CRUST_OVERLAY, 0, 0, 0.0F, 0.0F, g.guiWidth(), g.guiHeight(),
			g.guiWidth(), g.guiHeight(), ARGB.white(Mth.clamp(overlayAlpha, 0.0F, 1.0F)));
	}

	// --- body particles -----------------------------------------------------------------------------------

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused() || mc.player == null) return;
		boolean firstPerson = mc.options.getCameraType().isFirstPerson();
		for (AbstractClientPlayer player : level.players()) {
			if (!FormRules.isMolten(player) || player.isInvisible()) continue;
			if (player.distanceToSqr(mc.player) > 48 * 48) continue;
			boolean self = player == mc.player;
			bodyParticles(level, player, self && firstPerson);
		}
	}

	private static void bodyParticles(ClientLevel level, Player player, boolean firstPersonSelf) {
		RandomSource rnd = level.getRandom();
		MagmaStage stage = FormRules.stage(player);
		int crust = FormRules.crust(player);
		float w = player.getBbWidth();
		float h = player.getBbHeight();
		float size = Mth.clamp(stage.scale, 0.1F, 3.0F);
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		boolean wet = player.isInWater();
		boolean inLava = player.isInLava();

		// in first person only the lower body sheds particles, so the tiny camera is never blinded
		double topY = firstPersonSelf ? h * 0.35 : h;
		float rate = (firstPersonSelf ? 0.25F : 1.0F) * (crust >= 2 ? 0.4F : 1.0F);

		// glowing embers drifting off the body
		if (!wet && rnd.nextFloat() < (0.30F + 0.25F * size) * rate) {
			int c = EMBER_COLORS[rnd.nextInt(EMBER_COLORS.length)];
			if (crust >= 2) c = ARGB.scaleRGB(c, 0.5F);
			ParticleOptions dust = new DustParticleOptions(c, Mth.clamp(0.25F + 0.55F * size, 0.2F, 2.5F));
			level.addParticle(dust, x + (rnd.nextDouble() - 0.5) * w * 1.2, y + rnd.nextDouble() * topY,
				z + (rnd.nextDouble() - 0.5) * w * 1.2, 0.0, firstPersonSelf ? 0.0 : 0.02, 0.0);
		}
		// small flames licking up (smoke when crusted); they rise, so not from our own first-person body
		if (!wet && !inLava && !firstPersonSelf && rnd.nextFloat() < (0.04F + 0.12F * size) * rate) {
			level.addParticle(crust >= 2 ? ParticleTypes.SMOKE : ParticleTypes.SMALL_FLAME,
				x + (rnd.nextDouble() - 0.5) * w, y + rnd.nextDouble() * topY, z + (rnd.nextDouble() - 0.5) * w,
				0.0, 0.01 + 0.01 * size, 0.0);
		}
		// dripping lava from the underside
		if (!wet && !inLava && stage.atLeast(MagmaStage.MAGMA_DROPLET) && rnd.nextFloat() < (0.03F + 0.06F * size) * rate) {
			level.addParticle(ParticleTypes.DRIPPING_LAVA, x + (rnd.nextDouble() - 0.5) * w * 0.8, y + h * (0.1 + rnd.nextDouble() * 0.3),
				z + (rnd.nextDouble() - 0.5) * w * 0.8, 0.0, 0.0, 0.0);
		}
		// big bodies pop like a lava lake
		if (!wet && stage.atLeast(MagmaStage.SHIELD_ISLAND) && rnd.nextFloat() < 0.02F * size * rate) {
			level.addParticle(ParticleTypes.LAVA, x + (rnd.nextDouble() - 0.5) * w, y + h * (0.3 + rnd.nextDouble() * 0.6),
				z + (rnd.nextDouble() - 0.5) * w, 0.0, 0.0, 0.0);
		}
		// underwater: a stream of bubbles (and the server adds steam)
		if (wet && rnd.nextFloat() < (firstPersonSelf ? 0.15F : 0.5F)) {
			level.addParticle(ParticleTypes.BUBBLE, x + (rnd.nextDouble() - 0.5) * w, y + rnd.nextDouble() * topY,
				z + (rnd.nextDouble() - 0.5) * w, 0.0, 0.08, 0.0);
		}
		// Lava Flow and later: the ground under you glows where you walk
		if (stage.atLeast(MagmaStage.LAVA_FLOW) && player.onGround() && !wet
			&& player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4 && rnd.nextFloat() < 0.6F) {
			BlockPos below = player.getOnPos();
			BlockState s = level.getBlockState(below);
			if (!s.isAir()) {
				double top = below.getY() + Math.max(0.05, s.getCollisionShape(level, below).max(net.minecraft.core.Direction.Axis.Y));
				double spread = Math.max(w, 0.4);
				level.addParticle(new DustParticleOptions(EMBER_COLORS[1 + rnd.nextInt(3)], Mth.clamp(0.6F * size + 0.4F, 0.4F, 2.0F)),
					x + (rnd.nextDouble() - 0.5) * spread, top + 0.02, z + (rnd.nextDouble() - 0.5) * spread, 0.0, 0.0, 0.0);
			}
		}
	}
}
