package com.lavagenesis.land.birth;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A big block edit worked out in one go and then applied a slice per tick. Later puts at the same position
 * replace earlier ones. Applied without neighbour or shape updates (rock first, then fluids), so the lava and
 * water placed by the plan stay where they are put.
 */
final class BlockPlan {
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private final Long2ObjectLinkedOpenHashMap<BlockState> blocks = new Long2ObjectLinkedOpenHashMap<>();
	private long[] keys;
	private BlockState[] states;
	private int next;

	void put(int x, int y, int z, BlockState state) {
		blocks.put(BlockPos.asLong(x, y, z), state);
	}

	@Nullable BlockState get(int x, int y, int z) {
		return blocks.get(BlockPos.asLong(x, y, z));
	}

	int size() {
		return blocks.size();
	}

	/** Sets up to {@code budget} blocks; returns true when everything has been placed. */
	boolean apply(ServerLevel level, int budget) {
		if (keys == null) {
			// solids first, fluids last: lava placed next to not-yet-replaced sea water would instantly
			// turn into obsidian, so every fluid goes in only once all the rock around it exists
			keys = new long[blocks.size()];
			states = new BlockState[blocks.size()];
			int i = 0;
			for (boolean fluids : new boolean[] {false, true}) {
				for (var e : blocks.long2ObjectEntrySet()) {
					if (e.getValue().getFluidState().isEmpty() == fluids) continue;
					keys[i] = e.getLongKey();
					states[i++] = e.getValue();
				}
			}
		}
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int end = Math.min(keys.length, next + budget);
		for (; next < end; next++) {
			m.set(keys[next]);
			if (level.isOutsideBuildHeight(m)) continue;
			level.setBlock(m, states[next], FLAGS);
		}
		return next >= keys.length;
	}
}
