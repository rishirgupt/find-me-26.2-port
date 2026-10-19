package com.kuzhi.findme.server;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;

final class CompanionAnimationHelper {
   private static final Map<Class<?>, List<CompanionAnimationHelper.BooleanMethodCall>> FLYING_ANIMATION_METHODS = new HashMap<>();

   private CompanionAnimationHelper() {
   }

   static void restoreAnimationControl(LivingEntity living) {
      living.noPhysics = false;
      living.setNoGravity(false);
      restoreGenericFlightInput(living);
      if (living instanceof Mob mob) {
         mob.getNavigation().stop();
         mob.setTarget(null);
      }
   }

   static void forceFlyingAnimationPose(LivingEntity living) {
      living.setPose(Pose.FALL_FLYING);
      living.setNoGravity(true);
      living.setOnGround(false);
      living.fallDistance = 0.0F;
      living.hurtMarked = true;

      for (CompanionAnimationHelper.BooleanMethodCall call : FLYING_ANIMATION_METHODS.computeIfAbsent(
         living.getClass(), CompanionAnimationHelper::findFlyingAnimationMethods
      )) {
         try {
            call.method().invoke(living, call.value());
         } catch (ReflectiveOperationException | RuntimeException var4) {
         }
      }

      forceGenericMovingFlight(living);
   }

   static boolean keepsFlyingAnimationWithActiveAi(LivingEntity living) {
      String key = EntityType.getKey(living.getType()).toString().toLowerCase(Locale.ROOT);
      return key.startsWith("iceandfire:");
   }

   static boolean shouldSuppressOwnerFollowDuringCinematic(LivingEntity living) {
      String key = EntityType.getKey(living.getType()).toString().toLowerCase(Locale.ROOT);
      return CompanionEntityClassifier.isDragonMountsLegacyKey(key);
   }

   static void forceStandingPose(LivingEntity living) {
      callBooleanMethod(living, "setOrderedToSit", false);
      callBooleanMethod(living, "setInSittingPose", false);
      callBooleanMethod(living, "setSitting", false);
      callBooleanMethod(living, "setSittingPose", false);
      callBooleanMethod(living, "setIsSitting", false);
   }

   private static void restoreGenericFlightInput(LivingEntity living) {
      callBooleanMethod(living, "setGoingUp", false);
      callBooleanMethod(living, "setGoingDown", false);
      callBooleanMethod(living, "setAccelerating", false);
      callBooleanMethod(living, "setSprinting", false);
   }

   private static List<CompanionAnimationHelper.BooleanMethodCall> findFlyingAnimationMethods(Class<?> type) {
      ArrayList<CompanionAnimationHelper.BooleanMethodCall> methods = new ArrayList<>();

      for (Method method : type.getMethods()) {
         Class<?> parameter;
         if (method.getParameterCount() == 1 && ((parameter = method.getParameterTypes()[0]) == boolean.class || parameter == Boolean.class)) {
            String name = method.getName();
            if (name.equals("setFlying")
               || name.equals("setInAir")
               || name.equals("setAirborne")
               || name.equals("setAccelerating")
               || name.equals("setSprinting")) {
               methods.add(new CompanionAnimationHelper.BooleanMethodCall(method, true));
            } else if (name.equals("setHovering")
               || name.equals("setLanding")
               || name.equals("setTakeoff")
               || name.equals("setGoingUp")
               || name.equals("setGoingDown")) {
               methods.add(new CompanionAnimationHelper.BooleanMethodCall(method, false));
            } else if (name.equals("setOrderedToSit") || name.equals("setInSittingPose") || name.equals("setSitting") || name.equals("setSittingPose")) {
               methods.add(new CompanionAnimationHelper.BooleanMethodCall(method, false));
            }
         }
      }

      return List.copyOf(methods);
   }

   private static void forceGenericMovingFlight(LivingEntity living) {
      Vec3 motion = living.getDeltaMovement();
      double horizontalSpeedSqr = motion.horizontalDistanceSqr();
      if (horizontalSpeedSqr < 0.0016 && Math.abs(motion.y) < 0.03) {
         Vec3 look = living.getLookAngle();
         motion = new Vec3(look.x, 0.0, look.z);
         motion = motion.horizontalDistanceSqr() < 0.0016 ? new Vec3(0.0, 0.0, 0.28) : motion.normalize().scale(0.28);
         living.setDeltaMovement(motion);
      }

      callBooleanMethod(living, "setFlying", true);
      callBooleanMethod(living, "setInAir", true);
      callBooleanMethod(living, "setAirborne", true);
      callBooleanMethod(living, "setHovering", false);
      callBooleanMethod(living, "setLanding", false);
      callBooleanMethod(living, "setTakeoff", false);
      callBooleanMethod(living, "setGoingUp", false);
      callBooleanMethod(living, "setGoingDown", false);
      callBooleanMethod(living, "setAccelerating", true);
      callBooleanMethod(living, "setSprinting", true);
      int flightMode = 4;
      callIntMethod(living, "setFlightMode", flightMode);
      callIntMethod(living, "setSyncedFlightMode", flightMode);
      callIntMethod(living, "setMoveState", 2);
      callIntMethod(living, "setMovementState", 2);
      setSyncedIntAccessor(living, "getFlightModeAccessor", flightMode);
      setSyncedIntAccessor(living, "getMoveStateAccessor", 2);
      setSyncedIntAccessor(living, "getMovementStateAccessor", 2);
      callIntIntMethod(living, "syncAnimState", 0, flightMode);
   }

   private static void setSyncedIntAccessor(LivingEntity living, String accessorMethod, int value) {
      try {
         Method method = findNoArgMethod(living.getClass(), accessorMethod);
         if (method == null) {
            return;
         }

         method.setAccessible(true);
         if (method.invoke(living) instanceof EntityDataAccessor<?> dataAccessor) {
            living.getEntityData().set((EntityDataAccessor<Object>)dataAccessor, (Object)value);
         }
      } catch (ReflectiveOperationException | RuntimeException var6) {
      }
   }

   private static Method findNoArgMethod(Class<?> type, String name) {
      for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
         try {
            Method method = current.getDeclaredMethod(name);
            if (method.getParameterCount() == 0) {
               return method;
            }
         } catch (NoSuchMethodException var4) {
         }
      }

      return null;
   }

   private static void callBooleanMethod(Object target, String name, boolean value) {
      try {
         Method method = target.getClass().getMethod(name, boolean.class);
         method.invoke(target, value);
      } catch (ReflectiveOperationException | RuntimeException var4) {
      }
   }

   private static void callIntMethod(Object target, String name, int value) {
      try {
         Method method = target.getClass().getMethod(name, int.class);
         method.invoke(target, value);
      } catch (ReflectiveOperationException | RuntimeException var4) {
      }
   }

   private static void callIntIntMethod(Object target, String name, int first, int second) {
      try {
         Method method = target.getClass().getMethod(name, int.class, int.class);
         method.invoke(target, first, second);
      } catch (ReflectiveOperationException | RuntimeException var5) {
      }
   }

   private record BooleanMethodCall(Method method, boolean value) {
   }
}
