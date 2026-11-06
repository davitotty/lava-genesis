package com.lavagenesis.experience.client;

import com.lavagenesis.LavaGenesis;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client entry point of the experience module (see DESIGN.md). Called from LavaGenesisClient. */
public final class ExperienceClient {
	private ExperienceClient() {}

	/** The shared "Lava Genesis" key category (the eruption module usually registers it first). */
	public static final KeyMapping.Category CATEGORY = category();

	private static KeyMapping.Category category() {
		try {
			return KeyMapping.Category.register(LavaGenesis.id("main"));
		} catch (IllegalArgumentException alreadyRegistered) {
			return new KeyMapping.Category(LavaGenesis.id("main"));
		}
	}
	/** Cycles the Lava Genesis panel: full -> compact -> hidden. */
	public static KeyMapping toggleHud;

	private static final String CONFIG = "lavagenesis-hud.txt";

	public static void init() {
		toggleHud = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + LavaGenesis.MOD_ID + ".toggle_hud",
			InputConstants.Type.KEYBOARD, InputConstants.KEY_H, CATEGORY));

		// below the chat, so chat lines draw over the panel rather than under it
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, MagmaHud.ID, MagmaHud::extract);

		ClientTickEvents.END_CLIENT_TICK.register(ExperienceClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> MagmaHud.reset());

		MagmaHud.mode = loadMode();
		HudIntegrations.wire();
	}

	private static void tick(Minecraft mc) {
		while (toggleHud.consumeClick()) {
			MagmaHud.mode = MagmaHud.mode.next();
			saveMode(MagmaHud.mode);
			if (mc.player != null) {
				mc.player.sendOverlayMessage(Component.translatable("hud." + LavaGenesis.MOD_ID + ".mode",
					Component.translatable("hud." + LavaGenesis.MOD_ID + ".mode." + MagmaHud.mode.name().toLowerCase(java.util.Locale.ROOT))));
			}
		}
	}

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve(CONFIG);
	}

	private static MagmaHud.Mode loadMode() {
		try {
			Path p = configFile();
			if (Files.exists(p)) return MagmaHud.Mode.valueOf(Files.readString(p, StandardCharsets.UTF_8).trim());
		} catch (IOException | IllegalArgumentException e) {
			// fall through to the default
		}
		return MagmaHud.Mode.FULL;
	}

	private static void saveMode(MagmaHud.Mode mode) {
		try {
			Files.writeString(configFile(), mode.name(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			LavaGenesis.LOGGER.debug("Could not save HUD mode", e);
		}
	}
}
