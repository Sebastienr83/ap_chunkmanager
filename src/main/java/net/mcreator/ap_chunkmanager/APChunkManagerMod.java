package net.mcreator.ap_chunkmanager;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(APChunkManagerMod.MOD_ID)
public class APChunkManagerMod {
    public static final String MOD_ID = "ap_chunkmanager";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NETWORK_PROTOCOL = "1";
    public static final SimpleChannel NETWORK = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MOD_ID, "main"),
            () -> NETWORK_PROTOCOL,
            NETWORK_PROTOCOL::equals,
            NETWORK_PROTOCOL::equals
    );
    private static int nextPacketId;

    public APChunkManagerMod() {
        registerNetworkPackets();
        LOGGER.info("AP Chunkmanager initialized");
    }

    private static void registerNetworkPackets() {
        NETWORK.registerMessage(
                nextPacketId++,
                net.mcreator.ap_chunkmanager.network.CopyChunkMapSectionPacket.class,
                net.mcreator.ap_chunkmanager.network.CopyChunkMapSectionPacket::encode,
                net.mcreator.ap_chunkmanager.network.CopyChunkMapSectionPacket::decode,
                net.mcreator.ap_chunkmanager.network.CopyChunkMapSectionPacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.RequestMapTileDataPacket.class,
            net.mcreator.ap_chunkmanager.network.RequestMapTileDataPacket::encode,
            net.mcreator.ap_chunkmanager.network.RequestMapTileDataPacket::decode,
            net.mcreator.ap_chunkmanager.network.RequestMapTileDataPacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.MapTileDataPacket.class,
            net.mcreator.ap_chunkmanager.network.MapTileDataPacket::encode,
            net.mcreator.ap_chunkmanager.network.MapTileDataPacket::decode,
            net.mcreator.ap_chunkmanager.network.MapTileDataPacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.CreateChunkRulePacket.class,
            net.mcreator.ap_chunkmanager.network.CreateChunkRulePacket::encode,
            net.mcreator.ap_chunkmanager.network.CreateChunkRulePacket::decode,
            net.mcreator.ap_chunkmanager.network.CreateChunkRulePacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.RequestChunkClaimInfoPacket.class,
            net.mcreator.ap_chunkmanager.network.RequestChunkClaimInfoPacket::encode,
            net.mcreator.ap_chunkmanager.network.RequestChunkClaimInfoPacket::decode,
            net.mcreator.ap_chunkmanager.network.RequestChunkClaimInfoPacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.ChunkClaimInfoPacket.class,
            net.mcreator.ap_chunkmanager.network.ChunkClaimInfoPacket::encode,
            net.mcreator.ap_chunkmanager.network.ChunkClaimInfoPacket::decode,
            net.mcreator.ap_chunkmanager.network.ChunkClaimInfoPacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.TeamManagerActionPacket.class,
            net.mcreator.ap_chunkmanager.network.TeamManagerActionPacket::encode,
            net.mcreator.ap_chunkmanager.network.TeamManagerActionPacket::decode,
            net.mcreator.ap_chunkmanager.network.TeamManagerActionPacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.TeamManagerSyncPacket.class,
            net.mcreator.ap_chunkmanager.network.TeamManagerSyncPacket::encode,
            net.mcreator.ap_chunkmanager.network.TeamManagerSyncPacket::decode,
            net.mcreator.ap_chunkmanager.network.TeamManagerSyncPacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.ChunkRuleCreateResultPacket.class,
            net.mcreator.ap_chunkmanager.network.ChunkRuleCreateResultPacket::encode,
            net.mcreator.ap_chunkmanager.network.ChunkRuleCreateResultPacket::decode,
            net.mcreator.ap_chunkmanager.network.ChunkRuleCreateResultPacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.DeleteChunkRulesPacket.class,
            net.mcreator.ap_chunkmanager.network.DeleteChunkRulesPacket::encode,
            net.mcreator.ap_chunkmanager.network.DeleteChunkRulesPacket::decode,
            net.mcreator.ap_chunkmanager.network.DeleteChunkRulesPacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.RequestChunkRuleListPacket.class,
            net.mcreator.ap_chunkmanager.network.RequestChunkRuleListPacket::encode,
            net.mcreator.ap_chunkmanager.network.RequestChunkRuleListPacket::decode,
            net.mcreator.ap_chunkmanager.network.RequestChunkRuleListPacket::handle
        );
        NETWORK.registerMessage(
            nextPacketId++,
            net.mcreator.ap_chunkmanager.network.ChunkRuleListSyncPacket.class,
            net.mcreator.ap_chunkmanager.network.ChunkRuleListSyncPacket::encode,
            net.mcreator.ap_chunkmanager.network.ChunkRuleListSyncPacket::decode,
            net.mcreator.ap_chunkmanager.network.ChunkRuleListSyncPacket::handle
        );
    }
}