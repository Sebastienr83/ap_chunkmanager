package net.mcreator.ap_chunkmanager.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ChunkRolePickerScreen extends Screen {
    private static final int ROW_HEIGHT = 16;

    private final ChunkManagerAdminScreen parent;

    private EditBox searchEdit;
    private EditBox newRoleNameEdit;
    private EditBox redEdit;
    private EditBox greenEdit;
    private EditBox blueEdit;

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int createX;
    private int createY;
    private int createW;

    private int listScrollOffset;
    private List<String> filteredRoles = List.of();

    public ChunkRolePickerScreen(ChunkManagerAdminScreen parent, String currentRoleName, int currentRoleColor) {
        super(Component.literal("Select Chunk Role"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        panelW = Math.min(860, this.width - 56);
        panelH = Math.min(420, this.height - 56);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        int contentY = panelY + 24;
        int contentH = panelH - 74;
        int colGap = 14;
        listW = Math.max(220, (int) (panelW * 0.58));
        createW = panelW - listW - colGap - 24;
        listX = panelX + 12;
        createX = listX + listW + colGap;

        listY = contentY + 26;
        listH = Math.max(90, contentH - 26);
        createY = contentY;

        searchEdit = addRenderableWidget(new EditBox(this.font, listX, contentY, listW, 20, Component.literal("Search role")));
        searchEdit.setHint(Component.literal("Search role"));
        searchEdit.setResponder(value -> refreshFilter());

        newRoleNameEdit = addRenderableWidget(new EditBox(this.font, createX, createY + 22, createW, 20, Component.literal("New role")));
        newRoleNameEdit.setHint(Component.literal("New role name"));

        int rgbY = createY + 58;
        int rgbW = (createW - 8) / 3;
        redEdit = addRenderableWidget(new EditBox(this.font, createX, rgbY, rgbW, 20, Component.literal("R")));
        greenEdit = addRenderableWidget(new EditBox(this.font, createX + rgbW + 4, rgbY, rgbW, 20, Component.literal("G")));
        blueEdit = addRenderableWidget(new EditBox(this.font, createX + ((rgbW + 4) * 2), rgbY, rgbW, 20, Component.literal("B")));
        redEdit.setFilter(v -> v.matches("\\d{0,3}"));
        greenEdit.setFilter(v -> v.matches("\\d{0,3}"));
        blueEdit.setFilter(v -> v.matches("\\d{0,3}"));
        redEdit.setValue("255");
        greenEdit.setValue("255");
        blueEdit.setValue("255");

        addRenderableWidget(Button.builder(Component.literal("Create + Select"), b -> createAndSelect())
            .bounds(createX, createY + 90, createW, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("None"), b -> {
            parent.setSelectedChunkRole("", 0xFFFFFF);
            onClose();
        }).bounds(panelX + 12, panelY + panelH - 32, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(panelX + panelW - 72, panelY + panelH - 32, 60, 20)
                .build());

        refreshFilter();
    }

    private void refreshFilter() {
        String needle = searchEdit.getValue().trim().toLowerCase();
        List<String> roles = new ArrayList<>(TeamManagerClientState.getRoleCatalog());
        roles.sort(Comparator.comparing(ChunkRolePickerScreen::extractRoleName, String.CASE_INSENSITIVE_ORDER));

        if (needle.isEmpty()) {
            filteredRoles = roles;
            return;
        }

        filteredRoles = roles.stream()
                .filter(r -> extractRoleName(r).toLowerCase().contains(needle))
                .toList();
        listScrollOffset = 0;
    }

    private static String extractRoleName(String token) {
        int idx = token.indexOf('#');
        return idx >= 0 ? token.substring(0, idx) : token;
    }

    private static int extractRoleColor(String token) {
        int idx = token.indexOf('#');
        if (idx < 0 || idx + 1 >= token.length()) {
            return 0xFFFFFF;
        }
        try {
            return Integer.parseInt(token.substring(idx + 1), 16) & 0xFFFFFF;
        } catch (Exception ignored) {
            return 0xFFFFFF;
        }
    }

    private int parseRgb(EditBox box) {
        try {
            return Mth.clamp(Integer.parseInt(box.getValue().trim()), 0, 255);
        } catch (Exception ignored) {
            return 255;
        }
    }

    private void createAndSelect() {
        String role = newRoleNameEdit.getValue().trim();
        if (role.isEmpty()) {
            return;
        }
        int color = (parseRgb(redEdit) << 16) | (parseRgb(greenEdit) << 8) | parseRgb(blueEdit);
        parent.setSelectedChunkRole(role, color);
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int visibleRows = Math.max(1, listH / ROW_HEIGHT);
            int maxScroll = Math.max(0, filteredRoles.size() - visibleRows);
            listScrollOffset = Mth.clamp(listScrollOffset - (int) Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
                int index = listScrollOffset + (int) ((mouseY - listY) / ROW_HEIGHT);
                if (index >= 0 && index < filteredRoles.size()) {
                    String token = filteredRoles.get(index);
                    parent.setSelectedChunkRole(extractRoleName(token), extractRoleColor(token));
                    onClose();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);
        guiGraphics.fill(0, 0, this.width, this.height, 0xAA000000);
        guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF5C7EA3);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF111A24);

        guiGraphics.drawString(this.font, Component.literal("Select Chunk Role"), panelX + 12, panelY + 8, 0xFFE4F2FF, false);
        guiGraphics.drawString(this.font, Component.literal("Select role"), listX, listY - 14, 0xFF9FC2E5, false);
        guiGraphics.drawString(this.font, Component.literal("Create role"), createX, createY + 4, 0xFF9FC2E5, false);
        searchEdit.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.fill(listX, listY, listX + listW, listY + listH, 0xFF0A121B);

        int visibleRows = Math.max(1, listH / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredRoles.size() - visibleRows);
        listScrollOffset = Mth.clamp(listScrollOffset, 0, maxScroll);

        for (int row = 0; row < visibleRows; row++) {
            int index = listScrollOffset + row;
            if (index >= filteredRoles.size()) {
                break;
            }
            String token = filteredRoles.get(index);
            int rowY = listY + (row * ROW_HEIGHT);
            int bg = (row & 1) == 0 ? 0xFF0E1822 : 0xFF0C151E;
            guiGraphics.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_HEIGHT - 1, bg);

            String roleName = extractRoleName(token);
            int roleColor = extractRoleColor(token);
            guiGraphics.drawString(this.font, Component.literal(roleName), listX + 5, rowY + 4, 0xFFD9E9F8, false);
            guiGraphics.fill(listX + listW - 18, rowY + 3, listX + listW - 6, rowY + 13, 0xFF000000 | roleColor);
        }

        int trackX0 = listX + listW - 4;
        int trackX1 = listX + listW - 1;
        guiGraphics.fill(trackX0, listY, trackX1, listY + listH, 0x88324A60);
        int thumbH = Math.max(14, (int) ((visibleRows / (double) Math.max(visibleRows, filteredRoles.size())) * listH));
        int thumbTravel = Math.max(1, listH - thumbH);
        int thumbY = listY + (int) ((listScrollOffset / (double) Math.max(1, maxScroll)) * thumbTravel);
        guiGraphics.fill(trackX0, thumbY, trackX1, thumbY + thumbH, 0xFF9EC5EA);

        newRoleNameEdit.render(guiGraphics, mouseX, mouseY, partialTick);
        redEdit.render(guiGraphics, mouseX, mouseY, partialTick);
        greenEdit.render(guiGraphics, mouseX, mouseY, partialTick);
        blueEdit.render(guiGraphics, mouseX, mouseY, partialTick);

        int previewColor = (parseRgb(redEdit) << 16) | (parseRgb(greenEdit) << 8) | parseRgb(blueEdit);
        guiGraphics.drawString(this.font, Component.literal("Preview"), createX, createY + 118, 0xFFD7E3F4, false);
        guiGraphics.fill(createX + 48, createY + 114, createX + 66, createY + 132, 0xFF000000 | previewColor);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
