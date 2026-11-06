package com.lavagenesis.vent;

import com.lavagenesis.core.MagmaCore;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One Hotspot Vent: who owns it, whether it is the owner's home vent, and the size of its island when it was last
 * measured. The owner's stage is mirrored here (and synced) so the renderer can grow the magma core with the owner.
 */
public class HotspotVentBlockEntity extends BlockEntity {
	/** Ticks between two island measurements of one vent. */
	public static final int MEASURE_INTERVAL = 100;

	private @Nullable UUID owner;
	private String ownerName = "";
	private boolean home;
	private int lastIsland;
	private int ownerStage;
	private boolean finale;

	public HotspotVentBlockEntity(BlockPos pos, BlockState state) {
		super(VentBlocks.HOTSPOT_VENT_BE, pos, state);
	}

	public @Nullable UUID owner() { return owner; }
	public String ownerName() { return ownerName; }
	public boolean isHome() { return home; }
	public int lastIsland() { return lastIsland; }
	/** Ordinal of the owner's MagmaStage (synced to clients). */
	public int ownerStage() { return ownerStage; }
	/** True while this vent's Great Volcano is being built (synced to clients). */
	public boolean finale() { return finale; }

	public boolean isOwnedBy(Player player) {
		return owner != null && owner.equals(player.getUUID());
	}

	/** Server: give this vent to {@code player}. */
	public void bind(ServerPlayer player, boolean asHome) {
		owner = player.getUUID();
		ownerName = player.getName().getString();
		home = asHome;
		ownerStage = MagmaCore.get(player).stage().ordinal();
		sync();
	}

	void setHome(boolean value) {
		if (home != value) {
			home = value;
			sync();
		}
	}

	void setLastIsland(int size) {
		if (lastIsland != size) {
			lastIsland = size;
			setChanged();
		}
	}

	void setFinale(boolean value) {
		if (finale != value) {
			finale = value;
			sync();
		}
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, HotspotVentBlockEntity be) {
		if (!(level instanceof ServerLevel server) || be.owner == null) return;
		long t = level.getGameTime() + (pos.asLong() & 0x7FFF);
		if (t % 20 == 0) {
			GlobalPos gp = GlobalPos.of(level.dimension(), pos);
			VentRecords records = VentRecords.get(server.getServer());
			// keep the world record in step with the block (e.g. after the saved record was lost)
			if (records.get(gp) == null) records.put(gp, new VentRecords.Vent(be.owner, be.ownerName, be.home, be.lastIsland, Long.MIN_VALUE, java.util.List.of()));
			ServerPlayer p = server.getServer().getPlayerList().getPlayer(be.owner);
			if (p != null) {
				int stage = MagmaCore.get(p).stage().ordinal();
				String name = p.getName().getString();
				if (stage != be.ownerStage || !name.equals(be.ownerName)) {
					be.ownerStage = stage;
					be.ownerName = name;
					be.sync();
				}
			}
			be.setFinale(VentFinale.isBuildingAt(gp));
		}
		if (t % MEASURE_INTERVAL == 0) {
			IslandSurveyor.request(server, pos, be.owner);
		}
	}

	public void sync() {
		setChanged();
		if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel server) VentRegistry.onVentRemoved(server, pos);
	}

	@Override
	protected void saveAdditional(ValueOutput out) {
		super.saveAdditional(out);
		if (owner != null) out.store("Owner", UUIDUtil.CODEC, owner);
		out.putString("OwnerName", ownerName);
		out.putBoolean("Home", home);
		out.putInt("LastIsland", lastIsland);
		out.putInt("OwnerStage", ownerStage);
		out.putBoolean("Finale", finale);
	}

	@Override
	protected void loadAdditional(ValueInput in) {
		super.loadAdditional(in);
		owner = in.read("Owner", UUIDUtil.CODEC).orElse(null);
		ownerName = in.getStringOr("OwnerName", "");
		home = in.getBooleanOr("Home", false);
		lastIsland = in.getIntOr("LastIsland", 0);
		ownerStage = in.getIntOr("OwnerStage", 0);
		finale = in.getBooleanOr("Finale", false);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}
}
