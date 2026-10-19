package com.kuzhi.findme.common;

import com.kuzhi.findme.compat.Registered;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public final class ModBlocks {
   public static final Registered<Block> SMALL_HOUSE = register("small_house");

   private ModBlocks() {
   }

   private static Registered<Block> register(String name) {
      ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("find_me", name));
      Block block = new SmallHouseBlock(
         Properties.ofFullCopy(Blocks.OAK_PLANKS)
            .setId(key)
            .strength(1.5F)
            .noOcclusion()
            .isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false)
      );
      return new Registered<>(Registry.register(BuiltInRegistries.BLOCK, key, block));
   }

   public static void register() {
   }
}
