package com.lavagenesis.vent;

import com.lavagenesis.LavaGenesis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * The world's record of every owned Hotspot Vent (so islands around unloaded vents still count) plus the state of
 * running Great Volcano builds. Saved as {@code data/lavagenesis/vents.dat}.
 */
public final class VentRecords extends SavedData {
	/**
	 * @param size  land columns of this vent's island at its last measurement
	 * @param key   smallest packed column of that island (vents with the same key share a landmass), MIN_VALUE if none
	 * @param links packed positions of other vents of the same owner whose surroundings the last flood fill reached
	 */
	public record Vent(UUID owner, String ownerName, boolean home, int size, long key, List<Long> links) {
		public static final Codec<Vent> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("owner").forGetter(Vent::owner),
			Codec.STRING.optionalFieldOf("owner_name", "").forGetter(Vent::ownerName),
			Codec.BOOL.optionalFieldOf("home", false).forGetter(Vent::home),
			Codec.INT.optionalFieldOf("size", 0).forGetter(Vent::size),
			Codec.LONG.optionalFieldOf("key", Long.MIN_VALUE).forGetter(Vent::key),
			Codec.LONG.listOf().optionalFieldOf("links", List.of()).forGetter(Vent::links)
		).apply(i, Vent::new));

		public Vent withHome(boolean h) { return new Vent(owner, ownerName, h, size, key, links); }
		public Vent withMeasure(int s, long k, List<Long> l) { return new Vent(owner, ownerName, home, s, k, List.copyOf(l)); }
	}

	private record Entry(GlobalPos pos, Vent vent) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
			Vent.CODEC.fieldOf("vent").forGetter(Entry::vent)
		).apply(i, Entry::new));
	}

	public static final Codec<VentRecords> CODEC = RecordCodecBuilder.create(i -> i.group(
		Entry.CODEC.listOf().optionalFieldOf("vents", List.of()).forGetter(VentRecords::entries),
		VentFinale.Job.CODEC.listOf().optionalFieldOf("finales", List.of()).forGetter(r -> List.copyOf(r.finales)),
		UUIDUtil.CODEC.listOf().optionalFieldOf("finale_done", List.of()).forGetter(r -> List.copyOf(r.finaleDone))
	).apply(i, VentRecords::new));

	public static final SavedDataType<VentRecords> TYPE = new SavedDataType<>(LavaGenesis.id("vents"), VentRecords::new, CODEC,
		DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<GlobalPos, Vent> vents = new HashMap<>();
	final List<VentFinale.Job> finales = new ArrayList<>();
	final Set<UUID> finaleDone = new HashSet<>();

	public VentRecords() {}

	private VentRecords(List<Entry> entries, List<VentFinale.Job> jobs, List<UUID> done) {
		for (Entry e : entries) vents.put(e.pos(), e.vent());
		finales.addAll(jobs);
		finaleDone.addAll(done);
	}

	private List<Entry> entries() {
		List<Entry> list = new ArrayList<>(vents.size());
		vents.forEach((p, v) -> list.add(new Entry(p, v)));
		return list;
	}

	public static VentRecords get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public @Nullable Vent get(GlobalPos pos) {
		return vents.get(pos);
	}

	public void put(GlobalPos pos, Vent vent) {
		vents.put(pos, vent);
		setDirty();
	}

	public @Nullable Vent remove(GlobalPos pos) {
		Vent v = vents.remove(pos);
		if (v != null) setDirty();
		return v;
	}

	/** All vents of one owner (a fresh map, safe to iterate while changing records). */
	public Map<GlobalPos, Vent> ventsOf(UUID owner) {
		Map<GlobalPos, Vent> out = new HashMap<>();
		vents.forEach((p, v) -> {
			if (v.owner().equals(owner)) out.put(p, v);
		});
		return out;
	}

	public void markFinaleChanged() {
		setDirty();
	}
}
