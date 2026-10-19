package com.kuzhi.findme.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

final class ClientScreenBackground {
   private static final int DIM_COLOR = 1711276032;

   private ClientScreenBackground() {
   }

   static void renderDim(GuiGraphicsExtractor graphics, int width, int height) {
      graphics.fill(0, 0, width, height, 1711276032);
   }
}
