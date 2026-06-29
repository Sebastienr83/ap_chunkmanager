package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.mcreator.ap_chunkmanager.server.ChunkRuleRuntimeStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record RequestChunkClaimInfoPacket(int chunkX, int chunkZ) {
    public static void encode(RequestChunkClaimInfoPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.chunkX);
        buffer.writeInt(message.chunkZ);
    }

    public static RequestChunkClaimInfoPacket decode(FriendlyByteBuf buffer) {
        return new RequestChunkClaimInfoPacket(buffer.readInt(), buffer.readInt());
    }

    public static void handle(RequestChunkClaimInfoPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
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

            ChunkRuleRuntimeStore.ClaimInfo info = ChunkRuleRuntimeStore.getClaimInfo(player.level(), message.chunkX, message.chunkZ);
            APChunkManagerMod.NETWORK.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ChunkClaimInfoPacket(
                            message.chunkX,
                            message.chunkZ,
                            info.claimed(),
                            info.chunkName(),
                            info.teamName(),
                            info.roleName(),
                            info.ownerName()
                    )
            );
        });
        context.setPacketHandled(true);
    }
}
