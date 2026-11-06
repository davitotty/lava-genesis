package com.lavagenesis.experience;

import com.lavagenesis.LavaGenesis;
import com.lavagenesis.core.LavaIds;
import com.lavagenesis.core.MagmaStage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.util.Prediction;

/**
 * The Lava Journal: a written guide book (a {@link WrittenBookItem} whose default {@code written_book_content} is
 * built here). Every page is translatable; the stage, power and offering pages are generated from
 * {@link MagmaStage}, so the numbers in the book always match the game.
 * <p>
 * Prose page keys are {@code book.lavagenesis.journal.<section>.<n>} (see {@link #SECTIONS});
 * tools/gen_experience.py checks that every one of them exists in lang_parts/experience.json.
 */
public final class LavaJournal {
	private LavaJournal() {}

	public static Item ITEM;

	private static final String K = "book." + LavaGenesis.MOD_ID + ".journal.";
	private static final int INK = 0x3A1A0C;          // dark brown-red, readable on the book's paper
	private static final int INK_SOFT = 0x6A4A3A;

	/** Section id -> prose page count. Order = order in the book. */
	private record Section(String id, int pages) {}

	private static final List<Section> SECTIONS = List.of(
		new Section("intro", 3),
		new Section("heat", 2),
		new Section("mass", 3),
		new Section("pressure", 2),
		new Section("islands", 3),
		new Section("stages", 1),
		new Section("offerings", 1),
		new Section("tips", 3),
		new Section("end", 1));

	/** Power ids per stage (eruption module's {@code power.lavagenesis.<id>} keys; English fallbacks here). */
	static final String[] POWER_IDS = {"flare", "lava_spit", "flow_surge", "pillow_mound", "eruption", "shield_flow",
		"hotspot_leap", "bomb_rain", "great_eruption"};
	static final String[] POWER_FALLBACK = {"Flare", "Lava Spit", "Flow Surge", "Pillow Mound", "Eruption", "Shield Flow",
		"Hotspot Leap", "Bomb Rain", "Great Eruption"};

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, LavaIds.LAVA_JOURNAL);
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new WrittenBookItem(new Item.Properties()
			.setId(key)
			.stacksTo(1)
			.rarity(Rarity.UNCOMMON)
			// built when the registries are bound, so every module's items exist and their names can be used
			.delayedComponent(DataComponents.WRITTEN_BOOK_CONTENT, registries -> content())));
	}

	public static ItemStack create() {
		return new ItemStack(ITEM);
	}

	/** Puts a journal in the player's inventory (or at their feet). */
	public static void give(ServerPlayer player) {
		ItemStack stack = create();
		if (!player.addItem(stack)) {
			player.drop(stack, false, Prediction.SERVER_ONLY);
		}
	}

	// ---------------------------------------------------------------- content

	static WrittenBookContent content() {
		List<Component> pages = build();
		List<Filterable<Component>> filterable = new ArrayList<>(pages.size());
		for (Component p : pages) filterable.add(Filterable.passThrough(p));
		// blank title -> the stack shows the translated item name; resolved = no server-side resolving needed
		return new WrittenBookContent(Filterable.passThrough(""), "The Hotspot", 0, filterable, true);
	}

	/** Pages in order. Page numbers for the table of contents are worked out while building. */
	static List<Component> build() {
		// first pass: lay out the sections to know their page numbers
		List<String> tocIds = List.of("intro", "heat", "mass", "pressure", "powers", "controls", "islands", "stages", "offerings", "tips");
		int[] tocPages = new int[tocIds.size()];

		List<Component> body = new ArrayList<>();
		int firstBodyPage = 3; // 1 = title, 2 = contents
		for (Section s : SECTIONS) {
			int idx = tocIds.indexOf(s.id());
			if (idx >= 0) tocPages[idx] = firstBodyPage + body.size();
			for (int n = 1; n <= s.pages(); n++) {
				body.add(prose(s.id(), n));
			}
			switch (s.id()) {
				case "pressure" -> {
					tocPages[tocIds.indexOf("powers")] = firstBodyPage + body.size();
					body.addAll(powerPages());
					tocPages[tocIds.indexOf("controls")] = firstBodyPage + body.size();
					body.add(controlsPage());
				}
				case "stages" -> {
					for (MagmaStage stage : MagmaStage.values()) {
						body.add(stagePage(stage));
						if (stage != MagmaStage.EMBER) body.add(stageNeedsPage(stage));
					}
				}
				case "offerings" -> body.addAll(offeringPages());
				default -> {}
			}
		}

		List<Component> pages = new ArrayList<>();
		pages.add(titlePage());
		pages.add(contentsPage(tocIds, tocPages));
		pages.addAll(body);
		return pages;
	}

	private static MutableComponent tr(String key) {
		return Component.translatable(K + key);
	}

	/** Sections whose first page starts with a bold header ({@code <section>.header}). */
	private static final List<String> HEADED = List.of("intro", "heat", "mass", "pressure", "islands", "stages", "offerings", "tips");

	private static MutableComponent prose(String section, int n) {
		String key = K + section + "." + n;
		MutableComponent text = key.equals(K + "intro.3")
			? Component.translatable(key, Component.keybind("key." + LavaGenesis.MOD_ID + ".toggle_hud").withStyle(ChatFormatting.BOLD))
			: Component.translatable(key);
		MutableComponent page = Component.empty();
		if (n == 1 && HEADED.contains(section)) {
			page.append(tr(section + ".header").withStyle(s -> s.withBold(true).withColor(0x8A1E06))).append("\n\n");
		}
		return page.append(text.withColor(INK));
	}

	private static Component titlePage() {
		return Component.empty()
			.append("\n\n")
			.append(tr("title").withStyle(s -> s.withBold(true).withColor(0x8A1E06)))
			.append("\n\n")
			.append(tr("subtitle").withStyle(s -> s.withItalic(true).withColor(INK_SOFT)))
			.append("\n\n\n\n")
			.append(tr("motto").withColor(INK));
	}

	private static Component contentsPage(List<String> ids, int[] pageNumbers) {
		MutableComponent page = Component.empty()
			.append(tr("contents").withStyle(s -> s.withBold(true).withColor(0x8A1E06)))
			.append("\n\n");
		for (int i = 0; i < ids.size(); i++) {
			int target = pageNumbers[i];
			page.append(tr("contents." + ids.get(i)).withStyle(s -> s
				.withColor(INK)
				.withUnderlined(true)
				.withClickEvent(new ClickEvent.ChangePage(target))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable(K + "contents.goto", target)))));
			page.append(Component.literal("  " + target).withColor(INK_SOFT));
			page.append("\n");
		}
		return page;
	}

	static MutableComponent powerName(int stageOrdinal) {
		return Component.translatableWithFallback("power." + LavaGenesis.MOD_ID + "." + POWER_IDS[stageOrdinal],
			POWER_FALLBACK[stageOrdinal]);
	}

	private static List<Component> powerPages() {
		List<Component> out = new ArrayList<>();
		MagmaStage[] stages = MagmaStage.values();
		for (int start = 0; start < stages.length; start += 5) {
			MutableComponent page = Component.empty();
			if (start == 0) page.append(tr("powers").withStyle(s -> s.withBold(true).withColor(0x8A1E06))).append("\n\n");
			for (int i = start; i < Math.min(start + 5, stages.length); i++) {
				MagmaStage st = stages[i];
				page.append(st.displayName().copy().withColor(dark(st.color))).append("\n");
				page.append(Component.literal("  ").append(powerName(i).withStyle(ChatFormatting.BOLD)).withColor(INK)).append("\n");
			}
			out.add(page);
		}
		return out;
	}

	private static Component controlsPage() {
		String m = "key." + LavaGenesis.MOD_ID + ".";
		return Component.empty()
			.append(tr("controls").withStyle(s -> s.withBold(true).withColor(0x8A1E06))).append("\n\n")
			.append(Component.translatable(K + "controls.use", key(m + "use_power")).withColor(INK)).append("\n\n")
			.append(Component.translatable(K + "controls.switch", key(m + "cycle_power")).withColor(INK)).append("\n\n")
			.append(Component.translatable(K + "controls.flare", key(m + "flare")).withColor(INK)).append("\n\n")
			.append(Component.translatable(K + "controls.panel", key(m + "toggle_hud")).withColor(INK)).append("\n\n")
			.append(tr("controls.more").withColor(INK_SOFT));
	}

	private static MutableComponent key(String mapping) {
		return Component.keybind(mapping).withStyle(s -> s.withBold(true).withColor(0x8A1E06));
	}

	private static Component stagePage(MagmaStage st) {
		MutableComponent page = Component.empty()
			.append(Component.translatable(K + "stage.number", st.ordinal() + 1, MagmaStage.values().length).withColor(INK_SOFT))
			.append("\n")
			.append(st.displayName().copy().withStyle(s -> s.withBold(true).withColor(dark(st.color))))
			.append("\n")
			.append(st.description().copy().withStyle(s -> s.withItalic(true).withColor(INK_SOFT)))
			.append("\n\n");
		page.append(Component.translatable(K + "stage.power", powerName(st.ordinal()).withStyle(ChatFormatting.BOLD)).withColor(INK)).append("\n");
		page.append(Component.translatable(K + "stage.gift", Component.translatableWithFallback(
			"passive." + LavaGenesis.MOD_ID + "." + st.getSerializedName() + ".title", "-").withStyle(ChatFormatting.BOLD)).withColor(INK)).append("\n");
		page.append(Component.translatable(K + "stage.size", Math.round(st.scale * 100)).withColor(INK_SOFT));
		if (st == MagmaStage.EMBER) {
			page.append("\n\n").append(tr("stage.start").withColor(INK));
		}
		return page;
	}

	/** The second page of a stage: everything it asks for. */
	private static Component stageNeedsPage(MagmaStage st) {
		MutableComponent page = Component.empty()
			.append(Component.translatable(K + "stage.needs", st.displayName().copy().withColor(dark(st.color)))
				.withStyle(s -> s.withBold(true).withColor(INK)))
			.append("\n\n");
		page.append(line(Component.translatable(K + "stage.mass", fmt(st.minMass))));
		if (st.minIslands > 0) page.append(line(Component.translatable(K + "stage.islands", st.minIslands)));
		if (st.minLargestIsland > 0) page.append(line(Component.translatable(K + "stage.largest", fmt(st.minLargestIsland))));
		if (st.offering != null && st.offeringCount > 0) {
			page.append(line(Component.translatable(K + "stage.offering", st.offeringCount, itemName(st.offering))));
		}
		if (st.needsVent) page.append(line(tr("stage.vent")));
		return page;
	}

	private static Component line(Component text) {
		return Component.literal("- ").append(text).withColor(INK).append("\n");
	}

	private static List<Component> offeringPages() {
		List<Component> out = new ArrayList<>();
		List<MagmaStage> withOffering = new ArrayList<>();
		for (MagmaStage st : MagmaStage.values()) if (st.offering != null) withOffering.add(st);
		for (int start = 0; start < withOffering.size(); start += 1) {
			MutableComponent page = Component.empty();
			for (int i = start; i < Math.min(start + 1, withOffering.size()); i++) {
				MagmaStage st = withOffering.get(i);
				Identifier off = st.offering;
				if (i > start) page.append("\n");
				page.append(itemName(off).withStyle(s -> s.withBold(true).withColor(dark(st.color)))).append("\n");
				page.append(Component.translatable(K + "offering.for", st.offeringCount, st.displayName()).withColor(INK_SOFT)).append("\n");
				page.append(Component.translatable(K + "offering." + off.getPath()).withColor(INK)).append("\n");
			}
			out.add(page);
		}
		return out;
	}

	/** The translated name of another module's item, looked up at runtime; a readable fallback if it is missing. */
	static MutableComponent itemName(Identifier id) {
		Item item = LavaIds.item(id);
		if (item != null && item != Items.AIR) {
			return Component.translatable(item.getDescriptionId());
		}
		return Component.translatableWithFallback("item." + id.getNamespace() + "." + id.getPath(), pretty(id.getPath()));
	}

	static String pretty(String path) {
		StringBuilder b = new StringBuilder();
		for (String w : path.split("_")) {
			if (w.isEmpty()) continue;
			if (!b.isEmpty()) b.append(' ');
			b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return b.toString().replace("Peles ", "Pele's ");
	}

	static String fmt(int n) {
		return String.format(Locale.ROOT, "%,d", n);
	}

	/** Stage colours are made for a dark HUD; darken them for the paper of the book. */
	static int dark(int rgb) {
		int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
		return ((int) (r * 0.72F) << 16) | ((int) (g * 0.55F) << 8) | (int) (b * 0.55F);
	}
}
