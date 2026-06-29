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
    }
}