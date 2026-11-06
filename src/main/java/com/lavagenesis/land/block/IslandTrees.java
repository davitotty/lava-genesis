package com.lavagenesis.land.block;

import com.lavagenesis.land.LandBlocks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The two island trees, built block by block (small, so no worldgen feature needed):
 * the ohia lehua - a twisted grey trunk with a round crown of leaves full of red pompom blossoms - and the
 * coconut palm - a leaning ringed trunk topped by a star of drooping fronds.
 */
public final class IslandTrees {
	private IslandTrees() {}

	public static boolean growOhia(ServerLevel level, BlockPos base, RandomSource random) {
		Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
		BlockState log = LandBlocks.OHIA_LOG.defaultBlockState();
		int height = 3 + random.nextInt(3);
		for (int i = 0; i < height; i++) plan.put(base.above(i), log);
		BlockPos top = base.above(height - 1);

		List<BlockPos> crowns = new ArrayList<>();
		crowns.add(top);
		// one or two side branches (two blocks out, then up) give the crown its lopsided ohia look;
		// every log and leaf touches the next by a face, so vanilla leaf decay sees them as connected
		int branches = 1 + random.nextInt(2);
		Direction first = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		for (int b = 0; b < branches; b++) {
			Direction dir = b == 0 ? first : first.getOpposite();
			BlockState sideways = log.setValue(RotatedPillarBlock.AXIS, dir.getAxis());
			BlockPos branch = base.above(Math.max(1, height - 2 - random.nextInt(2))).relative(dir);
			plan.put(branch, sideways);
			plan.put(branch.relative(dir), sideways);
			BlockPos tip = branch.relative(dir).above();
			plan.put(tip, log);
			crowns.add(tip);
		}
		BlockState leaves = leaves(LandBlocks.OHIA_LEAVES);
		for (BlockPos c : crowns) {
			boolean main = c == top;
			int r = main ? 2 : 1;
			for (int dy = -1; dy <= 2; dy++) {
				int rr = dy == 2 ? 0 : dy == 1 ? Math.max(1, r - 1) : r;
				for (int dx = -rr; dx <= rr; dx++) {
					for (int dz = -rr; dz <= rr; dz++) {
						boolean corner = Math.abs(dx) == rr && Math.abs(dz) == rr && rr > 0;
						if (corner && (dy != 0 || random.nextInt(3) == 0)) continue;
						plan.putIfAbsent(c.offset(dx, dy, dz), leaves);
					}
				}
			}
		}
		return place(level, base, plan);
	}

	public static boolean growPalm(ServerLevel level, BlockPos base, RandomSource random) {
		Map<BlockPos, BlockState> plan = new LinkedHashMap<>();
		BlockState log = LandBlocks.PALM_LOG.defaultBlockState();
		int height = 5 + random.nextInt(3);
		Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
		BlockPos.MutableBlockPos p = base.mutable();
		for (int i = 0; i < height; i++) {
			plan.put(p.immutable(), log);
			if (i == height / 2 || i == height - 2) {
				// the trunk bends towards the sea: a sideways segment, then up again
				p.move(lean);
				plan.put(p.immutable(), log.setValue(RotatedPillarBlock.AXIS, lean.getAxis()));
			}
			p.move(Direction.UP);
		}
		BlockPos top = p.below().immutable();
		BlockState fronds = leaves(LandBlocks.PALM_FRONDS);
		BlockPos cap = top.above();
		plan.put(cap, fronds);
		for (Direction d : Direction.Plane.HORIZONTAL) {
			Direction side = d.getClockWise();
			// a tuft on top, then a long frond reaching out four blocks and drooping at the tip.
			// Each frond block touches the previous one by a face (leaf distance stays <= 6).
			plan.put(cap.relative(d), fronds);
			int[][] frond = {{1, 0, 0}, {2, 0, 0}, {3, 0, 0}, {3, -1, 0}, {4, -1, 0}, {4, -2, 0},
				// a shorter diagonal frond between this one and the next
				{1, 0, 1}, {2, 0, 1}, {2, 0, 2}, {2, -1, 2}, {3, -1, 2}};
			for (int[] o : frond) plan.put(top.relative(d, o[0]).relative(side, o[2]).above(o[1]), fronds);
		}
		return place(level, base, plan);
	}

	private static BlockState leaves(Block block) {
		BlockState s = block.defaultBlockState();
		return s.hasProperty(LeavesBlock.DISTANCE) ? s.setValue(LeavesBlock.DISTANCE, 1) : s;
	}

	private static boolean place(ServerLevel level, BlockPos base, Map<BlockPos, BlockState> plan) {
		for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
			BlockPos pos = e.getKey();
			if (pos.equals(base)) continue;
			if (pos.getY() >= level.getMaxY()) return false;
			BlockState there = level.getBlockState(pos);
			boolean isLog = e.getValue().getBlock() instanceof RotatedPillarBlock;
			boolean free = there.isAir() || there.canBeReplaced() || there.is(BlockTags.LEAVES);
			if (isLog && !free) return false; // trunk blocked: stay a sapling
		}
		for (Map.Entry<BlockPos, BlockState> e : plan.entrySet()) {
			BlockPos pos = e.getKey();
			BlockState there = level.getBlockState(pos);
			boolean isLog = e.getValue().getBlock() instanceof RotatedPillarBlock;
			if (!isLog && !(there.isAir() || there.canBeReplaced())) continue; // leaves just skip obstacles
			level.setBlock(pos, e.getValue(), Block.UPDATE_ALL);
			// let vanilla work out the real leaf distance to the nearest log
			if (!isLog) level.scheduleTick(pos, e.getValue().getBlock(), 1);
		}
		return true;
	}
}
