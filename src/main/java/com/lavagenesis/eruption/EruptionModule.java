package com.lavagenesis.eruption;

import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.eruption.entity.EruptionEntities;
import com.lavagenesis.eruption.fx.Fx;
import com.lavagenesis.eruption.land.BlockPlacer;
import com.lavagenesis.eruption.net.SelectPowerPayload;
import com.lavagenesis.eruption.net.ShakePayload;
import com.lavagenesis.eruption.net.UsePowerPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Entry point of the eruption module (see DESIGN.md): pressure, the active powers on keybinds, the land they
 * build (cones, flows, pillow mounds, seed islands), lava bombs and the effects. Called from LavaGenesis.onInitialize.
 */
public final class EruptionModule {
	private EruptionModule() {}

	public static void init() {
		EruptionState.init();
		EruptionEntities.init();

		PayloadTypeRegistry.serverboundPlay().register(UsePowerPayload.TYPE, UsePowerPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SelectPowerPayload.TYPE, SelectPowerPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ShakePayload.TYPE, ShakePayload.CODEC);

		// Fabric runs play payload handlers on the server thread
		ServerPlayNetworking.registerGlobalReceiver(UsePowerPayload.TYPE, (payload, ctx) -> Powers.use(ctx.player(), payload.power()));
		ServerPlayNetworking.registerGlobalReceiver(SelectPowerPayload.TYPE, (payload, ctx) -> Powers.select(ctx.player(), payload.power()));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Pressure.tick(server);
			Motion.tick(server);
		});
		ServerTickEvents.END_LEVEL_TICK.register(level -> {
			BlockPlacer.tick(level);
			Fx.tick(level);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BlockPlacer.clear();
			Fx.clear();
			Motion.clear();
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			Motion.forget(handler.getPlayer().getUUID());
			Pressure.forget(handler.getPlayer().getUUID());
		});

		// a new stage brings a new power: select it and say how to use it
		MagmaCore.STAGE_CHANGED.register((player, from, to) -> {
			for (Power p : Power.values()) {
				if (p.stage == to) {
					EruptionState.set(player, EruptionState.get(player).withSelected(p));
					// the experience module's stage-up summary names and describes the power; a tick later this adds the keys
					Component line = Component.translatable("msg.lavagenesis.power.unlocked",
							Component.keybind("key.lavagenesis.use_power").withStyle(ChatFormatting.YELLOW),
							p.displayName().copy().withColor(p.color).withStyle(ChatFormatting.BOLD),
							Component.keybind("key.lavagenesis.cycle_power").withStyle(ChatFormatting.YELLOW))
						.withStyle(ChatFormatting.GRAY);
					Fx.start(player.level(), (level, age) -> {
						if (age < 1) return true;
						if (!player.isRemoved()) player.sendSystemMessage(line);
						return false;
					});
				}
			}
		});
	}
}
