package com.lavagenesis.eruption.net;

import com.lavagenesis.LavaGenesis;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S: the player selected a power (cycled with the select key). The server checks it is unlocked. */
public record SelectPowerPayload(int power) implements CustomPacketPayload {
	public static final Type<SelectPowerPayload> TYPE = new Type<>(LavaGenesis.id("select_power"));
	public static final StreamCodec<ByteBuf, SelectPowerPayload> CODEC =
		StreamCodec.composite(ByteBufCodecs.VAR_INT, SelectPowerPayload::power, SelectPowerPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
