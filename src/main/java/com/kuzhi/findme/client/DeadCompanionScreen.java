package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.CompanionCommandPacket;
import com.kuzhi.findme.network.DeadCompanionListPacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DeadCompanionScreen extends FindMeScreen {
   private static final int ROW_HEIGHT = 22;
   private final Screen parent;
   private int scroll;

   public DeadCompanionScreen(Screen parent) {
      super(Component.translatable("screen.find_me.dead_list"));
      this.parent = parent;
   }

   protected void init() {
      this.send(CompanionAction.DEAD_SYNC, -1);
   }

   public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      ClientScreenBackground.renderDim(graphics, this.width, this.height);
      int panelW = CompanionScreenLayout.panelWidth(this.width);
      int visibleRows = this.visibleRows();
      int panelH = CompanionScreenLayout.panelHeight(this.height);
      int x = CompanionScreenLayout.panelX(this.width);
      int y = CompanionScreenLayout.panelY(this.height);
      graphics.fill(x, y, x + panelW, y + panelH, -586149096);
      graphics.text(this.font, this.title, x + 10, y + 10, -1);
      List<DeadCompanionListPacket.Entry> entries = ClientCompanionState.deadEntries();
      if (entries.isEmpty()) {
         graphics.centeredText(this.font, Component.translatable("screen.find_me.dead_empty"), x + panelW / 2, y + panelH / 2 - 4, -11930);
      }

      int listX = x + 10;
      int listY = y + 32;
      int listW = panelW - 20;

      for (int visible = 0; visible < visibleRows && visible + this.scroll < entries.size(); visible++) {
         int index = visible + this.scroll;
         DeadCompanionListPacket.Entry entry = entries.get(index);
         int rowY = listY + visible * 22;
         boolean hovered = mouseX >= listX && mouseX <= listX + listW && mouseY >= rowY && mouseY <= rowY + 22 - 2;
         graphics.fill(listX, rowY, listX + listW, rowY + 22 - 2, hovered ? 1429157439 : 857481770);
         graphics.fill(listX + 2, rowY, listX + 6, rowY + 22 - 2, -2005697660);
         graphics.text(this.font, this.trim(index + ". " + entry.name(), listW - 84), listX + 12, rowY + 6, -5592406);
         graphics.text(this.font, Component.translatable("screen.find_me.state_dead"), listX + listW - 48, rowY + 6, -5592406);
      }

      super.render(graphics, mouseX, mouseY, partialTick);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      int row = this.rowAt(mouseX, mouseY);
      List<DeadCompanionListPacket.Entry> entries = ClientCompanionState.deadEntries();
      if (row >= 0 && row < entries.size()) {
         if (button == 0) {
            Minecraft.getInstance().gui.setScreen(new CompanionDetailScreen(this, entries.get(row)));
            return true;
         }

         if (button == 1) {
            this.send(CompanionAction.DEAD_REMOVE, row);
            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
      int max = Math.max(0, ClientCompanionState.deadEntries().size() - this.visibleRows());
      this.scroll = Math.max(0, Math.min(max, this.scroll - (int)Math.signum(delta)));
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
      int panelW = CompanionScreenLayout.panelWidth(this.width);
      int visibleRows = this.visibleRows();
      int x = CompanionScreenLayout.panelX(this.width) + 10;
      int y = CompanionScreenLayout.panelY(this.height) + 32;
      int w = panelW - 20;
      if (!(mouseX < x) && !(mouseX > x + w) && !(mouseY < y)) {
         int visible = ((int)mouseY - y) / 22;
         return visible >= 0 && visible < visibleRows ? visible + this.scroll : -1;
      } else {
         return -1;
      }
   }

   private int visibleRows() {
      return Math.max(3, (CompanionScreenLayout.panelHeight(this.height) - 58) / 22);
   }

   private String trim(String text, int pixels) {
      return this.font.width(text) <= pixels ? text : this.font.plainSubstrByWidth(text, Math.max(8, pixels - this.font.width("..."))) + "...";
   }

   private void send(CompanionAction action, int value) {
      if (ModNetwork.channel != null) {
         ModNetwork.sendToServer(new CompanionCommandPacket(CompanionKind.COMPANION, action, value));
      }
   }
}
