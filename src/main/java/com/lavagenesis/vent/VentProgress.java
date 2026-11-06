package com.lavagenesis.vent;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.LavaIds;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Public read API for the stage requirements and the offerings given at the home vent. Works on both sides: the
 * offering count is an attachment synced to the owning client, everything else comes from {@link MagmaData}.
 * <pre>
 *   VentProgress.offered(player)        // offering items already given towards the NEXT stage
 *   VentProgress.offeringNeeded(player) // how many the next stage asks for (0 = none)
 *   VentProgress.offeringItem(player)   // which item (null = none)
 *   VentProgress.requirements(player)   // the whole checklist for the next stage
 *   VentProgress.ready(player)          // true when everything is met
 * </pre>
 */
public final class VentProgress {
	private VentProgress() {}

	/** @param stage the stage the offerings count towards (offerings for an older target are void) */
	public record Data(String stage, int offered) {
		public static final Data EMPTY = new Data("", 0);
		public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("stage", "").forGetter(Data::stage),
			Codec.INT.optionalFieldOf("offered", 0).forGetter(Data::offered)
		).apply(i, Data::new));
		public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Data::stage,
			ByteBufCodecs.VAR_INT, Data::offered,
			Data::new);
	}

	public static final AttachmentType<Data> OFFERINGS = AttachmentRegistry.create(LavaGenesis.id("vent_offerings"), b -> b
		.persistent(Data.CODEC)
		.copyOnDeath()
		.initializer(() -> Data.EMPTY)
		.syncWith(Data.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	static void init() {
		// class load registers the attachment
	}

	/** One line of the requirement checklist. {@code required == 0} lines are not asked for at this stage. */
	public record Requirement(String key, int current, int required, boolean met, @Nullable Item item) {
		/** "requirement.lavagenesis.<key>" */
		public Component label() {
			return Component.translatable("requirement.lavagenesis." + key);
		}
	}

	public static @Nullable MagmaStage nextStage(Player player) {
		return MagmaCore.get(player).stage().next();
	}

	/** Offering items given at the home vent towards the next stage. */
	public static int offered(Player player) {
		MagmaStage next = nextStage(player);
		if (next == null) return 0;
		Data d = player.getAttached(OFFERINGS);
		return d != null && d.stage().equals(next.getSerializedName()) ? d.offered() : 0;
	}

	public static int offeringNeeded(Player player) {
		MagmaStage next = nextStage(player);
		return next == null || next.offering == null ? 0 : next.offeringCount;
	}

	public static @Nullable Item offeringItem(Player player) {
		MagmaStage next = nextStage(player);
		if (next == null || next.offering == null) return null;
		Item item = LavaIds.item(next.offering);
		return item == null || item == Items.AIR ? null : item;
	}

	/** The checklist for the next stage (empty at the final stage). */
	public static List<Requirement> requirements(Player player) {
		List<Requirement> list = new ArrayList<>();
		MagmaStage next = nextStage(player);
		if (next == null) return list;
		MagmaData d = MagmaCore.get(player);
		list.add(new Requirement("mass", d.mass(), next.minMass, d.mass() >= next.minMass, null));
		if (next.minIslands > 0) list.add(new Requirement("islands", d.islands(), next.minIslands, d.islands() >= next.minIslands, null));
		if (next.minLargestIsland > 0) {
			list.add(new Requirement("largest_island", d.largestIsland(), next.minLargestIsland, d.largestIsland() >= next.minLargestIsland, null));
		}
		if (next.offering != null && next.offeringCount > 0) {
			int given = offered(player);
			list.add(new Requirement("offering", given, next.offeringCount, given >= next.offeringCount, offeringItem(player)));
		}
		if (next.needsVent) {
			boolean has = player instanceof ServerPlayer sp ? VentRegistry.hasHomeVent(sp) : d.homeVent().isPresent();
			list.add(new Requirement("home_vent", has ? 1 : 0, 1, has, null));
		}
		return list;
	}

	public static boolean ready(Player player) {
		if (nextStage(player) == null) return false;
		for (Requirement r : requirements(player)) if (!r.met()) return false;
		return true;
	}

	/** Server: adds {@code n} offerings towards the next stage. */
	static void addOffered(ServerPlayer player, int n) {
		MagmaStage next = nextStage(player);
		if (next == null) return;
		player.setAttached(OFFERINGS, new Data(next.getSerializedName(), offered(player) + n));
	}

	static void reset(ServerPlayer player) {
		player.setAttached(OFFERINGS, Data.EMPTY);
	}

	/** Display name of an offering item, or its id when the land module has not registered it. */
	static Component itemName(@Nullable Item item, @Nullable Identifier fallback) {
		if (item != null) return new ItemStack(item).getHoverName();
		return Component.literal(fallback == null ? "?" : fallback.getPath());
	}
}
