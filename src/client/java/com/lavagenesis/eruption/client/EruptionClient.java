package com.lavagenesis.eruption.client;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.eruption.Power;
import com.lavagenesis.eruption.entity.EruptionEntities;
import com.lavagenesis.eruption.net.SelectPowerPayload;
import com.lavagenesis.eruption.net.ShakePayload;
import com.lavagenesis.eruption.net.UsePowerPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/**
 * Client entry point of the eruption module (see DESIGN.md). Called from LavaGenesisClient.
 * <p>
 * Keys (category "Lava Genesis", all rebindable):
 * <ul>
 *   <li><b>R</b> - use the selected power. Flare and Lava Spit keep firing while held.</li>
 *   <li><b>V</b> - select the next unlocked power (sneak + V: the previous one).</li>
 *   <li><b>G</b> - Flare, whatever is selected: melting rock is always one key away.</li>
 * </ul>
 */
public final class EruptionClient {
	private EruptionClient() {}

	/** The "Lava Genesis" key category ({@code lavagenesis:main}); other modules may put their keys here too. */
	public static final KeyMapping.Category CATEGORY = category();

	private static KeyMapping.Category category() {
		try {
			return KeyMapping.Category.register(LavaGenesis.id("main"));
		} catch (IllegalArgumentException alreadyRegistered) {
			return new KeyMapping.Category(LavaGenesis.id("main")); // same record, registered by another module
		}
	}

	public static final KeyMapping USE = new KeyMapping("key.lavagenesis.use_power", InputConstants.KEY_R, CATEGORY);
	public static final KeyMapping CYCLE = new KeyMapping("key.lavagenesis.cycle_power", InputConstants.KEY_V, CATEGORY);
	public static final KeyMapping FLARE = new KeyMapping("key.lavagenesis.flare", InputConstants.KEY_G, CATEGORY);

	private static final long[] LAST_SENT = new long[Power.count()];

	public static void init() {
		KeyMappingHelper.registerKeyMapping(USE);
		KeyMappingHelper.registerKeyMapping(CYCLE);
		KeyMappingHelper.registerKeyMapping(FLARE);

		EntityRendererRegistry.register(EruptionEntities.LAVA_BOMB, ctx -> new ThrownItemRenderer<>(ctx, 1.4F, true));

		ClientPlayNetworking.registerGlobalReceiver(ShakePayload.TYPE, (payload, ctx) -> CameraShake.start(payload.strength(), payload.ticks()));

		ClientTickEvents.END_CLIENT_TICK.register(EruptionClient::tick);
	}

	private static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		CameraShake.tick(player);
		if (player == null || mc.level == null) {
			// drain stale clicks
			while (USE.consumeClick()) {}
			while (CYCLE.consumeClick()) {}
			while (FLARE.consumeClick()) {}
			return;
		}
		while (CYCLE.consumeClick()) {
			Power next = EruptionClientState.selected().next(EruptionClientState.stage(), player.isShiftKeyDown() ? -1 : 1);
			EruptionClientState.predictSelection(next);
			send(new SelectPowerPayload(next.ordinal()));
		}
		Power selected = EruptionClientState.selected();
		handle(USE, selected, mc);
		handle(FLARE, Power.FLARE, mc);
	}

	private static void handle(KeyMapping key, Power power, Minecraft mc) {
		boolean clicked = false;
		while (key.consumeClick()) clicked = true;
		long now = mc.level.getGameTime();
		if (now < LAST_SENT[power.ordinal()]) LAST_SENT[power.ordinal()] = 0; // new world, clock went back
		if (clicked) {
			fire(power, now);
		} else if (key.isDown() && power.repeatable) {
			// held: fire again whenever the synced cooldown says it is ready
			if (EruptionClientState.cooldownTicksLeft(power) == 0 && now - LAST_SENT[power.ordinal()] >= Math.max(3, power.cooldownTicks)) {
				fire(power, now);
			}
		}
	}

	private static void fire(Power power, long now) {
		LAST_SENT[power.ordinal()] = now;
		send(new UsePowerPayload(power.ordinal()));
	}

	private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
		if (ClientPlayNetworking.canSend(payload.type())) ClientPlayNetworking.send(payload);
	}
}
