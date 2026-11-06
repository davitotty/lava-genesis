package com.lavagenesis.form;

import com.lavagenesis.core.LavaIds;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;

/**
 * Per-stage passive powers (cumulative: a later stage keeps the earlier ones). Descriptions for the HUD/journal are
 * the translation keys {@code passive.lavagenesis.<stage>} in lang_parts/form.json.
 * <ul>
 *   <li>Ember: immune to fire and lava; melts snow and ice it touches, lights candles and campfires.</li>
 *   <li>Magma Droplet: lava dart - much faster lava swimming, leaps out of lava pools (client movement, mixin).</li>
 *   <li>Lava Flow: glowing footprints (client particles) and the warmth of fresh rock heals crust faster.</li>
 *   <li>Seamount: breathes underwater, swims like with depth strider, the sea barely cools it.</li>
 *   <li>Islet: lava-walking - water under your steps turns into cooling lava (sneak to sink instead).</li>
 *   <li>Shield Island: fire aura - whoever strikes you bursts into flame.</li>
 *   <li>Island Chain: steam jumps - jump out of the water in a burst of steam (client movement, mixin).</li>
 *   <li>Archipelago: lava sight - permanent night vision and clear sight in lava.</li>
 *   <li>Great Volcano: flight on an ash plume.</li>
 * </ul>
 */
public final class Passives {
	private Passives() {}

	static void tick(ServerPlayer player, FormRuntime rt, long time) {
		MagmaStage stage = MagmaCore.get(player).stage();
		ServerLevel level = player.level();
		if ((time + player.getId()) % 5 == 0) meltTouched(level, player);
		if (stage.atLeast(MagmaStage.ISLET)) lavaWalk(level, player, rt, stage, time);
		if (stage.atLeast(MagmaStage.ARCHIPELAGO) && (time + player.getId()) % 20 == 0) lavaSight(player);
		flight(level, player, rt, stage, time);
	}

	// --- Ember: everything it touches melts ---------------------------------------------------------------

	private static void meltTouched(ServerLevel level, ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(0.05);
		BlockPos min = BlockPos.containing(box.minX, box.minY - 0.1, box.minZ);
		BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
		int done = 0;
		for (BlockPos p : BlockPos.betweenClosed(min, max)) {
			if (done >= 3) break;
			BlockState s = level.getBlockState(p);
			BlockState result = null;
			if (s.is(Blocks.SNOW) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) {
				result = Blocks.AIR.defaultBlockState();
			} else if (s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE)) {
				boolean evaporates = level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, p);
				result = evaporates ? Blocks.AIR.defaultBlockState() : Blocks.WATER.defaultBlockState();
			} else if ((s.is(BlockTags.CANDLES) || s.is(BlockTags.CAMPFIRES) || s.is(BlockTags.CANDLE_CAKES))
				&& s.hasProperty(BlockStateProperties.LIT) && !s.getValue(BlockStateProperties.LIT)
				&& !(s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED))) {
				result = s.setValue(BlockStateProperties.LIT, true);
				level.playSound(null, p, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.4F, 1.4F);
				level.setBlock(p, result, Block.UPDATE_ALL);
				done++;
				continue;
			}
			if (result == null) continue;
			level.setBlock(p, result, Block.UPDATE_ALL);
			level.playSound(null, p, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3F, 1.6F);
			level.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.6, p.getZ() + 0.5, 3, 0.3, 0.2, 0.3, 0.02);
			done++;
		}
	}

	// --- Islet: lava-walking ------------------------------------------------------------------------------

	private static void lavaWalk(ServerLevel level, ServerPlayer player, FormRuntime rt, MagmaStage stage, long time) {
		if (player.isShiftKeyDown() || player.isPassenger() || player.getAbilities().flying || player.isUnderWater()) return;
		BlockPos feet = player.blockPosition();
		boolean moved = !feet.equals(rt.lastWalkPos);
		if (!moved && (time + player.getId()) % 10 != 0) return;
		rt.lastWalkPos = feet;

		int layer;
		if (player.onGround()) {
			layer = player.getOnPos().getY();
		} else if (player.isInWater() && isSurfaceWater(level, feet)) {
			layer = feet.getY();
		} else {
			return;
		}
		int radius = switch (stage) {
			case ISLET -> 2;
			case SHIELD_ISLAND, ISLAND_CHAIN -> 3;
			case ARCHIPELAGO -> 4;
			default -> 5;
		};
		Block rock = LavaIds.block(LavaIds.COOLING_LAVA);
		if (rock == Blocks.AIR) rock = Blocks.MAGMA_BLOCK;
		BlockState rockState = rock.defaultBlockState();
		AABB body = player.getBoundingBox();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int placed = 0;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius + 1) continue;
				p.set(feet.getX() + dx, layer, feet.getZ() + dz);
				if (!isSurfaceWater(level, p)) continue;
				if (new AABB(p).intersects(body)) continue;
				level.setBlock(p, rockState, Block.UPDATE_ALL);
				if (placed % 3 == 0) {
					level.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 1.05, p.getZ() + 0.5, 1, 0.3, 0.05, 0.3, 0.01);
				}
				placed++;
			}
		}
		if (placed > 0 && player.getRandom().nextInt(3) == 0) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS,
				0.25F, 1.2F + player.getRandom().nextFloat() * 0.5F);
		}
	}

	/** A water source block with open air above it: the sea surface. */
	private static boolean isSurfaceWater(ServerLevel level, BlockPos pos) {
		FluidState f = level.getFluidState(pos);
		if (!f.is(FluidTags.WATER) || !f.isSource()) return false;
		BlockState s = level.getBlockState(pos);
		if (!s.is(Blocks.WATER)) return false;
		return level.getBlockState(pos.above()).isAir();
	}

	// --- Shield Island: fire aura -------------------------------------------------------------------------

	static void onDamaged(ServerPlayer player, DamageSource source) {
		if (!FormRules.isMolten(player)) return;
		if (!MagmaCore.get(player).stage().atLeast(MagmaStage.SHIELD_ISLAND)) return;
		if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == player) return;
		if (attacker.distanceToSqr(player) > 64.0) return;
		attacker.igniteForSeconds(5.0F);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.FLAME, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(),
			12, attacker.getBbWidth() * 0.5, attacker.getBbHeight() * 0.4, attacker.getBbWidth() * 0.5, 0.03);
		level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6F, 1.3F);
	}

	// --- Archipelago: lava sight --------------------------------------------------------------------------

	private static void lavaSight(ServerPlayer player) {
		MobEffectInstance cur = player.getEffect(MobEffects.NIGHT_VISION);
		if (cur == null || (!cur.isInfiniteDuration() && cur.getDuration() < 260)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
		}
	}

	// --- Great Volcano: flight on an ash plume -------------------------------------------------------------

	private static void flight(ServerLevel level, ServerPlayer player, FormRuntime rt, MagmaStage stage, long time) {
		Abilities ab = player.getAbilities();
		boolean volcano = stage == MagmaStage.GREAT_VOLCANO;
		if (player.isCreative() || player.isSpectator()) {
			rt.grantedFlight = false;
		} else if (volcano && !ab.mayfly) {
			ab.mayfly = true;
			rt.grantedFlight = true;
			player.onUpdateAbilities();
		} else if (!volcano && rt.grantedFlight) {
			ab.mayfly = false;
			ab.flying = false;
			rt.grantedFlight = false;
			player.onUpdateAbilities();
		}
		if (volcano && ab.flying && time % 2 == 0) {
			double w = player.getBbWidth();
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.2, player.getZ(), 2, w * 0.4, 0.2, w * 0.4, 0.01);
			level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 0.5, player.getZ(), 6, w, 0.6, w, 0.0);
			if (time % 8 == 0) {
				level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY(), player.getZ(), 1, w * 0.3, 0.1, w * 0.3, 0.0);
			}
		}
	}
}
