package com.kuzhi.findme.client;

import com.kuzhi.findme.network.SableVehiclePreviewCapturePacket;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Screenshot previews for Sable physics vehicles. Sable has no Fabric 26.2 release, so no such vehicle can
 * exist here and nothing is ever captured; callers fall back to the regular entity preview.
 */
public final class SableVehicleScreenshotPreviewCache {
   private SableVehicleScreenshotPreviewCache() {
   }

   public static void capture(SableVehiclePreviewCapturePacket packet) {
   }

   public static void delete(UUID uuid) {
   }

   public static void transfer(UUID fromUuid, UUID toUuid) {
   }

   static void tick() {
   }

   /** @return false: there is never a cached screenshot to draw */
   static boolean render(GuiGraphicsExtractor graphics, UUID uuid, int x, int y, int width, int height) {
      return false;
   }
}
