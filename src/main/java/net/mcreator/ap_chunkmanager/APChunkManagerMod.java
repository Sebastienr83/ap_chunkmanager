package net.mcreator.ap_chunkmanager;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(APChunkManagerMod.MOD_ID)
public class APChunkManagerMod {
    public static final String MOD_ID = "ap_chunkmanager";
    private static final Logger LOGGER = LogUtils.getLogger();

    public APChunkManagerMod() {
        LOGGER.info("AP Chunkmanager initialized");
    }
}