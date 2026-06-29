package net.mcreator.ap_chunkmanager.server;

import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = APChunkManagerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChunkRuleBuildProtectionEvents {
    private ChunkRuleBuildProtectionEvents() {
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ChunkRuleRuntimeStore.BuildAccessResult access = ChunkRuleRuntimeStore.canBuildAt(serverPlayer, event.getPos());
        if (!access.allowed()) {
            event.setCanceled(true);
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(access.reason()), true);
        }
    }

    @SubscribeEvent
    public static void onPlaceBlock(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ChunkRuleRuntimeStore.BuildAccessResult access = ChunkRuleRuntimeStore.canBuildAt(serverPlayer, event.getPos());
        if (!access.allowed()) {
            event.setCanceled(true);
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(access.reason()), true);
        }
    }
}
