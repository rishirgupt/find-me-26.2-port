package com.kuzhi.findme.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class ClientCompanionDialogueRenderer {
   private ClientCompanionDialogueRenderer() {
   }

   static void renderGui(GuiGraphicsExtractor graphics, float partialTick) {
      if (ClientCompanionDialogueState.active()) {
         Minecraft minecraft = Minecraft.getInstance();
         Font font = minecraft.font;
         int width = Minecraft.getInstance().getWindow().getGuiScaledWidth();
         int y = Math.round(Minecraft.getInstance().getWindow().getGuiScaledHeight() * 0.68F);
         int maxWidth = Math.max(80, width - 64);
         int callAlpha = Math.round(ClientCompanionDialogueState.alpha(partialTick) * 255.0F);
         if (callAlpha > 0) {
            String call = fit(font, ClientCompanionDialogueState.call(), maxWidth);
            String reply = fit(font, ClientCompanionDialogueState.reply(), maxWidth);
            int replyAlpha = Math.round(ClientCompanionDialogueState.replyAlpha(partialTick) * 255.0F);
            int callColor = colorWithAlpha(ClientCompanionDialogueState.urgent() ? 9631487 : 14333951, callAlpha);
            int replyColor = colorWithAlpha(ClientCompanionDialogueState.urgent() ? 16056063 : 12121343, replyAlpha);
            if (ClientCompanionDialogueState.showingReply()) {
               drawCentered(graphics, font, call, width, y, callColor, callAlpha);
               drawCentered(graphics, font, reply, width, y + 12, replyColor, replyAlpha);
            } else {
               drawCentered(graphics, font, call, width, y, callColor, callAlpha);
            }
         }
      }
   }

   private static void drawCentered(GuiGraphicsExtractor graphics, Font font, String text, int screenWidth, int y, int color, int alpha) {
      if (!text.isBlank()) {
         int x = (screenWidth - font.width(text)) / 2;
         graphics.text(font, text, x + 1, y + 1, colorWithAlpha(0, Math.round(alpha * 0.58F)), false);
         graphics.text(font, text, x, y, color, false);
      }
   }

   private static String fit(Font font, String text, int width) {
      if (text != null && font.width(text) > width) {
         String suffix = "...";
         return font.plainSubstrByWidth(text, Math.max(1, width - font.width(suffix))) + suffix;
      } else {
         return text == null ? "" : text;
      }
   }

   private static int colorWithAlpha(int color, int alpha) {
      return Math.max(0, Math.min(255, alpha)) << 24 | color & 16777215;
   }
}
