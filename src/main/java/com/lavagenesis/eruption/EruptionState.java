package com.lavagenesis.eruption;

import com.lavagenesis.LavaGenesis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The eruption module's own per-player state (persistent, copied on death, synced to the owning client).
 *
 * @param selected    ordinal of the selected {@link Power}
 * @param readyAt     per power ordinal: the game time at which the power is ready again
 * @param seededVents island vents this player seeded with Hotspot Leap (to keep new islands 48+ blocks apart)
 */
public record EruptionState(int selected, List<Long> readyAt, List<GlobalPos> seededVents) {

	public static final EruptionState DEFAULT = new EruptionState(0, List.of(), List.of());

	public static final Codec<EruptionState> CODEC = RecordCodecBuilder.create(i -> i.group(
		Codec.INT.optionalFieldOf("selected", 0).forGetter(EruptionState::selected),
		Codec.LONG.listOf().optionalFieldOf("ready_at", List.of()).forGetter(EruptionState::readyAt),
		GlobalPos.CODEC.listOf().optionalFieldOf("seeded_vents", List.of()).forGetter(EruptionState::seededVents)
	).apply(i, EruptionState::new));

	public static final StreamCodec<ByteBuf, EruptionState> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, EruptionState::selected,
		ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), EruptionState::readyAt,
		GlobalPos.STREAM_CODEC.apply(ByteBufCodecs.list()), EruptionState::seededVents,
		EruptionState::new);

	public static final AttachmentType<EruptionState> ATTACHMENT = AttachmentRegistry.create(LavaGenesis.id("eruption"), b -> b
		.persistent(CODEC)
		.copyOnDeath()
		.initializer(() -> DEFAULT)
		.syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Loads the class (registers the attachment). */
	static void init() {}

	public static EruptionState get(Player player) {
		EruptionState s = player.getAttached(ATTACHMENT);
		return s != null ? s : DEFAULT;
	}

	static void set(ServerPlayer player, EruptionState state) {
		if (!get(player).equals(state)) player.setAttached(ATTACHMENT, state);
	}

	public Power selectedPower() {
		Power p = Power.byId(selected);
		return p != null ? p : Power.FLARE;
	}

	/** Game time at which {@code power} is ready (0 if it never was used). */
	public long readyAt(Power power) {
		return power.ordinal() < readyAt.size() ? readyAt.get(power.ordinal()) : 0L;
	}

	public EruptionState withSelected(Power p) {
		return new EruptionState(p.ordinal(), readyAt, seededVents);
	}

	public EruptionState withReadyAt(Power p, long time) {
		List<Long> list = new ArrayList<>(readyAt);
		while (list.size() < Power.count()) list.add(0L);
		list.set(p.ordinal(), time);
		return new EruptionState(selected, List.copyOf(list), seededVents);
	}

	public EruptionState withSeededVent(GlobalPos pos) {
		List<GlobalPos> list = new ArrayList<>(seededVents);
		list.add(pos);
		return new EruptionState(selected, readyAt, List.copyOf(list));
	}
}
