package com.lavagenesis.experience;

import com.lavagenesis.LavaGenesis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * The experience module's own per-player memory (server only, saved, kept on death).
 *
 * @param journalGiven  the Lava Journal was handed out once
 * @param bornAt        game time when this player was first seen (for the finale's "spark to volcano in ..." line)
 * @param massMilestone highest mass milestone already announced, as {@code stage.ordinal() * 4 + quarter}
 * @param flags         one-shot feedback bits (see {@link #FLAG_LOW_HEAT_WARNED} etc.)
 */
public record ExperienceData(boolean journalGiven, long bornAt, int massMilestone, int flags) {
	public static final int FLAG_LOW_HEAT_WARNED = 1;
	public static final int FLAG_FINALE_PLAYED = 1 << 1;

	public static final ExperienceData DEFAULT = new ExperienceData(false, -1L, 0, 0);

	public static final Codec<ExperienceData> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.BOOL.optionalFieldOf("journal_given", false).forGetter(ExperienceData::journalGiven),
		Codec.LONG.optionalFieldOf("born_at", -1L).forGetter(ExperienceData::bornAt),
		Codec.INT.optionalFieldOf("mass_milestone", 0).forGetter(ExperienceData::massMilestone),
		Codec.INT.optionalFieldOf("flags", 0).forGetter(ExperienceData::flags)
	).apply(i, ExperienceData::new));

	public static final AttachmentType<ExperienceData> TYPE = AttachmentRegistry.create(LavaGenesis.id("experience"), b -> b
		.persistent(CODEC)
		.copyOnDeath()
		.initializer(() -> DEFAULT));

	public static ExperienceData get(ServerPlayer player) {
		ExperienceData d = player.getAttached(TYPE);
		return d != null ? d : DEFAULT;
	}

	public static void set(ServerPlayer player, ExperienceData data) {
		player.setAttached(TYPE, data);
	}

	public boolean has(int flag) {
		return (flags & flag) != 0;
	}

	public ExperienceData withJournalGiven(boolean b) { return new ExperienceData(b, bornAt, massMilestone, flags); }
	public ExperienceData withBornAt(long t) { return new ExperienceData(journalGiven, t, massMilestone, flags); }
	public ExperienceData withMassMilestone(int m) { return new ExperienceData(journalGiven, bornAt, m, flags); }
	public ExperienceData withFlag(int flag, boolean on) {
		return new ExperienceData(journalGiven, bornAt, massMilestone, on ? flags | flag : flags & ~flag);
	}

	/** Forces class loading so the attachment is registered during mod init. */
	static void init() {}
}
