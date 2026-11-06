package com.lavagenesis.form;

import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import net.minecraft.world.entity.player.Player;

/** Small helpers that work on both sides (mixins call these on the client and on the server). */
public final class FormRules {
	private FormRules() {}

	/** Every living (non-spectator) player is a body of lava in Lava Genesis. */
	public static boolean isMolten(Player player) {
		return player != null && !player.isSpectator();
	}

	/**
	 * The player's stage. The server and the owning client read the core MAGMA attachment; other clients only get
	 * the form module's {@link BodyState} (synced to everyone).
	 */
	public static MagmaStage stage(Player player) {
		MagmaData data = player.getAttached(MagmaCore.MAGMA);
		if (data != null) return data.stage();
		BodyState body = player.getAttached(FormModule.BODY);
		return body != null ? body.stageEnum() : MagmaStage.EMBER;
	}

	/** 0 = glowing hot, 1 = crusting, 2 = hard crust, 3 = solidified (heat 0). */
	public static int crust(Player player) {
		MagmaData data = player.getAttached(MagmaCore.MAGMA);
		if (data != null) return crustFor(data.heat());
		BodyState body = player.getAttached(FormModule.BODY);
		return body != null ? body.crust() : 0;
	}

	public static int crustFor(float heat) {
		if (heat <= 0.0F) return 3;
		if (heat < HeatSystem.HARD_CRUST) return 2;
		if (heat < HeatSystem.CRUSTING) return 1;
		return 0;
	}

	/** The blob look and blob-only rules apply to the three smallest stages. */
	public static boolean isBlob(MagmaStage stage) {
		return !stage.atLeast(MagmaStage.SEAMOUNT);
	}
}
