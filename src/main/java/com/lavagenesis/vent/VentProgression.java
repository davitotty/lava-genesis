package com.lavagenesis.vent;

import com.lavagenesis.core.LavaIds;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Offerings, the requirement checklist, automatic stage advancement and hotspot upwelling. */
public final class VentProgression {
	private VentProgression() {}

	/** Ticks between two automatic checks of every player (also the upwelling period: 2 s). */
	private static final int PERIOD = 40;
	public static final double UPWELLING_RADIUS = 12.0;
	public static final int MASS_PER_OFFERING = 10;

	static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		if (t % PERIOD != 0) return;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			VentRegistry.validateHome(p);
			upwelling(p);
			tryAdvance(p);
		}
	}

	// ---------------------------------------------------------------- stage advancement

	/** Advances the player one stage when every requirement of the next stage is met. */
	public static boolean tryAdvance(ServerPlayer player) {
		MagmaStage next = MagmaCore.get(player).stage().next();
		if (next == null || !VentProgress.ready(player)) return false;
		MagmaCore.update(player, d -> d.withStage(next));
		Optional<GlobalPos> home = MagmaCore.get(player).homeVent();
		if (home.isPresent()) {
			ServerLevel level = player.level().getServer().getLevel(home.get().dimension());
			BlockPos v = home.get().pos();
			if (level != null && level.isLoaded(v)) {
				level.sendParticles(ParticleTypes.LAVA, v.getX() + 0.5, v.getY() + 1.1, v.getZ() + 0.5, 40, 0.6, 0.4, 0.6, 0.2);
				level.sendParticles(ParticleTypes.FLAME, v.getX() + 0.5, v.getY() + 1.1, v.getZ() + 0.5, 30, 0.3, 1.0, 0.3, 0.05);
				level.playSound(null, v, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 2.0F, 0.6F);
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- offerings

	/** Right-click on a vent with an item. Returns true when the click was handled as an offering attempt. */
	static boolean offer(ServerPlayer player, ServerLevel level, BlockPos pos, HotspotVentBlockEntity vent, ItemStack stack) {
		Item wanted = VentProgress.offeringItem(player);
		boolean isOffering = wanted != null && stack.is(wanted);
		if (!isOffering && !isAnyOffering(stack)) return false;
		if (!vent.isOwnedBy(player)) {
			player.sendOverlayMessage(Component.translatable("message.lavagenesis.vent.not_yours", vent.ownerName()).withStyle(ChatFormatting.RED));
			return true;
		}
		if (!vent.isHome()) {
			player.sendOverlayMessage(Component.translatable("message.lavagenesis.vent.offer_at_home").withStyle(ChatFormatting.GOLD));
			return true;
		}
		if (!isOffering) {
			player.sendOverlayMessage(Component.translatable("message.lavagenesis.vent.not_now", stack.getHoverName(),
				wanted == null ? Component.translatable("message.lavagenesis.vent.no_offering") : VentProgress.itemName(wanted, null))
				.withStyle(ChatFormatting.GOLD));
			return true;
		}
		int needed = VentProgress.offeringNeeded(player) - VentProgress.offered(player);
		if (needed <= 0) {
			player.sendOverlayMessage(Component.translatable("message.lavagenesis.vent.offering_full").withStyle(ChatFormatting.GOLD));
			return true;
		}
		int n = player.hasInfiniteMaterials() ? needed : Math.min(needed, stack.getCount());
		stack.consume(n, player);
		VentProgress.addOffered(player, n);
		MagmaCore.addMass(player, n * MASS_PER_OFFERING, "offering");

		double x = pos.getX() + 0.5, y = pos.getY() + 1.05, z = pos.getZ() + 0.5;
		level.sendParticles(ParticleTypes.LAVA, x, y, z, 8 + n * 2, 0.3, 0.2, 0.3, 0.1);
		level.sendParticles(ParticleTypes.FLAME, x, y, z, 12 + n * 2, 0.25, 0.4, 0.25, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.3, z, 6, 0.2, 0.3, 0.2, 0.02);
		level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.2F, 0.7F + level.getRandom().nextFloat() * 0.3F);
		level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.6F, 0.5F);

		int now = VentProgress.offered(player), total = VentProgress.offeringNeeded(player);
		player.sendOverlayMessage(Component.translatable("message.lavagenesis.vent.offered", now, total, VentProgress.itemName(wanted, null))
			.withStyle(now >= total ? ChatFormatting.GREEN : ChatFormatting.GOLD));
		if (now >= total) level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.BLOCKS, 1.0F, 0.8F);
		if (!tryAdvance(player) && now >= total) showChecklist(player, vent);
		return true;
	}

	static boolean isAnyOffering(ItemStack stack) {
		for (MagmaStage s : MagmaStage.values()) {
			if (s.offering == null) continue;
			Item item = LavaIds.item(s.offering);
			if (item != null && stack.is(item)) return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- checklist

	static void showChecklist(ServerPlayer player, HotspotVentBlockEntity vent) {
		if (vent.owner() == null) {
			player.sendSystemMessage(Component.translatable("message.lavagenesis.vent.unclaimed").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (!vent.isOwnedBy(player)) {
			player.sendSystemMessage(Component.translatable("message.lavagenesis.vent.belongs_to", vent.ownerName()).withStyle(ChatFormatting.GOLD));
			return;
		}
		MagmaData d = MagmaCore.get(player);
		player.sendSystemMessage(Component.translatable(vent.isHome() ? "message.lavagenesis.vent.header_home" : "message.lavagenesis.vent.header_island",
			vent.lastIsland()).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		MagmaStage next = d.stage().next();
		if (next == null) {
			player.sendSystemMessage(Component.translatable("message.lavagenesis.vent.final", d.stage().displayName()).withStyle(ChatFormatting.YELLOW));
			return;
		}
		player.sendSystemMessage(Component.translatable("message.lavagenesis.vent.next", next.displayName().copy().withColor(next.color))
			.withStyle(ChatFormatting.YELLOW));
		for (VentProgress.Requirement r : VentProgress.requirements(player)) {
			MutableComponent line = Component.literal(r.met() ? " ✔ " : " ✘ ").withStyle(r.met() ? ChatFormatting.GREEN : ChatFormatting.RED);
			Component text = switch (r.key()) {
				case "offering" -> Component.translatable("requirement.lavagenesis.offering.line", r.current(), r.required(),
					VentProgress.itemName(r.item(), next.offering));
				case "home_vent" -> Component.translatable(r.met() ? "requirement.lavagenesis.home_vent.yes" : "requirement.lavagenesis.home_vent.no");
				default -> Component.translatable("requirement.lavagenesis." + r.key() + ".line", r.current(), r.required());
			};
			line.append(text.copy().withStyle(r.met() ? ChatFormatting.WHITE : ChatFormatting.GRAY));
			player.sendSystemMessage(line);
		}
		if (VentProgress.ready(player)) {
			player.sendSystemMessage(Component.translatable("message.lavagenesis.vent.ready").withStyle(ChatFormatting.GREEN));
		} else if (!vent.isHome() && next.offering != null) {
			player.sendSystemMessage(Component.translatable("message.lavagenesis.vent.offer_at_home").withStyle(ChatFormatting.GRAY));
		}
	}

	// ---------------------------------------------------------------- upwelling

	/** Owner standing in lava / molten rock near the home vent: +mass and heat, every {@link #PERIOD} ticks. */
	private static void upwelling(ServerPlayer player) {
		MagmaData d = MagmaCore.get(player);
		if (d.homeVent().isEmpty() || !player.isAlive()) return;
		GlobalPos home = d.homeVent().get();
		if (!player.level().dimension().equals(home.dimension())) return;
		BlockPos v = home.pos();
		if (player.distanceToSqr(v.getX() + 0.5, v.getY() + 0.5, v.getZ() + 0.5) > UPWELLING_RADIUS * UPWELLING_RADIUS) return;
		if (!inMoltenRock(player)) return;
		MagmaCore.addMass(player, d.stage().ordinal() + 1, "upwelling");
		if (d.heat() < 1.0F) {
			float heat = Math.min(1.0F, d.heat() + 0.15F);
			MagmaCore.update(player, x -> x.heat() < heat ? x.withHeat(heat) : x);
		}
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 3,
			player.getBbWidth() * 0.4, player.getBbHeight() * 0.3, player.getBbWidth() * 0.4, 0.0);
	}

	private static boolean inMoltenRock(ServerPlayer player) {
		if (player.isInLava()) return true;
		Identifier molten = LavaIds.MOLTEN_ROCK;
		BlockPos feet = player.blockPosition();
		for (BlockPos p : new BlockPos[] {feet, feet.below(), feet.above()}) {
			BlockState s = player.level().getBlockState(p);
			if (s.is(VentBlocks.HOTSPOT_VENT)) return true;
			if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).equals(molten)) return true;
		}
		return false;
	}
}
