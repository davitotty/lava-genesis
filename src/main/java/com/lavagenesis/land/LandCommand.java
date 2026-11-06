package com.lavagenesis.land;

import com.lavagenesis.core.MagmaCore;
import com.lavagenesis.core.MagmaData;
import com.lavagenesis.core.MagmaStage;
import com.lavagenesis.land.birth.BirthSites;
import com.lavagenesis.land.birth.BirthVolcano;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lavagenesis} - admin and debug command (permission level 2). Every sub-command acts on the caller, or on
 * a named player given as the last argument:
 * <pre>
 * /lavagenesis info [player]
 * /lavagenesis stage set &lt;stage&gt; [player]
 * /lavagenesis mass add|set &lt;n&gt; [player]
 * /lavagenesis heat set &lt;0..1&gt; [player]
 * /lavagenesis pressure set &lt;0..100&gt; [player]
 * /lavagenesis rebuild [player]        - build a fresh birth volcano and move the player into it
 * /lavagenesis tp home [player]        - teleport to the home vent
 * </pre>
 */
public final class LandCommand {
	private LandCommand() {}

	private static final DynamicCommandExceptionType UNKNOWN_STAGE = new DynamicCommandExceptionType(
		s -> Component.translatable("commands.lavagenesis.stage.unknown", s));

	@FunctionalInterface
	private interface Action {
		int run(CommandContext<CommandSourceStack> c, ServerPlayer target) throws CommandSyntaxException;
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("lavagenesis")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(withTarget(Commands.literal("info"), LandCommand::info))
			.then(Commands.literal("stage").then(Commands.literal("set").then(withTarget(
				Commands.argument("stage", StringArgumentType.word()).suggests((c, b) ->
					SharedSuggestionProvider.suggest(Arrays.stream(MagmaStage.values()).map(MagmaStage::getSerializedName), b)),
				LandCommand::setStage))))
			.then(Commands.literal("mass")
				.then(Commands.literal("add").then(withTarget(Commands.argument("amount", IntegerArgumentType.integer(1, 1_000_000)),
					LandCommand::addMass)))
				.then(Commands.literal("set").then(withTarget(Commands.argument("amount", IntegerArgumentType.integer(0, 1_000_000)),
					LandCommand::setMass))))
			.then(Commands.literal("heat").then(Commands.literal("set").then(withTarget(
				Commands.argument("value", FloatArgumentType.floatArg(0.0F, 1.0F)), LandCommand::setHeat))))
			.then(Commands.literal("pressure").then(Commands.literal("set").then(withTarget(
				Commands.argument("value", IntegerArgumentType.integer(0, 100)), LandCommand::setPressure))))
			.then(withTarget(Commands.literal("rebuild"), LandCommand::rebuild))
			.then(Commands.literal("tp").then(withTarget(Commands.literal("home"), LandCommand::tpHome))));
	}

	/** Adds "executes on self" to the node, plus a [player] argument that executes on that player. */
	private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withTarget(T node, Action action) {
		return node
			.executes(c -> action.run(c, c.getSource().getPlayerOrException()))
			.then(Commands.argument("player", EntityArgument.player())
				.executes(c -> action.run(c, EntityArgument.getPlayer(c, "player"))));
	}

	private static int info(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		MagmaData d = MagmaCore.get(p);
		CommandSourceStack src = c.getSource();
		MagmaStage next = d.stage().next();
		src.sendSuccess(() -> Component.translatable("commands.lavagenesis.info.header", p.getDisplayName(),
			d.stage().displayName().copy().withColor(d.stage().color)).withStyle(ChatFormatting.GOLD), false);
		src.sendSuccess(() -> next == null
			? Component.translatable("commands.lavagenesis.info.mass_final", d.mass())
			: Component.translatable("commands.lavagenesis.info.mass", d.mass(), next.minMass, next.displayName()), false);
		src.sendSuccess(() -> Component.translatable("commands.lavagenesis.info.heat",
			String.format("%.2f", d.heat()), d.pressure()), false);
		src.sendSuccess(() -> Component.translatable("commands.lavagenesis.info.islands",
			d.islands(), d.largestIsland(), d.totalLand()), false);
		src.sendSuccess(() -> d.homeVent().map(v -> (Component) Component.translatable("commands.lavagenesis.info.vent",
				pos(v.pos()), v.dimension().identifier().toString()))
			.orElse(Component.translatable("commands.lavagenesis.info.no_vent")), false);
		Optional<BirthSites.Site> site = BirthSites.get(src.getServer().overworld()).latestFor(p.getUUID());
		src.sendSuccess(() -> BirthVolcano.isPending(p.getUUID())
			? Component.translatable("commands.lavagenesis.info.birth_pending")
			: site.map(s -> (Component) Component.translatable("commands.lavagenesis.info.birth", pos(s.center())))
				.orElse(Component.translatable(d.started() ? "commands.lavagenesis.info.birth_unknown"
					: "commands.lavagenesis.info.birth_none")), false);
		return d.mass();
	}

	private static int setStage(CommandContext<CommandSourceStack> c, ServerPlayer p) throws CommandSyntaxException {
		String name = StringArgumentType.getString(c, "stage");
		MagmaStage stage = Arrays.stream(MagmaStage.values()).filter(s -> s.getSerializedName().equals(name)).findFirst()
			.orElseThrow(() -> UNKNOWN_STAGE.create(name));
		MagmaCore.update(p, d -> d.withStage(stage));
		c.getSource().sendSuccess(() -> Component.translatable("commands.lavagenesis.stage.set", p.getDisplayName(),
			stage.displayName().copy().withColor(stage.color)), true);
		return stage.ordinal();
	}

	private static int addMass(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		int n = IntegerArgumentType.getInteger(c, "amount");
		MagmaCore.addMass(p, n, "command");
		int total = MagmaCore.get(p).mass();
		c.getSource().sendSuccess(() -> Component.translatable("commands.lavagenesis.mass.add", n, p.getDisplayName(), total), true);
		return total;
	}

	private static int setMass(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		int n = IntegerArgumentType.getInteger(c, "amount");
		MagmaCore.update(p, d -> d.withMass(n));
		c.getSource().sendSuccess(() -> Component.translatable("commands.lavagenesis.mass.set", p.getDisplayName(), n), true);
		return n;
	}

	private static int setHeat(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		float v = FloatArgumentType.getFloat(c, "value");
		MagmaCore.update(p, d -> d.withHeat(v));
		c.getSource().sendSuccess(() -> Component.translatable("commands.lavagenesis.heat.set", p.getDisplayName(),
			String.format("%.2f", v)), true);
		return Math.round(v * 100);
	}

	private static int setPressure(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		int v = IntegerArgumentType.getInteger(c, "value");
		MagmaCore.update(p, d -> d.withPressure(v));
		c.getSource().sendSuccess(() -> Component.translatable("commands.lavagenesis.pressure.set", p.getDisplayName(), v), true);
		return v;
	}

	private static int rebuild(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		if (!BirthVolcano.start(p, true)) {
			c.getSource().sendFailure(Component.translatable("commands.lavagenesis.rebuild.busy", p.getDisplayName()));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("commands.lavagenesis.rebuild.started", p.getDisplayName()), true);
		return 1;
	}

	private static int tpHome(CommandContext<CommandSourceStack> c, ServerPlayer p) {
		CommandSourceStack src = c.getSource();
		Optional<GlobalPos> vent = MagmaCore.get(p).homeVent();
		ServerLevel level;
		BlockPos at;
		if (vent.isPresent() && src.getServer().getLevel(vent.get().dimension()) != null) {
			level = src.getServer().getLevel(vent.get().dimension());
			at = vent.get().pos();
		} else {
			Optional<BirthSites.Site> site = BirthSites.get(src.getServer().overworld()).latestFor(p.getUUID());
			if (site.isEmpty()) {
				src.sendFailure(Component.translatable("commands.lavagenesis.tp.no_home", p.getDisplayName()));
				return 0;
			}
			level = src.getServer().overworld();
			at = site.get().vent();
		}
		p.teleportTo(level, at.getX() + 0.5, at.getY() + 1.05, at.getZ() + 0.5, Set.of(), p.getYRot(), p.getXRot(), true);
		p.resetFallDistance();
		src.sendSuccess(() -> Component.translatable("commands.lavagenesis.tp.home", p.getDisplayName(), pos(at)), true);
		return 1;
	}

	private static MutableComponent pos(BlockPos p) {
		return Component.literal(p.getX() + " " + p.getY() + " " + p.getZ()).withStyle(ChatFormatting.AQUA);
	}
}
