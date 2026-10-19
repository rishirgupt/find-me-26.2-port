package com.kuzhi.findme.client;

import com.kuzhi.findme.network.BlacklistPacket;
import java.util.List;

public final class ClientBlacklistState {
   private static List<BlacklistPacket.Entry> entries = List.of();

   private ClientBlacklistState() {
   }

   public static void update(List<BlacklistPacket.Entry> updated) {
      entries = List.copyOf(updated);
   }

   public static List<BlacklistPacket.Entry> entries() {
      return entries;
   }
}
