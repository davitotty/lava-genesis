package com.lavagenesis.vent;

import com.lavagenesis.core.LavaIds;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The Hotspot Vent block, its block item and block entity type. */
public final class VentBlocks {
	private VentBlocks() {}

	public static HotspotVentBlock HOTSPOT_VENT;
	public static Item HOTSPOT_VENT_ITEM;
	public static BlockEntityType<HotspotVentBlockEntity> HOTSPOT_VENT_BE;

	static void init() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, LavaIds.HOTSPOT_VENT);
		HOTSPOT_VENT = Registry.register(BuiltInRegistries.BLOCK, blockKey, new HotspotVentBlock(BlockBehaviour.Properties.of()
			.setId(blockKey)
			.mapColor(MapColor.FIRE)
			.strength(8.0F, 3600000.0F)
			.lightLevel(s -> 15)
			.emissiveRendering(s -> true)
			.sound(SoundType.BASALT)
			.pushReaction(PushReaction.IMMOVEABLE)
			.isValidSpawn((s, l, p, e) -> false)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, LavaIds.HOTSPOT_VENT);
		HOTSPOT_VENT_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(HOTSPOT_VENT,
			new Item.Properties().setId(itemKey).useBlockDescriptionPrefix().rarity(Rarity.EPIC).fireResistant().stacksTo(16)));

		HOTSPOT_VENT_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, LavaIds.HOTSPOT_VENT,
			new BlockEntityType<>(HotspotVentBlockEntity::new, Set.of(HOTSPOT_VENT)));
	}
}
