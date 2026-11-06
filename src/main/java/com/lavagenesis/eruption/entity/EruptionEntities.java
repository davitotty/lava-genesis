package com.lavagenesis.eruption.entity;

import com.lavagenesis.LavaGenesis;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entity types of the eruption module. */
public final class EruptionEntities {
	private EruptionEntities() {}

	public static final ResourceKey<EntityType<?>> LAVA_BOMB_KEY = ResourceKey.create(Registries.ENTITY_TYPE, LavaGenesis.id("lava_bomb"));

	/** {@code lavagenesis:lava_bomb}: the arcing glowing projectile of Lava Spit, eruptions and Bomb Rain. */
	public static final EntityType<LavaBomb> LAVA_BOMB = Registry.register(BuiltInRegistries.ENTITY_TYPE, LAVA_BOMB_KEY,
		EntityType.Builder.<LavaBomb>of(LavaBomb::new, MobCategory.MISC)
			.sized(0.4F, 0.4F)
			.clientTrackingRange(8)
			.updateInterval(5)
			.fireImmune()
			.noSave()
			.build(LAVA_BOMB_KEY));

	public static void init() {}
}
