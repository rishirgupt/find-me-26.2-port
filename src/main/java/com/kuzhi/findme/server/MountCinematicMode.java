package com.kuzhi.findme.server;

enum MountCinematicMode {
   NORMAL_SUMMON,
   MOUNT_SWITCH,
   FALL_RESCUE_FLY,
   FALL_RESCUE_WALK,
   FALL_RESCUE_SWIM,
   AIR_TO_FLY_SWITCH,
   AIR_TO_WALK_SWITCH,
   AIR_TO_SWIM_SWITCH;

   boolean isRescue() {
      return this == FALL_RESCUE_FLY
         || this == FALL_RESCUE_WALK
         || this == FALL_RESCUE_SWIM
         || this == AIR_TO_FLY_SWITCH
         || this == AIR_TO_WALK_SWITCH
         || this == AIR_TO_SWIM_SWITCH;
   }

   boolean isFlyingRescue() {
      return this == FALL_RESCUE_FLY || this == AIR_TO_FLY_SWITCH;
   }

   boolean isGroundOrWaterRescue() {
      return this == FALL_RESCUE_WALK || this == FALL_RESCUE_SWIM || this == AIR_TO_WALK_SWITCH || this == AIR_TO_SWIM_SWITCH;
   }

   boolean isAirToGroundSwitch() {
      return this == AIR_TO_WALK_SWITCH || this == AIR_TO_SWIM_SWITCH;
   }

   boolean isMountSwitch() {
      return this == MOUNT_SWITCH || this == AIR_TO_FLY_SWITCH || this == AIR_TO_WALK_SWITCH || this == AIR_TO_SWIM_SWITCH;
   }

   boolean isQuickFlyingMount() {
      return this == NORMAL_SUMMON || this == MOUNT_SWITCH;
   }
}
