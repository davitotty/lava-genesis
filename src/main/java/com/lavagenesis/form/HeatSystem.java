package com.lavagenesis.form;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.LavaIds;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Heat ({@link MagmaData#heat()}, owned by the form module): the lava body's life.
 * Water, rain, snow and cold biomes cool it (fast for the small early stages, barely at all from Seamount on);
 * lava, molten rock, magma blocks, fire and the home vent heat it. Low heat crusts the body (slower, weaker digging),
 * at zero it solidifies and takes damage ("solidified" death).
 */
public final class HeatSystem {
	private HeatSystem() {}

	public static final float CRUSTING = 0.30F;
	public static final float HARD_CRUST = 0.15F;
	/** heat changes are written to MagmaData (and synced) only in steps of this size */
	private static final float SYNC_STEP = 0.01F;
	/** heat is simulated every this many ticks */
	static final int INTERVAL = 5;
	private static final float DT = INTERVAL / 20.0F;

	public static final ResourceKey<DamageType> SOLIDIFIED =
		ResourceKey.create(Registries.DAMAGE_TYPE, LavaGenesis.id("solidified"));

	private static final Identifier CRUST_SPEED_ID = LavaGenesis.id("form/crust_slowness");
	private static final Identifier CRUST_MINING_ID = LavaGenesis.id("form/crust_mining");

	static void tick(ServerPlayer player, FormRuntime rt, long time) {
		MagmaData data = MagmaCore.get(player);
		if (rt.heat < 0.0F) rt.heat = data.heat();
		if ((time + player.getId()) % INTERVAL != 0) return;

		ServerLevel level = player.level();
		MagmaStage stage = data.stage();
		FormStats stats = FormStats.of(stage);
		BlockPos feet = player.blockPosition();
		BlockState below = level.getBlockState(player.getOnPos());
		BlockState at = level.getBlockState(feet);

		if ((time + player.getId()) % 20 < INTERVAL) rt.warmthCache = scanWarmth(level, feet);

		float gain = 0.0F;
		float loss = 0.0F;
		boolean hot = false;
		Block molten = LavaIds.block(LavaIds.MOLTEN_ROCK);
		Block cooling = LavaIds.block(LavaIds.COOLING_LAVA);

		if (player.isInLava()) { gain += 0.30F; hot = true; }
		if (molten != Blocks.AIR && (at.is(molten) || below.is(molten))) { gain += 0.25F; hot = true; }
		if (below.is(Blocks.MAGMA_BLOCK)) { gain += 0.12F; hot = true; }
		if (cooling != Blocks.AIR && (below.is(cooling) || at.is(cooling))) { gain += 0.05F; hot = true; }
		if (rt.warmthCache > 0) { gain += rt.warmthCache == 2 ? 0.10F : 0.05F; hot = true; }
		if (nearHomeVent(player, data, 8.0)) { gain += 0.15F; hot = true; }

		boolean inWater = player.isInWater();
		if (inWater) loss += stats.waterDrain() * (player.isUnderWater() ? 1.0F : 0.75F);
		else if (level.isRainingAt(BlockPos.containing(player.getEyePosition()))) loss += stats.rainDrain();
		if (player.isInPowderSnow || at.is(Blocks.POWDER_SNOW)) loss += stats.waterDrain() * 0.6F + 0.02F;
		if (isCold(level, feet)) loss += stats.coldDrain();

		float heat = rt.heat;
		heat += (gain - loss) * DT;
		if (!hot && loss <= 0.0F && heat < stats.ambientHeat()) {
			heat = Math.min(stats.ambientHeat(), heat + 0.012F * DT);
		}
		final float next = Math.clamp(heat, 0.0F, 1.0F);
		rt.heat = next;

		// sync only meaningful steps (and the exact ends)
		float stored = data.heat();
		if (Math.abs(stored - next) >= SYNC_STEP || (next == 0.0F && stored != 0.0F) || (next == 1.0F && stored != 1.0F)) {
			MagmaCore.update(player, d -> d.withHeat(next));
		}

		if (inWater || loss > 0.0F) steam(player, rt, inWater, loss);
		applyCrust(player, rt, next);
	}

	/** Called when lava is absorbed or another hot event happens. */
	static void addHeat(ServerPlayer player, float amount) {
		FormRuntime rt = FormRuntime.of(player);
		if (rt.heat < 0.0F) rt.heat = MagmaCore.get(player).heat();
		rt.heat = Math.clamp(rt.heat + amount, 0.0F, 1.0F);
		float h = rt.heat;
		if (Math.abs(MagmaCore.get(player).heat() - h) >= SYNC_STEP || h == 1.0F) {
			MagmaCore.update(player, d -> d.withHeat(h));
		}
	}

	/** The precise server-side heat (MagmaData holds it rounded to sync steps). */
	public static float heat(ServerPlayer player) {
		FormRuntime rt = FormRuntime.of(player);
		return rt.heat < 0.0F ? MagmaCore.get(player).heat() : rt.heat;
	}

	private static boolean nearHomeVent(ServerPlayer player, MagmaData data, double radius) {
		if (data.homeVent().isEmpty()) return false;
		GlobalPos vent = data.homeVent().get();
		if (!vent.dimension().equals(player.level().dimension())) return false;
		return vent.pos().distToCenterSqr(player.position()) <= radius * radius;
	}

	private static boolean isCold(ServerLevel level, BlockPos pos) {
		Holder<Biome> biome = level.getBiome(pos);
		return biome.value().getBaseTemperature() < 0.15F || biome.value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	/** 0 = nothing, 1 = some fire/lava within 2 blocks, 2 = a lot. 5x5x5 scan, once a second. */
	private static int scanWarmth(ServerLevel level, BlockPos center) {
		int count = 0;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
			BlockState s = level.getBlockState(p);
			if (s.is(BlockTags.FIRE) || s.getFluidState().is(FluidTags.LAVA) || s.is(Blocks.MAGMA_BLOCK)
				|| (s.is(BlockTags.CAMPFIRES) && s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT))) {
				count++;
				if (count >= 4) return 2;
			}
		}
		return count > 0 ? 1 : 0;
	}

	private static void steam(ServerPlayer player, FormRuntime rt, boolean inWater, float loss) {
		ServerLevel level = player.level();
		float w = player.getBbWidth();
		double h = player.getBbHeight();
		if (rt.steamSoundCooldown > 0) rt.steamSoundCooldown -= INTERVAL;
		boolean fierce = loss >= 0.04F;
		if (inWater) {
			int n = fierce ? 4 : 1;
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + h * 0.9, player.getZ(), n,
				w * 0.5, h * 0.2, w * 0.5, 0.02);
			level.sendParticles(ParticleTypes.BUBBLE, player.getX(), player.getY() + h * 0.5, player.getZ(), n + 1,
				w * 0.5, h * 0.4, w * 0.5, 0.05);
			if (rt.steamSoundCooldown <= 0 && (fierce || player.getRandom().nextInt(4) == 0)) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_EXTINGUISH,
					SoundSource.PLAYERS, fierce ? 0.5F : 0.2F, 1.6F + player.getRandom().nextFloat() * 0.4F);
				rt.steamSoundCooldown = fierce ? 20 : 60;
			}
		} else if (player.getRandom().nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.WHITE_SMOKE, player.getX(), player.getY() + h, player.getZ(), 1,
				w * 0.4, 0.05, w * 0.4, 0.01);
			if (rt.steamSoundCooldown <= 0 && player.getRandom().nextInt(6) == 0) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH,
					SoundSource.PLAYERS, 0.15F, 1.8F);
				rt.steamSoundCooldown = 80;
			}
		}
	}

	private static void applyCrust(ServerPlayer player, FormRuntime rt, float heat) {
		int crust = FormRules.crustFor(heat);
		float slow = crust == 0 ? 0.0F : crust == 1 ? -0.15F : crust == 2 ? -0.35F : -0.55F;
		float dig = crust == 0 ? 0.0F : crust == 1 ? -0.3F : crust == 2 ? -0.6F : -0.85F;
		setTransient(player.getAttribute(Attributes.MOVEMENT_SPEED), CRUST_SPEED_ID, slow);
		setTransient(player.getAttribute(Attributes.BLOCK_BREAK_SPEED), CRUST_MINING_ID, dig);

		if (crust != rt.lastHeatWarning) {
			if (crust > rt.lastHeatWarning && crust >= 1 && rt.lastHeatWarning >= 0) {
				player.sendOverlayMessage(Component.translatable(crust == 3
					? "form.lavagenesis.heat.solid" : "form.lavagenesis.heat.crusting"));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BASALT_PLACE,
					SoundSource.PLAYERS, 0.8F, 0.6F);
			}
			rt.lastHeatWarning = crust;
		}

		if (crust == 3 && !player.isCreative()) {
			rt.solidifyTimer += INTERVAL;
			if (rt.solidifyTimer >= 30) {
				rt.solidifyTimer = 0;
				player.hurtServer(player.level(), solidified(player.level()), 1.0F);
			}
		} else {
			rt.solidifyTimer = 0;
		}
	}

	private static void setTransient(AttributeInstance inst, Identifier id, float amount) {
		if (inst == null) return;
		AttributeModifier old = inst.getModifier(id);
		if (amount == 0.0F) {
			if (old != null) inst.removeModifier(id);
			return;
		}
		if (old != null && old.amount() == amount) return;
		inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	public static DamageSource solidified(ServerLevel level) {
		return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(SOLIDIFIED)
			.map(DamageSource::new)
			.orElseGet(() -> level.damageSources().freeze());
	}
}
