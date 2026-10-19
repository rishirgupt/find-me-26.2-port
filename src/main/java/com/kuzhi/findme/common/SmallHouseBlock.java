package com.kuzhi.findme.common;

import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.OpenHouseScreenPacket;
import com.kuzhi.findme.server.CompanionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SmallHouseBlock extends Block {
   private static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 4.0, 14.0);

   public SmallHouseBlock(Properties properties) {
      super(properties);
   }

   public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
      return SHAPE;
   }

   protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
      if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
         ModNetwork.sendToPlayer(serverPlayer, new OpenHouseScreenPacket(pos, level.dimension().identifier()));
      }

      return InteractionResult.SUCCESS;
   }

   protected InteractionResult useItemOn(
      ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit
   ) {
      if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
         ModNetwork.sendToPlayer(serverPlayer, new OpenHouseScreenPacket(pos, level.dimension().identifier()));
      }

      return InteractionResult.SUCCESS;
   }

   protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
      CompanionManager.clearHomesForHouse(level, pos);
      super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
   }
}
