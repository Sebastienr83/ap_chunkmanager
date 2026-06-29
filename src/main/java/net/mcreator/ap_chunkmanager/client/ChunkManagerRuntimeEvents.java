package net.mcreator.ap_chunkmanager.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.mcreator.ap_chunkmanager.network.RequestChunkClaimInfoPacket;
import net.mcreator.ap_chunkmanager.network.RequestMapTileDataPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ItemStack;
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
    private static final long TILE_REQUEST_COOLDOWN_MS = 700L;
    private static final byte HUD_MAP_SCALE = 0;
    private static final int HUD_MAX_TILES_PER_AXIS = 5;
    private static final int HUD_MAX_TOTAL_TILES = 25;
    private static final double HUD_TILE_PADDING_PIXELS = 2.0;
    private static final double HUD_TILE_OVERLAP_PIXELS = 1.0;
    private static final int HUD_FALLBACK_TEXTURE_SIZE = 256;
    private static final int HUD_FALLBACK_WRAP_BLOCKS = 8192;
    private static final ResourceLocation COMPASS_TEXTURE = new ResourceLocation("minecraft", "textures/item/compass_00.png");
    private static final ResourceLocation FULLMAP_FALLBACK_TEXTURE = new ResourceLocation("minecraft", "textures/map/map_background_checkerboard.png");
    private static final Map<Long, MapScanState> SCAN_STATE_BY_MAP = new HashMap<>();
    private static final Map<Integer, MapItemSavedData> VIRTUAL_MAPS = new HashMap<>();
    private static final Map<Integer, Long> LAST_TILE_REQUEST_MS = new HashMap<>();
    private static final long CLAIM_INFO_REQUEST_COOLDOWN_MS = 250L;
    private static final Map<Long, Long> LAST_CLAIM_REQUEST_MS = new HashMap<>();
    private static final Map<Long, ChunkClaimInfo> CLAIM_INFO_CACHE = new HashMap<>();

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
            if (mc.screen instanceof ChunkManagerAdminScreen) {
                mc.setScreen(null);
            } else {
                mc.setScreen(new ChunkManagerAdminScreen());
            }
        }

        while (ChunkManagerKeyMappings.TOGGLE_DEBUG_LOGS_KEY.consumeClick()) {
            boolean enabled = ChunkManagerClientState.toggleDebugLogs();
            mc.player.displayClientMessage(Component.literal("ChunkManager debug logs: " + (enabled ? "ON" : "OFF")), true);
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

        int minimapSize = ChunkManagerClientState.getMinimapSize();
        int mapX = 10;
        int mapY = 10;
        int pad = 2;

        event.getGuiGraphics().fill(mapX - pad, mapY - pad, mapX + minimapSize + pad, mapY + minimapSize + pad, 0x90303030);

        int scaleMultiplier = 1 << HUD_MAP_SCALE;
        double worldSpan = 128.0 * scaleMultiplier;
        double playerX = mc.player.getX();
        double playerZ = mc.player.getZ();
        double worldLeft = playerX - (worldSpan / 2.0);
        double worldTop = playerZ - (worldSpan / 2.0);
        double pixelsPerBlock = minimapSize / Math.max(1.0, worldSpan);

        renderHudMapPass(event.getGuiGraphics(), mc, mapX, mapY, minimapSize, worldLeft, worldTop, worldSpan, pixelsPerBlock, HUD_MAP_SCALE);
        renderHudOverlayPass(event.getGuiGraphics(), mc, mapX, mapY, minimapSize, worldLeft, worldTop, pixelsPerBlock);
    }

    private static void renderHudMapPass(net.minecraft.client.gui.GuiGraphics guiGraphics, Minecraft mc, int mapX, int mapY, int minimapSize,
            double worldLeft, double worldTop, double worldSpan, double pixelsPerBlock, byte scale) {
        guiGraphics.flush();
        beginHudScissor(mc, mapX, mapY, minimapSize);
        try {
            renderHudStitchedTiles(guiGraphics, mc, mapX, mapY, minimapSize, worldLeft, worldTop, worldSpan, pixelsPerBlock, scale);
        } finally {
            RenderSystem.disableScissor();
        }
        guiGraphics.flush();
    }

    private static void renderHudStitchedTiles(net.minecraft.client.gui.GuiGraphics guiGraphics, Minecraft mc, int mapX, int mapY, int minimapSize,
            double worldLeft, double worldTop, double worldSpan, double pixelsPerBlock, byte scale) {
        int blockSize = 128 * (1 << scale);
        HudTileBounds bounds = calculateHudTileBounds(worldLeft, worldTop, worldSpan, pixelsPerBlock, blockSize);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int renderedTiles = 0;

        for (int gridZ = bounds.minGridZ; gridZ <= bounds.maxGridZ; gridZ++) {
            for (int gridX = bounds.minGridX; gridX <= bounds.maxGridX; gridX++) {
                if (renderedTiles >= HUD_MAX_TOTAL_TILES) {
                    break;
                }

                int tileMinX = gridX * blockSize;
                int tileMinZ = gridZ * blockSize;
                double tileCenterX = tileMinX + (blockSize / 2.0);
                double tileCenterZ = tileMinZ + (blockSize / 2.0);

                int mapId = calculateVirtualMapId(tileCenterX, tileCenterZ, scale);
                MapItemSavedData mapData = getVirtualMapData(mc, tileCenterX, tileCenterZ, scale, mapId);

                double drawLeft = mapX + ((tileMinX - worldLeft) * pixelsPerBlock) - bounds.tileOverlapPixels;
                double drawTop = mapY + ((tileMinZ - worldTop) * pixelsPerBlock) - bounds.tileOverlapPixels;
                double drawRight = mapX + (((tileMinX + blockSize) - worldLeft) * pixelsPerBlock) + bounds.tileOverlapPixels;
                double drawBottom = mapY + (((tileMinZ + blockSize) - worldTop) * pixelsPerBlock) + bounds.tileOverlapPixels;

                int drawX = Mth.floor(drawLeft - 0.5);
                int drawY = Mth.floor(drawTop - 0.5);
                int drawW = Mth.ceil(drawRight + 0.5) - drawX;
                int drawH = Mth.ceil(drawBottom + 0.5) - drawY;
                if (drawW <= 0 || drawH <= 0) {
                    continue;
                }

                boolean missingData = mapData == null || !hasRenderableData(mapData) || mapId == -1;
                if (missingData) {
                    requestVirtualTileData(mc, tileCenterX, tileCenterZ, scale, mapId);
                    renderHudFallbackTile(guiGraphics, drawX, drawY, drawW, drawH, tileMinX, tileMinZ, blockSize);
                    renderedTiles++;
                    continue;
                }

                updateMapColorsClientSide(mc, mapData);
                mc.gameRenderer.getMapRenderer().update(mapId, mapData);

                PoseStack poseStack = guiGraphics.pose();
                poseStack.pushPose();
                poseStack.translate(drawX, drawY, 100.0f);
                poseStack.scale(drawW / 128.0f, drawH / 128.0f, 1.0f);
                mc.gameRenderer.getMapRenderer().render(poseStack, buffers, mapId, mapData, true, 15728880);
                poseStack.popPose();

                renderedTiles++;
            }

            if (renderedTiles >= HUD_MAX_TOTAL_TILES) {
                break;
            }
        }

        buffers.endBatch();
    }

    private static void renderHudFallbackTile(net.minecraft.client.gui.GuiGraphics guiGraphics, int drawX, int drawY, int drawW, int drawH, int tileMinX,
            int tileMinZ, int blockSize) {
        float u = (float) (Math.floorMod(tileMinX, HUD_FALLBACK_WRAP_BLOCKS) / (double) HUD_FALLBACK_WRAP_BLOCKS);
        float v = (float) (Math.floorMod(tileMinZ, HUD_FALLBACK_WRAP_BLOCKS) / (double) HUD_FALLBACK_WRAP_BLOCKS);
        float uSize = (float) (blockSize / (double) HUD_FALLBACK_WRAP_BLOCKS);
        float vSize = (float) (blockSize / (double) HUD_FALLBACK_WRAP_BLOCKS);
        int uWidthPx = Math.max(1, Mth.ceil(uSize * HUD_FALLBACK_TEXTURE_SIZE));
        int vHeightPx = Math.max(1, Mth.ceil(vSize * HUD_FALLBACK_TEXTURE_SIZE));

        guiGraphics.blit(
                FULLMAP_FALLBACK_TEXTURE,
                drawX,
                drawY,
                drawW,
                drawH,
                u * HUD_FALLBACK_TEXTURE_SIZE,
                v * HUD_FALLBACK_TEXTURE_SIZE,
                uWidthPx,
                vHeightPx,
                HUD_FALLBACK_TEXTURE_SIZE,
                HUD_FALLBACK_TEXTURE_SIZE
        );
    }

    private static void renderHudOverlayPass(net.minecraft.client.gui.GuiGraphics guiGraphics, Minecraft mc, int mapX, int mapY, int minimapSize,
            double worldLeft, double worldTop, double pixelsPerBlock) {
        guiGraphics.flush();
        RenderSystem.disableDepthTest();
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0.0f, 0.0f, 250.0f);

        try {
            beginHudScissor(mc, mapX, mapY, minimapSize);
            try {
                int centerMapId = calculateVirtualMapId(mc.player.getX(), mc.player.getZ(), HUD_MAP_SCALE);
                MapItemSavedData centerMapData = getVirtualMapData(mc, mc.player.getX(), mc.player.getZ(), HUD_MAP_SCALE, centerMapId);
                renderHudPlayerPointer(guiGraphics, mc, centerMapData, mapX, mapY, minimapSize);
            } finally {
                RenderSystem.disableScissor();
            }

            renderHudCompass(guiGraphics, mc, mapX, mapY);
        } finally {
            pose.popPose();
            RenderSystem.enableDepthTest();
        }

        guiGraphics.flush();
    }

    private static HudTileBounds calculateHudTileBounds(double worldLeft, double worldTop, double worldSpan, double pixelsPerBlock, int blockSize) {
        double safePixelsPerBlock = Math.max(0.0001, pixelsPerBlock);
        double paddingBlocks = Math.max(1.0, HUD_TILE_PADDING_PIXELS / safePixelsPerBlock);
        double tileOverlapPixels = Mth.clamp(HUD_TILE_OVERLAP_PIXELS, 0.0, Math.max(0.0, blockSize * safePixelsPerBlock * 0.25));

        double paddedWorldLeft = worldLeft - paddingBlocks;
        double paddedWorldTop = worldTop - paddingBlocks;
        double paddedWorldRight = worldLeft + worldSpan + paddingBlocks;
        double paddedWorldBottom = worldTop + worldSpan + paddingBlocks;

        int minGridX = Math.floorDiv(Mth.floor(paddedWorldLeft), blockSize);
        int minGridZ = Math.floorDiv(Mth.floor(paddedWorldTop), blockSize);
        int maxGridX = Math.floorDiv(Mth.ceil(paddedWorldRight) - 1, blockSize);
        int maxGridZ = Math.floorDiv(Mth.ceil(paddedWorldBottom) - 1, blockSize);

        int centerGridX = Math.floorDiv(Mth.floor(worldLeft + (worldSpan / 2.0)), blockSize);
        int centerGridZ = Math.floorDiv(Mth.floor(worldTop + (worldSpan / 2.0)), blockSize);
        minGridX = clampGridAxis(minGridX, maxGridX, centerGridX, HUD_MAX_TILES_PER_AXIS, true);
        maxGridX = clampGridAxis(minGridX, maxGridX, centerGridX, HUD_MAX_TILES_PER_AXIS, false);
        minGridZ = clampGridAxis(minGridZ, maxGridZ, centerGridZ, HUD_MAX_TILES_PER_AXIS, true);
        maxGridZ = clampGridAxis(minGridZ, maxGridZ, centerGridZ, HUD_MAX_TILES_PER_AXIS, false);

        return new HudTileBounds(minGridX, minGridZ, maxGridX, maxGridZ, tileOverlapPixels);
    }

    private static int clampGridAxis(int minGrid, int maxGrid, int centerGrid, int maxTilesAxis, boolean returnMin) {
        int currentCount = maxGrid - minGrid + 1;
        if (currentCount <= maxTilesAxis) {
            return returnMin ? minGrid : maxGrid;
        }

        int half = maxTilesAxis / 2;
        int newMin = centerGrid - half;
        int newMax = newMin + maxTilesAxis - 1;
        return returnMin ? newMin : newMax;
    }

    private static void beginHudScissor(Minecraft mc, int mapX, int mapY, int minimapSize) {
        Window window = mc.getWindow();
        double guiScale = window.getGuiScale();

        int scissorX = (int) Math.floor(mapX * guiScale);
        int scissorY = (int) Math.floor(window.getHeight() - ((mapY + minimapSize) * guiScale));
        int scissorW = Math.max(1, (int) Math.ceil(minimapSize * guiScale));
        int scissorH = Math.max(1, (int) Math.ceil(minimapSize * guiScale));

        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
    }

    private record HudTileBounds(int minGridX, int minGridZ, int maxGridX, int maxGridZ, double tileOverlapPixels) {
    }

    public static MapItemSavedData getVirtualMapData(Minecraft mc, double playerX, double playerZ, byte scale, int mapId) {
        if (mc == null || mc.level == null) {
            return null;
        }

        MapItemSavedData cached = VIRTUAL_MAPS.get(mapId);
        if (cached != null) {
            return cached;
        }

        int blockSize = 128 * (1 << scale);
        int gridX = Math.floorDiv((int) playerX, blockSize);
        int gridZ = Math.floorDiv((int) playerZ, blockSize);

        int centerX = (gridX * blockSize) + (blockSize / 2);
        int centerZ = (gridZ * blockSize) + (blockSize / 2);
        MapItemSavedData mapData = MapItemSavedData.createFresh(centerX, centerZ, scale, true, true, mc.level.dimension());
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

        refreshVirtualMapData(mc, virtualData, 128);

        String physicalKey = "map_" + physicalMapId;
        MapItemSavedData physicalData = mc.level.getMapData(physicalKey);
        if (physicalData == null) {
            physicalData = MapItemSavedData.createFresh(virtualData.centerX, virtualData.centerZ, virtualData.scale, true, true, mc.level.dimension());
            mc.level.setMapData(physicalKey, physicalData);
        }

        copyMapColors(virtualData, physicalData);
        physicalData.setDirty();
    }

    public static ItemStack createExtractedMapItem(Minecraft mc, int virtualMapId, double centerX, double centerZ, byte scale) {
        if (mc == null || mc.level == null) {
            return ItemStack.EMPTY;
        }

        MapItemSavedData virtualData = getVirtualMapData(mc, centerX, centerZ, scale, virtualMapId);
        if (virtualData == null) {
            return ItemStack.EMPTY;
        }

        refreshVirtualMapData(mc, virtualData, 128);

        ItemStack mapItem = MapItem.create(mc.level, virtualData.centerX, virtualData.centerZ, virtualData.scale, true, true);
        Integer physicalMapId = MapItem.getMapId(mapItem);
        if (physicalMapId == null) {
            return ItemStack.EMPTY;
        }

        MapItemSavedData extractedData = MapItem.getSavedData(mapItem, mc.level);
        if (extractedData == null) {
            extractedData = MapItemSavedData.createFresh(virtualData.centerX, virtualData.centerZ, virtualData.scale, true, true, mc.level.dimension());
            mc.level.setMapData(MapItem.makeKey(physicalMapId), extractedData);
        }

        copyMapColors(virtualData, extractedData);
        extractedData.setDirty();
        mc.gameRenderer.getMapRenderer().update(physicalMapId, extractedData);
        return mapItem;
    }

    public static int calculateVirtualMapId(double playerX, double playerZ, byte scale) {
        int base = calculateBaseMapHash(playerX, playerZ, scale);
        return -(base + 1);
    }

    public static int calculatePhysicalMapId(double playerX, double playerZ, byte scale) {
        return calculateBaseMapHash(playerX, playerZ, scale);
    }

    public static boolean hasRenderableData(MapItemSavedData mapData) {
        if (mapData == null || mapData.colors == null || mapData.colors.length < (128 * 128)) {
            return false;
        }

        int nonZero = 0;
        for (int z = 0; z < 128; z += 8) {
            for (int x = 0; x < 128; x += 8) {
                int idx = x + (z * 128);
                if (idx < mapData.colors.length && mapData.colors[idx] != 0) {
                    nonZero++;
                    if (nonZero >= 3) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static void requestVirtualTileData(Minecraft mc, double worldX, double worldZ, byte scale, int mapId) {
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastRequest = LAST_TILE_REQUEST_MS.getOrDefault(mapId, 0L);
        if ((now - lastRequest) < TILE_REQUEST_COOLDOWN_MS) {
            return;
        }

        LAST_TILE_REQUEST_MS.put(mapId, now);
        APChunkManagerMod.NETWORK.sendToServer(new RequestMapTileDataPacket(worldX, worldZ, scale, mapId));
    }

    public static void applyServerTileData(Minecraft mc, int mapId, int centerX, int centerZ, byte scale, byte[] colors) {
        if (mc == null || mc.level == null || colors == null || colors.length == 0) {
            return;
        }

        MapItemSavedData target = VIRTUAL_MAPS.get(mapId);
        if (target == null) {
            target = MapItemSavedData.createFresh(centerX, centerZ, scale, true, true, mc.level.dimension());
            VIRTUAL_MAPS.put(mapId, target);
        }

        int copyLength = Math.min(target.colors.length, colors.length);
        System.arraycopy(colors, 0, target.colors, 0, copyLength);
        target.setDirty();

        mc.gameRenderer.getMapRenderer().update(mapId, target);
        LAST_TILE_REQUEST_MS.remove(mapId);
    }

    public static void requestChunkClaimInfo(Minecraft mc, int chunkX, int chunkZ) {
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }

        long key = packChunk(chunkX, chunkZ);
        long now = System.currentTimeMillis();
        long lastRequest = LAST_CLAIM_REQUEST_MS.getOrDefault(key, 0L);
        if ((now - lastRequest) < CLAIM_INFO_REQUEST_COOLDOWN_MS) {
            return;
        }

        LAST_CLAIM_REQUEST_MS.put(key, now);
        APChunkManagerMod.NETWORK.sendToServer(new RequestChunkClaimInfoPacket(chunkX, chunkZ));
    }

    public static ChunkClaimInfo getCachedChunkClaimInfo(int chunkX, int chunkZ) {
        return CLAIM_INFO_CACHE.get(packChunk(chunkX, chunkZ));
    }

    public static void applyServerChunkClaimInfo(int chunkX, int chunkZ, boolean claimed, String chunkName, String teamName, String roleName,
            String ownerName) {
        long key = packChunk(chunkX, chunkZ);
        LAST_CLAIM_REQUEST_MS.remove(key);

        if (!claimed) {
            CLAIM_INFO_CACHE.remove(key);
            return;
        }

        CLAIM_INFO_CACHE.put(key, new ChunkClaimInfo(chunkX, chunkZ, safe(chunkName), safe(teamName), safe(roleName), safe(ownerName)));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static long packChunk(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    public record ChunkClaimInfo(int chunkX, int chunkZ, String chunkName, String teamName, String roleName, String ownerName) {
    }

    private static int calculateBaseMapHash(double playerX, double playerZ, byte scale) {
        int blockSize = 128 * (1 << scale);
        int gridX = Math.floorDiv((int) playerX, blockSize);
        int gridZ = Math.floorDiv((int) playerZ, blockSize);

        long packed = ((((long) gridX) & 0xffffffffL) << 32)
                ^ (((long) gridZ) & 0xffffffffL)
                ^ ((((long) scale) & 0xffL) << 56);

        // Strong 64-bit mix avoids frequent collisions across scales and far-away grid coordinates.
        long mixed = packed;
        mixed ^= (mixed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= (mixed >>> 33);
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= (mixed >>> 33);

        return (int) (mixed & 0x7fffffff);
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

        refreshVirtualMapData(mc, mapData, ROWS_PER_UPDATE);
    }

    private static void refreshVirtualMapData(Minecraft mc, MapItemSavedData mapData, int rowsToProcess) {
        if (mc == null || mc.level == null || mapData == null || rowsToProcess <= 0) {
            return;
        }

        int scaleMultiplier = 1 << mapData.scale;
        int startX = mapData.centerX - 64 * scaleMultiplier;
        int startZ = mapData.centerZ - 64 * scaleMultiplier;

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        long mapKey = (((long) mapData.centerX) << 32) ^ (mapData.centerZ & 0xffffffffL) ^ mapData.scale;
        MapScanState scanState = SCAN_STATE_BY_MAP.computeIfAbsent(mapKey, ignored -> new MapScanState());
        int startRow = scanState.nextRow;
        int limitedRows = Math.min(rowsToProcess, 128);

        for (int row = 0; row < limitedRows; row++) {
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

        scanState.nextRow = (startRow + limitedRows) & 127;
    }

    private static void copyMapColors(MapItemSavedData source, MapItemSavedData target) {
        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                int idx = x + z * 128;
                target.updateColor(x, z, source.colors[idx]);
            }
        }
    }

    private static final class MapScanState {
        private int nextRow;
        private long lastProcessedTick = Long.MIN_VALUE;
    }

    private static void renderHudCompass(net.minecraft.client.gui.GuiGraphics guiGraphics, Minecraft mc, int mapX, int mapY) {
        int cx = mapX + 16;
        int cy = mapY + 16;
        guiGraphics.fill(cx - 12, cy - 12, cx + 12, cy + 12, 0x80202C3E);
        drawRotatedTexture(guiGraphics, COMPASS_TEXTURE, cx, cy, 18, mc.player != null ? -mc.player.getYRot() : 0.0f);

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

    private static void drawRotatedTexture(net.minecraft.client.gui.GuiGraphics guiGraphics, ResourceLocation texture, int centerX, int centerY, int size,
            float rotationDegrees) {
        PoseStack pose = guiGraphics.pose();
        int halfSize = size / 2;
        pose.pushPose();
        pose.translate(centerX, centerY, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(rotationDegrees));
        guiGraphics.blit(texture, -halfSize, -halfSize, 0.0f, 0.0f, size, size, size, size);
        pose.popPose();
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
