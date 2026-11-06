package com.lavagenesis.core;

import com.lavagenesis.LavaGenesis;
import java.util.function.UnaryOperator;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Shared player-as-a-volcano state. Server code writes it; the owning client receives it automatically
 * (read it on the client with {@link #get(Player)} on the local player).
 */
public final class MagmaCore {
	private MagmaCore() {}

	public static final AttachmentType<MagmaData> MAGMA = AttachmentRegistry.create(LavaGenesis.id("magma"), b -> b
		.persistent(MagmaData.CODEC)
		.copyOnDeath()
		.initializer(() -> MagmaData.DEFAULT)
		.syncWith(MagmaData.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	@FunctionalInterface
	public interface StageChanged {
		/** Server side, after the new stage is stored. Only fires when the stage goes UP. */
		void onStageChanged(ServerPlayer player, MagmaStage from, MagmaStage to);
	}

	public static final Event<StageChanged> STAGE_CHANGED = EventFactory.createArrayBacked(StageChanged.class,
		listeners -> (player, from, to) -> {
			for (StageChanged l : listeners) l.onStageChanged(player, from, to);
		});

	@FunctionalInterface
	public interface MassGained {
		/** Server side, after mass was added. {@code reason} is a short tag such as "absorb_lava", "offering", "eat". */
		void onMassGained(ServerPlayer player, int amount, int newTotal, String reason);
	}

	public static final Event<MassGained> MASS_GAINED = EventFactory.createArrayBacked(MassGained.class,
		listeners -> (player, amount, total, reason) -> {
			for (MassGained l : listeners) l.onMassGained(player, amount, total, reason);
		});

	public static void init() {
		ModTab.init();
	}

	/** Works on both sides (a client only knows its own player's data). */
	public static MagmaData get(Player player) {
		MagmaData d = player.getAttached(MAGMA);
		return d != null ? d : MagmaData.DEFAULT;
	}

	public static void set(ServerPlayer player, MagmaData data) {
		MagmaData old = get(player);
		if (old.equals(data)) return;
		player.setAttached(MAGMA, data);
		if (data.stage().ordinal() > old.stage().ordinal()) {
			STAGE_CHANGED.invoker().onStageChanged(player, old.stage(), data.stage());
		}
	}

	public static MagmaData update(ServerPlayer player, UnaryOperator<MagmaData> change) {
		MagmaData next = change.apply(get(player));
		set(player, next);
		return next;
	}

	/** The only way to add mass. Use small, meaningful amounts; see DESIGN.md for the mass economy. */
	public static void addMass(ServerPlayer player, int amount, String reason) {
		if (amount <= 0) return;
		MagmaData next = update(player, d -> d.withMass(d.mass() + amount));
		MASS_GAINED.invoker().onMassGained(player, amount, next.mass(), reason);
	}
}
