package com.lavagenesis.form.client;

import com.lavagenesis.core.MagmaStage;
import com.lavagenesis.form.FormRules;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * How a player's lava body is drawn this frame, stored on the player's render state (Fabric render state data).
 *
 * @param blob  draw a glowing magma blob instead of the player model (Ember .. Lava Flow)
 * @param glow  0..1 how hot the body glows (crust dims it)
 */
public record BodyLook(MagmaStage stage, int crust, boolean blob, float glow) {
	public static final RenderStateDataKey<BodyLook> KEY = RenderStateDataKey.create(() -> "lavagenesis:form_body_look");
	public static final BodyLook NONE = new BodyLook(MagmaStage.EMBER, 0, false, 0.0F);

	public static BodyLook of(Player player) {
		if (!FormRules.isMolten(player)) return NONE;
		MagmaStage stage = FormRules.stage(player);
		int crust = FormRules.crust(player);
		float glow = switch (crust) {
			case 0 -> 1.0F;
			case 1 -> 0.7F;
			case 2 -> 0.45F;
			default -> 0.2F;
		};
		return new BodyLook(stage, crust, FormRules.isBlob(stage), glow);
	}

	public boolean molten() {
		return this != NONE;
	}

	public static @Nullable BodyLook get(EntityRenderState state) {
		return ((FabricRenderState) state).getData(KEY);
	}

	public static void set(EntityRenderState state, BodyLook look) {
		((FabricRenderState) state).setData(KEY, look);
	}

	public static boolean isBlob(EntityRenderState state) {
		BodyLook look = get(state);
		return look != null && look.blob();
	}
}
