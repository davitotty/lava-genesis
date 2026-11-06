package com.lavagenesis.land.birth;

import com.lavagenesis.LavaGenesis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;

/**
 * Every birth volcano ever built in a world (stored on the overworld as a persistent attachment), so each player's
 * volcano is built well apart from the others and {@code /lavagenesis tp home} has a fallback.
 */
public record BirthSites(List<Site> sites) {
	/** One birth volcano: who it was built for, its centre on the sea floor and where its home vent was placed. */
	public record Site(UUID owner, BlockPos center, BlockPos vent) {
		public static final Codec<Site> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Site::owner),
			BlockPos.CODEC.fieldOf("center").forGetter(Site::center),
			BlockPos.CODEC.fieldOf("vent").forGetter(Site::vent)
		).apply(i, Site::new));
	}

	public static final BirthSites EMPTY = new BirthSites(List.of());
	public static final Codec<BirthSites> CODEC = Site.CODEC.listOf().xmap(BirthSites::new, BirthSites::sites);

	public static final AttachmentType<BirthSites> TYPE = AttachmentRegistry.create(LavaGenesis.id("birth_sites"),
		b -> b.persistent(CODEC).initializer(() -> EMPTY));

	static void init() {
		// class loading registers the attachment
	}

	public static BirthSites get(ServerLevel overworld) {
		BirthSites s = overworld.getAttached(TYPE);
		return s != null ? s : EMPTY;
	}

	public static void add(ServerLevel overworld, Site site) {
		List<Site> next = new ArrayList<>(get(overworld).sites);
		next.add(site);
		overworld.setAttached(TYPE, new BirthSites(List.copyOf(next)));
	}

	/** The most recent birth volcano built for this player. */
	public Optional<Site> latestFor(UUID owner) {
		for (int i = sites.size() - 1; i >= 0; i--) {
			if (sites.get(i).owner.equals(owner)) return Optional.of(sites.get(i));
		}
		return Optional.empty();
	}

	public List<BlockPos> centers() {
		return sites.stream().map(Site::center).toList();
	}
}
