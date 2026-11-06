package com.lavagenesis.vent;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/** Vent ownership: placing and binding vents, the home vent, and summing a player's islands from the records. */
public final class VentRegistry {
	private VentRegistry() {}

	/** An island needs at least this many land columns to count towards {@code islands}. */
	public static final int MIN_ISLAND = 9;

	/** The {@link com.lavagenesis.core.VentAccess} implementation. */
	static boolean placeVent(ServerPlayer owner, ServerLevel level, BlockPos pos, boolean home) {
		if (level.isOutsideBuildHeight(pos)) return false;
		if (!level.setBlock(pos, VentBlocks.HOTSPOT_VENT.defaultBlockState(), Block.UPDATE_ALL)) {
			if (!level.getBlockState(pos).is(VentBlocks.HOTSPOT_VENT)) return false;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof HotspotVentBlockEntity vent)) return false;
		bind(owner, level, pos, vent, home);
		return true;
	}

	/** Gives an existing vent block to {@code owner}; {@code home} makes it the player's home vent. */
	static void bind(ServerPlayer owner, ServerLevel level, BlockPos pos, HotspotVentBlockEntity vent, boolean home) {
		vent.bind(owner, home);
		GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
		VentRecords records = VentRecords.get(level.getServer());
		records.put(gp, new VentRecords.Vent(owner.getUUID(), owner.getName().getString(), home, 0, Long.MIN_VALUE, List.of()));
		if (home) makeHome(owner, gp);
		IslandSurveyor.request(level, pos, owner.getUUID());
		LavaGenesis.LOGGER.debug("Hotspot vent at {} bound to {} (home={})", gp, owner.getName().getString(), home);
	}

	/** Makes {@code gp} the player's home vent; a previous home vent becomes an island vent. */
	static void makeHome(ServerPlayer owner, GlobalPos gp) {
		MinecraftServer server = owner.level().getServer();
		VentRecords records = VentRecords.get(server);
		Optional<GlobalPos> old = MagmaCore.get(owner).homeVent();
		if (old.isPresent() && !old.get().equals(gp)) {
			VentRecords.Vent rec = records.get(old.get());
			if (rec != null && rec.owner().equals(owner.getUUID())) records.put(old.get(), rec.withHome(false));
			HotspotVentBlockEntity oldBe = loadedVent(server, old.get());
			if (oldBe != null) oldBe.setHome(false);
		}
		VentRecords.Vent rec = records.get(gp);
		if (rec != null && !rec.home()) records.put(gp, rec.withHome(true));
		HotspotVentBlockEntity be = loadedVent(server, gp);
		if (be != null) be.setHome(true);
		MagmaCore.update(owner, d -> d.withHomeVent(Optional.of(gp)));
	}

	/** The vent block entity at {@code gp} if its chunk is loaded (never loads a chunk). */
	static @Nullable HotspotVentBlockEntity loadedVent(MinecraftServer server, GlobalPos gp) {
		ServerLevel level = server.getLevel(gp.dimension());
		if (level == null) return null;
		BlockPos p = gp.pos();
		if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null) return null;
		return level.getBlockEntity(p) instanceof HotspotVentBlockEntity v ? v : null;
	}

	/** Called when a vent block is removed (broken, exploded, replaced). */
	static void onVentRemoved(ServerLevel level, BlockPos pos) {
		GlobalPos gp = GlobalPos.of(level.dimension(), pos.immutable());
		VentRecords records = VentRecords.get(level.getServer());
		VentRecords.Vent rec = records.remove(gp);
		IslandSurveyor.forget(level, pos);
		if (rec == null) return;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(rec.owner());
		if (owner != null) {
			MagmaData d = MagmaCore.get(owner);
			if (d.homeVent().isPresent() && d.homeVent().get().equals(gp)) {
				MagmaCore.update(owner, x -> x.withHomeVent(Optional.empty()));
				owner.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.lavagenesis.vent.home_lost")
					.withStyle(net.minecraft.ChatFormatting.RED));
			}
			updateIslands(owner);
		}
	}

	/** True when the player's stored home vent still exists in the world record. */
	public static boolean hasHomeVent(ServerPlayer player) {
		Optional<GlobalPos> home = MagmaCore.get(player).homeVent();
		if (home.isEmpty()) return false;
		VentRecords.Vent rec = VentRecords.get(player.level().getServer()).get(home.get());
		return rec != null && rec.owner().equals(player.getUUID());
	}

	/** Clears a home vent that no longer exists (it was broken while the player was away). */
	static void validateHome(ServerPlayer player) {
		Optional<GlobalPos> home = MagmaCore.get(player).homeVent();
		if (home.isPresent() && !hasHomeVent(player)) {
			MagmaCore.update(player, d -> d.withHomeVent(Optional.empty()));
		}
	}

	/** Islands of one player, summed from the saved vent records (deduped per landmass). */
	public record Totals(int islands, int largest, int total) {}

	public static Totals totals(MinecraftServer server, UUID owner) {
		Map<GlobalPos, VentRecords.Vent> mine = VentRecords.get(server).ventsOf(owner);
		List<GlobalPos> keys = new ArrayList<>(mine.keySet());
		int n = keys.size();
		int[] parent = new int[n];
		for (int i = 0; i < n; i++) parent[i] = i;
		Map<GlobalPos, Integer> index = new HashMap<>();
		for (int i = 0; i < n; i++) index.put(keys.get(i), i);
		Map<String, Integer> byKey = new HashMap<>();
		for (int i = 0; i < n; i++) {
			GlobalPos p = keys.get(i);
			VentRecords.Vent v = mine.get(p);
			if (v.size() <= 0) continue;
			if (v.key() != Long.MIN_VALUE) {
				String k = p.dimension().identifier() + "#" + v.key();
				Integer other = byKey.putIfAbsent(k, i);
				if (other != null) union(parent, i, other);
			}
			for (long link : v.links()) {
				Integer j = index.get(GlobalPos.of(p.dimension(), BlockPos.of(link)));
				if (j != null && mine.get(keys.get(j)).size() > 0) union(parent, i, j);
			}
		}
		Map<Integer, Integer> groupSize = new HashMap<>();
		for (int i = 0; i < n; i++) {
			int size = mine.get(keys.get(i)).size();
			if (size <= 0) continue;
			groupSize.merge(find(parent, i), size, Math::max);
		}
		int islands = 0, largest = 0, total = 0;
		for (int s : groupSize.values()) {
			if (s >= MIN_ISLAND) islands++;
			largest = Math.max(largest, s);
			total += s;
		}
		return new Totals(islands, largest, total);
	}

	/** Writes the player's islands / largest island / total land into MagmaData. */
	public static void updateIslands(ServerPlayer player) {
		Totals t = totals(player.level().getServer(), player.getUUID());
		MagmaData d = MagmaCore.get(player);
		if (d.islands() != t.islands() || d.largestIsland() != t.largest() || d.totalLand() != t.total()) {
			MagmaCore.update(player, x -> x.withIslands(t.islands(), t.largest(), t.total()));
		}
	}

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	private static void union(int[] parent, int a, int b) {
		int ra = find(parent, a), rb = find(parent, b);
		if (ra != rb) parent[ra] = rb;
	}
}
