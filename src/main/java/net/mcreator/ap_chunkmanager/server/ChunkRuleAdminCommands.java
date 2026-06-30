package net.mcreator.ap_chunkmanager.server;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = APChunkManagerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChunkRuleAdminCommands {
    private static final int PAGE_SIZE = 8;
    private static final Map<UUID, LinkedHashSet<Long>> SELECTIONS = new ConcurrentHashMap<>();

    private ChunkRuleAdminCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("chunkmanager")
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
                        .then(Commands.literal("select")
                                .then(Commands.literal("here").executes(ChunkRuleAdminCommands::executeSelectHere))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                        .executes(ChunkRuleAdminCommands::executeSelectAdd))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                                        .executes(ChunkRuleAdminCommands::executeSelectRemove))))
                                .then(Commands.literal("clear").executes(ChunkRuleAdminCommands::executeSelectClear))
                                .then(Commands.literal("list").executes(ChunkRuleAdminCommands::executeSelectList)))
                        .then(Commands.literal("delete")
                                .then(Commands.literal("selected").executes(ChunkRuleAdminCommands::executeDeleteSelected)))
                        .then(Commands.literal("assignteam")
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .executes(ChunkRuleAdminCommands::executeAssignTeamSelected)))
        );
    }

    private static LinkedHashSet<Long> selectionFor(ServerPlayer player) {
        return SELECTIONS.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashSet<>());
    }

    private static long packChunk(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
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

        source.sendSuccess(() -> Component.literal("Claims in dimension " + source.getLevel().dimension().location() + " (page " + safePage + "/" + pageCount + ")"),
                false);

        for (int i = start; i < end; i++) {
            ChunkRuleRuntimeStore.StoredChunkRule rule = allRules.get(i);
            int minY = rule.faceY() - Math.max(0, rule.buildDepthBelowFace());
            int maxY = rule.faceY() + Math.max(0, rule.buildHeightAboveFace());
            String line = "- chunk(" + rule.chunkX() + "," + rule.chunkZ() + ") name='" + rule.name() + "' team='" + rule.ownerTeamName() + "' Y=" + minY + ".." + maxY;
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
        source.sendSuccess(() -> Component.literal("name='" + rule.name() + "', owner=" + rule.owner() + ", team='" + rule.ownerTeamName() + "'"), false);
        source.sendSuccess(() -> Component.literal("Y-limiet=" + minY + ".." + maxY + " (faceY=" + rule.faceY() + ")"), false);
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

    private static int executeSelectHere(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this as player."));
            return 0;
        }
        ChunkPos pos = new ChunkPos(player.blockPosition());
        selectionFor(player).add(packChunk(pos.x, pos.z));
        context.getSource().sendSuccess(() -> Component.literal("Selected chunk(" + pos.x + "," + pos.z + ")."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSelectAdd(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this as player."));
            return 0;
        }

        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        selectionFor(player).add(packChunk(chunkX, chunkZ));
        context.getSource().sendSuccess(() -> Component.literal("Selected chunk(" + chunkX + "," + chunkZ + ")."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSelectRemove(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this as player."));
            return 0;
        }

        int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
        int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
        selectionFor(player).remove(packChunk(chunkX, chunkZ));
        context.getSource().sendSuccess(() -> Component.literal("Deselected chunk(" + chunkX + "," + chunkZ + ")."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSelectClear(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this as player."));
            return 0;
        }

        selectionFor(player).clear();
        context.getSource().sendSuccess(() -> Component.literal("Selection cleared."), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeSelectList(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this as player."));
            return 0;
        }

        LinkedHashSet<Long> selected = selectionFor(player);
        context.getSource().sendSuccess(() -> Component.literal("Selected chunks: " + selected.size()), false);
        int shown = 0;
        for (long packed : selected) {
            if (shown >= 20) {
                break;
            }
            int x = unpackX(packed);
            int z = unpackZ(packed);
            context.getSource().sendSuccess(() -> Component.literal("- chunk(" + x + "," + z + ")"), false);
            shown++;
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int executeDeleteSelected(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this as player."));
            return 0;
        }

        List<ChunkPos> chunks = new ArrayList<>();
        for (long packed : selectionFor(player)) {
            chunks.add(new ChunkPos(unpackX(packed), unpackZ(packed)));
        }

        int removed = ChunkRuleRuntimeStore.removeRules(player.level(), chunks);
        context.getSource().sendSuccess(() -> Component.literal("Deleted selected claims: " + removed), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int executeAssignTeamSelected(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            context.getSource().sendFailure(Component.literal("Run this as player."));
            return 0;
        }

        String teamName = StringArgumentType.getString(context, "team");
        if (!TeamManagerRuntimeStore.teamExists(player.level(), teamName)) {
            context.getSource().sendFailure(Component.literal("Unknown team: " + teamName));
            return 0;
        }

        List<ChunkPos> chunks = new ArrayList<>();
        for (long packed : selectionFor(player)) {
            chunks.add(new ChunkPos(unpackX(packed), unpackZ(packed)));
        }

        int updated = ChunkRuleRuntimeStore.assignTeamToChunks(player.level(), chunks, teamName);
        context.getSource().sendSuccess(() -> Component.literal("Assigned team '" + teamName + "' to claims: " + updated), true);
        return Command.SINGLE_SUCCESS;
    }
}
