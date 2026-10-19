package com.kuzhi.findme.server;

import com.kuzhi.findme.common.CompanionKind;
import com.kuzhi.findme.common.SavedPosition;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.nbt.CompoundTag;

public class PlayerCompanionData {
   private static final int VAULT_LIMIT_DEFAULT = 30;
   private static final int BACKUP_LIMIT_DEFAULT = 6;
   final Map<CompanionKind, List<UUID>> companions = new EnumMap<>(CompanionKind.class);
   final Map<CompanionKind, List<UUID>> wheelSlots = new EnumMap<>(CompanionKind.class);
   final Map<CompanionKind, Integer> activeIndexes = new EnumMap<>(CompanionKind.class);
   final Map<CompanionKind, UUID> previous = new EnumMap<>(CompanionKind.class);
   final Map<CompanionKind, UUID> deployed = new EnumMap<>(CompanionKind.class);
   final Map<CompanionKind, List<UUID>> deployedLists = new EnumMap<>(CompanionKind.class);
   final List<UUID> deadCompanions = new ArrayList<>();
   final Map<UUID, CompanionKind> deadKinds = new HashMap<>();
   final Map<UUID, SavedPosition> origins = new HashMap<>();
   final Map<UUID, SavedPosition> lastKnownPositions = new HashMap<>();
   final Map<UUID, SavedPosition> homePositions = new HashMap<>();
   final Map<UUID, SavedPosition> homeNestBlocks = new HashMap<>();
   final Map<UUID, CompoundTag> storedEntities = new HashMap<>();
   final Map<UUID, String> displayNames = new HashMap<>();
   final List<PlayerCompanionData.VaultEntry> vault = new ArrayList<>();
   final List<PlayerCompanionData.BackupEntry> backups = new ArrayList<>();
   final Map<UUID, String> autoBindIgnoredNames = new HashMap<>();
   final Set<UUID> mountEligible = new HashSet<>();
   final Set<UUID> vehicleMounts = new HashSet<>();
   final List<UUID> vehicles = new ArrayList<>();
   final List<UUID> vehicleWheelSlots = new ArrayList<>();
   final Set<UUID> autoBindIgnored = new HashSet<>();
   final Set<String> autoBindTypeBlacklist = new HashSet<>();
   boolean vehicleWheelSlotsConfigured;
   UUID deployedVehicle;
   int vehicleActiveIndex;
   long mountReadyAt;
   long companionReadyAt;

   public PlayerCompanionData() {
      for (CompanionKind kind : CompanionKind.values()) {
         this.companions.put(kind, new ArrayList<>());
         this.wheelSlots.put(kind, new ArrayList<>());
         this.deployedLists.put(kind, new ArrayList<>());
         this.activeIndexes.put(kind, 0);
      }
   }

   public static PlayerCompanionData load(CompoundTag persistentData) {
      return PlayerCompanionDataCodec.load(persistentData);
   }

   public void save(CompoundTag persistentData) {
      PlayerCompanionDataCodec.save(this, persistentData);
   }

   public boolean add(CompanionKind kind, UUID uuid) {
      List<UUID> list = this.companions.get(kind);
      boolean clearedDead = this.deadCompanions.remove(uuid);
      clearedDead |= this.deadKinds.remove(uuid) != null;
      if (list.contains(uuid)) {
         return clearedDead;
      }

      CompanionKind other = other(kind);
      this.companions.get(other).remove(uuid);
      this.clearDeployed(other, uuid);
      list.add(uuid);
      if (kind == CompanionKind.MOUNT) {
         this.markMountEligible(uuid);
      }

      this.activeIndexes.put(kind, list.size() - 1);
      return true;
   }

   public boolean contains(UUID uuid) {
      for (List<UUID> list : this.companions.values()) {
         if (list.contains(uuid)) {
            return true;
         }
      }

      return false;
   }

   public boolean contains(CompanionKind kind, UUID uuid) {
      return this.companions.get(kind).contains(uuid);
   }

   public Optional<UUID> active(CompanionKind kind) {
      List<UUID> list = this.companions.get(kind);
      if (list.isEmpty()) {
         return Optional.empty();
      }

      int index = Math.floorMod(this.activeIndexes.getOrDefault(kind, 0), list.size());
      this.activeIndexes.put(kind, index);
      return Optional.of(list.get(index));
   }

   public List<UUID> list(CompanionKind kind) {
      return List.copyOf(this.companions.get(kind));
   }

   public int activeIndex(CompanionKind kind) {
      List<UUID> list = this.companions.get(kind);
      if (list.isEmpty()) {
         return -1;
      }

      int index = Math.floorMod(this.activeIndexes.getOrDefault(kind, 0), list.size());
      this.activeIndexes.put(kind, index);
      return index;
   }

   public boolean setActiveIndex(CompanionKind kind, int index) {
      List<UUID> list = this.companions.get(kind);
      if (index >= 0 && index < list.size()) {
         this.activeIndexes.put(kind, index);
         return true;
      } else {
         return false;
      }
   }

   public boolean setActiveUuid(CompanionKind kind, UUID uuid) {
      List<UUID> list = this.companions.get(kind);
      int index = list.indexOf(uuid);
      if (index < 0) {
         return false;
      }

      this.activeIndexes.put(kind, index);
      return true;
   }

   public Optional<UUID> uuidAt(CompanionKind kind, int index) {
      List<UUID> list = this.companions.get(kind);
      return index >= 0 && index < list.size() ? Optional.of(list.get(index)) : Optional.empty();
   }

   public Optional<UUID> previous(CompanionKind kind) {
      return Optional.ofNullable(this.previous.get(kind));
   }

   public void rememberPrevious(CompanionKind kind, UUID uuid) {
      this.previous.put(kind, uuid);
   }

   public Optional<UUID> deployed(CompanionKind kind) {
      List<UUID> list = this.deployedLists.get(kind);
      return list != null && !list.isEmpty() ? Optional.of(list.get(0)) : Optional.ofNullable(this.deployed.get(kind));
   }

   public void setDeployed(CompanionKind kind, UUID uuid) {
      List<UUID> list = this.deployedLists.get(kind);
      if (kind == CompanionKind.MOUNT) {
         list.clear();
         list.add(uuid);
         this.deployed.put(kind, uuid);
      } else {
         list.remove(uuid);
         list.add(uuid);
         this.deployed.put(kind, list.get(0));
      }
   }

   public void clearDeployed(CompanionKind kind) {
      this.deployedLists.get(kind).clear();
      this.deployed.remove(kind);
   }

   public void clearDeployed(CompanionKind kind, UUID uuid) {
      List<UUID> list = this.deployedLists.get(kind);
      list.remove(uuid);
      UUID first = list.isEmpty() ? null : list.get(0);
      if (first == null) {
         this.deployed.remove(kind);
      } else {
         this.deployed.put(kind, first);
      }
   }

   public boolean isDeployed(CompanionKind kind, UUID uuid) {
      return this.deployedLists.get(kind).contains(uuid);
   }

   public List<UUID> deployedList(CompanionKind kind) {
      return List.copyOf(this.deployedLists.get(kind));
   }

   public void cycle(CompanionKind kind, int delta) {
      List<UUID> list = this.companions.get(kind);
      if (!list.isEmpty()) {
         this.activeIndexes.put(kind, Math.floorMod(this.activeIndexes.getOrDefault(kind, 0) + delta, list.size()));
      }
   }

   public void remove(UUID uuid) {
      this.vaultSnapshot(uuid, this.kindOf(uuid).orElse(CompanionKind.COMPANION), "remove", 30);

      for (List<UUID> list : this.companions.values()) {
         list.remove(uuid);
      }

      this.origins.remove(uuid);
      this.lastKnownPositions.remove(uuid);
      this.homePositions.remove(uuid);
      this.homeNestBlocks.remove(uuid);
      this.storedEntities.remove(uuid);
      this.displayNames.remove(uuid);

      for (List<UUID> slots : this.wheelSlots.values()) {
         slots.remove(uuid);
      }

      this.deployed.values().removeIf(uuid::equals);

      for (List<UUID> list : this.deployedLists.values()) {
         list.remove(uuid);
      }

      this.mountEligible.remove(uuid);
      this.vehicleMounts.remove(uuid);
      this.vehicles.remove(uuid);
      this.vehicleWheelSlots.remove(uuid);
      if (uuid.equals(this.deployedVehicle)) {
         this.deployedVehicle = null;
      }

      this.deadCompanions.remove(uuid);
      this.deadKinds.remove(uuid);
   }

   void replaceUuid(UUID oldUuid, UUID newUuid) {
      if (oldUuid != null && newUuid != null && !oldUuid.equals(newUuid)) {
         for (List<UUID> list : this.companions.values()) {
            replaceInList(list, oldUuid, newUuid);
         }

         for (List<UUID> list : this.wheelSlots.values()) {
            replaceInList(list, oldUuid, newUuid);
         }

         for (List<UUID> list : this.deployedLists.values()) {
            replaceInList(list, oldUuid, newUuid);
         }

         for (Entry<CompanionKind, UUID> entry : new ArrayList<>(this.previous.entrySet())) {
            if (oldUuid.equals(entry.getValue())) {
               this.previous.put(entry.getKey(), newUuid);
            }
         }

         for (Entry<CompanionKind, UUID> entry : new ArrayList<>(this.deployed.entrySet())) {
            if (oldUuid.equals(entry.getValue())) {
               this.deployed.put(entry.getKey(), newUuid);
            }
         }

         moveMapEntry(this.origins, oldUuid, newUuid);
         moveMapEntry(this.lastKnownPositions, oldUuid, newUuid);
         moveMapEntry(this.homePositions, oldUuid, newUuid);
         moveMapEntry(this.homeNestBlocks, oldUuid, newUuid);
         moveMapEntry(this.storedEntities, oldUuid, newUuid);
         moveMapEntry(this.displayNames, oldUuid, newUuid);
         replaceInList(this.deadCompanions, oldUuid, newUuid);
         CompanionKind deadKind = this.deadKinds.remove(oldUuid);
         if (deadKind != null) {
            this.deadKinds.put(newUuid, deadKind);
         }

         if (this.mountEligible.remove(oldUuid)) {
            this.mountEligible.add(newUuid);
         }

         if (this.vehicleMounts.remove(oldUuid)) {
            this.vehicleMounts.add(newUuid);
         }

         replaceInList(this.vehicles, oldUuid, newUuid);
         replaceInList(this.vehicleWheelSlots, oldUuid, newUuid);
         if (oldUuid.equals(this.deployedVehicle)) {
            this.deployedVehicle = newUuid;
         }

         if (this.autoBindIgnored.remove(oldUuid)) {
            this.autoBindIgnored.add(newUuid);
         }

         moveMapEntry(this.autoBindIgnoredNames, oldUuid, newUuid);
      }
   }

   private static void replaceInList(List<UUID> list, UUID oldUuid, UUID newUuid) {
      for (int i = 0; i < list.size(); i++) {
         if (oldUuid.equals(list.get(i))) {
            list.set(i, newUuid);
         }
      }
   }

   private static <T> void moveMapEntry(Map<UUID, T> map, UUID oldUuid, UUID newUuid) {
      if (map.containsKey(oldUuid)) {
         T value = map.remove(oldUuid);
         map.put(newUuid, value);
      }
   }

   public Optional<CompanionKind> kindOf(UUID uuid) {
      for (CompanionKind kind : CompanionKind.values()) {
         if (this.companions.get(kind).contains(uuid)) {
            return Optional.of(kind);
         }
      }

      return Optional.ofNullable(this.deadKinds.get(uuid));
   }

   public boolean markDead(UUID uuid, CompanionKind kind) {
      return PlayerCompanionDeadService.markDead(this, uuid, kind, 30);
   }

   public List<UUID> deadList() {
      return PlayerCompanionDeadService.deadList(this);
   }

   public Optional<UUID> deadUuidAt(int index) {
      return PlayerCompanionDeadService.deadUuidAt(this, index);
   }

   public boolean removeDeadAt(int index) {
      return PlayerCompanionDeadService.removeDeadAt(this, index, 30);
   }

   void purgeDeadFromLiveLists() {
      PlayerCompanionDeadService.purgeDeadFromLiveLists(this);
   }

   public void markMountEligible(UUID uuid) {
      this.mountEligible.add(uuid);
   }

   public boolean isMountEligible(UUID uuid) {
      return this.mountEligible.contains(uuid);
   }

   public void markVehicleMount(UUID uuid) {
      this.vehicleMounts.add(uuid);
      this.markMountEligible(uuid);
   }

   public boolean isVehicleMount(UUID uuid) {
      return this.vehicleMounts.contains(uuid);
   }

   public boolean addVehicle(UUID uuid) {
      boolean changed = false;

      for (List<UUID> list : this.companions.values()) {
         changed |= list.remove(uuid);
      }

      for (List<UUID> slots : this.wheelSlots.values()) {
         slots.remove(uuid);
      }

      this.deadCompanions.remove(uuid);
      this.deadKinds.remove(uuid);
      this.vehicleMounts.remove(uuid);
      if (!this.vehicles.contains(uuid)) {
         this.vehicles.add(uuid);
         changed = true;
      }

      this.vehicleWheelSlotsConfigured = true;
      if (!this.vehicleWheelSlots.contains(uuid)) {
         this.vehicleWheelSlots.add(uuid);
         changed = true;
      }

      this.vehicleActiveIndex = this.vehicleWheelSlots.indexOf(uuid);
      if (this.vehicleActiveIndex < 0) {
         this.vehicleActiveIndex = 0;
      }

      return changed;
   }

   public boolean containsVehicle(UUID uuid) {
      return this.vehicles.contains(uuid);
   }

   public List<UUID> vehicleList() {
      return List.copyOf(this.vehicles);
   }

   public List<UUID> vehicleWheelOrder() {
      this.vehicleWheelSlotsConfigured = true;
      this.vehicleWheelSlots.removeIf(uuid -> !this.vehicles.contains(uuid));
      return List.copyOf(this.vehicleWheelSlots);
   }

   public List<UUID> vehicleWarehouseOrder() {
      this.vehicleWheelOrder();
      ArrayList<UUID> warehouse = new ArrayList<>();

      for (UUID uuid : this.vehicles) {
         if (!this.vehicleWheelSlots.contains(uuid)) {
            warehouse.add(uuid);
         }
      }

      return List.copyOf(warehouse);
   }

   public int activeVehicleIndex() {
      if (this.vehicleWheelSlots.isEmpty()) {
         return -1;
      }

      this.vehicleActiveIndex = Math.floorMod(this.vehicleActiveIndex, this.vehicleWheelSlots.size());
      return this.vehicleActiveIndex;
   }

   public Optional<UUID> activeVehicle() {
      int index = this.activeVehicleIndex();
      return index < 0 ? Optional.empty() : Optional.of(this.vehicleWheelSlots.get(index));
   }

   public Optional<UUID> vehicleWheelUuidAt(int index) {
      this.vehicleWheelOrder();
      return index >= 0 && index < this.vehicleWheelSlots.size() ? Optional.of(this.vehicleWheelSlots.get(index)) : Optional.empty();
   }

   public boolean setActiveVehicleIndex(int index) {
      this.vehicleWheelOrder();
      if (index >= 0 && index < this.vehicleWheelSlots.size()) {
         this.vehicleActiveIndex = index;
         return true;
      } else {
         return false;
      }
   }

   public boolean setActiveVehicleUuid(UUID uuid) {
      this.vehicleWheelOrder();
      int index = this.vehicleWheelSlots.indexOf(uuid);
      if (index < 0) {
         return false;
      }

      this.vehicleActiveIndex = index;
      return true;
   }

   public void setDeployedVehicle(UUID uuid) {
      this.deployedVehicle = uuid;
   }

   public Optional<UUID> deployedVehicle() {
      return Optional.ofNullable(this.deployedVehicle);
   }

   public boolean isVehicleDeployed(UUID uuid) {
      return uuid != null && uuid.equals(this.deployedVehicle);
   }

   public void clearDeployedVehicle(UUID uuid) {
      if (uuid == null || uuid.equals(this.deployedVehicle)) {
         this.deployedVehicle = null;
      }
   }

   public boolean addVehicleWheelSlot(UUID uuid) {
      this.vehicleWheelSlotsConfigured = true;
      if (this.vehicles.contains(uuid) && !this.vehicleWheelSlots.contains(uuid)) {
         this.vehicleWheelSlots.add(uuid);
         return true;
      } else {
         return false;
      }
   }

   public boolean removeVehicleWheelSlot(int index) {
      this.vehicleWheelOrder();
      this.vehicleWheelSlotsConfigured = true;
      if (index >= 0 && index < this.vehicleWheelSlots.size()) {
         this.vehicleWheelSlots.remove(index);
         if (!this.vehicleWheelSlots.isEmpty()) {
            this.vehicleActiveIndex = Math.min(this.vehicleActiveIndex, this.vehicleWheelSlots.size() - 1);
         } else {
            this.vehicleActiveIndex = 0;
         }

         return true;
      } else {
         return false;
      }
   }

   public boolean reorderVehicleWheel(int from, int to) {
      this.vehicleWheelOrder();
      this.vehicleWheelSlotsConfigured = true;
      if (from >= 0 && from < this.vehicleWheelSlots.size() && to >= 0 && to < this.vehicleWheelSlots.size()) {
         UUID uuid = this.vehicleWheelSlots.remove(from);
         this.vehicleWheelSlots.add(to, uuid);
         this.vehicleActiveIndex = to;
         return true;
      } else {
         return false;
      }
   }

   public void ignoreAutoBind(UUID uuid) {
      PlayerCompanionAutoBindIgnoreService.ignore(this, uuid);
   }

   public void ignoreAutoBind(UUID uuid, String name) {
      PlayerCompanionAutoBindIgnoreService.ignore(this, uuid, name);
   }

   public boolean isAutoBindIgnored(UUID uuid) {
      return PlayerCompanionAutoBindIgnoreService.isIgnored(this, uuid);
   }

   public List<UUID> autoBindIgnoredList() {
      return PlayerCompanionAutoBindIgnoreService.ignoredList(this);
   }

   public boolean removeAutoBindIgnored(int index) {
      return PlayerCompanionAutoBindIgnoreService.removeIgnored(this, index);
   }

   public void clearAutoBindIgnored() {
      PlayerCompanionAutoBindIgnoreService.clearIgnored(this);
   }

   public String autoBindIgnoredName(UUID uuid) {
      return PlayerCompanionAutoBindIgnoreService.ignoredName(this, uuid);
   }

   public void blacklistAutoBindType(String entityType) {
      PlayerCompanionAutoBindIgnoreService.blacklistType(this, entityType);
   }

   public boolean isAutoBindTypeBlacklisted(String entityType) {
      return PlayerCompanionAutoBindIgnoreService.isTypeBlacklisted(this, entityType);
   }

   public List<String> autoBindTypeBlacklist() {
      return PlayerCompanionAutoBindIgnoreService.blacklistedTypes(this);
   }

   public boolean removeAutoBindTypeBlacklist(int index) {
      return PlayerCompanionAutoBindIgnoreService.removeBlacklistedType(this, index);
   }

   public void setOrigin(UUID uuid, SavedPosition position) {
      PlayerCompanionEntityStateService.setOrigin(this, uuid, position);
   }

   public Optional<SavedPosition> origin(UUID uuid) {
      return PlayerCompanionEntityStateService.origin(this, uuid);
   }

   public void setLastKnownPosition(UUID uuid, SavedPosition position) {
      PlayerCompanionEntityStateService.setLastKnownPosition(this, uuid, position);
   }

   public Optional<SavedPosition> lastKnownPosition(UUID uuid) {
      return PlayerCompanionEntityStateService.lastKnownPosition(this, uuid);
   }

   public void setHomePosition(UUID uuid, SavedPosition position) {
      PlayerCompanionEntityStateService.setHomePosition(this, uuid, position);
   }

   public Optional<SavedPosition> homePosition(UUID uuid) {
      return PlayerCompanionEntityStateService.homePosition(this, uuid);
   }

   public void clearHomePosition(UUID uuid) {
      PlayerCompanionEntityStateService.clearHomePosition(this, uuid);
   }

   public void setHomeNestBlock(UUID uuid, SavedPosition position) {
      PlayerCompanionEntityStateService.setHomeNestBlock(this, uuid, position);
   }

   public Optional<SavedPosition> homeNestBlock(UUID uuid) {
      return PlayerCompanionEntityStateService.homeNestBlock(this, uuid);
   }

   public void clearHomeNestBlock(UUID uuid) {
      PlayerCompanionEntityStateService.clearHomeNestBlock(this, uuid);
   }

   public int clearHomesForNestBlock(SavedPosition source) {
      return PlayerCompanionEntityStateService.clearHomesForNestBlock(this, source);
   }

   public void storeEntity(UUID uuid, CompoundTag tag) {
      PlayerCompanionEntityStateService.storeEntity(this, uuid, tag);
   }

   public Optional<CompoundTag> storedEntity(UUID uuid) {
      return PlayerCompanionEntityStateService.storedEntity(this, uuid);
   }

   public Optional<String> displayName(UUID uuid) {
      return PlayerCompanionEntityStateService.displayName(this, uuid);
   }

   public void setDisplayName(UUID uuid, String name) {
      PlayerCompanionEntityStateService.setDisplayName(this, uuid, name);
   }

   public void removeStoredEntity(UUID uuid) {
      PlayerCompanionEntityStateService.removeStoredEntity(this, uuid);
   }

   public void addVaultSnapshot(UUID uuid, CompanionKind kind, String name, CompoundTag entityTag, long savedAt, String reason, int limit) {
      PlayerCompanionArchiveService.addVaultSnapshot(this, uuid, kind, name, entityTag, savedAt, reason, limit);
   }

   public void vaultSnapshot(UUID uuid, CompanionKind kind, String reason, int limit) {
      PlayerCompanionArchiveService.vaultSnapshot(this, uuid, kind, reason, limit);
   }

   public List<PlayerCompanionData.VaultEntry> vaultList() {
      return PlayerCompanionArchiveService.vaultList(this);
   }

   public Optional<PlayerCompanionData.VaultEntry> vaultAt(int index) {
      return PlayerCompanionArchiveService.vaultAt(this, index);
   }

   public boolean clearVault() {
      return PlayerCompanionArchiveService.clearVault(this);
   }

   public boolean restoreVaultAt(int index) {
      return PlayerCompanionArchiveService.restoreVaultAt(this, index);
   }

   public void createBackup(long savedAt, String reason, int limit) {
      PlayerCompanionArchiveService.createBackup(this, savedAt, reason, limit);
   }

   public List<PlayerCompanionData.BackupEntry> backupList() {
      return PlayerCompanionArchiveService.backupList(this);
   }

   public Optional<PlayerCompanionData.BackupEntry> backupAt(int index) {
      return PlayerCompanionArchiveService.backupAt(this, index);
   }

   public boolean restoreBackupAt(int index) {
      return PlayerCompanionArchiveService.restoreBackupAt(this, index);
   }

   public void restoreBackup(PlayerCompanionData.BackupEntry backup) {
      PlayerCompanionArchiveService.restoreBackup(this, backup);
   }

   public long lastBackupAt() {
      return PlayerCompanionArchiveService.lastBackupAt(this);
   }

   public void trimVault(int limit) {
      PlayerCompanionArchiveService.trimVault(this, limit);
   }

   public void trimBackups(int limit) {
      PlayerCompanionArchiveService.trimBackups(this, limit);
   }

   public List<UUID> wheelOrder(CompanionKind kind) {
      return PlayerCompanionWheelService.wheelOrder(this, kind);
   }

   public int activeWheelIndex(CompanionKind kind) {
      return PlayerCompanionWheelService.activeWheelIndex(this, kind);
   }

   public Optional<UUID> wheelUuidAt(CompanionKind kind, int index) {
      return PlayerCompanionWheelService.wheelUuidAt(this, kind, index);
   }

   public boolean setWheelSlot(CompanionKind kind, int sourceIndex, int slotIndex) {
      return PlayerCompanionWheelService.setWheelSlot(this, kind, sourceIndex, slotIndex);
   }

   public boolean reorder(CompanionKind kind, int from, int to) {
      return PlayerCompanionWheelService.reorder(this, kind, from, to);
   }

   public long readyAt(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? this.mountReadyAt : this.companionReadyAt;
   }

   public void setReadyAt(CompanionKind kind, long tick) {
      if (kind == CompanionKind.MOUNT) {
         this.mountReadyAt = tick;
      } else {
         this.companionReadyAt = tick;
      }
   }

   private static CompanionKind other(CompanionKind kind) {
      return kind == CompanionKind.MOUNT ? CompanionKind.COMPANION : CompanionKind.MOUNT;
   }

   public record BackupEntry(long savedAt, String reason, CompoundTag state) {
      public BackupEntry {
         reason = reason == null ? "" : reason;
         state = state == null ? new CompoundTag() : state.copy();
      }

      public CompoundTag state() {
         return this.state.copy();
      }
   }

   public record VaultEntry(UUID uuid, CompanionKind kind, String entityType, String name, CompoundTag entityTag, long savedAt, String reason) {
      public VaultEntry {
         entityType = entityType == null ? "" : entityType;
         name = name == null ? "" : name;
         reason = reason == null ? "" : reason;
         entityTag = entityTag == null ? new CompoundTag() : entityTag.copy();
      }

      public CompoundTag entityTag() {
         return this.entityTag.copy();
      }
   }
}
