package com.lavagenesis.land.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Rock that stays liquid-hot forever: the glowing walls of a magma chamber, vent throats and crater floors.
 * Solid to walk on but burns like lava. Never cools and never flows. Breaking it yields Magma Shards.
 */
public class MoltenRockBlock extends Block {
	public MoltenRockBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (level instanceof ServerLevel && entity instanceof LivingEntity && !entity.isSteppingCarefully()) {
			entity.lavaIgnite();
			entity.lavaHurt();
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		BlockState up = level.getBlockState(pos.above());
		double x = pos.getX() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
		if (up.isAir()) {
			if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.LAVA, x, pos.getY() + 1.0, z, 0.0, 0.0, 0.0);
			if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, x, pos.getY() + 1.0, z, 0.0, 0.02, 0.0);
			if (random.nextInt(8) == 0) {
				level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, SoundEvents.LAVA_AMBIENT,
					SoundSource.BLOCKS, 0.25F + random.nextFloat() * 0.2F, 0.9F + random.nextFloat() * 0.15F, false);
			}
		} else if (up.getFluidState().is(FluidTags.WATER)) {
			// sea water boiling over molten rock
			level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x, pos.getY() + 1.05, z, 0.0, 0.1, 0.0);
			level.addParticle(ParticleTypes.BUBBLE, x, pos.getY() + 1.05, z, 0.0, 0.1, 0.0);
		}
		if (random.nextInt(5) == 0 && level.getBlockState(pos.below()).isAir()) {
			level.addParticle(ParticleTypes.DRIPPING_LAVA, x, pos.getY() - 0.05, z, 0.0, 0.0, 0.0);
		}
	}
}
