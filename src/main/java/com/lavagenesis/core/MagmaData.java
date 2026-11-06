package com.lavagenesis.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Everything shared about one player-as-a-volcano. Immutable: change it with {@link MagmaCore#update}.
 * Each field has ONE owning module that writes it; everyone may read it.
 *
 * @param stage         current life stage (vent module advances it)
 * @param mass          magma gathered so far; any module adds to it through {@link MagmaCore#addMass} only
 * @param heat          0..1, how hot the player's body is; water and rain cool it, lava reheats it (form module)
 * @param pressure      0..100, energy spent by active eruption powers (eruption module)
 * @param homeVent      the player's bound home Hotspot Vent, if any (vent module)
 * @param islands       how many separate islands the player has raised above sea level (vent module)
 * @param largestIsland land columns of the player's largest island (vent module)
 * @param totalLand     land columns summed over all the player's islands (vent module)
 * @param started       whether the player's birth volcano has been built and they were placed in it (land module)
 */
public record MagmaData(MagmaStage stage, int mass, float heat, int pressure, Optional<GlobalPos> homeVent,
	int islands, int largestIsland, int totalLand, boolean started) {

	public static final MagmaData DEFAULT = new MagmaData(MagmaStage.EMBER, 0, 1.0F, 40, Optional.empty(), 0, 0, 0, false);

	public static final Codec<MagmaData> CODEC = RecordCodecBuilder.create(i -> i.group(
		MagmaStage.CODEC.optionalFieldOf("stage", MagmaStage.EMBER).forGetter(MagmaData::stage),
		Codec.INT.optionalFieldOf("mass", 0).forGetter(MagmaData::mass),
		Codec.FLOAT.optionalFieldOf("heat", 1.0F).forGetter(MagmaData::heat),
		Codec.INT.optionalFieldOf("pressure", 40).forGetter(MagmaData::pressure),
		GlobalPos.CODEC.optionalFieldOf("home_vent").forGetter(MagmaData::homeVent),
		Codec.INT.optionalFieldOf("islands", 0).forGetter(MagmaData::islands),
		Codec.INT.optionalFieldOf("largest_island", 0).forGetter(MagmaData::largestIsland),
		Codec.INT.optionalFieldOf("total_land", 0).forGetter(MagmaData::totalLand),
		Codec.BOOL.optionalFieldOf("started", false).forGetter(MagmaData::started)
	).apply(i, MagmaData::new));

	public static final StreamCodec<ByteBuf, MagmaData> STREAM_CODEC = StreamCodec.composite(
		MagmaStage.STREAM_CODEC, MagmaData::stage,
		ByteBufCodecs.VAR_INT, MagmaData::mass,
		ByteBufCodecs.FLOAT, MagmaData::heat,
		ByteBufCodecs.VAR_INT, MagmaData::pressure,
		ByteBufCodecs.optional(GlobalPos.STREAM_CODEC), MagmaData::homeVent,
		ByteBufCodecs.VAR_INT, MagmaData::islands,
		ByteBufCodecs.VAR_INT, MagmaData::largestIsland,
		ByteBufCodecs.VAR_INT, MagmaData::totalLand,
		ByteBufCodecs.BOOL, MagmaData::started,
		MagmaData::new);

	public MagmaData withStage(MagmaStage s) { return new MagmaData(s, mass, heat, pressure, homeVent, islands, largestIsland, totalLand, started); }
	public MagmaData withMass(int m) { return new MagmaData(stage, Math.max(0, m), heat, pressure, homeVent, islands, largestIsland, totalLand, started); }
	public MagmaData withHeat(float h) { return new MagmaData(stage, mass, Math.clamp(h, 0.0F, 1.0F), pressure, homeVent, islands, largestIsland, totalLand, started); }
	public MagmaData withPressure(int p) { return new MagmaData(stage, mass, heat, Math.clamp(p, 0, 100), homeVent, islands, largestIsland, totalLand, started); }
	public MagmaData withHomeVent(Optional<GlobalPos> v) { return new MagmaData(stage, mass, heat, pressure, v, islands, largestIsland, totalLand, started); }
	public MagmaData withIslands(int count, int largest, int total) { return new MagmaData(stage, mass, heat, pressure, homeVent, count, largest, total, started); }
	public MagmaData withStarted(boolean b) { return new MagmaData(stage, mass, heat, pressure, homeVent, islands, largestIsland, totalLand, b); }
}
