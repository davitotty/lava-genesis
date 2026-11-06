package com.lavagenesis.experience;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Small, non-spammy feedback on mass gains ({@link MagmaCore#MASS_GAINED}): an action-bar line, a chime and a puff of
 * sparks only when the player crosses a quarter of the way to the next stage's mass (25 / 50 / 75 / 100 %), and
 * every 2,500 mass after the final stage. Each milestone is announced once (remembered in {@link ExperienceData}).
 */
final class MassFeedback {
	private MassFeedback() {}

	private static final String K = "experience." + LavaGenesis.MOD_ID + ".";
	private static final int FINAL_STEP = 2500;

	static void onMassGained(ServerPlayer player, int amount, int total, String reason) {
		ExperienceAdvancements.onMassReason(player, reason);

		MagmaData d = MagmaCore.get(player);
		MagmaStage stage = d.stage();
		MagmaStage next = stage.next();
		int milestone;
		int quarter;
		if (next == null) {
			int steps = Math.max(0, (total - stage.minMass) / FINAL_STEP);
			if (steps == 0) return;
			milestone = (stage.ordinal() + 1) * 4 + steps;
			quarter = -1;
		} else {
			int from = stage.minMass, to = next.minMass;
			float frac = to > from ? (total - from) / (float) (to - from) : 1.0F;
			quarter = Math.min(4, (int) Math.floor(Math.max(0.0F, frac) * 4.0F));
			if (quarter == 0) return;
			milestone = stage.ordinal() * 4 + quarter;
		}

		ExperienceData ed = ExperienceData.get(player);
		if (milestone <= ed.massMilestone()) return;
		ExperienceData.set(player, ed.withMassMilestone(milestone));

		if (next == null) {
			player.sendOverlayMessage(Component.translatable(K + "mass.beyond", LavaJournal.fmt(total)).withColor(stage.color));
			Cues.sound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.7F);
			Cues.burst(player, ParticleTypes.FLAME, 30, stage.scale * 0.6, 0.05);
		} else if (quarter < 4) {
			player.sendOverlayMessage(Component.translatable(K + "mass.quarter", quarter * 25,
				next.displayName().copy().withColor(next.color), LavaJournal.fmt(total), LavaJournal.fmt(next.minMass)).withColor(0xFFD27A));
			Cues.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.7F + quarter * 0.2F);
			Cues.burst(player, ParticleTypes.FLAME, 6 + quarter * 4, Math.max(0.15, stage.scale * 0.5), 0.03);
			Cues.burst(player, ParticleTypes.LAVA, 2 + quarter, Math.max(0.1, stage.scale * 0.3), 0.0);
		} else {
			player.sendOverlayMessage(Component.translatable(K + "mass.ready",
				next.displayName().copy().withColor(next.color)).withColor(0xFFE8A0));
			Cues.sound(player, SoundEvents.PLAYER_LEVELUP, 0.6F, 0.7F);
			Cues.sound(player, SoundEvents.LAVA_POP, 1.0F, 0.6F);
			Cues.burst(player, ParticleTypes.FLAME, 24, Math.max(0.2, stage.scale * 0.6), 0.06);
			Cues.burst(player, ParticleTypes.LAVA, 8, Math.max(0.2, stage.scale * 0.4), 0.0);
			player.sendSystemMessage(Component.literal("  • ").withColor(0xFF6A00)
				.append(Component.translatable(K + "mass.ready_chat", next.displayName().copy().withColor(next.color),
					StageCelebration.requirements(next)).withColor(0xD8D0C8)));
		}
	}
}
