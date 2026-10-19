package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

final class CompanionContextMenu {
   static final int WIDTH = 112;
   private static final int ITEM_HEIGHT = 21;
   static final int HEIGHT = 105;
   private static final int NORMAL_ITEMS = 4;
   private static final int HOUSE_ITEMS = 5;
   private CompanionContextMenu.Target target;

   boolean isOpen() {
      return this.target != null;
   }

   void open(CompanionKind kind, int index, int x, int y, boolean activeInWorld, boolean hasHome, boolean houseMode) {
      this.target = new CompanionContextMenu.Target(kind, index, x, y, activeInWorld, hasHome, houseMode);
   }

   void close() {
      this.target = null;
   }

   boolean mouseClicked(
      double mouseX,
      double mouseY,
      int button,
      CompanionContextMenu.RenameStarter renameStarter,
      CompanionContextMenu.PrimaryActionHandler primaryActionHandler,
      CompanionContextMenu.HomeActionHandler homeActionHandler,
      CompanionContextMenu.BlacklistHandler blacklistHandler,
      CompanionContextMenu.DeleteHandler deleteHandler
   ) {
      if (!this.isOpen()) {
         return false;
      }

      if (button == 0 && this.contains(mouseX, mouseY)) {
         CompanionContextMenu.Target clicked = this.target;
         this.close();
         int item = (int)((mouseY - clicked.y()) / 21.0);
         if (item == 0) {
            renameStarter.startRename(clicked.kind(), clicked.index());
         } else if (item == 1) {
            primaryActionHandler.runPrimaryAction(clicked.kind(), clicked.index(), clicked.activeInWorld());
         } else if (clicked.houseMode() && item == 2) {
            homeActionHandler.runHomeAction(
               clicked.kind(), clicked.index(), clicked.hasHome() ? CompanionContextMenu.HomeAction.CLEAR : CompanionContextMenu.HomeAction.SET
            );
         } else if (item == blacklistIndex(clicked.houseMode())) {
            blacklistHandler.blacklist(clicked.kind(), clicked.index());
         } else if (item == deleteIndex(clicked.houseMode())) {
            deleteHandler.delete(clicked.kind(), clicked.index());
         }

         return true;
      } else {
         this.close();
         return false;
      }
   }

   void render(GuiGraphicsExtractor graphics, Font font) {
      if (this.isOpen()) {
         int x = this.target.x();
         int y = this.target.y();
         int itemCount = itemCount(this.target.houseMode());
         int height = itemCount * 21;
         graphics.pose().pushMatrix();
         graphics.pose().translate(0.0F, 0.0F);
         graphics.fill(x, y, x + 112, y + height, -16777216);

         for (int i = 0; i < itemCount; i++) {
            graphics.fill(x + 2, y + 21 * i + 2, x + 112 - 2, y + 21 * (i + 1) - 1, -14669774);
         }

         graphics.text(font, Component.translatable("screen.find_me.remark"), x + 8, y + 7, -1);
         String primaryKey = this.target.activeInWorld() && this.target.hasHome()
            ? "screen.find_me.return_home"
            : (this.target.activeInWorld() ? "screen.find_me.store" : "screen.find_me.summon");
         graphics.text(font, Component.translatable(primaryKey), x + 8, y + 27, -1);
         if (this.target.houseMode()) {
            String homeKey = this.target.hasHome() ? "screen.find_me.home_clear" : "screen.find_me.home_set";
            graphics.text(font, Component.translatable(homeKey), x + 8, y + 48, -1118482);
         }

         graphics.text(
            font, Component.translatable("screen.find_me.blacklist_add"), x + 8, y + 21 * blacklistIndex(this.target.houseMode()) + 6, -1118482
         );
         graphics.text(font, Component.translatable("screen.find_me.delete"), x + 8, y + 21 * deleteIndex(this.target.houseMode()) + 6, -30584);
         graphics.pose().popMatrix();
      }
   }

   private boolean contains(double mouseX, double mouseY) {
      int x = this.target.x();
      int y = this.target.y();
      return mouseX >= x && mouseX <= x + 112 && mouseY >= y && mouseY <= y + itemCount(this.target.houseMode()) * 21;
   }

   private static int itemCount(boolean houseMode) {
      return houseMode ? 5 : 4;
   }

   private static int blacklistIndex(boolean houseMode) {
      return houseMode ? 3 : 2;
   }

   private static int deleteIndex(boolean houseMode) {
      return houseMode ? 4 : 3;
   }

   @FunctionalInterface
   interface BlacklistHandler {
      void blacklist(CompanionKind var1, int var2);
   }

   @FunctionalInterface
   interface DeleteHandler {
      void delete(CompanionKind var1, int var2);
   }

   enum HomeAction {
      SET,
      GO,
      CLEAR;
   }

   @FunctionalInterface
   interface HomeActionHandler {
      void runHomeAction(CompanionKind var1, int var2, CompanionContextMenu.HomeAction var3);
   }

   @FunctionalInterface
   interface PrimaryActionHandler {
      void runPrimaryAction(CompanionKind var1, int var2, boolean var3);
   }

   @FunctionalInterface
   interface RenameStarter {
      void startRename(CompanionKind var1, int var2);
   }

   private record Target(CompanionKind kind, int index, int x, int y, boolean activeInWorld, boolean hasHome, boolean houseMode) {
   }
}
