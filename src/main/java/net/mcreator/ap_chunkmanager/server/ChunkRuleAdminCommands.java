package net.mcreator.ap_chunkmanager.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.mcreator.ap_chunkmanager.APChunkManagerMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = APChunkManagerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChunkRuleAdminCommands {
    private static final int PAGE_SIZE = 8;

    private ChunkRuleAdminCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("apchunk")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("rules")
                                .then(Commands.literal("list")
                                        .executes(context -> executeList(context, 1))
                                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                                .executes(context -> executeList(context, IntegerArgumentType.getInteger(context, "page")))))
                                .then(Commands.literal("info")
                                        .executes(ChunkRuleAdminCommands::executeInfoAtCurrentChunk)
                                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                        .executes(ChunkRuleAdminCommands::executeInfoAtCoordinates))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                        .executes(ChunkRuleAdminCommands::executeRemoveAtCoordinates))))
                                .then(Commands.literal("remove_here")
                                        .executes(ChunkRuleAdminCommands::executeRemoveAtCurrentChunk))
                                .then(Commands.literal("clear_dimension")
                                        .executes(ChunkRuleAdminCommands::executeClearDimension)))
        );
    }

    private static int executeList(CommandContext<CommandSourceStack> context, int page) {
        CommandSourceStack source = context.getSource();
        List<ChunkRuleRuntimeStore.StoredChunkRule> allRules = ChunkRuleRuntimeStore.getRulesForDimension(source.getLevel())
                .stream()
                .sorted(Comparator.comparingLong(ChunkRuleRuntimeStore.StoredChunkRule::createdAtMs).reversed())
                .toList();

        if (allRules.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No claims found in this dimension."), false);
            return Command.SINGLE_SUCCESS;
        }

        int pageCount = Math.max(1, (int) Math.ceil(allRules.size() / (double) PAGE_SIZE));
        int safePage = Math.min(Math.max(1, page), pageCount);
        int start = (safePage - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, allRules.size());

        source.sendSuccess(
            () -> Component.literal("Claims in dimension " + source.getLevel().dimension().location() + " (page " + safePage + "/" + pageCount + ")"),
                false
        );

        for (int i = start; i < end; i++) {
            ChunkRuleRuntimeStore.StoredChunkRule rule = allRules.get(i);
            int minY = rule.faceY() - Math.max(0, rule.buildDepthBelowFace());
            int maxY = rule.faceY() + Math.max(0, rule.buildHeightAboveFace());
            String line = "- chunk(" + rule.chunkX() + "," + rule.chunkZ() + ") name='" + rule.name() + "' allowBuild=" + rule.allowBuild()
                    + " requireTeam=" + rule.requireTeam() + " ownerTeam='" + rule.ownerTeamName() + "' Y=" + minY + ".." + maxY;
            source.sendSuccess(() -> Component.literal(line), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int executeInfoAtCurrentChunk(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Use this variant as a player, or provide chunk coordinates."));
            return 0;
        }

        ChunkPos pos = new ChunkPos(player.blockPosition());
        return executeInfo(source, pos.x, pos.z);
    }

    private static int executeInfoAtCoordinates(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        return executeInfo(source, chunkX, chunkZ);
    }

    private static int executeInfo(CommandSourceStack source, int chunkX, int chunkZ) {
        ChunkRuleRuntimeStore.StoredChunkRule rule = ChunkRuleRuntimeStore.getRuleAt(source.getLevel(), chunkX, chunkZ);
        if (rule == null) {
            source.sendFailure(Component.literal("No claim found at chunk(" + chunkX + "," + chunkZ + ")."));
            return 0;
        }

        int minY = rule.faceY() - Math.max(0, rule.buildDepthBelowFace());
        int maxY = rule.faceY() + Math.max(0, rule.buildHeightAboveFace());

        source.sendSuccess(() -> Component.literal("Claim info for chunk(" + chunkX + "," + chunkZ + ")"), false);
        source.sendSuccess(() -> Component.literal("name='" + rule.name() + "', owner=" + rule.owner() + ", ownerTeam='" + rule.ownerTeamName() + "'"), false);
        source.sendSuccess(() -> Component.literal("allowBuild=" + rule.allowBuild() + ", requireTeam=" + rule.requireTeam() + ", assignRole=" + rule.assignRoleToChunk()), false);
        source.sendSuccess(() -> Component.literal("Y-limiet=" + minY + ".." + maxY + " (faceY=" + rule.faceY() + ")"), false);
        source.sendSuccess(() -> Component.literal("reward=" + rule.rewardResource() + " x" + rule.rewardAmount() + ", cost=" + rule.costResource() + " x" + rule.costAmount()), false);
        source.sendSuccess(() -> Component.literal("color=#" + String.format("%06X", rule.chunkColorRgb() & 0xFFFFFF)), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeRemoveAtCoordinates(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        return executeRemove(source, chunkX, chunkZ);
    }

    private static int executeRemoveAtCurrentChunk(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Use this variant as a player, or provide chunk coordinates."));
            return 0;
        }

        ChunkPos pos = new ChunkPos(player.blockPosition());
        return executeRemove(source, pos.x, pos.z);
    }

    private static int executeRemove(CommandSourceStack source, int chunkX, int chunkZ) {
        boolean removed = ChunkRuleRuntimeStore.removeRuleAt(source.getLevel(), chunkX, chunkZ);
        if (!removed) {
            source.sendFailure(Component.literal("No claim found to remove at chunk(" + chunkX + "," + chunkZ + ")."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Claim removed at chunk(" + chunkX + "," + chunkZ + ")."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeClearDimension(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int removedCount = ChunkRuleRuntimeStore.clearRulesForDimension(source.getLevel());
        source.sendSuccess(() -> Component.literal("Claims removed in dimension: " + removedCount), true);
        return Command.SINGLE_SUCCESS;
    }
}
