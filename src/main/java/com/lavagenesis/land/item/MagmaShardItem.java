package com.lavagenesis.land.item;

import com.lavagenesis.core.MagmaCore;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A still-warm splinter of magma. Eating one feeds the player-volcano: +6 mass. Always edible. */
public class MagmaShardItem extends Item {
	public static final int MASS = 6;

	public MagmaShardItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (entity instanceof ServerPlayer player && level instanceof ServerLevel server) {
			MagmaCore.addMass(player, MASS, "eat");
			server.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + player.getBbHeight() * 0.6, player.getZ(),
				4, 0.2, 0.2, 0.2, 0.0);
			level.playSound(null, player.blockPosition(), SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.7F, 1.2F);
		}
		return super.finishUsingItem(stack, level, entity);
	}
}
