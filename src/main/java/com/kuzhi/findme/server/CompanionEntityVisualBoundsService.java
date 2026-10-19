package com.kuzhi.findme.server;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

final class CompanionEntityVisualBoundsService {
   private CompanionEntityVisualBoundsService() {
   }

   static AABB visualBounds(Entity entity) {
      AABB box = CompanionMountContactService.entityContactBox(entity);
      CompanionEntityVisualBoundsService.VisualDimensions minimum = minimumVisualDimensions(entity);
      return minimum.isEmpty() ? box : withMinimumDimensions(box, minimum.width(), minimum.height(), minimum.depth());
   }

   static CompanionEntityVisualBoundsService.VisualDimensions previewDimensions(Entity entity) {
      AABB box = visualBounds(entity);
      double width = Math.max(box.getXsize(), box.getZsize());
      double height = box.getYsize();
      double depth = Math.max(0.0, Math.min(box.getXsize(), box.getZsize()));
      return new CompanionEntityVisualBoundsService.VisualDimensions(width, height, depth);
   }

   static float contractCircleRadius(LivingEntity entity) {
      AABB box = visualBounds(entity);
      double width = Math.max(box.getXsize(), box.getZsize());
      double centerOffset = horizontal(box.getCenter().subtract(entity.position())).length();
      return (float)Mth.clamp(width * 0.56 + centerOffset * 0.35 + 1.2, 2.6, 16.0);
   }

   static float summonCircleRadius(Entity entity) {
      AABB box = visualBounds(entity);
      double width = Math.max(box.getXsize(), box.getZsize());
      double centerOffset = horizontal(box.getCenter().subtract(entity.position())).length();
      return (float)Mth.clamp(width * 0.78 + centerOffset * 0.45 + 1.35, 2.6, 22.0);
   }

   static float portalRadius(LivingEntity entity) {
      AABB box = visualBounds(entity);
      double horizontalSize = Math.max(box.getXsize(), box.getZsize());
      double majorSize = Math.max(horizontalSize, box.getYsize());
      double centerOffset = new Vec3(box.getCenter().x - entity.getX(), 0.0, box.getCenter().z - entity.getZ()).length();
      return (float)Mth.clamp(majorSize * 1.28 + centerOffset * 0.38 + 2.2, 3.4, 28.0);
   }

   static float portalHeight(LivingEntity entity) {
      AABB box = visualBounds(entity);
      return (float)Mth.clamp(Math.max(box.getYsize(), entity.getBbHeight()), 1.2, 32.0);
   }

   private static AABB withMinimumDimensions(AABB box, double width, double height, double depth) {
      double xSize = Math.max(box.getXsize(), width);
      double ySize = Math.max(box.getYsize(), height);
      double zSize = Math.max(box.getZsize(), depth);
      Vec3 center = box.getCenter();
      return new AABB(center.x - xSize * 0.5, box.minY, center.z - zSize * 0.5, center.x + xSize * 0.5, box.minY + ySize, center.z + zSize * 0.5);
   }

   private static CompanionEntityVisualBoundsService.VisualDimensions minimumVisualDimensions(Entity entity) {
      Identifier id = EntityType.getKey(entity.getType());
      return CompanionEntityClassifier.isIceAndFireDragonKey(id.toString())
         ? new CompanionEntityVisualBoundsService.VisualDimensions(7.5, 5.5, 7.5)
         : CompanionEntityVisualBoundsService.VisualDimensions.EMPTY;
   }

   private static Vec3 horizontal(Vec3 vector) {
      return new Vec3(vector.x, 0.0, vector.z);
   }

   record VisualDimensions(double width, double height, double depth) {
      private static final CompanionEntityVisualBoundsService.VisualDimensions EMPTY = new CompanionEntityVisualBoundsService.VisualDimensions(0.0, 0.0, 0.0);

      boolean isEmpty() {
         return this.width <= 0.0 && this.height <= 0.0 && this.depth <= 0.0;
      }
   }
}
