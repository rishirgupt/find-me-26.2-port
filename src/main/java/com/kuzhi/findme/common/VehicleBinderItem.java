package com.kuzhi.findme.common;

import com.kuzhi.findme.server.VehicleManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class VehicleBinderItem extends Item {
   public VehicleBinderItem(Properties properties) {
      super(properties);
   }

   public InteractionResult use(Level level, Player player, InteractionHand hand) {
      if (!level.isClientSide()) {
         player.sendOverlayMessage(Component.translatable("message.find_me.vehicle_binder_hint"));
      }

      return InteractionResult.SUCCESS;
   }

   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      Player player = context.getPlayer();
      if (level.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (player instanceof ServerPlayer serverPlayer) {
         return VehicleManager.bindVehicleAt(serverPlayer, context.getClickedPos(), context.getHand()) ? InteractionResult.CONSUME : InteractionResult.FAIL;
      } else {
         return InteractionResult.FAIL;
      }
   }
}
