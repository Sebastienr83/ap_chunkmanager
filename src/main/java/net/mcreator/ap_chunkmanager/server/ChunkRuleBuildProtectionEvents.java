package net.mcreator.ap_chunkmanager.server;

import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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

        ChunkRuleRuntimeStore.BuildAccessResult access = ChunkRuleRuntimeStore.canPerform(serverPlayer, event.getPos(), ChunkRuleRuntimeStore.Permission.BREAK);
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

        ChunkRuleRuntimeStore.BuildAccessResult access = ChunkRuleRuntimeStore.canPerform(serverPlayer, event.getPos(), ChunkRuleRuntimeStore.Permission.BUILD);
        if (!access.allowed()) {
            event.setCanceled(true);
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(access.reason()), true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ChunkRuleRuntimeStore.Permission permission = event.getLevel().getBlockState(event.getPos()).getMenuProvider(event.getLevel(), event.getPos()) != null
                ? ChunkRuleRuntimeStore.Permission.OPEN_CONTAINER
                : ChunkRuleRuntimeStore.Permission.INTERACT_BLOCK;

        ChunkRuleRuntimeStore.BuildAccessResult access = ChunkRuleRuntimeStore.canPerform(serverPlayer, event.getPos(), permission);
        if (!access.allowed()) {
            event.setCanceled(true);
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(access.reason()), true);
        }
    }

    @SubscribeEvent
    public static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ChunkRuleRuntimeStore.BuildAccessResult access = ChunkRuleRuntimeStore.canPerform(
                serverPlayer,
                event.getTarget().blockPosition(),
                ChunkRuleRuntimeStore.Permission.INTERACT_ENTITY
        );
        if (!access.allowed()) {
            event.setCanceled(true);
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(access.reason()), true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ChunkRuleRuntimeStore.BuildAccessResult access = ChunkRuleRuntimeStore.canPerform(
                serverPlayer,
                event.getTarget().blockPosition(),
                ChunkRuleRuntimeStore.Permission.INTERACT_ENTITY
        );
        if (!access.allowed()) {
            event.setCanceled(true);
            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(access.reason()), true);
        }
    }
}
