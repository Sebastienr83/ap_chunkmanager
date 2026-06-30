package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.mcreator.ap_chunkmanager.server.ChunkRuleRuntimeStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record DeleteChunkRulesPacket(List<ChunkPos> selectedChunks) {
    public DeleteChunkRulesPacket {
        selectedChunks = List.copyOf(selectedChunks == null ? List.of() : selectedChunks);
    }

    public static void encode(DeleteChunkRulesPacket message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.selectedChunks.size());
        for (ChunkPos chunkPos : message.selectedChunks) {
            buffer.writeInt(chunkPos.x);
            buffer.writeInt(chunkPos.z);
        }
    }

    public static DeleteChunkRulesPacket decode(FriendlyByteBuf buffer) {
        int chunkCount = Math.max(0, Math.min(8192, buffer.readVarInt()));
        List<ChunkPos> selectedChunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            selectedChunks.add(new ChunkPos(buffer.readInt(), buffer.readInt()));
        }
        return new DeleteChunkRulesPacket(selectedChunks);
    }

    public static void handle(DeleteChunkRulesPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
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

            int removed = ChunkRuleRuntimeStore.removeRules(player.level(), message.selectedChunks());
            APChunkManagerMod.NETWORK.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ChunkRuleCreateResultPacket(true, "Deleted chunk rules: " + removed)
            );

                for (ChunkPos chunkPos : message.selectedChunks()) {
                APChunkManagerMod.NETWORK.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ChunkClaimInfoPacket(
                        chunkPos.x,
                        chunkPos.z,
                        false,
                        "",
                        "",
                        "",
                        "",
                        false,
                        false,
                        false,
                        false,
                        false,
                        false
                    )
                );
                }
        });
        context.setPacketHandled(true);
    }
}
