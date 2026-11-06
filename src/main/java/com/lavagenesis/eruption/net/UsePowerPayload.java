package com.lavagenesis.eruption.net;

import com.lavagenesis.LavaGenesis;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the player pressed a power key. {@code power} is a {@link com.lavagenesis.eruption.Power} ordinal. */
public record UsePowerPayload(int power) implements CustomPacketPayload {
	public static final Type<UsePowerPayload> TYPE = new Type<>(LavaGenesis.id("use_power"));
	public static final StreamCodec<ByteBuf, UsePowerPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, UsePowerPayload::power, UsePowerPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
