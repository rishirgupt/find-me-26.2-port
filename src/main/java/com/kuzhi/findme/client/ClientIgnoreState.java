package com.kuzhi.findme.client;

import com.kuzhi.findme.network.IgnoreListPacket;
import java.util.List;

public final class ClientIgnoreState {
   private static List<IgnoreListPacket.Entry> entries = List.of();

   private ClientIgnoreState() {
   }

   public static void update(List<IgnoreListPacket.Entry> updated) {
      entries = List.copyOf(updated);
   }

   public static List<IgnoreListPacket.Entry> entries() {
      return entries;
   }
}
