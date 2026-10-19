package com.kuzhi.findme.common;

import com.kuzhi.findme.compat.Registered;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;

public final class ModItems {
   public static final Registered<Item> VEHICLE_BINDER = register("vehicle_binder", properties -> new VehicleBinderItem(properties.stacksTo(16)));
   public static final Registered<Item> NAME_PAPER = register("name_paper", properties -> new NamePaperItem(properties.stacksTo(16)));
   public static final Registered<BlockItem> SMALL_HOUSE = register(
      "small_house", properties -> new BlockItem(ModBlocks.SMALL_HOUSE.get(), properties.useBlockDescriptionPrefix())
   );

   private ModItems() {
   }

   private static <T extends Item> Registered<T> register(String name, Function<Properties, T> factory) {
      ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("find_me", name));
      return new Registered<>(Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Properties().setId(key))));
   }

   public static void register() {
      CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
         output.accept(VEHICLE_BINDER.get());
         output.accept(NAME_PAPER.get());
         output.accept(SMALL_HOUSE.get());
      });
   }
}
