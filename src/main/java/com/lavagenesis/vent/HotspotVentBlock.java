package com.lavagenesis.vent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The Hotspot Vent: a glowing volcanic vent owned by one player. Unbreakable for everyone but its owner (and
 * creative players); a home vent can't be broken by its owner either - the hotspot does not move.
 */
public class HotspotVentBlock extends BaseEntityBlock {
	public HotspotVentBlock(BlockBehaviour.Properties props) {
		super(props);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new HotspotVentBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level instanceof ServerLevel ? createTickerHelper(type, VentBlocks.HOTSPOT_VENT_BE, HotspotVentBlockEntity::serverTick) : null;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
		if (player.hasInfiniteMaterials()) return super.getDestroyProgress(state, player, level, pos);
		if (level.getBlockEntity(pos) instanceof HotspotVentBlockEntity vent && vent.owner() != null) {
			if (!vent.isOwnedBy(player) || vent.isHome()) return 0.0F;
		}
		return super.getDestroyProgress(state, player, level, pos);
	}

	@Override
	public void attack(BlockState state, Level level, BlockPos pos, Player player) {
		super.attack(state, level, pos, player);
		if (level.isClientSide() || player.hasInfiniteMaterials()) return;
		if (level.getBlockEntity(pos) instanceof HotspotVentBlockEntity vent && vent.owner() != null) {
			if (!vent.isOwnedBy(player)) {
				player.sendOverlayMessage(Component.translatable("message.lavagenesis.vent.unbreakable_other", vent.ownerName()).withStyle(ChatFormatting.RED));
			} else if (vent.isHome()) {
				player.sendOverlayMessage(Component.translatable("message.lavagenesis.vent.unbreakable_home").withStyle(ChatFormatting.GOLD));
			}
		}
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level instanceof ServerLevel server && placer instanceof ServerPlayer player
			&& level.getBlockEntity(pos) instanceof HotspotVentBlockEntity vent) {
			boolean home = !VentRegistry.hasHomeVent(player);
			VentRegistry.bind(player, server, pos, vent, home);
			player.sendSystemMessage(Component.translatable(home ? "message.lavagenesis.vent.placed_home" : "message.lavagenesis.vent.placed_island")
				.withStyle(ChatFormatting.GOLD));
		}
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!(level.getBlockEntity(pos) instanceof HotspotVentBlockEntity vent)) return InteractionResult.PASS;
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			return VentProgression.offer(sp, server, pos, vent, stack) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		}
		// client: mirror the server - offering items are offered (not eaten or used), other items act normally
		return VentProgression.isAnyOffering(stack) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof HotspotVentBlockEntity vent) {
			// an unclaimed vent (placed some other way) is claimed by the first player to touch it
			if (vent.owner() == null) {
				VentRegistry.bind(sp, server, pos, vent, !VentRegistry.hasHomeVent(sp));
			}
			VentProgression.showChecklist(sp, vent);
			server.playSound(null, pos, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.6F, 0.8F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		// a vent burns whatever stands on it - except its owner and fire-immune things
		if (!level.isClientSide() && entity instanceof LivingEntity living && !entity.fireImmune()
			&& !(entity instanceof Player p && level.getBlockEntity(pos) instanceof HotspotVentBlockEntity v && v.isOwnedBy(p))) {
			living.hurtServer((ServerLevel) level, level.damageSources().hotFloor(), 1.5F);
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
		BlockPos above = pos.above();
		BlockState up = level.getBlockState(above);
		boolean water = up.getFluidState().is(FluidTags.WATER);
		boolean open = up.isAir() || water || !up.getFluidState().isEmpty();
		int stage = level.getBlockEntity(pos) instanceof HotspotVentBlockEntity v ? v.ownerStage() : 0;
		if (!open) return;
		if (water) {
			// an undersea vent: streams of bubbles and the odd dark plume
			for (int i = 0; i < 3; i++) {
				level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x + (random.nextDouble() - 0.5) * 0.6, y, z + (random.nextDouble() - 0.5) * 0.6,
					0.0, 0.2 + random.nextDouble() * 0.2, 0.0);
			}
			if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + 0.1, z, 0.0, 0.05, 0.0);
			if (random.nextInt(60) == 0) level.playLocalSound(x, y, z, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.8F, 0.6F, false);
		} else {
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.LAVA, x + (random.nextDouble() - 0.5) * 0.5, y, z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.0, 0.0);
			}
			level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.7, y + 0.05, z + (random.nextDouble() - 0.5) * 0.7,
				0.0, 0.02 + random.nextDouble() * 0.03, 0.0);
			if (random.nextInt(2) == 0) {
				level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, x + (random.nextDouble() - 0.5) * 0.4, y + 0.2,
					z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.03 + stage * 0.004, 0.0);
			}
			if (random.nextInt(40) == 0) level.playLocalSound(x, y, z, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.6F, 0.6F + random.nextFloat() * 0.4F, false);
		}
		if (random.nextInt(90) == 0) {
			level.playLocalSound(x, y, z, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.9F, 0.5F + random.nextFloat() * 0.2F, false);
		}
		if (random.nextInt(260) == 0) {
			// deep rumble of the hotspot
			level.playLocalSound(x, y, z, SoundEvents.AMBIENT_BASALT_DELTAS_MOOD.value(), SoundSource.BLOCKS, 0.7F, 0.6F, false);
		}
	}

	/** Helper for others: is this player the owner of the vent at pos (both sides, loaded chunks only). */
	public static boolean isOwner(Level level, BlockPos pos, Player player) {
		return level.getBlockEntity(pos) instanceof HotspotVentBlockEntity v && v.isOwnedBy(player);
	}
}
