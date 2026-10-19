package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.DeadCompanionListPacket;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class CompanionDetailScreen extends FindMeScreen {
   private final Screen parent;
   private final CompanionKind kind;
   private final int index;
   private final CompanionListPacket.Entry fixedEntry;
   private final CompanionDetailPreviewRenderer previewRenderer = new CompanionDetailPreviewRenderer();
   private float yaw = 180.0F;
   private float pitch = 0.0F;
   private float zoom = 1.0F;
   private boolean draggingPreview;
   private boolean movingPreview;
   private float cameraOffsetX;
   private float cameraOffsetY;

   public CompanionDetailScreen(Screen parent, CompanionKind kind, int index) {
      super(Component.translatable("screen.find_me.details"));
      this.parent = parent;
      this.kind = kind;
      this.index = index;
      this.fixedEntry = null;
   }

   public CompanionDetailScreen(Screen parent, DeadCompanionListPacket.Entry deadEntry) {
      super(Component.translatable("screen.find_me.details"));
      this.parent = parent;
      this.kind = deadEntry.kind();
      this.index = -1;
      this.fixedEntry = new CompanionListPacket.Entry(
         deadEntry.uuid(),
         -1,
         deadEntry.entityType(),
         deadEntry.name(),
         false,
         false,
         false,
         false,
         false,
         false,
         deadEntry.health(),
         deadEntry.maxHealth(),
         deadEntry.armor(),
         deadEntry.moveType(),
         deadEntry.previewTag()
      );
   }

   public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      ClientScreenBackground.renderDim(graphics, this.width, this.height);
      CompanionListPacket.Entry entry = this.fixedEntry;
      List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(this.kind);
      if (entry != null || this.index >= 0 && this.index < entries.size()) {
         if (entry == null) {
            entry = entries.get(this.index);
         }

         int x = CompanionScreenLayout.panelX(this.width);
         int y = CompanionScreenLayout.panelY(this.height);
         int w = CompanionScreenLayout.panelWidth(this.width);
         int h = CompanionScreenLayout.panelHeight(this.height);
         graphics.fill(x, y, x + w, y + h, -267118304);
         graphics.fill(x + 1, y + 1, x + w - 1, y + 30, -14537930);
         graphics.text(this.font, this.title, x + 14, y + 11, -1);
         int previewX = x + 12;
         int previewY = y + 36;
         int previewSize = Math.max(112, Math.min(180, Math.min(h - 72, w / 2 - 24)));
         graphics.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, -15921131);
         graphics.fill(previewX, previewY + previewSize - 2, previewX + previewSize, previewY + previewSize, -13616570);
         this.renderPreview(graphics, entry, previewX, previewY, previewSize, previewSize);
         graphics.centeredText(
            this.font, Component.translatable("screen.find_me.preview_hint"), previewX + previewSize / 2, previewY + previewSize + 10, -6642510
         );
         int infoX = previewX + previewSize + 16;
         int infoY = y + 48;
         this.drawInfoLine(graphics, "screen.find_me.detail_name", entry.name(), infoX, infoY);
         this.drawInfoLine(graphics, "screen.find_me.detail_type", entry.entityType(), infoX, infoY + 18);
         this.drawInfoLine(
            graphics, "screen.find_me.detail_health", String.format(Locale.ROOT, "%.1f / %.1f", entry.health(), entry.maxHealth()), infoX, infoY + 36
         );
         this.drawInfoLine(graphics, "screen.find_me.detail_armor", String.format(Locale.ROOT, "%.0f", entry.armor()), infoX, infoY + 54);
         this.drawInfoLine(graphics, "screen.find_me.detail_move", entry.moveType().name().toLowerCase(Locale.ROOT), infoX, infoY + 72);
         graphics.text(
            this.font,
            Component.translatable("screen.find_me.detail_state", new Object[]{entry.loaded(), entry.alive(), entry.deployed()}),
            infoX,
            infoY + 96,
            -1511947
         );
         graphics.text(
            this.font,
            Component.translatable(
               "screen.find_me.detail_home",
               new Object[]{Component.translatable(entry.hasHome() ? "screen.find_me.home_state_set" : "screen.find_me.home_state_unset")}
            ),
            infoX,
            infoY + 110,
            -1511947
         );
         graphics.centeredText(this.font, Component.translatable("screen.find_me.back_esc"), x + w / 2, y + h - 18, -4734772);
         super.render(graphics, mouseX, mouseY, partialTick);
      } else {
         Minecraft.getInstance().gui.setScreen(this.parent);
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && this.isInsidePreview(mouseX, mouseY)) {
         this.draggingPreview = true;
         return true;
      } else if (button == 1 && this.isInsidePreview(mouseX, mouseY)) {
         this.movingPreview = true;
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      this.draggingPreview = false;
      this.movingPreview = false;
      return super.mouseReleased(mouseX, mouseY, button);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      if (this.draggingPreview && button == 0) {
         this.yaw += (float)dragX * 1.4F;
         this.pitch = Math.max(-70.0F, Math.min(70.0F, this.pitch + (float)dragY * 1.1F));
         return true;
      } else if (this.movingPreview && button == 1) {
         this.cameraOffsetX = Math.max(-96.0F, Math.min(96.0F, this.cameraOffsetX + (float)dragX));
         this.cameraOffsetY = Math.max(-96.0F, Math.min(96.0F, this.cameraOffsetY + (float)dragY));
         return true;
      } else {
         return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
      if (this.isInsidePreview(mouseX, mouseY)) {
         this.zoom = Math.max(0.25F, Math.min(4.0F, this.zoom + (float)delta * 0.12F));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256) {
         Minecraft.getInstance().gui.setScreen(this.parent);
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private boolean isInsidePreview(double mouseX, double mouseY) {
      int x = CompanionScreenLayout.panelX(this.width) + 12;
      int y = CompanionScreenLayout.panelY(this.height) + 36;
      int size = this.previewSize();
      return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
   }

   private void drawInfoLine(GuiGraphicsExtractor graphics, String key, String value, int x, int y) {
      graphics.text(this.font, Component.translatable(key, new Object[]{trim(value, 26)}), x, y, -1511947);
   }

   private void renderPreview(GuiGraphicsExtractor graphics, CompanionListPacket.Entry entry, int x, int y, int w, int h) {
      String fallbackType = this.kind == CompanionKind.MOUNT ? "minecraft:horse" : "minecraft:wolf";
      this.previewRenderer
         .render(graphics, entry, x, y, w, h, this.yaw, this.pitch, this.zoom, this.cameraOffsetX, this.cameraOffsetY - 2.0F, fallbackType, 112.0F, 36.0F);
   }

   private int previewSize() {
      int panelW = CompanionScreenLayout.panelWidth(this.width);
      int panelH = CompanionScreenLayout.panelHeight(this.height);
      return Math.max(112, Math.min(180, Math.min(panelH - 72, panelW / 2 - 24)));
   }

   private static String trim(String text, int max) {
      return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "...";
   }
}
