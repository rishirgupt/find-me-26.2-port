package com.kuzhi.findme.client;

/** Whether the first-person hand is drawn; switched off while the contract ceremony camera is active. */
public final class HandVisibility {
   private static boolean visible = true;

   private HandVisibility() {
   }

   public static void set(boolean handVisible) {
      visible = handVisible;
   }

   public static boolean visible() {
      return visible;
   }
}
