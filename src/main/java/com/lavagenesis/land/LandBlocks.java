package com.lavagenesis.land;

import com.lavagenesis.core.LavaIds;
import com.lavagenesis.land.block.CoolingLavaBlock;
import com.lavagenesis.land.block.FumaroleBlock;
import com.lavagenesis.land.block.IslandFlora;
import com.lavagenesis.land.block.IslandPlantBlock;
import com.lavagenesis.land.block.IslandSaplingBlock;
import com.lavagenesis.land.block.IslandTrees;
import com.lavagenesis.land.block.LushVolcanicSoilBlock;
import com.lavagenesis.land.block.MoltenRockBlock;
import com.lavagenesis.land.block.SulfurClusterBlock;
import com.lavagenesis.land.block.VolcanicAshBlock;
import com.lavagenesis.land.block.VolcanicSoilBlock;
import com.lavagenesis.land.block.WeatheringBasaltBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ColorRGBA;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Every land block. Hot and offering-source blocks deliberately do not require a tool for their drops: the
 * player is a creature of lava, so a bare "hand" must be able to harvest what the stages ask for.
 */
public final class LandBlocks {
	private LandBlocks() {}

	/** Blocks that get a block item, in creative-tab order. */
	static final List<Block> WITH_ITEMS = new ArrayList<>();

	// ---- lava and rock ----
	public static final Block COOLING_LAVA = register(LavaIds.COOLING_LAVA, CoolingLavaBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.FIRE).instrument(NoteBlockInstrument.BASEDRUM).strength(1.0F, 6.0F).sound(SoundType.BASALT)
		.lightLevel(CoolingLavaBlock::lightFor).emissiveRendering(s -> s.getValue(CoolingLavaBlock.AGE) <= 1)
		.isValidSpawn((s, l, p, e) -> e.fireImmune()), true);
	public static final Block MOLTEN_ROCK = register(LavaIds.MOLTEN_ROCK, MoltenRockBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.FIRE).instrument(NoteBlockInstrument.BASEDRUM).strength(1.2F, 8.0F).sound(SoundType.BASALT)
		.lightLevel(s -> 15).emissiveRendering(s -> true).isValidSpawn((s, l, p, e) -> e.fireImmune()), true);
	public static final Block FRESH_BASALT = register(LavaIds.FRESH_BASALT, p -> new WeatheringBasaltBlock(24, p),
		rock(MapColor.COLOR_BLACK, 1.25F).sound(SoundType.BASALT), true);
	public static final Block PILLOW_LAVA = register(LandIds.PILLOW_LAVA, Block::new,
		rock(MapColor.COLOR_BLACK, 1.5F).sound(SoundType.BASALT), true);
	public static final Block GLASSY_BASALT = register(LandIds.GLASSY_BASALT, Block::new,
		rock(MapColor.COLOR_BLACK, 1.0F).sound(SoundType.AMETHYST), true);
	public static final Block OLIVINE_ORE = register(LandIds.OLIVINE_ORE, p -> new DropExperienceBlock(UniformInt.of(1, 4), p),
		rock(MapColor.COLOR_BLACK, 2.0F).sound(SoundType.BASALT), true);
	public static final Block FUMAROLE = register(LandIds.FUMAROLE, FumaroleBlock::new,
		rock(MapColor.COLOR_YELLOW, 1.5F).sound(SoundType.BASALT).lightLevel(s -> 3), true);
	public static final Block SULFUR_CLUSTER = register(LandIds.SULFUR_CLUSTER, SulfurClusterBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.COLOR_YELLOW).strength(0.6F).sound(SoundType.SULFUR_SPIKE).noOcclusion()
		.pushReaction(PushReaction.POPPED), false);
	public static final Block BASALT_BRICKS = register(LandIds.BASALT_BRICKS, Block::new,
		rock(MapColor.COLOR_BLACK, 2.0F).sound(SoundType.DEEPSLATE_BRICKS).requiresCorrectToolForDrops(), true);

	// ---- sand, ash and soils ----
	public static final Block BLACK_SAND = register(LavaIds.BLACK_SAND, p -> new ColoredFallingBlock(new ColorRGBA(0xFF1C1B22), p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.SNARE).strength(0.5F)
			.sound(SoundType.SAND), true);
	public static final Block VOLCANIC_ASH = register(LandIds.VOLCANIC_ASH, p -> new VolcanicAshBlock(new ColorRGBA(0xFF77757A), p),
		BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).instrument(NoteBlockInstrument.SNARE).strength(0.5F)
			.sound(SoundType.SAND), true);
	public static final Block VOLCANIC_SOIL = register(LavaIds.VOLCANIC_SOIL, VolcanicSoilBlock::new, BlockBehaviour.Properties.of()
		.mapColor(MapColor.TERRACOTTA_BROWN).strength(0.6F).sound(SoundType.ROOTED_DIRT), true);
	public static final Block LUSH_VOLCANIC_SOIL = register(LavaIds.LUSH_VOLCANIC_SOIL, LushVolcanicSoilBlock::new,
		BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.MOSS), true);
	public static final Block BLACK_SAND_GLASS = register(LandIds.BLACK_SAND_GLASS, TransparentBlock::new, BlockBehaviour.Properties.of()
		.instrument(NoteBlockInstrument.HAT).strength(0.3F).sound(SoundType.GLASS).noOcclusion()
		.isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false)
		.isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p, box) -> false), true);

	// ---- island plants and trees ----
	public static final Block HAPUU_FERN = register(LandIds.HAPUU_FERN, p -> new IslandPlantBlock(Block.column(13.0, 0.0, 14.0),
		IslandFlora::isIslandSoil, p), plant(SoundType.GRASS).replaceable(), true);
	public static final Block SILVERSWORD = register(LandIds.SILVERSWORD, p -> new IslandPlantBlock(Block.column(12.0, 0.0, 10.0),
		IslandFlora::isCinderOrSoil, p), plant(SoundType.AZALEA), true);
	public static final Block OHIA_SAPLING = register(LandIds.OHIA_SAPLING, p -> new IslandSaplingBlock(IslandTrees::growOhia,
		IslandFlora::isCinderOrSoil, p), plant(SoundType.FLOWERING_AZALEA).randomTicks(), true);
	public static final Block PALM_SAPLING = register(LandIds.PALM_SAPLING, p -> new IslandSaplingBlock(IslandTrees::growPalm,
		IslandFlora::isBeachOrSoil, p), plant(SoundType.GRASS).randomTicks(), true);
	public static final Block OHIA_LOG = register(LandIds.OHIA_LOG, RotatedPillarBlock::new, log(MapColor.COLOR_GRAY), true);
	public static final Block PALM_LOG = register(LandIds.PALM_LOG, RotatedPillarBlock::new, log(MapColor.SAND), true);
	public static final Block OHIA_LEAVES = register(LandIds.OHIA_LEAVES,
		p -> new UntintedParticleLeavesBlock(0.02F, ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFFD8302A),
			AmbientLeavesBlockSoundPlayer.noAmbientSound(), p), leaves(SoundType.AZALEA_LEAVES), true);
	public static final Block PALM_FRONDS = register(LandIds.PALM_FRONDS,
		p -> new UntintedParticleLeavesBlock(0.005F, ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, 0xFF5E9E2E),
			AmbientLeavesBlockSoundPlayer.noAmbientSound(), p), leaves(SoundType.GRASS), true);
	public static final Block OHIA_PLANKS = register(LandIds.OHIA_PLANKS, Block::new, planks(MapColor.TERRACOTTA_RED), true);
	public static final Block PALM_PLANKS = register(LandIds.PALM_PLANKS, Block::new, planks(MapColor.SAND), true);

	static void init() {
		// class loading registers everything above
	}

	private static BlockBehaviour.Properties rock(MapColor color, float hardness) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).strength(hardness, 6.0F);
	}

	private static BlockBehaviour.Properties plant(SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().instabreak().sound(sound)
			.pushReaction(PushReaction.POPPED).ignitedByLava();
	}

	private static BlockBehaviour.Properties log(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(2.0F)
			.sound(SoundType.WOOD).ignitedByLava();
	}

	private static BlockBehaviour.Properties planks(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F)
			.sound(SoundType.WOOD).ignitedByLava();
	}

	private static BlockBehaviour.Properties leaves(SoundType sound) {
		return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(sound).noOcclusion()
			.isValidSpawn((s, l, p, e) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p, box) -> false)
			.ignitedByLava().pushReaction(PushReaction.POPPED).isRedstoneConductor((s, l, p) -> false);
	}

	private static Block register(Identifier id, Function<BlockBehaviour.Properties, Block> factory,
		BlockBehaviour.Properties props, boolean item) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
		Block block = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
		if (item) WITH_ITEMS.add(block);
		return block;
	}
}
