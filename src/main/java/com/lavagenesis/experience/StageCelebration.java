package com.lavagenesis.experience;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;

/**
 * What happens on screen and in your ears when you grow into a new stage ({@link MagmaCore#STAGE_CHANGED}):
 * a title, a stage-specific sound mix, a particle burst sized to the new body, a chat summary of what unlocked,
 * and for the Great Volcano a long finale.
 */
final class StageCelebration {
	private StageCelebration() {}

	private static final String K = "experience." + LavaGenesis.MOD_ID + ".";

	static void onStageChanged(ServerPlayer player, MagmaStage from, MagmaStage to) {
		ExperienceAdvancements.grantStagesUpTo(player, to);
		// mass milestones restart for the new stage
		ExperienceData ed = ExperienceData.get(player);
		ExperienceData.set(player, ed.withMassMilestone(Math.max(ed.massMilestone(), to.ordinal() * 4)));

		if (to.isFinal()) {
			if (!ed.has(ExperienceData.FLAG_FINALE_PLAYED)) {
				ExperienceData.set(player, ExperienceData.get(player).withFlag(ExperienceData.FLAG_FINALE_PLAYED, true));
				finale(player).play(player);
				return;
			}
		}
		celebrate(player, to).play(player);
	}

	// ---------------------------------------------------------------- normal stages

	static Cues.Timeline celebrate(ServerPlayer player, MagmaStage to) {
		float size = Math.max(0.3F, to.scale);
		Cues.Timeline t = new Cues.Timeline();

		// the title shows at once; the chat summary a moment later so it is read after the title
		t.at(0, p -> Cues.title(p, stageTitle(to), to.description().copy().withStyle(ChatFormatting.GRAY), 10, 60, 25));
		t.at(0, p -> burst(p, to));
		t.at(20, p -> sendSummary(p, to));
		t.at(6, p -> Cues.ring(p, ParticleTypes.FLAME, 24 + (int) (size * 8), size * 0.6, p.getBbHeight() * 0.3, 0.12 + size * 0.04));
		t.at(12, p -> Cues.ring(p, ParticleTypes.LARGE_SMOKE, 16, size * 0.8, 0.1, 0.06));

		switch (to) {
			case MAGMA_DROPLET -> t
				.at(0, p -> Cues.sound(p, SoundEvents.LAVA_POP, 1.0F, 0.8F))
				.at(3, p -> Cues.sound(p, SoundEvents.LAVA_POP, 1.0F, 1.1F))
				.at(6, p -> Cues.sound(p, SoundEvents.LAVA_POP, 1.0F, 1.4F))
				.at(8, p -> Cues.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.9F))
				.at(10, p -> Cues.sound(p, SoundEvents.PLAYER_LEVELUP, 0.6F, 1.3F));
			case LAVA_FLOW -> t
				.at(0, p -> Cues.sound(p, SoundEvents.BUCKET_EMPTY_LAVA, 1.2F, 0.8F))
				.at(2, p -> Cues.sound(p, SoundEvents.LAVA_AMBIENT, 1.5F, 0.7F))
				.at(6, p -> Cues.sound(p, SoundEvents.BLAZE_SHOOT, 0.8F, 0.6F))
				.at(12, p -> Cues.sound(p, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.2F))
				.every(4, 20, 4, p -> Cues.burst(p, ParticleTypes.LAVA, 4, 0.4, 0.0));
			case SEAMOUNT -> t
				.at(0, p -> Cues.sound(p, SoundEvents.GENERIC_EXPLODE, 1.6F, 0.5F))
				.at(2, p -> Cues.sound(p, SoundEvents.BASALT_PLACE, 2.0F, 0.5F))
				.at(5, p -> Cues.sound(p, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD, 2.0F, 0.8F))
				.at(14, p -> Cues.sound(p, SoundEvents.BEACON_ACTIVATE, 1.2F, 0.8F))
				.every(0, 30, 3, p -> Cues.burst(p, ParticleTypes.BUBBLE_COLUMN_UP, 12, 0.8, 0.2));
			case ISLET -> t
				.at(0, p -> Cues.sound(p, SoundEvents.FIRE_EXTINGUISH, 2.0F, 0.6F))
				.at(1, p -> Cues.sound(p, SoundEvents.LAVA_EXTINGUISH, 2.0F, 0.8F))
				.at(4, p -> Cues.sound(p, SoundEvents.GENERIC_EXPLODE, 1.8F, 0.7F))
				.at(12, p -> Cues.sound(p, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.0F))
				.at(22, p -> Cues.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.6F, 1.0F))
				.every(0, 40, 4, p -> Cues.column(p, ParticleTypes.CLOUD, 10, 6.0, 1.5, 0.35))
				.every(0, 24, 6, p -> Cues.column(p, ParticleTypes.WHITE_SMOKE, 8, 3.0, 2.0, 0.2));
			case SHIELD_ISLAND -> t
				.at(0, p -> Cues.sound(p, SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F))
				.at(8, p -> Cues.sound(p, SoundEvents.GENERIC_EXPLODE, 1.5F, 0.8F))
				.at(4, p -> Cues.sound(p, SoundEvents.LAVA_AMBIENT, 2.0F, 0.6F))
				.at(12, p -> Cues.sound(p, SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.5F, 0.8F))
				.at(18, p -> Cues.sound(p, SoundEvents.BELL_RESONATE, 1.2F, 1.0F))
				.every(0, 30, 5, p -> Cues.ring(p, ParticleTypes.LAVA, 12, 1.2, 0.2, 0.0));
			case ISLAND_CHAIN -> t
				.at(0, p -> Cues.sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.0F, 0.8F))
				.at(6, p -> Cues.sound(p, SoundEvents.BEACON_ACTIVATE, 1.5F, 0.9F))
				.at(10, p -> Cues.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 1.5F, Cues.note(0)))
				.at(14, p -> Cues.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 1.5F, Cues.note(4)))
				.at(18, p -> Cues.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 1.5F, Cues.note(7)))
				.at(22, p -> Cues.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 1.5F, Cues.note(12)))
				.every(0, 30, 6, p -> Cues.column(p, ParticleTypes.LAVA, 8, 4.0, 2.0, 0.0));
			case ARCHIPELAGO -> t
				.at(0, p -> Cues.sound(p, SoundEvents.GENERIC_EXPLODE, 2.5F, 0.55F))
				.at(4, p -> Cues.sound(p, SoundEvents.TOTEM_USE, 1.0F, 0.9F))
				.at(12, p -> Cues.sound(p, SoundEvents.BELL_BLOCK, 2.0F, 0.8F))
				.at(20, p -> Cues.sound(p, SoundEvents.BELL_BLOCK, 2.0F, 1.0F))
				.at(4, p -> Cues.burst(p, ParticleTypes.TOTEM_OF_UNDYING, 80, 1.5, 0.6))
				.every(0, 40, 5, p -> Cues.burst(p, ParticleTypes.HAPPY_VILLAGER, 10, 3.0, 0.0));
			default -> t
				.at(0, p -> Cues.sound(p, SoundEvents.PLAYER_LEVELUP, 1.0F, 0.8F));
		}
		return t;
	}

	static Component stageTitle(MagmaStage s) {
		return s.displayName().copy().withStyle(st -> st.withColor(s.color).withBold(true));
	}

	/** The main particle burst: lava, flame, smoke and ash, scaled to the body. */
	static void burst(ServerPlayer p, MagmaStage s) {
		float size = Math.max(0.25F, s.scale);
		int n = (int) (20 + 30 * size);
		Cues.burst(p, ParticleTypes.LAVA, n / 2, size * 0.5, 0.0);
		Cues.burst(p, ParticleTypes.FLAME, n, size * 0.6, 0.08);
		Cues.burst(p, ParticleTypes.LARGE_SMOKE, n / 2, size * 0.7, 0.03);
		Cues.burst(p, ParticleTypes.ASH, n * 2, size * 1.5, 0.02);
		Cues.burst(p, new DustParticleOptions(s.color, 1.6F), n, size * 0.8, 0.0);
		if (p.isUnderWater()) Cues.burst(p, ParticleTypes.BUBBLE_COLUMN_UP, n, size * 0.8, 0.3);
	}

	// ---------------------------------------------------------------- chat summary

	static void sendSummary(ServerPlayer p, MagmaStage to) {
		p.sendSystemMessage(Component.empty());
		p.sendSystemMessage(Component.literal("» ").withColor(to.color)
			.append(Component.translatable(K + "stage_up.header", stageTitle(to)).withColor(0xFFE0B0))
			.append(Component.literal(" «").withColor(to.color)));
		p.sendSystemMessage(Component.literal("  ").append(to.description().copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));

		// the power this stage wakes (eruption module's key), and the passive gift (form module's key)
		String power = "power." + LavaGenesis.MOD_ID + "." + LavaJournal.POWER_IDS[to.ordinal()];
		if (known(power)) {
			MutableComponent line = bullet(Component.translatable(K + "stage_up.power",
				Component.translatable(power).withStyle(st -> st.withColor(0xFFB43C).withBold(true))));
			if (known(power + ".desc")) {
				line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.translatable(power + ".desc").withStyle(ChatFormatting.GRAY));
			}
			p.sendSystemMessage(line);
		}
		String passive = "passive." + LavaGenesis.MOD_ID + "." + to.getSerializedName();
		if (known(passive)) {
			p.sendSystemMessage(bullet(Component.translatable(K + "stage_up.passive",
				Component.translatable(passive).withColor(0xFFD27A))));
		}
		p.sendSystemMessage(bullet(Component.translatable(K + "stage_up.size",
			Component.literal(Math.round(to.scale * 100) + "%").withColor(0xFFFFFF))));

		MagmaStage next = to.next();
		if (next == null) {
			p.sendSystemMessage(bullet(Component.translatable(K + "stage_up.final").withColor(0xFFD27A)));
		} else {
			p.sendSystemMessage(bullet(Component.translatable(K + "stage_up.next",
				next.displayName().copy().withColor(next.color), requirements(next))));
		}
	}

	/**
	 * Whether another module defines this translation. If the server's language does not even know our own stage
	 * names, mod translations are not loaded on this side (dedicated server), so assume the key exists.
	 */
	private static boolean known(String key) {
		Language lang = Language.getInstance();
		return lang.has(key) || !lang.has("stage." + LavaGenesis.MOD_ID + ".ember");
	}

	private static MutableComponent bullet(Component text) {
		return Component.literal("  • ").withColor(0xFF6A00).append(Component.empty().append(text).withColor(0xD8D0C8));
	}

	/** "900 mass, 1 island, largest island 120, 8 Pele's Tear, home vent" */
	static Component requirements(MagmaStage s) {
		MutableComponent c = Component.translatable(K + "req.mass", LavaJournal.fmt(s.minMass));
		if (s.minIslands > 0) c.append(", ").append(Component.translatable(K + "req.islands", s.minIslands));
		if (s.minLargestIsland > 0) c.append(", ").append(Component.translatable(K + "req.largest", LavaJournal.fmt(s.minLargestIsland)));
		if (s.offering != null && s.offeringCount > 0) {
			c.append(", ").append(Component.translatable(K + "req.offering", s.offeringCount, LavaJournal.itemName(s.offering)));
		}
		if (s.needsVent) c.append(", ").append(Component.translatable(K + "req.vent"));
		return c.withColor(0xB8B0A8);
	}

	// ---------------------------------------------------------------- the Great Volcano finale (~22 s)

	static Cues.Timeline finale(ServerPlayer player) {
		MagmaStage s = MagmaStage.GREAT_VOLCANO;
		Cues.Timeline t = new Cues.Timeline();

		// 0-5 s: the ground groans, smoke gathers
		t.at(0, p -> p.sendOverlayMessage(Component.translatable(K + "finale.rumble").withStyle(st -> st.withColor(0xFF8C1A).withItalic(true))));
		t.every(0, 100, 20, p -> Cues.sound(p, SoundEvents.GENERIC_EXPLODE, 1.2F, 0.5F));
		t.every(10, 110, 20, p -> Cues.sound(p, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD, 2.5F, 0.6F));
		t.every(0, 100, 4, p -> Cues.ring(p, ParticleTypes.LARGE_SMOKE, 10, 2.5, 0.2, 0.05));
		t.every(0, 100, 8, p -> Cues.burst(p, ParticleTypes.ASH, 60, 6.0, 0.02));

		// 3-15 s: the ash column, lava fountains and lightning in the cloud
		t.every(60, 300, 3, p -> Cues.column(p, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, 4, 18.0, 3.0, 0.25));
		t.every(60, 300, 5, p -> Cues.column(p, ParticleTypes.LAVA, 10, 6.0, 2.5, 0.0));
		t.every(60, 300, 4, p -> Cues.column(p, ParticleTypes.FLAME, 8, 5.0, 2.0, 0.4));
		t.every(80, 320, 6, p -> Cues.burst(p, ParticleTypes.WHITE_ASH, 40, 10.0, 0.02));
		t.at(60, p -> Cues.sound(p, SoundEvents.ENDER_DRAGON_GROWL, 1.2F, 0.5F));
		t.at(70, p -> Cues.sound(p, SoundEvents.LAVA_AMBIENT, 3.0F, 0.5F));
		for (int tick : new int[]{90, 125, 150, 190, 215, 250, 290}) t.at(tick, StageCelebration::ashLightning);

		// 5 s: the title, with chimes
		t.at(100, p -> Cues.title(p, stageTitle(s), s.description().copy().withStyle(ChatFormatting.GOLD), 20, 100, 30));
		t.at(100, p -> Cues.sound(p, SoundEvents.BEACON_ACTIVATE, 2.0F, 0.6F));
		t.at(104, p -> Cues.sound(p, SoundEvents.TOTEM_USE, 1.5F, 0.8F));
		t.at(104, p -> Cues.burst(p, ParticleTypes.TOTEM_OF_UNDYING, 200, 3.0, 0.8));
		t.at(100, p -> burst(p, s));
		int[] melody = {0, 4, 7, 12, 7, 12, 16, 19};
		for (int i = 0; i < melody.length; i++) {
			int n = melody[i];
			t.at(112 + i * 5, p -> Cues.sound(p, SoundEvents.NOTE_BLOCK_BELL, 2.0F, Cues.note(n)));
		}

		// 8-13 s: fireworks of lava
		t.every(160, 260, 12, p -> {
			Cues.sound(p, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, 3.0F, 0.7F + p.getRandom().nextFloat() * 0.4F);
			ServerLevel level = p.level();
			RandomSource r = p.getRandom();
			double x = p.getX() + (r.nextDouble() - 0.5) * 24, z = p.getZ() + (r.nextDouble() - 0.5) * 24;
			double y = p.getY() + p.getBbHeight() + 12 + r.nextDouble() * 10;
			level.sendParticles(ParticleTypes.FIREWORK, x, y, z, 60, 0.3, 0.3, 0.3, 0.25);
			level.sendParticles(ParticleTypes.LAVA, x, y, z, 20, 1.5, 1.5, 1.5, 0.0);
			level.sendParticles(new DustParticleOptions(s.color, 3.0F), x, y, z, 40, 2.0, 2.0, 2.0, 0.0);
		});
		t.at(140, p -> sendSummary(p, s));
		t.at(200, StageCelebration::journeyLine);

		// 15-22 s: calm, a second title, a final chord
		t.at(330, p -> Cues.title(p, Component.translatable(K + "finale.title2").withStyle(st -> st.withColor(0xFFD27A).withItalic(true)),
			Component.translatable(K + "finale.subtitle2").withStyle(ChatFormatting.GRAY), 30, 90, 40));
		t.at(330, p -> Cues.sound(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F));
		t.at(380, p -> Cues.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.6F));
		t.at(384, p -> Cues.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 2.0F, Cues.note(0)));
		t.at(384, p -> Cues.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 2.0F, Cues.note(7)));
		t.at(384, p -> Cues.sound(p, SoundEvents.NOTE_BLOCK_CHIME, 2.0F, Cues.note(16)));
		t.every(330, 440, 10, p -> Cues.burst(p, ParticleTypes.END_ROD, 12, 4.0, 0.02));
		return t;
	}

	/** Visual-only lightning somewhere in the ash cloud above the volcano. */
	private static void ashLightning(ServerPlayer p) {
		ServerLevel level = p.level();
		LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
		if (bolt == null) return;
		RandomSource r = p.getRandom();
		double ang = r.nextDouble() * Math.PI * 2, dist = 8 + r.nextDouble() * 16;
		bolt.snapTo(p.getX() + Math.cos(ang) * dist, p.getY() + p.getBbHeight() + 6 + r.nextDouble() * 10, p.getZ() + Math.sin(ang) * dist);
		bolt.setVisualOnly(true);
		level.addFreshEntity(bolt);
	}

	private static void journeyLine(ServerPlayer p) {
		MagmaData d = MagmaCore.get(p);
		ExperienceData ed = ExperienceData.get(p);
		long ticks = ed.bornAt() >= 0 ? Math.max(0, p.level().getGameTime() - ed.bornAt()) : -1;
		Component time = ticks >= 0 ? Component.literal(duration(ticks)).withColor(0xFFFFFF) : Component.literal("?");
		p.sendSystemMessage(bullet(Component.translatable(K + "finale.journey",
			time,
			Component.literal(LavaJournal.fmt(d.mass())).withColor(0xFFFFFF),
			Component.literal(String.valueOf(d.islands())).withColor(0xFFFFFF),
			Component.literal(LavaJournal.fmt(d.totalLand())).withColor(0xFFFFFF))));
	}

	static String duration(long ticks) {
		long minutes = ticks / 1200;
		long h = minutes / 60, m = minutes % 60;
		return h > 0 ? h + "h " + m + "m" : m + "m";
	}
}
