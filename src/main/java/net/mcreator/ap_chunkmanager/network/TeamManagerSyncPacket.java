package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.client.TeamManagerClientState;
import net.mcreator.ap_chunkmanager.server.TeamManagerRuntimeStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public record TeamManagerSyncPacket(
        boolean success,
        String statusMessage,
        List<TeamSnapshot> teams
) {
    public TeamManagerSyncPacket {
        statusMessage = statusMessage == null ? "" : statusMessage;
        teams = List.copyOf(teams == null ? List.of() : teams);
    }

    public static TeamManagerSyncPacket fromResult(TeamManagerRuntimeStore.ActionResult result) {
        List<TeamSnapshot> snapshots = result.teams().stream()
                .map(team -> new TeamSnapshot(team.name(), team.leaderName(), team.maxPlayers(), team.roles(), team.members(), team.memberRoles()))
                .toList();
        return new TeamManagerSyncPacket(result.success(), result.message(), snapshots);
    }

    public static void encode(TeamManagerSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.success);
        buffer.writeUtf(message.statusMessage, 256);
        buffer.writeVarInt(message.teams.size());
        for (TeamSnapshot team : message.teams) {
            buffer.writeUtf(team.name, 64);
            buffer.writeUtf(team.leaderName, 64);
            buffer.writeVarInt(team.maxPlayers);

            buffer.writeVarInt(team.roles.size());
            for (String role : team.roles) {
                buffer.writeUtf(role, 64);
            }

            buffer.writeVarInt(team.members.size());
            for (String member : team.members) {
                buffer.writeUtf(member, 64);
            }

            buffer.writeVarInt(team.memberRoles.size());
            for (Map.Entry<String, String> entry : team.memberRoles.entrySet()) {
                buffer.writeUtf(entry.getKey(), 64);
                buffer.writeUtf(entry.getValue(), 64);
            }
        }
    }

    public static TeamManagerSyncPacket decode(FriendlyByteBuf buffer) {
        boolean success = buffer.readBoolean();
        String status = buffer.readUtf(256);
        int teamCount = Math.max(0, Math.min(512, buffer.readVarInt()));
        List<TeamSnapshot> teams = new ArrayList<>(teamCount);

        for (int i = 0; i < teamCount; i++) {
            String name = buffer.readUtf(64);
            String leaderName = buffer.readUtf(64);
            int maxPlayers = Math.max(1, buffer.readVarInt());

            int roleCount = Math.max(0, Math.min(64, buffer.readVarInt()));
            List<String> roles = new ArrayList<>(roleCount);
            for (int r = 0; r < roleCount; r++) {
                roles.add(buffer.readUtf(64));
            }

            int memberCount = Math.max(0, Math.min(2048, buffer.readVarInt()));
            List<String> members = new ArrayList<>(memberCount);
            for (int m = 0; m < memberCount; m++) {
                members.add(buffer.readUtf(64));
            }

            int memberRoleCount = Math.max(0, Math.min(2048, buffer.readVarInt()));
            Map<String, String> memberRoles = new LinkedHashMap<>();
            for (int mr = 0; mr < memberRoleCount; mr++) {
                memberRoles.put(buffer.readUtf(64), buffer.readUtf(64));
            }

            teams.add(new TeamSnapshot(name, leaderName, maxPlayers, roles, members, memberRoles));
        }

        return new TeamManagerSyncPacket(success, status, teams);
    }

    public static void handle(TeamManagerSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> TeamManagerClientState.applySync(message));
        context.setPacketHandled(true);
    }

    public record TeamSnapshot(
            String name,
            String leaderName,
            int maxPlayers,
            List<String> roles,
            List<String> members,
            Map<String, String> memberRoles
    ) {
        public TeamSnapshot {
            name = name == null ? "" : name;
            leaderName = leaderName == null ? "" : leaderName;
            maxPlayers = Math.max(1, maxPlayers);
            roles = List.copyOf(roles == null ? List.of() : roles);
            members = List.copyOf(members == null ? List.of() : members);
            memberRoles = Map.copyOf(memberRoles == null ? Map.of() : memberRoles);
        }
    }
}
