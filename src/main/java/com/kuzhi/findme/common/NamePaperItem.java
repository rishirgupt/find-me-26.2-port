package com.kuzhi.findme.common;

import com.kuzhi.findme.server.CompanionContractService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.Properties;

public class NamePaperItem extends Item {
   public NamePaperItem(Properties properties) {
      super(properties);
   }

   public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
      if (player.level().isClientSide()) {
         return InteractionResult.SUCCESS;
      }

      if (player instanceof ServerPlayer serverPlayer) {
         boolean started = CompanionContractService.start(serverPlayer, target);
         if (started) {
            player.getCooldowns().addCooldown(stack, 80);
            consumeOne(stack, player);
         }

         return started ? InteractionResult.CONSUME : InteractionResult.FAIL;
      } else {
         return InteractionResult.PASS;
      }
   }

   public static void consumeOne(ItemStack stack, Player player) {
      if (!stack.isEmpty() && !player.getAbilities().instabuild) {
         stack.shrink(1);
      }
   }

   public Component getName(ItemStack stack) {
      return Component.translatable("item.find_me.name_paper");
   }
}
