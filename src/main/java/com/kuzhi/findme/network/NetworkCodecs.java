package com.kuzhi.findme.network;

import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

final class NetworkCodecs {
   private NetworkCodecs() {
   }

   static <T> StreamCodec<RegistryFriendlyByteBuf, T> of(final BiConsumer<T, FriendlyByteBuf> encoder, final Function<FriendlyByteBuf, T> decoder) {
      return new StreamCodec<RegistryFriendlyByteBuf, T>() {
         public T decode(RegistryFriendlyByteBuf buffer) {
            return decoder.apply(buffer);
         }

         public void encode(RegistryFriendlyByteBuf buffer, T packet) {
            encoder.accept(packet, buffer);
         }
      };
   }
}
