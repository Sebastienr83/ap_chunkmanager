package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.client.ChunkManagerRuntimeEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MapTileDataPacket(int mapId, int centerX, int centerZ, byte scale, byte[] colors) {
    private static final int EXPECTED_COLOR_COUNT = 128 * 128;

    public static void encode(MapTileDataPacket message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.mapId);
        buffer.writeInt(message.centerX);
        buffer.writeInt(message.centerZ);
        buffer.writeByte(message.scale);
        buffer.writeByteArray(message.colors);
    }

    public static MapTileDataPacket decode(FriendlyByteBuf buffer) {
        return new MapTileDataPacket(
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readByte(),
                buffer.readByteArray(EXPECTED_COLOR_COUNT)
        );
    }

    public static void handle(MapTileDataPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ChunkManagerRuntimeEvents.applyServerTileData(mc, message.mapId, message.centerX, message.centerZ, message.scale, message.colors);
        });

        context.setPacketHandled(true);
    }
}
