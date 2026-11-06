package com.lavagenesis.eruption.entity;

import com.lavagenesis.eruption.land.BlockPlacer;
import com.lavagenesis.eruption.land.FlowSim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A glowing blob of lava thrown by Lava Spit, eruption fountains and Bomb Rain. Arcs under gravity trailing smoke
 * and flame, hisses into steam in water, and where it lands it splashes into a small patch of cooling lava,
 * burning and hurting the mobs around it.
 */
public class LavaBomb extends ThrowableItemProjectile {
	private int splashVolume = 3;
	private float damage = 4.0F;
	private float radius = 1.5F;
	private int age;
	private boolean wasInWater;

	public LavaBomb(EntityType<? extends LavaBomb> type, Level level) {
		super(type, level);
	}

	/** Spawns a bomb at {@code from} flying with {@code velocity} (blocks/tick). */
	public static LavaBomb launch(ServerLevel level, Vec3 from, Vec3 velocity, Entity owner, Item look,
		int splashVolume, float damage, float radius) {
		LavaBomb b = new LavaBomb(EruptionEntities.LAVA_BOMB, level);
		b.setPos(from.x, from.y, from.z);
		b.setOwner(owner);
		b.setItem(new ItemStack(look));
		b.setDeltaMovement(velocity);
		b.splashVolume = splashVolume;
		b.damage = damage;
		b.radius = radius;
		level.addFreshEntity(b);
		return b;
	}

	/** Spawns a bomb that arcs from {@code from} to land near {@code to} after about {@code ticks} ticks. */
	public static LavaBomb lob(ServerLevel level, Vec3 from, Vec3 to, int ticks, Entity owner, Item look,
		int splashVolume, float damage, float radius) {
		double g = 0.04;
		// air drag of 0.99 per tick shortens the throw; stretch the horizontal speed to make up for it
		double stretch = ticks / ((1 - Math.pow(0.99, ticks)) / 0.01);
		double vx = (to.x - from.x) / ticks * stretch;
		double vz = (to.z - from.z) / ticks * stretch;
		double vy = ((to.y - from.y) + 0.5 * g * ticks * ticks) / ticks * stretch;
		return launch(level, from, new Vec3(vx, vy, vz), owner, look, splashVolume, damage, radius);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.FIRE_CHARGE;
	}

	@Override
	protected double getDefaultGravity() {
		return 0.04;
	}

	@Override
	public void tick() {
		super.tick();
		age++;
		Level level = level();
		boolean inWater = isInWater();
		if (level.isClientSide()) {
			RandomSource r = getRandom();
			Vec3 v = getDeltaMovement();
			double x = getX() - v.x * 0.5, y = getY() + 0.1 - v.y * 0.5, z = getZ() - v.z * 0.5;
			if (inWater) {
				level.addParticle(ParticleTypes.BUBBLE, x, y, z, (r.nextDouble() - 0.5) * 0.1, 0.1, (r.nextDouble() - 0.5) * 0.1);
				level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 0, 0.05, 0);
			} else {
				level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0.02, 0);
				level.addParticle(ParticleTypes.FLAME, getX(), getY() + 0.1, getZ(), (r.nextDouble() - 0.5) * 0.04, 0.02, (r.nextDouble() - 0.5) * 0.04);
				if (r.nextInt(3) == 0) level.addParticle(ParticleTypes.FALLING_LAVA, x, y, z, 0, 0, 0);
				if (r.nextInt(4) == 0) level.addParticle(ParticleTypes.SMOKE, x, y, z, 0, 0.05, 0);
			}
		} else {
			if (inWater && !wasInWater) {
				ServerLevel sl = (ServerLevel) level;
				sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 8, 0.3, 0.3, 0.3, 0.05);
				sl.playSound(null, getX(), getY(), getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL, 0.7F, 1.2F);
			}
			if (age > 300) discard();
		}
		wasInWater = inWater;
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return super.canHitEntity(e) && !(e instanceof LavaBomb);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		if (level() instanceof ServerLevel sl && hit.getEntity() instanceof LivingEntity living) {
			living.hurtServer(sl, source(sl), damage * 1.5F);
			living.igniteForSeconds(5.0F);
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!(level() instanceof ServerLevel sl) || isRemoved()) return;
		BlockPos at;
		if (hit instanceof BlockHitResult bh) {
			at = bh.getBlockPos().relative(bh.getDirection());
		} else if (hit instanceof EntityHitResult eh) {
			at = eh.getEntity().blockPosition();
		} else {
			at = blockPosition();
		}
		splash(sl, at);
		discard();
	}

	private DamageSource source(ServerLevel sl) {
		Entity owner = getOwner();
		return sl.damageSources().thrown(this, owner != null ? owner : this);
	}

	private void splash(ServerLevel sl, BlockPos at) {
		RandomSource r = getRandom();
		double x = getX(), y = getY(), z = getZ();
		boolean water = isInWater();
		boolean big = radius >= 2.0F;
		if (water) {
			sl.sendParticles(ParticleTypes.CLOUD, x, y + 0.3, z, 10, 0.5, 0.4, 0.5, 0.06);
			sl.sendParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 12, 0.5, 0.3, 0.5, 0.2);
			sl.playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.NEUTRAL, 0.9F, 0.8F + r.nextFloat() * 0.3F);
		} else {
			sl.sendParticles(ParticleTypes.LAVA, x, y, z, big ? 14 : 7, 0.4, 0.2, 0.4, 0.0);
			sl.sendParticles(ParticleTypes.FLAME, x, y, z, big ? 18 : 8, 0.4, 0.3, 0.4, 0.06);
			sl.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, big ? 8 : 3, 0.4, 0.3, 0.4, 0.03);
			if (big) {
				sl.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.3, z, 1, 0, 0, 0, 0);
				sl.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 0.6F, 1.1F + r.nextFloat() * 0.3F);
			}
			sl.playSound(null, x, y, z, SoundEvents.LAVA_POP, SoundSource.NEUTRAL, 1.0F, 0.6F + r.nextFloat() * 0.4F);
			sl.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.4F, 1.4F);
		}
		Entity owner = getOwner();
		AABB area = new AABB(x - radius, y - radius, z - radius, x + radius, y + radius, z + radius);
		for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e.isAlive() && e != owner)) {
			e.hurtServer(sl, source(sl), damage);
			if (!water) e.igniteForSeconds(4.0F);
		}
		if (splashVolume > 0) {
			FlowSim.Params p = FlowSim.Params.of(splashVolume, Math.max(2, (int) Math.ceil(radius) + 1))
				.slopes(0.8, 0.6).noise(0.8);
			FlowSim.Result plan = FlowSim.run(sl, at, p, r.nextLong());
			BlockPlacer.submit(sl, plan, owner instanceof ServerPlayer sp ? sp : null, Math.max(1, splashVolume / 2), null);
		}
	}
}
