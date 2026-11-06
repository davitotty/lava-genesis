package com.lavagenesis.eruption;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.LavaIds;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import com.lavagenesis.core.VentAccess;
import com.lavagenesis.eruption.entity.LavaBomb;
import com.lavagenesis.eruption.fx.Effects;
import com.lavagenesis.eruption.fx.Fx;
import com.lavagenesis.eruption.land.BlockPlacer;
import com.lavagenesis.eruption.land.FlowSim;
import com.lavagenesis.eruption.land.Palette;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Server side of the active powers: validation (stage, pressure, cooldown, range) and what each one does. */
public final class Powers {
	private Powers() {}

	/** Blocks Flare can melt (data/lavagenesis/tags/block/flare_meltable.json). */
	public static final TagKey<Block> FLARE_MELTABLE = TagKey.create(Registries.BLOCK, LavaGenesis.id("flare_meltable"));

	/** Hotspot Leap: new island vents must be at least this far from the player's other vents. */
	public static final int MIN_VENT_SPACING = 48;

	private static MutableComponent msg(String key, Object... args) {
		return Component.translatable("msg.lavagenesis." + key, args);
	}

	// ------------------------------------------------------------------ entry points (server thread)

	static void select(ServerPlayer p, int ordinal) {
		Power power = Power.byId(ordinal);
		if (power == null) return;
		MagmaStage stage = MagmaCore.get(p).stage();
		if (!power.unlockedAt(stage)) return;
		EruptionState.set(p, EruptionState.get(p).withSelected(power));
		p.sendOverlayMessage(msg("power.selected", power.displayName().copy().withColor(power.color), power.cost));
	}

	static void use(ServerPlayer p, int ordinal) {
		Power power = Power.byId(ordinal);
		if (power == null || !p.isAlive() || p.isSpectator()) return;
		MagmaData data = MagmaCore.get(p);
		if (!power.unlockedAt(data.stage())) {
			p.sendOverlayMessage(msg("power.locked", power.displayName(), power.stage.displayName()).withStyle(ChatFormatting.GRAY));
			return;
		}
		EruptionState st = EruptionState.get(p);
		long now = p.level().getGameTime();
		long ready = st.readyAt(power);
		if (now < ready && ready - now <= power.cooldownTicks) { // (a clock that went back never locks a power)
			// repeat-fire keys arrive a little early all the time; stay quiet for those
			if (!power.repeatable && ready - now > 10) {
				p.sendOverlayMessage(msg("power.cooldown", power.displayName(), String.format("%.1f", (ready - now) / 20.0))
					.withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (!p.hasInfiniteMaterials() && data.pressure() < power.cost) {
			p.sendOverlayMessage(msg("power.pressure", power.displayName(), power.cost, data.pressure()).withStyle(ChatFormatting.RED));
			return;
		}
		boolean ok;
		try {
			ok = run(p, power);
		} catch (RuntimeException e) {
			LavaGenesis.LOGGER.error("Power {} failed", power.id, e);
			ok = false;
		}
		if (ok) {
			Pressure.spend(p, power.cost);
			EruptionState.set(p, EruptionState.get(p).withReadyAt(power, now + power.cooldownTicks));
			EruptionEvents.POWER_USED.invoker().onPowerUsed(p, power);
		} else if (power.repeatable) {
			// a failed Flare/Spit still waits a moment so a held key does not spam
			EruptionState.set(p, EruptionState.get(p).withReadyAt(power, now + 4));
		}
	}

	private static boolean run(ServerPlayer p, Power power) {
		return switch (power) {
			case FLARE -> flare(p);
			case LAVA_SPIT -> lavaSpit(p);
			case FLOW_SURGE -> flowSurge(p);
			case PILLOW_MOUND -> pillowMound(p);
			case ERUPTION -> eruption(p);
			case SHIELD_FLOW -> shieldFlow(p);
			case HOTSPOT_LEAP -> hotspotLeap(p);
			case BOMB_RAIN -> bombRain(p);
			case GREAT_ERUPTION -> greatEruption(p);
		};
	}

	// ------------------------------------------------------------------ helpers

	private static BlockHitResult ray(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1.0F).scale(range));
		return p.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
	}

	/** The free cell in front of the face the player looks at, or null if nothing is in range. */
	private static @Nullable BlockPos target(ServerPlayer p, double range) {
		BlockHitResult hit = ray(p, range);
		if (hit.getType() != HitResult.Type.BLOCK) {
			p.sendOverlayMessage(msg("power.no_target", (int) range).withStyle(ChatFormatting.GRAY));
			return null;
		}
		return hit.getBlockPos().relative(hit.getDirection());
	}

	private static float scale(ServerPlayer p) {
		return MagmaCore.get(p).stage().scale;
	}

	// ------------------------------------------------------------------ Flare

	private static boolean flare(ServerPlayer p) {
		ServerLevel level = p.level();
		double range = 3.0 + 3.5 * scale(p);
		BlockHitResult hit = ray(p, range);
		Vec3 eye = p.getEyePosition();
		if (hit.getType() != HitResult.Type.BLOCK) {
			Vec3 end = eye.add(p.getViewVector(1.0F).scale(range * 0.6));
			Fx.beam(level, eye, end, 4);
			return false;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		Vec3 c = Vec3.atCenterOf(pos);
		if (!state.is(FLARE_MELTABLE) || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0 || Palette.isVent(state)) {
			level.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.01);
			Fx.beam(level, eye, hit.getLocation(), 4);
			p.sendOverlayMessage(msg("flare.too_hard").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (state.is(LavaIds.VOLCANIC_LAND) && pos.getY() >= level.getSeaLevel() - 2) {
			p.sendOverlayMessage(msg("flare.own_land").withStyle(ChatFormatting.GRAY));
			return false;
		}
		boolean glass = state.is(BlockTags.SAND) && !state.is(LavaIds.block(LavaIds.BLACK_SAND));
		level.setBlock(pos, glass ? Blocks.GLASS.defaultBlockState() : Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
		Fx.beam(level, eye, c, (int) Math.max(3, eye.distanceTo(c) * 2));
		RandomSource r = level.getRandom();
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.4, c.z, 4, 0.3, 0.2, 0.3, 0.0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 10, 0.35, 0.35, 0.35, 0.02);
		if (level.getFluidState(pos.above()).is(FluidTags.WATER)) {
			level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.7, c.z, 4, 0.3, 0.3, 0.3, 0.03);
		}
		level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.9F, 0.8F + r.nextFloat() * 0.4F);
		level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.35F, 1.4F + r.nextFloat() * 0.3F);
		MagmaCore.addMass(p, 1, "melt");
		return true;
	}

	// ------------------------------------------------------------------ Lava Spit

	private static boolean lavaSpit(ServerPlayer p) {
		ServerLevel level = p.level();
		Vec3 look = p.getViewVector(1.0F);
		Vec3 from = p.getEyePosition().add(look.scale(0.4)).subtract(0, 0.1, 0);
		float s = scale(p);
		LavaBomb.launch(level, from, look.scale(1.15 + s * 0.3).add(p.getDeltaMovement().multiply(1, 0, 1)), p,
			Items.FIRE_CHARGE, 4, 4.0F + s * 2.0F, 1.6F);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6F, 1.3F + level.getRandom().nextFloat() * 0.3F);
		level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 5, 0.1, 0.1, 0.1, 0.03);
		return true;
	}

	// ------------------------------------------------------------------ Flow Surge

	private static boolean flowSurge(ServerPlayer p) {
		Vec3 look = p.getViewVector(1.0F);
		Vec3 dir = p.isInWater()
			? new Vec3(look.x, Mth.clamp(look.y, -0.4, 0.4), look.z).normalize()
			: new Vec3(look.x, 0, look.z).normalize();
		if (dir.lengthSqr() < 1.0E-4) return false;
		Motion.startSurge(p, dir, 12);
		ServerLevel level = p.level();
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}

	// ------------------------------------------------------------------ Pillow Mound

	private static boolean pillowMound(ServerPlayer p) {
		BlockPos at = target(p, 24);
		if (at == null) return false;
		ServerLevel level = p.level();
		FlowSim.Params params = FlowSim.Params.of(110, 8).slopes(4.0, 0.5).cap(level.getSeaLevel() + 2)
			.noise(1.4).style(FlowSim.Style.PILLOW).shore(false);
		FlowSim.Result plan = FlowSim.run(level, at, params, level.getRandom().nextLong());
		if (plan.isEmpty()) {
			p.sendOverlayMessage(msg("power.no_room").withStyle(ChatFormatting.GRAY));
			return false;
		}
		BlockPlacer.submit(level, plan, p, 40, 12, null);
		Vec3 c = Vec3.atBottomCenterOf(at);
		boolean wet = level.getFluidState(at).is(FluidTags.WATER);
		level.sendParticles(wet ? ParticleTypes.BUBBLE_COLUMN_UP : ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, 20, 1.5, 0.5, 1.5, 0.3);
		if (wet) Fx.far(level, ParticleTypes.CLOUD, c.x, Math.max(c.y, level.getSeaLevel()) + 0.5, c.z, 10, 2, 0.5, 2, 0.04);
		level.playSound(null, at, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 1.5F, 0.6F);
		level.playSound(null, at, SoundEvents.BASALT_PLACE, SoundSource.BLOCKS, 1.5F, 0.5F);
		Fx.shake(level, c, 20, 0.5F, 12);
		return true;
	}

	// ------------------------------------------------------------------ Eruption

	private static boolean eruption(ServerPlayer p) {
		BlockPos at = target(p, 40);
		if (at == null) return false;
		ServerLevel level = p.level();
		FlowSim.Params params = FlowSim.Params.of(220, 14).slopes(2.5, 0.45).noise(1.6).crater(0);
		FlowSim.Result plan = FlowSim.run(level, at, params, level.getRandom().nextLong());
		if (plan.isEmpty()) {
			p.sendOverlayMessage(msg("power.no_room").withStyle(ChatFormatting.GRAY));
			return false;
		}
		BlockPlacer.submit(level, plan, p, 90, 24, null);
		Vec3 crater = new Vec3(plan.originX() + 0.5, plan.topOr(at.getY() - 1) + 1.2, plan.originZ() + 0.5);
		Fx.start(level, Effects.fountain(p, crater, 60, 7));
		return true;
	}

	// ------------------------------------------------------------------ Shield Flow

	private static boolean shieldFlow(ServerPlayer p) {
		BlockPos at = target(p, 32);
		if (at == null) return false;
		ServerLevel level = p.level();
		int sea = level.getSeaLevel();
		int cap = Math.max(sea + 1, at.getY() + 2);
		FlowSim.Params params = FlowSim.Params.of(700, 20).slopes(2.5, 0.07).cap(cap).noise(2.2);
		FlowSim.Result plan = FlowSim.run(level, at, params, level.getRandom().nextLong());
		if (plan.isEmpty()) {
			p.sendOverlayMessage(msg("power.no_room").withStyle(ChatFormatting.GRAY));
			return false;
		}
		BlockPlacer.submit(level, plan, p, 160, 40, null);
		Vec3 c = Vec3.atBottomCenterOf(at);
		level.playSound(null, at, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 2.0F, 0.5F);
		level.playSound(null, at, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 2.0F, 0.5F);
		Fx.far(level, ParticleTypes.LARGE_SMOKE, c.x, c.y + 1, c.z, 20, 4, 1, 4, 0.03);
		Fx.far(level, ParticleTypes.CLOUD, c.x, Math.max(c.y, sea) + 0.5, c.z, 16, 6, 0.5, 6, 0.04);
		Fx.shake(level, c, 28, 0.7F, 20);
		return true;
	}

	// ------------------------------------------------------------------ Hotspot Leap

	private static boolean hotspotLeap(ServerPlayer p) {
		ServerLevel level = p.level();
		if (Motion.leaping(p)) return false;
		Vec3 look = p.getViewVector(1.0F);
		Vec3 dir = new Vec3(look.x, 0, look.z);
		if (dir.lengthSqr() < 1.0E-4) {
			p.sendOverlayMessage(msg("leap.look").withStyle(ChatFormatting.GRAY));
			return false;
		}
		dir = dir.normalize();
		// look up to leap further: level = 40 blocks, 45 degrees up = 80
		int wanted = 40 + (int) (40 * Mth.clamp(-p.getXRot() / 45.0F, 0.0F, 1.0F));
		List<Integer> tries = new ArrayList<>();
		for (int d = wanted; d >= 40; d -= 4) tries.add(d);
		for (int d = wanted + 4; d <= 80; d += 4) tries.add(d);

		List<GlobalPos> vents = new ArrayList<>(EruptionState.get(p).seededVents());
		MagmaCore.get(p).homeVent().ifPresent(vents::add);

		String fail = "leap.no_ocean";
		int closest = Integer.MAX_VALUE;
		BlockPos found = null;
		for (int d : tries) {
			BlockPos c = BlockPos.containing(p.getX() + dir.x * d, level.getSeaLevel() - 1, p.getZ() + dir.z * d);
			if (!level.isLoaded(c)) { fail = "leap.unloaded"; continue; }
			if (!openOcean(level, c)) continue;
			int near = nearestVent(level, vents, c);
			if (near < MIN_VENT_SPACING) { fail = "leap.too_close"; closest = Math.min(closest, near); continue; }
			found = c;
			break;
		}
		if (found == null) {
			p.sendOverlayMessage(fail.equals("leap.too_close")
				? msg(fail, closest, MIN_VENT_SPACING).withStyle(ChatFormatting.RED)
				: msg(fail).withStyle(ChatFormatting.RED));
			return false;
		}

		int sea = level.getSeaLevel();
		int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, found.getX(), found.getZ());
		BlockPos origin = new BlockPos(found.getX(), floor, found.getZ());
		FlowSim.Params params = FlowSim.Params.of(14000, 13).slopes(5.0, 0.42).stopAt(sea + 3).noise(1.4);
		FlowSim.Result plan = FlowSim.run(level, origin, params, level.getRandom().nextLong());
		if (plan.isEmpty() || plan.originTop() < sea) {
			p.sendOverlayMessage(msg("leap.no_ocean").withStyle(ChatFormatting.RED));
			return false;
		}
		BlockPos ventPos = new BlockPos(plan.originX(), plan.originTop(), plan.originZ());
		UUID owner = p.getUUID();
		BlockPlacer.submit(level, plan, p, 150, 240, () -> seedVent(level, owner, ventPos));

		double dist = Math.sqrt(p.distanceToSqr(found.getX() + 0.5, p.getY(), found.getZ() + 0.5));
		Vec3 land = new Vec3(ventPos.getX() + 0.5 + dir.x * 2.5, ventPos.getY() + 2.5, ventPos.getZ() + 0.5 + dir.z * 2.5);
		Motion.startLeap(p, land, 10 + dist / 5.0, (int) (30 + dist / 2));
		// launch: a burst under the player and steam over the new island
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.LAVA, p.getX(), p.getY(), p.getZ(), 20, 0.8, 0.3, 0.8, 0.0);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.7F);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 2.0F, 0.5F);
		Fx.shake(level, p.position(), 24, 1.0F, 15);
		Vec3 crater = new Vec3(ventPos.getX() + 0.5, sea, ventPos.getZ() + 0.5);
		Fx.start(level, (lvl, age) -> {
			RandomSource r = lvl.getRandom();
			Fx.far(lvl, ParticleTypes.CLOUD, crater.x, crater.y + 0.5, crater.z, 12, 5, 0.6, 5, 0.06);
			Fx.far(lvl, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, crater.x, crater.y, crater.z, 2, 2, 0.3, 2, 0.02);
			lvl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, crater.x, crater.y - 2, crater.z, 12, 3, 1, 3, 0.3);
			if (age % 6 == 0) lvl.playSound(null, crater.x, crater.y, crater.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 3.0F, 0.5F + r.nextFloat() * 0.2F);
			if (age == 30) Fx.shake(lvl, crater, 40, 1.0F, 30);
			return age < 80;
		});
		return true;
	}

	/** Open ocean: water at the surface and at least 4 deep, here and in a ring 8 blocks around. */
	private static boolean openOcean(ServerLevel level, BlockPos c) {
		int sea = level.getSeaLevel();
		for (int i = -1; i < 8; i++) {
			int x = c.getX(), z = c.getZ();
			if (i >= 0) {
				double a = i * Math.PI / 4;
				x += (int) Math.round(Math.cos(a) * 8);
				z += (int) Math.round(Math.sin(a) * 8);
			}
			BlockPos s = new BlockPos(x, sea - 1, z);
			if (!level.isLoaded(s) || !level.getFluidState(s).is(FluidTags.WATER)) return false;
			if (level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) > sea) return false; // something sticks out
			if (level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) > sea - 4) return false;
		}
		return true;
	}

	private static int nearestVent(ServerLevel level, List<GlobalPos> vents, BlockPos c) {
		int best = Integer.MAX_VALUE;
		for (GlobalPos v : vents) {
			if (!v.dimension().equals(level.dimension())) continue;
			double dx = v.pos().getX() - c.getX(), dz = v.pos().getZ() - c.getZ();
			best = Math.min(best, (int) Math.sqrt(dx * dx + dz * dz));
		}
		return best;
	}

	private static void seedVent(ServerLevel level, UUID ownerId, BlockPos pos) {
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
		if (owner == null) {
			LavaGenesis.LOGGER.warn("Hotspot Leap island at {} finished while its owner was offline; no vent placed", pos);
			return;
		}
		boolean ok = VentAccess.placeVent(owner, level, pos, false);
		if (!ok) {
			owner.sendSystemMessage(msg("leap.vent_failed").withStyle(ChatFormatting.RED));
			return;
		}
		EruptionState.set(owner, EruptionState.get(owner).withSeededVent(GlobalPos.of(level.dimension(), pos)));
		Vec3 c = Vec3.atCenterOf(pos);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.6, c.z, 16, 0.5, 0.3, 0.5, 0.0);
		Fx.far(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, c.x, c.y + 1, c.z, 6, 0.4, 0.3, 0.4, 0.03);
		level.playSound(null, pos, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 2.0F, 0.6F);
		owner.sendSystemMessage(msg("leap.success").withStyle(ChatFormatting.GOLD));
		EruptionEvents.ISLAND_SEEDED.invoker().onIslandSeeded(owner, level, pos);
	}

	// ------------------------------------------------------------------ Bomb Rain

	private static boolean bombRain(ServerPlayer p) {
		ServerLevel level = p.level();
		BlockHitResult hit = ray(p, 56);
		Vec3 target;
		if (hit.getType() == HitResult.Type.BLOCK) {
			target = hit.getLocation();
		} else {
			Vec3 far = p.getEyePosition().add(p.getViewVector(1.0F).scale(40));
			int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR, (int) Math.floor(far.x), (int) Math.floor(far.z));
			target = new Vec3(far.x, Math.max(y, level.getSeaLevel() - 1), far.z);
		}
		Fx.start(level, Effects.bombVolley(p, target, 18, 15));
		return true;
	}

	// ------------------------------------------------------------------ Great Eruption

	private static boolean greatEruption(ServerPlayer p) {
		ServerLevel level = p.level();
		BlockHitResult hit = ray(p, 64);
		BlockPos at = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().relative(hit.getDirection()) : p.blockPosition();
		FlowSim.Params params = FlowSim.Params.of(6000, 30).slopes(2.5, 0.42).noise(3.0).crater(2);
		FlowSim.Result plan = FlowSim.run(level, at, params, level.getRandom().nextLong());
		if (plan.isEmpty()) {
			p.sendOverlayMessage(msg("power.no_room").withStyle(ChatFormatting.GRAY));
			return false;
		}
		BlockPlacer.submit(level, plan, p, 400, 110, null);
		Vec3 crater = new Vec3(plan.originX() + 0.5, plan.topOr(at.getY() - 1) + 1.0, plan.originZ() + 0.5);
		Fx.start(level, Effects.ashColumn(p, crater, 240));
		return true;
	}
}
