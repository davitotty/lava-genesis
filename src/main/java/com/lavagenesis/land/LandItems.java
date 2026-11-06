package com.lavagenesis.land;

import com.lavagenesis.core.LavaIds;
import com.lavagenesis.land.item.MagmaShardItem;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.Block;

/**
 * The offering items (one per stage that asks for one) and the land block items. Every item is fire resistant:
 * the player lives in lava, and dropped loot must not burn up in it.
 */
public final class LandItems {
	private LandItems() {}

	private static final Style LORE = Style.EMPTY.withColor(TextColor.fromRgb(0xC9A27E)).withItalic(false);

	public static final Item MAGMA_SHARD = register(LavaIds.MAGMA_SHARD, MagmaShardItem::new, offering(LavaIds.MAGMA_SHARD)
		.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.1F).alwaysEdible().build(),
			Consumables.defaultFood().consumeSeconds(0.8F).sound(SoundEvents.GENERIC_EAT)
				.onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0), 1.0F))
				.build()));
	public static final Item OLIVINE = register(LavaIds.OLIVINE, Item::new, offering(LavaIds.OLIVINE));
	public static final Item PELES_TEAR = register(LavaIds.PELES_TEAR, Item::new, offering(LavaIds.PELES_TEAR));
	public static final Item SULFUR_CRYSTAL = register(LavaIds.SULFUR_CRYSTAL, Item::new, offering(LavaIds.SULFUR_CRYSTAL));
	public static final Item VOLCANIC_GLASS = register(LavaIds.VOLCANIC_GLASS, Item::new, offering(LavaIds.VOLCANIC_GLASS));
	public static final Item HOTSPOT_HEART = register(LavaIds.HOTSPOT_HEART, Item::new, offering(LavaIds.HOTSPOT_HEART)
		.rarity(Rarity.EPIC).stacksTo(16).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));

	static void init() {
		for (Block block : LandBlocks.WITH_ITEMS) {
			Identifier id = BuiltInRegistries.BLOCK.getKey(block);
			register(id, p -> new BlockItem(block, p.useBlockDescriptionPrefix()), new Item.Properties().fireResistant());
		}
	}

	/** Offering items carry one line of lore: what they are and where they come from (item.lavagenesis.x.desc). */
	private static Item.Properties offering(Identifier id) {
		return new Item.Properties().fireResistant().component(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("item." + id.getNamespace() + "." + id.getPath() + ".desc").withStyle(LORE))));
	}

	private static Item register(Identifier id, Function<Item.Properties, Item> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}
}
