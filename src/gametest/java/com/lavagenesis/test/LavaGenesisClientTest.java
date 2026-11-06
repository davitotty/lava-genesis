package com.lavagenesis.test;

import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import com.lavagenesis.experience.ExperienceAdvancements;
import com.lavagenesis.experience.ExperienceData;
import com.lavagenesis.experience.LavaJournal;
import com.lavagenesis.experience.client.ExperienceClient;
import com.lavagenesis.experience.client.MagmaHud;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real-game client test for Lava Genesis. Creates a world, waits for the player's birth volcano (land module) or
 * carries on without it, then walks the whole life of a volcano while taking screenshots:
 * <ol>
 *   <li>the ember in its chamber (first and third person) and the HUD,</li>
 *   <li>a mass gain (server assert + client sync assert) and the Lava Journal pages,</li>
 *   <li>Flare on its key, if the eruption module registered power keys,</li>
 *   <li>every stage up to the Great Volcano, via {@code /lavagenesis stage set} when the land module provides it
 *       (else straight through {@link MagmaCore}), asserting the stage, its advancement and the client sync,</li>
 *   <li>the HUD's compact mode,</li>
 *   <li>with {@code -Dlavagenesis.makeworld=<dir>}: a fresh survival play-test world saved to that folder.</li>
 * </ol>
 * Screenshots go to {@code -Dlavagenesis.shots} (default {@code run/screenshots}); relative paths are resolved
 * against the project root.
 * Run with {@code ./gradlew runClientGameTest -Plavagenesis.shots=... -Plavagenesis.makeworld=run/saves/LavaGenesis}.
 */
@SuppressWarnings("UnstableApiUsage")
public class LavaGenesisClientTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("lavagenesis-test");
	private static final String SEED = "lava genesis";
	private static final int BIRTH_TIMEOUT = 1800;    // ticks (90 s): finding an ocean and building the volcano
	private static final int SYNC_TIMEOUT = 100;

	private Path shots;
	private int shotCounter;
	private final List<String> warnings = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		shots = projectPath(System.getProperty("lavagenesis.shots", "run/screenshots"));
		try {
			Files.createDirectories(shots);
		} catch (IOException e) {
			throw new UncheckedIOException("cannot create screenshot dir " + shots, e);
		}
		LOG.info("Lava Genesis test: screenshots -> {}", shots);
		TestInput input = ctx.getInput();
		input.resizeWindow(1280, 720);

		try (TestSingleplayerContext world = createWorld(ctx, "Lava Genesis Test")) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("time set 1000");

			boolean born = waitForBirth(ctx, server);
			world.getConnection().waitForChunksRender();
			ctx.waitTicks(40);

			assertSynced(ctx, server, "after birth");
			checkJournalGiven(ctx, server);

			// 1. the ember in its chamber: blob stages start in third person on their own (form module)
			boolean autoThird = ctx.computeOnClient((Minecraft mc) -> mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK);
			if (!autoThird) throw new AssertionError("an Ember should start in third person (FormClient.autoCamera)");
			shot(ctx, "ember_chamber");
			camera(ctx, CameraType.FIRST_PERSON);
			camera(ctx, CameraType.THIRD_PERSON_BACK);
			ctx.waitTicks(5);
			shot(ctx, "ember_body");
			camera(ctx, CameraType.FIRST_PERSON);

			// 2. a mass gain: server state, client sync, the floating "+25" on the HUD
			massGain(ctx, server);
			journalPages(ctx);

			// 3. Flare on its key, if the eruption module made power keys
			tryPowerKey(ctx, server, "ember");

			// 4. every stage
			stages(ctx, server, born);

			// 5. HUD modes
			hudModes(ctx);
		}

		String makeWorld = System.getProperty("lavagenesis.makeworld");
		if (makeWorld != null && !makeWorld.isBlank()) {
			makePlayWorld(ctx, projectPath(makeWorld));
		}

		if (warnings.isEmpty()) {
			LOG.info("Lava Genesis test passed with no warnings");
		} else {
			LOG.warn("Lava Genesis test passed with {} warning(s):", warnings.size());
			for (String w : warnings) LOG.warn("  - {}", w);
		}
	}

	// ------------------------------------------------------------------------------------------ world

	private static TestSingleplayerContext createWorld(ClientGameTestContext ctx, String name) {
		return ctx.worldBuilder()
			.setUseConsistentSettings(false)     // a real overworld: the birth volcano needs a deep ocean
			.adjustSettings(s -> {
				s.setName(name);
				s.setSeed(SEED);
				s.setGameMode(WorldCreationUiState.SelectedGameMode.SURVIVAL);
				s.setAllowCommands(true);
				s.setGenerateStructures(false);
			})
			.create();
	}

	private boolean waitForBirth(ClientGameTestContext ctx, TestServerContext server) {
		LOG.info("waiting up to {} s for the birth volcano...", BIRTH_TIMEOUT / 20);
		boolean born = waitUntil(ctx, () -> server.computeOnServer(s -> MagmaCore.get(player(s)).started()), BIRTH_TIMEOUT);
		if (born) {
			String where = server.computeOnServer(s -> player(s).blockPosition().toShortString());
			LOG.info("birth volcano ready at {}", where);
		} else {
			warn("the player was never marked started (land module's birth volcano missing?) - testing in place");
		}
		return born;
	}

	// ------------------------------------------------------------------------------------------ checks

	private void assertSynced(ClientGameTestContext ctx, TestServerContext server, String when) {
		boolean ok = waitUntil(ctx, () -> {
			MagmaData sd = server.computeOnServer(s -> MagmaCore.get(player(s)));
			MagmaData cd = ctx.computeOnClient(mc -> mc.player == null ? null : MagmaCore.get(mc.player));
			return cd != null && cd.stage() == sd.stage() && cd.mass() == sd.mass() && cd.started() == sd.started()
				&& cd.islands() == sd.islands() && cd.homeVent().equals(sd.homeVent());
		}, SYNC_TIMEOUT);
		if (!ok) {
			MagmaData sd = server.computeOnServer(s -> MagmaCore.get(player(s)));
			MagmaData cd = ctx.computeOnClient(mc -> mc.player == null ? null : MagmaCore.get(mc.player));
			throw new AssertionError("MagmaData not synced to the client " + when + ": server=" + sd + " client=" + cd);
		}
	}

	private void checkJournalGiven(ClientGameTestContext ctx, TestServerContext server) {
		boolean given = waitUntil(ctx, () -> server.computeOnServer(s -> ExperienceData.get(player(s)).journalGiven()), 200);
		check(given, "the Lava Journal was not handed out within 10 s of joining");
		boolean inInventory = server.computeOnServer(s -> player(s).getInventory().contains(new ItemStack(LavaJournal.ITEM)));
		check(inInventory, "the Lava Journal is not in the player's inventory");
		boolean root = waitUntil(ctx, () -> server.computeOnServer(s -> ExperienceAdvancements.has(player(s), ExperienceAdvancements.ROOT)), 60);
		check(root, "advancement lavagenesis:experience/root was not granted");
	}

	private void massGain(ClientGameTestContext ctx, TestServerContext server) {
		int before = server.computeOnServer(s -> MagmaCore.get(player(s)).mass());
		server.runOnServer(s -> MagmaCore.addMass(player(s), 25, "test"));
		int after = server.computeOnServer(s -> MagmaCore.get(player(s)).mass());
		check(after == before + 25, "MagmaCore.addMass(25) changed mass from " + before + " to " + after);
		boolean synced = waitUntil(ctx, () -> ctx.computeOnClient(mc -> mc.player != null && MagmaCore.get(mc.player).mass() == after), SYNC_TIMEOUT);
		check(synced, "client never saw mass " + after);
		ctx.waitTicks(6);
		shot(ctx, "hud_mass_gain");
	}

	private void journalPages(ClientGameTestContext ctx) {
		ItemStack journal = ctx.computeOnClient(mc -> {
			var inv = mc.player.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (inv.getItem(i).is(LavaJournal.ITEM)) return inv.getItem(i).copy();
			}
			return ItemStack.EMPTY;
		});
		if (journal.isEmpty()) {
			journal = LavaJournal.create();
			warn("client inventory had no journal; reading a fresh one");
		}
		WrittenBookContent content = journal.get(DataComponents.WRITTEN_BOOK_CONTENT);
		check(content != null, "the Lava Journal has no written_book_content component");
		int pages = content.pages().size();
		check(pages >= 20 && pages <= 100, "the Lava Journal has " + pages + " pages (expected 20..100)");
		LOG.info("Lava Journal: {} pages", pages);

		ItemStack book = journal;
		ctx.setScreen(() -> new BookViewScreen(BookViewScreen.BookAccess.fromItem(book)));
		ctx.waitTicks(3);
		shot(ctx, "journal_title");
		for (int page : new int[]{1, 2, 6, pages / 2, pages - 1}) {
			ctx.runOnClient(mc -> {
				if (mc.gui.screen() instanceof BookViewScreen s) s.setPage(page);
			});
			ctx.waitTicks(2);
			shot(ctx, "journal_page_" + (page + 1));
		}
		ctx.setScreen(() -> null);
		ctx.waitTicks(2);
	}

	/** Presses the eruption module's "use power" key if it exists and reports what happened on the server. */
	private void tryPowerKey(ClientGameTestContext ctx, TestServerContext server, String label) {
		List<KeyMapping> keys = ctx.computeOnClient(mc -> Arrays.stream(mc.options.keyMappings)
			.filter(k -> k.getName().startsWith("key.lavagenesis.") && k != ExperienceClient.toggleHud)
			.toList());
		if (keys.isEmpty()) {
			warn("no eruption power keys registered - power key test skipped");
			return;
		}
		LOG.info("Lava Genesis keys: {}", keys.stream().map(KeyMapping::getName).toList());
		Optional<KeyMapping> use = keys.stream()
			.filter(k -> {
				String n = k.getName().toLowerCase(Locale.ROOT);
				return n.contains("use") || n.contains("unleash") || n.contains("cast") || n.endsWith(".erupt") || n.endsWith(".power");
			})
			.findFirst();
		if (use.isEmpty()) {
			warn("no 'use power' key among " + keys.stream().map(KeyMapping::getName).toList());
			return;
		}
		server.runOnServer(s -> MagmaCore.update(player(s), d -> d.withPressure(100)));
		ctx.waitTicks(5);
		int massBefore = server.computeOnServer(s -> MagmaCore.get(player(s)).mass());
		ctx.getInput().lookAt(ctx.computeOnClient(mc -> mc.player.getYRot()), 25.0F);
		ctx.getInput().holdKeyFor(use.get(), 10);
		ctx.waitTicks(20);
		MagmaData after = server.computeOnServer(s -> MagmaCore.get(player(s)));
		LOG.info("power key {} as {}: pressure 100 -> {}, mass {} -> {}", use.get().getName(), label, after.pressure(), massBefore, after.mass());
		if (after.pressure() >= 100 && after.mass() == massBefore) {
			warn("pressing " + use.get().getName() + " as " + label + " changed neither pressure nor mass");
		}
		shot(ctx, "power_" + label);
	}

	private void stages(ClientGameTestContext ctx, TestServerContext server, boolean born) {
		boolean command = server.computeOnServer(s -> s.getCommands().getDispatcher().getRoot().getChild("lavagenesis") != null);
		if (!command) warn("/lavagenesis command missing - stages are set through MagmaCore directly");
		// creative: the big bodies can fly over the sea and nothing in the chamber can kill them mid-shot
		server.runCommand("gamemode creative @a");
		server.runCommand("effect give @a minecraft:night_vision infinite 0 true");
		int[] origin = server.computeOnServer(s -> {
			var pos = player(s).blockPosition();
			return new int[]{pos.getX(), pos.getY(), pos.getZ()};
		});

		for (MagmaStage stage : MagmaStage.values()) {
			if (stage == MagmaStage.EMBER) continue;
			LOG.info("-> stage {}", stage.getSerializedName());
			setStage(ctx, server, stage, command);
			boolean reached = waitUntil(ctx, () -> server.computeOnServer(s -> MagmaCore.get(player(s)).stage()) == stage, 100);
			check(reached, "stage " + stage.getSerializedName() + " was not reached on the server (now "
				+ server.computeOnServer(s -> MagmaCore.get(player(s)).stage().getSerializedName()) + ")");
			boolean adv = waitUntil(ctx, () -> server.computeOnServer(s -> ExperienceAdvancements.has(player(s), stage.getSerializedName())), 60);
			check(adv, "advancement lavagenesis:experience/" + stage.getSerializedName() + " was not granted");
			assertSynced(ctx, server, "at stage " + stage.getSerializedName());

			if (stage.isAboveWater()) {
				// show the grown body above the sea, over the birth volcano
				server.runOnServer(s -> {
					ServerPlayer p = player(s);
					ServerLevel level = p.level();
					int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, origin[0], origin[2]);
					double y = Math.max(top, level.getSeaLevel()) + 2 + stage.scale * 2;
					p.getAbilities().flying = true;
					p.onUpdateAbilities();
					p.teleportTo(level, origin[0] + 0.5, y, origin[2] + 0.5, Set.of(), 30.0F, 25.0F, true);
				});
				ctx.waitTicks(5);
			}

			if (stage.isFinal()) {
				camera(ctx, CameraType.THIRD_PERSON_BACK);
				ctx.waitTicks(105);                 // the finale's title arrives at ~5 s
				shot(ctx, "stage_9_great_volcano_finale_title");
				ctx.waitTicks(80);
				shot(ctx, "stage_9_great_volcano_finale_fireworks");
				ctx.waitTicks(170);
				shot(ctx, "stage_9_great_volcano_finale_end");
			} else {
				ctx.waitTicks(30);                  // title on screen, body grown
				camera(ctx, CameraType.THIRD_PERSON_BACK);
				ctx.waitTicks(10);
				shot(ctx, "stage_" + (stage.ordinal() + 1) + "_" + stage.getSerializedName());
			}
			camera(ctx, CameraType.FIRST_PERSON);

			float scale = server.computeOnServer(s -> player(s).getScale());
			if (Math.abs(scale - stage.scale) > 0.05F) {
				warn("body scale at " + stage.getSerializedName() + " is " + scale + ", MagmaStage says " + stage.scale + " (form module)");
			}
		}

		tryPowerKey(ctx, server, "great_volcano");
		int mass = server.computeOnServer(s -> MagmaCore.get(player(s)).mass());
		check(mass >= MagmaStage.GREAT_VOLCANO.minMass,
			"mass " + mass + " below the Great Volcano's " + MagmaStage.GREAT_VOLCANO.minMass);
		if (!born) warn("stage screenshots were taken without a birth volcano");
	}

	private void setStage(ClientGameTestContext ctx, TestServerContext server, MagmaStage stage, boolean command) {
		if (command) {
			server.runOnServer(s -> {
				ServerPlayer p = player(s);
				int mass = Math.max(MagmaCore.get(p).mass(), stage.minMass);
				// mass first (so the HUD shows believable numbers), then the stage itself
				s.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "lavagenesis mass set " + mass);
				s.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "lavagenesis stage set " + stage.getSerializedName());
			});
			ctx.waitTicks(2);
			if (server.computeOnServer(s -> MagmaCore.get(player(s)).stage()) == stage) return;
			warn("'/lavagenesis stage set " + stage.getSerializedName() + "' did not change the stage - using MagmaCore");
		}
		server.runOnServer(s -> MagmaCore.update(player(s), d -> d.withStage(stage).withMass(Math.max(d.mass(), stage.minMass))));
	}

	private void hudModes(ClientGameTestContext ctx) {
		TestInput input = ctx.getInput();
		MagmaHud.Mode start = MagmaHud.mode();
		input.pressKey(ExperienceClient.toggleHud);
		ctx.waitTicks(2);
		check(MagmaHud.mode() == next(start), "HUD toggle key did not cycle the panel (" + start + " -> " + MagmaHud.mode() + ")");
		shot(ctx, "hud_" + MagmaHud.mode().name().toLowerCase(Locale.ROOT));
		while (MagmaHud.mode() != start) {
			input.pressKey(ExperienceClient.toggleHud);
			ctx.waitTicks(2);
		}
	}

	private static MagmaHud.Mode next(MagmaHud.Mode m) {
		MagmaHud.Mode[] v = MagmaHud.Mode.values();
		return v[(m.ordinal() + 1) % v.length];
	}

	// ------------------------------------------------------------------------------------------ play-test world

	private void makePlayWorld(ClientGameTestContext ctx, Path target) {
		LOG.info("building the play-test world for {}", target);
		Path source;
		try (TestSingleplayerContext world = createWorld(ctx, "Lava Genesis")) {
			TestServerContext server = world.getServer();
			world.getConnection().waitForChunksRender();
			if (!waitForBirth(ctx, server)) warn("play-test world saved without a birth volcano");
			world.getConnection().waitForChunksRender();
			waitUntil(ctx, () -> server.computeOnServer(s -> ExperienceData.get(player(s)).journalGiven()), 200);
			ctx.waitTicks(60);
			shot(ctx, "playtest_world_start");
			source = world.getWorldSave().getSaveDirectory();
		}
		// never overwrite: an existing folder gets a numbered sibling
		Path dest = target;
		for (int n = 2; Files.exists(dest); n++) dest = target.resolveSibling(target.getFileName() + "-" + n);
		try {
			copyTree(source, dest);
		} catch (IOException e) {
			throw new UncheckedIOException("could not copy the play-test world to " + dest, e);
		}
		LOG.info("play-test world saved to {} (open it with ./gradlew runClient -Plavagenesis.world={})", dest, dest.getFileName());
	}

	private static void copyTree(Path from, Path to) throws IOException {
		Files.walkFileTree(from, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(to.resolve(from.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				if (!file.getFileName().toString().equals("session.lock")) {
					Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}

	// ------------------------------------------------------------------------------------------ helpers

	/**
	 * Relative paths are taken from the project root (the nearest folder with a settings.gradle above the working
	 * directory), not from the game test's own run folder, which Loom wipes before every run.
	 */
	private static Path projectPath(String value) {
		Path p = Path.of(value);
		if (p.isAbsolute()) return p.normalize();
		Path cwd = Path.of("").toAbsolutePath();
		Path dir = cwd;
		while (dir != null && !Files.exists(dir.resolve("settings.gradle"))) dir = dir.getParent();
		return (dir != null ? dir : cwd).resolve(p).normalize();
	}

	private static ServerPlayer player(MinecraftServer s) {
		List<ServerPlayer> players = s.getPlayerList().getPlayers();
		if (players.isEmpty()) throw new AssertionError("no player on the server");
		return players.getFirst();
	}

	private static boolean waitUntil(ClientGameTestContext ctx, BooleanSupplier cond, int ticks) {
		for (int i = 0; i < ticks; i++) {
			if (cond.getAsBoolean()) return true;
			ctx.waitTick();
		}
		return cond.getAsBoolean();
	}

	private static void camera(ClientGameTestContext ctx, CameraType type) {
		ctx.runOnClient((Minecraft mc) -> mc.options.setCameraType(type));
	}

	private void shot(ClientGameTestContext ctx, String name) {
		String file = String.format(Locale.ROOT, "%02d_%s", ++shotCounter, name);
		Path p = ctx.takeScreenshot(TestScreenshotOptions.of(file).withDestinationDir(shots).disableCounterPrefix());
		LOG.info("screenshot {}", p);
	}

	private void warn(String message) {
		warnings.add(message);
		LOG.warn("[lavagenesis-test] {}", message);
	}

	private static void check(boolean ok, String failure) {
		if (!ok) throw new AssertionError("[lavagenesis-test] " + failure);
	}
}
