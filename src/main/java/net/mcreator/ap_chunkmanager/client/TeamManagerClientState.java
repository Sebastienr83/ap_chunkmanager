package net.mcreator.ap_chunkmanager.client;

import net.mcreator.ap_chunkmanager.network.TeamManagerSyncPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class TeamManagerClientState {
    private static List<TeamManagerSyncPacket.TeamSnapshot> teams = List.of();
    private static String statusMessage = "";
    private static boolean statusSuccess = true;
    private static long version;

    private TeamManagerClientState() {
    }

    public static void applySync(TeamManagerSyncPacket packet) {
        teams = packet.teams().stream()
                .sorted(Comparator.comparing(TeamManagerSyncPacket.TeamSnapshot::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        statusMessage = packet.statusMessage() == null ? "" : packet.statusMessage();
        statusSuccess = packet.success();
        version++;
    }

    public static List<TeamManagerSyncPacket.TeamSnapshot> getTeams() {
        return new ArrayList<>(teams);
    }

    public static String getStatusMessage() {
        return statusMessage;
    }

    public static boolean isStatusSuccess() {
        return statusSuccess;
    }

    public static long getVersion() {
        return version;
    }

    public static List<String> getRoleCatalog() {
        Set<String> values = new LinkedHashSet<>();
        for (TeamManagerSyncPacket.TeamSnapshot team : teams) {
            values.addAll(team.roles());
        }
        return new ArrayList<>(values);
    }
}
