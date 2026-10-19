package com.kuzhi.findme.client;

import com.kuzhi.findme.Config;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FindMeDialogueListEditScreen extends FindMeScreen {
   private final Screen parent;
   private final Config.DialogueListOption option;
   private MultiLineEditBox editor;
   private String draft;
   private int savedTicks;

   public FindMeDialogueListEditScreen(Screen parent, Config.DialogueListOption option) {
      super(Component.translatable(option.labelKey()));
      this.parent = parent;
      this.option = option;
      this.draft = String.join("\n", Config.dialogueValue(option));
   }

   protected void init() {
      int x = this.panelX();
      int y = this.panelY();
      int panelW = this.panelWidth();
      int panelH = this.panelHeight();
      int editorY = y + 48;
      int editorH = Math.max(72, panelH - 96);
      this.editor = MultiLineEditBox.builder().setX(x + 12).setY(editorY).setPlaceholder(Component.translatable("screen.find_me.config_dialogue_editor")).build(this.font, panelW - 24, editorH, Component.empty());
      this.editor.setCharacterLimit(32767);
      this.editor.setValue(this.draft);
      this.editor.setValueListener(value -> this.draft = value);
      this.addRenderableWidget(this.editor);
      this.setInitialFocus(this.editor);
      int buttonY = y + panelH - 32;
      int buttonW = Math.min(96, (panelW - 48) / 3);
      int gap = 8;
      int totalW = buttonW * 3 + gap * 2;
      int buttonX = x + panelW / 2 - totalW / 2;
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.find_me.config_save"), button -> this.save()).bounds(buttonX, buttonY, buttonW, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("screen.find_me.config_reset"), button -> this.resetToDefault())
            .bounds(buttonX + buttonW + gap, buttonY, buttonW, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.back"), button -> this.close()).bounds(buttonX + (buttonW + gap) * 2, buttonY, buttonW, 20).build()
      );
   }

   public void tick() {
      if (this.savedTicks > 0) {
         this.savedTicks--;
      }
   }

   public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      ClientScreenBackground.renderDim(graphics, this.width, this.height);
      int x = this.panelX();
      int y = this.panelY();
      int panelW = this.panelWidth();
      int panelH = this.panelHeight();
      graphics.fill(x, y, x + panelW, y + panelH, -300936424);
      graphics.fill(x + 1, y + 1, x + panelW - 1, y + 28, -14669774);
      graphics.text(this.font, this.title, x + 10, y + 10, -1);
      graphics.text(
         this.font, this.trimToWidth(Component.translatable("screen.find_me.config_dialogue_editor_hint").getString(), panelW - 20), x + 10, y + 34, -4734772
      );
      super.render(graphics, mouseX, mouseY, partialTick);
      if (this.savedTicks > 0) {
         graphics.centeredText(this.font, Component.translatable("screen.find_me.config_saved"), x + panelW / 2, y + panelH - 46, -11930);
      }
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (this.controlDown() && keyCode == 83) {
         this.save();
         return true;
      } else if (keyCode == 256) {
         this.close();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   public void onClose() {
      this.close();
   }

   public boolean isPauseScreen() {
      return false;
   }

   private void save() {
      List<String> lines = this.draft.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
      Config.setDialogueValue(this.option, lines);
      this.draft = String.join("\n", lines);
      this.editor.setValue(this.draft);
      this.savedTicks = 40;
   }

   private void resetToDefault() {
      this.draft = String.join("\n", Config.defaultDialogueValue(this.option));
      this.editor.setValue(this.draft);
      this.savedTicks = 0;
   }

   private void close() {
      Minecraft.getInstance().gui.setScreen(this.parent);
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
}
