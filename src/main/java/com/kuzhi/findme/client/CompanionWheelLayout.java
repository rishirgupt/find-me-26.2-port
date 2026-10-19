package com.kuzhi.findme.client;

final class CompanionWheelLayout {
   static final int BACKDROP_RADIUS = 117;
   static final int CENTER_RADIUS = 12;
   static final int SLOT_RADIUS = 25;
   static final int HOVERED_SLOT_RADIUS = 29;
   static final int ITEM_RADIUS = 82;
   private static final int PAGE_SIZE = 8;
   private static final double HOVER_DISTANCE = 42.0;

   private CompanionWheelLayout() {
   }

   static CompanionWheelLayout.Page page(int entryCount, int currentPage) {
      int count = Math.max(1, (entryCount + 8 - 1) / 8);
      int page = Math.max(0, Math.min(currentPage, count - 1));
      int start = page * 8;
      int end = Math.min(entryCount, start + 8);
      return new CompanionWheelLayout.Page(page, count, start, end);
   }

   static int scrolledPage(int entryCount, int currentPage, double delta) {
      int count = Math.max(1, (entryCount + 8 - 1) / 8);
      return count <= 1 ? currentPage : Math.floorMod(currentPage + (delta < 0.0 ? 1 : -1), count);
   }

   static int hoveredSlot(int centerX, int centerY, int mouseX, int mouseY, int visibleSize) {
      if (visibleSize <= 0) {
         return -1;
      }

      double dx = mouseX - centerX;
      double dy = mouseY - centerY;
      double distance = Math.sqrt(dx * dx + dy * dy);
      if (distance < 12.0) {
         return -1;
      }

      int best = -1;
      double bestDistance = Double.MAX_VALUE;

      for (int i = 0; i < visibleSize; i++) {
         CompanionWheelLayout.Slot slot = slot(centerX, centerY, i, visibleSize);
         double slotDistance = Math.hypot((double)mouseX - slot.x(), (double)mouseY - slot.y());
         if (!(slotDistance >= bestDistance) && !(slotDistance >= 42.0)) {
            best = i;
            bestDistance = slotDistance;
         }
      }

      return best;
   }

   static CompanionWheelLayout.Slot slot(int centerX, int centerY, int index, int visibleSize) {
      double angle = angleFor(index, visibleSize);
      int x = centerX + (int)Math.round(Math.cos(angle) * 82.0);
      int y = centerY + (int)Math.round(Math.sin(angle) * 82.0);
      return new CompanionWheelLayout.Slot(x, y);
   }

   private static double angleFor(int index, int size) {
      return (-Math.PI / 2) + (Math.PI * 2) * index / Math.max(1, size);
   }

   record Page(int index, int count, int start, int end) {
      int visibleSize() {
         return this.end - this.start;
      }
   }

   record Slot(int x, int y) {
   }
}
