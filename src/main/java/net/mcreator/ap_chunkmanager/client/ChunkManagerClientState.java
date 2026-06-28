package net.mcreator.ap_chunkmanager.client;

public final class ChunkManagerClientState {
    private static final int[] MINIMAP_SIZES = new int[] {64, 96, 128};

    private static boolean showGrid = true;
    private static int minimapSizeIndex = 1;

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
}
