package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.mcreator.ap_chunkmanager.server.TeamManagerRuntimeStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record TeamManagerActionPacket(
        Action action,
        String teamName,
        int maxPlayers,
        String rolesCsv,
        String memberName
) {
    public enum Action {
        REQUEST_STATE,
        CREATE_TEAM,
        UPDATE_TEAM,
        ADD_MEMBER,
        REMOVE_MEMBER
    }

    public TeamManagerActionPacket {
        action = action == null ? Action.REQUEST_STATE : action;
        teamName = teamName == null ? "" : teamName;
        rolesCsv = rolesCsv == null ? "" : rolesCsv;
        memberName = memberName == null ? "" : memberName;
        maxPlayers = Math.max(1, maxPlayers);
    }

    public static void encode(TeamManagerActionPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.action.ordinal());
        buffer.writeUtf(message.teamName, 64);
        buffer.writeVarInt(message.maxPlayers);
        buffer.writeUtf(message.rolesCsv, 256);
        buffer.writeUtf(message.memberName, 64);
    }

    public static TeamManagerActionPacket decode(FriendlyByteBuf buffer) {
        int actionOrdinal = Math.max(0, Math.min(Action.values().length - 1, buffer.readVarInt()));
        Action action = Action.values()[actionOrdinal];
        String teamName = buffer.readUtf(64);
        int maxPlayers = Math.max(1, buffer.readVarInt());
        String rolesCsv = buffer.readUtf(256);
        String memberName = buffer.readUtf(64);
        return new TeamManagerActionPacket(action, teamName, maxPlayers, rolesCsv, memberName);
    }

    public static void handle(TeamManagerActionPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_SERVER) {
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || player.level() == null) {
                return;
            }

            TeamManagerRuntimeStore.ActionResult result = TeamManagerRuntimeStore.handleAction(player, message);
            APChunkManagerMod.NETWORK.send(PacketDistributor.PLAYER.with(() -> player), TeamManagerSyncPacket.fromResult(result));
        });

        context.setPacketHandled(true);
    }
}
