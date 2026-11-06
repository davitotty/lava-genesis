package com.lavagenesis.eruption.net;

import com.lavagenesis.LavaGenesis;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S2C: shake the camera ({@code strength} in degrees, fading out over {@code ticks}). Sent near big eruptions. */
public record ShakePayload(float strength, int ticks) implements CustomPacketPayload {
	public static final Type<ShakePayload> TYPE = new Type<>(LavaGenesis.id("shake"));
	public static final StreamCodec<ByteBuf, ShakePayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.FLOAT, ShakePayload::strength,
		ByteBufCodecs.VAR_INT, ShakePayload::ticks,
		ShakePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
