package com.kuzhi.findme.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;

/**
 * Base of every Find me screen. The screens are written against the older input and drawing hooks (separate
 * mouse coordinates, key codes, a {@code render} method); this class receives the current event objects and
 * forwards them to those hooks, and forwards the hooks' {@code super} calls back to the game.
 */
abstract class FindMeScreen extends Screen {
   private int currentModifiers;
   private boolean currentDoubleClick;

   protected FindMeScreen(Component title) {
      super(title);
   }

   public void renderBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
   }

   /** The background is drawn by each screen itself. */
   @Override
   public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
   }

   @Override
   public final void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      this.render(graphics, mouseX, mouseY, partialTick);
   }

   /** Draws the screen; the default draws the widgets. */
   public void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(graphics, mouseX, mouseY, partialTick);
   }

   @Override
   public final boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
      this.currentModifiers = event.modifiers();
      this.currentDoubleClick = doubleClick;
      return this.mouseClicked(event.x(), event.y(), event.button());
   }

   public boolean mouseClicked(double mouseX, double mouseY, int button) {
      return super.mouseClicked(this.mouseEvent(mouseX, mouseY, button), this.currentDoubleClick);
   }

   @Override
   public final boolean mouseReleased(MouseButtonEvent event) {
      this.currentModifiers = event.modifiers();
      return this.mouseReleased(event.x(), event.y(), event.button());
   }

   public boolean mouseReleased(double mouseX, double mouseY, int button) {
      return super.mouseReleased(this.mouseEvent(mouseX, mouseY, button));
   }

   @Override
   public final boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      this.currentModifiers = event.modifiers();
      return this.mouseDragged(event.x(), event.y(), event.button(), dragX, dragY);
   }

   public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
      return super.mouseDragged(this.mouseEvent(mouseX, mouseY, button), dragX, dragY);
   }

   @Override
   public final boolean keyPressed(KeyEvent event) {
      this.currentModifiers = event.modifiers();
      return this.keyPressed(event.key(), event.scancode(), event.modifiers());
   }

   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      return super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
   }

   @Override
   public final boolean charTyped(CharacterEvent event) {
      boolean handled = false;
      for (char character : Character.toChars(event.codepoint())) {
         handled |= this.charTyped(character, this.currentModifiers);
      }

      return handled;
   }

   public boolean charTyped(char codePoint, int modifiers) {
      return super.charTyped(new CharacterEvent(codePoint));
   }

   protected final MouseButtonEvent mouseEvent(double mouseX, double mouseY, int button) {
      return new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, this.currentModifiers));
   }

   /** Whether Ctrl (Cmd on macOS) was held for the input event being handled. */
   protected final boolean controlDown() {
      return (this.currentModifiers & (org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL | org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER)) != 0;
   }

   protected final boolean shiftDown() {
      return (this.currentModifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
   }
}
