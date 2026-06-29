package net.mcreator.ap_chunkmanager.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.mcreator.ap_chunkmanager.network.CopyChunkMapSectionPacket;
import net.mcreator.ap_chunkmanager.network.CreateChunkRulePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ChunkManagerAdminScreen extends Screen {
    private static final Component TITLE = Component.literal("Chunk Manager Admin");
    private static final int OUTER_MARGIN = 10;
    private static final int SPLIT_GAP = 12;
    private static final int PANEL_PADDING = 8;
    private static final int ATTR_ROW_HEIGHT = 26;
    private static final int ATTR_LABEL_HEIGHT = 12;
    private static final int ATTR_ROW_GAP = 8;
    private static final int WINDOW_MAX_W = 1160;
    private static final int WINDOW_MAX_H = 700;
    private static final int MAP_CONTROLS_H = 148;
    private static final int MAP_INFO_PANEL_H = 62;

    private static final byte MIN_SCALE = 0;
    private static final byte MAX_SCALE = 4;
    private static final double MIN_ZOOM_RADIUS_BLOCKS = 64.0;
    private static final double MAX_ZOOM_RADIUS_BLOCKS = 2048.0;
    private static final int MAX_RENDER_TILES = 81;

    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;

    private int mapX;
    private int mapY;
    private int mapW;
    private int mapH;

    private int mapControlsX;
    private int mapControlsY;
    private int mapControlsW;
    private int mapControlsH;

    private int controlsX;
    private int controlsY;
    private int controlsW;
    private int controlsH;

    private int attributesX;
    private int attributesY;
    private int attributesW;
    private int attributesH;

    private byte currentMapScale = 1;
    private double zoomRadiusBlocks = 256.0;
    private double centerBlockX;
    private double centerBlockZ;
    private boolean centerInitialized;

    private double worldLeft;
    private double worldTop;
    private double worldSpan;
    private double blocksPerPixel = 1.0;
    private double pixelsPerBlock = 1.0;

    private boolean panning;
    private double panStartMouseX;
    private double panStartMouseY;
    private double panStartCenterX;
    private double panStartCenterZ;

    private boolean rightSelecting;
    private boolean rightDragEraseMode;
    private int lastSelectedChunkX = Integer.MIN_VALUE;
    private int lastSelectedChunkZ = Integer.MIN_VALUE;

    private int attributesScrollOffset;
    private int attributesContentHeight;
    private int mapSettingsScrollOffset;
    private int mapSettingsContentHeight;

    private Button createChunkButton;
    private Button teamManagerButton;
    private Button gridButton;
    private Button minimapScaleButton;
    private Button centerOnPlayerButton;
    private EditBox nameEdit;
    private Button rolePickerButton;
    private String selectedRoleName = "";
    private int selectedRoleColor = 0xFFFFFF;
    private Button teamRequirementButton;
    private TeamRequirement teamRequirement = TeamRequirement.REQUIRE_TEAM;
    private EditBox buildHeightAboveFaceEdit;
    private EditBox buildDepthBelowFaceEdit;
    private EditBox initialChunkQuotaEdit;
    private EditBox chunkRewardResourceEdit;
    private EditBox chunkRewardAmountEdit;
    private EditBox chunkCostResourceEdit;
    private EditBox chunkCostAmountEdit;
    private Button rewardResourceBrowseButton;
    private Button costResourceBrowseButton;
    private Checkbox allowBuildCheckbox;
    private ColorSlider redSlider;
    private ColorSlider greenSlider;
    private ColorSlider blueSlider;
    private int colorPreviewContentY;
    private List<String> registryEntries = List.of();

    private boolean selectorOpen;
    private EditBox selectorSearchEdit;
    private EditBox selectorTargetEdit;
    private String selectorTitle = "Select Resource";
    private List<String> selectorFilteredEntries = List.of();
    private int selectorScrollOffset;
    private int selectorX;
    private int selectorY;
    private int selectorW;
    private int selectorH;
    private static final int SELECTOR_ROW_HEIGHT = 16;
    private static final int MAX_SELECTOR_RESULTS = 500;
    private static final List<String> RECENT_RESOURCES = new ArrayList<>();
    private static final Set<String> FAVORITE_RESOURCES = new LinkedHashSet<>(List.of(
            "minecraft:emerald",
            "minecraft:diamond",
            "minecraft:gold_ingot",
            "minecraft:iron_ingot",
            "minecraft:stone"
    ));

    private final List<AttributeField> attributeFields = new ArrayList<>();
    private final Set<Long> selectedChunks = new LinkedHashSet<>();
    private final boolean isAdminMode;
    private int playerChunkCredit = 0;

    public ChunkManagerAdminScreen() {
        this(true);
    }

    public ChunkManagerAdminScreen(boolean isAdminMode) {
        super(Component.literal("ChunkManager Admin"));
        this.isAdminMode = isAdminMode;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        layoutPanels();
        registryEntries = buildRegistryEntries();
        mapSettingsContentHeight = 112;

        gridButton = addRenderableWidget(
            Button.builder(Component.empty(), b -> {
                ChunkManagerClientState.toggleGrid();
                updateMapControlButtonLabels();
            }).bounds(mapControlsX + PANEL_PADDING, mapControlsY + 70, mapControlsW - (PANEL_PADDING * 2), 20).build()
        );

        minimapScaleButton = addRenderableWidget(
            Button.builder(Component.empty(), b -> {
                ChunkManagerClientState.cycleMinimapScale();
                updateMapControlButtonLabels();
            }).bounds(mapControlsX + PANEL_PADDING, mapControlsY + 70, mapControlsW - (PANEL_PADDING * 2), 20).build()
        );

        centerOnPlayerButton = addRenderableWidget(
            Button.builder(Component.literal("Center on player"), b -> centerOnPlayer())
                .bounds(mapControlsX + PANEL_PADDING, mapControlsY + 94, mapControlsW - (PANEL_PADDING * 2), 20)
                .build()
        );
        updateMapControlButtonLabels();
        updateFixedWidgetPositions();

        selectorSearchEdit = new EditBox(this.font, 0, 0, 100, 20, Component.literal("Search resource"));
        selectorSearchEdit.setResponder(value -> refreshSelectorFilteredEntries());

        attributeFields.clear();
        if (isAdminMode) {
            createChunkButton = addRenderableWidget(
                    Button.builder(Component.literal("Create Chunk"), button -> submitCreateChunkRule())
                    .bounds(controlsX + PANEL_PADDING, controlsY + PANEL_PADDING, controlsW - (PANEL_PADDING * 2), 22)
                            .build()
            );

            teamManagerButton = addRenderableWidget(
                Button.builder(Component.literal("Team Manager"), button -> openTeamManager())
                    .bounds(controlsX + PANEL_PADDING, controlsY + PANEL_PADDING, controlsW - (PANEL_PADDING * 2), 22)
                    .build()
            );

            int contentY = PANEL_PADDING + 30;
            int fieldX = attributesX + PANEL_PADDING;
            int fieldW = attributesW - (PANEL_PADDING * 2) - 8;

            nameEdit = addTextField(fieldX, fieldW, Component.literal("Name"), "Roads", false);
            addAttributeField(Component.literal("Name"), nameEdit, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

                rolePickerButton = addRenderableWidget(
                    Button.builder(Component.empty(), b -> openChunkRolePicker())
                        .bounds(fieldX, attributesY, fieldW, 20)
                        .build()
                );
                addAttributeField(Component.literal("Chunk Role"), rolePickerButton, contentY);
                contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            teamRequirementButton = addRenderableWidget(
                Button.builder(teamRequirement.label(), button -> {
                    teamRequirement = teamRequirement == TeamRequirement.REQUIRE_TEAM ? TeamRequirement.STANDALONE : TeamRequirement.REQUIRE_TEAM;
                    button.setMessage(teamRequirement.label());
                }).bounds(fieldX, attributesY, fieldW, 20).build()
            );
            addAttributeField(Component.literal("Team Requirement"), teamRequirementButton, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            buildHeightAboveFaceEdit = addTextField(fieldX, fieldW, Component.literal("Build Height (Above Face)"), "64", true);
            addAttributeField(Component.literal("Build Height (Above Face)"), buildHeightAboveFaceEdit, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            buildDepthBelowFaceEdit = addTextField(fieldX, fieldW, Component.literal("Build Depth (Below Face)"), "8", true);
            addAttributeField(Component.literal("Build Depth (Below Face)"), buildDepthBelowFaceEdit, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            initialChunkQuotaEdit = addTextField(fieldX, fieldW, Component.literal("Initial Chunk Quota"), "8", true);
            addAttributeField(Component.literal("Initial Chunk Quota"), initialChunkQuotaEdit, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            chunkRewardResourceEdit = addTextField(fieldX, fieldW - 66, Component.literal("Chunk Reward Resource"), "minecraft:emerald", false);
            rewardResourceBrowseButton = addRenderableWidget(
                Button.builder(Component.literal("Browse"), b -> openResourceSelector(chunkRewardResourceEdit, "Reward Resource"))
                    .bounds(fieldX + fieldW - 62, attributesY, 62, 20)
                    .build()
            );
            addAttributeField(Component.literal("Chunk Reward Resource"), chunkRewardResourceEdit, contentY);
            addAttributeField(Component.literal(""), rewardResourceBrowseButton, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            chunkRewardAmountEdit = addTextField(fieldX, fieldW, Component.literal("Chunk Reward Amount"), "0", true);
            addAttributeField(Component.literal("Chunk Reward Amount"), chunkRewardAmountEdit, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            chunkCostResourceEdit = addTextField(fieldX, fieldW - 66, Component.literal("Chunk Cost Resource"), "minecraft:diamond", false);
            costResourceBrowseButton = addRenderableWidget(
                Button.builder(Component.literal("Browse"), b -> openResourceSelector(chunkCostResourceEdit, "Cost Resource"))
                    .bounds(fieldX + fieldW - 62, attributesY, 62, 20)
                    .build()
            );
            addAttributeField(Component.literal("Chunk Cost Resource"), chunkCostResourceEdit, contentY);
            addAttributeField(Component.literal(""), costResourceBrowseButton, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            chunkCostAmountEdit = addTextField(fieldX, fieldW, Component.literal("Chunk Cost Amount"), "0", true);
            addAttributeField(Component.literal("Chunk Cost Amount"), chunkCostAmountEdit, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            allowBuildCheckbox = addRenderableWidget(new Checkbox(fieldX, attributesY, fieldW, 20, Component.literal("Allow Build"), true));
            addAttributeField(Component.literal(""), allowBuildCheckbox, contentY);
            contentY += ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            redSlider = addRenderableWidget(new ColorSlider(fieldX, attributesY, fieldW, 20, "Red", 51));
            addAttributeField(Component.literal("Chunk Color - Red"), redSlider, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            greenSlider = addRenderableWidget(new ColorSlider(fieldX, attributesY, fieldW, 20, "Green", 204));
            addAttributeField(Component.literal("Chunk Color - Green"), greenSlider, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            blueSlider = addRenderableWidget(new ColorSlider(fieldX, attributesY, fieldW, 20, "Blue", 102));
            addAttributeField(Component.literal("Chunk Color - Blue"), blueSlider, contentY);
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            colorPreviewContentY = contentY;
            contentY += ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT + ATTR_ROW_GAP;

            attributesContentHeight = contentY + PANEL_PADDING;
        } else {
            createChunkButton = null;
            teamManagerButton = null;
            rolePickerButton = null;
            attributesContentHeight = 0;
        }
        updateRolePickerButtonLabel();
        updateAttributeFieldPositions();
    }

    private EditBox addTextField(int x, int w, Component hint, String initialValue, boolean numericOnly) {
        EditBox edit = new EditBox(this.font, x, attributesY, w, 20, hint);
        edit.setValue(initialValue);
        edit.setHint(hint);
        if (numericOnly) {
            edit.setFilter(value -> value.matches("-?\\d{0,9}"));
        }
        return addRenderableWidget(edit);
    }

    private void updateMapControlButtonLabels() {
        if (gridButton != null) {
            gridButton.setMessage(Component.literal("Grid: " + (ChunkManagerClientState.isGridEnabled() ? "ON" : "OFF")));
        }
        if (minimapScaleButton != null) {
            minimapScaleButton.setMessage(Component.literal("Minimap scale: " + ChunkManagerClientState.getMinimapScale()));
        }
    }

    private void centerOnPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            centerBlockX = mc.player.getX();
            centerBlockZ = mc.player.getZ();
            centerInitialized = true;
        }
    }

    private void updateFixedWidgetPositions() {
        if (createChunkButton != null) {
            int buttonGap = 6;
            int halfW = Math.max(72, (controlsW - (PANEL_PADDING * 2) - buttonGap) / 2);
            createChunkButton.setX(controlsX + PANEL_PADDING);
            createChunkButton.setY(controlsY + PANEL_PADDING);
            createChunkButton.setWidth(halfW);
        }

        if (teamManagerButton != null) {
            int buttonGap = 6;
            int halfW = Math.max(72, (controlsW - (PANEL_PADDING * 2) - buttonGap) / 2);
            teamManagerButton.setX(controlsX + PANEL_PADDING + halfW + buttonGap);
            teamManagerButton.setY(controlsY + PANEL_PADDING);
            teamManagerButton.setWidth(halfW);
        }

        int left = mapControlsX + PANEL_PADDING;
        int innerW = Math.max(80, mapControlsW - (PANEL_PADDING * 2));
        int settingsViewportY = mapControlsY + MAP_INFO_PANEL_H + 6;
        int settingsViewportH = Math.max(24, mapControlsH - MAP_INFO_PANEL_H - 10);
        int maxScroll = Math.max(0, mapSettingsContentHeight - settingsViewportH);
        mapSettingsScrollOffset = Mth.clamp(mapSettingsScrollOffset, 0, maxScroll);

        int contentY = settingsViewportY - mapSettingsScrollOffset;
        int rowMinimapScaleY = contentY + 10;
        int rowCenterY = rowMinimapScaleY + 22;
        int rowGridY = rowCenterY + 22;

        if (gridButton != null) {
            gridButton.setX(left);
            gridButton.setY(rowGridY);
            gridButton.setWidth(innerW);
            gridButton.visible = rowGridY >= settingsViewportY - 2 && (rowGridY + 20) <= (settingsViewportY + settingsViewportH + 2);
            gridButton.active = gridButton.visible;
        }
        if (minimapScaleButton != null) {
            minimapScaleButton.setX(left);
            minimapScaleButton.setY(rowMinimapScaleY);
            minimapScaleButton.setWidth(innerW);
            minimapScaleButton.visible = rowMinimapScaleY >= settingsViewportY - 2
                    && (rowMinimapScaleY + 20) <= (settingsViewportY + settingsViewportH + 2);
            minimapScaleButton.active = minimapScaleButton.visible;
        }
        if (centerOnPlayerButton != null) {
            centerOnPlayerButton.setX(left);
            centerOnPlayerButton.setY(rowCenterY);
            centerOnPlayerButton.setWidth(innerW);
            centerOnPlayerButton.visible = rowCenterY >= settingsViewportY - 2 && (rowCenterY + 20) <= (settingsViewportY + settingsViewportH + 2);
            centerOnPlayerButton.active = centerOnPlayerButton.visible;
        }
    }

    private static List<String> buildRegistryEntries() {
        Set<String> all = new HashSet<>();
        for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
            all.add(key.toString());
        }
        for (ResourceLocation key : BuiltInRegistries.BLOCK.keySet()) {
            all.add(key.toString());
        }
        List<String> values = new ArrayList<>(all);
        Collections.sort(values);
        if (values.isEmpty()) {
            values.add("minecraft:stone");
        }
        return List.copyOf(values);
    }

    private void addAttributeField(Component label, GuiEventListener listener, int contentY) {
        attributeFields.add(new AttributeField(label, listener, contentY));
    }

    private void layoutPanels() {
        windowX = OUTER_MARGIN;
        windowY = OUTER_MARGIN;
        windowW = Math.max(320, this.width - (OUTER_MARGIN * 2));
        windowH = Math.max(260, this.height - (OUTER_MARGIN * 2));

        int panelWidth = isAdminMode ? Math.min(250, this.width / 3) : 0;
        int leftW = windowW - (isAdminMode ? (panelWidth + SPLIT_GAP) : 0);
        leftW = Math.max(220, leftW);

        mapX = windowX;
        mapY = windowY;
        mapW = leftW;
        mapH = Math.max(120, windowH - MAP_CONTROLS_H - 6);

        mapControlsX = mapX;
        mapControlsY = mapY + mapH + 6;
        mapControlsW = leftW;
        mapControlsH = MAP_CONTROLS_H;

        if (isAdminMode) {
            controlsX = mapX + mapW + SPLIT_GAP;
            controlsY = windowY;
            controlsW = panelWidth;
            controlsH = windowH;

            attributesX = controlsX + 4;
            attributesY = controlsY + 38;
            attributesW = Math.max(120, controlsW - 8);
            attributesH = Math.min(Math.max(120, controlsH - 44), this.height - 40);
        } else {
            controlsX = 0;
            controlsY = 0;
            controlsW = 0;
            controlsH = 0;
            attributesX = 0;
            attributesY = 0;
            attributesW = 0;
            attributesH = 0;
        }
    }

    private void updateAttributeFieldPositions() {
        int maxOffset = Math.max(0, attributesContentHeight - attributesH);
        attributesScrollOffset = Mth.clamp(attributesScrollOffset, 0, maxOffset);

        int viewportTop = attributesY + PANEL_PADDING;
        int viewportBottom = attributesY + attributesH - PANEL_PADDING;

        for (AttributeField field : attributeFields) {
            int y = viewportTop + field.contentY - attributesScrollOffset;
            if (field.listener instanceof EditBox edit) {
                edit.setY(y + ATTR_LABEL_HEIGHT);
                edit.setVisible(y >= (viewportTop - 4) && (y + ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT) <= (viewportBottom + 4));
                edit.active = edit.visible;
            } else if (field.listener instanceof Checkbox checkbox) {
                checkbox.setY(y);
                checkbox.visible = y >= (viewportTop - 4) && (y + ATTR_ROW_HEIGHT) <= (viewportBottom + 4);
                checkbox.active = checkbox.visible;
            } else if (field.listener instanceof AbstractWidget widget) {
                widget.setY(y + ATTR_LABEL_HEIGHT);
                widget.visible = y >= (viewportTop - 4) && (y + ATTR_LABEL_HEIGHT + ATTR_ROW_HEIGHT) <= (viewportBottom + 4);
                widget.active = widget.visible;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (selectorOpen && delta != 0.0) {
            int viewportRows = Math.max(1, (selectorH - 54) / SELECTOR_ROW_HEIGHT);
            int maxScroll = Math.max(0, selectorFilteredEntries.size() - viewportRows);
            selectorScrollOffset = Mth.clamp(selectorScrollOffset - (int) Math.signum(delta), 0, maxScroll);
            return true;
        }

        if (isInsideMapSettings(mouseX, mouseY) && delta != 0.0) {
            int settingsViewportH = Math.max(24, mapControlsH - MAP_INFO_PANEL_H - 10);
            int maxScroll = Math.max(0, mapSettingsContentHeight - settingsViewportH);
            mapSettingsScrollOffset = Mth.clamp(mapSettingsScrollOffset - (int) Math.round(delta * 10.0), 0, maxScroll);
            updateFixedWidgetPositions();
            return true;
        }

        if (isAdminMode && isInsideAttributes(mouseX, mouseY) && delta != 0.0) {
            int step = 16;
            attributesScrollOffset = Mth.clamp(
                    attributesScrollOffset - (int) Math.round(delta * step),
                    0,
                    Math.max(0, attributesContentHeight - attributesH)
            );
            updateAttributeFieldPositions();
            return true;
        }

        if (isInsideMap(mouseX, mouseY) && delta != 0.0) {
            double localX = Mth.clamp((mouseX - mapX) / Math.max(1.0, mapW), 0.0, 1.0);
            double localZ = Mth.clamp((mouseY - mapY) / Math.max(1.0, mapH), 0.0, 1.0);
            double anchorWorldX = worldLeft + (localX * worldSpan);
            double anchorWorldZ = worldTop + (localZ * worldSpan);

            double factor = delta > 0.0 ? 0.85 : 1.2;
            double newRadius = Mth.clamp(zoomRadiusBlocks * factor, MIN_ZOOM_RADIUS_BLOCKS, MAX_ZOOM_RADIUS_BLOCKS);
            double newSpan = newRadius * 2.0;

            centerBlockX = anchorWorldX - (localX * newSpan) + newRadius;
            centerBlockZ = anchorWorldZ - (localZ * newSpan) + newRadius;
            zoomRadiusBlocks = newRadius;
            currentMapScale = scaleForZoom(zoomRadiusBlocks);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectorOpen) {
            updateSelectorGeometry();

            if (isInsideRect(mouseX, mouseY, selectorX, selectorY, selectorW, selectorH)) {
                if (selectorSearchEdit.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }

                if (button == 0) {
                    int listX = selectorX + 10;
                    int listY = selectorY + 36;
                    int listW = selectorW - 20;
                    int listH = selectorH - 46;
                    if (isInsideRect(mouseX, mouseY, listX, listY, listW, listH)) {
                        int clickedIndex = selectorScrollOffset + (int) ((mouseY - listY) / SELECTOR_ROW_HEIGHT);
                        if (clickedIndex >= 0 && clickedIndex < selectorFilteredEntries.size()) {
                            selectResourceEntry(selectorFilteredEntries.get(clickedIndex));
                        }
                        return true;
                    }
                }

                if (button == 1) {
                    int listX = selectorX + 10;
                    int listY = selectorY + 36;
                    int listW = selectorW - 20;
                    int listH = selectorH - 46;
                    if (isInsideRect(mouseX, mouseY, listX, listY, listW, listH)) {
                        int clickedIndex = selectorScrollOffset + (int) ((mouseY - listY) / SELECTOR_ROW_HEIGHT);
                        if (clickedIndex >= 0 && clickedIndex < selectorFilteredEntries.size()) {
                            toggleFavorite(selectorFilteredEntries.get(clickedIndex));
                        }
                        return true;
                    }
                }

                return true;
            }

            closeSelector();
            return true;
        }

        if (button == 0 && isInsideMap(mouseX, mouseY)) {
            panning = true;
            panStartMouseX = mouseX;
            panStartMouseY = mouseY;
            panStartCenterX = centerBlockX;
            panStartCenterZ = centerBlockZ;
            return true;
        }

        if (button == 2 && isInsideMap(mouseX, mouseY)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
                double localX = Mth.clamp((mouseX - mapX) / Math.max(1.0, mapW), 0.0, 1.0);
                double localZ = Mth.clamp((mouseY - mapY) / Math.max(1.0, mapH), 0.0, 1.0);
                double targetWorldX = worldLeft + (localX * worldSpan);
                double targetWorldZ = worldTop + (localZ * worldSpan);

                APChunkManagerMod.NETWORK.sendToServer(new CopyChunkMapSectionPacket(targetWorldX, targetWorldZ, currentMapScale));
                return true;
            }
        }

        if (button == 1 && isInsideMap(mouseX, mouseY)) {
            rightSelecting = true;
            ChunkPos chunk = chunkAtMouse(mouseX, mouseY);
            if (chunk != null) {
                int quota = getCurrentChunkQuota();
                rightDragEraseMode = selectedChunks.contains(packChunk(chunk.x, chunk.z));
                applyChunkSelection(chunk.x, chunk.z, quota, rightDragEraseMode);
                lastSelectedChunkX = chunk.x;
                lastSelectedChunkZ = chunk.z;
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning && button == 0) {
            centerBlockX = panStartCenterX - ((mouseX - panStartMouseX) * blocksPerPixel);
            centerBlockZ = panStartCenterZ - ((mouseY - panStartMouseY) * blocksPerPixel);
            return true;
        }

        if (rightSelecting && button == 1 && isInsideMap(mouseX, mouseY)) {
            selectChunkAtMouse(mouseX, mouseY, rightDragEraseMode);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && panning) {
            panning = false;
            return true;
        }
        if (button == 1 && rightSelecting) {
            rightSelecting = false;
            lastSelectedChunkX = Integer.MIN_VALUE;
            lastSelectedChunkZ = Integer.MIN_VALUE;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void selectChunkAtMouse(double mouseX, double mouseY, boolean erase) {
        ChunkPos chunk = chunkAtMouse(mouseX, mouseY);
        if (chunk == null) {
            return;
        }

        int quota = getCurrentChunkQuota();

        if (lastSelectedChunkX != Integer.MIN_VALUE) {
            applySelectionLine(lastSelectedChunkX, lastSelectedChunkZ, chunk.x, chunk.z, quota, erase);
        } else {
            applyChunkSelection(chunk.x, chunk.z, quota, erase);
        }

        lastSelectedChunkX = chunk.x;
        lastSelectedChunkZ = chunk.z;
    }

    private void applySelectionLine(int x0, int z0, int x1, int z1, int quota, boolean erase) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dz = -Math.abs(z1 - z0);
        int sz = z0 < z1 ? 1 : -1;
        int err = dx + dz;

        int x = x0;
        int z = z0;
        while (true) {
            if (!applyChunkSelection(x, z, quota, erase) && !erase) {
                return;
            }
            if (x == x1 && z == z1) {
                return;
            }
            int e2 = 2 * err;
            if (e2 >= dz) {
                err += dz;
                x += sx;
            }
            if (e2 <= dx) {
                err += dx;
                z += sz;
            }
        }
    }

    private boolean applyChunkSelection(int chunkX, int chunkZ, int quota, boolean erase) {
        long packed = packChunk(chunkX, chunkZ);
        if (erase) {
            selectedChunks.remove(packed);
            return true;
        }
        if (selectedChunks.contains(packed)) {
            return true;
        }
        if (quota <= 0 || selectedChunks.size() >= quota) {
            return false;
        }
        selectedChunks.add(packed);
        return true;
    }

    private ChunkPos chunkAtMouse(double mouseX, double mouseY) {
        if (!isInsideMap(mouseX, mouseY)) {
            return null;
        }

        GridProjection projection = buildGridProjection();
        double scaledCell = projection.gridCellSize * projection.zoom;
        if (scaledCell <= 0.0001) {
            return null;
        }

        int gridX = (int) Math.floor((mouseX - projection.mapStartX - projection.panX) / scaledCell);
        int gridZ = (int) Math.floor((mouseY - projection.mapStartY - projection.panY) / scaledCell);
        return new ChunkPos(projection.originChunkX + gridX, projection.originChunkZ + gridZ);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xEE14191F, 0xEE10151C);

        layoutPanels();
        updateMapControlButtonLabels();
        updateFixedWidgetPositions();
        updateAttributeFieldPositions();

        guiGraphics.fill(mapX - 1, mapY - 1, mapX + mapW + 1, mapY + mapH + 1, 0xFF2A3440);
        guiGraphics.fill(mapX, mapY, mapX + mapW, mapY + mapH, 0xFF0E1318);

        guiGraphics.fill(mapControlsX - 1, mapControlsY - 1, mapControlsX + mapControlsW + 1, mapControlsY + mapControlsH + 1, 0xFF2A3440);
        guiGraphics.fill(mapControlsX, mapControlsY, mapControlsX + mapControlsW, mapControlsY + mapControlsH, 0xCC0F1722);

        if (isAdminMode) {
            guiGraphics.fill(controlsX - 1, controlsY - 1, controlsX + controlsW + 1, controlsY + controlsH + 1, 0xFF2A3440);
            guiGraphics.fill(controlsX, controlsY, controlsX + controlsW, controlsY + controlsH, 0xCC0F1722);

            guiGraphics.fill(attributesX, attributesY, attributesX + attributesW, attributesY + attributesH, 0xAA0A1018);
            guiGraphics.fill(attributesX, attributesY, attributesX + attributesW, attributesY + 1, 0xFF3A5168);
            guiGraphics.fill(attributesX, attributesY + attributesH - 1, attributesX + attributesW, attributesY + attributesH, 0xFF3A5168);

            guiGraphics.drawString(this.font, TITLE, controlsX + PANEL_PADDING, controlsY + 14, 0xFFE5F0FF, false);
            guiGraphics.drawString(this.font, Component.literal("Attributes"), attributesX + PANEL_PADDING, attributesY + 4, 0xFF9FC2E5, false);
        } else {
            guiGraphics.drawString(this.font, Component.literal("Chunk Manager"), mapX + PANEL_PADDING, mapY + 8, 0xFFE5F0FF, false);
            guiGraphics.drawString(this.font, Component.literal("Chunk Credit: " + playerChunkCredit), mapX + PANEL_PADDING, mapY + 22, 0xFFBEE29E, false);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            if (!centerInitialized) {
                centerBlockX = mc.player.getX();
                centerBlockZ = mc.player.getZ();
                centerInitialized = true;
            }

            currentMapScale = scaleForZoom(zoomRadiusBlocks);
            worldSpan = zoomRadiusBlocks * 2.0;
            worldLeft = centerBlockX - zoomRadiusBlocks;
            worldTop = centerBlockZ - zoomRadiusBlocks;
            blocksPerPixel = worldSpan / Math.max(1.0, mapW);
            pixelsPerBlock = mapW / Math.max(1.0, worldSpan);

            guiGraphics.enableScissor(mapX, mapY, mapX + mapW, mapY + mapH);
            renderMapTiles(guiGraphics, mc);
            PoseStack selectionPose = guiGraphics.pose();
            selectionPose.pushPose();
            selectionPose.translate(0.0f, 0.0f, 140.0f);
            renderChunkSelectionOverlay(guiGraphics);
            selectionPose.popPose();

            PoseStack gridPose = guiGraphics.pose();
            gridPose.pushPose();
            gridPose.translate(0.0f, 0.0f, 150.0f);
            if (ChunkManagerClientState.isGridEnabled() && zoomRadiusBlocks <= 400.0) {
                renderChunkGridOverlay(guiGraphics);
            }
            gridPose.popPose();
            guiGraphics.disableScissor();

            int infoX = mapControlsX + PANEL_PADDING;
            int infoY = mapControlsY + 8;
            int innerW = Math.max(80, mapControlsW - (PANEL_PADDING * 2));
            int colGap = 6;
            int colW = (innerW - colGap) / 2;

            int centerVirtualId = ChunkManagerRuntimeEvents.calculateVirtualMapId(centerBlockX, centerBlockZ, currentMapScale);
            String mapIdText = "Map ID: " + centerVirtualId;
            String centerText = "Center: " + Mth.floor(centerBlockX) + ", " + Mth.floor(centerBlockZ);
            String zoomText = "Zoom: " + Mth.floor(zoomRadiusBlocks) + " blocks";
            String hintText = "Pan: drag | Zoom: wheel";

            int rightTextWidth = Math.max(this.font.width(centerText), this.font.width(hintText));
            boolean twoColumns = colW >= (rightTextWidth + 4);
            if (twoColumns) {
                guiGraphics.drawString(this.font, Component.literal(mapIdText), infoX, infoY, 0xFFD7E3F4, false);
                guiGraphics.drawString(this.font, Component.literal(centerText), infoX + colW + colGap, infoY, 0xFFD7E3F4, false);
                guiGraphics.drawString(this.font, Component.literal(zoomText), infoX, infoY + 14, 0xFFD7E3F4, false);
                guiGraphics.drawString(this.font, Component.literal(hintText), infoX + colW + colGap, infoY + 14, 0xFF9FB0C6, false);
            } else {
                guiGraphics.drawString(this.font, Component.literal(mapIdText), infoX, infoY, 0xFFD7E3F4, false);
                guiGraphics.drawString(this.font, Component.literal(centerText), infoX, infoY + 14, 0xFFD7E3F4, false);
                guiGraphics.drawString(this.font, Component.literal(zoomText), infoX, infoY + 28, 0xFFD7E3F4, false);
                guiGraphics.drawString(this.font, Component.literal(hintText), infoX, infoY + 42, 0xFF9FB0C6, false);
            }

            int settingsPanelY = mapControlsY + MAP_INFO_PANEL_H + 2;
            int settingsViewportY = mapControlsY + MAP_INFO_PANEL_H + 6;
            int settingsViewportH = Math.max(24, mapControlsH - MAP_INFO_PANEL_H - 10);
            int settingsViewportX = mapControlsX + PANEL_PADDING;
            int settingsViewportW = mapControlsW - (PANEL_PADDING * 2) - 7;
            guiGraphics.fill(mapControlsX, settingsPanelY, mapControlsX + mapControlsW, mapControlsY + mapControlsH, 0xCC0C1622);
            guiGraphics.fill(mapControlsX + 1, settingsViewportY, mapControlsX + mapControlsW - 7, settingsViewportY + settingsViewportH, 0xAA08111B);
            guiGraphics.drawString(this.font, Component.literal("Map Settings"), settingsViewportX, settingsViewportY + 2, 0xFFB7CCE5, false);
            guiGraphics.drawString(this.font, Component.literal("(scroll)"), settingsViewportX + settingsViewportW - 44, settingsViewportY + 2, 0xFF6E87A1, false);

            int trackX0 = mapControlsX + mapControlsW - 6;
            int trackX1 = mapControlsX + mapControlsW - 3;
            int trackY0 = settingsViewportY;
            int trackY1 = settingsViewportY + settingsViewportH;
            guiGraphics.fill(trackX0, trackY0, trackX1, trackY1, 0x88324A60);

            int maxScroll = Math.max(1, mapSettingsContentHeight - settingsViewportH);
            int thumbH = Math.max(14, (int) ((settingsViewportH / (double) Math.max(settingsViewportH, mapSettingsContentHeight)) * settingsViewportH));
            int thumbTravel = Math.max(1, settingsViewportH - thumbH);
            int thumbY = trackY0 + (int) ((mapSettingsScrollOffset / (double) maxScroll) * thumbTravel);
            guiGraphics.fill(trackX0, thumbY, trackX1, thumbY + thumbH, 0xFF9EC5EA);
        } else {
            guiGraphics.drawString(this.font, Component.literal("Loading world..."), mapX + 8, mapY + 8, 0xFFFF8484, false);
        }

        if (isAdminMode) {
            renderAttributeLabels(guiGraphics);
        }
        renderSelectionInfo(guiGraphics);

        List<Component> hoverTooltip = null;
        if (!selectorOpen && isInsideMap(mouseX, mouseY) && mc.player != null && mc.level != null) {
            ChunkPos hoveredChunk = chunkAtMouse(mouseX, mouseY);
            if (hoveredChunk != null) {
                ChunkManagerRuntimeEvents.requestChunkClaimInfo(mc, hoveredChunk.x, hoveredChunk.z);
                ChunkManagerRuntimeEvents.ChunkClaimInfo info = ChunkManagerRuntimeEvents.getCachedChunkClaimInfo(hoveredChunk.x, hoveredChunk.z);
                if (info != null) {
                    hoverTooltip = new ArrayList<>();
                    hoverTooltip.add(Component.literal(info.chunkName().isEmpty() ? "Unnamed Chunk" : info.chunkName()).withStyle(ChatFormatting.GOLD));
                    hoverTooltip.add(Component.literal("Team: " + (info.teamName().isEmpty() ? "None" : info.teamName())));
                    hoverTooltip.add(Component.literal("Role: " + (info.roleName().isEmpty() ? "None" : info.roleName())));
                    hoverTooltip.add(Component.literal("Owner/Player: " + (info.ownerName().isEmpty() ? "Unknown" : info.ownerName())));
                }
            }
        }

        if (selectorOpen) {
            renderSelectorOverlay(guiGraphics, mouseX, mouseY, partialTick);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (hoverTooltip != null && !hoverTooltip.isEmpty()) {
            guiGraphics.renderComponentTooltip(this.font, hoverTooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (selectorOpen) {
            if (keyCode == 256) {
                closeSelector();
                return true;
            }
            if (keyCode == 257 || keyCode == 335) {
                if (!selectorFilteredEntries.isEmpty()) {
                    selectResourceEntry(selectorFilteredEntries.get(0));
                }
                return true;
            }
            if (selectorSearchEdit.keyPressed(keyCode, scanCode, modifiers)) {
                refreshSelectorFilteredEntries();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (selectorOpen) {
            if (selectorSearchEdit.charTyped(codePoint, modifiers)) {
                refreshSelectorFilteredEntries();
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void openResourceSelector(EditBox targetEdit, String title) {
        selectorOpen = true;
        selectorTargetEdit = targetEdit;
        selectorTitle = title;
        selectorScrollOffset = 0;
        selectorSearchEdit.setValue("");
        selectorSearchEdit.setFocused(true);
        refreshSelectorFilteredEntries();
    }

    private void closeSelector() {
        selectorOpen = false;
        selectorTargetEdit = null;
        selectorSearchEdit.setFocused(false);
    }

    private void selectResourceEntry(String value) {
        if (selectorTargetEdit != null) {
            selectorTargetEdit.setValue(value);
        }
        markResourceRecent(value);
        closeSelector();
    }

    private void refreshSelectorFilteredEntries() {
        String needle = selectorSearchEdit.getValue().trim().toLowerCase();
        selectorFilteredEntries = registryEntries.stream()
                .map(entry -> new RankedResource(entry, fuzzyScore(entry, needle)))
                .filter(rank -> needle.isEmpty() || rank.score() >= 0)
                .sorted(Comparator
                        .comparingInt((RankedResource rank) -> categoryPriority(rank.resource()))
                        .thenComparingInt((RankedResource rank) -> -rank.score())
                        .thenComparing(RankedResource::resource))
                .limit(MAX_SELECTOR_RESULTS)
                .map(RankedResource::resource)
                .toList();
        selectorScrollOffset = 0;
    }

    private static int fuzzyScore(String entry, String needle) {
        if (needle == null || needle.isBlank()) {
            return 1;
        }

        String candidate = entry.toLowerCase();
        String query = needle.toLowerCase();
        int containsIndex = candidate.indexOf(query);
        if (containsIndex >= 0) {
            return 1000 - containsIndex;
        }

        String[] tokens = query.split("[\\s:_/.-]+");
        int tokenScore = 0;
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int idx = candidate.indexOf(token);
            if (idx < 0) {
                return -1;
            }
            tokenScore += 120 - Math.min(100, idx);
        }

        int seqScore = 0;
        int ci = 0;
        for (int qi = 0; qi < query.length(); qi++) {
            char q = query.charAt(qi);
            int found = candidate.indexOf(q, ci);
            if (found < 0) {
                return tokenScore > 0 ? tokenScore : -1;
            }
            seqScore += Math.max(1, 40 - (found - ci));
            ci = found + 1;
        }

        return Math.max(1, tokenScore + seqScore);
    }

    private static int categoryPriority(String resource) {
        if (FAVORITE_RESOURCES.contains(resource)) {
            return 0;
        }
        int recentIndex = RECENT_RESOURCES.indexOf(resource);
        if (recentIndex >= 0) {
            return 1 + recentIndex;
        }
        return 1000;
    }

    private static void markResourceRecent(String resource) {
        if (resource == null || resource.isBlank()) {
            return;
        }

        RECENT_RESOURCES.remove(resource);
        RECENT_RESOURCES.add(0, resource);
        while (RECENT_RESOURCES.size() > 20) {
            RECENT_RESOURCES.remove(RECENT_RESOURCES.size() - 1);
        }
    }

    private void toggleFavorite(String resource) {
        if (FAVORITE_RESOURCES.contains(resource)) {
            FAVORITE_RESOURCES.remove(resource);
        } else {
            FAVORITE_RESOURCES.add(resource);
        }
        refreshSelectorFilteredEntries();
    }

    private void updateSelectorGeometry() {
        selectorW = Math.min(520, this.width - 40);
        selectorH = Math.min(320, this.height - 40);
        selectorX = (this.width - selectorW) / 2;
        selectorY = (this.height - selectorH) / 2;

        selectorSearchEdit.setX(selectorX + 10);
        selectorSearchEdit.setY(selectorY + 12);
        selectorSearchEdit.setWidth(selectorW - 20);
        selectorSearchEdit.setHeight(20);
    }

    private void renderSelectorOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateSelectorGeometry();

        guiGraphics.fill(0, 0, this.width, this.height, 0xAA000000);
        guiGraphics.fill(selectorX - 1, selectorY - 1, selectorX + selectorW + 1, selectorY + selectorH + 1, 0xFF5C7EA3);
        guiGraphics.fill(selectorX, selectorY, selectorX + selectorW, selectorY + selectorH, 0xFF111A24);

        guiGraphics.drawString(this.font, Component.literal(selectorTitle), selectorX + 10, selectorY + 4, 0xFFE4F2FF, false);
        selectorSearchEdit.render(guiGraphics, mouseX, mouseY, partialTick);

        int listX = selectorX + 10;
        int listY = selectorY + 36;
        int listW = selectorW - 20;
        int listH = selectorH - 46;
        guiGraphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0A121B);

        int visibleRows = Math.max(1, listH / SELECTOR_ROW_HEIGHT);
        int maxScroll = Math.max(0, selectorFilteredEntries.size() - visibleRows);
        selectorScrollOffset = Mth.clamp(selectorScrollOffset, 0, maxScroll);

        for (int row = 0; row < visibleRows; row++) {
            int entryIndex = selectorScrollOffset + row;
            if (entryIndex >= selectorFilteredEntries.size()) {
                break;
            }

            int rowY = listY + (row * SELECTOR_ROW_HEIGHT);
            boolean hovered = isInsideRect(mouseX, mouseY, listX, rowY, listW, SELECTOR_ROW_HEIGHT);
            int bg = hovered ? 0xFF1E3348 : ((row & 1) == 0 ? 0xFF0E1822 : 0xFF0C151E);
            guiGraphics.fill(listX, rowY, listX + listW, rowY + SELECTOR_ROW_HEIGHT, bg);
            String resource = selectorFilteredEntries.get(entryIndex);
            String marker = FAVORITE_RESOURCES.contains(resource) ? "* " : (RECENT_RESOURCES.contains(resource) ? "R " : "  ");
            guiGraphics.drawString(this.font, marker + resource, listX + 4, rowY + 4, 0xFFD9E9F8, false);
        }

        if (selectorFilteredEntries.isEmpty()) {
            guiGraphics.drawString(this.font, Component.literal("No results"), listX + 6, listY + 6, 0xFFFFA0A0, false);
        }
    }

    private static boolean isInsideRect(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < (x + w) && mouseY >= y && mouseY < (y + h);
    }

    private record RankedResource(String resource, int score) {
    }

    private static final class ColorSlider extends AbstractSliderButton {
        private final String label;
        private int intValue;

        private ColorSlider(int x, int y, int width, int height, String label, int initialValue) {
            super(x, y, width, height, Component.empty(), Mth.clamp(initialValue, 0, 255) / 255.0);
            this.label = label;
            this.intValue = Mth.clamp(initialValue, 0, 255);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(label + ": " + intValue));
        }

        @Override
        protected void applyValue() {
            intValue = Mth.clamp((int) Math.round(value * 255.0), 0, 255);
            updateMessage();
        }

        int getIntValue() {
            return intValue;
        }
    }

    private void renderSelectionInfo(GuiGraphics guiGraphics) {
        int quota = getCurrentChunkQuota();
        int color = selectedChunks.size() <= quota ? 0xFFA7E2A8 : 0xFFFF8D8D;
        int textX = isAdminMode ? controlsX + PANEL_PADDING : mapControlsX + PANEL_PADDING;
        int textY = isAdminMode ? controlsY + controlsH - 18 : mapControlsY + mapControlsH - 18;
        guiGraphics.drawString(
                this.font,
                Component.literal("Selected Chunks: " + selectedChunks.size() + " / " + quota),
            textX,
            textY,
                color,
                false
        );

            if (ChunkManagerClientState.hasChunkCreateFeedback()) {
                int feedbackY = textY - 14;
                guiGraphics.drawString(
                    this.font,
                    Component.literal(ChunkManagerClientState.getChunkCreateFeedbackMessage()),
                    textX,
                    feedbackY,
                    ChunkManagerClientState.getChunkCreateFeedbackColor(),
                    false
                );
            }
    }

    private void renderAttributeLabels(GuiGraphics guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        beginScissor(mc, attributesX + 1, attributesY + 1, attributesW - 2, attributesH - 2);
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0.0f, 0.0f, 210.0f);
        try {
            int viewportTop = attributesY + PANEL_PADDING;
            int viewportBottom = attributesY + attributesH - PANEL_PADDING;
            for (AttributeField field : attributeFields) {
                if (field.label.getString().isEmpty()) {
                    continue;
                }
                int y = viewportTop + field.contentY - attributesScrollOffset;
                if (y + ATTR_LABEL_HEIGHT < viewportTop || y > viewportBottom) {
                    continue;
                }
                guiGraphics.drawString(this.font, field.label, attributesX + PANEL_PADDING, y, 0xFFE0EBFA, false);
            }

            int previewLabelY = viewportTop + colorPreviewContentY - attributesScrollOffset;
            int previewRectY = previewLabelY + ATTR_LABEL_HEIGHT;
            int previewRectX = attributesX + PANEL_PADDING;
            int previewRectW = Math.max(40, attributesW - (PANEL_PADDING * 2) - 8);
            int previewRectH = 20;
            if (previewRectY > viewportTop - previewRectH && previewRectY < viewportBottom + previewRectH) {
                int color = getSelectedChunkColorRgb();
                guiGraphics.fill(previewRectX, previewRectY, previewRectX + previewRectW, previewRectY + previewRectH, 0xFF000000 | color);
                guiGraphics.fill(previewRectX, previewRectY, previewRectX + previewRectW, previewRectY + 1, 0xFFB7C5D6);
                guiGraphics.fill(previewRectX, previewRectY + previewRectH - 1, previewRectX + previewRectW, previewRectY + previewRectH, 0xFFB7C5D6);
                guiGraphics.fill(previewRectX, previewRectY, previewRectX + 1, previewRectY + previewRectH, 0xFFB7C5D6);
                guiGraphics.fill(previewRectX + previewRectW - 1, previewRectY, previewRectX + previewRectW, previewRectY + previewRectH, 0xFFB7C5D6);
                guiGraphics.drawString(this.font, Component.literal(String.format("#%06X", color & 0xFFFFFF)), previewRectX + 6, previewRectY + 6, 0xFFEAF2FF,
                        false);
            }
        } finally {
            pose.popPose();
            RenderSystem.disableScissor();
        }

        renderScrollBar(guiGraphics);
    }

    private void renderScrollBar(GuiGraphics guiGraphics) {
        int trackX0 = attributesX + attributesW - 6;
        int trackX1 = attributesX + attributesW - 3;
        int trackY0 = attributesY + PANEL_PADDING;
        int trackY1 = attributesY + attributesH - PANEL_PADDING;

        guiGraphics.fill(trackX0, trackY0, trackX1, trackY1, 0x88324A60);

        int maxScroll = Math.max(1, attributesContentHeight - attributesH);
        int viewport = Math.max(1, attributesH - (PANEL_PADDING * 2));
        int thumbH = Math.max(18, (int) ((viewport / (double) Math.max(viewport, attributesContentHeight)) * viewport));
        int thumbTravel = Math.max(1, viewport - thumbH);
        int thumbY = trackY0 + (int) ((attributesScrollOffset / (double) maxScroll) * thumbTravel);

        guiGraphics.fill(trackX0, thumbY, trackX1, thumbY + thumbH, 0xFF9EC5EA);
    }

    private void renderMapTiles(GuiGraphics guiGraphics, Minecraft mc) {
        int blockSize = 128 * (1 << currentMapScale);
        int minGridX = Math.floorDiv(Mth.floor(worldLeft), blockSize) - 1;
        int minGridZ = Math.floorDiv(Mth.floor(worldTop), blockSize) - 1;
        int maxGridX = Math.floorDiv(Mth.ceil(worldLeft + worldSpan), blockSize) + 1;
        int maxGridZ = Math.floorDiv(Mth.ceil(worldTop + worldSpan), blockSize) + 1;

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int rendered = 0;
        for (int gridZ = minGridZ; gridZ <= maxGridZ; gridZ++) {
            for (int gridX = minGridX; gridX <= maxGridX; gridX++) {
                if (rendered >= MAX_RENDER_TILES) {
                    break;
                }

                int tileMinX = gridX * blockSize;
                int tileMinZ = gridZ * blockSize;
                double tileCenterX = tileMinX + (blockSize / 2.0);
                double tileCenterZ = tileMinZ + (blockSize / 2.0);
                int mapId = ChunkManagerRuntimeEvents.calculateVirtualMapId(tileCenterX, tileCenterZ, currentMapScale);
                MapItemSavedData mapData = ChunkManagerRuntimeEvents.getVirtualMapData(mc, tileCenterX, tileCenterZ, currentMapScale, mapId);

                double drawLeft = mapX + ((tileMinX - worldLeft) * pixelsPerBlock);
                double drawTop = mapY + ((tileMinZ - worldTop) * pixelsPerBlock);
                double drawRight = mapX + (((tileMinX + blockSize) - worldLeft) * pixelsPerBlock);
                double drawBottom = mapY + (((tileMinZ + blockSize) - worldTop) * pixelsPerBlock);

                int drawX = Mth.floor(drawLeft);
                int drawY = Mth.floor(drawTop);
                int drawW = Mth.ceil(drawRight) - drawX;
                int drawH = Mth.ceil(drawBottom) - drawY;
                if (drawW <= 0 || drawH <= 0) {
                    continue;
                }

                boolean missingData = mapData == null || !ChunkManagerRuntimeEvents.hasRenderableData(mapData) || mapId == -1;
                if (missingData) {
                    ChunkManagerRuntimeEvents.requestVirtualTileData(mc, tileCenterX, tileCenterZ, currentMapScale, mapId);
                    guiGraphics.fill(drawX, drawY, drawX + drawW, drawY + drawH, 0xFF111820);
                    guiGraphics.fill(drawX + 1, drawY + 1, drawX + drawW - 1, drawY + drawH - 1, 0xFF1A2531);
                    rendered++;
                    continue;
                }

                ChunkManagerRuntimeEvents.updateMapColorsClientSide(mc, mapData);
                mc.gameRenderer.getMapRenderer().update(mapId, mapData);

                PoseStack pose = guiGraphics.pose();
                pose.pushPose();
                pose.translate(drawX, drawY, 120.0f);
                pose.scale(drawW / 128.0f, drawH / 128.0f, 1.0f);
                mc.gameRenderer.getMapRenderer().render(pose, buffers, mapId, mapData, true, 15728880);
                pose.popPose();
                rendered++;
            }
            if (rendered >= MAX_RENDER_TILES) {
                break;
            }
        }
        buffers.endBatch();
    }

    private void renderChunkSelectionOverlay(GuiGraphics guiGraphics) {
        GridProjection projection = buildGridProjection();
        double scaledCell = projection.gridCellSize * projection.zoom;
        if (scaledCell <= 0.0001) {
            return;
        }

        int chunkColor = getSelectedChunkColorRgb();
        int fillColor = 0x66000000 | chunkColor;
        int borderColor = 0xCC000000 | chunkColor;

        for (long packed : selectedChunks) {
            int chunkX = unpackChunkX(packed);
            int chunkZ = unpackChunkZ(packed);

            double x0d = projection.mapStartX + projection.panX + ((chunkX - projection.originChunkX) * scaledCell);
            double y0d = projection.mapStartY + projection.panY + ((chunkZ - projection.originChunkZ) * scaledCell);
            int x0 = Mth.floor(x0d);
            int y0 = Mth.floor(y0d);
            int x1 = Mth.ceil(x0d + scaledCell);
            int y1 = Mth.ceil(y0d + scaledCell);

            if (x1 < mapX || x0 > mapX + mapW || y1 < mapY || y0 > mapY + mapH) {
                continue;
            }

            guiGraphics.fill(x0, y0, x1, y1, fillColor);
            guiGraphics.fill(x0, y0, x1, y0 + 1, borderColor);
            guiGraphics.fill(x0, y1 - 1, x1, y1, borderColor);
            guiGraphics.fill(x0, y0, x0 + 1, y1, borderColor);
            guiGraphics.fill(x1 - 1, y0, x1, y1, borderColor);
        }
    }

    private void renderChunkGridOverlay(GuiGraphics guiGraphics) {
        GridProjection projection = buildGridProjection();
        double scaledCell = projection.gridCellSize * projection.zoom;
        if (scaledCell <= 0.0001) {
            return;
        }

        int majorColor = 0x78E6F0FF;
        int minorColor = 0x46FFFFFF;
        int minChunkX = Math.floorDiv(Mth.floor(worldLeft), 16) - 1;
        int minChunkZ = Math.floorDiv(Mth.floor(worldTop), 16) - 1;
        int maxChunkX = Math.floorDiv(Mth.ceil(worldLeft + worldSpan), 16) + 1;
        int maxChunkZ = Math.floorDiv(Mth.ceil(worldTop + worldSpan), 16) + 1;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            double x = projection.mapStartX + projection.panX + ((chunkX - projection.originChunkX) * scaledCell);
            int sx = Mth.floor(x);
            boolean major = Math.floorMod(chunkX, 4) == 0;
            guiGraphics.fill(sx, mapY, sx + (major ? 2 : 1), mapY + mapH, major ? majorColor : minorColor);
        }

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            double y = projection.mapStartY + projection.panY + ((chunkZ - projection.originChunkZ) * scaledCell);
            int sy = Mth.floor(y);
            boolean major = Math.floorMod(chunkZ, 4) == 0;
            guiGraphics.fill(mapX, sy, mapX + mapW, sy + (major ? 2 : 1), major ? majorColor : minorColor);
        }
    }

    private int worldToScreenX(double worldX) {
        return mapX + Mth.floor((worldX - worldLeft) * pixelsPerBlock);
    }

    private int worldToScreenY(double worldZ) {
        return mapY + Mth.floor((worldZ - worldTop) * pixelsPerBlock);
    }

    private GridProjection buildGridProjection() {
        int originChunkX = Math.floorDiv(Mth.floor(worldLeft), 16);
        int originChunkZ = Math.floorDiv(Mth.floor(worldTop), 16);
        int originBlockX = originChunkX << 4;
        int originBlockZ = originChunkZ << 4;

        double mapStartX = mapX;
        double mapStartY = mapY;
        double zoom = pixelsPerBlock;
        double gridCellSize = 16.0;
        double panX = (originBlockX - worldLeft) * pixelsPerBlock;
        double panY = (originBlockZ - worldTop) * pixelsPerBlock;
        return new GridProjection(mapStartX, mapStartY, panX, panY, gridCellSize, zoom, originChunkX, originChunkZ);
    }

    private int getCurrentChunkQuota() {
        if (isAdminMode) {
            return Math.max(0, parseInteger(initialChunkQuotaEdit != null ? initialChunkQuotaEdit.getValue() : "0", 0));
        }
        return Math.max(0, playerChunkCredit);
    }

    public void setPlayerChunkCredit(int playerChunkCredit) {
        this.playerChunkCredit = Math.max(0, playerChunkCredit);
    }

    private boolean isInsideMap(double mouseX, double mouseY) {
        return mouseX >= mapX && mouseX <= (mapX + mapW) && mouseY >= mapY && mouseY <= (mapY + mapH);
    }

    private boolean isInsideMapSettings(double mouseX, double mouseY) {
        int y0 = mapControlsY + MAP_INFO_PANEL_H + 6;
        int h = Math.max(24, mapControlsH - MAP_INFO_PANEL_H - 10);
        return mouseX >= mapControlsX + PANEL_PADDING && mouseX <= (mapControlsX + mapControlsW - PANEL_PADDING)
                && mouseY >= y0 && mouseY <= (y0 + h);
    }

    private boolean isInsideAttributes(double mouseX, double mouseY) {
        if (!isAdminMode) {
            return false;
        }
        return mouseX >= attributesX && mouseX <= (attributesX + attributesW) && mouseY >= attributesY && mouseY <= (attributesY + attributesH);
    }

    private void submitCreateChunkRule() {
        if (!isAdminMode || minecraft == null || minecraft.player == null || nameEdit == null || teamRequirementButton == null
                || buildHeightAboveFaceEdit == null || buildDepthBelowFaceEdit == null || initialChunkQuotaEdit == null || chunkRewardResourceEdit == null
                || chunkRewardAmountEdit == null || chunkCostResourceEdit == null || chunkCostAmountEdit == null || allowBuildCheckbox == null) {
            return;
        }

        List<ChunkPos> chunkPositions = selectedChunks.stream()
                .map(packed -> new ChunkPos(unpackChunkX(packed), unpackChunkZ(packed)))
                .toList();

        int buildHeightAboveFace = Mth.clamp(parseInteger(buildHeightAboveFaceEdit.getValue(), 64), 0, 384);
        int buildDepthBelowFace = Mth.clamp(parseInteger(buildDepthBelowFaceEdit.getValue(), 8), 0, 384);

        boolean assignRoleToChunk = !selectedRoleName.isBlank();
        CreateChunkRulePacket packet = new CreateChunkRulePacket(
                nameEdit.getValue().trim(),
            assignRoleToChunk,
            selectedRoleName,
            selectedRoleColor,
                teamRequirement == TeamRequirement.REQUIRE_TEAM,
                buildHeightAboveFace,
                buildDepthBelowFace,
                Mth.clamp(parseInteger(initialChunkQuotaEdit.getValue(), 0), 0, 4096),
                chunkRewardResourceEdit.getValue().trim(),
                Mth.clamp(parseInteger(chunkRewardAmountEdit.getValue(), 0), 0, 999999),
                chunkCostResourceEdit.getValue().trim(),
                Mth.clamp(parseInteger(chunkCostAmountEdit.getValue(), 0), 0, 999999),
                allowBuildCheckbox.selected(),
                getSelectedChunkColorRgb(),
                chunkPositions
        );

        APChunkManagerMod.NETWORK.sendToServer(packet);
        ChunkManagerClientState.setChunkCreateFeedback("Creating chunks...", 0xFF9FC2E5, 2200L);
    }

    private void openTeamManager() {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new TeamManagerScreen(this));
    }

    private void openChunkRolePicker() {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new ChunkRolePickerScreen(this, selectedRoleName, selectedRoleColor));
    }

    public void setSelectedChunkRole(String roleName, int roleColorRgb) {
        selectedRoleName = roleName == null ? "" : roleName.trim();
        selectedRoleColor = roleColorRgb & 0xFFFFFF;
        updateRolePickerButtonLabel();
    }

    private void updateRolePickerButtonLabel() {
        if (rolePickerButton == null) {
            return;
        }

        if (selectedRoleName.isBlank()) {
            rolePickerButton.setMessage(Component.literal("Role: None (Select/Create)"));
        } else {
            rolePickerButton.setMessage(Component.literal("Role: " + selectedRoleName + "  #" + String.format("%06X", selectedRoleColor)));
        }
    }

    private int getSelectedChunkColorRgb() {
        int r = redSlider != null ? redSlider.getIntValue() : 51;
        int g = greenSlider != null ? greenSlider.getIntValue() : 204;
        int b = blueSlider != null ? blueSlider.getIntValue() : 102;
        return ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private void beginScissor(Minecraft mc, int x, int y, int w, int h) {
        Window window = mc.getWindow();
        double guiScale = window.getGuiScale();
        int scissorX = (int) Math.floor(x * guiScale);
        int scissorY = (int) Math.floor(window.getHeight() - ((y + h) * guiScale));
        int scissorW = Math.max(1, (int) Math.ceil(w * guiScale));
        int scissorH = Math.max(1, (int) Math.ceil(h * guiScale));
        RenderSystem.enableScissor(scissorX, scissorY, scissorW, scissorH);
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

    private static long packChunk(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int unpackChunkX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackChunkZ(long packed) {
        return (int) packed;
    }

    private record GridProjection(double mapStartX, double mapStartY, double panX, double panY, double gridCellSize, double zoom,
            int originChunkX, int originChunkZ) {
    }

    private enum TeamRequirement {
        REQUIRE_TEAM(Component.literal("Require Team")),
        STANDALONE(Component.literal("Standalone Chunk"));

        private final Component label;

        TeamRequirement(Component label) {
            this.label = label;
        }

        public Component label() {
            return label;
        }
    }

    private record AttributeField(Component label, GuiEventListener listener, int contentY) {
    }
}
