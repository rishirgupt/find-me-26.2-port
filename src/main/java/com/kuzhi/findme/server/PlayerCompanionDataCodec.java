package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.SavedPosition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

final class PlayerCompanionDataCodec {
   private static final String ROOT = "find_me";

   private PlayerCompanionDataCodec() {
   }

   static PlayerCompanionData load(CompoundTag persistentData) {
      PlayerCompanionData data = new PlayerCompanionData();
      CompoundTag root = persistentData.getCompoundOrEmpty("find_me");

      for (CompanionKind kind : CompanionKind.values()) {
         ListTag list = root.getListOrEmpty(kind.name().toLowerCase() + "s");
         List<UUID> uuids = data.companions.get(kind);
         uuids.clear();

         for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            if (entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
               uuids.add(entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
            }
         }

         data.activeIndexes.put(kind, root.getIntOr(kind.name().toLowerCase() + "Index", 0));
         ListTag slots = root.getListOrEmpty(kind.name().toLowerCase() + "WheelSlots");
         List<UUID> slotUuids = data.wheelSlots.get(kind);
         slotUuids.clear();

         for (int i = 0; i < slots.size(); i++) {
            CompoundTag entry2 = slots.getCompoundOrEmpty(i);
            if (entry2.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
               slotUuids.add(entry2.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
            }
         }

         if (root.read(kind.name().toLowerCase() + "Previous", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            data.previous.put(kind, root.read(kind.name().toLowerCase() + "Previous", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
         }

         if (root.read(kind.name().toLowerCase() + "Deployed", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            data.setDeployed(kind, root.read(kind.name().toLowerCase() + "Deployed", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
         }

         ListTag deployedList = root.getListOrEmpty(kind.name().toLowerCase() + "DeployedList");

         for (int i = 0; i < deployedList.size(); i++) {
            CompoundTag entry3 = deployedList.getCompoundOrEmpty(i);
            if (entry3.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
               data.setDeployed(kind, entry3.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
            }
         }
      }

      ListTag origins = root.getListOrEmpty("origins");
      ListTag dead = root.getListOrEmpty("deadCompanions");

      for (int i = 0; i < dead.size(); i++) {
         CompoundTag deadEntry = dead.getCompoundOrEmpty(i);
         if (deadEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            UUID uuid = deadEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
            data.deadCompanions.add(uuid);
            if (deadEntry.contains("kind")) {
               try {
                  data.deadKinds.put(uuid, CompanionKind.valueOf(deadEntry.getStringOr("kind", "")));
               } catch (IllegalArgumentException ignored) {
                  data.deadKinds.put(uuid, CompanionKind.COMPANION);
               }
            }
         }
      }

      data.purgeDeadFromLiveLists();

      for (int i = 0; i < origins.size(); i++) {
         CompoundTag entry4 = origins.getCompoundOrEmpty(i);
         if (entry4.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && entry4.contains("position")) {
            data.origins.put(entry4.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), SavedPosition.load(entry4.getCompoundOrEmpty("position")));
         }
      }

      ListTag locations = root.getListOrEmpty("lastKnownPositions");

      for (int i = 0; i < locations.size(); i++) {
         CompoundTag entry5 = locations.getCompoundOrEmpty(i);
         if (entry5.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && entry5.contains("position")) {
            data.lastKnownPositions.put(entry5.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), SavedPosition.load(entry5.getCompoundOrEmpty("position")));
         }
      }

      ListTag homes = root.getListOrEmpty("homePositions");

      for (int i = 0; i < homes.size(); i++) {
         CompoundTag homeEntry = homes.getCompoundOrEmpty(i);
         if (homeEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && homeEntry.contains("position")) {
            data.homePositions.put(homeEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), SavedPosition.load(homeEntry.getCompoundOrEmpty("position")));
         }
      }

      ListTag homeNestBlocks = root.getListOrEmpty("homeNestBlocks");

      for (int i = 0; i < homeNestBlocks.size(); i++) {
         CompoundTag sourceEntry = homeNestBlocks.getCompoundOrEmpty(i);
         if (sourceEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && sourceEntry.contains("position")) {
            data.homeNestBlocks.put(sourceEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), SavedPosition.load(sourceEntry.getCompoundOrEmpty("position")));
         }
      }

      ListTag stored = root.getListOrEmpty("storedEntities");

      for (int i = 0; i < stored.size(); i++) {
         CompoundTag entry6 = stored.getCompoundOrEmpty(i);
         if (entry6.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && entry6.contains("entity")) {
            data.storedEntities.put(entry6.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), CompanionStorageService.sanitizedStoredTag(entry6.getCompoundOrEmpty("entity")));
         }
      }

      ListTag names = root.getListOrEmpty("displayNames");

      for (int i = 0; i < names.size(); i++) {
         CompoundTag entry7 = names.getCompoundOrEmpty(i);
         String name;
         if (entry7.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && !(name = entry7.getStringOr("name", "")).isBlank()) {
            data.displayNames.put(entry7.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), name);
         }
      }

      ListTag vaultList = root.getListOrEmpty("vault");

      for (int i = 0; i < vaultList.size(); i++) {
         CompoundTag vaultEntry = vaultList.getCompoundOrEmpty(i);
         if (vaultEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && vaultEntry.contains("entity")) {
            data.vault.add(loadVaultEntry(vaultEntry));
         }
      }

      ListTag backupList = root.getListOrEmpty("backups");

      for (int i = 0; i < backupList.size(); i++) {
         CompoundTag backupEntry = backupList.getCompoundOrEmpty(i);
         if (backupEntry.contains("state")) {
            data.backups
               .add(new PlayerCompanionData.BackupEntry(backupEntry.getLongOr("savedAt", 0L), backupEntry.getStringOr("reason", ""), backupEntry.getCompoundOrEmpty("state")));
         }
      }

      ListTag mountEligible = root.getListOrEmpty("mountEligible");

      for (int i = 0; i < mountEligible.size(); i++) {
         CompoundTag entry8 = mountEligible.getCompoundOrEmpty(i);
         if (entry8.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            data.markMountEligible(entry8.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
         }
      }

      ListTag vehicleMounts = root.getListOrEmpty("vehicleMounts");

      for (int i = 0; i < vehicleMounts.size(); i++) {
         CompoundTag entry8 = vehicleMounts.getCompoundOrEmpty(i);
         if (entry8.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            UUID uuid = entry8.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
            data.markVehicleMount(uuid);
            data.addVehicle(uuid);
         }
      }

      ListTag vehicles = root.getListOrEmpty("vehicles");
      data.vehicles.clear();

      for (int i = 0; i < vehicles.size(); i++) {
         CompoundTag vehicleEntry = vehicles.getCompoundOrEmpty(i);
         if (vehicleEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            UUID uuid = vehicleEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
            if (!data.vehicles.contains(uuid)) {
               data.vehicles.add(uuid);
            }
         }
      }

      for (UUID legacyVehicle : List.copyOf(data.vehicleMounts)) {
         if (!data.vehicles.contains(legacyVehicle)) {
            data.vehicles.add(legacyVehicle);
         }
      }

      boolean hasVehicleWheelSlots = root.contains("vehicleWheelSlots") || root.getBooleanOr("vehicleWheelSlotsConfigured", false);
      data.vehicleWheelSlotsConfigured = hasVehicleWheelSlots;
      ListTag vehicleSlots = root.getListOrEmpty("vehicleWheelSlots");
      data.vehicleWheelSlots.clear();

      for (int i = 0; i < vehicleSlots.size(); i++) {
         CompoundTag slotEntry = vehicleSlots.getCompoundOrEmpty(i);
         if (slotEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            UUID uuid = slotEntry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
            if (data.vehicles.contains(uuid) && !data.vehicleWheelSlots.contains(uuid)) {
               data.vehicleWheelSlots.add(uuid);
            }
         }
      }

      if (!hasVehicleWheelSlots && data.vehicleWheelSlots.isEmpty()) {
         data.vehicleWheelSlots.addAll(data.vehicles);
         data.vehicleWheelSlotsConfigured = true;
      }

      data.vehicleActiveIndex = root.getIntOr("vehicleIndex", 0);
      if (root.read("vehicleDeployed", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
         UUID deployedVehicle = root.read("vehicleDeployed", net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
         if (data.vehicles.contains(deployedVehicle)) {
            data.deployedVehicle = deployedVehicle;
         }
      }

      for (UUID vehicleUuid : data.vehicles) {
         data.companions.get(CompanionKind.MOUNT).remove(vehicleUuid);
         data.companions.get(CompanionKind.COMPANION).remove(vehicleUuid);
         data.wheelSlots.get(CompanionKind.MOUNT).remove(vehicleUuid);
         data.wheelSlots.get(CompanionKind.COMPANION).remove(vehicleUuid);
         data.clearDeployed(CompanionKind.MOUNT, vehicleUuid);
         data.clearDeployed(CompanionKind.COMPANION, vehicleUuid);
      }

      ListTag ignored = root.getListOrEmpty("autoBindIgnored");

      for (int i = 0; i < ignored.size(); i++) {
         CompoundTag entry = ignored.getCompoundOrEmpty(i);
         if (entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            data.ignoreAutoBind(entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), entry.getStringOr("name", ""));
         }
      }

      ListTag blacklistedTypes = root.getListOrEmpty("autoBindTypeBlacklist");

      for (int i = 0; i < blacklistedTypes.size(); i++) {
         CompoundTag blacklistEntry = blacklistedTypes.getCompoundOrEmpty(i);
         data.blacklistAutoBindType(blacklistEntry.getStringOr("type", ""));
      }

      data.mountReadyAt = root.getLongOr("mountReadyAt", 0L);
      data.companionReadyAt = root.getLongOr("companionReadyAt", 0L);
      return data;
   }

   static boolean hasMeaningfulRoot(CompoundTag persistentData) {
      return persistentData.contains("find_me") && isMeaningfulRoot(persistentData.getCompoundOrEmpty("find_me"));
   }

   static boolean hasRoot(CompoundTag persistentData) {
      return persistentData.contains("find_me");
   }

   static CompoundTag rootCopy(CompoundTag persistentData) {
      return persistentData.contains("find_me") ? persistentData.getCompoundOrEmpty("find_me").copy() : new CompoundTag();
   }

   static void putRoot(CompoundTag persistentData, CompoundTag root) {
      if (root != null) {
         persistentData.put("find_me", root.copy());
      }
   }

   static boolean isMeaningfulRoot(CompoundTag root) {
      if (root != null && !root.keySet().isEmpty()) {
         for (CompanionKind kind : CompanionKind.values()) {
            String prefix = kind.name().toLowerCase();
            if (!root.getListOrEmpty(prefix + "s").isEmpty()
               || !root.getListOrEmpty(prefix + "WheelSlots").isEmpty()
               || !root.getListOrEmpty(prefix + "DeployedList").isEmpty()
               || root.read(prefix + "Previous", net.minecraft.core.UUIDUtil.CODEC).isPresent()
               || root.read(prefix + "Deployed", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
               return true;
            }
         }

         return !root.getListOrEmpty("deadCompanions").isEmpty()
            || !root.getListOrEmpty("origins").isEmpty()
            || !root.getListOrEmpty("lastKnownPositions").isEmpty()
            || !root.getListOrEmpty("homePositions").isEmpty()
            || !root.getListOrEmpty("homeNestBlocks").isEmpty()
            || !root.getListOrEmpty("storedEntities").isEmpty()
            || !root.getListOrEmpty("displayNames").isEmpty()
            || !root.getListOrEmpty("vault").isEmpty()
            || !root.getListOrEmpty("backups").isEmpty()
            || !root.getListOrEmpty("mountEligible").isEmpty()
            || !root.getListOrEmpty("vehicleMounts").isEmpty()
            || !root.getListOrEmpty("vehicles").isEmpty()
            || !root.getListOrEmpty("vehicleWheelSlots").isEmpty()
            || root.read("vehicleDeployed", net.minecraft.core.UUIDUtil.CODEC).isPresent()
            || !root.getListOrEmpty("autoBindIgnored").isEmpty()
            || !root.getListOrEmpty("autoBindTypeBlacklist").isEmpty();
      } else {
         return false;
      }
   }

   static void save(PlayerCompanionData data, CompoundTag persistentData) {
      CompoundTag root = new CompoundTag();

      for (CompanionKind kind : CompanionKind.values()) {
         root.put(kind.name().toLowerCase() + "s", uuidList(data.companions.get(kind)));
         root.put(kind.name().toLowerCase() + "WheelSlots", uuidList(data.wheelSlots.get(kind)));
         root.putInt(kind.name().toLowerCase() + "Index", data.activeIndexes.getOrDefault(kind, 0));
         UUID previousUuid = data.previous.get(kind);
         if (previousUuid != null) {
            root.store(kind.name().toLowerCase() + "Previous", net.minecraft.core.UUIDUtil.CODEC, previousUuid);
         }

         UUID deployedUuid;
         if ((deployedUuid = data.deployed.get(kind)) != null) {
            root.store(kind.name().toLowerCase() + "Deployed", net.minecraft.core.UUIDUtil.CODEC, deployedUuid);
         }

         root.put(kind.name().toLowerCase() + "DeployedList", uuidList(data.deployedLists.get(kind)));
      }

      root.put("deadCompanions", deadList(data));
      root.put("origins", positionMap(data.origins));
      root.put("lastKnownPositions", positionMap(data.lastKnownPositions));
      root.put("homePositions", positionMap(data.homePositions));
      root.put("homeNestBlocks", positionMap(data.homeNestBlocks));
      root.put("storedEntities", storedEntityList(data.storedEntities));
      root.put("displayNames", displayNameList(data.displayNames));
      ListTag vaultList = new ListTag();

      for (PlayerCompanionData.VaultEntry vaultEntry : data.vault) {
         vaultList.add(saveVaultEntry(vaultEntry));
      }

      root.put("vault", vaultList);
      ListTag backupList = new ListTag();

      for (PlayerCompanionData.BackupEntry backup : data.backups) {
         CompoundTag backupTag = new CompoundTag();
         backupTag.putLong("savedAt", backup.savedAt());
         backupTag.putString("reason", backup.reason());
         backupTag.put("state", backup.state());
         backupList.add(backupTag);
      }

      root.put("backups", backupList);
      root.put("mountEligible", uuidList(data.mountEligible));
      root.put("vehicleMounts", uuidList(data.vehicleMounts));
      root.put("vehicles", uuidList(data.vehicles));
      root.put("vehicleWheelSlots", uuidList(data.vehicleWheelSlots));
      root.putBoolean("vehicleWheelSlotsConfigured", data.vehicleWheelSlotsConfigured || !data.vehicles.isEmpty());
      root.putInt("vehicleIndex", data.vehicleActiveIndex);
      if (data.deployedVehicle != null) {
         root.store("vehicleDeployed", net.minecraft.core.UUIDUtil.CODEC, data.deployedVehicle);
      }

      ListTag ignoredList = new ListTag();

      for (UUID uuid : data.autoBindIgnored) {
         CompoundTag entry = new CompoundTag();
         entry.store("uuid", net.minecraft.core.UUIDUtil.CODEC, uuid);
         String name = data.autoBindIgnoredNames.get(uuid);
         if (name != null && !name.isBlank()) {
            entry.putString("name", name);
         }

         ignoredList.add(entry);
      }

      root.put("autoBindIgnored", ignoredList);
      root.put("autoBindTypeBlacklist", stringValueList(data.autoBindTypeBlacklist, "type"));
      root.putLong("mountReadyAt", data.mountReadyAt);
      root.putLong("companionReadyAt", data.companionReadyAt);
      persistentData.put("find_me", root);
   }

   static CompoundTag createBackupState(PlayerCompanionData data) {
      CompoundTag root = new CompoundTag();

      for (CompanionKind kind : CompanionKind.values()) {
         root.put(kind.name().toLowerCase() + "s", uuidList(data.companions.get(kind)));
         root.put(kind.name().toLowerCase() + "WheelSlots", uuidList(data.wheelSlots.get(kind)));
         root.putInt(kind.name().toLowerCase() + "Index", data.activeIndexes.getOrDefault(kind, 0));
         UUID previousUuid = data.previous.get(kind);
         if (previousUuid != null) {
            root.store(kind.name().toLowerCase() + "Previous", net.minecraft.core.UUIDUtil.CODEC, previousUuid);
         }

         UUID deployedUuid = data.deployed.get(kind);
         if (deployedUuid != null) {
            root.store(kind.name().toLowerCase() + "Deployed", net.minecraft.core.UUIDUtil.CODEC, deployedUuid);
         }

         root.put(kind.name().toLowerCase() + "DeployedList", uuidList(data.deployedLists.get(kind)));
      }

      root.put("deadCompanions", deadList(data));
      root.put("origins", positionMap(data.origins));
      root.put("lastKnownPositions", positionMap(data.lastKnownPositions));
      root.put("homePositions", positionMap(data.homePositions));
      root.put("homeNestBlocks", positionMap(data.homeNestBlocks));
      root.put("storedEntities", storedEntityList(data.storedEntities));
      root.put("displayNames", displayNameList(data.displayNames));
      root.put("mountEligible", uuidList(data.mountEligible));
      root.put("vehicleMounts", uuidList(data.vehicleMounts));
      root.put("vehicles", uuidList(data.vehicles));
      root.put("vehicleWheelSlots", uuidList(data.vehicleWheelSlots));
      root.putBoolean("vehicleWheelSlotsConfigured", data.vehicleWheelSlotsConfigured || !data.vehicles.isEmpty());
      root.putInt("vehicleIndex", data.vehicleActiveIndex);
      if (data.deployedVehicle != null) {
         root.store("vehicleDeployed", net.minecraft.core.UUIDUtil.CODEC, data.deployedVehicle);
      }

      root.put("autoBindTypeBlacklist", stringValueList(data.autoBindTypeBlacklist, "type"));
      return root;
   }

   static void restoreBackupState(PlayerCompanionData data, CompoundTag root) {
      for (CompanionKind kind : CompanionKind.values()) {
         data.companions.get(kind).clear();
         data.companions.get(kind).addAll(readUuidList(root.getListOrEmpty(kind.name().toLowerCase() + "s")));
         data.wheelSlots.get(kind).clear();
         data.wheelSlots.get(kind).addAll(readUuidList(root.getListOrEmpty(kind.name().toLowerCase() + "WheelSlots")));
         data.activeIndexes.put(kind, root.getIntOr(kind.name().toLowerCase() + "Index", 0));
         data.previous.remove(kind);
         if (root.read(kind.name().toLowerCase() + "Previous", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            data.previous.put(kind, root.read(kind.name().toLowerCase() + "Previous", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
         }

         data.deployed.remove(kind);
         if (root.read(kind.name().toLowerCase() + "Deployed", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            data.deployed.put(kind, root.read(kind.name().toLowerCase() + "Deployed", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
         }

         data.deployedLists.get(kind).clear();
         data.deployedLists.get(kind).addAll(readUuidList(root.getListOrEmpty(kind.name().toLowerCase() + "DeployedList")));
      }

      data.deadCompanions.clear();
      data.deadKinds.clear();
      ListTag deadList = root.getListOrEmpty("deadCompanions");

      for (int i = 0; i < deadList.size(); i++) {
         CompoundTag entry = deadList.getCompoundOrEmpty(i);
         if (entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            UUID uuid = entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow();
            data.deadCompanions.add(uuid);

            try {
               data.deadKinds.put(uuid, CompanionKind.valueOf(entry.getStringOr("kind", "")));
            } catch (IllegalArgumentException ignored) {
               data.deadKinds.put(uuid, CompanionKind.COMPANION);
            }
         }
      }

      readPositionMap(root.getListOrEmpty("origins"), data.origins);
      readPositionMap(root.getListOrEmpty("lastKnownPositions"), data.lastKnownPositions);
      readPositionMap(root.getListOrEmpty("homePositions"), data.homePositions);
      readPositionMap(root.getListOrEmpty("homeNestBlocks"), data.homeNestBlocks);
      data.storedEntities.clear();
      ListTag storedList = root.getListOrEmpty("storedEntities");

      for (int i = 0; i < storedList.size(); i++) {
         CompoundTag entry = storedList.getCompoundOrEmpty(i);
         if (entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && entry.contains("entity")) {
            data.storedEntities.put(entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), CompanionStorageService.sanitizedStoredTag(entry.getCompoundOrEmpty("entity")));
         }
      }

      data.displayNames.clear();
      ListTag nameList = root.getListOrEmpty("displayNames");

      for (int i = 0; i < nameList.size(); i++) {
         CompoundTag entry = nameList.getCompoundOrEmpty(i);
         if (entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            data.setDisplayName(entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), entry.getStringOr("name", ""));
         }
      }

      data.mountEligible.clear();
      data.mountEligible.addAll(readUuidList(root.getListOrEmpty("mountEligible")));
      data.vehicleMounts.clear();
      data.vehicleMounts.addAll(readUuidList(root.getListOrEmpty("vehicleMounts")));
      data.vehicles.clear();
      data.vehicles.addAll(readUuidList(root.getListOrEmpty("vehicles")));

      for (UUID legacyVehicle : data.vehicleMounts) {
         if (!data.vehicles.contains(legacyVehicle)) {
            data.vehicles.add(legacyVehicle);
         }
      }

      data.vehicleWheelSlots.clear();
      boolean hasVehicleWheelSlots = root.contains("vehicleWheelSlots") || root.getBooleanOr("vehicleWheelSlotsConfigured", false);
      data.vehicleWheelSlotsConfigured = hasVehicleWheelSlots;
      data.vehicleWheelSlots.addAll(readUuidList(root.getListOrEmpty("vehicleWheelSlots")));
      data.vehicleWheelSlots.removeIf(uuid -> !data.vehicles.contains(uuid));
      if (!hasVehicleWheelSlots && data.vehicleWheelSlots.isEmpty()) {
         data.vehicleWheelSlots.addAll(data.vehicles);
         data.vehicleWheelSlotsConfigured = true;
      }

      data.vehicleActiveIndex = root.getIntOr("vehicleIndex", 0);
      data.deployedVehicle = root.read("vehicleDeployed", net.minecraft.core.UUIDUtil.CODEC).isPresent() ? root.read("vehicleDeployed", net.minecraft.core.UUIDUtil.CODEC).orElseThrow() : null;
      if (data.deployedVehicle != null && !data.vehicles.contains(data.deployedVehicle)) {
         data.deployedVehicle = null;
      }

      data.autoBindTypeBlacklist.clear();

      for (String entityType : readStringValueList(root.getListOrEmpty("autoBindTypeBlacklist"), "type")) {
         data.blacklistAutoBindType(entityType);
      }
   }

   private static ListTag deadList(PlayerCompanionData data) {
      ListTag list = new ListTag();

      for (UUID deadUuid : data.deadCompanions) {
         CompoundTag entry = new CompoundTag();
         entry.store("uuid", net.minecraft.core.UUIDUtil.CODEC, deadUuid);
         CompanionKind kind = data.deadKinds.get(deadUuid);
         if (kind != null) {
            entry.putString("kind", kind.name());
         }

         list.add(entry);
      }

      return list;
   }

   private static ListTag storedEntityList(Map<UUID, CompoundTag> storedEntities) {
      ListTag list = new ListTag();

      for (Entry<UUID, CompoundTag> stored : storedEntities.entrySet()) {
         CompoundTag entry = new CompoundTag();
         entry.store("uuid", net.minecraft.core.UUIDUtil.CODEC, stored.getKey());
         entry.put("entity", CompanionStorageService.sanitizedStoredTag(stored.getValue()));
         list.add(entry);
      }

      return list;
   }

   private static ListTag displayNameList(Map<UUID, String> displayNames) {
      ListTag list = new ListTag();

      for (Entry<UUID, String> displayName : displayNames.entrySet()) {
         CompoundTag entry = new CompoundTag();
         entry.store("uuid", net.minecraft.core.UUIDUtil.CODEC, displayName.getKey());
         entry.putString("name", displayName.getValue());
         list.add(entry);
      }

      return list;
   }

   private static ListTag uuidList(Collection<UUID> uuids) {
      ListTag list = new ListTag();

      for (UUID uuid : uuids) {
         CompoundTag entry = new CompoundTag();
         entry.store("uuid", net.minecraft.core.UUIDUtil.CODEC, uuid);
         list.add(entry);
      }

      return list;
   }

   private static ListTag stringValueList(Collection<String> values, String key) {
      ListTag list = new ListTag();

      for (String value : values) {
         if (value != null && !value.isBlank()) {
            CompoundTag entry = new CompoundTag();
            entry.putString(key, value);
            list.add(entry);
         }
      }

      return list;
   }

   private static List<String> readStringValueList(ListTag list, String key) {
      ArrayList<String> values = new ArrayList<>();

      for (int i = 0; i < list.size(); i++) {
         String value = list.getCompoundOrEmpty(i).getStringOr(key, "");
         if (!value.isBlank()) {
            values.add(value);
         }
      }

      return values;
   }

   private static List<UUID> readUuidList(ListTag list) {
      ArrayList<UUID> uuids = new ArrayList<>();

      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompoundOrEmpty(i);
         if (entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent()) {
            uuids.add(entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow());
         }
      }

      return uuids;
   }

   private static ListTag positionMap(Map<UUID, SavedPosition> positions) {
      ListTag list = new ListTag();

      for (Entry<UUID, SavedPosition> position : positions.entrySet()) {
         CompoundTag entry = new CompoundTag();
         entry.store("uuid", net.minecraft.core.UUIDUtil.CODEC, position.getKey());
         entry.put("position", position.getValue().save());
         list.add(entry);
      }

      return list;
   }

   private static void readPositionMap(ListTag list, Map<UUID, SavedPosition> target) {
      target.clear();

      for (int i = 0; i < list.size(); i++) {
         CompoundTag entry = list.getCompoundOrEmpty(i);
         if (entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).isPresent() && entry.contains("position")) {
            target.put(entry.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(), SavedPosition.load(entry.getCompoundOrEmpty("position")));
         }
      }
   }

   private static PlayerCompanionData.VaultEntry loadVaultEntry(CompoundTag tag) {
      CompanionKind kind;
      try {
         kind = CompanionKind.valueOf(tag.getStringOr("kind", ""));
      } catch (IllegalArgumentException ignored) {
         kind = CompanionKind.COMPANION;
      }

      return new PlayerCompanionData.VaultEntry(
         tag.read("uuid", net.minecraft.core.UUIDUtil.CODEC).orElseThrow(),
         kind,
         tag.getStringOr("entityType", ""),
         tag.getStringOr("name", ""),
         tag.getCompoundOrEmpty("entity"),
         tag.getLongOr("savedAt", 0L),
         tag.getStringOr("reason", "")
      );
   }

   private static CompoundTag saveVaultEntry(PlayerCompanionData.VaultEntry entry) {
      CompoundTag tag = new CompoundTag();
      tag.store("uuid", net.minecraft.core.UUIDUtil.CODEC, entry.uuid());
      tag.putString("kind", entry.kind().name());
      tag.putString("entityType", entry.entityType());
      tag.putString("name", entry.name());
      tag.put("entity", entry.entityTag());
      tag.putLong("savedAt", entry.savedAt());
      tag.putString("reason", entry.reason());
      return tag;
   }
}
