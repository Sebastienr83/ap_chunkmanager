package net.mcreator.ap_chunkmanager.network;

import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.mcreator.ap_chunkmanager.server.ChunkRuleRuntimeStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.function.Supplier;

public record RequestChunkRuleListPacket() {
    public static void encode(RequestChunkRuleListPacket message, FriendlyByteBuf buffer) {
    }

    public static RequestChunkRuleListPacket decode(FriendlyByteBuf buffer) {
        return new RequestChunkRuleListPacket();
    }

    public static void handle(RequestChunkRuleListPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
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

            List<ChunkRuleListSyncPacket.ChunkRuleEntry> entries = ChunkRuleRuntimeStore.getRulesForDimension(player.level()).stream()
                    .map(rule -> {
                        ChunkRuleRuntimeStore.RolePermissions everyone = rule.rolePermissions().getOrDefault(
                                "@everyone",
                            new ChunkRuleRuntimeStore.RolePermissions(
                                rule.allowBuild(),
                                rule.allowBuild(),
                                rule.allowBuild(),
                                rule.allowBuild(),
                                rule.allowBuild()
                            )
                        );
                        return new ChunkRuleListSyncPacket.ChunkRuleEntry(
                                rule.chunkX(),
                                rule.chunkZ(),
                                rule.name(),
                                rule.ownerTeamName(),
                                rule.roleName(),
                                rule.ownerName(),
                                everyone.canBuild(),
                                everyone.canBreak(),
                                everyone.canInteractBlocks(),
                                everyone.canInteractEntities(),
                                everyone.canOpenContainers()
                        );
                    })
                    .toList();

            APChunkManagerMod.NETWORK.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ChunkRuleListSyncPacket(true, "Loaded " + entries.size() + " chunk rules", entries)
            );
        });

        context.setPacketHandled(true);
    }
}
