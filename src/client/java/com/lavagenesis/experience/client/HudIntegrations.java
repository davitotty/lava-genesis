package com.lavagenesis.experience.client;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.eruption.Power;
import com.lavagenesis.eruption.client.EruptionClientState;
import com.lavagenesis.vent.VentProgress;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Connects the optional HUD rows ({@link HudExtras}) to the other modules' client-readable APIs:
 * {@link VentProgress#offered} (vent module, synced attachment) and {@link EruptionClientState} (eruption module).
 * Each hook is guarded by the HUD: if a call ever fails it is switched off and that row disappears.
 */
final class HudIntegrations {
	private HudIntegrations() {}

	static void wire() {
		try {
			HudExtras.offered = VentProgress::offered;
		} catch (LinkageError e) {
			LavaGenesis.LOGGER.warn("Lava Genesis HUD: vent module API missing, offering counts hidden");
		}
		try {
			HudExtras.power = player -> powerRow();
		} catch (LinkageError e) {
			LavaGenesis.LOGGER.warn("Lava Genesis HUD: eruption module API missing, power row hidden");
		}
	}

	private static HudExtras.PowerRow powerRow() {
		Power sel = EruptionClientState.selected();
		List<Power> unlocked = EruptionClientState.unlockedPowers();
		List<ItemStack> icons = new ArrayList<>(unlocked.size());
		for (Power p : unlocked) icons.add(EruptionClientState.icon(p));
		Component hint = Component.translatable("hud." + LavaGenesis.MOD_ID + ".power_keys",
			key(EruptionClientState.useKey()), key(EruptionClientState.cycleKey()), key(EruptionClientState.flareKey()));
		return new HudExtras.PowerRow(
			EruptionClientState.name(sel),
			EruptionClientState.icon(sel),
			sel.color,
			sel.cost,
			EruptionClientState.canAfford(sel),
			EruptionClientState.cooldownTicksLeft(sel),
			sel.cooldownTicks,
			icons,
			unlocked.indexOf(sel),
			hint);
	}

	private static Component key(KeyMapping k) {
		return k.isUnbound() ? Component.literal("-") : k.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.GRAY);
	}
}
