package net.mcreator.ap_chunkmanager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = APChunkManagerMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChunkManagerRuntimeEvents {
    private static final int ROWS_PER_UPDATE = 8;
    private static final Map<Long, MapScanState> SCAN_STATE_BY_MAP = new HashMap<>();
    private static final Map<Integer, MapItemSavedData> VIRTUAL_MAPS = new HashMap<>();
    private static int NEXT_EXTRACTED_MAP_ID = -100_000;

    private ChunkManagerRuntimeEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        while (ChunkManagerKeyMappings.OPEN_FULLMAP_KEY.consumeClick()) {
            if (mc.screen instanceof ChunkManagerMapScreen) {
                mc.setScreen(null);
            } else {
                mc.setScreen(new ChunkManagerMapScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        if (mc.screen instanceof ChunkManagerMapScreen) {
            return;
        }

        byte schaal = 0;
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        int mapId = calculateVirtualMapId(playerX, playerZ, schaal);
        MapItemSavedData mapData = getVirtualMapData(mc, playerX, playerZ, schaal, mapId);
        if (mapData == null) {
            return;
        }

        updateMapColorsClientSide(mc, mapData);
        mc.gameRenderer.getMapRenderer().update(mapId, mapData);

        int minimapSize = ChunkManagerClientState.getMinimapSize();
        int mapX = 10;
        int mapY = 10;
        int pad = 2;

        event.getGuiGraphics().fill(mapX - pad, mapY - pad, mapX + minimapSize + pad, mapY + minimapSize + pad, 0x90303030);

        PoseStack poseStack = event.getGuiGraphics().pose();
        poseStack.pushPose();
        poseStack.translate(mapX, mapY, 100.0f);
        float hudScale = minimapSize / 128.0f;
        poseStack.scale(hudScale, hudScale, 1.0f);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        mc.gameRenderer.getMapRenderer().render(
                poseStack,
                buffers,
                mapId,
                mapData,
                true,
                15728880
        );
        buffers.endBatch();
        poseStack.popPose();

        renderHudCompass(event.getGuiGraphics(), mc, mapX, mapY);
        renderHudPlayerPointer(event.getGuiGraphics(), mc, mapData, mapX, mapY, minimapSize);
    }

    public static MapItemSavedData getVirtualMapData(Minecraft mc, double playerX, double playerZ, byte schaal, int mapId) {
        if (mc == null || mc.level == null) {
            return null;
        }

        MapItemSavedData cached = VIRTUAL_MAPS.get(mapId);
        if (cached != null) {
            return cached;
        }

        int blockSize = 128 * (1 << schaal);
        int gridX = Math.floorDiv((int) playerX, blockSize);
        int gridZ = Math.floorDiv((int) playerZ, blockSize);

        int centrumX = (gridX * blockSize) + (blockSize / 2);
        int centrumZ = (gridZ * blockSize) + (blockSize / 2);
        MapItemSavedData mapData = MapItemSavedData.createFresh(centrumX, centrumZ, schaal, true, true, mc.level.dimension());
        VIRTUAL_MAPS.put(mapId, mapData);
        return mapData;
    }

    public static void syncVirtualToPhysicalMap(Minecraft mc, int virtualMapId, int physicalMapId, double centerX, double centerZ, byte scale) {
        if (mc == null || mc.level == null) {
            return;
        }

        MapItemSavedData virtualData = getVirtualMapData(mc, centerX, centerZ, scale, virtualMapId);
        if (virtualData == null) {
            return;
        }

        String physicalKey = "map_" + physicalMapId;
        MapItemSavedData physicalData = mc.level.getMapData(physicalKey);
        if (physicalData == null) {
            physicalData = MapItemSavedData.createFresh(virtualData.centerX, virtualData.centerZ, virtualData.scale, true, true, mc.level.dimension());
            mc.level.setMapData(physicalKey, physicalData);
        }

        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                int idx = x + z * 128;
                physicalData.updateColor(x, z, virtualData.colors[idx]);
            }
        }
    }

    public static int createExtractedMapFromVirtual(Minecraft mc, int virtualMapId, double centerX, double centerZ, byte scale) {
        if (mc == null || mc.level == null) {
            return calculatePhysicalMapId(centerX, centerZ, scale);
        }

        MapItemSavedData virtualData = getVirtualMapData(mc, centerX, centerZ, scale, virtualMapId);
        if (virtualData == null) {
            return calculatePhysicalMapId(centerX, centerZ, scale);
        }

        int extractedId = NEXT_EXTRACTED_MAP_ID--;
        String physicalKey = "map_" + extractedId;
        MapItemSavedData extractedData = MapItemSavedData.createFresh(virtualData.centerX, virtualData.centerZ, virtualData.scale, true, true, mc.level.dimension());

        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                int idx = x + z * 128;
                extractedData.updateColor(x, z, virtualData.colors[idx]);
            }
        }

        mc.level.setMapData(physicalKey, extractedData);
        mc.gameRenderer.getMapRenderer().update(extractedId, extractedData);
        return extractedId;
    }

    public static int calculateVirtualMapId(double playerX, double playerZ, byte schaal) {
        int base = calculateBaseMapHash(playerX, playerZ, schaal);
        return -(base + 1);
    }

    public static int calculatePhysicalMapId(double playerX, double playerZ, byte schaal) {
        return calculateBaseMapHash(playerX, playerZ, schaal);
    }

    private static int calculateBaseMapHash(double playerX, double playerZ, byte schaal) {
        int blockSize = 128 * (1 << schaal);
        int gridX = Math.floorDiv((int) playerX, blockSize);
        int gridZ = Math.floorDiv((int) playerZ, blockSize);
        return Math.abs((gridX * 31 + gridZ) ^ schaal);
    }

    public static void updateMapColorsClientSide(Minecraft mc, MapItemSavedData mapData) {
        if (mc == null || mc.level == null || mapData == null) {
            return;
        }

        long mapKey = (((long) mapData.centerX) << 32) ^ (mapData.centerZ & 0xffffffffL) ^ mapData.scale;
        long gameTime = mc.level.getGameTime();
        MapScanState scanState = SCAN_STATE_BY_MAP.computeIfAbsent(mapKey, ignored -> new MapScanState());
        if (scanState.lastProcessedTick == gameTime) {
            return;
        }
        scanState.lastProcessedTick = gameTime;

        int scaleMultiplier = 1 << mapData.scale;
        int startX = mapData.centerX - 64 * scaleMultiplier;
        int startZ = mapData.centerZ - 64 * scaleMultiplier;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        int startRow = scanState.nextRow;
        int rowsToProcess = Math.min(ROWS_PER_UPDATE, 128);

        for (int row = 0; row < rowsToProcess; row++) {
            int z = (startRow + row) & 127;
            for (int x = 0; x < 128; x++) {
                int worldX = startX + (x * scaleMultiplier) + (scaleMultiplier / 2);
                int worldZ = startZ + (z * scaleMultiplier) + (scaleMultiplier / 2);

                if (!mc.level.hasChunk(worldX >> 4, worldZ >> 4)) {
                    continue;
                }

                int y = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                mutablePos.set(worldX, y, worldZ);
                BlockState state = mc.level.getBlockState(mutablePos);

                MapColor mapColor = state.getMapColor(mc.level, mutablePos);
                if (mapColor != null && mapColor != MapColor.NONE) {
                    byte packedColor = (byte) (mapColor.id * 4 + 2);
                    mapData.updateColor(x, z, packedColor);
                }
            }
        }

        scanState.nextRow = (startRow + rowsToProcess) & 127;
    }

    private static final class MapScanState {
        private int nextRow;
        private long lastProcessedTick = Long.MIN_VALUE;
    }

    private static void renderHudCompass(net.minecraft.client.gui.GuiGraphics guiGraphics, Minecraft mc, int mapX, int mapY) {
        int cx = mapX + 16;
        int cy = mapY + 16;
        guiGraphics.fill(cx - 1, cy - 8, cx + 1, cy + 8, 0xC0D7E8FF);
        guiGraphics.fill(cx - 8, cy - 1, cx + 8, cy + 1, 0xC0D7E8FF);

        if (mc.font != null) {
            float yaw = mc.player != null ? mc.player.getYRot() : 0.0f;
            double yawRad = Math.toRadians(yaw);

            drawRotatedCompassLabel(guiGraphics, mc, "N", cx, cy, 14.0, -yawRad, 0xFFEAF3FF);
            drawRotatedCompassLabel(guiGraphics, mc, "E", cx, cy, 14.0, -yawRad + (Math.PI / 2.0), 0xFFEAF3FF);
            drawRotatedCompassLabel(guiGraphics, mc, "S", cx, cy, 14.0, -yawRad + Math.PI, 0xFFEAF3FF);
            drawRotatedCompassLabel(guiGraphics, mc, "W", cx, cy, 14.0, -yawRad + (Math.PI * 1.5), 0xFFEAF3FF);
        }
    }

    private static void renderHudPlayerPointer(net.minecraft.client.gui.GuiGraphics guiGraphics, Minecraft mc, MapItemSavedData mapData, int mapX, int mapY,
            int minimapSize) {
        if (mc.player == null || mapData == null) {
            return;
        }

        int scaleMultiplier = 1 << mapData.scale;
        double mapLeft = mapData.centerX - (64.0 * scaleMultiplier);
        double mapTop = mapData.centerZ - (64.0 * scaleMultiplier);
        double mapSpan = 128.0 * scaleMultiplier;

        double localX = (mc.player.getX() - mapLeft) / mapSpan;
        double localZ = (mc.player.getZ() - mapTop) / mapSpan;

        int rawPx = mapX + (int) Math.round(localX * minimapSize);
        int rawPz = mapY + (int) Math.round(localZ * minimapSize);
        boolean outside = localX < 0.0 || localX > 1.0 || localZ < 0.0 || localZ > 1.0;

        int px = Mth.clamp(rawPx, mapX + 2, mapX + minimapSize - 2);
        int pz = Mth.clamp(rawPz, mapY + 2, mapY + minimapSize - 2);

        float yawRad = (float) Math.toRadians(mc.player.getYRot());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        int tipLen = outside ? 6 : 8;
        int tipX = px + (int) Math.round(dirX * tipLen);
        int tipZ = pz + (int) Math.round(dirZ * tipLen);

        int coreColor = outside ? 0xFFFFE18A : 0xFF8FE8FF;
        int arrowColor = outside ? 0xFFFFB347 : 0xFF42D1FF;
        int coreHalf = outside ? 1 : 2;

        guiGraphics.fill(px - coreHalf, pz - coreHalf, px + coreHalf, pz + coreHalf, coreColor);
        drawPixelLine(guiGraphics, px, pz, tipX, tipZ, arrowColor);
        guiGraphics.fill(tipX - 1, tipZ - 1, tipX + 1, tipZ + 1, arrowColor);

        if (outside) {
            int mapCx = mapX + (minimapSize / 2);
            int mapCy = mapY + (minimapSize / 2);
            drawPixelLine(guiGraphics, mapCx, mapCy, px, pz, 0x80FFE18A);
        }
    }

    private static void drawRotatedCompassLabel(net.minecraft.client.gui.GuiGraphics guiGraphics, Minecraft mc, String text, int cx, int cy, double radius,
            double angleRad, int color) {
        int tx = cx + (int) Math.round(Math.sin(angleRad) * radius) - 3;
        int ty = cy - (int) Math.round(Math.cos(angleRad) * radius) - 4;
        guiGraphics.fill(tx - 2, ty - 1, tx + 8, ty + 9, 0x70000000);
        guiGraphics.drawString(mc.font, text, tx, ty, color, false);
    }

    private static void drawPixelLine(net.minecraft.client.gui.GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;

        int x = x0;
        int y = y0;
        while (true) {
            guiGraphics.fill(x, y, x + 1, y + 1, color);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y += sy;
            }
        }
    }
}
