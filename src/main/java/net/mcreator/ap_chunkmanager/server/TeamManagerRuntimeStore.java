package net.mcreator.ap_chunkmanager.server;

import net.mcreator.ap_chunkmanager.network.TeamManagerActionPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TeamManagerRuntimeStore {
    private static final String DATA_KEY = "ap_chunkmanager_team_manager";

    private TeamManagerRuntimeStore() {
    }

    public static ActionResult handleAction(ServerPlayer player, TeamManagerActionPacket packet) {
        TeamSavedData data = getData(player.level());

        return switch (packet.action()) {
            case REQUEST_STATE -> new ActionResult(true, "", data.toViews());
            case CREATE_TEAM -> createTeam(player, data, packet);
            case UPDATE_TEAM -> updateTeam(player, data, packet);
            case ADD_MEMBER -> addMember(player, data, packet);
            case REMOVE_MEMBER -> removeMember(player, data, packet);
        };
    }

    private static ActionResult createTeam(ServerPlayer player, TeamSavedData data, TeamManagerActionPacket packet) {
        String teamName = sanitizeTeamName(packet.teamName());
        if (teamName.isEmpty()) {
            return fail(data, "Team name is required.");
        }

        String key = normalizeKey(teamName);
        if (data.teamsByName.containsKey(key)) {
            return fail(data, "Team already exists.");
        }

        int maxPlayers = clampMaxPlayers(packet.maxPlayers());
        List<String> roles = parseRoles(packet.rolesCsv());
        Set<String> members = new LinkedHashSet<>();
        members.add(player.getGameProfile().getName());

        ManagedTeam team = new ManagedTeam(teamName, player.getUUID(), player.getGameProfile().getName(), maxPlayers, roles, members);
        data.teamsByName.put(key, team);
        data.setDirty();

        return ok(data, "Team created: " + teamName);
    }

    private static ActionResult updateTeam(ServerPlayer player, TeamSavedData data, TeamManagerActionPacket packet) {
        ManagedTeam team = data.teamsByName.get(normalizeKey(packet.teamName()));
        if (team == null) {
            return fail(data, "Team not found.");
        }
        if (!canManage(player, team)) {
            return fail(data, "Only the team leader or server OP can edit this team.");
        }

        int maxPlayers = clampMaxPlayers(packet.maxPlayers());
        if (team.members().size() > maxPlayers) {
            return fail(data, "Max players cannot be lower than current member count.");
        }

        List<String> roles = parseRoles(packet.rolesCsv());
        ManagedTeam updated = team.withMaxPlayersAndRoles(maxPlayers, roles);
        data.teamsByName.put(normalizeKey(team.name()), updated);
        data.setDirty();
        return ok(data, "Team updated: " + team.name());
    }

    private static ActionResult addMember(ServerPlayer player, TeamSavedData data, TeamManagerActionPacket packet) {
        ManagedTeam team = data.teamsByName.get(normalizeKey(packet.teamName()));
        if (team == null) {
            return fail(data, "Team not found.");
        }
        if (!canManage(player, team)) {
            return fail(data, "Only the team leader or server OP can manage members.");
        }

        String member = sanitizeMemberName(packet.memberName());
        if (member.isEmpty()) {
            return fail(data, "Member name is required.");
        }
        if (team.members().contains(member)) {
            return fail(data, member + " is already in the team.");
        }
        if (team.members().size() >= team.maxPlayers()) {
            return fail(data, "Team is full.");
        }

        Set<String> members = new LinkedHashSet<>(team.members());
        members.add(member);
        data.teamsByName.put(normalizeKey(team.name()), team.withMembers(members));
        data.setDirty();
        return ok(data, "Added " + member + " to " + team.name());
    }

    private static ActionResult removeMember(ServerPlayer player, TeamSavedData data, TeamManagerActionPacket packet) {
        ManagedTeam team = data.teamsByName.get(normalizeKey(packet.teamName()));
        if (team == null) {
            return fail(data, "Team not found.");
        }
        if (!canManage(player, team)) {
            return fail(data, "Only the team leader or server OP can manage members.");
        }

        String member = sanitizeMemberName(packet.memberName());
        if (member.isEmpty()) {
            return fail(data, "Member name is required.");
        }
        if (team.leaderName().equalsIgnoreCase(member)) {
            return fail(data, "Team leader cannot be removed.");
        }
        if (!team.members().contains(member)) {
            return fail(data, member + " is not in the team.");
        }

        Set<String> members = new LinkedHashSet<>(team.members());
        members.remove(member);
        data.teamsByName.put(normalizeKey(team.name()), team.withMembers(members));
        data.setDirty();
        return ok(data, "Removed " + member + " from " + team.name());
    }

    private static ActionResult ok(TeamSavedData data, String message) {
        return new ActionResult(true, message, data.toViews());
    }

    private static ActionResult fail(TeamSavedData data, String message) {
        return new ActionResult(false, message, data.toViews());
    }

    private static boolean canManage(ServerPlayer player, ManagedTeam team) {
        return player.hasPermissions(2) || player.getUUID().equals(team.leaderUuid());
    }

    private static int clampMaxPlayers(int value) {
        return Math.max(1, Math.min(512, value));
    }

    private static String sanitizeTeamName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.substring(0, Math.min(32, trimmed.length()));
    }

    private static String sanitizeMemberName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        return trimmed.substring(0, Math.min(16, trimmed.length()));
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static List<String> parseRoles(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> roles = new LinkedHashSet<>();
        String[] split = csv.split(",");
        for (String role : split) {
            String trimmed = role == null ? "" : role.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            roles.add(trimmed.substring(0, Math.min(32, trimmed.length())));
            if (roles.size() >= 64) {
                break;
            }
        }
        return List.copyOf(roles);
    }

    private static TeamSavedData getData(Level level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TeamSavedData::load, TeamSavedData::new, DATA_KEY);
    }

    public record TeamView(String name, String leaderName, int maxPlayers, List<String> roles, List<String> members) {
    }

    public record ActionResult(boolean success, String message, List<TeamView> teams) {
    }

    private record ManagedTeam(String name, UUID leaderUuid, String leaderName, int maxPlayers, List<String> roles, Set<String> members) {
        private static final String KEY_NAME = "name";
        private static final String KEY_LEADER_UUID = "leaderUuid";
        private static final String KEY_LEADER_NAME = "leaderName";
        private static final String KEY_MAX_PLAYERS = "maxPlayers";
        private static final String KEY_ROLES = "roles";
        private static final String KEY_MEMBERS = "members";

        ManagedTeam {
            name = name == null ? "" : name;
            leaderName = leaderName == null ? "" : leaderName;
            maxPlayers = clampMaxPlayers(maxPlayers);
            roles = List.copyOf(roles == null ? List.of() : roles);
            members = Set.copyOf(members == null ? Set.of() : members);
        }

        ManagedTeam withMaxPlayersAndRoles(int newMaxPlayers, List<String> newRoles) {
            return new ManagedTeam(name, leaderUuid, leaderName, newMaxPlayers, newRoles, members);
        }

        ManagedTeam withMembers(Set<String> newMembers) {
            return new ManagedTeam(name, leaderUuid, leaderName, maxPlayers, roles, newMembers);
        }

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString(KEY_NAME, name);
            tag.putUUID(KEY_LEADER_UUID, leaderUuid);
            tag.putString(KEY_LEADER_NAME, leaderName);
            tag.putInt(KEY_MAX_PLAYERS, maxPlayers);

            ListTag roleList = new ListTag();
            for (String role : roles) {
                CompoundTag roleTag = new CompoundTag();
                roleTag.putString("value", role);
                roleList.add(roleTag);
            }
            tag.put(KEY_ROLES, roleList);

            ListTag memberList = new ListTag();
            for (String member : members) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putString("value", member);
                memberList.add(memberTag);
            }
            tag.put(KEY_MEMBERS, memberList);
            return tag;
        }

        static ManagedTeam fromTag(CompoundTag tag) {
            List<String> roles = new ArrayList<>();
            ListTag roleTags = tag.getList(KEY_ROLES, Tag.TAG_COMPOUND);
            for (int i = 0; i < roleTags.size(); i++) {
                String role = roleTags.getCompound(i).getString("value");
                if (!role.isBlank()) {
                    roles.add(role);
                }
            }

            Set<String> members = new LinkedHashSet<>();
            ListTag memberTags = tag.getList(KEY_MEMBERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < memberTags.size(); i++) {
                String member = memberTags.getCompound(i).getString("value");
                if (!member.isBlank()) {
                    members.add(member);
                }
            }

            return new ManagedTeam(
                    tag.getString(KEY_NAME),
                    tag.getUUID(KEY_LEADER_UUID),
                    tag.getString(KEY_LEADER_NAME),
                    tag.getInt(KEY_MAX_PLAYERS),
                    roles,
                    members
            );
        }
    }

    private static final class TeamSavedData extends SavedData {
        private static final String KEY_TEAMS = "teams";

        private final Map<String, ManagedTeam> teamsByName = new HashMap<>();

        static TeamSavedData load(CompoundTag tag) {
            TeamSavedData data = new TeamSavedData();
            ListTag teams = tag.getList(KEY_TEAMS, Tag.TAG_COMPOUND);
            for (int i = 0; i < teams.size(); i++) {
                ManagedTeam team = ManagedTeam.fromTag(teams.getCompound(i));
                if (!team.name().isBlank()) {
                    data.teamsByName.put(normalizeKey(team.name()), team);
                }
            }
            return data;
        }

        List<TeamView> toViews() {
            return teamsByName.values().stream()
                    .sorted(Comparator.comparing(ManagedTeam::name, String.CASE_INSENSITIVE_ORDER))
                    .map(team -> new TeamView(
                            team.name(),
                            team.leaderName(),
                            team.maxPlayers(),
                            List.copyOf(team.roles()),
                            team.members().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()
                    ))
                    .toList();
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag teams = new ListTag();
            for (ManagedTeam team : teamsByName.values()) {
                teams.add(team.toTag());
            }
            tag.put(KEY_TEAMS, teams);
            return tag;
        }
    }
}
