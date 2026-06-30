package net.mcreator.ap_chunkmanager.client;

import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.mcreator.ap_chunkmanager.network.TeamManagerActionPacket;
import net.mcreator.ap_chunkmanager.network.TeamManagerSyncPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class TeamManagerScreen extends Screen {
    private static final int ROW_HEIGHT = 18;
    private static final int ROLE_ROW_HEIGHT = 16;

    private final Screen parent;

    private EditBox teamNameEdit;
    private EditBox maxPlayersEdit;
    private EditBox memberEdit;
    private Button rolePickerButton;

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
    private List<TeamManagerSyncPacket.TeamSnapshot> teams = List.of();
    private List<ListRow> visibleRows = List.of();
    private final Set<String> expandedTeamKeys = new LinkedHashSet<>();

    private String selectedRoleName = "";
    private int selectedRoleColor = 0xFFFFFF;

    private boolean rolePopupOpen;
    private EditBox roleSearchEdit;
    private EditBox roleNewNameEdit;
    private EditBox roleNewColorEdit;
    private int rolePopupX;
    private int rolePopupY;
    private int rolePopupW;
    private int rolePopupH;
    private int rolePopupScrollOffset;
    private List<String> filteredRoles = List.of();

    public TeamManagerScreen(Screen parent) {
        super(Component.literal("Team Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        recalcLayout();

        int fieldX = panelX + panelW / 2 + 16;
        int fieldW = panelW / 2 - 28;
        int y = panelY + 42;

        teamNameEdit = addRenderableWidget(new EditBox(this.font, fieldX, y, fieldW, 20, Component.literal("Team Name")));
        teamNameEdit.setHint(Component.literal("Team Name"));

        y += 34;
        maxPlayersEdit = addRenderableWidget(new EditBox(this.font, fieldX, y, fieldW, 20, Component.literal("Max Players")));
        maxPlayersEdit.setHint(Component.literal("Max Players"));
        maxPlayersEdit.setFilter(value -> value.matches("\\d{0,4}"));
        maxPlayersEdit.setValue("8");

        y += 34;
        rolePickerButton = addRenderableWidget(
                Button.builder(Component.empty(), b -> openRolePopup())
                        .bounds(fieldX, y, fieldW, 20)
                        .build()
        );
        updateRolePickerButtonLabel();

        y += 38;
        addRenderableWidget(Button.builder(Component.literal("Create Team"), b -> createTeam()).bounds(fieldX, y, fieldW, 20).build());

        y += 30;
        addRenderableWidget(Button.builder(Component.literal("Save Selected Team"), b -> saveTeam()).bounds(fieldX, y, fieldW, 20).build());

        y += 40;
        memberEdit = addRenderableWidget(new EditBox(this.font, fieldX, y, fieldW, 20, Component.literal("Player name")));
        memberEdit.setHint(Component.literal("Player name"));

        y += 30;
        int half = (fieldW - 6) / 2;
        addRenderableWidget(Button.builder(Component.literal("Add Player"), b -> addMember()).bounds(fieldX, y, half, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Remove Player"), b -> removeMember()).bounds(fieldX + half + 6, y, half, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> requestState()).bounds(panelX + panelW - 86, panelY + 10, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose()).bounds(panelX + 14, panelY + panelH - 30, 90, 20).build());

        roleSearchEdit = addRenderableWidget(new EditBox(this.font, 0, 0, 100, 20, Component.literal("Search role")));
        roleSearchEdit.setVisible(false);
        roleSearchEdit.setResponder(value -> refreshRoleFilter());

        roleNewNameEdit = addRenderableWidget(new EditBox(this.font, 0, 0, 100, 20, Component.literal("New role")));
        roleNewNameEdit.setVisible(false);
        roleNewNameEdit.setHint(Component.literal("New role"));

        roleNewColorEdit = addRenderableWidget(new EditBox(this.font, 0, 0, 100, 20, Component.literal("RGB (hex)")));
        roleNewColorEdit.setVisible(false);
        roleNewColorEdit.setHint(Component.literal("RGB hex, e.g. FFAA33"));
        roleNewColorEdit.setFilter(value -> value.matches("[0-9a-fA-F]{0,6}"));

        requestState();
    }

    private void recalcLayout() {
        panelW = Math.min(1040, this.width - 32);
        panelH = Math.min(620, this.height - 32);
        panelX = (this.width - panelW) / 2;
        panelY = (this.height - panelH) / 2;

        listX = panelX + 14;
        listY = panelY + 42;
        listW = panelW / 2 - 24;
        listH = panelH - 108;

        rolePopupW = Math.min(520, this.width - 80);
        rolePopupH = Math.min(340, this.height - 80);
        rolePopupX = (this.width - rolePopupW) / 2;
        rolePopupY = (this.height - rolePopupH) / 2;
    }

    private void requestState() {
        APChunkManagerMod.NETWORK.sendToServer(new TeamManagerActionPacket(TeamManagerActionPacket.Action.REQUEST_STATE, "", 8, "", ""));
    }

    private static String teamKey(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private static String normalizeRole(String value) {
        return value == null ? "" : value.trim();
    }

    private static String roleNameFromToken(String token) {
        int idx = token.indexOf('#');
        return idx >= 0 ? token.substring(0, idx).trim() : token.trim();
    }

    private static int roleColorFromToken(String token) {
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

    private static String composeRoleToken(String name, int color) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.trim() + "#" + String.format("%06X", color & 0xFFFFFF);
    }

    private void selectRoleToken(String token) {
        selectedRoleName = roleNameFromToken(token);
        selectedRoleColor = roleColorFromToken(token);
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

    private void rebuildVisibleRows() {
        List<ListRow> rows = new ArrayList<>();
        for (int i = 0; i < teams.size(); i++) {
            TeamManagerSyncPacket.TeamSnapshot team = teams.get(i);
            String key = teamKey(team.name());
            rows.add(ListRow.team(i, team));
            if (!expandedTeamKeys.contains(key)) {
                continue;
            }
            for (String member : team.members()) {
                String role = normalizeRole(team.memberRoles().getOrDefault(member, ""));
                rows.add(ListRow.member(i, team.name(), member, role));
            }
        }
        visibleRows = rows;
        int rowsPerPage = Math.max(1, listH / ROW_HEIGHT);
        int maxScroll = Math.max(0, visibleRows.size() - rowsPerPage);
        listScrollOffset = Mth.clamp(listScrollOffset, 0, maxScroll);
    }

    private String buildRolesCsvForTeam(TeamManagerSyncPacket.TeamSnapshot team) {
        List<String> roles = new ArrayList<>(team.roles());
        if (!selectedRoleName.isBlank()) {
            String token = composeRoleToken(selectedRoleName, selectedRoleColor);
            if (roles.stream().noneMatch(existing -> existing.equalsIgnoreCase(token))) {
                roles.add(token);
            }
        }
        return String.join(",", roles);
    }

    private void createTeam() {
        String rolesCsv = selectedRoleName.isBlank() ? "" : composeRoleToken(selectedRoleName, selectedRoleColor);
        APChunkManagerMod.NETWORK.sendToServer(new TeamManagerActionPacket(
                TeamManagerActionPacket.Action.CREATE_TEAM,
                teamNameEdit.getValue(),
                parseMaxPlayers(),
                rolesCsv,
                ""
        ));
    }

    private void saveTeam() {
        TeamManagerSyncPacket.TeamSnapshot selected = getSelectedTeam();
        if (selected == null) {
            return;
        }

        APChunkManagerMod.NETWORK.sendToServer(new TeamManagerActionPacket(
                TeamManagerActionPacket.Action.UPDATE_TEAM,
                selected.name(),
                parseMaxPlayers(),
                buildRolesCsvForTeam(selected),
                ""
        ));
    }

    private void addMember() {
        TeamManagerSyncPacket.TeamSnapshot selected = getSelectedTeam();
        if (selected == null) {
            return;
        }

        APChunkManagerMod.NETWORK.sendToServer(new TeamManagerActionPacket(
                TeamManagerActionPacket.Action.ADD_MEMBER,
                selected.name(),
                selected.maxPlayers(),
                "",
                memberEdit.getValue()
        ));
    }

    private void removeMember() {
        TeamManagerSyncPacket.TeamSnapshot selected = getSelectedTeam();
        if (selected == null) {
            return;
        }

        APChunkManagerMod.NETWORK.sendToServer(new TeamManagerActionPacket(
                TeamManagerActionPacket.Action.REMOVE_MEMBER,
                selected.name(),
                selected.maxPlayers(),
                "",
                memberEdit.getValue()
        ));
    }

    private void openRolePopup() {
        TeamManagerSyncPacket.TeamSnapshot selected = getSelectedTeam();
        if (selected == null && !teams.isEmpty()) {
            selectedIndex = 0;
            selected = teams.get(0);
            teamNameEdit.setValue(selected.name());
            maxPlayersEdit.setValue(Integer.toString(selected.maxPlayers()));
        }
        if (selected == null) {
            return;
        }

        rolePopupOpen = true;
        rolePopupScrollOffset = 0;
        roleSearchEdit.setVisible(true);
        roleNewNameEdit.setVisible(true);
        roleNewColorEdit.setVisible(true);
        roleSearchEdit.setFocused(true);
        positionRolePopupFields();
        refreshRoleFilter();
    }

    private void closeRolePopup() {
        rolePopupOpen = false;
        roleSearchEdit.setVisible(false);
        roleNewNameEdit.setVisible(false);
        roleNewColorEdit.setVisible(false);
        roleSearchEdit.setFocused(false);
    }

    private void positionRolePopupFields() {
        roleSearchEdit.setX(rolePopupX + 12);
        roleSearchEdit.setY(rolePopupY + 22);
        roleSearchEdit.setWidth(rolePopupW - 24);

        roleNewNameEdit.setX(rolePopupX + 12);
        roleNewNameEdit.setY(rolePopupY + rolePopupH - 66);
        roleNewNameEdit.setWidth(rolePopupW - 150);

        roleNewColorEdit.setX(rolePopupX + rolePopupW - 132);
        roleNewColorEdit.setY(rolePopupY + rolePopupH - 66);
        roleNewColorEdit.setWidth(58);
    }

    private void refreshRoleFilter() {
        TeamManagerSyncPacket.TeamSnapshot selected = getSelectedTeam();
        if (selected == null) {
            filteredRoles = List.of();
            return;
        }

        String needle = roleSearchEdit.getValue().trim().toLowerCase();
        if (needle.isBlank()) {
            filteredRoles = selected.roles();
            return;
        }

        List<String> out = new ArrayList<>();
        for (String value : selected.roles()) {
            if (value.toLowerCase().contains(needle)) {
                out.add(value);
            }
        }
        filteredRoles = out;
    }

    private void createRoleFromPopup() {
        TeamManagerSyncPacket.TeamSnapshot selected = getSelectedTeam();
        if (selected == null) {
            return;
        }

        String name = roleNewNameEdit.getValue().trim();
        if (name.isEmpty()) {
            return;
        }

        int color = 0xFFFFFF;
        try {
            String raw = roleNewColorEdit.getValue().trim();
            if (!raw.isBlank()) {
                color = Integer.parseInt(raw, 16) & 0xFFFFFF;
            }
        } catch (Exception ignored) {
            color = 0xFFFFFF;
        }

        String token = composeRoleToken(name, color);
        APChunkManagerMod.NETWORK.sendToServer(new TeamManagerActionPacket(
                TeamManagerActionPacket.Action.ADD_ROLE,
                selected.name(),
                selected.maxPlayers(),
                token,
                ""
        ));
        selectRoleToken(token);
        requestState();
        roleNewNameEdit.setValue("");
    }

    private int parseMaxPlayers() {
        try {
            return Mth.clamp(Integer.parseInt(maxPlayersEdit.getValue().trim()), 1, 512);
        } catch (Exception ignored) {
            return 8;
        }
    }

    private TeamManagerSyncPacket.TeamSnapshot getSelectedTeam() {
        if (selectedIndex < 0 || selectedIndex >= teams.size()) {
            return null;
        }
        return teams.get(selectedIndex);
    }

    private void toggleTeamExpanded(TeamManagerSyncPacket.TeamSnapshot team) {
        String key = teamKey(team.name());
        if (expandedTeamKeys.contains(key)) {
            expandedTeamKeys.remove(key);
        } else {
            expandedTeamKeys.add(key);
        }
        rebuildVisibleRows();
    }

    private void cycleMemberRole(TeamManagerSyncPacket.TeamSnapshot team, String memberName) {
        List<String> roles = new ArrayList<>();
        roles.add("");
        roles.addAll(team.roles());

        String current = normalizeRole(team.memberRoles().getOrDefault(memberName, ""));
        int idx = 0;
        for (int i = 0; i < roles.size(); i++) {
            if (roles.get(i).equalsIgnoreCase(current)) {
                idx = i;
                break;
            }
        }

        String nextRole = roles.get((idx + 1) % roles.size());
        APChunkManagerMod.NETWORK.sendToServer(new TeamManagerActionPacket(
                TeamManagerActionPacket.Action.ASSIGN_MEMBER_ROLE,
                team.name(),
                team.maxPlayers(),
                nextRole,
                memberName
        ));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (rolePopupOpen) {
            int listPX = rolePopupX + 12;
            int listPY = rolePopupY + 48;
            int listPW = rolePopupW - 24;
            int listPH = rolePopupH - 122;
            if (mouseX >= listPX && mouseX <= listPX + listPW && mouseY >= listPY && mouseY <= listPY + listPH) {
                int visibleRows = Math.max(1, listPH / ROLE_ROW_HEIGHT);
                int maxScroll = Math.max(0, filteredRoles.size() - visibleRows);
                rolePopupScrollOffset = Mth.clamp(rolePopupScrollOffset - (int) Math.signum(delta), 0, maxScroll);
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, delta);
        }

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int visibleRowsCount = Math.max(1, listH / ROW_HEIGHT);
            int maxScroll = Math.max(0, this.visibleRows.size() - visibleRowsCount);
            listScrollOffset = Mth.clamp(listScrollOffset - (int) Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (rolePopupOpen) {
            positionRolePopupFields();

            if (button == 0) {
                int listPX = rolePopupX + 12;
                int listPY = rolePopupY + 48;
                int listPW = rolePopupW - 24;
                int listPH = rolePopupH - 122;
                if (mouseX >= listPX && mouseX <= listPX + listPW && mouseY >= listPY && mouseY <= listPY + listPH) {
                    int row = (int) ((mouseY - listPY) / ROLE_ROW_HEIGHT);
                    int index = rolePopupScrollOffset + row;
                    if (index >= 0 && index < filteredRoles.size()) {
                        selectRoleToken(filteredRoles.get(index));
                        closeRolePopup();
                    }
                    return true;
                }

                int createX = rolePopupX + rolePopupW - 68;
                int createY = rolePopupY + rolePopupH - 66;
                if (mouseX >= createX && mouseX <= createX + 56 && mouseY >= createY && mouseY <= createY + 20) {
                    createRoleFromPopup();
                    return true;
                }

                int closeX = rolePopupX + rolePopupW - 68;
                int closeY = rolePopupY + 8;
                if (mouseX >= closeX && mouseX <= closeX + 56 && mouseY >= closeY && mouseY <= closeY + 18) {
                    closeRolePopup();
                    return true;
                }
            }

            if (roleSearchEdit.mouseClicked(mouseX, mouseY, button) || roleNewNameEdit.mouseClicked(mouseX, mouseY, button)
                    || roleNewColorEdit.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            return true;
        }

        if (button == 0 && mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            int row = (int) ((mouseY - listY) / ROW_HEIGHT);
            int index = listScrollOffset + row;
            if (index >= 0 && index < visibleRows.size()) {
                ListRow clickedRow = visibleRows.get(index);
                selectedIndex = clickedRow.teamIndex();
                TeamManagerSyncPacket.TeamSnapshot selected = teams.get(clickedRow.teamIndex());
                teamNameEdit.setValue(selected.name());
                maxPlayersEdit.setValue(Integer.toString(selected.maxPlayers()));

                if (clickedRow.isTeamRow()) {
                    toggleTeamExpanded(selected);
                } else {
                    cycleMemberRole(selected, clickedRow.memberName());
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (rolePopupOpen && keyCode == 256) {
            closeRolePopup();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (lastSeenVersion != TeamManagerClientState.getVersion()) {
            lastSeenVersion = TeamManagerClientState.getVersion();
            teams = TeamManagerClientState.getTeams();
            if (selectedIndex >= teams.size()) {
                selectedIndex = teams.isEmpty() ? -1 : 0;
            }
            rebuildVisibleRows();
            if (rolePopupOpen) {
                refreshRoleFilter();
            }
        }

        renderBackground(guiGraphics);
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xE9101722, 0xE9121A28);
        guiGraphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF3A5168);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE0E1522);

        guiGraphics.drawString(this.font, Component.literal("Team Manager"), panelX + 18, panelY + 12, 0xFFE4F2FF, false);
        guiGraphics.drawString(this.font, Component.literal("Teams"), listX, listY - 18, 0xFF9FC2E5, false);

        String status = TeamManagerClientState.getStatusMessage();
        if (!status.isBlank()) {
            int statusColor = TeamManagerClientState.isStatusSuccess() ? 0xFFA7E2A8 : 0xFFFF8D8D;
            guiGraphics.drawString(this.font, Component.literal(status), panelX + 18, panelY + panelH - 26, statusColor, false);
        }

        guiGraphics.fill(listX, listY, listX + listW, listY + listH, 0xAA08111B);
        int rows = Math.max(1, listH / ROW_HEIGHT);
        for (int i = 0; i < rows; i++) {
            int index = listScrollOffset + i;
            if (index >= visibleRows.size()) {
                break;
            }

            ListRow row = visibleRows.get(index);
            TeamManagerSyncPacket.TeamSnapshot team = teams.get(row.teamIndex());
            int rowY = listY + (i * ROW_HEIGHT);
            int bg = row.teamIndex() == selectedIndex ? 0xFF24405A : ((i & 1) == 0 ? 0xFF0E1822 : 0xFF0C151E);
            guiGraphics.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_HEIGHT - 1, bg);

            if (row.isTeamRow()) {
                boolean expanded = expandedTeamKeys.contains(teamKey(team.name()));
                String line = (expanded ? "[-] " : "[+] ") + team.name() + "  [" + team.members().size() + "/" + team.maxPlayers() + "]";
                guiGraphics.drawString(this.font, Component.literal(line), listX + 6, rowY + 5, 0xFFD9E9F8, false);
            } else {
                String role = row.memberRole().isBlank() ? "None" : row.memberRole();
                String memberLine = "  - " + row.memberName() + "  (role: " + role + ")";
                guiGraphics.drawString(this.font, Component.literal(memberLine), listX + 12, rowY + 5, 0xFFBFD3E8, false);
            }
        }

        int trackX0 = listX + listW - 4;
        int trackX1 = listX + listW - 1;
        guiGraphics.fill(trackX0, listY, trackX1, listY + listH, 0x88324A60);
        int visibleRowsCount = Math.max(1, listH / ROW_HEIGHT);
        int maxScroll = Math.max(1, this.visibleRows.size() - visibleRowsCount);
        int thumbH = Math.max(16, (int) ((visibleRowsCount / (double) Math.max(visibleRowsCount, this.visibleRows.size())) * listH));
        int thumbTravel = Math.max(1, listH - thumbH);
        int thumbY = listY + (int) ((listScrollOffset / (double) maxScroll) * thumbTravel);
        guiGraphics.fill(trackX0, thumbY, trackX1, thumbY + thumbH, 0xFF9EC5EA);

        TeamManagerSyncPacket.TeamSnapshot selected = getSelectedTeam();
        int rightX = panelX + panelW / 2 + 16;
        int infoY = panelY + panelH - 114;
        guiGraphics.drawString(this.font, Component.literal("Selected Team"), rightX, infoY, 0xFF9FC2E5, false);
        if (selected != null) {
            guiGraphics.drawString(this.font, Component.literal("Leader: " + selected.leaderName()), rightX, infoY + 16, 0xFFD7E3F4, false);
            guiGraphics.drawString(this.font, Component.literal("Roles: " + String.join(", ", selected.roles())), rightX, infoY + 32, 0xFFD7E3F4, false);
            guiGraphics.drawString(this.font, Component.literal("Assign role: click member row"), rightX, infoY + 46, 0xFF9FC2E5, false);
            String membersPreview = String.join(", ", selected.members());
            if (membersPreview.length() > 88) {
                membersPreview = membersPreview.substring(0, 88) + "...";
            }
            guiGraphics.drawString(this.font, Component.literal("Members: " + membersPreview), rightX, infoY + 62, 0xFFD7E3F4, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (rolePopupOpen) {
            renderRolePopup(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    private void renderRolePopup(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        positionRolePopupFields();

        guiGraphics.fill(0, 0, this.width, this.height, 0xAA000000);
        guiGraphics.fill(rolePopupX - 1, rolePopupY - 1, rolePopupX + rolePopupW + 1, rolePopupY + rolePopupH + 1, 0xFF5C7EA3);
        guiGraphics.fill(rolePopupX, rolePopupY, rolePopupX + rolePopupW, rolePopupY + rolePopupH, 0xFF111A24);

        guiGraphics.drawString(this.font, Component.literal("Role Browser"), rolePopupX + 12, rolePopupY + 8, 0xFFE4F2FF, false);
        guiGraphics.fill(rolePopupX + rolePopupW - 68, rolePopupY + 8, rolePopupX + rolePopupW - 12, rolePopupY + 26, 0xFF3B556E);
        guiGraphics.drawString(this.font, Component.literal("Close"), rolePopupX + rolePopupW - 56, rolePopupY + 14, 0xFFEAF2FF, false);

        roleSearchEdit.render(guiGraphics, mouseX, mouseY, partialTick);

        int listPX = rolePopupX + 12;
        int listPY = rolePopupY + 48;
        int listPW = rolePopupW - 24;
        int listPH = rolePopupH - 122;
        guiGraphics.fill(listPX, listPY, listPX + listPW, listPY + listPH, 0xFF0A121B);

        int rows = Math.max(1, listPH / ROLE_ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredRoles.size() - rows);
        rolePopupScrollOffset = Mth.clamp(rolePopupScrollOffset, 0, maxScroll);

        for (int i = 0; i < rows; i++) {
            int index = rolePopupScrollOffset + i;
            if (index >= filteredRoles.size()) {
                break;
            }
            int y = listPY + (i * ROLE_ROW_HEIGHT);
            int bg = (i & 1) == 0 ? 0xFF0E1822 : 0xFF0C151E;
            guiGraphics.fill(listPX + 1, y, listPX + listPW - 1, y + ROLE_ROW_HEIGHT - 1, bg);
            guiGraphics.drawString(this.font, Component.literal(filteredRoles.get(index)), listPX + 5, y + 4, 0xFFD9E9F8, false);
        }

        int trackX0 = listPX + listPW - 4;
        int trackX1 = listPX + listPW - 1;
        guiGraphics.fill(trackX0, listPY, trackX1, listPY + listPH, 0x88324A60);
        int thumbH = Math.max(14, (int) ((rows / (double) Math.max(rows, filteredRoles.size())) * listPH));
        int thumbTravel = Math.max(1, listPH - thumbH);
        int thumbY = listPY + (int) ((rolePopupScrollOffset / (double) Math.max(1, maxScroll)) * thumbTravel);
        guiGraphics.fill(trackX0, thumbY, trackX1, thumbY + thumbH, 0xFF9EC5EA);

        roleNewNameEdit.render(guiGraphics, mouseX, mouseY, partialTick);
        roleNewColorEdit.render(guiGraphics, mouseX, mouseY, partialTick);

        int createX = rolePopupX + rolePopupW - 68;
        int createY = rolePopupY + rolePopupH - 66;
        guiGraphics.fill(createX, createY, createX + 56, createY + 20, 0xFF3B556E);
        guiGraphics.drawString(this.font, Component.literal("Add"), createX + 18, createY + 6, 0xFFEAF2FF, false);

        guiGraphics.drawString(this.font, Component.literal("Select role or create + persist role"), rolePopupX + 12, rolePopupY + rolePopupH - 84, 0xFF9FC2E5,
                false);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private record ListRow(int teamIndex, boolean isTeamRow, String teamName, String memberName, String memberRole) {
        static ListRow team(int teamIndex, TeamManagerSyncPacket.TeamSnapshot team) {
            return new ListRow(teamIndex, true, team.name(), "", "");
        }

        static ListRow member(int teamIndex, String teamName, String memberName, String memberRole) {
            return new ListRow(teamIndex, false, teamName, memberName, memberRole == null ? "" : memberRole);
        }
    }
}
