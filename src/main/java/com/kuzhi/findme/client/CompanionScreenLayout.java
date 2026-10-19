package com.kuzhi.findme.client;

final class CompanionScreenLayout {
   static final int MAX_PANEL_WIDTH = 404;
   static final int MAX_PANEL_HEIGHT = 316;
   static final int MIN_PANEL_WIDTH = 260;
   static final int MIN_PANEL_HEIGHT = 190;
   static final int SIDE_MARGIN = 16;
   static final int TOP_MARGIN = 18;
   static final int BOTTOM_MARGIN = 28;

   private CompanionScreenLayout() {
   }

   static int panelWidth(int screenWidth) {
      int available = Math.max(160, screenWidth - 32);
      return Math.min(404, Math.max(Math.min(260, available), available));
   }

   static int panelHeight(int screenHeight) {
      return Math.min(316, Math.max(190, screenHeight - 36));
   }

   static int panelX(int screenWidth) {
      return screenWidth / 2 - panelWidth(screenWidth) / 2;
   }

   static int panelY(int screenHeight) {
      int panelHeight = panelHeight(screenHeight);
      return Math.max(18, Math.min(screenHeight - panelHeight - 28, screenHeight / 2 - panelHeight / 2));
   }
}
