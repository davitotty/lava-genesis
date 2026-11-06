package com.lavagenesis.land;

import com.lavagenesis.LavaGenesis;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;

/**
 * Natural volcanic features of the ocean (placed features in data/lavagenesis/worldgen/.../land/):
 * olivine ore on every ocean floor (more in deep oceans), hydrothermal fields of fumaroles and pillow lava and
 * small glowing lava seeps in deep oceans. Also: vanilla magma blocks sometimes drop a Magma Shard.
 */
final class LandWorldgen {
	private LandWorldgen() {}

	static void init() {
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OCEAN), GenerationStep.Decoration.UNDERGROUND_ORES,
			placed("land/olivine_ore_seafloor"));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN), GenerationStep.Decoration.UNDERGROUND_ORES,
			placed("land/olivine_ore_deep"));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN), GenerationStep.Decoration.VEGETAL_DECORATION,
			placed("land/hydrothermal_field"));
		BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_DEEP_OCEAN), GenerationStep.Decoration.VEGETAL_DECORATION,
			placed("land/lava_seep"));

		LootTableEvents.MODIFY.register((key, table, source, registries) -> {
			if (source.isBuiltin() && Blocks.MAGMA_BLOCK.getLootTable().filter(key::equals).isPresent()) {
				table.withPool(LootPool.lootPool().when(LootItemRandomChanceCondition.randomChance(0.4F))
					.add(LootItem.lootTableItem(LandItems.MAGMA_SHARD)));
			}
		});
	}

	private static ResourceKey<PlacedFeature> placed(String path) {
		return ResourceKey.create(Registries.PLACED_FEATURE, LavaGenesis.id(path));
	}
}
