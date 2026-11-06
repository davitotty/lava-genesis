package com.lavagenesis.core;

import com.lavagenesis.LavaGenesis;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The mod's one creative tab: lists every item in the lavagenesis namespace. Don't make another tab. */
public final class ModTab {
	private ModTab() {}

	public static CreativeModeTab TAB;

	static void init() {
		TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, LavaGenesis.id("main"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.lavagenesis"))
			.icon(() -> {
				ItemStack vent = new ItemStack(LavaIds.item(LavaIds.HOTSPOT_VENT));
				return vent.isEmpty() ? new ItemStack(Items.LAVA_BUCKET) : vent;
			})
			.displayItems((params, output) -> BuiltInRegistries.ITEM.forEach(item -> {
				if (LavaGenesis.MOD_ID.equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) output.accept(item);
			}))
			.build());
	}
}
