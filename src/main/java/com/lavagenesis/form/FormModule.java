package com.lavagenesis.form;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.food.FoodData;

/**
 * Entry point of the form module (see DESIGN.md): the player's lava body.
 * <ul>
 *   <li>size and stats per stage ({@link BodySize}, {@link FormStats}), growing smoothly on stage-up,</li>
 *   <li>fire/lava/magma immunity and lava swimming (mixins in {@code com.lavagenesis.form.mixin}),</li>
 *   <li>heat and solidifying ({@link HeatSystem}), no hunger (heat is food),</li>
 *   <li>absorbing lava by sneaking ({@link LavaAbsorb}),</li>
 *   <li>per-stage passives ({@link Passives}),</li>
 *   <li>{@link #BODY}: the look of each body, synced to every client for rendering.</li>
 * </ul>
 */
public final class FormModule {
	private FormModule() {}

	/** Stage + crust of every player, synced to all clients (the core MAGMA data only reaches its owner). */
	public static final AttachmentType<BodyState> BODY = AttachmentRegistry.create(LavaGenesis.id("form_body"), b -> b
		.initializer(() -> BodyState.DEFAULT)
		.syncWith(BodyState.STREAM_CODEC, AttachmentSyncPredicate.all()));

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(FormModule::tickServer);

		MagmaCore.STAGE_CHANGED.register((player, from, to) -> {
			FormRuntime rt = FormRuntime.of(player);
			BodySize.tick(player, rt);       // starts the growth animation right away
			updateBody(player);
		});

		ServerPlayerEvents.JOIN.register(player -> {
			FormRuntime rt = FormRuntime.of(player);
			rt.heat = -1.0F;
			BodySize.reapply(player, rt);
			updateBody(player);
		});
		ServerPlayerEvents.LEAVE.register(FormRuntime::forget);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			FormRuntime rt = FormRuntime.of(newPlayer);
			rt.heat = -1.0F;
			rt.grantedFlight = false;
			BodySize.reapply(newPlayer, rt);
			// a reborn ember comes back warm
			if (!alive) MagmaCore.update(newPlayer, d -> d.withHeat(Math.max(d.heat(), 0.6F)));
			updateBody(newPlayer);
		});
		ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
			FormRuntime rt = FormRuntime.of(player);
			BodySize.reapply(player, rt);
			updateBody(player);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> FormRuntime.clear());

		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !FormRules.isMolten(player)) return true;
			if (source.is(DamageTypeTags.IS_FIRE)) return false;            // molten body: fire, lava, magma blocks
			if (source.is(DamageTypes.IN_WALL)
				&& player.level().getServer().getTickCount() < FormRuntime.of(player).noSuffocateUntil) {
				return false;                                               // just grew into the rock around us
			}
			return true;
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && !blocked) Passives.onDamaged(player, source);
		});
	}

	private static void tickServer(MinecraftServer server) {
		long time = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!FormRules.isMolten(player)) continue;
			FormRuntime rt = FormRuntime.of(player);
			BodySize.tick(player, rt);
			HeatSystem.tick(player, rt, time);
			LavaAbsorb.tick(player, rt);
			Passives.tick(player, rt, time);
			feed(player);
			player.clearFire();
			if ((time + player.getId()) % 10 == 0) updateBody(player);
		}
	}

	/** No hunger: heat is food. Warm bodies keep a full-ish bar (slow natural regen, can still eat); cold ones starve. */
	private static void feed(ServerPlayer player) {
		FoodData food = player.getFoodData();
		float heat = HeatSystem.heat(player);
		int target = heat >= HeatSystem.CRUSTING ? 19 : heat > 0.0F ? 17 : 6;
		if (food.getFoodLevel() != target) food.setFoodLevel(target);
		if (food.getSaturationLevel() > 2.0F) food.setSaturation(2.0F);
	}

	/** Pushes the render-relevant body state to clients when it changed. */
	static void updateBody(ServerPlayer player) {
		MagmaData data = MagmaCore.get(player);
		BodyState next = new BodyState(data.stage().ordinal(), FormRules.crustFor(HeatSystem.heat(player)));
		if (!next.equals(player.getAttached(BODY))) player.setAttached(BODY, next);
	}
}
