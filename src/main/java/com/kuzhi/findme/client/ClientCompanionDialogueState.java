package com.kuzhi.findme.client;

import com.kuzhi.findme.network.CompanionDialoguePacket;
import net.minecraft.client.resources.language.I18n;

public final class ClientCompanionDialogueState {
   private static String call = "";
   private static String reply = "";
   private static int age;
   private static int durationTicks;
   private static int replyDelayTicks;
   private static boolean urgent;

   private ClientCompanionDialogueState() {
   }

   public static void start(CompanionDialoguePacket packet) {
      call = resolve(packet.call(), packet.playerName(), packet.creatureName(), packet.seconds());
      reply = resolve(packet.reply(), packet.playerName(), packet.creatureName(), packet.seconds());
      durationTicks = Math.max(1, packet.durationTicks());
      replyDelayTicks = Math.max(0, packet.replyDelayTicks());
      age = 0;
      urgent = packet.urgent();
   }

   static void tick() {
      if (active()) {
         age++;
         if (age >= durationTicks) {
            clear();
         }
      }
   }

   static boolean active() {
      return durationTicks > 0 && age < durationTicks && (!call.isBlank() || !reply.isBlank());
   }

   static String call() {
      return call;
   }

   static String reply() {
      return reply;
   }

   static boolean showingReply() {
      return !reply.isBlank() && age >= replyDelayTicks;
   }

   static float alpha(float partialTick) {
      return !active() ? 0.0F : lineAlpha(age + partialTick, durationTicks);
   }

   static float replyAlpha(float partialTick) {
      if (active() && !reply.isBlank()) {
         float lineAge = age + partialTick - replyDelayTicks;
         if (lineAge <= 0.0F) {
            return 0.0F;
         }

         int remainingDuration = Math.max(1, durationTicks - replyDelayTicks);
         return lineAlpha(lineAge, remainingDuration);
      } else {
         return 0.0F;
      }
   }

   static boolean urgent() {
      return urgent;
   }

   private static void clear() {
      call = "";
      reply = "";
      age = 0;
      durationTicks = 0;
      replyDelayTicks = 0;
      urgent = false;
   }

   private static String resolve(String value, String playerName, String creatureName, String seconds) {
      String resolved = value == null ? "" : value.trim();
      if (!resolved.isBlank() && net.minecraft.locale.Language.getInstance().has(resolved)) {
         resolved = I18n.get(resolved, new Object[0]);
      }

      return resolved.replace("{player}", resolveArgument(playerName))
         .replace("{name}", resolveArgument(creatureName))
         .replace("{seconds}", seconds == null ? "" : seconds)
         .trim();
   }

   private static String resolveArgument(String value) {
      if (value != null && !value.isBlank()) {
         String trimmed = value.trim();
         return net.minecraft.locale.Language.getInstance().has(trimmed) ? I18n.get(trimmed, new Object[0]) : trimmed;
      } else {
         return "";
      }
   }

   private static float lineAlpha(float lineAge, int lineDuration) {
      float progress = Math.min(1.0F, lineAge / lineDuration);
      float in = Math.min(1.0F, lineAge / 6.0F);
      float out = 1.0F - Math.max(0.0F, (progress - 0.78F) / 0.22F);
      return Math.max(0.0F, Math.min(in, out));
   }
}
