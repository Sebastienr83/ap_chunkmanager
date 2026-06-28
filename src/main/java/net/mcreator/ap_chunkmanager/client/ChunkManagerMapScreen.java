package net.mcreator.ap_chunkmanager.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class ChunkManagerMapScreen extends Screen {
    private static final byte MIN_SCALE = 0;
    private static final byte MAX_SCALE = 4;
    private static final double MIN_ZOOM_RADIUS_BLOCKS = 48.0;
    private static final double MAX_ZOOM_RADIUS_BLOCKS = 2048.0;

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

                int virtualMapId = ChunkManagerRuntimeEvents.calculateVirtualMapId(targetWorldX, targetWorldZ, currentMapScale);
                int physicalMapId = ChunkManagerRuntimeEvents.createExtractedMapFromVirtual(mc, virtualMapId, targetWorldX, targetWorldZ, currentMapScale);

                ItemStack mapItem = new ItemStack(Items.FILLED_MAP);
                mapItem.getOrCreateTag().putInt("map", physicalMapId);
                mc.player.getInventory().add(mapItem);
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

        beginMapScissor(mc);
        try {
            renderStitchedMaps(guiGraphics, mc, worldLeft, worldTop, worldSpan, pixelsPerBlock);

            if (ChunkManagerClientState.isGridEnabled()) {
                renderChunkGrid(guiGraphics, worldLeft, worldTop, pixelsPerBlock);
            }

            renderPlayerMarker(guiGraphics, mc, worldLeft, worldTop, pixelsPerBlock);
        } finally {
            RenderSystem.disableScissor();
        }

        renderCompass(guiGraphics);

        int centerVirtualId = ChunkManagerRuntimeEvents.calculateVirtualMapId(centerBlockX, centerBlockZ, currentMapScale);
        guiGraphics.drawString(this.font, "Map ID: " + centerVirtualId, panelX + 12, panelY + 116, 0xFFD7E3F4, false);
        guiGraphics.drawString(this.font, "Center XZ: " + Mth.floor(centerBlockX) + ", " + Mth.floor(centerBlockZ), panelX + 12, panelY + 132, 0xFFD7E3F4, false);
        guiGraphics.drawString(this.font, "Zoom radius: " + Mth.floor(zoomRadiusBlocks) + " blocks", panelX + 12, panelY + 148, 0xFFD7E3F4, false);
        guiGraphics.drawString(this.font, "LMB drag = pan", panelX + 12, panelY + 248, 0xFF9FB0C6, false);
        guiGraphics.drawString(this.font, "Wheel = zoom on cursor", panelX + 12, panelY + 262, 0xFF9FB0C6, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderStitchedMaps(GuiGraphics guiGraphics, Minecraft mc, double worldLeft, double worldTop, double worldSpan, double pixelsPerBlock) {
        int blockSize = 128 * (1 << currentMapScale);
        int minGridX = Math.floorDiv(Mth.floor(worldLeft), blockSize);
        int minGridZ = Math.floorDiv(Mth.floor(worldTop), blockSize);
        int maxGridX = Math.floorDiv(Mth.ceil(worldLeft + worldSpan) - 1, blockSize);
        int maxGridZ = Math.floorDiv(Mth.ceil(worldTop + worldSpan) - 1, blockSize);

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

                int drawX = mapX + (int) Math.round((tileMinX - worldLeft) * pixelsPerBlock);
                int drawY = mapY + (int) Math.round((tileMinZ - worldTop) * pixelsPerBlock);
                int drawW = (int) Math.ceil(blockSize * pixelsPerBlock);
                int drawH = drawW;

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
        int color = 0x60FFFFFF;
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
            guiGraphics.fill(sx, mapY, sx + 1, mapY + mapSize, color);
        }

        for (int gz = firstGridZ; gz <= maxZ; gz += gridStepBlocks) {
            int sy = mapY + (int) Math.round((gz - worldTop) * pixelsPerBlock);
            guiGraphics.fill(mapX, sy, mapX + mapSize, sy + 1, color);
        }
    }

    private void renderCompass(GuiGraphics guiGraphics) {
        int cx = mapX + 20;
        int cy = mapY + 20;
        guiGraphics.fill(cx - 1, cy - 10, cx + 1, cy + 10, 0xCCB6C7E2);
        guiGraphics.fill(cx - 10, cy - 1, cx + 10, cy + 1, 0xCCB6C7E2);
        guiGraphics.drawString(this.font, "N", cx - 3, cy - 20, 0xFFEAF2FF, false);
        guiGraphics.drawString(this.font, "S", cx - 3, cy + 12, 0xFFEAF2FF, false);
        guiGraphics.drawString(this.font, "W", cx - 20, cy - 4, 0xFFEAF2FF, false);
        guiGraphics.drawString(this.font, "E", cx + 12, cy - 4, 0xFFEAF2FF, false);
    }

    private void renderPlayerMarker(GuiGraphics guiGraphics, Minecraft mc, double worldLeft, double worldTop, double pixelsPerBlock) {
        if (mc.player == null) {
            return;
        }

        int px = mapX + (int) Math.round((mc.player.getX() - worldLeft) * pixelsPerBlock);
        int pz = mapY + (int) Math.round((mc.player.getZ() - worldTop) * pixelsPerBlock);
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
