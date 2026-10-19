package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.CompanionCommandPacket;
import com.kuzhi.findme.network.CompanionListPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class CompanionWheelScreen extends FindMeScreen {
   private static final int FADE_TICKS = 6;
   private final CompanionKind kind;
   private int hoveredIndex = -1;
   private int selectedHoverIndex = -1;
   private int page;
   private int openTicks;
   private final CompanionDetailPreviewRenderer previewRenderer = new CompanionDetailPreviewRenderer();

   public CompanionWheelScreen(CompanionKind kind) {
      super(Component.translatable(kind == CompanionKind.MOUNT ? "screen.find_me.mount_wheel" : "screen.find_me.companion_wheel"));
      this.kind = kind;
   }

   public CompanionKind kind() {
      return this.kind;
   }

   protected void init() {
      CompanionDetailPreviewRenderer.enqueueWarm(ClientCompanionState.entries(this.kind), this.fallbackPreviewType());
      this.send(CompanionAction.SYNC, -1);
   }

   public void tick() {
      super.tick();
      if (this.openTicks < 6) {
         this.openTicks++;
      }

      syncMovementKeys();
   }

   public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      float fade = this.fade(partialTick);
      List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(this.kind);
      CompanionWheelLayout.Page pageState = CompanionWheelLayout.page(entries.size(), this.page);
      this.page = pageState.index();
      int centerX = this.wheelCenterX();
      int centerY = this.wheelCenterY();
      int hoveredLocal = CompanionWheelLayout.hoveredSlot(centerX, centerY, mouseX, mouseY, pageState.visibleSize());
      this.hoveredIndex = hoveredLocal >= 0 ? pageState.start() + hoveredLocal : -1;
      if (this.hoveredIndex >= 0 && this.hoveredIndex != this.selectedHoverIndex) {
         this.selectedHoverIndex = this.hoveredIndex;
         this.send(CompanionAction.SELECT, this.hoveredIndex);
         this.playHoverSound();
      }

      this.beginFadeTransform(graphics, centerX, centerY, fade);
      drawCircle(graphics, centerX, centerY, 117, guiColor(1712329496, fade));
      drawCircle(graphics, centerX, centerY, 12, guiColor(-871296751, fade));
      this.drawCenterCancel(graphics, centerX, centerY, fade);
      if (entries.isEmpty()) {
         graphics.centeredText(
            this.font,
            Component.translatable(this.kind == CompanionKind.MOUNT ? "screen.find_me.no_mounts" : "screen.find_me.no_companions"),
            centerX,
            centerY + 56,
            guiColor(-11930, fade)
         );
         graphics.pose().popMatrix();
         super.render(graphics, mouseX, mouseY, partialTick);
      } else {
         int active = ClientCompanionState.activeIndex(this.kind);

         for (int i = pageState.start(); i < pageState.end(); i++) {
            int local = i - pageState.start();
            CompanionWheelLayout.Slot slot = CompanionWheelLayout.slot(centerX, centerY, local, pageState.visibleSize());
            int x = slot.x();
            int y = slot.y();
            CompanionListPacket.Entry entry = entries.get(i);
            boolean hovered = i == this.hoveredIndex;
            boolean selected = i == active;
            boolean deployed = entry.ridden() || entry.deployed();
            int radius = hovered ? 29 : 25;
            int color = guiColor(deployed ? -869499931 : (hovered ? -1152690845 : -1439418046), fade);
            drawCircle(graphics, x, y, radius, color);
            if (selected) {
               drawDashedCircle(graphics, x, y, radius + 5, guiColor(-1511947, fade));
            }

            int textColor = guiColor(entry.alive() ? -1 : (entry.loaded() ? -34953 : -5592406), fade);
            this.previewRenderer.renderWheel(graphics, entry, x, y, this.fallbackPreviewType());
            graphics.centeredText(this.font, trim(entry.name(), hovered ? 14 : 10), x, y - 23, textColor);
            if (entry.hasHome()) {
               this.drawHomeMark(graphics, x, y, radius, fade);
            }

            if (entry.maxHealth() > 0.0F) {
               int barW = radius * 2 - 16;
               int barX = x - barW / 2;
               int barY = y + 17;
               float pct = Math.max(0.0F, Math.min(1.0F, entry.health() / entry.maxHealth()));
               graphics.fill(barX, barY, barX + barW, barY + 3, guiColor(-12902619, fade));
               graphics.fill(barX, barY, barX + Math.round(barW * pct), barY + 3, guiColor(pct < 0.35F ? -1812649 : -12139653, fade));
            }
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
         this.send(CompanionAction.SELECT, this.hoveredIndex);
      }

      Minecraft.getInstance().gui.setScreen(null);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      if (button == 0 && this.hoveredIndex >= 0) {
         List<CompanionListPacket.Entry> entries = ClientCompanionState.entries(this.kind);
         if (this.hoveredIndex >= entries.size()) {
            return true;
         }

         this.playConfirmSound();
         CompanionListPacket.Entry entry = entries.get(this.hoveredIndex);
         if (!entry.ridden() && !entry.deployed()) {
            this.send(CompanionAction.SELECT_SUMMON, this.hoveredIndex);
         } else {
            this.send(CompanionAction.SELECT, this.hoveredIndex);
            this.send(CompanionAction.RETURN_HOME, -1);
         }

         Minecraft.getInstance().gui.setScreen(null);
         return true;
      } else {
         return super.mouseClicked(mouseX, mouseY, button);
      }
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
      int nextPage = CompanionWheelLayout.scrolledPage(ClientCompanionState.entries(this.kind).size(), this.page, delta);
      if (nextPage != this.page) {
         this.playHoverSound();
      }

      this.page = nextPage;
      return true;
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 258 && this.kind == CompanionKind.MOUNT) {
         ClientEvents.setMountWheelMode(ClientEvents.MountWheelMode.VEHICLE);
         Minecraft.getInstance().gui.setScreen(new VehicleWheelScreen());
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public boolean isPauseScreen() {
      return false;
   }

   /** Keeps sprinting available while a wheel is open; the movement keys themselves are read by {@link #heldMovement()}. */
   public static void syncMovementKeys() {
      Minecraft minecraft = Minecraft.getInstance();
      net.minecraft.world.entity.player.Input held = heldMovement();
      if (minecraft.player != null && held != null && held.sprint() && held.forward() && !held.shift()) {
         minecraft.player.setSprinting(true);
      }
   }

   /**
    * The movement keys physically held while a summon wheel is open, so the player keeps moving behind it;
    * null when no wheel is open.
    */
   public static net.minecraft.world.entity.player.Input heldMovement() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player == null || !(minecraft.gui.screen() instanceof CompanionWheelScreen || minecraft.gui.screen() instanceof VehicleWheelScreen)) {
         return null;
      }

      com.mojang.blaze3d.platform.Window window = minecraft.getWindow();
      return new net.minecraft.world.entity.player.Input(
         rawKey(minecraft.options.keyUp, window),
         rawKey(minecraft.options.keyDown, window),
         rawKey(minecraft.options.keyLeft, window),
         rawKey(minecraft.options.keyRight, window),
         rawKey(minecraft.options.keyJump, window),
         rawKey(minecraft.options.keyShift, window),
         rawKey(minecraft.options.keySprint, window)
      );
   }

   private static boolean rawKey(KeyMapping key, com.mojang.blaze3d.platform.Window window) {
      com.mojang.blaze3d.platform.InputConstants.Key bound = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(key);
      return bound.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, bound.getValue());
   }

   private static String trim(String text, int max) {
      return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "...";
   }

   private void drawCenterCancel(GuiGraphicsExtractor graphics, int centerX, int centerY, float fade) {
      String text = Component.translatable("screen.find_me.cancel_short").getString();
      if (text.isBlank()) {
         text = "X";
      }

      text = text.substring(0, 1).toUpperCase();
      int textX = Math.round(centerX - this.font.width(text) / 2.0F);
      int textY = Math.round(centerY - 9.0F / 2.0F);
      graphics.text(this.font, text, textX, textY, guiColor(-2039584, fade), false);
   }

   private void drawHomeMark(GuiGraphicsExtractor graphics, int x, int y, int radius, float fade) {
      String mark = "H";
      int badgeX = x + radius - 11;
      int badgeY = y - radius + 3;
      graphics.fill(badgeX - 2, badgeY - 1, badgeX + 8, badgeY + 9, guiColor(-1340594126, fade));
      graphics.text(this.font, mark, badgeX, badgeY, guiColor(-6362113, fade), false);
   }

   private int wheelCenterX() {
      return this.width / 2;
   }

   private int wheelCenterY() {
      return this.height / 2;
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

   private String fallbackPreviewType() {
      return this.kind == CompanionKind.MOUNT ? "minecraft:horse" : "minecraft:wolf";
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

   private void send(CompanionAction action, int value) {
      if (ModNetwork.channel != null) {
         if (this.kind == CompanionKind.MOUNT && action == CompanionAction.SELECT_SUMMON) {
            ClientCameraLock.arm();
         }

         ModNetwork.sendToServer(new CompanionCommandPacket(this.kind, action, value));
      }
   }

   private void playHoverSound() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), 1.45F, 0.22F));
   }

   private void playConfirmSound() {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI((SoundEvent)SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.42F));
   }
}
