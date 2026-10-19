package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.VehicleCommandPacket;
import com.kuzhi.findme.network.VehicleListPacket;
import com.kuzhi.findme.network.VehicleRenamePacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class VehicleManageScreen extends FindMeScreen {
   private static final int FADE_TICKS = 6;
   private static final int COLUMN_GAP = 10;
   private static final int ROW_HEIGHT = 22;
   private static final int BOTTOM_CONTROLS_GAP = 8;
   private EditBox searchBox;
   private final CompanionRenameOverlay renameOverlay = new CompanionRenameOverlay();
   private final VehicleManageScreen.VehicleContextMenu contextMenu = new VehicleManageScreen.VehicleContextMenu();
   private VehicleManageScreen.Drag drag;
   private boolean renameWheel;
   private int wheelScroll;
   private int allScroll;
   private int openTicks;

   public VehicleManageScreen() {
      super(Component.translatable("screen.find_me.vehicle_manager"));
   }

   protected void init() {
      this.send(VehicleCommandPacket.Action.SYNC, -1);
      int panelX = this.panelX();
      int panelY = this.panelY();
      int controlsY = this.bottomControlsY(panelY);
      this.searchBox = new EditBox(
         this.font, panelX + 12, controlsY + 18, this.searchBoxWidth(panelX, panelY), 18, Component.translatable("screen.find_me.search")
      );
      this.searchBox.setMaxLength(64);
   }

   public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      float fade = this.fade(partialTick);
      ClientScreenBackground.renderDim(graphics, this.width, this.height);
      int panelX = this.panelX();
      int panelY = this.panelY();
      int panelW = this.panelWidth();
      int columnW = this.columnWidth();
      this.beginFadeTransform(graphics, panelX + panelW / 2, panelY + this.panelHeight() / 2, fade);
      graphics.fill(panelX, panelY, panelX + panelW, panelY + this.panelHeight(), fadeColor(-586149096, fade));
      graphics.text(this.font, this.title, panelX + 8, panelY + 7, fadeColor(-1, fade));
      int y = panelY + 24;
      this.renderColumn(graphics, this.font, true, panelX + 8, y, columnW, this.listHeight(), mouseX, mouseY, fade);
      this.renderColumn(graphics, this.font, false, panelX + 8 + columnW + 10, y, columnW, this.listHeight(), mouseX, mouseY, fade);
      this.renderBottomControls(graphics, panelX, panelY, mouseX, mouseY, fade);
      if (this.drag != null) {
         graphics.fill(mouseX - 42, mouseY - 9, mouseX + 42, mouseY + 9, fadeColor(-584287259, fade));
         graphics.centeredText(this.font, this.drag.name(), mouseX, mouseY - 4, fadeColor(-1, fade));
      }

      if (this.contextMenu.isOpen()) {
      }

      this.contextMenu.render(graphics, this.font);
      super.render(graphics, mouseX, mouseY, partialTick);
      graphics.pose().popMatrix();
      if (this.renameOverlay.isOpen()) {
         this.renameOverlay.render(graphics, this.font, this.width, this.height, this.panelWidth());
      }
   }

   public void tick() {
      super.tick();
      if (this.openTicks < 6) {
         this.openTicks++;
      }
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (this.renameOverlay.isOpen()) {
         return this.renameOverlay.mouseClicked(mouseX, mouseY, button);
      }

      if (this.searchBox != null && LegacyInput.mouseClicked(this.searchBox, mouseX, mouseY, button)) {
         this.searchBox.setFocused(true);
         return true;
      }

      if (this.searchBox != null && button == 0) {
         this.searchBox.setFocused(false);
      }

      if (this.contextMenu.isOpen() && this.contextMenu.mouseClicked(mouseX, mouseY, button, this::openRenameOverlay, this::sendRemove)) {
         return true;
      }

      VehicleManageScreen.Hit hit = this.hit(mouseX, mouseY);
      if (hit == null) {
         return super.mouseClicked(mouseX, mouseY, button);
      }

      List<VehicleListPacket.Entry> entries = hit.wheel() ? ClientVehicleState.wheelEntries() : ClientVehicleState.allEntries();
      if (hit.index() >= 0 && hit.index() < entries.size()) {
         VehicleListPacket.Entry entry = entries.get(hit.index());
         if (button == 1) {
            this.contextMenu.open(hit.wheel(), hit.index(), this.clampContextMenuX((int)mouseX), this.clampContextMenuY((int)mouseY));
            return true;
         } else if (button == 0) {
            this.contextMenu.close();
            this.drag = new VehicleManageScreen.Drag(hit.wheel(), hit.index(), entry.name());
            return true;
         } else {
            return super.mouseClicked(mouseX, mouseY, button);
         }
      } else {
         return true;
      }
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.drag == null) {
         return super.mouseReleased(mouseX, mouseY, button);
      }

      VehicleManageScreen.Hit target = this.hit(mouseX, mouseY);
      VehicleManageScreen.HitColumn targetColumn = this.hitColumn(mouseX, mouseY);
      if (target != null) {
         if (this.drag.wheel() && target.wheel()) {
            this.send(VehicleCommandPacket.Action.REORDER_WHEEL, target.index() * 1000 + this.drag.index());
         } else if (!this.drag.wheel() && target.wheel()) {
            this.send(VehicleCommandPacket.Action.ADD_TO_WHEEL, this.drag.index());
         } else if (this.drag.wheel() && !target.wheel()) {
            this.send(VehicleCommandPacket.Action.REMOVE_FROM_WHEEL, this.drag.index());
         }
      } else if (targetColumn != null) {
         if (!this.drag.wheel() && targetColumn.wheel()) {
            this.send(VehicleCommandPacket.Action.ADD_TO_WHEEL, this.drag.index());
         } else if (this.drag.wheel() && !targetColumn.wheel()) {
            this.send(VehicleCommandPacket.Action.REMOVE_FROM_WHEEL, this.drag.index());
         }
      }

      this.drag = null;
      return true;
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
      VehicleManageScreen.HitColumn column = this.hitColumn(mouseX, mouseY);
      if (column == null) {
         return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
      }

      int max = Math.max(0, this.filtered(column.wheel()).size() - this.visibleRows());
      if (column.wheel()) {
         this.wheelScroll = Math.max(0, Math.min(max, this.wheelScroll + (delta < 0.0 ? 1 : -1)));
      } else {
         this.allScroll = Math.max(0, Math.min(max, this.allScroll + (delta < 0.0 ? 1 : -1)));
      }

      return true;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.renameOverlay.isOpen()) {
         return this.renameOverlay.keyPressed(keyCode, scanCode, modifiers, (kind, index, name) -> {
            if (ModNetwork.channel != null) {
               ModNetwork.sendToServer(new VehicleRenamePacket(this.renameWheel, index, name));
            }
         }) || super.keyPressed(keyCode, scanCode, modifiers);
      } else if (this.searchBox != null && this.searchBox.isFocused()) {
         return LegacyInput.keyPressed(this.searchBox, keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
      } else if (keyCode == 256 && this.contextMenu.isOpen()) {
         this.contextMenu.close();
         return true;
      } else if (keyCode == 258) {
         Minecraft.getInstance().gui.setScreen(new CompanionManageScreen(CompanionKind.MOUNT));
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean charTyped(char codePoint, int modifiers) {
      if (this.renameOverlay.isOpen()) {
         return this.renameOverlay.charTyped(codePoint, modifiers);
      } else {
         return this.searchBox != null && this.searchBox.isFocused() ? LegacyInput.charTyped(this.searchBox, codePoint) : super.charTyped(codePoint, modifiers);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private void renderColumn(GuiGraphicsExtractor graphics, Font font, boolean wheel, int x, int y, int columnW, int listHeight, int mouseX, int mouseY, float fade) {
      List<VehicleListPacket.Entry> entries = wheel ? ClientVehicleState.wheelEntries() : ClientVehicleState.allEntries();
      List<Integer> filtered = this.filtered(wheel);
      int active = ClientVehicleState.activeIndex();
      graphics.text(
         font, Component.translatable(wheel ? "screen.find_me.vehicle_wheel_slots" : "screen.find_me.vehicle_storage"), x, y, fadeColor(-1, fade)
      );
      graphics.fill(x, y + 14, x + columnW, y + 14 + listHeight, fadeColor(1428169771, fade));
      if (filtered.isEmpty()) {
         graphics.centeredText(font, Component.translatable("screen.find_me.no_entries"), x + columnW / 2, y + 108, fadeColor(-11930, fade));
      }

      int scroll = wheel ? this.wheelScroll : this.allScroll;

      for (int visible = 0; visible < this.visibleRows() && visible + scroll < filtered.size(); visible++) {
         int i = filtered.get(visible + scroll);
         int rowY = y + 18 + visible * 22;
         if (rowY + 22 > y + 14 + listHeight) {
            break;
         }

         VehicleListPacket.Entry entry = entries.get(i);
         boolean hovered = this.hit(mouseX, mouseY) != null && this.hit(mouseX, mouseY).wheel() == wheel && this.hit(mouseX, mouseY).index() == i;
         boolean activeInWorld = entry.ridden() || entry.deployed();
         int bg = hovered ? -2009315755 : (activeInWorld ? -2010091585 : (wheel && i == active ? 1999478136 : 857744427));
         graphics.fill(x + 3, rowY, x + columnW - 3, rowY + 22 - 2, fadeColor(bg, fade));
         graphics.fill(x + 4, rowY, x + 8, rowY + 22 - 2, fadeColor(-10827656, fade));
         int stateX = x + columnW - 42;
         graphics.text(
            font, trimToWidth(font, i + ". " + entry.name(), Math.max(52, stateX - x - 18)), x + 12, rowY + 6, fadeColor(entry.alive() ? -1 : -5592406, fade)
         );
         graphics.text(
            font, Component.translatable(entry.deployed() ? "screen.find_me.store" : "screen.find_me.state_ready"), stateX, rowY + 6, fadeColor(-1, fade)
         );
      }
   }

   private void renderBottomControls(GuiGraphicsExtractor graphics, int panelX, int panelY, int mouseX, int mouseY, float fade) {
      int y = this.bottomControlsY(panelY);
      graphics.text(this.font, Component.translatable("screen.find_me.search"), panelX + 12, y + 4, fadeColor(-4734772, fade));
      if (this.searchBox != null) {
         this.searchBox.extractRenderState(graphics, mouseX, mouseY, 0.0F);
      }

      graphics.text(this.font, Component.translatable("screen.find_me.tab_pets"), panelX + this.panelWidth() - 90, y + 23, fadeColor(-3355444, fade));
   }

   private VehicleManageScreen.Hit hit(double mouseX, double mouseY) {
      VehicleManageScreen.HitColumn column = this.hitColumn(mouseX, mouseY);
      if (column == null) {
         return null;
      } else {
         int visible = (int)((mouseY - column.y()) / 22.0);
         if (visible >= 0 && visible < this.visibleRows()) {
            List<Integer> filtered = this.filtered(column.wheel());
            int filteredIndex = visible + (column.wheel() ? this.wheelScroll : this.allScroll);
            return filteredIndex >= 0 && filteredIndex < filtered.size() ? new VehicleManageScreen.Hit(column.wheel(), filtered.get(filteredIndex)) : null;
         } else {
            return null;
         }
      }
   }

   private VehicleManageScreen.HitColumn hitColumn(double mouseX, double mouseY) {
      VehicleManageScreen.Rect left = this.listRect(true);
      if (inside(left, mouseX, mouseY)) {
         return new VehicleManageScreen.HitColumn(true, left.y());
      }

      VehicleManageScreen.Rect right = this.listRect(false);
      return inside(right, mouseX, mouseY) ? new VehicleManageScreen.HitColumn(false, right.y()) : null;
   }

   private VehicleManageScreen.Rect listRect(boolean wheel) {
      int panelX = this.panelX();
      int x = wheel ? panelX + 11 : panelX + 11 + this.columnWidth() + 10;
      return new VehicleManageScreen.Rect(x, this.panelY() + 42, this.columnWidth() - 6, this.listHeight() - 4);
   }

   private List<Integer> filtered(boolean wheel) {
      List<VehicleListPacket.Entry> entries = wheel ? ClientVehicleState.wheelEntries() : ClientVehicleState.allEntries();
      String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
      ArrayList<Integer> result = new ArrayList<>();

      for (int i = 0; i < entries.size(); i++) {
         VehicleListPacket.Entry entry = entries.get(i);
         if (query.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(query) || entry.entityType().toLowerCase(Locale.ROOT).contains(query)) {
            result.add(i);
         }
      }

      return result;
   }

   private void send(VehicleCommandPacket.Action action, int value) {
      if (ModNetwork.channel != null) {
         ModNetwork.sendToServer(new VehicleCommandPacket(action, value));
      }
   }

   private void sendRemove(boolean wheel, int index) {
      this.send(VehicleCommandPacket.Action.REMOVE, wheel ? index : -index - 1);
   }

   private void openRenameOverlay(boolean wheel, int index) {
      List<VehicleListPacket.Entry> entries = wheel ? ClientVehicleState.wheelEntries() : ClientVehicleState.allEntries();
      String current = index >= 0 && index < entries.size() ? entries.get(index).name() : "";
      this.renameWheel = wheel;
      this.renameOverlay.open(this.font, CompanionKind.MOUNT, index, current, this.width, this.height, this.panelWidth());
   }

   private int visibleRows() {
      return (this.listHeight() - 4) / 22;
   }

   private int panelX() {
      return CompanionScreenLayout.panelX(this.width);
   }

   private int panelY() {
      return CompanionScreenLayout.panelY(this.height);
   }

   private int panelHeight() {
      return CompanionScreenLayout.panelHeight(this.height);
   }

   private int panelWidth() {
      return CompanionScreenLayout.panelWidth(this.width);
   }

   private int columnWidth() {
      return Math.max(64, (this.panelWidth() - 16 - 10) / 2);
   }

   private int listHeight() {
      return Math.max(88, this.panelHeight() - 24 - 14 - 8 - 42 - 8);
   }

   private int bottomControlsY(int panelY) {
      return panelY + 24 + 14 + this.listHeight() + 8;
   }

   private int searchBoxWidth(int panelX, int panelY) {
      return Math.max(80, this.panelWidth() - 132);
   }

   private int clampContextMenuX(int mouseX) {
      int left = this.panelX() + 4;
      int right = this.panelX() + this.panelWidth() - 112 - 4;
      return Math.max(left, Math.min(mouseX, right));
   }

   private int clampContextMenuY(int mouseY) {
      int top = this.panelY() + 4;
      int bottom = this.bottomControlsY(this.panelY()) - 42 - 8;
      if (bottom < top) {
         bottom = Math.max(top, this.height - 42 - 4);
      }

      return Math.max(top, Math.min(mouseY, bottom));
   }

   private void beginFadeTransform(GuiGraphicsExtractor graphics, int centerX, int centerY, float fade) {
      float scale = 0.97F + fade * 0.03F;
      graphics.pose().pushMatrix();
      graphics.pose().translate(centerX, centerY);
      graphics.pose().scale(scale, scale);
      graphics.pose().translate(-centerX, -centerY);
   }

   private float fade(float partialTick) {
      float progress = Math.min(6.0F, this.openTicks + partialTick) / 6.0F;
      return progress * progress * (3.0F - 2.0F * progress);
   }

   private static boolean inside(VehicleManageScreen.Rect rect, double x, double y) {
      return x >= rect.x() && x <= rect.x() + rect.w() && y >= rect.y() && y <= rect.y() + rect.h();
   }

   private static String trimToWidth(Font font, String text, int pixels) {
      return font.width(text) <= pixels ? text : font.plainSubstrByWidth(text, Math.max(8, pixels - font.width("..."))) + "...";
   }

   private static int fadeColor(int color, float fade) {
      int alpha = color >>> 24 & 0xFF;
      int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * fade)));
      return color & 16777215 | scaledAlpha << 24;
   }

   private record Drag(boolean wheel, int index, String name) {
   }

   private record Hit(boolean wheel, int index) {
   }

   private record HitColumn(boolean wheel, int y) {
   }

   private record Rect(int x, int y, int w, int h) {
   }

   private static final class VehicleContextMenu {
      static final int WIDTH = 112;
      static final int ITEM_HEIGHT = 21;
      static final int HEIGHT = 42;
      private VehicleManageScreen.VehicleContextMenu.Target target;

      boolean isOpen() {
         return this.target != null;
      }

      void open(boolean wheel, int index, int x, int y) {
         this.target = new VehicleManageScreen.VehicleContextMenu.Target(wheel, index, x, y);
      }

      void close() {
         this.target = null;
      }

      boolean mouseClicked(
         double mouseX,
         double mouseY,
         int button,
         VehicleManageScreen.VehicleContextMenu.RenameStarter renameStarter,
         VehicleManageScreen.VehicleContextMenu.DeleteHandler deleteHandler
      ) {
         if (!this.isOpen()) {
            return false;
         }

         if (button == 0 && this.contains(mouseX, mouseY)) {
            VehicleManageScreen.VehicleContextMenu.Target clicked = this.target;
            this.close();
            int item = (int)((mouseY - clicked.y()) / 21.0);
            if (item == 0) {
               renameStarter.startRename(clicked.wheel(), clicked.index());
            } else if (item == 1) {
               deleteHandler.delete(clicked.wheel(), clicked.index());
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
            graphics.pose().pushMatrix();
            graphics.pose().translate(0.0F, 0.0F);
            graphics.fill(x, y, x + 112, y + 42, -16777216);

            for (int i = 0; i < 2; i++) {
               graphics.fill(x + 2, y + 21 * i + 2, x + 112 - 2, y + 21 * (i + 1) - 1, -14669774);
            }

            graphics.text(font, Component.translatable("screen.find_me.remark"), x + 8, y + 7, -1);
            graphics.text(font, Component.translatable("screen.find_me.delete"), x + 8, y + 27, -30584);
            graphics.pose().popMatrix();
         }
      }

      private boolean contains(double mouseX, double mouseY) {
         int x = this.target.x();
         int y = this.target.y();
         return mouseX >= x && mouseX <= x + 112 && mouseY >= y && mouseY <= y + 42;
      }

      @FunctionalInterface
      interface DeleteHandler {
         void delete(boolean var1, int var2);
      }

      @FunctionalInterface
      interface RenameStarter {
         void startRename(boolean var1, int var2);
      }

      private record Target(boolean wheel, int index, int x, int y) {
      }
   }
}
