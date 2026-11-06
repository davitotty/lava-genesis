package com.lavagenesis;

import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.eruption.EruptionModule;
import com.lavagenesis.experience.ExperienceModule;
import com.lavagenesis.form.FormModule;
import com.lavagenesis.land.LandModule;
import com.lavagenesis.vent.VentModule;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LavaGenesis implements ModInitializer {
	public static final String MOD_ID = "lavagenesis";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		MagmaCore.init();
		// Land first: it registers the blocks and items the other modules look up by id.
		LandModule.init();
		VentModule.init();
		FormModule.init();
		EruptionModule.init();
		ExperienceModule.init();
		LOGGER.info("Lava Genesis loaded - the hotspot is awake");
	}
}
