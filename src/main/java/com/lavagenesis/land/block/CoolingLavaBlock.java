package com.lavagenesis.land.block;

import com.lavagenesis.land.LandBlocks;
import com.lavagenesis.land.LandItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Fresh lava rock: glowing crust over molten rock that cools one step per scheduled tick (age 0 = just erupted,
 * age 3 = nearly cold) and then turns into land. Where it cools decides what it becomes:
 * <ul>
 *   <li>in open air above sea level: fresh basalt (sometimes a fumarole), and now and then it flings out a Pele's Tear;</li>
 *   <li>touching water at the shoreline: black sand or glassy basalt;</li>
 *   <li>under water: pillow lava, glassy basalt and, in deep water, a little olivine ore.</li>
 * </ul>
 * Water next to it makes it cool several times faster, with hissing steam. While hot it burns what stands on it.
 */
public class CoolingLavaBlock extends Block {
	public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
	private static final int[] LIGHT = {15, 12, 9, 5};

	public CoolingLavaBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(AGE, 0));
	}

	public static int lightFor(BlockState state) {
		return LIGHT[state.getValue(AGE)];
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AGE);
	}

	/** Ticks until the next cooling step: ~15 s per step in air, about a second with water next to it. */
	public static int coolingDelay(LevelReader level, BlockPos pos, RandomSource random) {
		if (Heat.touchesWater(level, pos)) return 12 + random.nextInt(18);
		int delay = 140 + random.nextInt(160);
		if (level instanceof Level l && l.isRainingAt(pos.above())) delay /= 2;
		return delay;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
		if (!old.is(this) && !level.isClientSide()) level.scheduleTick(pos, this, coolingDelay(level, pos, level.getRandom()));
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		cool(state, level, pos, random);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	/** Safety net for cooling lava placed without a scheduled tick (structures, /setblock with strict flags...). */
	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.getBlockTicks().hasScheduledTick(pos, this)) level.scheduleTick(pos, this, coolingDelay(level, pos, random));
	}

	private void cool(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean wet = Heat.touchesWater(level, pos);
		if (wet) steam(level, pos, random);
		int age = state.getValue(AGE);
		if (age < 3) {
			level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_ALL);
			level.scheduleTick(pos, this, coolingDelay(level, pos, random));
		} else {
			solidify(level, pos, random, wet);
		}
	}

	private static void steam(ServerLevel level, BlockPos pos, RandomSource random) {
		boolean submerged = level.getFluidState(pos.above()).is(FluidTags.WATER);
		double x = pos.getX() + 0.5, y = pos.getY() + 1.05, z = pos.getZ() + 0.5;
		if (submerged) {
			level.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 6, 0.3, 0.1, 0.3, 0.05);
		} else {
			level.sendParticles(ParticleTypes.CLOUD, x, y, z, 3, 0.3, 0.05, 0.3, 0.02);
			level.sendParticles(ParticleTypes.WHITE_SMOKE, x, y, z, 4, 0.35, 0.1, 0.35, 0.03);
		}
		if (random.nextInt(3) == 0) {
			level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.35F, 1.4F + random.nextFloat() * 0.5F);
		}
	}

	/** What this lava becomes when it is fully cold, based on where it cooled. */
	private static void solidify(ServerLevel level, BlockPos pos, RandomSource random, boolean wet) {
		int sea = level.getSeaLevel();
		int y = pos.getY();
		float r = random.nextFloat();
		BlockState result;
		boolean submerged = level.getFluidState(pos.above()).is(FluidTags.WATER);
		boolean shoreline = y >= sea - 2 && y <= sea + 1;
		if (wet && shoreline) {
			// waves hitting lava at the coast: black sand beaches
			result = r < 0.55F ? LandBlocks.BLACK_SAND.defaultBlockState()
				: r < 0.67F ? LandBlocks.GLASSY_BASALT.defaultBlockState()
				: LandBlocks.FRESH_BASALT.defaultBlockState();
		} else if (submerged) {
			boolean deep = Heat.waterAbove(level, pos, 10) >= 8 || y < sea - 12;
			if (deep) {
				result = r < 0.07F ? LandBlocks.OLIVINE_ORE.defaultBlockState()
					: r < 0.62F ? LandBlocks.PILLOW_LAVA.defaultBlockState()
					: r < 0.70F ? LandBlocks.GLASSY_BASALT.defaultBlockState()
					: LandBlocks.FRESH_BASALT.defaultBlockState();
			} else {
				result = r < 0.40F ? LandBlocks.PILLOW_LAVA.defaultBlockState()
					: r < 0.55F ? LandBlocks.GLASSY_BASALT.defaultBlockState()
					: LandBlocks.FRESH_BASALT.defaultBlockState();
			}
		} else if (wet) {
			result = r < 0.12F ? LandBlocks.GLASSY_BASALT.defaultBlockState() : LandBlocks.FRESH_BASALT.defaultBlockState();
		} else {
			boolean openAir = y >= sea && level.canSeeSky(pos.above());
			result = openAir && r < 0.015F ? LandBlocks.FUMAROLE.defaultBlockState() : LandBlocks.FRESH_BASALT.defaultBlockState();
			if (openAir && random.nextFloat() < 0.05F) {
				// a drop of molten glass thrown up by escaping gas: Pele's Tear
				Block.popResourceFromFace(level, pos, Direction.UP, new ItemStack(LandItems.PELES_TEAR));
				level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.3F + random.nextFloat() * 0.4F);
				level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.0);
			}
		}
		level.setBlock(pos, result, Block.UPDATE_ALL);
		if (wet) level.levelEvent(1501, pos, 0); // lava fizz: hiss + smoke
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		int age = state.getValue(AGE);
		if (age <= 1) Heat.hotFloor(level, entity, age == 0 ? 2.0F : 1.0F, age == 0 ? 3.0F : 0.0F);
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int age = state.getValue(AGE);
		BlockPos above = pos.above();
		BlockState up = level.getBlockState(above);
		boolean airAbove = up.isAir();
		double x = pos.getX() + random.nextDouble(), y = pos.getY() + 1.02, z = pos.getZ() + random.nextDouble();
		if (airAbove && age <= 1 && random.nextInt(age == 0 ? 3 : 6) == 0) {
			level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
			if (random.nextInt(4) == 0) {
				level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.3F, 0.9F + random.nextFloat() * 0.2F, false);
			}
		}
		if (airAbove && age <= 2 && random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.03, 0.0);
		}
		if (Heat.touchesWater(level, pos)) {
			if (up.getFluidState().is(FluidTags.WATER)) {
				level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 0.0, 0.05, 0.0);
				level.addParticle(ParticleTypes.BUBBLE, x, y, z, 0.0, 0.08, 0.0);
			} else {
				level.addParticle(ParticleTypes.WHITE_SMOKE, x, y, z, 0.0, 0.06, 0.0);
				level.addParticle(ParticleTypes.CLOUD, x, y, z, 0.0, 0.04, 0.0);
			}
		}
	}
}
