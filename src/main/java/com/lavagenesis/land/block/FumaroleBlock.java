package com.lavagenesis.land.block;

import com.lavagenesis.land.LandBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A volcanic gas vent: puffs steam and yellow sulfur smoke (bubbles under water) and slowly grows a sulfur crystal
 * cluster on top of itself (small, medium, large). Harvest the cluster for Sulfur Crystals and it grows again.
 * Fumaroles can be mined and moved, so a player can farm sulfur on their own island.
 */
public class FumaroleBlock extends Block {
	private static final DustParticleOptions SULFUR_SMOKE = new DustParticleOptions(0xE8D040, 1.4F);

	public FumaroleBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) != 0) return;
		BlockPos up = pos.above();
		BlockState above = level.getBlockState(up);
		if (above.is(LandBlocks.SULFUR_CLUSTER)) {
			int age = above.getValue(SulfurClusterBlock.AGE);
			if (age < SulfurClusterBlock.MAX_AGE) level.setBlock(up, above.setValue(SulfurClusterBlock.AGE, age + 1), Block.UPDATE_ALL);
		} else if (above.isAir() || above.is(Blocks.WATER) && above.getFluidState().isSource()) {
			level.setBlock(up, LandBlocks.SULFUR_CLUSTER.defaultBlockState()
				.setValue(SulfurClusterBlock.WATERLOGGED, above.is(Blocks.WATER)), Block.UPDATE_ALL);
		}
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		Heat.hotFloor(level, entity, 1.0F, 0.0F);
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		BlockState above = level.getBlockState(pos.above());
		double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
		if (above.getFluidState().is(FluidTags.WATER)) {
			for (int i = 0; i < 3; i++) {
				level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x + (random.nextDouble() - 0.5) * 0.4, y + 0.1,
					z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.15, 0.0);
			}
			if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SULFUR_BUBBLES, x, y + 0.2, z, 0.0, 0.1, 0.0);
			return;
		}
		if (above.isSolidRender()) return;
		level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x + (random.nextDouble() - 0.5) * 0.3,
			y + 0.2, z + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.04 + random.nextDouble() * 0.03, 0.0);
		for (int i = 0; i < 2; i++) {
			level.addParticle(SULFUR_SMOKE, x + (random.nextDouble() - 0.5) * 0.6, y + 0.3 + random.nextDouble() * 0.5,
				z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.05, 0.0);
		}
		if (random.nextInt(3) == 0) {
			// a puff: a burst of steam with a hiss
			for (int i = 0; i < 6; i++) {
				level.addParticle(ParticleTypes.WHITE_SMOKE, x + (random.nextDouble() - 0.5) * 0.3, y + 0.1,
					z + (random.nextDouble() - 0.5) * 0.3, (random.nextDouble() - 0.5) * 0.05, 0.15 + random.nextDouble() * 0.1,
					(random.nextDouble() - 0.5) * 0.05);
			}
			level.playLocalSound(x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.15F, 0.6F + random.nextFloat() * 0.3F, false);
		}
	}
}
