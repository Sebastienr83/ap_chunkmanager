package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.client.ChunkRuleManagerClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record ChunkRuleListSyncPacket(
        boolean success,
        String statusMessage,
        List<ChunkRuleEntry> entries
) {
    public ChunkRuleListSyncPacket {
        statusMessage = statusMessage == null ? "" : statusMessage;
        entries = List.copyOf(entries == null ? List.of() : entries);
    }

    public static void encode(ChunkRuleListSyncPacket message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.success);
        buffer.writeUtf(message.statusMessage, 256);
        buffer.writeVarInt(message.entries.size());
        for (ChunkRuleEntry entry : message.entries) {
            buffer.writeInt(entry.chunkX);
            buffer.writeInt(entry.chunkZ);
            buffer.writeUtf(entry.chunkName, 128);
            buffer.writeUtf(entry.teamName, 128);
            buffer.writeUtf(entry.roleName, 128);
            buffer.writeUtf(entry.ownerName, 128);
            buffer.writeBoolean(entry.everyoneCanBuild);
            buffer.writeBoolean(entry.everyoneCanBreak);
            buffer.writeBoolean(entry.everyoneCanInteractBlocks);
            buffer.writeBoolean(entry.everyoneCanInteractEntities);
            buffer.writeBoolean(entry.everyoneCanOpenContainers);
        }
    }

    public static ChunkRuleListSyncPacket decode(FriendlyByteBuf buffer) {
        boolean success = buffer.readBoolean();
        String status = buffer.readUtf(256);
        int count = Math.max(0, Math.min(16384, buffer.readVarInt()));
        List<ChunkRuleEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new ChunkRuleEntry(
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readUtf(128),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            ));
        }

        return new ChunkRuleListSyncPacket(success, status, entries);
    }

    public static void handle(ChunkRuleListSyncPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        if (context.getDirection() != NetworkDirection.PLAY_TO_CLIENT) {
            context.setPacketHandled(true);
            return;
        }

        context.enqueueWork(() -> ChunkRuleManagerClientState.applySync(message));
        context.setPacketHandled(true);
    }

    public record ChunkRuleEntry(
            int chunkX,
            int chunkZ,
            String chunkName,
            String teamName,
            String roleName,
            String ownerName,
            boolean everyoneCanBuild,
            boolean everyoneCanBreak,
            boolean everyoneCanInteractBlocks,
            boolean everyoneCanInteractEntities,
            boolean everyoneCanOpenContainers
    ) {
        public ChunkRuleEntry {
            chunkName = chunkName == null ? "" : chunkName;
            teamName = teamName == null ? "" : teamName;
            roleName = roleName == null ? "" : roleName;
            ownerName = ownerName == null ? "" : ownerName;
        }
    }
}
