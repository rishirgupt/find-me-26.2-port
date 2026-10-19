package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.CompanionCommandPacket;
import com.kuzhi.findme.network.CompanionHouseHomePacket;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.CompanionRenamePacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public class CompanionManageScreen extends FindMeScreen {
   private static final int FADE_TICKS = 6;
   private static final int COLUMN_GAP = 10;
   private static final int BOTTOM_CONTROLS_GAP = 8;
   private static final int BOTTOM_CONTROLS_HEIGHT = 42;
   private CompanionManageScreen.Drag drag;
   private CompanionManageScreen.PendingClick pendingClick;
   private double pendingClickX;
   private double pendingClickY;
   private EditBox searchBox;
   private final CompanionManageListView listView = new CompanionManageListView();
   private final CompanionRenameOverlay renameOverlay = new CompanionRenameOverlay();
   private final CompanionContextMenu contextMenu = new CompanionContextMenu();
   private Component notice;
   private int noticeTicks;
   private int openTicks;
   private final BlockPos housePos;
   private final ResourceKey<Level> houseDimension;

   public CompanionManageScreen(CompanionKind initialKind) {
      super(Component.translatable("screen.find_me.manager"));
      this.housePos = null;
      this.houseDimension = null;
   }

   public CompanionManageScreen(CompanionKind initialKind, BlockPos housePos, ResourceKey<Level> houseDimension) {
      super(Component.translatable("screen.find_me.small_house"));
      this.housePos = housePos;
      this.houseDimension = houseDimension;
   }

   protected void init() {
      this.send(CompanionKind.MOUNT, CompanionAction.SYNC, -1);
      this.send(CompanionKind.COMPANION, CompanionAction.SYNC, -1);
      this.send(CompanionKind.COMPANION, CompanionAction.DEAD_SYNC, -1);
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
      graphics.fill(panelX, panelY, panelX + panelW, panelY + this.panelHeight(), this.fadeColor(-586149096, fade));
      graphics.text(this.font, this.title, panelX + 8, panelY + 7, this.fadeColor(-1, fade));
      if (this.notice != null && this.noticeTicks > 0) {
         graphics.centeredText(this.font, this.notice, panelX + panelW / 2, panelY + 8, this.fadeColor(-11930, fade));
      }

      this.listView.renderColumns(graphics, this.font, panelX, panelY, columnW, 10, this.listHeight(), mouseX, mouseY, fade, this.searchQuery());
      this.renderBottomControls(graphics, panelX, panelY, mouseX, mouseY, fade);
      if (this.drag != null) {
         graphics.fill(mouseX - 42, mouseY - 9, mouseX + 42, mouseY + 9, this.fadeColor(-584287259, fade));
         graphics.centeredText(this.font, this.drag.name(), mouseX, mouseY - 4, this.fadeColor(-1, fade));
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

   private void renderBottomControls(GuiGraphicsExtractor graphics, int panelX, int panelY, int mouseX, int mouseY, float fade) {
      int y = this.bottomControlsY(panelY);
      graphics.text(this.font, Component.translatable("screen.find_me.search"), panelX + 12, y + 4, this.fadeColor(-4734772, fade));
      if (this.searchBox != null) {
         this.searchBox.extractRenderState(graphics, mouseX, mouseY, 0.0F);
      }

      CompanionManageScreen.Rect blacklistButton = this.blacklistButtonRect(panelX, panelY);
      boolean blacklistHovered = mouseX >= blacklistButton.x()
         && mouseX <= blacklistButton.x() + blacklistButton.w()
         && mouseY >= blacklistButton.y()
         && mouseY <= blacklistButton.y() + blacklistButton.h();
      graphics.fill(
         blacklistButton.x(),
         blacklistButton.y(),
         blacklistButton.x() + blacklistButton.w(),
         blacklistButton.y() + blacklistButton.h(),
         this.fadeColor(blacklistHovered ? 1715481120 : 1294672157, fade)
      );
      graphics.centeredText(
         this.font,
         Component.translatable("screen.find_me.blacklist"),
         blacklistButton.x() + blacklistButton.w() / 2,
         blacklistButton.y() + 6,
         this.fadeColor(-1118482, fade)
      );
      CompanionManageScreen.Rect button;
      boolean hovered = mouseX >= (button = this.ignoreButtonRect(panelX, panelY)).x()
         && mouseX <= button.x() + button.w()
         && mouseY >= button.y()
         && mouseY <= button.y() + button.h();
      graphics.fill(button.x(), button.y(), button.x() + button.w(), button.y() + button.h(), this.fadeColor(hovered ? -13418156 : -14669774, fade));
      graphics.centeredText(
         this.font, Component.translatable("screen.find_me.ignore_list"), button.x() + button.w() / 2, button.y() + 6, this.fadeColor(-1, fade)
      );
      CompanionManageScreen.Rect deadButton = this.deadButtonRect(panelX, panelY);
      boolean deadHovered = mouseX >= deadButton.x()
         && mouseX <= deadButton.x() + deadButton.w()
         && mouseY >= deadButton.y()
         && mouseY <= deadButton.y() + deadButton.h();
      graphics.fill(
         deadButton.x(),
         deadButton.y(),
         deadButton.x() + deadButton.w(),
         deadButton.y() + deadButton.h(),
         this.fadeColor(deadHovered ? 1714824242 : 1294605092, fade)
      );
      graphics.centeredText(
         this.font, Component.translatable("screen.find_me.dead_list"), deadButton.x() + deadButton.w() / 2, deadButton.y() + 6, this.fadeColor(-1118482, fade)
      );
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

      int panelX = this.panelX();
      int panelY = this.panelY();
      CompanionManageScreen.Rect ignoreButton = this.ignoreButtonRect(panelX, panelY);
      CompanionManageScreen.Rect blacklistButton = this.blacklistButtonRect(panelX, panelY);
      CompanionManageScreen.Rect deadButton = this.deadButtonRect(panelX, panelY);
      if (button == 0
         && mouseX >= deadButton.x()
         && mouseX <= deadButton.x() + deadButton.w()
         && mouseY >= deadButton.y()
         && mouseY <= deadButton.y() + deadButton.h()) {
         Minecraft.getInstance().gui.setScreen(new DeadCompanionScreen(this));
         return true;
      }

      if (button == 0
         && mouseX >= blacklistButton.x()
         && mouseX <= blacklistButton.x() + blacklistButton.w()
         && mouseY >= blacklistButton.y()
         && mouseY <= blacklistButton.y() + blacklistButton.h()) {
         Minecraft.getInstance().gui.setScreen(new BlacklistScreen(this));
         return true;
      }

      if (button == 0
         && mouseX >= ignoreButton.x()
         && mouseX <= ignoreButton.x() + ignoreButton.w()
         && mouseY >= ignoreButton.y()
         && mouseY <= ignoreButton.y() + ignoreButton.h()) {
         Minecraft.getInstance().gui.setScreen(new IgnoreListScreen(this));
         return true;
      }

      if (this.contextMenu.isOpen()
         && this.contextMenu
            .mouseClicked(mouseX, mouseY, button, this::openRenameOverlay, this::sendPrimaryAction, this::sendHomeAction, this::sendBlacklist, this::sendRemove)
         )
       {
         return true;
      }

      if (button == 1) {
         for (CompanionKind kind : CompanionKind.values()) {
            int row = this.rowAt(kind, mouseX, mouseY);
            List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(kind);
            if (row >= 0 && row < entries.size()) {
               int menuX = this.clampContextMenuX((int)mouseX);
               int menuY = this.clampContextMenuY((int)mouseY, panelY);
               CompanionListPacket.Entry entry = entries.get(row);
               this.contextMenu.open(kind, row, menuX, menuY, isActiveInWorld(entry), entry.hasHome(), this.isHouseMode());
               return true;
            }
         }
      }

      for (CompanionKind kind : CompanionKind.values()) {
         int row = this.rowAt(kind, mouseX, mouseY);
         List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(kind);
         if (row >= 0 && row < entries.size()) {
            if (button == 0 && this.isNameColumn(kind, mouseX, row)) {
               this.pendingClick = new CompanionManageScreen.PendingClick(kind, row);
               this.pendingClickX = mouseX;
               this.pendingClickY = mouseY;
               this.drag = null;
               return true;
            }

            this.drag = new CompanionManageScreen.Drag(kind, row, entries.get(row).name());
            return true;
         }
      }

      return super.mouseClicked(mouseX, mouseY, button);
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      if (this.pendingClick != null) {
         Minecraft.getInstance().gui.setScreen(new CompanionDetailScreen(this, this.pendingClick.kind(), this.pendingClick.index()));
         this.pendingClick = null;
         return true;
      }

      if (this.drag == null) {
         return super.mouseReleased(mouseX, mouseY, button);
      }

      CompanionKind source = this.drag.kind();
      CompanionKind target = source == CompanionKind.MOUNT ? CompanionKind.COMPANION : CompanionKind.MOUNT;
      if (this.isInsideList(target, mouseX, mouseY)) {
         List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(source);
         if (this.drag.index() >= 0 && this.drag.index() < entries.size() && isActiveInWorld(entries.get(this.drag.index()))) {
            this.showNotice(Component.translatable("screen.find_me.cannot_move_active"));
            this.drag = null;
            return true;
         } else {
            this.send(source, CompanionAction.MOVE_OTHER, this.drag.index());
            this.drag = null;
            return true;
         }
      } else {
         int targetRow;
         if (this.isInsideList(source, mouseX, mouseY) && (targetRow = this.rowAt(source, mouseX, mouseY)) >= 0) {
            this.send(source, CompanionAction.REORDER, targetRow * 1000 + this.drag.index());
            this.drag = null;
            return true;
         } else {
            this.drag = null;
            return super.mouseReleased(mouseX, mouseY, button);
         }
      }
   }

   public void tick() {
      super.tick();
      if (this.openTicks < 6) {
         this.openTicks++;
      }

      if (this.noticeTicks > 0) {
         this.noticeTicks--;
         if (this.noticeTicks == 0) {
            this.notice = null;
         }
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
      for (CompanionKind kind : CompanionKind.values()) {
         if (this.scrollList(kind, mouseX, mouseY, delta)) {
            return true;
         }
      }

      return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      double dy;
      double dx;
      if (this.pendingClick != null && button == 0 && (dx = mouseX - this.pendingClickX) * dx + (dy = mouseY - this.pendingClickY) * dy > 16.0) {
         List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(this.pendingClick.kind());
         if (this.pendingClick.index() >= 0 && this.pendingClick.index() < entries.size()) {
            this.drag = new CompanionManageScreen.Drag(this.pendingClick.kind(), this.pendingClick.index(), entries.get(this.pendingClick.index()).name());
         }

         this.pendingClick = null;
         return true;
      } else {
         return this.drag != null || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.renameOverlay.isOpen()) {
         return this.renameOverlay.keyPressed(keyCode, scanCode, modifiers, this::sendRename) || super.keyPressed(keyCode, scanCode, modifiers);
      } else if (this.searchBox != null && this.searchBox.isFocused()) {
         return LegacyInput.keyPressed(this.searchBox, keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
      } else if (keyCode == 256 && this.contextMenu.isOpen()) {
         this.contextMenu.close();
         return true;
      } else if (keyCode == 258 && !this.isHouseMode()) {
         Minecraft.getInstance().gui.setScreen(new VehicleManageScreen());
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

   private int rowAt(CompanionKind kind, double mouseX, double mouseY) {
      return this.listView.rowAt(kind, mouseX, mouseY, this.panelX(), this.panelY(), this.columnWidth(), 10, this.listHeight(), this.searchQuery());
   }

   private boolean isInsideList(CompanionKind kind, double mouseX, double mouseY) {
      return this.listView.isInsideList(kind, mouseX, mouseY, this.panelX(), this.panelY(), this.columnWidth(), 10, this.listHeight());
   }

   private boolean isNameColumn(CompanionKind kind, double mouseX, int row) {
      return this.listView.isNameColumn(kind, mouseX, row, this.panelX(), this.panelY(), this.columnWidth(), 10, this.listHeight());
   }

   private boolean scrollList(CompanionKind kind, double mouseX, double mouseY, double delta) {
      return this.listView.scroll(kind, mouseX, mouseY, delta, this.panelX(), this.panelY(), this.columnWidth(), 10, this.listHeight(), this.searchQuery());
   }

   private void send(CompanionKind kind, CompanionAction action, int value) {
      if (ModNetwork.channel != null) {
         ModNetwork.sendToServer(new CompanionCommandPacket(kind, action, value));
      }
   }

   private void sendRename(CompanionKind kind, int index, String name) {
      if (ModNetwork.channel != null) {
         ModNetwork.sendToServer(new CompanionRenamePacket(kind, index, name));
      }
   }

   private void sendRemove(CompanionKind kind, int index) {
      this.send(kind, CompanionAction.REMOVE, index);
   }

   private void sendPrimaryAction(CompanionKind kind, int index, boolean activeInWorld) {
      this.send(kind, activeInWorld ? CompanionAction.RECALL : CompanionAction.SELECT_SUMMON, index);
   }

   private void sendHomeAction(CompanionKind kind, int index, CompanionContextMenu.HomeAction action) {
      CompanionAction packetAction = switch (action) {
         case SET -> CompanionAction.SET_HOME;
         case GO -> CompanionAction.GO_HOME;
         case CLEAR -> CompanionAction.CLEAR_HOME;
      };
      if (this.isHouseMode()) {
         if (ModNetwork.channel != null) {
            ModNetwork.sendToServer(new CompanionHouseHomePacket(kind, packetAction, index, this.housePos, this.houseDimension.identifier()));
         }
      } else {
         this.send(kind, packetAction, index);
      }
   }

   private void sendBlacklist(CompanionKind kind, int index) {
      this.send(kind, CompanionAction.BLACKLIST_ADD, index);
   }

   private void openRenameOverlay(CompanionKind kind, int index) {
      List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(kind);
      String current = index >= 0 && index < entries.size() ? entries.get(index).name() : "";
      this.renameOverlay.open(this.font, kind, index, current, this.width, this.height, this.panelWidth());
   }

   private void showNotice(Component message) {
      this.notice = message;
      this.noticeTicks = 80;
   }

   private static boolean isActiveInWorld(CompanionListPacket.Entry entry) {
      return entry.ridden() || entry.deployed();
   }

   private boolean isHouseMode() {
      return this.housePos != null && this.houseDimension != null;
   }

   private int clampContextMenuX(int mouseX) {
      int left = this.panelX() + 4;
      int right = this.panelX() + this.panelWidth() - 112 - 4;
      return Math.max(left, Math.min(mouseX, right));
   }

   private int clampContextMenuY(int mouseY, int panelY) {
      int top = panelY + 4;
      int bottom = this.bottomControlsY(panelY) - 105 - 8;
      if (bottom < top) {
         bottom = Math.max(top, this.height - 105 - 4);
      }

      return Math.max(top, Math.min(mouseY, bottom));
   }

   private CompanionManageScreen.Rect ignoreButtonRect(int panelX, int panelY) {
      return new CompanionManageScreen.Rect(panelX + this.panelWidth() - 98, this.bottomControlsY(panelY) + 18, 86, 20);
   }

   private CompanionManageScreen.Rect blacklistButtonRect(int panelX, int panelY) {
      return new CompanionManageScreen.Rect(panelX + this.panelWidth() - 180, this.bottomControlsY(panelY) + 18, 78, 20);
   }

   private CompanionManageScreen.Rect deadButtonRect(int panelX, int panelY) {
      return new CompanionManageScreen.Rect(panelX + this.panelWidth() - 226, this.bottomControlsY(panelY) + 18, 44, 20);
   }

   private int searchBoxWidth(int panelX, int panelY) {
      int searchX = panelX + 12;
      int maxRight = this.deadButtonRect(panelX, panelY).x() - 8;
      return Math.max(80, maxRight - searchX);
   }

   private int panelX() {
      return CompanionScreenLayout.panelX(this.width);
   }

   private int panelY() {
      return CompanionScreenLayout.panelY(this.height);
   }

   private int bottomControlsY(int panelY) {
      return panelY + 24 + 14 + this.listHeight() + 8;
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

   private int fadeColor(int color, float fade) {
      int alpha = color >>> 24 & 0xFF;
      int scaledAlpha = Math.max(0, Math.min(255, Math.round(alpha * fade)));
      return color & 16777215 | scaledAlpha << 24;
   }

   private String searchQuery() {
      return this.searchBox == null ? "" : this.searchBox.getValue();
   }

   private record Drag(CompanionKind kind, int index, String name) {
   }

   private record PendingClick(CompanionKind kind, int index) {
   }

   private record Rect(int x, int y, int w, int h) {
   }
}
