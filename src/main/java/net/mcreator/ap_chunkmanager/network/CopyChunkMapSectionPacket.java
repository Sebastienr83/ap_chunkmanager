package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.map.ChunkManagerMapItemFactory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record CopyChunkMapSectionPacket(double worldX, double worldZ, byte scale) {
    public static void encode(CopyChunkMapSectionPacket message, FriendlyByteBuf buffer) {
        buffer.writeDouble(message.worldX);
        buffer.writeDouble(message.worldZ);
        buffer.writeByte(message.scale);
    }

    public static CopyChunkMapSectionPacket decode(FriendlyByteBuf buffer) {
        return new CopyChunkMapSectionPacket(buffer.readDouble(), buffer.readDouble(), buffer.readByte());
    }

    public static void handle(CopyChunkMapSectionPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }

            ItemStack mapItem = ChunkManagerMapItemFactory.createMapItem(player.level(), message.worldX, message.worldZ, message.scale);
            if (mapItem.isEmpty()) {
                return;
            }

            if (!player.getInventory().add(mapItem)) {
                player.drop(mapItem, false);
            }
        });
        context.setPacketHandled(true);
    }
}