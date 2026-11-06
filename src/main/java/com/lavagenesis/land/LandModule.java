package com.lavagenesis.land;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.land.birth.BirthVolcano;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * Entry point of the land module (see DESIGN.md). Called from LavaGenesis.onInitialize, before the other modules,
 * so the blocks and items exist when they look them up by id.
 * <p>
 * Additions to vanilla tags (mineable, logs, leaves, dirt, sand...) live in the always-on built-in pack
 * {@code resourcepacks/land_tags/} so they can never collide with another module's file of the same path.
 */
public final class LandModule {
	private LandModule() {}

	public static void init() {
		LandBlocks.init();
		LandItems.init();
		LandWorldgen.init();
		BirthVolcano.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> LandCommand.register(dispatcher));
		FabricLoader.getInstance().getModContainer(LavaGenesis.MOD_ID).ifPresent(mod -> ResourceLoader.registerBuiltinPack(
			LavaGenesis.id("land_tags"), mod, Component.translatable("pack.lavagenesis.land_tags"), PackActivationType.ALWAYS_ENABLED));
	}
}
