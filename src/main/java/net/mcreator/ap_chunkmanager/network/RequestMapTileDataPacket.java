package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.mcreator.ap_chunkmanager.map.ChunkManagerMapItemFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.function.Supplier;

public record RequestMapTileDataPacket(double worldX, double worldZ, byte scale, int mapId) {
    public static void encode(RequestMapTileDataPacket message, FriendlyByteBuf buffer) {
        buffer.writeDouble(message.worldX);
        buffer.writeDouble(message.worldZ);
        buffer.writeByte(message.scale);
        buffer.writeInt(message.mapId);
    }

    public static RequestMapTileDataPacket decode(FriendlyByteBuf buffer) {
        return new RequestMapTileDataPacket(buffer.readDouble(), buffer.readDouble(), buffer.readByte(), buffer.readInt());
    }

    public static void handle(RequestMapTileDataPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
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

            int blockSize = 128 * (1 << message.scale);
            int gridX = Math.floorDiv((int) Math.floor(message.worldX), blockSize);
            int gridZ = Math.floorDiv((int) Math.floor(message.worldZ), blockSize);
            int centerX = (gridX * blockSize) + (blockSize / 2);
            int centerZ = (gridZ * blockSize) + (blockSize / 2);

            MapItemSavedData tileData = ChunkManagerMapItemFactory.createMapData(player.level(), centerX, centerZ, message.scale);
            if (tileData == null) {
                return;
            }

            byte[] colors = Arrays.copyOf(tileData.colors, tileData.colors.length);
            APChunkManagerMod.NETWORK.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new MapTileDataPacket(message.mapId, centerX, centerZ, message.scale, colors)
            );
        });
        context.setPacketHandled(true);
    }
}
