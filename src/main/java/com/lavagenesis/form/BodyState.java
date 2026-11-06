package com.lavagenesis.form;

import com.lavagenesis.core.MagmaStage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * What every client needs to know to draw a player's lava body: the stage (for remote players the core MAGMA
 * attachment is only synced to its owner) and how crusted the body is (0 hot .. 3 solidified).
 * Written by the form module on the server only when it changes; synced to everyone tracking the player.
 */
public record BodyState(int stage, int crust) {
	public static final BodyState DEFAULT = new BodyState(0, 0);

	public static final StreamCodec<ByteBuf, BodyState> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, BodyState::stage,
		ByteBufCodecs.VAR_INT, BodyState::crust,
		BodyState::new);

	public MagmaStage stageEnum() {
		MagmaStage[] all = MagmaStage.values();
		return all[Math.clamp(stage, 0, all.length - 1)];
	}
}
