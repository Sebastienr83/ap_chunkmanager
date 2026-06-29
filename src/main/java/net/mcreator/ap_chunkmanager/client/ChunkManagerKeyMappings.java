package net.mcreator.ap_chunkmanager.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = APChunkManagerMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ChunkManagerKeyMappings {
    public static final KeyMapping OPEN_FULLMAP_KEY = new KeyMapping(
            "key.ap_chunkmanager.open_fullmap",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.ap_chunkmanager"
    );
        public static final KeyMapping TOGGLE_DEBUG_LOGS_KEY = new KeyMapping(
            "key.ap_chunkmanager.toggle_debug_logs",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "key.categories.ap_chunkmanager"
        );

    private ChunkManagerKeyMappings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_FULLMAP_KEY);
        event.register(TOGGLE_DEBUG_LOGS_KEY);
    }
}
