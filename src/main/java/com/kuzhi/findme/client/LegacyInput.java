package com.kuzhi.findme.client;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

/** Passes input given as plain coordinates and key codes on to widgets, which now take event objects. */
final class LegacyInput {
   private LegacyInput() {
   }

   static boolean mouseClicked(AbstractWidget widget, double mouseX, double mouseY, int button) {
      return widget.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
   }

   static boolean keyPressed(EditBox box, int keyCode, int scanCode, int modifiers) {
      return box.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
   }

   static boolean charTyped(EditBox box, char codePoint) {
      return box.charTyped(new CharacterEvent(codePoint));
   }
}
