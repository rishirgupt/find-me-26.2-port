package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.VehicleCommandPacket;
import com.kuzhi.findme.network.VehicleListPacket;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class VehicleWheelScreen extends FindMeScreen {
   private static final int FADE_TICKS = 6;
   private int hoveredIndex = -1;
   private int selectedHoverIndex = -1;
   private int page;
   private int openTicks;
   private final CompanionDetailPreviewRenderer previewRenderer = new CompanionDetailPreviewRenderer();

   public VehicleWheelScreen() {
      super(Component.translatable("screen.find_me.vehicle_wheel"));
   }

   protected void init() {
      CompanionDetailPreviewRenderer.enqueueWarm(
         ClientVehicleState.wheelEntries().stream().map(VehicleListPacket.Entry::asPreviewEntry).toList(), "minecraft:boat"
      );
      this.send(VehicleCommandPacket.Action.SYNC, -1);
   }

   public void tick() {
      super.tick();
      if (this.openTicks < 6) {
         this.openTicks++;
      }

      CompanionWheelScreen.syncMovementKeys();
   }

   public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      float fade = this.fade(partialTick);
      List<VehicleListPacket.Entry> entries = ClientVehicleState.wheelEntries();
      CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(entries.size(), this.page);
      this.page = pageState.index();
      int centerX = this.width / 2;
      int centerY = this.height / 2;
      int hoveredLocal = CompanionWheelLayout.hoveredSlot(centerX, centerY, mouseX, mouseY, pageState.visibleSize());
      this.hoveredIndex = hoveredLocal >= 0 ? pageState.start() + hoveredLocal : -1;
      if (this.hoveredIndex >= 0 && this.hoveredIndex != this.selectedHoverIndex) {
         this.selectedHoverIndex = this.hoveredIndex;
         this.send(VehicleCommandPacket.Action.SELECT, this.hoveredIndex);
         this.playHoverSound();
      }

      this.beginFadeTransform(graphics, centerX, centerY, fade);
      drawCircle(graphics, centerX, centerY, 117, guiColor(1712329496, fade));
      drawCircle(graphics, centerX, centerY, 12, guiColor(-871296751, fade));
      this.drawCenterCancel(graphics, centerX, centerY, fade);
      if (entries.isEmpty()) {
         graphics.centeredText(this.font, Component.translatable("screen.find_me.no_vehicles"), centerX, centerY + 56, guiColor(-11930, fade));
         graphics.pose().popMatrix();
         super.render(graphics, mouseX, mouseY, partialTick);
      } else {
         int active = ClientVehicleState.activeIndex();

         for (int i = pageState.start(); i < pageState.end(); i++) {
            int local = i - pageState.start();
            CompanionWheelLayout.Slot slot = CompanionWheelLayout.slot(centerX, centerY, local, pageState.visibleSize());
            VehicleListPacket.Entry entry = entries.get(i);
            boolean hovered = i == this.hoveredIndex;
            boolean selected = i == active;
            boolean deployed = entry.ridden() || entry.deployed();
            int radius = hovered ? 29 : 25;
            drawCircle(graphics, slot.x(), slot.y(), radius, guiColor(deployed ? -869499931 : (hovered ? -1152690845 : -1439418046), fade));
            if (selected) {
               drawDashedCircle(graphics, slot.x(), slot.y(), radius + 5, guiColor(-1511947, fade));
            }

            int textColor = guiColor(entry.alive() ? -1 : (entry.loaded() ? -34953 : -5592406), fade);
            this.previewRenderer.renderWheel(graphics, entry.asPreviewEntry(), slot.x(), slot.y(), "minecraft:boat");
            graphics.centeredText(this.font, trim(entry.name(), hovered ? 14 : 10), slot.x(), slot.y() - 23, textColor);
         }

         if (pageState.count() > 1) {
            graphics.centeredText(this.font, this.page + 1 + "/" + pageState.count(), centerX, centerY + 12 + 8, guiColor(-3355444, fade));
         }

         graphics.pose().popMatrix();
         super.render(graphics, mouseX, mouseY, partialTick);
      }
   }

   public void confirmSelection() {
      if (this.hoveredIndex >= 0) {
         this.send(VehicleCommandPacket.Action.SELECT, this.hoveredIndex);
      }

      Minecraft.getInstance().gui.setScreen(null);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && this.hoveredIndex >= 0) {
         List<VehicleListPacket.Entry> entries = ClientVehicleState.wheelEntries();
         if (this.hoveredIndex >= entries.size()) {
            return true;
         }

         this.playConfirmSound();
         VehicleListPacket.Entry entry = entries.get(this.hoveredIndex);
         if (!entry.ridden() && !entry.deployed()) {
            this.send(VehicleCommandPacket.Action.SELECT_SUMMON, this.hoveredIndex);
         } else {
            this.send(VehicleCommandPacket.Action.RECALL, this.hoveredIndex);
         }

         Minecraft.getInstance().gui.setScreen(null);
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
      int nextPage = CompanionWheelLayout.scrolledPage(ClientVehicleState.wheelEntries().size(), this.page, delta);
      if (nextPage != this.page) {
         this.playHoverSound();
      }

      this.page = nextPage;
      return true;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 258) {
         ClientEvents.setMountWheelMode(ClientEvents.MountWheelMode.MOUNT);
         Minecraft.getInstance().gui.setScreen(new CompanionWheelScreen(CompanionKind.MOUNT));
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   private void beginFadeTransform(GuiGraphicsExtractor graphics, int centerX, int centerY, float fade) {
      float scale = 0.96F + fade * 0.04F;
      graphics.pose().pushMatrix();
      graphics.pose().translate(centerX, centerY);
      graphics.pose().scale(scale, scale);
      graphics.pose().translate(-centerX, -centerY);
   }

   private float fade(float partialTick) {
      float progress = Math.min(6.0F, this.openTicks + partialTick) / 6.0F;
      return progress * progress * (3.0F - 2.0F * progress);
   }

   private void drawCenterCancel(GuiGraphicsExtractor graphics, int centerX, int centerY, float fade) {
      String text = Component.translatable("screen.find_me.cancel_short").getString();
      if (text.isBlank()) {
         text = "X";
      }

      text = text.substring(0, 1).toUpperCase();
      graphics.text(
         this.font, text, Math.round(centerX - this.font.width(text) / 2.0F), Math.round(centerY - 9.0F / 2.0F), guiColor(-2039584, fade), false
      );
   }

   private void send(VehicleCommandPacket.Action action, int value) {
      if (ModNetwork.channel != null) {
         ModNetwork.sendToServer(new VehicleCommandPacket(action, value));
      }
   }

   private void playHoverSound() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), 1.45F, 0.22F));
   }

   private void playConfirmSound() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.42F));
   }

   private static String trim(String text, int max) {
      return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "...";
   }

   private static void drawCircle(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
      for (int y = -radius; y <= radius; y++) {
         int halfWidth = (int)Math.sqrt(radius * radius - y * y);
         graphics.fill(centerX - halfWidth, centerY + y, centerX + halfWidth + 1, centerY + y + 1, color);
      }
   }

   private static void drawDashedCircle(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
      int segments = 48;

      for (int i = 0; i < segments; i += 2) {
         double angle = (Math.PI * 2) * i / segments;
         int x = centerX + (int)Math.round(Math.cos(angle) * radius);
         int y = centerY + (int)Math.round(Math.sin(angle) * radius);
         graphics.fill(x - 1, y - 1, x + 2, y + 2, color);
      }
   }

   private static int guiColor(int color, float fade) {
      int alpha = color >>> 24 & 0xFF;
      int scaledAlpha = Math.max(0, Math.min(255, (int)Math.round(alpha * Config.guiOpacity * fade)));
      return color & 16777215 | scaledAlpha << 24;
   }
}
