package net.mcreator.ap_chunkmanager.client;

import net.mcreator.ap_chunkmanager.network.ChunkRuleListSyncPacket;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ChunkRuleManagerClientState {
    private static List<ChunkRuleListSyncPacket.ChunkRuleEntry> entries = List.of();
    private static String statusMessage = "";
    private static boolean statusSuccess = true;
    private static long version;

    private ChunkRuleManagerClientState() {
    }

    public static void applySync(ChunkRuleListSyncPacket packet) {
        entries = packet.entries().stream()
                .sorted(Comparator
                        .comparingInt(ChunkRuleListSyncPacket.ChunkRuleEntry::chunkX)
                        .thenComparingInt(ChunkRuleListSyncPacket.ChunkRuleEntry::chunkZ))
                .toList();
        statusMessage = packet.statusMessage();
        statusSuccess = packet.success();
        version++;
    }

    public static List<ChunkRuleListSyncPacket.ChunkRuleEntry> getEntries() {
        return new ArrayList<>(entries);
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
}
