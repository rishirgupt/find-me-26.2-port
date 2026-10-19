package com.kuzhi.findme.server;

import com.kuzhi.findme.Config;
import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.network.CompanionDialoguePacket;
import com.kuzhi.findme.network.ModNetwork;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

final class CompanionSummonLineService {
   private static final String SEPARATOR = "|";
   private static final int DIALOGUE_TICKS = 46;
   private static final int DIRECT_REPLY_DELAY_TICKS = 14;
   private static final Map<UUID, CompanionSummonLineService.PendingDialogue> PENDING = new HashMap<>();

   private CompanionSummonLineService() {
   }

   static void showOrSchedule(ServerPlayer player, LivingEntity living, CompanionKind kind, boolean rescue, boolean combatRescue, boolean arrivalStarted) {
      if (player != null && living != null && kind != null) {
         if (!Config.enableDialogue) {
            PENDING.remove(living.getUUID());
            showSummonFallback(player, living, rescue || combatRescue);
         } else {
            CompanionSummonLineService.Dialogue dialogue = choose(
               player, living.getDisplayName().getString(), templates(player, living, kind, rescue, combatRescue)
            );
            if (dialogue != null) {
               boolean urgent = rescue || combatRescue;
               if (arrivalStarted && CompanionManager.isArrivalPending(living)) {
                  send(player, dialogue.call(), "", dialogue.context(), urgent, 0);
                  PENDING.put(
                     living.getUUID(),
                     new CompanionSummonLineService.PendingDialogue(player.getUUID(), dialogue.call(), dialogue.reply(), dialogue.context(), urgent)
                  );
               } else {
                  send(player, dialogue.call(), dialogue.reply(), dialogue.context(), urgent, 14);
               }
            }
         }
      }
   }

   static void showScheduled(LivingEntity living) {
      if (living != null) {
         CompanionSummonLineService.PendingDialogue pending = PENDING.remove(living.getUUID());
         if (Config.enableDialogue && pending != null && living.level().getServer() != null) {
            ServerPlayer player = living.level().getServer().getPlayerList().getPlayer(pending.playerUuid());
            if (player != null) {
               send(player, pending.call(), pending.reply(), pending.context(), pending.urgent(), 0);
            }
         }
      }
   }

   static void cancelScheduled(LivingEntity living) {
      if (living != null) {
         PENDING.remove(living.getUUID());
      }
   }

   static void cancelScheduled(UUID uuid) {
      if (uuid != null) {
         PENDING.remove(uuid);
      }
   }

   static void showBusy(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, UUID uuid) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.busy", ChatFormatting.YELLOW, creatureMessageArg(player, data, uuid, kind));
      } else {
         String creatureName = creatureName(player, data, uuid, kind);
         showImmediatePair(player, creatureName, Config.busyDialogueLines, false);
      }
   }

   static void showCooldown(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, long seconds) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.cooldown", ChatFormatting.YELLOW, Long.toString(Math.max(0L, seconds)));
      } else {
         String creatureName = activeCreatureName(player, data, kind);
         showImmediatePair(player, creatureName, Config.cooldownDialogueLines, false, Long.toString(Math.max(0L, seconds)));
      }
   }

   static void showNoRegistered(ServerPlayer player, CompanionKind kind) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.no_registered", ChatFormatting.YELLOW, CompanionManager.label(kind));
      } else {
         showImmediatePair(player, kindName(kind), Config.noRegisteredDialogueLines, false);
      }
   }

   static void showUnavailable(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.unavailable", ChatFormatting.RED, activeCreatureMessageArg(player, data, kind));
      } else {
         showImmediatePair(player, activeCreatureName(player, data, kind), Config.unavailableDialogueLines, true);
      }
   }

   static void showPvpDisabled(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.pvp_disabled", ChatFormatting.YELLOW);
      } else {
         showImmediatePair(player, activeCreatureName(player, data, kind), Config.pvpDisabledDialogueLines, true);
      }
   }

   static void showAlreadyRiding(ServerPlayer player, LivingEntity living) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.already_riding", ChatFormatting.YELLOW);
      } else {
         showImmediatePair(
            player, living == null ? kindName(CompanionKind.MOUNT) : living.getDisplayName().getString(), Config.alreadyRidingDialogueLines, false
         );
      }
   }

   static void showOccupied(ServerPlayer player, LivingEntity living, CompanionKind kind) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.occupied", ChatFormatting.YELLOW, living == null ? CompanionManager.label(kind) : living.getDisplayName());
      } else {
         showImmediatePair(player, living == null ? kindName(kind) : living.getDisplayName().getString(), Config.occupiedDialogueLines, true);
      }
   }

   static void showTooHigh(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.companion_too_high", ChatFormatting.YELLOW);
      } else {
         String creatureName = living == null ? activeCreatureName(player, data, kind) : living.getDisplayName().getString();
         showImmediatePair(player, creatureName, Config.tooHighDialogueLines, true);
      }
   }

   static void showNeedOpenSpace(ServerPlayer player, PlayerCompanionData data, CompanionKind kind, LivingEntity living) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.need_open_space", ChatFormatting.YELLOW);
      } else {
         String creatureName = living == null ? activeCreatureName(player, data, kind) : living.getDisplayName().getString();
         showImmediatePair(player, creatureName, Config.blockedDialogueLines, true);
      }
   }

   static void showNoWater(ServerPlayer player, CompanionKind kind) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.no_water", ChatFormatting.YELLOW);
      } else {
         showImmediatePair(player, kindName(kind), Config.noWaterDialogueLines, false);
      }
   }

   static void showVoidUnsafe(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      if (!Config.enableDialogue) {
         String key = kind == CompanionKind.MOUNT ? "message.find_me.void_mount_unsafe" : "message.find_me.void_companion_unsafe";
         showFallback(player, key, ChatFormatting.YELLOW);
      } else {
         List<String> lines = kind == CompanionKind.MOUNT ? Config.voidMountUnsafeDialogueLines : Config.voidCompanionUnsafeDialogueLines;
         showImmediatePair(player, activeCreatureName(player, data, kind), lines, true);
      }
   }

   static void showVoidFlyingMount(ServerPlayer player, LivingEntity living) {
      if (!Config.enableDialogue) {
         if (living != null) {
            showSummonFallback(player, living, false);
         }
      } else {
         String creatureName = living == null ? kindName(CompanionKind.MOUNT) : living.getDisplayName().getString();
         showImmediatePair(player, creatureName, Config.voidFlyingMountDialogueLines, true);
      }
   }

   static void showBlocked(ServerPlayer player, LivingEntity living) {
      if (player != null && living != null) {
         if (!Config.enableDialogue) {
            showFallback(player, "message.find_me.rescue_space_too_small", ChatFormatting.YELLOW, living.getDisplayName());
         } else {
            showImmediatePair(player, living.getDisplayName().getString(), Config.blockedDialogueLines, true);
         }
      }
   }

   static void showStorage(ServerPlayer player, LivingEntity living) {
      if (player != null && living != null) {
         if (!Config.enableDialogue) {
            String key = hasHome(player, living) ? "message.find_me.returned_home" : "message.find_me.collected";
            showFallback(player, key, ChatFormatting.GRAY, living.getDisplayName());
         } else {
            List<String> templates = hasHome(player, living) ? Config.homeStorageDialogueLines : Config.storageDialogueLines;
            showImmediatePair(player, living.getDisplayName().getString(), templates, false);
         }
      }
   }

   static void showHomeSet(ServerPlayer player, String creatureName) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.home_set", ChatFormatting.GREEN, Component.literal(creatureName == null ? "" : creatureName));
      } else {
         showImmediatePair(player, creatureName, Config.homeSetDialogueLines, false);
      }
   }

   static void showHomeCleared(ServerPlayer player, String creatureName) {
      if (!Config.enableDialogue) {
         showFallback(player, "message.find_me.home_cleared", ChatFormatting.GREEN, Component.literal(creatureName == null ? "" : creatureName));
      } else {
         showImmediatePair(player, creatureName, Config.homeClearDialogueLines, false);
      }
   }

   static void showRegistered(ServerPlayer player, LivingEntity living) {
      if (player != null && living != null) {
         if (!Config.enableDialogue) {
            showFallback(player, "message.find_me.registered_creature", ChatFormatting.GREEN, living.getDisplayName());
         } else {
            showImmediatePair(player, living.getDisplayName().getString(), Config.registeredDialogueLines, false);
         }
      }
   }

   static void showSleepRevived(ServerPlayer player, String creatureName, boolean spawned) {
      if (!Config.enableDialogue) {
         showFallback(
            player,
            spawned ? "message.find_me.sleep_revived" : "message.find_me.sleep_revived_stored",
            ChatFormatting.LIGHT_PURPLE,
            Component.literal(creatureName == null ? "" : creatureName)
         );
      } else {
         showImmediatePair(player, creatureName, spawned ? Config.sleepReviveDialogueLines : Config.sleepReviveStoredDialogueLines, false);
      }
   }

   private static void showSummonFallback(ServerPlayer player, LivingEntity living, boolean rescue) {
      showFallback(player, rescue ? "message.find_me.rescue_summoned" : "message.find_me.summoned", ChatFormatting.AQUA, living.getDisplayName());
   }

   private static void showFallback(ServerPlayer player, String key, ChatFormatting color, Object... args) {
      if (player != null && key != null) {
         CompanionManager.tell(player, key, color, args);
      }
   }

   private static void showImmediatePair(ServerPlayer player, String creatureName, List<String> templates, boolean urgent) {
      showImmediatePair(player, creatureName, templates, urgent, "");
   }

   private static void showImmediatePair(ServerPlayer player, String creatureName, List<String> templates, boolean urgent, String seconds) {
      CompanionSummonLineService.Dialogue dialogue = choose(player, creatureName, templates);
      if (dialogue != null) {
         int replyDelay = dialogue.call().isBlank() ? 0 : 14;
         CompanionSummonLineService.DialogueContext context = seconds != null && !seconds.isBlank()
            ? dialogue.context().withSeconds(seconds)
            : dialogue.context();
         send(player, dialogue.call(), dialogue.reply(), context, urgent, replyDelay);
      }
   }

   private static CompanionSummonLineService.Dialogue choose(ServerPlayer player, String creatureName, List<String> templates) {
      if (Config.enableDialogue && player != null && templates != null && !templates.isEmpty()) {
         String template = templates.get(player.getRandom().nextInt(templates.size()));
         CompanionSummonLineService.Dialogue dialogue = CompanionSummonLineService.Dialogue.parse(
            template, defaultCall(), player.getGameProfile().name(), creatureName
         );
         return dialogue.call().isBlank() && dialogue.reply().isBlank() ? null : dialogue;
      } else {
         return null;
      }
   }

   private static void send(
      ServerPlayer player, String call, String reply, CompanionSummonLineService.DialogueContext context, boolean urgent, int replyDelayTicks
   ) {
      if (Config.enableDialogue) {
         ModNetwork.sendToPlayer(
            player, new CompanionDialoguePacket(call, reply, context.playerName(), context.creatureName(), context.seconds(), 46, replyDelayTicks, urgent)
         );
      }
   }

   private static List<String> templates(ServerPlayer player, LivingEntity living, CompanionKind kind, boolean rescue, boolean combatRescue) {
      if (!rescue && !combatRescue && hasHome(player, living)) {
         return Config.homeSummonDialogueLines;
      } else if (kind == CompanionKind.MOUNT) {
         return rescue ? Config.mountRescueDialogueLines : Config.mountSummonDialogueLines;
      } else {
         return combatRescue ? Config.companionCombatRescueDialogueLines : Config.companionSummonDialogueLines;
      }
   }

   private static boolean hasHome(ServerPlayer player, LivingEntity living) {
      return player != null && living != null ? CompanionManager.data(player).homePosition(living.getUUID()).isPresent() : false;
   }

   private static String defaultCall() {
      return "dialogue.find_me.default_call";
   }

   private static String creatureName(ServerPlayer player, PlayerCompanionData data, UUID uuid, CompanionKind kind) {
      if (data != null && uuid != null && data.storedEntity(uuid).isPresent()) {
         return CompanionManager.storedEntityName(data.storedEntity(uuid).get(), uuid);
      } else if (player != null
         && player.level().getServer() != null
         && uuid != null
         && data != null
         && CompanionManager.findLoadedEntity(player.level().getServer(), data, uuid).orElse(null) instanceof LivingEntity found) {
         return found.getDisplayName().getString();
      } else {
         return kind == CompanionKind.MOUNT ? "dialogue.find_me.kind.mount" : "dialogue.find_me.kind.companion";
      }
   }

   private static Object creatureMessageArg(ServerPlayer player, PlayerCompanionData data, UUID uuid, CompanionKind kind) {
      String name = creatureName(player, data, uuid, kind);
      return isDialogueKindName(name) ? CompanionManager.label(kind) : Component.literal(name);
   }

   private static String activeCreatureName(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      return data == null ? kindName(kind) : data.active(kind).map(uuid -> creatureName(player, data, uuid, kind)).orElseGet(() -> kindName(kind));
   }

   private static Object activeCreatureMessageArg(ServerPlayer player, PlayerCompanionData data, CompanionKind kind) {
      String name = activeCreatureName(player, data, kind);
      return isDialogueKindName(name) ? CompanionManager.label(kind) : Component.literal(name);
   }

   private static String kindName(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? "dialogue.find_me.kind.mount" : "dialogue.find_me.kind.companion";
   }

   private static boolean isDialogueKindName(String name) {
      return "dialogue.find_me.kind.mount".equals(name) || "dialogue.find_me.kind.companion".equals(name);
   }

   private record Dialogue(String call, String reply, CompanionSummonLineService.DialogueContext context) {
      static CompanionSummonLineService.Dialogue parse(String template, String fallbackCall, String playerName, String creatureName) {
         String safe = template == null ? "" : template;
         int separator = safe.indexOf("|");
         String call = separator >= 0 ? safe.substring(0, separator) : fallbackCall;
         String reply = separator >= 0 ? safe.substring(separator + "|".length()) : safe;
         return new CompanionSummonLineService.Dialogue(call.trim(), reply.trim(), new CompanionSummonLineService.DialogueContext(playerName, creatureName, ""));
      }
   }

   private record DialogueContext(String playerName, String creatureName, String seconds) {
      CompanionSummonLineService.DialogueContext withSeconds(String value) {
         return new CompanionSummonLineService.DialogueContext(this.playerName, this.creatureName, value == null ? "" : value);
      }
   }

   private record PendingDialogue(UUID playerUuid, String call, String reply, CompanionSummonLineService.DialogueContext context, boolean urgent) {
   }
}
