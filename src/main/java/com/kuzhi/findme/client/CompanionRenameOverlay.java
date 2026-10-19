package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class CompanionRenameOverlay {
   private CompanionRenameOverlay.Target target;
   private EditBox input;

   boolean isOpen() {
      return this.target != null && this.input != null;
   }

   void open(Font font, CompanionKind kind, int index, String currentName, int screenWidth, int screenHeight, int panelWidth) {
      CompanionRenameOverlay.Rect inputRect = this.inputRect(screenWidth, screenHeight, panelWidth);
      this.target = new CompanionRenameOverlay.Target(kind, index);
      this.input = new EditBox(font, inputRect.x(), inputRect.y(), inputRect.w(), inputRect.h(), Component.translatable("screen.find_me.remark"));
      this.input.setMaxLength(48);
      this.input.setValue(currentName);
      this.input.setFocused(true);
   }

   boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (!this.isOpen()) {
         return false;
      }

      LegacyInput.mouseClicked(this.input, mouseX, mouseY, button);
      return true;
   }

   boolean keyPressed(int keyCode, int scanCode, int modifiers, CompanionRenameOverlay.RenameCommitter committer) {
      if (!this.isOpen()) {
         return false;
      } else if (keyCode == 257 || keyCode == 335) {
         committer.commit(this.target.kind(), this.target.index(), this.input.getValue());
         this.close();
         return true;
      } else if (keyCode == 256) {
         this.close();
         return true;
      } else {
         return LegacyInput.keyPressed(this.input, keyCode, scanCode, modifiers);
      }
   }

   boolean charTyped(char codePoint, int modifiers) {
      return this.isOpen() && LegacyInput.charTyped(this.input, codePoint);
   }

   void render(GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight, int panelWidth) {
      if (this.isOpen()) {
         CompanionRenameOverlay.Rect overlay = this.overlayRect(screenWidth, screenHeight, panelWidth);
         CompanionRenameOverlay.Rect inputRect = this.inputRect(screenWidth, screenHeight, panelWidth);
         this.layoutInput(inputRect);
         int x = overlay.x();
         int y = overlay.y();
         int w = overlay.w();
         int h = overlay.h();
         graphics.pose().pushMatrix();
         graphics.pose().translate(0.0F, 0.0F);
         graphics.fill(0, 0, screenWidth, screenHeight, -1728053248);
         graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, -586149096);
         graphics.fill(x, y, x + w, y + h, -586149096);
         graphics.fill(x + 1, y + 1, x + w - 1, y + 24, -14669774);
         graphics.text(font, Component.translatable("screen.find_me.remark"), x + 10, y + 9, -1);
         graphics.fill(inputRect.x(), inputRect.y(), inputRect.x() + inputRect.w(), inputRect.y() + inputRect.h(), -2013265920);
         this.input.extractRenderState(graphics, 0, 0, 0.0F);
         graphics.centeredText(font, Component.translatable("screen.find_me.confirm_enter"), x + w / 2, y + h - 24, -3355444);
         graphics.pose().popMatrix();
      }
   }

   private void close() {
      this.target = null;
      this.input = null;
   }

   private void layoutInput(CompanionRenameOverlay.Rect inputRect) {
      this.input.setX(inputRect.x());
      this.input.setY(inputRect.y());
      this.input.setWidth(inputRect.w());
   }

   private CompanionRenameOverlay.Rect overlayRect(int screenWidth, int screenHeight, int panelWidth) {
      int w = Math.max(220, Math.min(panelWidth - 28, 300));
      int h = 88;
      int x = screenWidth / 2 - w / 2;
      int y = Math.max(18, Math.min(screenHeight - h - 28, screenHeight / 2 - h / 2));
      return new CompanionRenameOverlay.Rect(x, y, w, h);
   }

   private CompanionRenameOverlay.Rect inputRect(int screenWidth, int screenHeight, int panelWidth) {
      CompanionRenameOverlay.Rect overlay = this.overlayRect(screenWidth, screenHeight, panelWidth);
      int margin = Math.max(16, Math.min(30, overlay.w() / 8));
      return new CompanionRenameOverlay.Rect(overlay.x() + margin, overlay.y() + 38, overlay.w() - margin * 2, 18);
   }

   private record Rect(int x, int y, int w, int h) {
   }

   @FunctionalInterface
   interface RenameCommitter {
      void commit(CompanionKind var1, int var2, String var3);
   }

   private record Target(CompanionKind kind, int index) {
   }
}
