package net.mcreator.ap_chunkmanager.client;

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

public class ChunkManagerMapScreen extends Screen {
    private static final byte MIN_SCALE = 0;
    private static final byte MAX_SCALE = 4;
    private static final double MIN_ZOOM_RADIUS_BLOCKS = 48.0;
    private static final double MAX_ZOOM_RADIUS_BLOCKS = 2048.0;
    private static final double TILE_SELECTION_PADDING_PIXELS = 3.0;
    private static final double TILE_OVERLAP_PIXELS = 1.25;
    private static final ResourceLocation COMPASS_TEXTURE = new ResourceLocation("minecraft", "textures/item/compass_00.png");

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

        renderMapLayer(guiGraphics, mc, worldLeft, worldTop, worldSpan, pixelsPerBlock);
        renderTopOverlay(guiGraphics, mc, worldLeft, worldTop, pixelsPerBlock);

        int centerVirtualId = ChunkManagerRuntimeEvents.calculateVirtualMapId(centerBlockX, centerBlockZ, currentMapScale);
        guiGraphics.drawString(this.font, "Map ID: " + centerVirtualId, panelX + 12, panelY + 116, 0xFFD7E3F4, false);
        guiGraphics.drawString(this.font, "Center XZ: " + Mth.floor(centerBlockX) + ", " + Mth.floor(centerBlockZ), panelX + 12, panelY + 132, 0xFFD7E3F4, false);
        guiGraphics.drawString(this.font, "Zoom radius: " + Mth.floor(zoomRadiusBlocks) + " blocks", panelX + 12, panelY + 148, 0xFFD7E3F4, false);
        guiGraphics.drawString(this.font, "LMB drag = pan", panelX + 12, panelY + 248, 0xFF9FB0C6, false);
        guiGraphics.drawString(this.font, "Wheel = zoom on cursor", panelX + 12, panelY + 262, 0xFF9FB0C6, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderMapLayer(GuiGraphics guiGraphics, Minecraft mc, double worldLeft, double worldTop, double worldSpan, double pixelsPerBlock) {
        beginMapScissor(mc);
        try {
            renderStitchedMaps(guiGraphics, mc, worldLeft, worldTop, worldSpan, pixelsPerBlock);
        } finally {
            RenderSystem.disableScissor();
        }
    }

    private void renderStitchedMaps(GuiGraphics guiGraphics, Minecraft mc, double worldLeft, double worldTop, double worldSpan, double pixelsPerBlock) {
        double tileSelectionPaddingBlocks = Math.max(1.0, TILE_SELECTION_PADDING_PIXELS / Math.max(0.0001, pixelsPerBlock));
        double paddedWorldLeft = worldLeft - tileSelectionPaddingBlocks;
        double paddedWorldTop = worldTop - tileSelectionPaddingBlocks;
        double paddedWorldRight = worldLeft + worldSpan + tileSelectionPaddingBlocks;
        double paddedWorldBottom = worldTop + worldSpan + tileSelectionPaddingBlocks;
        int blockSize = 128 * (1 << currentMapScale);
        int minGridX = Math.floorDiv(Mth.floor(paddedWorldLeft), blockSize);
        int minGridZ = Math.floorDiv(Mth.floor(paddedWorldTop), blockSize);
        int maxGridX = Math.floorDiv(Mth.ceil(paddedWorldRight) - 1, blockSize);
        int maxGridZ = Math.floorDiv(Mth.ceil(paddedWorldBottom) - 1, blockSize);
        double tileOverlapPixels = Mth.clamp(TILE_OVERLAP_PIXELS, 0.0, Math.max(0.0, blockSize * pixelsPerBlock * 0.25));

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        for (int gridZ = minGridZ; gridZ <= maxGridZ; gridZ++) {
            for (int gridX = minGridX; gridX <= maxGridX; gridX++) {
                int tileMinX = gridX * blockSize;
                int tileMinZ = gridZ * blockSize;
                double tileCenterX = tileMinX + (blockSize / 2.0);
                double tileCenterZ = tileMinZ + (blockSize / 2.0);

                int mapId = ChunkManagerRuntimeEvents.calculateVirtualMapId(tileCenterX, tileCenterZ, currentMapScale);
                MapItemSavedData mapData = ChunkManagerRuntimeEvents.getVirtualMapData(mc, tileCenterX, tileCenterZ, currentMapScale, mapId);
                if (mapData == null) {
                    continue;
                }

                ChunkManagerRuntimeEvents.updateMapColorsClientSide(mc, mapData);
                mc.gameRenderer.getMapRenderer().update(mapId, mapData);

                double drawLeft = mapX + ((tileMinX - worldLeft) * pixelsPerBlock) - tileOverlapPixels;
                double drawTop = mapY + ((tileMinZ - worldTop) * pixelsPerBlock) - tileOverlapPixels;
                double drawRight = mapX + (((tileMinX + blockSize) - worldLeft) * pixelsPerBlock) + tileOverlapPixels;
                double drawBottom = mapY + (((tileMinZ + blockSize) - worldTop) * pixelsPerBlock) + tileOverlapPixels;

                int drawX = Mth.floor(drawLeft);
                int drawY = Mth.floor(drawTop);
                int drawW = Mth.ceil(drawRight) - drawX;
                int drawH = Mth.ceil(drawBottom) - drawY;

                if (drawW <= 0 || drawH <= 0) {
                    continue;
                }

                PoseStack pose = guiGraphics.pose();
                pose.pushPose();
                pose.translate(drawX, drawY, 100.0f);
                pose.scale(drawW / 128.0f, drawH / 128.0f, 1.0f);
                mc.gameRenderer.getMapRenderer().render(pose, buffers, mapId, mapData, true, 15728880);
                pose.popPose();
            }
        }

        buffers.endBatch();
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

    private void renderTopOverlay(GuiGraphics guiGraphics, Minecraft mc, double worldLeft, double worldTop, double pixelsPerBlock) {
        guiGraphics.flush();
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0.0f, 0.0f, 250.0f);

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
        guiGraphics.flush();
        pose.popPose();
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
