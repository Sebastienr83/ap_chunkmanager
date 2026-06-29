package net.mcreator.ap_chunkmanager.client;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.Window;
import com.mojang.math.Axis;
import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.slf4j.Logger;

public class ChunkManagerMapScreen extends Screen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final byte MIN_SCALE = 0;
    private static final byte MAX_SCALE = 4;
    private static final double MIN_ZOOM_RADIUS_BLOCKS = 48.0;
    private static final double MAX_ZOOM_RADIUS_BLOCKS = 2048.0;
    private static final double TILE_SELECTION_PADDING_PIXELS = 3.0;
    private static final double TILE_OVERLAP_PIXELS = 1.25;
    private static final double MAX_DYNAMIC_PADDING_PIXELS = 7.5;
    private static final double MAX_DYNAMIC_OVERLAP_PIXELS = 4.5;
    private static final double OVERLAP_QUANTIZATION_PIXELS = 0.25;
    private static final int MAX_TILES_PER_AXIS = 9;
    private static final int MAX_TOTAL_TILES = 81;
    private static final long DEBUG_LOG_INTERVAL_MS = 450L;
    private static final int FALLBACK_TEXTURE_SIZE = 256;
    private static final int FALLBACK_WORLD_WRAP_BLOCKS = 8192;
    private static final ResourceLocation COMPASS_TEXTURE = new ResourceLocation("minecraft", "textures/item/compass_00.png");
    private static final ResourceLocation FULLMAP_FALLBACK_TEXTURE = new ResourceLocation("minecraft", "textures/map/map_background_checkerboard.png");

    private byte currentMapScale = 0;
    private int mapX;
    private int mapY;
    private int mapSize;
    private double zoomRadiusBlocks = 256.0;
    private double centerBlockX;
    private double centerBlockZ;
    private boolean centerInitialized;
    private boolean dragging;
    private double dragStartMouseX;
    private double dragStartMouseY;
    private double dragStartCenterX;
    private double dragStartCenterZ;
    private double lastWorldLeft;
    private double lastWorldTop;
    private double lastWorldSpan;
    private double lastBlocksPerPixel = 1.0;
    private long lastDebugLogMs;
    private Button gridButton;
    private Button minimapSizeButton;

    public ChunkManagerMapScreen() {
        super(Component.literal("AP Chunk Manager Map"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();

        int sidePanelWidth = 220;
        int localMapSize = Math.max(128, Math.min(this.width - sidePanelWidth - 48, this.height - 56));
        int panelX = 20 + localMapSize + 16;
        int panelY = (this.height - localMapSize) / 2;

        this.gridButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
            ChunkManagerClientState.toggleGrid();
            updateButtonLabels();
        }).bounds(panelX + 12, panelY + 170, sidePanelWidth - 24, 20).build());

        this.minimapSizeButton = this.addRenderableWidget(Button.builder(Component.empty(), b -> {
            ChunkManagerClientState.cycleMinimapSize();
            updateButtonLabels();
        }).bounds(panelX + 12, panelY + 196, sidePanelWidth - 24, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Center on player"), b -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                centerBlockX = mc.player.getX();
                centerBlockZ = mc.player.getZ();
                centerInitialized = true;
            }
        }).bounds(panelX + 12, panelY + 222, sidePanelWidth - 24, 20).build());

        updateButtonLabels();
    }

    private void updateButtonLabels() {
        if (gridButton != null) {
            gridButton.setMessage(Component.literal("Grid: " + (ChunkManagerClientState.isGridEnabled() ? "ON" : "OFF")));
        }
        if (minimapSizeButton != null) {
            minimapSizeButton.setMessage(Component.literal("Minimap size: " + ChunkManagerClientState.getMinimapSize()));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!isInsideMap(mouseX, mouseY) || delta == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        double localX = Mth.clamp((mouseX - mapX) / Math.max(1.0, mapSize), 0.0, 1.0);
        double localZ = Mth.clamp((mouseY - mapY) / Math.max(1.0, mapSize), 0.0, 1.0);
        double anchorWorldX = lastWorldLeft + (localX * lastWorldSpan);
        double anchorWorldZ = lastWorldTop + (localZ * lastWorldSpan);

        double factor = delta > 0.0 ? 0.8 : 1.25;
        double newRadius = Mth.clamp(zoomRadiusBlocks * factor, MIN_ZOOM_RADIUS_BLOCKS, MAX_ZOOM_RADIUS_BLOCKS);
        double newSpan = newRadius * 2.0;

        centerBlockX = anchorWorldX - (localX * newSpan) + newRadius;
        centerBlockZ = anchorWorldZ - (localZ * newSpan) + newRadius;
        zoomRadiusBlocks = newRadius;
        currentMapScale = scaleForZoom(zoomRadiusBlocks);

        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInsideMap(mouseX, mouseY)) {
            dragging = true;
            dragStartMouseX = mouseX;
            dragStartMouseY = mouseY;
            dragStartCenterX = centerBlockX;
            dragStartCenterZ = centerBlockZ;
            return true;
        }

        if (button == 2 && isInsideMap(mouseX, mouseY)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                double localX = Mth.clamp((mouseX - mapX) / Math.max(1.0, mapSize), 0.0, 1.0);
                double localZ = Mth.clamp((mouseY - mapY) / Math.max(1.0, mapSize), 0.0, 1.0);
                double targetWorldX = lastWorldLeft + (localX * lastWorldSpan);
                double targetWorldZ = lastWorldTop + (localZ * lastWorldSpan);

                APChunkManagerMod.NETWORK.sendToServer(new net.mcreator.ap_chunkmanager.network.CopyChunkMapSectionPacket(targetWorldX, targetWorldZ, currentMapScale));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            double blocksPerPixel = lastBlocksPerPixel;
            centerBlockX = dragStartCenterX - ((mouseX - dragStartMouseX) * blocksPerPixel);
            centerBlockZ = dragStartCenterZ - ((mouseY - dragStartMouseY) * blocksPerPixel);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        renderBackground(guiGraphics);

        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xE6101623, 0xE61A2233);

        int sidePanelWidth = 220;
        mapSize = Math.max(128, Math.min(this.width - sidePanelWidth - 48, this.height - 56));
        mapX = 20;
        mapY = (this.height - mapSize) / 2;

        int panelX = mapX + mapSize + 16;
        int panelY = mapY;
        int panelH = mapSize;

        guiGraphics.fill(mapX - 6, mapY - 6, mapX + mapSize + 6, mapY + mapSize + 6, 0x661A2536);
        guiGraphics.fill(mapX - 4, mapY - 4, mapX + mapSize + 4, mapY + mapSize + 4, 0xFF2D3A52);
        guiGraphics.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFF0D1118);
        guiGraphics.fill(panelX, panelY, panelX + sidePanelWidth, panelY + panelH, 0xCC111827);

        guiGraphics.drawString(this.font, "AP Chunk Manager", panelX + 12, panelY + 12, 0xFF9BD7FF, false);
        guiGraphics.drawString(this.font, "Vanilla Map Screen", panelX + 12, panelY + 28, 0xFFBFC9D8, false);
        guiGraphics.drawString(this.font, "Scale: " + currentMapScale + " (wheel zoom)", panelX + 12, panelY + 54, 0xFFE7EEF7, false);
        guiGraphics.drawString(this.font, "MMB op map = fysieke map", panelX + 12, panelY + 70, 0xFF9FB0C6, false);
        guiGraphics.drawString(this.font, "C of ESC = sluiten", panelX + 12, panelY + 86, 0xFF9FB0C6, false);

        if (mc.player == null || mc.level == null) {
            guiGraphics.drawString(this.font, "Wacht op wereld...", mapX + 8, mapY + 8, 0xFFFF9090, false);
            super.render(guiGraphics, mouseX, mouseY, partialTick);
            return;
        }

        if (!centerInitialized) {
            centerBlockX = mc.player.getX();
            centerBlockZ = mc.player.getZ();
            centerInitialized = true;
        }

        currentMapScale = scaleForZoom(zoomRadiusBlocks);
        double worldSpan = zoomRadiusBlocks * 2.0;
        double worldLeft = centerBlockX - zoomRadiusBlocks;
        double worldTop = centerBlockZ - zoomRadiusBlocks;
        double blocksPerPixel = worldSpan / Math.max(1.0, mapSize);
        double pixelsPerBlock = mapSize / Math.max(1.0, worldSpan);

        lastWorldLeft = worldLeft;
        lastWorldTop = worldTop;
        lastWorldSpan = worldSpan;
        lastBlocksPerPixel = blocksPerPixel;

        renderMapPass(guiGraphics, mc, worldLeft, worldTop, worldSpan, pixelsPerBlock);
        renderOverlayPass(guiGraphics, mc, worldLeft, worldTop, pixelsPerBlock);

        int centerVirtualId = ChunkManagerRuntimeEvents.calculateVirtualMapId(centerBlockX, centerBlockZ, currentMapScale);
        guiGraphics.drawString(this.font, "Map ID: " + centerVirtualId, panelX + 12, panelY + 116, 0xFFD7E3F4, false);
        guiGraphics.drawString(this.font, "Center XZ: " + Mth.floor(centerBlockX) + ", " + Mth.floor(centerBlockZ), panelX + 12, panelY + 132, 0xFFD7E3F4, false);
        guiGraphics.drawString(this.font, "Zoom radius: " + Mth.floor(zoomRadiusBlocks) + " blocks", panelX + 12, panelY + 148, 0xFFD7E3F4, false);
        guiGraphics.drawString(this.font, "LMB drag = pan", panelX + 12, panelY + 248, 0xFF9FB0C6, false);
        guiGraphics.drawString(this.font, "Wheel = zoom on cursor", panelX + 12, panelY + 262, 0xFF9FB0C6, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderMapPass(GuiGraphics guiGraphics, Minecraft mc, double worldLeft, double worldTop, double worldSpan, double pixelsPerBlock) {
        guiGraphics.flush();
        beginMapScissor(mc);
        try {
            renderStitchedMaps(guiGraphics, mc, worldLeft, worldTop, worldSpan, pixelsPerBlock);
        } finally {
            RenderSystem.disableScissor();
        }
        guiGraphics.flush();
    }

    private void renderStitchedMaps(GuiGraphics guiGraphics, Minecraft mc, double worldLeft, double worldTop, double worldSpan, double pixelsPerBlock) {
        int blockSize = 128 * (1 << currentMapScale);
        TileRenderBounds tileBounds = calculateTileRenderBounds(worldLeft, worldTop, worldSpan, pixelsPerBlock, blockSize);
        int minGridX = tileBounds.minGridX;
        int minGridZ = tileBounds.minGridZ;
        int maxGridX = tileBounds.maxGridX;
        int maxGridZ = tileBounds.maxGridZ;
        double tileOverlapPixels = tileBounds.tileOverlapPixels;

        double playerX = mc.player != null ? mc.player.getX() : centerBlockX;
        double playerZ = mc.player != null ? mc.player.getZ() : centerBlockZ;
        double cameraOffsetBlocksX = centerBlockX - playerX;
        double cameraOffsetBlocksZ = centerBlockZ - playerZ;
        double anchorScreenX = mapX + (mapSize * 0.5) - (cameraOffsetBlocksX * pixelsPerBlock);
        double anchorScreenY = mapY + (mapSize * 0.5) - (cameraOffsetBlocksZ * pixelsPerBlock);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int renderedTiles = 0;

        for (int gridZ = minGridZ; gridZ <= maxGridZ; gridZ++) {
            for (int gridX = minGridX; gridX <= maxGridX; gridX++) {
                if (renderedTiles >= MAX_TOTAL_TILES) {
                    break;
                }

                int tileMinX = gridX * blockSize;
                int tileMinZ = gridZ * blockSize;
                double tileCenterX = tileMinX + (blockSize / 2.0);
                double tileCenterZ = tileMinZ + (blockSize / 2.0);

                int mapId = ChunkManagerRuntimeEvents.calculateVirtualMapId(tileCenterX, tileCenterZ, currentMapScale);
                MapItemSavedData mapData = ChunkManagerRuntimeEvents.getVirtualMapData(mc, tileCenterX, tileCenterZ, currentMapScale, mapId);

                int scaleMultiplier = 1 << currentMapScale;
                // Vanilla map space: top-left comes from map center minus 64 pixels in map coordinates.
                double tileWorldLeft = mapData != null ? mapData.centerX - (64.0 * scaleMultiplier) : tileMinX;
                double tileWorldTop = mapData != null ? mapData.centerZ - (64.0 * scaleMultiplier) : tileMinZ;
                double tileWorldRight = tileWorldLeft + (128.0 * scaleMultiplier);
                double tileWorldBottom = tileWorldTop + (128.0 * scaleMultiplier);

                // Player acts as absolute world anchor; tile offsets are projected into screen pixels.
                double xOffsetPixels = (tileWorldLeft - playerX) * pixelsPerBlock;
                double yOffsetPixels = (tileWorldTop - playerZ) * pixelsPerBlock;
                double drawLeft = anchorScreenX + xOffsetPixels - tileOverlapPixels;
                double drawTop = anchorScreenY + yOffsetPixels - tileOverlapPixels;
                double drawRight = anchorScreenX + ((tileWorldRight - playerX) * pixelsPerBlock) + tileOverlapPixels;
                double drawBottom = anchorScreenY + ((tileWorldBottom - playerZ) * pixelsPerBlock) + tileOverlapPixels;

                int drawX = Mth.floor(drawLeft - 0.5);
                int drawY = Mth.floor(drawTop - 0.5);
                int drawW = Mth.ceil(drawRight + 0.5) - drawX;
                int drawH = Mth.ceil(drawBottom + 0.5) - drawY;

                if (drawW <= 0 || drawH <= 0) {
                    continue;
                }

                boolean missingData = mapData == null || !ChunkManagerRuntimeEvents.hasRenderableData(mapData);
                if (missingData || mapId == -1) {
                    ChunkManagerRuntimeEvents.requestVirtualTileData(mc, tileCenterX, tileCenterZ, currentMapScale, mapId);
                    renderFullmapFallbackTile(guiGraphics, drawX, drawY, drawW, drawH, tileMinX, tileMinZ, blockSize);
                    debugTilePlacement(
                            gridX,
                            gridZ,
                            tileWorldLeft,
                            tileWorldTop,
                            tileWorldRight,
                            tileWorldBottom,
                            drawX,
                            drawY,
                            drawW,
                            drawH,
                            mapId,
                            true,
                            anchorScreenX,
                            anchorScreenY,
                            playerX,
                            playerZ
                    );
                    renderedTiles++;
                    continue;
                }

                ChunkManagerRuntimeEvents.updateMapColorsClientSide(mc, mapData);
                mc.gameRenderer.getMapRenderer().update(mapId, mapData);

                PoseStack pose = guiGraphics.pose();
                pose.pushPose();
                pose.translate(drawX, drawY, 100.0f);
                pose.scale(drawW / 128.0f, drawH / 128.0f, 1.0f);
                mc.gameRenderer.getMapRenderer().render(pose, buffers, mapId, mapData, true, 15728880);
                pose.popPose();

                debugTilePlacement(
                    gridX,
                    gridZ,
                    tileWorldLeft,
                    tileWorldTop,
                    tileWorldRight,
                    tileWorldBottom,
                    drawX,
                    drawY,
                    drawW,
                    drawH,
                    mapId,
                    false,
                    anchorScreenX,
                    anchorScreenY,
                    playerX,
                    playerZ
                );
                renderedTiles++;
            }

            if (renderedTiles >= MAX_TOTAL_TILES) {
                break;
            }
        }

        buffers.endBatch();
    }

    private void renderFullmapFallbackTile(GuiGraphics guiGraphics, int drawX, int drawY, int drawW, int drawH, int tileMinX, int tileMinZ, int blockSize) {
        float u = (float) (Math.floorMod(tileMinX, FALLBACK_WORLD_WRAP_BLOCKS) / (double) FALLBACK_WORLD_WRAP_BLOCKS);
        float v = (float) (Math.floorMod(tileMinZ, FALLBACK_WORLD_WRAP_BLOCKS) / (double) FALLBACK_WORLD_WRAP_BLOCKS);
        float uSize = (float) (blockSize / (double) FALLBACK_WORLD_WRAP_BLOCKS);
        float vSize = (float) (blockSize / (double) FALLBACK_WORLD_WRAP_BLOCKS);
        int uWidthPx = Math.max(1, Mth.ceil(uSize * FALLBACK_TEXTURE_SIZE));
        int vHeightPx = Math.max(1, Mth.ceil(vSize * FALLBACK_TEXTURE_SIZE));

        guiGraphics.blit(
                FULLMAP_FALLBACK_TEXTURE,
                drawX,
                drawY,
                drawW,
                drawH,
                u * FALLBACK_TEXTURE_SIZE,
                v * FALLBACK_TEXTURE_SIZE,
                uWidthPx,
                vHeightPx,
                FALLBACK_TEXTURE_SIZE,
                FALLBACK_TEXTURE_SIZE
        );
    }

    private void debugTilePlacement(int gridX, int gridZ, double tileWorldLeft, double tileWorldTop, double tileWorldRight, double tileWorldBottom,
            int drawX, int drawY, int drawW, int drawH, int mapId, boolean fallback, double anchorScreenX, double anchorScreenY, double playerX,
            double playerZ) {
        if (!ChunkManagerClientState.isDebugLogsEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastDebugLogMs) < DEBUG_LOG_INTERVAL_MS) {
            return;
        }
        lastDebugLogMs = now;

        LOGGER.info(
            "[ap_chunkmanager/map-debug] grid=({}, {}) mapId={} scale={} zoomRadius={} fallback={} player=({}, {}) anchor=({}, {}) worldRect=({}, {})-({}, {}) drawRect=({}, {}) {}x{}",
            gridX,
            gridZ,
                mapId,
                currentMapScale,
            Mth.floor(zoomRadiusBlocks),
            fallback,
            Mth.floor(playerX),
            Mth.floor(playerZ),
            Mth.floor(anchorScreenX),
            Mth.floor(anchorScreenY),
            Mth.floor(tileWorldLeft),
            Mth.floor(tileWorldTop),
            Mth.floor(tileWorldRight),
            Mth.floor(tileWorldBottom),
            drawX,
            drawY,
            drawW,
            drawH
        );
    }

    private void renderChunkGrid(GuiGraphics guiGraphics, double worldLeft, double worldTop, double pixelsPerBlock) {
        int majorColor = 0x78E6F0FF;
        int minorColor = 0x46FFFFFF;
        int gridStepBlocks = 16;
        while ((gridStepBlocks * pixelsPerBlock) < 14.0 && gridStepBlocks < 1024) {
            gridStepBlocks *= 2;
        }

        int minX = Mth.floor(worldLeft);
        int minZ = Mth.floor(worldTop);
        int maxX = Mth.floor(worldLeft + lastWorldSpan);
        int maxZ = Mth.floor(worldTop + lastWorldSpan);

        int firstGridX = Math.floorDiv(minX, gridStepBlocks) * gridStepBlocks;
        int firstGridZ = Math.floorDiv(minZ, gridStepBlocks) * gridStepBlocks;

        for (int gx = firstGridX; gx <= maxX; gx += gridStepBlocks) {
            int sx = mapX + (int) Math.round((gx - worldLeft) * pixelsPerBlock);
            boolean majorLine = isMajorGridLine(gx);
            guiGraphics.fill(sx, mapY, sx + (majorLine ? 2 : 1), mapY + mapSize, majorLine ? majorColor : minorColor);
        }

        for (int gz = firstGridZ; gz <= maxZ; gz += gridStepBlocks) {
            int sy = mapY + (int) Math.round((gz - worldTop) * pixelsPerBlock);
            boolean majorLine = isMajorGridLine(gz);
            guiGraphics.fill(mapX, sy, mapX + mapSize, sy + (majorLine ? 2 : 1), majorLine ? majorColor : minorColor);
        }
    }

    private static boolean isMajorGridLine(int worldCoordinate) {
        return Math.floorMod(worldCoordinate, 64) == 0;
    }

    private void renderCompass(GuiGraphics guiGraphics) {
        int cx = mapX + 20;
        int cy = mapY + 20;
        guiGraphics.fill(cx - 14, cy - 14, cx + 14, cy + 14, 0x80202C3E);
        drawRotatedTexture(guiGraphics, COMPASS_TEXTURE, cx, cy, 20, Minecraft.getInstance().player != null ? -Minecraft.getInstance().player.getYRot() : 0.0f);
        guiGraphics.drawString(this.font, "N", cx - 3, cy - 20, 0xFFEAF2FF, false);
        guiGraphics.drawString(this.font, "S", cx - 3, cy + 12, 0xFFEAF2FF, false);
        guiGraphics.drawString(this.font, "W", cx - 20, cy - 4, 0xFFEAF2FF, false);
        guiGraphics.drawString(this.font, "E", cx + 12, cy - 4, 0xFFEAF2FF, false);
    }

    private void renderPlayerMarker(GuiGraphics guiGraphics, Minecraft mc, double worldLeft, double worldTop, double pixelsPerBlock) {
        if (mc.player == null) {
            return;
        }

        int px = worldToMapPixel(mc.player.getX(), worldLeft, pixelsPerBlock, mapX);
        int pz = worldToMapPixel(mc.player.getZ(), worldTop, pixelsPerBlock, mapY);
        if (px < mapX - 12 || px > mapX + mapSize + 12 || pz < mapY - 12 || pz > mapY + mapSize + 12) {
            return;
        }

        float yawRad = (float) Math.toRadians(mc.player.getYRot());
        double dirX = -Math.sin(yawRad);
        double dirZ = Math.cos(yawRad);

        int tipX = px + (int) Math.round(dirX * 10.0);
        int tipZ = pz + (int) Math.round(dirZ * 10.0);

        guiGraphics.fill(px - 2, pz - 2, px + 2, pz + 2, 0xFF8FE8FF);
        drawPixelLine(guiGraphics, px, pz, tipX, tipZ, 0xFF42D1FF);
        guiGraphics.fill(tipX - 2, tipZ - 2, tipX + 2, tipZ + 2, 0xFF42D1FF);
    }

    private void renderOverlayPass(GuiGraphics guiGraphics, Minecraft mc, double worldLeft, double worldTop, double pixelsPerBlock) {
        guiGraphics.flush();
        RenderSystem.disableDepthTest();
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0.0f, 0.0f, 350.0f);

        try {
            beginMapScissor(mc);
            try {
                if (ChunkManagerClientState.isGridEnabled()) {
                    renderChunkGrid(guiGraphics, worldLeft, worldTop, pixelsPerBlock);
                }
                renderPlayerMarker(guiGraphics, mc, worldLeft, worldTop, pixelsPerBlock);
            } finally {
                RenderSystem.disableScissor();
            }

            renderCompass(guiGraphics);
        } finally {
            pose.popPose();
            RenderSystem.enableDepthTest();
        }
        guiGraphics.flush();
    }

    private TileRenderBounds calculateTileRenderBounds(double worldLeft, double worldTop, double worldSpan, double pixelsPerBlock, int blockSize) {
        double safePixelsPerBlock = Math.max(0.0001, pixelsPerBlock);
        double tileSizePixels = blockSize * safePixelsPerBlock;

        double dynamicPaddingPixels = Mth.clamp(
                TILE_SELECTION_PADDING_PIXELS + (tileSizePixels * 0.02),
                TILE_SELECTION_PADDING_PIXELS,
                MAX_DYNAMIC_PADDING_PIXELS
        );
        double paddingBlocks = Math.max(1.0, dynamicPaddingPixels / safePixelsPerBlock);

        double dynamicOverlapPixels = Mth.clamp(
                TILE_OVERLAP_PIXELS + (dynamicPaddingPixels * 0.35),
                TILE_OVERLAP_PIXELS,
                Math.max(TILE_OVERLAP_PIXELS, Math.min(MAX_DYNAMIC_OVERLAP_PIXELS, tileSizePixels * 0.25))
        );
        double tileOverlapPixels = quantizeOverlap(dynamicOverlapPixels);

        double paddedWorldLeft = worldLeft - paddingBlocks;
        double paddedWorldTop = worldTop - paddingBlocks;
        double paddedWorldRight = worldLeft + worldSpan + paddingBlocks;
        double paddedWorldBottom = worldTop + worldSpan + paddingBlocks;

        int minGridX = Math.floorDiv(Mth.floor(paddedWorldLeft), blockSize);
        int minGridZ = Math.floorDiv(Mth.floor(paddedWorldTop), blockSize);
        int maxGridX = Math.floorDiv(Mth.ceil(paddedWorldRight) - 1, blockSize);
        int maxGridZ = Math.floorDiv(Mth.ceil(paddedWorldBottom) - 1, blockSize);

        int centerGridX = Math.floorDiv(Mth.floor(centerBlockX), blockSize);
        int centerGridZ = Math.floorDiv(Mth.floor(centerBlockZ), blockSize);
        int viewTileCount = Math.max(3, Mth.ceil(worldSpan / Math.max(1.0, blockSize)) + 2);
        int maxTilesAxis = Math.min(MAX_TILES_PER_AXIS, viewTileCount);

        minGridX = clampToTileAxis(minGridX, maxGridX, centerGridX, maxTilesAxis, true);
        maxGridX = clampToTileAxis(minGridX, maxGridX, centerGridX, maxTilesAxis, false);
        minGridZ = clampToTileAxis(minGridZ, maxGridZ, centerGridZ, maxTilesAxis, true);
        maxGridZ = clampToTileAxis(minGridZ, maxGridZ, centerGridZ, maxTilesAxis, false);

        return new TileRenderBounds(minGridX, minGridZ, maxGridX, maxGridZ, tileOverlapPixels);
    }

    private static int clampToTileAxis(int minGrid, int maxGrid, int centerGrid, int maxTilesAxis, boolean returnMin) {
        int currentCount = maxGrid - minGrid + 1;
        if (currentCount <= maxTilesAxis) {
            return returnMin ? minGrid : maxGrid;
        }

        int half = maxTilesAxis / 2;
        int newMin = centerGrid - half;
        int newMax = newMin + maxTilesAxis - 1;
        return returnMin ? newMin : newMax;
    }

    private static double quantizeOverlap(double overlapPixels) {
        return Math.round(overlapPixels / OVERLAP_QUANTIZATION_PIXELS) * OVERLAP_QUANTIZATION_PIXELS;
    }

    private record TileRenderBounds(int minGridX, int minGridZ, int maxGridX, int maxGridZ, double tileOverlapPixels) {
    }

    private static void drawPixelLine(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int color) {
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

    private static byte scaleForZoom(double zoomRadiusBlocks) {
        if (zoomRadiusBlocks <= 128.0) {
            return MIN_SCALE;
        }
        if (zoomRadiusBlocks <= 256.0) {
            return 1;
        }
        if (zoomRadiusBlocks <= 512.0) {
            return 2;
        }
        if (zoomRadiusBlocks <= 1024.0) {
            return 3;
        }
        return MAX_SCALE;
    }

    private boolean isInsideMap(double mouseX, double mouseY) {
        return mouseX >= mapX && mouseX <= mapX + mapSize && mouseY >= mapY && mouseY <= mapY + mapSize;
    }

    private static int worldToMapPixel(double worldCoord, double worldStart, double pixelsPerBlock, int mapStart) {
        return mapStart + (int) Math.round((worldCoord - worldStart) * pixelsPerBlock);
    }

    private static void drawRotatedTexture(GuiGraphics guiGraphics, ResourceLocation texture, int centerX, int centerY, int size, float rotationDegrees) {
        PoseStack pose = guiGraphics.pose();
        int halfSize = size / 2;
        pose.pushPose();
        pose.translate(centerX, centerY, 0.0f);
        pose.mulPose(Axis.ZP.rotationDegrees(rotationDegrees));
        guiGraphics.blit(texture, -halfSize, -halfSize, 0.0f, 0.0f, size, size, size, size);
        pose.popPose();
    }

    private void beginMapScissor(Minecraft mc) {
        Window window = mc.getWindow();
        double guiScale = window.getGuiScale();

        int scissorX = (int) Math.floor(mapX * guiScale);
        int scissorY = (int) Math.floor(window.getHeight() - ((mapY + mapSize) * guiScale));
        int scissorW = Math.max(1, (int) Math.ceil(mapSize * guiScale));
        int scissorH = Math.max(1, (int) Math.ceil(mapSize * guiScale));

        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
    }
}
