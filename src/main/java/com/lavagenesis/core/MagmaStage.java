package com.lavagenesis.core;

import com.lavagenesis.LavaGenesis;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.Nullable;

/**
 * The life of a hotspot volcano, from one spark of lava to a giant shield volcano ruling an archipelago.
 * <p>
 * A stage is reached at the player's home Hotspot Vent when ALL of these hold:
 * <ul>
 *   <li>{@code mass >= minMass} (magma the player has gathered),</li>
 *   <li>{@code islands >= minIslands} (separate islands the player has raised above sea level),</li>
 *   <li>{@code largestIsland >= minLargestIsland} (land columns of the player's biggest island),</li>
 *   <li>{@code offeringCount} of the {@code offering} item have been given to the home vent (if an offering is set),</li>
 *   <li>a home vent is bound when {@code needsVent} is true.</li>
 * </ul>
 * {@code scale} is the player's body size at that stage (the minecraft:scale attribute, 1.0 = normal player).
 */
public enum MagmaStage implements StringRepresentable {
	EMBER("ember",                   0, 0,    0, null,                  0, false, 0.10F, 0xFFB43C),
	MAGMA_DROPLET("magma_droplet",  60, 0,    0, null,                  0, false, 0.18F, 0xFF8C1A),
	LAVA_FLOW("lava_flow",         200, 0,    0, LavaIds.MAGMA_SHARD,   8, false, 0.30F, 0xFF6A00),
	SEAMOUNT("seamount",           500, 0,    0, LavaIds.OLIVINE,       4, true,  0.50F, 0xE0480E),
	ISLET("islet",                 900, 1,  120, null,                  0, true,  0.75F, 0xC93A0C),
	SHIELD_ISLAND("shield_island", 1600, 1, 600, LavaIds.PELES_TEAR,    8, true,  1.00F, 0xB0300A),
	ISLAND_CHAIN("island_chain",   2800, 3, 600, LavaIds.SULFUR_CRYSTAL, 8, true, 1.40F, 0x962808),
	ARCHIPELAGO("archipelago",     4500, 5, 900, LavaIds.VOLCANIC_GLASS, 16, true, 2.00F, 0x7A2006),
	GREAT_VOLCANO("great_volcano", 7000, 6, 2500, LavaIds.HOTSPOT_HEART, 1, true, 3.00F, 0xFF3300);

	public static final Codec<MagmaStage> CODEC = StringRepresentable.fromEnum(MagmaStage::values);
	public static final StreamCodec<ByteBuf, MagmaStage> STREAM_CODEC =
		ByteBufCodecs.VAR_INT.map(i -> values()[Math.clamp(i, 0, values().length - 1)], MagmaStage::ordinal);

	private final String name;
	public final int minMass;
	public final int minIslands;
	public final int minLargestIsland;
	public final @Nullable Identifier offering;
	public final int offeringCount;
	public final boolean needsVent;
	public final float scale;
	public final int color;

	MagmaStage(String name, int minMass, int minIslands, int minLargestIsland, @Nullable Identifier offering,
		int offeringCount, boolean needsVent, float scale, int color) {
		this.name = name;
		this.minMass = minMass;
		this.minIslands = minIslands;
		this.minLargestIsland = minLargestIsland;
		this.offering = offering;
		this.offeringCount = offeringCount;
		this.needsVent = needsVent;
		this.scale = scale;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public @Nullable MagmaStage next() {
		return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
	}

	public boolean atLeast(MagmaStage other) {
		return ordinal() >= other.ordinal();
	}

	public boolean isFinal() {
		return this == GREAT_VOLCANO;
	}

	/** True from ISLET on: the player has broken the ocean surface. */
	public boolean isAboveWater() {
		return atLeast(ISLET);
	}

	/** Translation key "stage.lavagenesis.<name>". */
	public Component displayName() {
		return Component.translatable("stage." + LavaGenesis.MOD_ID + "." + name);
	}

	/** Translation key "stage.lavagenesis.<name>.desc": one line about this stage. */
	public Component description() {
		return Component.translatable("stage." + LavaGenesis.MOD_ID + "." + name + ".desc");
	}
}
