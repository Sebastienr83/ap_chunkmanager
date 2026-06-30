package net.mcreator.ap_chunkmanager.client;

import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.mcreator.ap_chunkmanager.network.ChunkRuleListSyncPacket;
import net.mcreator.ap_chunkmanager.network.DeleteChunkRulesPacket;
import net.mcreator.ap_chunkmanager.network.RequestChunkRuleListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public class ChunkRuleManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 18;

    private final Screen parent;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;

    private int selectedIndex = -1;
    private int listScrollOffset;
    private long lastSeenVersion;
    private List<ChunkRuleListSyncPacket.ChunkRuleEntry> entries = List.of();

    public ChunkRuleManagerScreen(Screen parent) {
        super(Component.literal("Chunk Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        recalcLayout();

        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> requestState())
                .bounds(panelX + panelW - 86, panelY + 10, 76, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Delete Selected"), b -> deleteSelected())
                .bounds(panelX + panelW - 140, panelY + panelH - 30, 130, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(panelX + 12, panelY + panelH - 30, 76, 20)
                .build());

        requestState();
    }

    private void recalcLayout() {
        panelW = Math.min(980, this.width - 32);
        panelH = Math.min(620, this.height - 32);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        listX = panelX + 14;
        listY = panelY + 42;
        listW = panelW - 28;
        listH = panelH - 92;
    }

    private void requestState() {
        APChunkManagerMod.NETWORK.sendToServer(new RequestChunkRuleListPacket());
    }

    private void deleteSelected() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            return;
        }

        ChunkRuleListSyncPacket.ChunkRuleEntry selected = entries.get(selectedIndex);
        APChunkManagerMod.NETWORK.sendToServer(new DeleteChunkRulesPacket(List.of(new ChunkPos(selected.chunkX(), selected.chunkZ()))));
        ChunkManagerRuntimeEvents.invalidateClaimInfo(selected.chunkX(), selected.chunkZ());
        requestState();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int rowsVisible = Math.max(1, listH / ROW_HEIGHT);
            int maxScroll = Math.max(0, entries.size() - rowsVisible);
            listScrollOffset = Mth.clamp(listScrollOffset - (int) Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int row = (int) ((mouseY - listY) / ROW_HEIGHT);
            int index = listScrollOffset + row;
            if (index >= 0 && index < entries.size()) {
                selectedIndex = index;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (lastSeenVersion != ChunkRuleManagerClientState.getVersion()) {
            lastSeenVersion = ChunkRuleManagerClientState.getVersion();
            entries = ChunkRuleManagerClientState.getEntries();
            if (selectedIndex >= entries.size()) {
                selectedIndex = entries.isEmpty() ? -1 : 0;
            }
        }

        renderBackground(guiGraphics);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xE9101722, 0xE9121A28);
        guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF3A5168);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE0E1522);

        guiGraphics.drawString(this.font, Component.literal("Chunk Manager"), panelX + 14, panelY + 12, 0xFFE4F2FF, false);
        guiGraphics.drawString(this.font, Component.literal("ChunkX,ChunkZ | Name | Team | Role | Owner | @everyone perms"), listX, listY - 18, 0xFF9FC2E5,
                false);

        String status = ChunkRuleManagerClientState.getStatusMessage();
        if (!status.isBlank()) {
            int statusColor = ChunkRuleManagerClientState.isStatusSuccess() ? 0xFFA7E2A8 : 0xFFFF8D8D;
            guiGraphics.drawString(this.font, Component.literal(status), panelX + 96, panelY + panelH - 24, statusColor, false);
        }

        guiGraphics.fill(listX, listY, listX + listW, listY + listH, 0xAA08111B);
        int rows = Math.max(1, listH / ROW_HEIGHT);
        for (int i = 0; i < rows; i++) {
            int index = listScrollOffset + i;
            if (index >= entries.size()) {
                break;
            }

            ChunkRuleListSyncPacket.ChunkRuleEntry entry = entries.get(index);
            int y = listY + (i * ROW_HEIGHT);
            int bg = index == selectedIndex ? 0xFF24405A : ((i & 1) == 0 ? 0xFF0E1822 : 0xFF0C151E);
            guiGraphics.fill(listX + 1, y, listX + listW - 1, y + ROW_HEIGHT - 1, bg);

            String name = entry.chunkName().isBlank() ? "Unnamed" : entry.chunkName();
            String team = entry.teamName().isBlank() ? "None" : entry.teamName();
            String role = entry.roleName().isBlank() ? "None" : entry.roleName();
            String owner = entry.ownerName().isBlank() ? "Unknown" : entry.ownerName();
            String perms = "B:" + (entry.everyoneCanBuild() ? "Y" : "N")
                    + " Br:" + (entry.everyoneCanBreak() ? "Y" : "N")
                    + " Blk:" + (entry.everyoneCanInteractBlocks() ? "Y" : "N")
                    + " Ent:" + (entry.everyoneCanInteractEntities() ? "Y" : "N")
                    + " C:" + (entry.everyoneCanOpenContainers() ? "Y" : "N");

            String line = entry.chunkX() + "," + entry.chunkZ() + " | " + name + " | " + team + " | " + role + " | " + owner + " | " + perms;
            if (line.length() > 156) {
                line = line.substring(0, 156) + "...";
            }
            guiGraphics.drawString(this.font, Component.literal(line), listX + 5, y + 5, 0xFFD9E9F8, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
