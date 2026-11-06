package com.lavagenesis.experience.client;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.LavaIds;
import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

/**
 * The volcanic status panel in the top-left corner: stage, mass toward the next stage, heat, pressure, land, a
 * checklist of what the next stage still needs, and (when the eruption module provides it) the selected power.
 * Everything is read client-side from {@link MagmaCore#get} on the local player. Sizes are in GUI pixels, so the
 * panel follows the GUI scale; on very small GUIs it shrinks a little more so it never crowds the screen.
 */
public final class MagmaHud {
	private MagmaHud() {}

	public enum Mode {
		FULL, COMPACT, HIDDEN;

		Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	static Mode mode = Mode.FULL;

	public static Mode mode() {
		return mode;
	}

	public static final Identifier ID = LavaGenesis.id("magma_hud");
	private static final String K = "hud." + LavaGenesis.MOD_ID + ".";

	// layout (GUI px)
	static final int X = 4, Y = 4, W = 160, PAD = 5;
	/** Width of the label column of the heat and pressure rows, and of their value column. */
	static final int LABEL_W = 52, VALUE_W = 26;

	// palette
	private static final int BG = 0xD8100C0C;
	private static final int BG_HEADER_TOP = 0x50FF6A00;
	private static final int TRACK = 0xFF231A17;
	private static final int TRACK_EDGE = 0xFF0A0706;
	private static final int LABEL = 0xFFA89C94;
	private static final int VALUE = 0xFFF2E8DE;
	private static final int DIM = 0xFF6E625C;
	private static final int DONE = 0xFF8FD47A;
	private static final int TODO = 0xFFE8DCD2;
	private static final int PRESSURE_LOW = 0xFF2E6C88, PRESSURE_HIGH = 0xFF9CE6FF;

	// animation state
	private static float shownMass = -1, shownHeat = -1, shownPressure = -1;
	private static int lastMass = -1;
	private static int gain;
	private static long gainAt, lastFrame;
	private static @Nullable MagmaStage lastStage;
	private static long stageAt = -100000;
	private static @Nullable ItemStack headerIcon;

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mode == Mode.HIDDEN || player.isSpectator()) return;
		if (mc.getDebugOverlay().showDebugScreen()) return;

		MagmaData d = MagmaCore.get(player);
		long now = Util.getMillis();
		animate(d, now);

		float scale = g.guiHeight() < 270 || g.guiWidth() < 400 ? 0.75F : 1.0F;
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(X, Y);
		pose.scale(scale, scale);
		try {
			draw(g, mc.font, player, d, now);
		} finally {
			pose.popMatrix();
		}
	}

	// ---------------------------------------------------------------- state smoothing

	private static void animate(MagmaData d, long now) {
		float dt = lastFrame == 0 ? 0 : Math.min(250, now - lastFrame);
		lastFrame = now;
		float k = 1.0F - (float) Math.exp(-dt / 110.0);
		if (shownMass < 0 || Math.abs(shownMass - d.mass()) > 5000) shownMass = d.mass();
		if (shownHeat < 0) shownHeat = d.heat();
		if (shownPressure < 0) shownPressure = d.pressure();
		shownMass += (d.mass() - shownMass) * k;
		shownHeat += (d.heat() - shownHeat) * k;
		shownPressure += (d.pressure() - shownPressure) * k;

		if (lastMass >= 0 && d.mass() > lastMass) {
			gain = now - gainAt < 1800 ? gain + (d.mass() - lastMass) : d.mass() - lastMass;
			gainAt = now;
		}
		lastMass = d.mass();

		if (lastStage != null && d.stage() != lastStage) stageAt = now;
		lastStage = d.stage();
	}

	/** Forget animation state (new world). */
	static void reset() {
		shownMass = shownHeat = shownPressure = -1;
		lastMass = -1;
		lastStage = null;
		headerIcon = null;
	}

	// ---------------------------------------------------------------- drawing

	private static void draw(GuiGraphicsExtractor g, Font font, LocalPlayer player, MagmaData d, long now) {
		MagmaStage stage = d.stage();
		MagmaStage next = stage.next();
		boolean full = mode == Mode.FULL;
		int stageArgb = 0xFF000000 | stage.color;
		float flash = Mth.clamp(1.0F - (now - stageAt) / 3000.0F, 0.0F, 1.0F);
		HudExtras.PowerRow power = full && HudExtras.power != null ? safePower(player) : null;
		boolean showLand = full && (stage.atLeast(MagmaStage.SEAMOUNT) || d.islands() > 0 || d.totalLand() > 0);

		int h = panelHeight(full, showLand, next, power);

		// panel: dark basalt with a warm glow at the top and a stage-coloured edge
		g.fill(0, 0, W, h, BG);
		g.fillGradient(0, 0, W, 24, mix(BG_HEADER_TOP, 0x90FFFFFF, flash * 0.6F), 0x00000000);
		int edge = mix(alpha(stageArgb, 0.75F), 0xFFFFFFFF, flash);
		g.outline(0, 0, W, h, edge);
		g.fill(0, 1, 2, h - 1, stageArgb);

		// header: icon, stage name, stage number
		float glow = 0.5F + 0.5F * Mth.sin(now / 420.0F);
		g.fill(4, 3, 22, 21, alpha(stageArgb, 0.10F + 0.15F * glow + flash * 0.4F));
		g.item(headerIcon(), 5, 4);
		g.text(font, stage.displayName().copy().withStyle(s -> s.withBold(true)), 26, 4, stageArgb, true);
		Component sub = next == null
			? Component.translatable(K + "final")
			: Component.translatable(K + "stage_of", stage.ordinal() + 1, MagmaStage.values().length);
		g.text(font, sub, 26, 14, LABEL, false);

		int y = 26;
		// mass toward the next stage
		g.text(font, Component.translatable(K + "mass"), PAD, y, LABEL, false);
		String massText = next == null ? fmt(Math.round(shownMass)) : fmt(Math.round(shownMass)) + " / " + fmt(next.minMass);
		g.text(font, massText, W - PAD - font.width(massText), y, VALUE, false);
		if (now - gainAt < 1800 && gain > 0) {
			float a = 1.0F - (now - gainAt) / 1800.0F;
			int rise = (int) ((1.0F - a) * 4);
			String gt = "+" + gain;
			g.text(font, gt, PAD + font.width(Component.translatable(K + "mass")) + 5, y - rise, alpha(0xFFFFD27A, Math.max(0.1F, a)), true);
		}
		y += 10;
		float massFrac;
		if (next == null) {
			massFrac = 1.0F;
		} else {
			int from = stage.minMass;
			massFrac = Mth.clamp((shownMass - from) / Math.max(1.0F, next.minMass - from), 0.0F, 1.0F);
		}
		int massColor = 0xFF000000 | (next != null ? next.color : stage.color);
		bar(g, PAD, y, W - PAD * 2, 6, massFrac, lighten(massColor, 0.35F), massColor, true);
		if (massFrac >= 1.0F && next != null) {
			// ready: a slow shimmer along the bar
			int sx = PAD + (int) (((now / 12) % (W + 40)) - 20);
			g.fill(Math.max(PAD, sx), y + 1, Math.min(W - PAD, sx + 8), y + 5, 0x60FFFFFF);
		}
		y += 10;

		// heat: glowing lava -> dull grey crust, pulsing when low
		float heat = Mth.clamp(shownHeat, 0.0F, 1.0F);
		boolean cold = d.heat() < 0.25F;
		float pulse = cold ? 0.5F + 0.5F * Mth.sin(now / (d.heat() < 0.1F ? 90.0F : 160.0F)) : 0.0F;
		int heatColor = heatColor(heat);
		int heatLabel = cold ? mix(LABEL, 0xFFFF5A3A, pulse) : LABEL;
		Component heatName = d.heat() < 0.1F ? Component.translatable(K + "solidifying") : Component.translatable(K + "heat");
		row(g, font, y, heatName, heatLabel, Math.round(d.heat() * 100) + "%", heat,
			mix(lighten(heatColor, 0.4F), 0xFFFFFFFF, pulse * 0.3F), mix(heatColor, 0xFF2A2420, pulse * 0.4F));
		if (cold) g.outline(PAD + LABEL_W - 1, y, W - PAD * 2 - LABEL_W - VALUE_W + 2, 7, alpha(0xFFFF5A3A, 0.3F + 0.6F * pulse));
		y += 11;

		// pressure
		float pr = Mth.clamp(shownPressure / 100.0F, 0.0F, 1.0F);
		int prColor = mix(PRESSURE_LOW, PRESSURE_HIGH, pr);
		row(g, font, y, Component.translatable(K + "pressure"), LABEL, String.valueOf(d.pressure()), pr,
			lighten(prColor, 0.35F), prColor);
		y += 11;

		if (!full) return;

		// land
		if (showLand) {
			y += 2;
			g.fill(PAD, y - 1, W - PAD, y, 0x30FFFFFF);
			int cw = (W - PAD * 2) / 3;
			landCell(g, font, PAD, y + 2, Component.translatable(K + "islands"), fmt(d.islands()), d.islands() > 0);
			landCell(g, font, PAD + cw, y + 2, Component.translatable(K + "largest"), fmt(d.largestIsland()), d.largestIsland() > 0);
			landCell(g, font, PAD + cw * 2, y + 2, Component.translatable(K + "land"), fmt(d.totalLand()), d.totalLand() > 0);
			y += 22;
		}

		// next stage checklist
		y += 2;
		g.fill(PAD, y - 1, W - PAD, y, 0x30FFFFFF);
		y += 2;
		if (next == null) {
			g.text(font, Component.translatable(K + "done"), PAD, y, 0xFFFFD27A, false);
			y += 10;
		} else {
			g.text(font, Component.translatable(K + "next", next.displayName().copy().withColor(next.color)), PAD, y, LABEL, false);
			y += 11;
			y = check(g, font, y, Component.translatable(K + "check.mass"), fmt(d.mass()) + "/" + fmt(next.minMass), d.mass() >= next.minMass);
			if (next.minIslands > 0) {
				y = check(g, font, y, Component.translatable(K + "check.islands"), d.islands() + "/" + next.minIslands, d.islands() >= next.minIslands);
			}
			if (next.minLargestIsland > 0) {
				y = check(g, font, y, Component.translatable(K + "check.largest"),
					fmt(d.largestIsland()) + "/" + fmt(next.minLargestIsland), d.largestIsland() >= next.minLargestIsland);
			}
			if (next.offering != null && next.offeringCount > 0) {
				int given = safeOffered(player);
				Component name = offeringName(next.offering);
				if (given >= 0) {
					y = check(g, font, y, name, Math.min(given, next.offeringCount) + "/" + next.offeringCount, given >= next.offeringCount);
				} else {
					y = checkUnknown(g, font, y, name, "x" + next.offeringCount);
				}
			}
			if (next.needsVent) {
				boolean bound = d.homeVent().isPresent();
				y = check(g, font, y, Component.translatable(K + "check.vent"), bound ? "" : "-", bound);
			}
		}

		// selected power (eruption module)
		if (power != null) {
			y += 2;
			g.fill(PAD, y - 1, W - PAD, y, 0x30FFFFFF);
			y += 3;
			int pc = 0xFF000000 | power.color();
			if (!power.icon().isEmpty()) {
				Matrix3x2fStack pose = g.pose();
				pose.pushMatrix();
				pose.translate(PAD, y - 1);
				pose.scale(0.75F, 0.75F);
				g.item(power.icon(), 0, 0);
				pose.popMatrix();
			}
			g.text(font, power.name(), PAD + 15, y, pc, true);
			String cost = String.valueOf(power.cost());
			int costX = W - PAD - font.width(cost);
			g.text(font, cost, costX, y, power.affordable() ? PRESSURE_HIGH : 0xFFFF6A4A, false);
			g.fill(costX - 5, y + 2, costX - 2, y + 6, power.affordable() ? PRESSURE_LOW : 0xFF7A2A1A);   // tiny pressure gauge
			y += 11;
			float cd = power.cooldownTotal() > 0 ? Mth.clamp(1.0F - power.cooldownLeft() / (float) power.cooldownTotal(), 0.0F, 1.0F) : 1.0F;
			boolean ready = power.cooldownLeft() <= 0;
			bar(g, PAD, y, W - PAD * 2, 3, cd, ready ? lighten(pc, 0.4F) : 0xFF8A7A70, ready ? pc : 0xFF5A4C44, false);
			y += 5;
			if (power.unlocked().size() > 1) {
				// every unlocked power as a small icon, the selected one framed
				Matrix3x2fStack pose = g.pose();
				for (int i = 0; i < power.unlocked().size(); i++) {
					int ix = PAD + i * 11;
					if (i == power.selectedIndex()) {
						g.fill(ix - 1, y - 1, ix + 10, y + 10, alpha(pc, 0.45F));
						g.outline(ix - 1, y - 1, 11, 11, pc);
					}
					pose.pushMatrix();
					pose.translate(ix + 0.5F, y + 0.5F);
					pose.scale(0.5625F, 0.5625F);
					g.item(power.unlocked().get(i), 0, 0);
					pose.popMatrix();
				}
				y += 12;
			}
			if (power.keyHint() != null) {
				g.text(font, power.keyHint(), PAD, y, DIM, false);
			}
		}
	}

	private static int panelHeight(boolean full, boolean land, @Nullable MagmaStage next, HudExtras.@Nullable PowerRow power) {
		int h = 26 + 20 + 11 + 11;           // header, mass, heat, pressure
		if (!full) return h + 2;
		if (land) h += 24;
		h += 4;
		if (next == null) {
			h += 10;
		} else {
			h += 11 + 10;                   // "Next" + mass
			if (next.minIslands > 0) h += 10;
			if (next.minLargestIsland > 0) h += 10;
			if (next.offering != null && next.offeringCount > 0) h += 10;
			if (next.needsVent) h += 10;
		}
		if (power != null) {
			h += 5 + 11 + 5;
			if (power.unlocked().size() > 1) h += 12;
			if (power.keyHint() != null) h += 10;
		}
		return h + 2;
	}

	private static void row(GuiGraphicsExtractor g, Font font, int y, Component label, int labelColor, String value,
		float frac, int top, int bottom) {
		g.text(font, label, PAD, y, labelColor, false);
		int bx = PAD + LABEL_W, bw = W - PAD * 2 - LABEL_W - VALUE_W;
		bar(g, bx, y + 1, bw, 5, frac, top, bottom, false);
		g.text(font, value, W - PAD - font.width(value), y, VALUE, false);
	}

	/** A recessed bar with a two-tone fill and optional quarter ticks. */
	private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float frac, int top, int bottom, boolean ticks) {
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, TRACK_EDGE);
		g.fill(x, y, x + w, y + h, TRACK);
		int fw = Math.round(w * Mth.clamp(frac, 0.0F, 1.0F));
		if (fw > 0) {
			g.fillGradient(x, y, x + fw, y + h, top, bottom);
			g.fill(x, y, x + fw, y + 1, alpha(lighten(top, 0.5F), 0.7F));     // glossy top line
			if (fw < w) g.fill(x + fw - 1, y, x + fw, y + h, 0xC0FFFFFF);       // hot leading edge
		}
		if (ticks) {
			for (int q = 1; q < 4; q++) {
				int tx = x + w * q / 4;
				g.fill(tx, y, tx + 1, y + h, 0x70000000);
			}
		}
	}

	private static void landCell(GuiGraphicsExtractor g, Font font, int x, int y, Component label, String value, boolean any) {
		g.text(font, label, x, y, DIM, false);
		g.text(font, value, x, y + 10, any ? VALUE : DIM, false);
	}

	private static int check(GuiGraphicsExtractor g, Font font, int y, Component label, String value, boolean done) {
		box(g, PAD + 1, y + 1, done ? 1 : 0);
		g.text(font, label, PAD + 12, y, done ? DONE : TODO, false);
		if (!value.isEmpty()) g.text(font, value, W - PAD - font.width(value), y, done ? DONE : VALUE, false);
		return y + 10;
	}

	private static int checkUnknown(GuiGraphicsExtractor g, Font font, int y, Component label, String value) {
		box(g, PAD + 1, y + 1, 2);
		g.text(font, label, PAD + 12, y, TODO, false);
		g.text(font, value, W - PAD - font.width(value), y, VALUE, false);
		return y + 10;
	}

	/** 7x7 check box: 0 = open, 1 = done (green tick), 2 = unknown (amber dot). */
	private static void box(GuiGraphicsExtractor g, int x, int y, int state) {
		g.fill(x, y, x + 7, y + 7, 0xFF0A0706);
		g.outline(x, y, 7, 7, state == 1 ? 0xFF5FA84A : state == 2 ? 0xFFB08A3A : 0xFF6E625C);
		if (state == 1) {
			int c = DONE;
			g.fill(x + 1, y + 3, x + 2, y + 5, c);
			g.fill(x + 2, y + 4, x + 3, y + 6, c);
			g.fill(x + 3, y + 3, x + 4, y + 5, c);
			g.fill(x + 4, y + 2, x + 5, y + 4, c);
			g.fill(x + 5, y + 1, x + 6, y + 3, c);
		} else if (state == 2) {
			g.fill(x + 3, y + 3, x + 4, y + 4, 0xFFE0B050);
		}
	}

	// ---------------------------------------------------------------- data helpers

	private static ItemStack headerIcon() {
		if (headerIcon == null) {
			Item vent = LavaIds.item(LavaIds.HOTSPOT_VENT);
			headerIcon = new ItemStack(vent != null && vent != Items.AIR ? vent : Items.MAGMA_BLOCK);
		}
		return headerIcon;
	}

	private static Component offeringName(Identifier id) {
		Item item = LavaIds.item(id);
		if (item != null && item != Items.AIR) return new ItemStack(item).getHoverName();
		return Component.translatable("item." + id.getNamespace() + "." + id.getPath());
	}

	private static int safeOffered(LocalPlayer player) {
		if (HudExtras.offered == null) return -1;
		try {
			return HudExtras.offered.applyAsInt(player);
		} catch (RuntimeException | LinkageError e) {
			HudExtras.offered = null;
			LavaGenesis.LOGGER.warn("Lava Genesis HUD: offering count unavailable", e);
			return -1;
		}
	}

	private static HudExtras.@Nullable PowerRow safePower(LocalPlayer player) {
		try {
			return HudExtras.power.apply(player);
		} catch (RuntimeException | LinkageError e) {
			HudExtras.power = null;
			LavaGenesis.LOGGER.warn("Lava Genesis HUD: power row unavailable", e);
			return null;
		}
	}

	static String fmt(int n) {
		return String.format(Locale.ROOT, "%,d", n);
	}

	// ---------------------------------------------------------------- colour helpers

	/** 0 = cold grey crust, 0.35 = dark red, 0.7 = orange lava, 1 = white-yellow glow. */
	static int heatColor(float t) {
		if (t < 0.35F) return mix(0xFF4E4844, 0xFF8A2A10, t / 0.35F);
		if (t < 0.7F) return mix(0xFF8A2A10, 0xFFFF6A00, (t - 0.35F) / 0.35F);
		return mix(0xFFFF6A00, 0xFFFFD27A, (t - 0.7F) / 0.3F);
	}

	static int mix(int a, int b, float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		int aa = a >>> 24, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
			| ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	static int lighten(int argb, float t) {
		return mix(argb, argb | 0x00FFFFFF, t);
	}

	static int alpha(int argb, float a) {
		return ((int) (Mth.clamp(a, 0.0F, 1.0F) * 255) << 24) | (argb & 0x00FFFFFF);
	}
}
