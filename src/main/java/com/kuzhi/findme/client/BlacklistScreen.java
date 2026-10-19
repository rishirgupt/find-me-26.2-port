package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.BlacklistPacket;
import com.kuzhi.findme.network.CompanionCommandPacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class BlacklistScreen extends FindMeScreen {
   private static final int ROW_HEIGHT = 22;
   private final Screen parent;
   private int scroll;

   public BlacklistScreen(Screen parent) {
      super(Component.translatable("screen.find_me.blacklist"));
      this.parent = parent;
   }

   protected void init() {
      send(CompanionAction.BLACKLIST_SYNC, -1);
   }

   public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      ClientScreenBackground.renderDim(graphics, this.width, this.height);
      int x = this.panelX();
      int y = this.panelY();
      int panelW = this.panelWidth();
      int panelH = this.panelHeight();
      graphics.fill(x, y, x + panelW, y + panelH, -300936424);
      graphics.fill(x + 1, y + 1, x + panelW - 1, y + 26, -14669774);
      graphics.text(this.font, this.title, x + 10, y + 9, -1);
      graphics.text(
         this.font, this.trimToWidth(Component.translatable("screen.find_me.blacklist_hint").getString(), panelW - 20), x + 10, y + panelH - 18, -4734772
      );
      List<BlacklistPacket.Entry> entries = ClientBlacklistState.entries();
      if (entries.isEmpty()) {
         graphics.centeredText(this.font, Component.translatable("screen.find_me.blacklist_empty"), x + panelW / 2, y + panelH / 2 - 4, -11930);
      }

      int listX = x + 10;
      int listY = y + 34;
      int listW = panelW - 20;
      int visibleRows = this.visibleRows();

      for (int visible = 0; visible < visibleRows && visible + this.scroll < entries.size(); visible++) {
         int index = visible + this.scroll;
         int rowY = listY + visible * 22;
         boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + 22 - 2;
         graphics.fill(listX, rowY, listX + listW, rowY + 22 - 2, hovered ? -2009315755 : 857744427);
         BlacklistPacket.Entry entry = entries.get(index);
         graphics.text(this.font, this.trimToWidth(index + ". " + entry.entityType(), listW - 16), listX + 8, rowY + 6, -1);
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      int row;
      if (button == 1 && (row = this.rowAt(mouseX, mouseY)) >= 0 && row < ClientBlacklistState.entries().size()) {
         send(CompanionAction.BLACKLIST_REMOVE, row);
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
      int max = Math.max(0, ClientBlacklistState.entries().size() - this.visibleRows());
      this.scroll = Math.max(0, Math.min(max, this.scroll + (delta < 0.0 ? 1 : -1)));
      return true;
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

   private int rowAt(double mouseX, double mouseY) {
      int x = this.panelX() + 10;
      int y = this.panelY() + 34;
      int w = this.panelWidth() - 20;
      if (!(mouseX < x) && !(mouseX > x + w) && !(mouseY < y) && !(mouseY > y + this.visibleRows() * 22)) {
         int visible = (int)((mouseY - y) / 22.0);
         return visible + this.scroll;
      } else {
         return -1;
      }
   }

   private int visibleRows() {
      return Math.max(3, (this.panelHeight() - 58) / 22);
   }

   private int panelX() {
      return CompanionScreenLayout.panelX(this.width);
   }

   private int panelY() {
      return CompanionScreenLayout.panelY(this.height);
   }

   private int panelWidth() {
      return CompanionScreenLayout.panelWidth(this.width);
   }

   private int panelHeight() {
      return CompanionScreenLayout.panelHeight(this.height);
   }

   private String trimToWidth(String text, int pixels) {
      return this.font.width(text) <= pixels ? text : this.font.plainSubstrByWidth(text, Math.max(8, pixels - this.font.width("..."))) + "...";
   }

   private static void send(CompanionAction action, int value) {
      if (ModNetwork.channel != null) {
         ModNetwork.sendToServer(new CompanionCommandPacket(CompanionKind.MOUNT, action, value));
      }
   }
}
