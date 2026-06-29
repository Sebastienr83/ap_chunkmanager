package net.mcreator.ap_chunkmanager.client;

public final class ChunkManagerClientState {
    private static final byte MINIMAP_SCALE_MIN = 0;
    private static final byte MINIMAP_SCALE_MAX = 4;
    private static final int[] MINIMAP_SIZES = new int[] {64, 96, 128};

    private static boolean showGrid = true;
    private static boolean showDebugLogs;
    private static int minimapSizeIndex = 1;
    private static byte minimapScale = 1;
    private static String chunkCreateFeedbackMessage = "";
    private static int chunkCreateFeedbackColor = 0xFFA7E2A8;
    private static long chunkCreateFeedbackUntilMs;

    private ChunkManagerClientState() {
    }

    public static boolean isGridEnabled() {
        return showGrid;
    }

    public static void toggleGrid() {
        showGrid = !showGrid;
    }

    public static int getMinimapSize() {
        return MINIMAP_SIZES[minimapSizeIndex];
    }

    public static void cycleMinimapSize() {
        minimapSizeIndex = (minimapSizeIndex + 1) % MINIMAP_SIZES.length;
    }

    public static byte getMinimapScale() {
        return minimapScale;
    }

    public static void cycleMinimapScale() {
        minimapScale++;
        if (minimapScale > MINIMAP_SCALE_MAX) {
            minimapScale = MINIMAP_SCALE_MIN;
        }
    }

    public static boolean isDebugLogsEnabled() {
        return showDebugLogs;
    }

    public static boolean toggleDebugLogs() {
        showDebugLogs = !showDebugLogs;
        return showDebugLogs;
    }

    public static void setChunkCreateFeedback(String message, int color, long durationMs) {
        chunkCreateFeedbackMessage = message == null ? "" : message;
        chunkCreateFeedbackColor = color;
        chunkCreateFeedbackUntilMs = System.currentTimeMillis() + Math.max(250L, durationMs);
    }

    public static boolean hasChunkCreateFeedback() {
        return !chunkCreateFeedbackMessage.isBlank() && System.currentTimeMillis() <= chunkCreateFeedbackUntilMs;
    }

    public static String getChunkCreateFeedbackMessage() {
        return chunkCreateFeedbackMessage;
    }

    public static int getChunkCreateFeedbackColor() {
        return chunkCreateFeedbackColor;
    }
}
