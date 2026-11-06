package com.lavagenesis.experience;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Entry point of the experience module (see DESIGN.md): the Lava Journal, stage-up celebrations, mass milestone
 * feedback and the advancement tree. The HUD lives in {@code experience.client}.
 */
public final class ExperienceModule {
	private ExperienceModule() {}

	private static final String K = "experience." + LavaGenesis.MOD_ID + ".";
	/** Ticks a player must have been in the world before the journal is handed over (lets the birth volcano settle). */
	private static final int WELCOME_DELAY = 60;

	public static void init() {
		ExperienceData.init();
		LavaJournal.register();

		MagmaCore.STAGE_CHANGED.register(StageCelebration::onStageChanged);
		MagmaCore.MASS_GAINED.register(MassFeedback::onMassGained);

		ServerTickEvents.END_SERVER_TICK.register(ExperienceModule::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> Cues.clear());
	}

	private static void tick(MinecraftServer server) {
		Cues.tick(server);
		int now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// spread the once-a-second checks over the second
			if ((now + (player.getId() & 15)) % 20 != 0) continue;
			try {
				perSecond(player);
			} catch (RuntimeException e) {
				LavaGenesis.LOGGER.warn("Lava Genesis experience check failed for {}", player.getName().getString(), e);
			}
		}
	}

	private static void perSecond(ServerPlayer player) {
		ExperienceData ed = ExperienceData.get(player);
		if (ed.bornAt() < 0) {
			ed = ed.withBornAt(player.level().getGameTime());
			ExperienceData.set(player, ed);
		}
		if (!ed.journalGiven() && player.tickCount >= WELCOME_DELAY && player.isAlive()) {
			welcome(player);
			ed = ExperienceData.get(player);
		}
		ExperienceAdvancements.periodicCheck(player);

		MagmaData d = MagmaCore.get(player);
		if (d.started() && d.heat() < 0.3F && !ed.has(ExperienceData.FLAG_LOW_HEAT_WARNED)) {
			ExperienceData.set(player, ed.withFlag(ExperienceData.FLAG_LOW_HEAT_WARNED, true));
			player.sendSystemMessage(Component.literal("  • ").withColor(0x8899AA)
				.append(Component.translatable(K + "hint.cooling").withColor(0xB8C8D8)));
			Cues.sound(player, SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.4F);
		}
	}

	/** First time in the world: hand over the Lava Journal and say hello. */
	private static void welcome(ServerPlayer player) {
		ExperienceData.set(player, ExperienceData.get(player).withJournalGiven(true));
		LavaJournal.give(player);
		ExperienceAdvancements.grant(player, ExperienceAdvancements.ROOT);
		player.sendSystemMessage(Component.literal("» ").withColor(0xFF6A00)
			.append(Component.translatable(K + "welcome.title").withStyle(s -> s.withColor(0xFFB43C).withBold(true))));
		player.sendSystemMessage(Component.literal("  ").append(Component.translatable(K + "welcome.journal").withColor(0xD8D0C8)));
		Cues.sound(player, SoundEvents.LAVA_POP, 0.8F, 1.2F);
		Cues.sound(player, SoundEvents.BOOK_PAGE_TURN, 1.0F, 0.9F);
	}
}
