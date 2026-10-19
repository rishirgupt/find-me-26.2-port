package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.CompanionListPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class CompanionManageListView {
   private static final int ROW_HEIGHT = 22;
   private int mountScroll;
   private int companionScroll;

   void renderColumns(
      GuiGraphicsExtractor graphics, Font font, int panelX, int panelY, int columnW, int columnGap, int listHeight, int mouseX, int mouseY, float fade, String query
   ) {
      int y = panelY + 24;
      this.renderColumn(graphics, font, CompanionKind.MOUNT, panelX + 8, y, columnW, listHeight, mouseX, mouseY, fade, query);
      this.renderColumn(graphics, font, CompanionKind.COMPANION, panelX + 8 + columnW + columnGap, y, columnW, listHeight, mouseX, mouseY, fade, query);
   }

   int rowAt(CompanionKind kind, double mouseX, double mouseY, int panelX, int panelY, int columnW, int columnGap, int listHeight, String query) {
      CompanionManageListView.Rect rect = this.listRect(kind, panelX, panelY, columnW, columnGap, listHeight);
      if (!(mouseX < rect.x()) && !(mouseX > rect.x() + rect.w()) && !(mouseY < rect.y()) && !(mouseY > rect.y() + rect.h())) {
         int visible = (int)((mouseY - rect.y()) / 22.0);
         if (visible >= 0 && visible < this.visibleRows(listHeight)) {
            List<Integer> filtered = this.filteredIndices(kind, query);
            int filteredIndex = visible + this.scroll(kind);
            return filteredIndex >= 0 && filteredIndex < filtered.size() ? filtered.get(filteredIndex) : -1;
         } else {
            return -1;
         }
      } else {
         return -1;
      }
   }

   boolean isInsideList(CompanionKind kind, double mouseX, double mouseY, int panelX, int panelY, int columnW, int columnGap, int listHeight) {
      CompanionManageListView.Rect rect = this.listRect(kind, panelX, panelY, columnW, columnGap, listHeight);
      return mouseX >= rect.x() && mouseX <= rect.x() + rect.w() && mouseY >= rect.y() && mouseY <= rect.y() + rect.h();
   }

   boolean isNameColumn(CompanionKind kind, double mouseX, int row, int panelX, int panelY, int columnW, int columnGap, int listHeight) {
      CompanionManageListView.Rect rect = this.listRect(kind, panelX, panelY, columnW, columnGap, listHeight);
      int visible = row - this.scroll(kind);
      return visible >= 0 && visible < this.visibleRows(listHeight) ? mouseX >= rect.x() + 12 && mouseX <= rect.x() + 104 : false;
   }

   boolean scroll(
      CompanionKind kind, double mouseX, double mouseY, double delta, int panelX, int panelY, int columnW, int columnGap, int listHeight, String query
   ) {
      if (!this.isInsideList(kind, mouseX, mouseY, panelX, panelY, columnW, columnGap, listHeight)) {
         return false;
      }

      int max = Math.max(0, this.filteredIndices(kind, query).size() - this.visibleRows(listHeight));
      this.setScroll(kind, Math.max(0, Math.min(max, this.scroll(kind) + (delta < 0.0 ? 1 : -1))));
      return true;
   }

   private void renderColumn(
      GuiGraphicsExtractor graphics, Font font, CompanionKind kind, int x, int y, int columnW, int listHeight, int mouseX, int mouseY, float fade, String query
   ) {
      List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(kind);
      List<Integer> filtered = this.filteredIndices(kind, query);
      int active = ClientCompanionState.activeIndex(kind);
      graphics.text(
         font, Component.translatable(kind == CompanionKind.MOUNT ? "screen.find_me.mounts" : "screen.find_me.companions"), x, y, fadeColor(-1, fade)
      );
      graphics.fill(x, y + 14, x + columnW, y + 14 + listHeight, fadeColor(1428169771, fade));
      if (filtered.isEmpty()) {
         graphics.centeredText(font, Component.translatable("screen.find_me.no_entries"), x + columnW / 2, y + 108, fadeColor(-11930, fade));
      }

      int scroll = this.scroll(kind);
      int visibleRows = this.visibleRows(listHeight);

      for (int visible = 0; visible < visibleRows && visible + scroll < filtered.size(); visible++) {
         int i = filtered.get(visible + scroll);
         int rowY = y + 18 + visible * 22;
         if (rowY + 22 > y + 14 + listHeight) {
            break;
         }

         CompanionListPacket.Entry entry = entries.get(i);
         boolean hovered = this.rowAtInColumn(filtered, scroll, x, y, columnW, listHeight, mouseX, mouseY) == i;
         boolean activeInWorld = entry.ridden() || entry.deployed();
         int bg = hovered ? -2009315755 : (activeInWorld ? -2010091585 : (i == active ? 1999478136 : 857744427));
         graphics.fill(x + 3, rowY, x + columnW - 3, rowY + 22 - 2, fadeColor(bg, fade));
         graphics.fill(x + 4, rowY, x + 8, rowY + 22 - 2, fadeColor(colorFor(entry), fade));
         int color = fadeColor(entry.alive() ? -1 : (entry.loaded() ? -34953 : -5592406), fade);
         int stateX = x + columnW - 42;
         int barX = Math.max(x + 88, stateX - 42);
         graphics.text(font, trimToWidth(font, i + ". " + entry.name(), Math.max(52, barX - x - 18)), x + 12, rowY + 6, color);
         if (entry.maxHealth() > 0.0F) {
            int barY = rowY + 8;
            int barW = Math.max(24, Math.min(68, stateX - barX - 8));
            float pct = Math.max(0.0F, Math.min(1.0F, entry.health() / entry.maxHealth()));
            graphics.fill(barX, barY, barX + barW, barY + 4, fadeColor(-12902619, fade));
            graphics.fill(barX, barY, barX + Math.round(barW * pct), barY + 4, fadeColor(pct < 0.35F ? -1812649 : -12139653, fade));
         }

         MutableComponent state = Component.translatable(
            entry.alive() ? "screen.find_me.state_ready" : (entry.loaded() ? "screen.find_me.state_dead" : "screen.find_me.state_unloaded")
         );
         if (entry.hasHome()) {
            graphics.text(font, "H", stateX - 10, rowY + 6, fadeColor(-5317377, fade));
         }

         graphics.text(font, state, stateX, rowY + 6, color);
      }
   }

   private CompanionManageListView.Rect listRect(CompanionKind kind, int panelX, int panelY, int columnW, int columnGap, int listHeight) {
      int x = kind == CompanionKind.MOUNT ? panelX + 11 : panelX + 11 + columnW + columnGap;
      return new CompanionManageListView.Rect(x, panelY + 42, columnW - 6, listHeight - 4);
   }

   private int rowAtInColumn(List<Integer> filtered, int scroll, int x, int y, int columnW, int listHeight, int mouseX, int mouseY) {
      int listX = x + 3;
      int listY = y + 18;
      int listW = columnW - 6;
      int listH = listHeight - 4;
      if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
         int visible = (mouseY - listY) / 22;
         if (visible >= 0 && visible < this.visibleRows(listHeight)) {
            int filteredIndex = visible + scroll;
            return filteredIndex >= 0 && filteredIndex < filtered.size() ? filtered.get(filteredIndex) : -1;
         } else {
            return -1;
         }
      } else {
         return -1;
      }
   }

   private int visibleRows(int listHeight) {
      return (listHeight - 4) / 22;
   }

   private int scroll(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? this.mountScroll : this.companionScroll;
   }

   private void setScroll(CompanionKind kind, int value) {
      if (kind == CompanionKind.MOUNT) {
         this.mountScroll = value;
      } else {
         this.companionScroll = value;
      }
   }

   private List<Integer> filteredIndices(CompanionKind kind, String rawQuery) {
      List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(kind);
      String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
      ArrayList<Integer> filtered = new ArrayList<>();

      for (int i = 0; i < entries.size(); i++) {
         CompanionListPacket.Entry entry = entries.get(i);
         if (query.isEmpty()
            || entry.name().toLowerCase(Locale.ROOT).contains(query)
            || entry.entityType().toLowerCase(Locale.ROOT).contains(query)
            || entry.moveType().name().toLowerCase(Locale.ROOT).contains(query)) {
            filtered.add(i);
         }
      }

      return filtered;
   }

   private static int colorFor(CompanionListPacket.Entry entry) {
      return switch (entry.moveType()) {
         case FLY -> -1;
         case SWIM -> -13056828;
         case COMPANION -> -1930693;
         case WALK -> -10827656;
      };
   }

   private static String trimToWidth(Font font, String text, int pixels) {
      return font.width(text) <= pixels ? text : font.plainSubstrByWidth(text, Math.max(8, pixels - font.width("..."))) + "...";
   }

   private static int fadeColor(int color, float fade) {
      int alpha = color >>> 24 & 0xFF;
      int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * fade)));
      return color & 16777215 | scaledAlpha << 24;
   }

   private record Rect(int x, int y, int w, int h) {
   }
}
