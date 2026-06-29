package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.client.ChunkManagerClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ChunkRuleCreateResultPacket(boolean success, String message) {
    public ChunkRuleCreateResultPacket {
        message = message == null ? "" : message;
    }

    public static void encode(ChunkRuleCreateResultPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.success);
        buffer.writeUtf(message.message, 256);
    }

    public static ChunkRuleCreateResultPacket decode(FriendlyByteBuf buffer) {
        return new ChunkRuleCreateResultPacket(buffer.readBoolean(), buffer.readUtf(256));
    }

    public static void handle(ChunkRuleCreateResultPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> {
            int color = message.success ? 0xFFA7E2A8 : 0xFFFF8D8D;
            String text = message.message.isBlank() ? (message.success ? "Chunk creation succeeded." : "Chunk creation failed.") : message.message;
            ChunkManagerClientState.setChunkCreateFeedback(text, color, 4500L);
        });
        context.setPacketHandled(true);
    }
}
