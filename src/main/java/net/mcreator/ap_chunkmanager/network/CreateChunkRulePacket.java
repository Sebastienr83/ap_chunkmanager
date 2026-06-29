package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.server.ChunkRuleRuntimeStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record CreateChunkRulePacket(
        String name,
        boolean assignRoleToChunk,
        boolean requireTeam,
        int buildHeightAboveFace,
        int buildDepthBelowFace,
        int initialChunkQuota,
    String rewardResource,
    int rewardAmount,
    String costResource,
    int costAmount,
        boolean allowBuild,
        int chunkColorRgb,
        List<ChunkPos> selectedChunks
) {
    public CreateChunkRulePacket {
        name = name == null ? "" : name;
    rewardResource = rewardResource == null ? "minecraft:air" : rewardResource;
    costResource = costResource == null ? "minecraft:air" : costResource;
        selectedChunks = List.copyOf(selectedChunks == null ? List.of() : selectedChunks);
    }

    public static void encode(CreateChunkRulePacket message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.name, 64);
        buffer.writeBoolean(message.assignRoleToChunk);
        buffer.writeBoolean(message.requireTeam);
        buffer.writeVarInt(message.buildHeightAboveFace);
        buffer.writeVarInt(message.buildDepthBelowFace);
        buffer.writeVarInt(message.initialChunkQuota);
        buffer.writeUtf(message.rewardResource, 128);
        buffer.writeVarInt(message.rewardAmount);
        buffer.writeUtf(message.costResource, 128);
        buffer.writeVarInt(message.costAmount);
        buffer.writeBoolean(message.allowBuild);
        buffer.writeInt(message.chunkColorRgb);

        buffer.writeVarInt(message.selectedChunks.size());
        for (ChunkPos chunkPos : message.selectedChunks) {
            buffer.writeInt(chunkPos.x);
            buffer.writeInt(chunkPos.z);
        }
    }

    public static CreateChunkRulePacket decode(FriendlyByteBuf buffer) {
        String name = buffer.readUtf(64);
        boolean assignRoleToChunk = buffer.readBoolean();
        boolean requireTeam = buffer.readBoolean();
        int buildHeightAboveFace = buffer.readVarInt();
        int buildDepthBelowFace = buffer.readVarInt();
        int initialChunkQuota = buffer.readVarInt();
        String rewardResource = buffer.readUtf(128);
        int rewardAmount = buffer.readVarInt();
        String costResource = buffer.readUtf(128);
        int costAmount = buffer.readVarInt();
        boolean allowBuild = buffer.readBoolean();
        int chunkColorRgb = buffer.readInt();

        int chunkCount = Math.max(0, Math.min(8192, buffer.readVarInt()));
        List<ChunkPos> selectedChunks = new ArrayList<>(chunkCount);
        for (int i = 0; i < chunkCount; i++) {
            selectedChunks.add(new ChunkPos(buffer.readInt(), buffer.readInt()));
        }

        return new CreateChunkRulePacket(
                name,
                assignRoleToChunk,
                requireTeam,
                buildHeightAboveFace,
                buildDepthBelowFace,
                initialChunkQuota,
                rewardResource,
                rewardAmount,
                costResource,
                costAmount,
                allowBuild,
                chunkColorRgb,
                selectedChunks
        );
    }

    public static void handle(CreateChunkRulePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
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

            ChunkRuleRuntimeStore.storeRule(player, message);
        });
        context.setPacketHandled(true);
    }
}
