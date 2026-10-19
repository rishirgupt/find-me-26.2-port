package com.kuzhi.findme.client;

import com.kuzhi.findme.common.CompanionAction;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.ModParticles;
import com.kuzhi.findme.network.CompanionCommandPacket;
import com.kuzhi.findme.network.ModNetwork;
import com.kuzhi.findme.network.VehicleCommandPacket;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.client.player.ClientHotbarScrollEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents implements ClientModInitializer {
   private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("find_me", "find_me"));
   private static final KeyMapping MOUNT_KEY = new KeyMapping("key.find_me.mount", Type.KEYSYM, 82, CATEGORY);
   private static final KeyMapping COMPANION_KEY = new KeyMapping("key.find_me.companion", Type.KEYSYM, 86, CATEGORY);
   private static final KeyMapping MANAGE_KEY = new KeyMapping("key.find_me.manage", Type.KEYSYM, 71, CATEGORY);
   private static boolean mountWasDown;
   private static boolean companionWasDown;
   private static boolean rawMountDown;
   private static boolean rawCompanionDown;
   private static long mountDownAt;
   private static long companionDownAt;
   private static boolean mountWheelOpened;
   private static boolean companionWheelOpened;
   private static boolean perspectiveKeyWasDown;
   private static ClientEvents.MountWheelMode mountWheelMode = ClientEvents.MountWheelMode.MOUNT;

   @Override
   public void onInitializeClient() {
      KeyMappingHelper.registerKeyMapping(MOUNT_KEY);
      KeyMappingHelper.registerKeyMapping(COMPANION_KEY);
      KeyMappingHelper.registerKeyMapping(MANAGE_KEY);
      ParticleProviderRegistry particles = ParticleProviderRegistry.getInstance();
      particles.register(ModParticles.CONTRACT_GLYPH.get(), sprites -> new ClientContractGlyphParticle.Provider(sprites, 0.58F, 0.92F, 1.0F));
      particles.register(ModParticles.CONTRACT_GLYPH_RESPONSE.get(), sprites -> new ClientContractGlyphParticle.Provider(sprites, 0.72F, 0.44F, 1.0F));
      particles.register(ModParticles.CONTRACT_GLYPH_VOW.get(), sprites -> new ClientContractGlyphParticle.Provider(sprites, 1.0F, 0.66F, 0.22F));
      ClientNetwork.register();
      ClientTickEvents.END_CLIENT_TICK.register(client -> onClientTick());
      ClientHotbarScrollEvents.ALLOW.register((inventory, from, to, scrollX, scrollY) -> !onMouseScroll(scrollY));
      LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
         WorldRenderFrame frame = new WorldRenderFrame(context);
         ClientContractCeremonyRenderer.renderWorld(frame);
         ClientRescueMagicRenderer.renderWorld(frame);
         ClientStorageEffectRenderer.renderWorld(frame);
         ClientVehicleSealEffectRenderer.renderWorld(frame);
      });
      HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("find_me", "overlay"), (graphics, deltaTracker) -> {
         ClientContractCeremonyRenderer.renderGui(graphics);
         ClientCompanionDialogueRenderer.renderGui(graphics, deltaTracker.getGameTimeDeltaPartialTick(false));
      });
   }

   private static void tickManageKey() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null) {
         while (MANAGE_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null) {
               minecraft.gui.setScreen(new CompanionManageScreen(CompanionKind.MOUNT));
            }
         }
      }
   }

   private static void tickKey(CompanionKind kind, KeyMapping key) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null) {
         boolean isDown = isKeyDown(kind, key);
         boolean wasDown = wasDown(kind);
         long now = System.currentTimeMillis();
         if (isDown && !wasDown) {
            setWasDown(kind, true);
            setDownAt(kind, now);
            setWheelOpened(kind, false);
            if (kind == CompanionKind.MOUNT && mountWheelMode == ClientEvents.MountWheelMode.VEHICLE) {
               CompanionDetailPreviewRenderer.enqueueWarm(
                  ClientVehicleState.wheelEntries().stream().map(entry -> entry.asPreviewEntry()).toList(), "minecraft:boat"
               );
               sendVehicle(VehicleCommandPacket.Action.SYNC, -1);
            } else {
               CompanionDetailPreviewRenderer.enqueueWarm(ClientCompanionState.entries(kind), fallbackPreviewType(kind));
               send(kind, CompanionAction.SYNC, -1);
            }
         } else if (isDown && wasDown && !wheelOpened(kind) && now - downAt(kind) >= 250L && minecraft.gui.screen() == null) {
            setWheelOpened(kind, true);
            if (kind == CompanionKind.MOUNT && mountWheelMode == ClientEvents.MountWheelMode.VEHICLE) {
               minecraft.gui.setScreen(new VehicleWheelScreen());
            } else {
               minecraft.gui.setScreen(new CompanionWheelScreen(kind));
            }
         } else {
            if (!isDown && wasDown) {
               setWasDown(kind, false);
               if (wheelOpened(kind)) {
                  if (kind == CompanionKind.MOUNT && minecraft.gui.screen() instanceof VehicleWheelScreen vehicleWheelScreen) {
                     vehicleWheelScreen.confirmSelection();
                     setWheelOpened(kind, false);
                     return;
                  }

                  Screen screen2 = minecraft.gui.screen();
                  CompanionWheelScreen screen;
                  if (screen2 instanceof CompanionWheelScreen && (screen = (CompanionWheelScreen)screen2).kind() == kind) {
                     screen.confirmSelection();
                  }

                  setWheelOpened(kind, false);
               } else {
                  handleShortPress(kind);
               }
            }
         }
      }
   }

   private static void handleShortPress(CompanionKind kind) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null) {
         if (kind == CompanionKind.MOUNT && mountWheelMode == ClientEvents.MountWheelMode.VEHICLE) {
            sendVehicle(VehicleCommandPacket.Action.SELECT_SUMMON, ClientVehicleState.activeIndex());
         } else {
            send(kind, CompanionAction.SUMMON, -1);
         }
      }
   }

   private static void send(CompanionKind kind, CompanionAction action, int targetEntityId) {
      if (ModNetwork.channel != null) {
         if (kind == CompanionKind.MOUNT && action == CompanionAction.SUMMON) {
            ClientCameraLock.arm();
         }

         ModNetwork.sendToServer(new CompanionCommandPacket(kind, action, targetEntityId));
      }
   }

   private static void sendVehicle(VehicleCommandPacket.Action action, int value) {
      if (ModNetwork.channel != null) {
         ModNetwork.sendToServer(new VehicleCommandPacket(action, value));
      }
   }

   public static ClientEvents.MountWheelMode mountWheelMode() {
      return mountWheelMode;
   }

   public static void setMountWheelMode(ClientEvents.MountWheelMode mode) {
      mountWheelMode = mode == null ? ClientEvents.MountWheelMode.MOUNT : mode;
   }

   private static boolean isKeyDown(CompanionKind kind, KeyMapping key) {
      if (!key.isDown() && (kind == CompanionKind.MOUNT ? !rawMountDown : !rawCompanionDown)) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.getWindow() == null) {
            return false;
         } else {
            Window window = minecraft.getWindow();
            Key bound = KeyMappingHelper.getBoundKeyOf(key);
            if (bound.getType() == Type.KEYSYM) {
               return InputConstants.isKeyDown(window, bound.getValue());
            } else {
               return bound.getType() == Type.MOUSE ? GLFW.glfwGetMouseButton(window.handle(), bound.getValue()) == 1 : false;
            }
         }
      } else {
         return true;
      }
   }

   private static boolean wasDown(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? mountWasDown : companionWasDown;
   }

   private static void setWasDown(CompanionKind kind, boolean value) {
      if (kind == CompanionKind.MOUNT) {
         mountWasDown = value;
      } else {
         companionWasDown = value;
      }
   }

   private static long downAt(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? mountDownAt : companionDownAt;
   }

   private static void setDownAt(CompanionKind kind, long value) {
      if (kind == CompanionKind.MOUNT) {
         mountDownAt = value;
      } else {
         companionDownAt = value;
      }
   }

   private static boolean wheelOpened(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? mountWheelOpened : companionWheelOpened;
   }

   private static void setWheelOpened(CompanionKind kind, boolean value) {
      if (kind == CompanionKind.MOUNT) {
         mountWheelOpened = value;
      } else {
         companionWheelOpened = value;
      }
   }

   private static String fallbackPreviewType(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? "minecraft:horse" : "minecraft:wolf";
   }

   /** @return true when the scroll was used by a held summon key and must not move the hotbar */
   private static boolean onMouseScroll(double scrollDeltaY) {
      Screen screen = Minecraft.getInstance().gui.screen();
      if (screen instanceof CompanionWheelScreen || screen instanceof VehicleWheelScreen) {
         return false;
      } else if (isKeyDown(CompanionKind.MOUNT, MOUNT_KEY)) {
         if (mountWheelMode == ClientEvents.MountWheelMode.VEHICLE) {
            sendVehicle(VehicleCommandPacket.Action.SYNC, -1);
         } else {
            send(CompanionKind.MOUNT, scrollDeltaY > 0.0 ? CompanionAction.PREVIOUS : CompanionAction.NEXT, -1);
         }

         return true;
      } else if (isKeyDown(CompanionKind.COMPANION, COMPANION_KEY)) {
         send(CompanionKind.COMPANION, scrollDeltaY > 0.0 ? CompanionAction.PREVIOUS : CompanionAction.NEXT, -1);
         return true;
      } else {
         return false;
      }
   }

   private static void onClientTick() {
      Minecraft minecraft = Minecraft.getInstance();
      boolean perspectiveKeyDown = minecraft.options.keyTogglePerspective.isDown();
      if (perspectiveKeyDown && !perspectiveKeyWasDown) {
         ClientCameraLock.allowManualChange();
      }

      perspectiveKeyWasDown = perspectiveKeyDown;
      tickManageKey();
      tickKey(CompanionKind.MOUNT, MOUNT_KEY);
      tickKey(CompanionKind.COMPANION, COMPANION_KEY);
      ClientCameraLock.tick();
      ClientContractCamera.tick();
      ClientContractRenderState.tick();
      ClientRescueMagicRenderState.tick();
      ClientStorageEffectState.tick();
      ClientVehicleSealEffectState.tick();
      ClientCompanionDialogueState.tick();
      SableVehicleScreenshotPreviewCache.tick();
      CompanionDetailPreviewRenderer.tickWarmCache();
   }

   public enum MountWheelMode {
      MOUNT,
      VEHICLE;
   }
}
