package com.kuzhi.findme;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import com.kuzhi.findme.compat.ModConfigSpec;
import com.kuzhi.findme.compat.ModConfigSpec.BooleanValue;
import com.kuzhi.findme.compat.ModConfigSpec.Builder;
import com.kuzhi.findme.compat.ModConfigSpec.ConfigValue;
import com.kuzhi.findme.compat.ModConfigSpec.DoubleValue;
import com.kuzhi.findme.compat.ModConfigSpec.IntValue;

public class Config {
   private static final Builder BUILDER = new Builder();
   private static final BooleanValue ENABLE_VAULT = BUILDER.comment(
         "[Safety] Keep emergency creature snapshots before risky removal/death operations. Recommended: true."
      )
      .translation("config.find_me.enableVault")
      .define("enableVault", true);
   private static final IntValue VAULT_CAPACITY = BUILDER.comment("[Safety] Maximum emergency creature snapshots kept per player.")
      .translation("config.find_me.vaultCapacity")
      .defineInRange("vaultCapacity", 30, 1, 200);
   private static final BooleanValue ENABLE_AUTO_BACKUPS = BUILDER.comment("[Safety] Periodically back up each player's Find me lists. Recommended: true.")
      .translation("config.find_me.enableAutoBackups")
      .define("enableAutoBackups", true);
   private static final IntValue BACKUP_INTERVAL_MINUTES = BUILDER.comment("[Safety] Minutes between automatic player list backups.")
      .translation("config.find_me.backupIntervalMinutes")
      .defineInRange("backupIntervalMinutes", 30, 1, 1440);
   private static final IntValue BACKUP_CAPACITY = BUILDER.comment("[Safety] Maximum full-list backups kept per player.")
      .translation("config.find_me.backupCapacity")
      .defineInRange("backupCapacity", 6, 1, 48);
   private static final DoubleValue POST_TELEPORT_INVULNERABILITY_SECONDS = BUILDER.comment(
         "[Safety] Invulnerability seconds given to players and pets after forced teleport/rescue."
      )
      .translation("config.find_me.postTeleportInvulnerabilitySeconds")
      .defineInRange("postTeleportInvulnerabilitySeconds", 1.5, 0.0, 30.0);
   private static final BooleanValue PREVENT_BOUND_CREATURE_DEATH_DROPS = BUILDER.comment(
         "[Safety] If true, bound mounts and companions do not drop inventory/equipment items when they die. FindMe still keeps its death snapshot for revival, preventing duplicate saddles, armor, or modded equipment."
      )
      .translation("config.find_me.preventBoundCreatureDeathDrops")
      .define("preventBoundCreatureDeathDrops", true);
   private static final BooleanValue ENABLE_AUTO_BINDING = BUILDER.comment(
         "[Binding] Automatically recognize owned creatures and add them to Find me lists. Disabled by default; use Name Paper for manual contracts."
      )
      .translation("config.find_me.enableAutoBinding")
      .define("enableAutoBinding", false);
   private static final BooleanValue ENABLE_CONTRACT_ANIMATION = BUILDER.comment(
         "[Binding] Play the Name Paper contract ceremony animation before manual binding completes. Disable to bind immediately."
      )
      .translation("config.find_me.enableContractAnimation")
      .define("enableContractAnimation", true);
   private static final BooleanValue ENABLE_CONTRACT_CINEMATIC_CAMERA = BUILDER.comment(
         "[Binding] Use the temporary side-view cinematic camera during the Name Paper ceremony. If false, effects play from the player's normal view."
      )
      .translation("config.find_me.enableContractCinematicCamera")
      .define("enableContractCinematicCamera", true);
   private static final BooleanValue DEBUG_DIAGNOSTICS = BUILDER.comment(
         "[Debug] Write FindMe diagnostic logs for contact boxes, multipart detection, summon/cinematic handoff, and other compatibility checks. Disabled by default; enable only while testing."
      )
      .translation("config.find_me.debugDiagnostics")
      .define("debugDiagnostics", false);
   private static final BooleanValue ENABLE_SLEEP_REVIVE = BUILDER.comment("[Revive] Allows dead companions to return when a player wakes up.")
      .translation("config.find_me.enableSleepRevive")
      .define("enableSleepRevive", true);
   private static final IntValue SLEEP_REVIVE_COOLDOWN_MINUTES = BUILDER.comment("[Revive] Minutes between sleep revive attempts per player.")
      .translation("config.find_me.sleepReviveCooldownMinutes")
      .defineInRange("sleepReviveCooldownMinutes", 10, 0, 10080);
   private static final DoubleValue SLEEP_REVIVE_CHANCE = BUILDER.comment("[Revive] Chance that sleep revives one dead companion.")
      .translation("config.find_me.sleepReviveChance")
      .defineInRange("sleepReviveChance", 0.7, 0.0, 1.0);
   private static final BooleanValue SLEEP_REVIVE_SPAWN_ENTITY = BUILDER.comment(
         "[Revive] If true, sleep revive tries to place the creature nearby. Disable for packs with destructive large mobs."
      )
      .translation("config.find_me.sleepReviveSpawnEntity")
      .define("sleepReviveSpawnEntity", true);
   private static final IntValue MOUNT_COOLDOWN_TICKS = BUILDER.comment("[Summon] Normal mount summon cooldown in ticks.")
      .translation("config.find_me.mountCooldownTicks")
      .defineInRange("mountCooldownTicks", 60, 0, 1200);
   private static final IntValue COMPANION_COOLDOWN_TICKS = BUILDER.comment("[Summon] Companion summon cooldown in ticks.")
      .translation("config.find_me.companionCooldownTicks")
      .defineInRange("companionCooldownTicks", 40, 0, 1200);
   private static final IntValue COMBAT_MOUNT_COOLDOWN_TICKS = BUILDER.comment("[Summon] Mount summon cooldown in ticks while the player is in combat.")
      .translation("config.find_me.combatMountCooldownTicks")
      .defineInRange("combatMountCooldownTicks", 40, 0, 1200);
   private static final IntValue RETURN_COOLDOWN_TICKS = BUILDER.comment("[Summon] Short cooldown after returning a pet to its saved origin.")
      .translation("config.find_me.returnCooldownTicks")
      .defineInRange("returnCooldownTicks", 40, 0, 1200);
   private static final IntValue RESCUE_MIN_FALL_DISTANCE = BUILDER.comment(
         "[Summon] If the player is falling at least this many blocks, rescue summon can trigger."
      )
      .translation("config.find_me.rescueMinFallDistance")
      .defineInRange("rescueMinFallDistance", 5, 0, 128);
   private static final IntValue SAFE_SEARCH_RADIUS = BUILDER.comment("[Summon] Radius used to look for a safe teleport or revive landing spot.")
      .translation("config.find_me.safeSearchRadius")
      .defineInRange("safeSearchRadius", 12, 1, 64);
   private static final BooleanValue ENABLE_CREATURE_ARRIVAL_VOICE = BUILDER.comment(
         "[Audio] Play the summoned creature's own voice when summon or rescue is accepted."
      )
      .translation("config.find_me.enableCreatureArrivalVoice")
      .define("enableCreatureArrivalVoice", true);
   private static final DoubleValue CREATURE_ARRIVAL_VOICE_VOLUME = BUILDER.comment(
         "[Audio] Volume for summoned creature voice. 1.0 is full volume; default is 40%."
      )
      .translation("config.find_me.creatureArrivalVoiceVolume")
      .defineInRange("creatureArrivalVoiceVolume", 0.4, 0.0, 2.0);
   private static final DoubleValue MAGIC_EFFECT_VOLUME = BUILDER.comment(
         "[Audio] Volume for FindMe magic effects such as circles, storage, summon, rescue, and contract ceremony sounds. 1.0 is full volume; default is 65%."
      )
      .translation("config.find_me.magicEffectVolume")
      .defineInRange("magicEffectVolume", 0.65, 0.0, 2.0);
   private static final BooleanValue ENABLE_PVP_SUMMON = BUILDER.comment(
         "[Combat] Allows summon while the player has recently hurt or been hurt by another player."
      )
      .translation("config.find_me.enablePvpSummon")
      .define("enablePvpSummon", true);
   private static final BooleanValue ENABLE_COMBAT_SPEED_BOOST = BUILDER.comment("[Combat] Gives a short movement speed boost after mounting during combat.")
      .translation("config.find_me.enableCombatSpeedBoost")
      .define("enableCombatSpeedBoost", true);
   private static final DoubleValue COMBAT_SPEED_BOOST_PERCENT = BUILDER.comment("[Combat] Movement speed boost strength after mounting during combat.")
      .translation("config.find_me.combatSpeedBoostPercent")
      .defineInRange("combatSpeedBoostPercent", 0.2, 0.0, 2.0);
   private static final IntValue COMBAT_SPEED_BOOST_TICKS = BUILDER.comment("[Combat] Movement speed boost duration in ticks.")
      .translation("config.find_me.combatSpeedBoostTicks")
      .defineInRange("combatSpeedBoostTicks", 60, 0, 1200);
   private static final DoubleValue GUI_OPACITY = BUILDER.comment("[Visual] Opacity for wheel GUI 2D elements. Does not affect 3D entity previews.")
      .translation("config.find_me.guiOpacity")
      .defineInRange("guiOpacity", 1.0, 0.05, 1.0);
   private static final ConfigValue<List<? extends String>> PREVIEW_SCALE_OVERRIDES = BUILDER.comment(
         "[Visual] Advanced: GUI preview scale overrides, formatted as entity_id=scale. Example: dragonmounts:dragon=0.75"
      )
      .translation("config.find_me.previewScaleOverrides")
      .defineListAllowEmpty("previewScaleOverrides", List.of(), Config::isString);
   private static final BooleanValue ENABLE_DIALOGUE = BUILDER.comment(
         "[Dialogue] Show FindMe's flavor subtitle dialogue. Disabled by default; players who like roleplay-style lines can enable it."
      )
      .translation("config.find_me.enableDialogue")
      .define("enableDialogue", false);
   private static final ConfigValue<List<? extends String>> SUMMON_LINES = BUILDER.comment(
         "[Flavor] Random action-bar lines shown after a normal summon. Use {name} for the creature name. Clear this list to disable."
      )
      .translation("config.find_me.summonLines")
      .defineListAllowEmpty(
         "summonLines",
         List.of(
            "dialogue.find_me.legacy_summon.0",
            "dialogue.find_me.legacy_summon.1",
            "dialogue.find_me.legacy_summon.2",
            "dialogue.find_me.legacy_summon.3",
            "dialogue.find_me.legacy_summon.4"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> RESCUE_LINES = BUILDER.comment(
         "[Flavor] Random action-bar lines shown after a rescue summon. Use {name} for the creature name. Clear this list to disable."
      )
      .translation("config.find_me.rescueLines")
      .defineListAllowEmpty(
         "rescueLines",
         List.of(
            "dialogue.find_me.legacy_rescue.0",
            "dialogue.find_me.legacy_rescue.1",
            "dialogue.find_me.legacy_rescue.2",
            "dialogue.find_me.legacy_rescue.3",
            "dialogue.find_me.legacy_rescue.4"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> COMBAT_RESCUE_LINES = BUILDER.comment(
         "[Flavor] Random action-bar lines shown when a companion is called against a nearby threat. Use {name} for the creature name. Clear this list to disable."
      )
      .translation("config.find_me.combatRescueLines")
      .defineListAllowEmpty(
         "combatRescueLines",
         List.of(
            "dialogue.find_me.legacy_combat_rescue.0",
            "dialogue.find_me.legacy_combat_rescue.1",
            "dialogue.find_me.legacy_combat_rescue.2",
            "dialogue.find_me.legacy_combat_rescue.3"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> MOUNT_SUMMON_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Title/subtitle dialogue after a mount appears. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.mountSummonDialogueLines")
      .defineListAllowEmpty(
         "mountSummonDialogueLines",
         List.of(
            "dialogue.find_me.mount_summon.0.call|dialogue.find_me.mount_summon.0.reply",
            "dialogue.find_me.mount_summon.1.call|dialogue.find_me.mount_summon.1.reply",
            "dialogue.find_me.mount_summon.2.call|dialogue.find_me.mount_summon.2.reply",
            "dialogue.find_me.mount_summon.3.call|dialogue.find_me.mount_summon.3.reply",
            "dialogue.find_me.mount_summon.4.call|dialogue.find_me.mount_summon.4.reply",
            "dialogue.find_me.mount_summon.5.call|dialogue.find_me.mount_summon.5.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> COMPANION_SUMMON_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Title/subtitle dialogue after a companion appears. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.companionSummonDialogueLines")
      .defineListAllowEmpty(
         "companionSummonDialogueLines",
         List.of(
            "dialogue.find_me.companion_summon.0.call|dialogue.find_me.companion_summon.0.reply",
            "dialogue.find_me.companion_summon.1.call|dialogue.find_me.companion_summon.1.reply",
            "dialogue.find_me.companion_summon.2.call|dialogue.find_me.companion_summon.2.reply",
            "dialogue.find_me.companion_summon.3.call|dialogue.find_me.companion_summon.3.reply",
            "dialogue.find_me.companion_summon.4.call|dialogue.find_me.companion_summon.4.reply",
            "dialogue.find_me.companion_summon.5.call|dialogue.find_me.companion_summon.5.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> MOUNT_RESCUE_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Title/subtitle dialogue after a mount rescue appears. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.mountRescueDialogueLines")
      .defineListAllowEmpty(
         "mountRescueDialogueLines",
         List.of(
            "dialogue.find_me.mount_rescue.0.call|dialogue.find_me.mount_rescue.0.reply",
            "dialogue.find_me.mount_rescue.1.call|dialogue.find_me.mount_rescue.1.reply",
            "dialogue.find_me.mount_rescue.2.call|dialogue.find_me.mount_rescue.2.reply",
            "dialogue.find_me.mount_rescue.3.call|dialogue.find_me.mount_rescue.3.reply",
            "dialogue.find_me.mount_rescue.4.call|dialogue.find_me.mount_rescue.4.reply",
            "dialogue.find_me.mount_rescue.5.call|dialogue.find_me.mount_rescue.5.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> COMPANION_COMBAT_RESCUE_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Title/subtitle dialogue after a companion appears against a threat. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.companionCombatRescueDialogueLines")
      .defineListAllowEmpty(
         "companionCombatRescueDialogueLines",
         List.of(
            "dialogue.find_me.companion_combat_rescue.0.call|dialogue.find_me.companion_combat_rescue.0.reply",
            "dialogue.find_me.companion_combat_rescue.1.call|dialogue.find_me.companion_combat_rescue.1.reply",
            "dialogue.find_me.companion_combat_rescue.2.call|dialogue.find_me.companion_combat_rescue.2.reply",
            "dialogue.find_me.companion_combat_rescue.3.call|dialogue.find_me.companion_combat_rescue.3.reply",
            "dialogue.find_me.companion_combat_rescue.4.call|dialogue.find_me.companion_combat_rescue.4.reply",
            "dialogue.find_me.companion_combat_rescue.5.call|dialogue.find_me.companion_combat_rescue.5.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> STORAGE_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Title/subtitle dialogue when storing a creature. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.storageDialogueLines")
      .defineListAllowEmpty(
         "storageDialogueLines",
         List.of(
            "dialogue.find_me.storage.0.call|dialogue.find_me.storage.0.reply",
            "dialogue.find_me.storage.1.call|dialogue.find_me.storage.1.reply",
            "dialogue.find_me.storage.2.call|dialogue.find_me.storage.2.reply",
            "dialogue.find_me.storage.3.call|dialogue.find_me.storage.3.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> HOME_SUMMON_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Title/subtitle dialogue when a creature with a home is summoned. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.homeSummonDialogueLines")
      .defineListAllowEmpty(
         "homeSummonDialogueLines",
         List.of(
            "dialogue.find_me.home_summon.0.call|dialogue.find_me.home_summon.0.reply",
            "dialogue.find_me.home_summon.1.call|dialogue.find_me.home_summon.1.reply",
            "dialogue.find_me.home_summon.2.call|dialogue.find_me.home_summon.2.reply",
            "dialogue.find_me.home_summon.3.call|dialogue.find_me.home_summon.3.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> HOME_STORAGE_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Title/subtitle dialogue when storing a creature that has a home. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.homeStorageDialogueLines")
      .defineListAllowEmpty(
         "homeStorageDialogueLines",
         List.of(
            "dialogue.find_me.home_storage.0.call|dialogue.find_me.home_storage.0.reply",
            "dialogue.find_me.home_storage.1.call|dialogue.find_me.home_storage.1.reply",
            "dialogue.find_me.home_storage.2.call|dialogue.find_me.home_storage.2.reply",
            "dialogue.find_me.home_storage.3.call|dialogue.find_me.home_storage.3.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> HOME_SET_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when setting a creature's home. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.homeSetDialogueLines")
      .defineListAllowEmpty(
         "homeSetDialogueLines",
         List.of("dialogue.find_me.home_set.0.call|dialogue.find_me.home_set.0.reply", "dialogue.find_me.home_set.1.call|dialogue.find_me.home_set.1.reply"),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> HOME_CLEAR_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when clearing a creature's home. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.homeClearDialogueLines")
      .defineListAllowEmpty(
         "homeClearDialogueLines",
         List.of(
            "dialogue.find_me.home_clear.0.call|dialogue.find_me.home_clear.0.reply", "dialogue.find_me.home_clear.1.call|dialogue.find_me.home_clear.1.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> REGISTERED_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when a creature is registered into FindMe. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.registeredDialogueLines")
      .defineListAllowEmpty(
         "registeredDialogueLines",
         List.of(
            "dialogue.find_me.registered.0.call|dialogue.find_me.registered.0.reply", "dialogue.find_me.registered.1.call|dialogue.find_me.registered.1.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> SLEEP_REVIVE_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when sleep revives and places a creature nearby. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.sleepReviveDialogueLines")
      .defineListAllowEmpty(
         "sleepReviveDialogueLines",
         List.of(
            "dialogue.find_me.sleep_revive.0.call|dialogue.find_me.sleep_revive.0.reply",
            "dialogue.find_me.sleep_revive.1.call|dialogue.find_me.sleep_revive.1.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> SLEEP_REVIVE_STORED_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when sleep revives a creature but stores it because nearby space is unsafe. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.sleepReviveStoredDialogueLines")
      .defineListAllowEmpty(
         "sleepReviveStoredDialogueLines",
         List.of(
            "dialogue.find_me.sleep_revive_stored.0.call|dialogue.find_me.sleep_revive_stored.0.reply",
            "dialogue.find_me.sleep_revive_stored.1.call|dialogue.find_me.sleep_revive_stored.1.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> BLOCKED_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Title/subtitle dialogue when summon/rescue was answered but space is blocked. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.blockedDialogueLines")
      .defineListAllowEmpty(
         "blockedDialogueLines",
         List.of("dialogue.find_me.blocked.0.call|dialogue.find_me.blocked.0.reply", "dialogue.find_me.blocked.1.call|dialogue.find_me.blocked.1.reply"),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> BUSY_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Title/subtitle dialogue when the previous summon/storage animation is still closing. Format: first line|second line. Supports {player} and {name}. Clear to disable."
      )
      .translation("config.find_me.busyDialogueLines")
      .defineListAllowEmpty(
         "busyDialogueLines",
         List.of("dialogue.find_me.busy.0.call|dialogue.find_me.busy.0.reply", "dialogue.find_me.busy.1.call|dialogue.find_me.busy.1.reply"),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> COOLDOWN_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when summon is still on cooldown. Format: first line|second line. Supports {player}, {name}, and {seconds}."
      )
      .translation("config.find_me.cooldownDialogueLines")
      .defineListAllowEmpty(
         "cooldownDialogueLines",
         List.of("dialogue.find_me.cooldown.0.call|dialogue.find_me.cooldown.0.reply", "dialogue.find_me.cooldown.1.call|dialogue.find_me.cooldown.1.reply"),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> NO_REGISTERED_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when no creature is registered for this key. Format: first line|second line. Supports {player} and {name}."
      )
      .translation("config.find_me.noRegisteredDialogueLines")
      .defineListAllowEmpty(
         "noRegisteredDialogueLines",
         List.of(
            "dialogue.find_me.no_registered.0.call|dialogue.find_me.no_registered.0.reply",
            "dialogue.find_me.no_registered.1.call|dialogue.find_me.no_registered.1.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> UNAVAILABLE_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when the selected creature cannot be found or restored. Format: first line|second line. Supports {player} and {name}."
      )
      .translation("config.find_me.unavailableDialogueLines")
      .defineListAllowEmpty(
         "unavailableDialogueLines",
         List.of(
            "dialogue.find_me.unavailable.0.call|dialogue.find_me.unavailable.0.reply",
            "dialogue.find_me.unavailable.1.call|dialogue.find_me.unavailable.1.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> PVP_DISABLED_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when PvP summon is disabled. Format: first line|second line. Supports {player} and {name}."
      )
      .translation("config.find_me.pvpDisabledDialogueLines")
      .defineListAllowEmpty("pvpDisabledDialogueLines", List.of("dialogue.find_me.pvp_disabled.0.call|dialogue.find_me.pvp_disabled.0.reply"), Config::isString);
   private static final ConfigValue<List<? extends String>> ALREADY_RIDING_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when the player is already riding the selected mount. Format: first line|second line. Supports {player} and {name}."
      )
      .translation("config.find_me.alreadyRidingDialogueLines")
      .defineListAllowEmpty(
         "alreadyRidingDialogueLines",
         List.of(
            "dialogue.find_me.already_riding.0.call|dialogue.find_me.already_riding.0.reply",
            "dialogue.find_me.already_riding.1.call|dialogue.find_me.already_riding.1.reply",
            "dialogue.find_me.already_riding.2.call|dialogue.find_me.already_riding.2.reply",
            "dialogue.find_me.already_riding.3.call|dialogue.find_me.already_riding.3.reply",
            "|dialogue.find_me.already_riding.4.reply",
            "|dialogue.find_me.already_riding.5.reply",
            "|dialogue.find_me.already_riding.6.reply",
            "|dialogue.find_me.already_riding.7.reply",
            "|dialogue.find_me.already_riding.8.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> OCCUPIED_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when the selected creature is occupied by another passenger. Format: first line|second line. Supports {player} and {name}."
      )
      .translation("config.find_me.occupiedDialogueLines")
      .defineListAllowEmpty("occupiedDialogueLines", List.of("dialogue.find_me.occupied.0.call|dialogue.find_me.occupied.0.reply"), Config::isString);
   private static final ConfigValue<List<? extends String>> TOO_HIGH_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when a companion cannot appear safely at the player's height. Format: first line|second line. Supports {player} and {name}."
      )
      .translation("config.find_me.tooHighDialogueLines")
      .defineListAllowEmpty(
         "tooHighDialogueLines",
         List.of("dialogue.find_me.too_high.0.call|dialogue.find_me.too_high.0.reply", "dialogue.find_me.too_high.1.call|dialogue.find_me.too_high.1.reply"),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> NO_WATER_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when a swimming creature has no nearby water. Format: first line|second line. Supports {player} and {name}."
      )
      .translation("config.find_me.noWaterDialogueLines")
      .defineListAllowEmpty("noWaterDialogueLines", List.of("dialogue.find_me.no_water.0.call|dialogue.find_me.no_water.0.reply"), Config::isString);
   private static final ConfigValue<List<? extends String>> VOID_MOUNT_UNSAFE_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when a non-flying mount cannot be summoned over the void. Format: first line|second line. Supports {player} and {name}."
      )
      .translation("config.find_me.voidMountUnsafeDialogueLines")
      .defineListAllowEmpty(
         "voidMountUnsafeDialogueLines",
         List.of(
            "dialogue.find_me.void_mount_unsafe.0.call|dialogue.find_me.void_mount_unsafe.0.reply",
            "dialogue.find_me.void_mount_unsafe.1.call|dialogue.find_me.void_mount_unsafe.1.reply",
            "dialogue.find_me.void_mount_unsafe.2.call|dialogue.find_me.void_mount_unsafe.2.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> VOID_COMPANION_UNSAFE_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when a companion cannot be summoned over the void. Format: first line|second line. Supports {player} and {name}."
      )
      .translation("config.find_me.voidCompanionUnsafeDialogueLines")
      .defineListAllowEmpty(
         "voidCompanionUnsafeDialogueLines",
         List.of(
            "dialogue.find_me.void_companion_unsafe.0.call|dialogue.find_me.void_companion_unsafe.0.reply",
            "dialogue.find_me.void_companion_unsafe.1.call|dialogue.find_me.void_companion_unsafe.1.reply",
            "dialogue.find_me.void_companion_unsafe.2.call|dialogue.find_me.void_companion_unsafe.2.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> VOID_FLYING_MOUNT_DIALOGUE_LINES = BUILDER.comment(
         "[Flavor] Dialogue when a flying mount answers directly over the void. Format: first line|second line. Supports {player} and {name}."
      )
      .translation("config.find_me.voidFlyingMountDialogueLines")
      .defineListAllowEmpty(
         "voidFlyingMountDialogueLines",
         List.of(
            "dialogue.find_me.void_flying_mount.0.call|dialogue.find_me.void_flying_mount.0.reply",
            "dialogue.find_me.void_flying_mount.1.call|dialogue.find_me.void_flying_mount.1.reply",
            "dialogue.find_me.void_flying_mount.2.call|dialogue.find_me.void_flying_mount.2.reply",
            "dialogue.find_me.void_flying_mount.3.call|dialogue.find_me.void_flying_mount.3.reply"
         ),
         Config::isString
      );
   private static final ConfigValue<List<? extends String>> CUSTOM_MOUNTS = BUILDER.comment(
         "[Compatibility] Advanced: entity ids that should always be considered rideable mounts."
      )
      .translation("config.find_me.customMounts")
      .defineListAllowEmpty("customMounts", List.of(), Config::isString);
   private static final ConfigValue<List<? extends String>> CUSTOM_COMPANIONS = BUILDER.comment(
         "[Compatibility] Advanced: entity ids that should always be considered non-rideable companions."
      )
      .translation("config.find_me.customCompanions")
      .defineListAllowEmpty("customCompanions", List.of(), Config::isString);
   private static final ConfigValue<List<? extends String>> CUSTOM_MOVE_TYPES = BUILDER.comment(
         "[Compatibility] Preferred movement override format: entity_id=walk, entity_id=fly, or entity_id=swim."
      )
      .translation("config.find_me.customMoveTypes")
      .defineListAllowEmpty("customMoveTypes", List.of(), Config::isString);
   private static final ConfigValue<List<? extends String>> CUSTOM_FLYING = BUILDER.comment(
         "[Compatibility] Legacy movement override. Prefer customMoveTypes for new configs."
      )
      .translation("config.find_me.customFlying")
      .defineListAllowEmpty("customFlying", List.of(), Config::isString);
   private static final ConfigValue<List<? extends String>> CUSTOM_SWIMMING = BUILDER.comment(
         "[Compatibility] Legacy movement override. Prefer customMoveTypes for new configs."
      )
      .translation("config.find_me.customSwimming")
      .defineListAllowEmpty("customSwimming", List.of(), Config::isString);
   private static final ConfigValue<List<? extends String>> CUSTOM_WALKING = BUILDER.comment(
         "[Compatibility] Legacy movement override. Prefer customMoveTypes for new configs."
      )
      .translation("config.find_me.customWalking")
      .defineListAllowEmpty("customWalking", List.of(), Config::isString);
   public static final ModConfigSpec SPEC = BUILDER.build();
   public static int mountCooldownTicks;
   public static int companionCooldownTicks;
   public static int combatMountCooldownTicks;
   public static int returnCooldownTicks;
   public static int rescueMinFallDistance;
   public static int safeSearchRadius;
   public static boolean enableCreatureArrivalVoice;
   public static double creatureArrivalVoiceVolume;
   public static double magicEffectVolume;
   public static int postTeleportInvulnerabilityTicks;
   public static boolean enablePvpSummon;
   public static boolean enableCombatSpeedBoost;
   public static double combatSpeedBoostPercent;
   public static int combatSpeedBoostTicks;
   public static List<String> customMounts;
   public static List<String> customCompanions;
   public static List<String> customFlying;
   public static List<String> customSwimming;
   public static List<String> customWalking;
   public static Map<String, String> customMoveTypes;
   public static Map<String, Double> previewScaleOverrides;
   public static double guiOpacity;
   public static boolean enableVault;
   public static int vaultCapacity;
   public static boolean enableAutoBackups;
   public static int backupIntervalMinutes;
   public static int backupCapacity;
   public static boolean preventBoundCreatureDeathDrops;
   public static boolean enableSleepRevive;
   public static int sleepReviveCooldownMinutes;
   public static double sleepReviveChance;
   public static boolean sleepReviveSpawnEntity;
   public static boolean enableAutoBinding;
   public static boolean enableContractAnimation;
   public static boolean enableContractCinematicCamera;
   public static boolean debugDiagnostics;
   public static boolean enableDialogue;
   public static List<String> summonLines;
   public static List<String> rescueLines;
   public static List<String> combatRescueLines;
   public static List<String> mountSummonDialogueLines;
   public static List<String> companionSummonDialogueLines;
   public static List<String> mountRescueDialogueLines;
   public static List<String> companionCombatRescueDialogueLines;
   public static List<String> storageDialogueLines;
   public static List<String> homeSummonDialogueLines;
   public static List<String> homeStorageDialogueLines;
   public static List<String> homeSetDialogueLines;
   public static List<String> homeClearDialogueLines;
   public static List<String> registeredDialogueLines;
   public static List<String> sleepReviveDialogueLines;
   public static List<String> sleepReviveStoredDialogueLines;
   public static List<String> blockedDialogueLines;
   public static List<String> busyDialogueLines;
   public static List<String> cooldownDialogueLines;
   public static List<String> noRegisteredDialogueLines;
   public static List<String> unavailableDialogueLines;
   public static List<String> pvpDisabledDialogueLines;
   public static List<String> alreadyRidingDialogueLines;
   public static List<String> occupiedDialogueLines;
   public static List<String> tooHighDialogueLines;
   public static List<String> noWaterDialogueLines;
   public static List<String> voidMountUnsafeDialogueLines;
   public static List<String> voidCompanionUnsafeDialogueLines;
   public static List<String> voidFlyingMountDialogueLines;

   public static void register() {
      SPEC.load(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("find_me.json"));
      syncFromSpec();
   }

   public static List<Config.DialogueListOption> dialogueOptions() {
      return List.of(
         new Config.DialogueListOption("config.find_me.mountSummonDialogueLines", MOUNT_SUMMON_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.companionSummonDialogueLines", COMPANION_SUMMON_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.mountRescueDialogueLines", MOUNT_RESCUE_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.companionCombatRescueDialogueLines", COMPANION_COMBAT_RESCUE_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.storageDialogueLines", STORAGE_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.homeSummonDialogueLines", HOME_SUMMON_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.homeStorageDialogueLines", HOME_STORAGE_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.homeSetDialogueLines", HOME_SET_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.homeClearDialogueLines", HOME_CLEAR_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.registeredDialogueLines", REGISTERED_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.sleepReviveDialogueLines", SLEEP_REVIVE_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.sleepReviveStoredDialogueLines", SLEEP_REVIVE_STORED_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.blockedDialogueLines", BLOCKED_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.busyDialogueLines", BUSY_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.cooldownDialogueLines", COOLDOWN_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.noRegisteredDialogueLines", NO_REGISTERED_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.unavailableDialogueLines", UNAVAILABLE_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.pvpDisabledDialogueLines", PVP_DISABLED_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.alreadyRidingDialogueLines", ALREADY_RIDING_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.occupiedDialogueLines", OCCUPIED_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.tooHighDialogueLines", TOO_HIGH_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.noWaterDialogueLines", NO_WATER_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.voidMountUnsafeDialogueLines", VOID_MOUNT_UNSAFE_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.voidCompanionUnsafeDialogueLines", VOID_COMPANION_UNSAFE_DIALOGUE_LINES),
         new Config.DialogueListOption("config.find_me.voidFlyingMountDialogueLines", VOID_FLYING_MOUNT_DIALOGUE_LINES)
      );
   }

   public static List<String> dialogueValue(Config.DialogueListOption option) {
      return normalizeTextList((List<? extends String>)option.value().get());
   }

   public static List<String> defaultDialogueValue(Config.DialogueListOption option) {
      return normalizeTextList((List<? extends String>)option.value().getDefault());
   }

   public static void setDialogueValue(Config.DialogueListOption option, List<String> lines) {
      option.value().set(List.copyOf(lines));
      option.value().save();
      syncFromSpec();
   }

   private static boolean isString(Object value) {
      return value instanceof String;
   }

   public static void syncFromSpec() {
      mountCooldownTicks = (Integer)MOUNT_COOLDOWN_TICKS.get();
      companionCooldownTicks = (Integer)COMPANION_COOLDOWN_TICKS.get();
      combatMountCooldownTicks = (Integer)COMBAT_MOUNT_COOLDOWN_TICKS.get();
      returnCooldownTicks = (Integer)RETURN_COOLDOWN_TICKS.get();
      rescueMinFallDistance = (Integer)RESCUE_MIN_FALL_DISTANCE.get();
      safeSearchRadius = (Integer)SAFE_SEARCH_RADIUS.get();
      enableCreatureArrivalVoice = (Boolean)ENABLE_CREATURE_ARRIVAL_VOICE.get();
      creatureArrivalVoiceVolume = (Double)CREATURE_ARRIVAL_VOICE_VOLUME.get();
      magicEffectVolume = (Double)MAGIC_EFFECT_VOLUME.get();
      postTeleportInvulnerabilityTicks = (int)Math.round((Double)POST_TELEPORT_INVULNERABILITY_SECONDS.get() * 20.0);
      enablePvpSummon = (Boolean)ENABLE_PVP_SUMMON.get();
      enableCombatSpeedBoost = (Boolean)ENABLE_COMBAT_SPEED_BOOST.get();
      combatSpeedBoostPercent = (Double)COMBAT_SPEED_BOOST_PERCENT.get();
      combatSpeedBoostTicks = (Integer)COMBAT_SPEED_BOOST_TICKS.get();
      customMounts = normalizeList((List<? extends String>)CUSTOM_MOUNTS.get());
      customCompanions = normalizeList((List<? extends String>)CUSTOM_COMPANIONS.get());
      customFlying = normalizeList((List<? extends String>)CUSTOM_FLYING.get());
      customSwimming = normalizeList((List<? extends String>)CUSTOM_SWIMMING.get());
      customWalking = normalizeList((List<? extends String>)CUSTOM_WALKING.get());
      customMoveTypes = parseStringMap((List<? extends String>)CUSTOM_MOVE_TYPES.get());
      previewScaleOverrides = parseDoubleMap((List<? extends String>)PREVIEW_SCALE_OVERRIDES.get());
      guiOpacity = (Double)GUI_OPACITY.get();
      enableDialogue = (Boolean)ENABLE_DIALOGUE.get();
      summonLines = normalizeTextList((List<? extends String>)SUMMON_LINES.get());
      rescueLines = normalizeTextList((List<? extends String>)RESCUE_LINES.get());
      combatRescueLines = normalizeTextList((List<? extends String>)COMBAT_RESCUE_LINES.get());
      mountSummonDialogueLines = normalizeTextList((List<? extends String>)MOUNT_SUMMON_DIALOGUE_LINES.get());
      companionSummonDialogueLines = normalizeTextList((List<? extends String>)COMPANION_SUMMON_DIALOGUE_LINES.get());
      mountRescueDialogueLines = normalizeTextList((List<? extends String>)MOUNT_RESCUE_DIALOGUE_LINES.get());
      companionCombatRescueDialogueLines = normalizeTextList((List<? extends String>)COMPANION_COMBAT_RESCUE_DIALOGUE_LINES.get());
      storageDialogueLines = normalizeTextList((List<? extends String>)STORAGE_DIALOGUE_LINES.get());
      homeSummonDialogueLines = normalizeTextList((List<? extends String>)HOME_SUMMON_DIALOGUE_LINES.get());
      homeStorageDialogueLines = normalizeTextList((List<? extends String>)HOME_STORAGE_DIALOGUE_LINES.get());
      homeSetDialogueLines = normalizeTextList((List<? extends String>)HOME_SET_DIALOGUE_LINES.get());
      homeClearDialogueLines = normalizeTextList((List<? extends String>)HOME_CLEAR_DIALOGUE_LINES.get());
      registeredDialogueLines = normalizeTextList((List<? extends String>)REGISTERED_DIALOGUE_LINES.get());
      sleepReviveDialogueLines = normalizeTextList((List<? extends String>)SLEEP_REVIVE_DIALOGUE_LINES.get());
      sleepReviveStoredDialogueLines = normalizeTextList((List<? extends String>)SLEEP_REVIVE_STORED_DIALOGUE_LINES.get());
      blockedDialogueLines = normalizeTextList((List<? extends String>)BLOCKED_DIALOGUE_LINES.get());
      busyDialogueLines = normalizeTextList((List<? extends String>)BUSY_DIALOGUE_LINES.get());
      cooldownDialogueLines = normalizeTextList((List<? extends String>)COOLDOWN_DIALOGUE_LINES.get());
      noRegisteredDialogueLines = normalizeTextList((List<? extends String>)NO_REGISTERED_DIALOGUE_LINES.get());
      unavailableDialogueLines = normalizeTextList((List<? extends String>)UNAVAILABLE_DIALOGUE_LINES.get());
      pvpDisabledDialogueLines = normalizeTextList((List<? extends String>)PVP_DISABLED_DIALOGUE_LINES.get());
      alreadyRidingDialogueLines = normalizeTextList((List<? extends String>)ALREADY_RIDING_DIALOGUE_LINES.get());
      occupiedDialogueLines = normalizeTextList((List<? extends String>)OCCUPIED_DIALOGUE_LINES.get());
      tooHighDialogueLines = normalizeTextList((List<? extends String>)TOO_HIGH_DIALOGUE_LINES.get());
      noWaterDialogueLines = normalizeTextList((List<? extends String>)NO_WATER_DIALOGUE_LINES.get());
      voidMountUnsafeDialogueLines = normalizeTextList((List<? extends String>)VOID_MOUNT_UNSAFE_DIALOGUE_LINES.get());
      voidCompanionUnsafeDialogueLines = normalizeTextList((List<? extends String>)VOID_COMPANION_UNSAFE_DIALOGUE_LINES.get());
      voidFlyingMountDialogueLines = normalizeTextList((List<? extends String>)VOID_FLYING_MOUNT_DIALOGUE_LINES.get());
      enableVault = (Boolean)ENABLE_VAULT.get();
      vaultCapacity = (Integer)VAULT_CAPACITY.get();
      enableAutoBackups = (Boolean)ENABLE_AUTO_BACKUPS.get();
      backupIntervalMinutes = (Integer)BACKUP_INTERVAL_MINUTES.get();
      backupCapacity = (Integer)BACKUP_CAPACITY.get();
      preventBoundCreatureDeathDrops = (Boolean)PREVENT_BOUND_CREATURE_DEATH_DROPS.get();
      enableAutoBinding = (Boolean)ENABLE_AUTO_BINDING.get();
      enableContractAnimation = (Boolean)ENABLE_CONTRACT_ANIMATION.get();
      enableContractCinematicCamera = (Boolean)ENABLE_CONTRACT_CINEMATIC_CAMERA.get();
      debugDiagnostics = (Boolean)DEBUG_DIAGNOSTICS.get();
      enableSleepRevive = (Boolean)ENABLE_SLEEP_REVIVE.get();
      sleepReviveCooldownMinutes = (Integer)SLEEP_REVIVE_COOLDOWN_MINUTES.get();
      sleepReviveChance = (Double)SLEEP_REVIVE_CHANCE.get();
      sleepReviveSpawnEntity = (Boolean)SLEEP_REVIVE_SPAWN_ENTITY.get();
   }

   private static Map<String, String> parseStringMap(List<? extends String> entries) {
      HashMap<String, String> parsed = new HashMap<>();

      for (String string : entries) {
         String[] parts = splitOverride(string);
         if (parts != null) {
            parsed.put(parts[0], parts[1].toLowerCase(Locale.ROOT));
         }
      }

      return Map.copyOf(parsed);
   }

   private static List<String> normalizeList(List<? extends String> entries) {
      return entries.stream().filter(value -> value != null && !value.isBlank()).map(value -> value.trim().toLowerCase(Locale.ROOT)).toList();
   }

   private static List<String> normalizeTextList(List<? extends String> entries) {
      return entries.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
   }

   private static Map<String, Double> parseDoubleMap(List<? extends String> entries) {
      HashMap<String, Double> parsed = new HashMap<>();

      for (String string : entries) {
         String[] parts = splitOverride(string);
         if (parts != null) {
            try {
               parsed.put(parts[0], Double.parseDouble(parts[1]));
            } catch (NumberFormatException var6) {
            }
         }
      }

      return Map.copyOf(parsed);
   }

   private static String[] splitOverride(String entry) {
      if (entry == null) {
         return null;
      }

      int separator = entry.indexOf(61);
      if (separator > 0 && separator < entry.length() - 1) {
         String key = entry.substring(0, separator).trim().toLowerCase(Locale.ROOT);
         String value = entry.substring(separator + 1).trim();
         String[] stringArray;
         if (!key.isBlank() && !value.isBlank()) {
            String[] stringArray2 = new String[]{key, null};
            stringArray = stringArray2;
            stringArray2[1] = value;
         } else {
            stringArray = null;
         }

         return stringArray;
      } else {
         return null;
      }
   }

   public static Optional<String> customMoveType(String entityType) {
      return entityType == null ? Optional.empty() : Optional.ofNullable(customMoveTypes.get(entityType.toLowerCase(Locale.ROOT)));
   }

   static {
      BUILDER.comment(
            "[Dialogue] Editable summon, rescue, storage, and warning subtitle lines. Entries use first line|second line. Either side may be spoken by the player or creature. A blank first line such as |reply makes only the second line appear."
         )
         .push("dialogue");
      BUILDER.pop();
   }

   public record DialogueListOption(String labelKey, ConfigValue<List<? extends String>> value) {
   }
}
