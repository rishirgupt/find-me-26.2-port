package com.kuzhi.findme.compat;

import java.util.concurrent.Executor;
import net.minecraft.server.level.ServerPlayer;

/** The packet-handling context the packets were written against, backed by Fabric networking. */
public final class NetworkEvent {
   private NetworkEvent() {
   }

   public static final class Context {
      private final ServerPlayer sender;
      private final Executor executor;

      private Context(ServerPlayer sender, Executor executor) {
         this.sender = sender;
         this.executor = executor;
      }

      /** A packet received on the server from {@code sender}. */
      public static NetworkEvent.Context ofServer(ServerPlayer sender, Executor executor) {
         return new NetworkEvent.Context(sender, executor);
      }

      /** A packet received on the client. */
      public static NetworkEvent.Context ofClient(Executor executor) {
         return new NetworkEvent.Context(null, executor);
      }

      public ServerPlayer getSender() {
         return this.sender;
      }

      public void enqueueWork(Runnable task) {
         this.executor.execute(task);
      }

      public void setPacketHandled(boolean handled) {
      }
   }
}
