package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.client.ChunkManagerRuntimeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ChunkClaimInfoPacket(
        int chunkX,
        int chunkZ,
        boolean claimed,
        String chunkName,
        String teamName,
        String roleName,
        String ownerName
) {
    public static void encode(ChunkClaimInfoPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.chunkX);
        buffer.writeInt(message.chunkZ);
        buffer.writeBoolean(message.claimed);
        buffer.writeUtf(message.chunkName, 128);
        buffer.writeUtf(message.teamName, 128);
        buffer.writeUtf(message.roleName, 128);
        buffer.writeUtf(message.ownerName, 128);
    }

    public static ChunkClaimInfoPacket decode(FriendlyByteBuf buffer) {
        return new ChunkClaimInfoPacket(
                buffer.readInt(),
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(128),
                buffer.readUtf(128)
        );
    }

    public static void handle(ChunkClaimInfoPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ChunkManagerRuntimeEvents.applyServerChunkClaimInfo(
                    message.chunkX,
                    message.chunkZ,
                    message.claimed,
                    message.chunkName,
                    message.teamName,
                    message.roleName,
                    message.ownerName
            );
        });
        context.setPacketHandled(true);
    }
}
