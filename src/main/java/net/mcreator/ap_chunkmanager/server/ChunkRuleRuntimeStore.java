package net.mcreator.ap_chunkmanager.server;

import com.mojang.logging.LogUtils;
import net.mcreator.ap_chunkmanager.network.CreateChunkRulePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChunkRuleRuntimeStore {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_KEY = "ap_chunkmanager_chunk_rules";

    private ChunkRuleRuntimeStore() {
    }

    public static StoreFeedback storeRule(ServerPlayer player, CreateChunkRulePacket packet) {
        ValidationResult validation = validateClaimPacket(player, packet);
        if (!validation.isAllowed()) {
            player.displayClientMessage(validation.message(), true);
            return StoreFeedback.fail(validation.message().getString());
        }

        ChunkRuleSavedData data = getData(player.level());
        SaveResult result = data.storeRule(player, packet);
        if (result.storedChunks() > 0) {
            player.displayClientMessage(Component.literal("Chunk rules stored: " + result.storedChunks() + " chunks"), true);
        }
        if (!result.message().getString().isEmpty()) {
            player.displayClientMessage(result.message(), true);
        }

        if (result.storedChunks() > 0) {
            String suffix = result.message().getString().isEmpty() ? "" : " (" + result.message().getString() + ")";
            return StoreFeedback.success("Chunk creation succeeded: " + result.storedChunks() + " chunks" + suffix);
        }
        if (!result.message().getString().isEmpty()) {
            return StoreFeedback.fail(result.message().getString());
        }
        return StoreFeedback.fail("Chunk creation failed.");
    }

    public static BuildAccessResult canBuildAt(ServerPlayer player, BlockPos pos) {
        if (player.hasPermissions(2)) {
            return BuildAccessResult.permit();
        }

        ChunkRuleSavedData data = getData(player.level());
        StoredChunkRule rule = data.getRule(player.level(), new ChunkPos(pos));
        if (rule == null) {
            return BuildAccessResult.permit();
        }

        if (!rule.allowBuild()) {
            return BuildAccessResult.deny("Building is disabled in this claim.");
        }

        if (!isPlayerAllowedByTeamRules(player, rule)) {
            return BuildAccessResult.deny("Your team/role does not satisfy this claim rule.");
        }

        int minY = rule.faceY() - Math.max(0, rule.buildDepthBelowFace());
        int maxY = rule.faceY() + Math.max(0, rule.buildHeightAboveFace());
        if (pos.getY() < minY || pos.getY() > maxY) {
            return BuildAccessResult.deny("Y is outside limits: allowed from " + minY + " to " + maxY + ".");
        }

        return BuildAccessResult.permit();
    }

    public static List<StoredChunkRule> getRulesForDimension(Level level) {
        return getData(level).getRulesForDimension(level);
    }

    public static StoredChunkRule getRuleAt(Level level, int chunkX, int chunkZ) {
        return getData(level).getRule(level, new ChunkPos(chunkX, chunkZ));
    }

    public static boolean removeRuleAt(Level level, int chunkX, int chunkZ) {
        return getData(level).removeRule(level, chunkX, chunkZ);
    }

    public static int clearRulesForDimension(Level level) {
        return getData(level).clearDimension(level);
    }

    public static ClaimInfo getClaimInfo(Level level, int chunkX, int chunkZ) {
        StoredChunkRule rule = getRuleAt(level, chunkX, chunkZ);
        if (rule == null) {
            return ClaimInfo.none();
        }

        String roleName = rule.assignRoleToChunk() ? rule.roleName() : "None";
        return new ClaimInfo(true, rule.name(), rule.ownerTeamName(), roleName, rule.ownerName());
    }

    private static ValidationResult validateClaimPacket(ServerPlayer player, CreateChunkRulePacket packet) {
        int quota = Math.max(0, Math.min(packet.initialChunkQuota(), 4096));
        if (quota <= 0) {
            return ValidationResult.deny("Initial Chunk Quota must be greater than 0.");
        }
        if (packet.selectedChunks().isEmpty()) {
            return ValidationResult.deny("No chunks selected.");
        }
        if (packet.selectedChunks().size() > quota) {
            return ValidationResult.deny("More chunks selected than the quota allows.");
        }
        if (packet.requireTeam() && player.getTeam() == null) {
            return ValidationResult.deny("This rule requires a team.");
        }
        if (packet.assignRoleToChunk() && player.getTeam() == null) {
            return ValidationResult.deny("Assign @Role requires that you are in a team.");
        }
        if (packet.assignRoleToChunk() && packet.roleName().isBlank()) {
            return ValidationResult.deny("Select a role for this chunk.");
        }
        if (!isKnownResource(packet.rewardResource())) {
            return ValidationResult.deny("Unknown reward resource: " + packet.rewardResource());
        }
        if (!isKnownResource(packet.costResource())) {
            return ValidationResult.deny("Unknown cost resource: " + packet.costResource());
        }
        if (packet.rewardAmount() < 0 || packet.costAmount() < 0) {
            return ValidationResult.deny("Reward/cost amount must not be negative.");
        }
        return ValidationResult.permit();
    }

    private static boolean isKnownResource(String id) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(id == null ? "" : id.trim());
        if (resourceLocation == null) {
            return false;
        }
        return BuiltInRegistries.ITEM.containsKey(resourceLocation) || BuiltInRegistries.BLOCK.containsKey(resourceLocation);
    }

    private static boolean isPlayerAllowedByTeamRules(ServerPlayer player, StoredChunkRule rule) {
        if (player.getUUID().equals(rule.owner())) {
            return true;
        }

        String playerTeam = player.getTeam() != null ? player.getTeam().getName() : "";
        String ownerTeam = rule.ownerTeamName();

        if (rule.requireTeam() && playerTeam.isEmpty()) {
            return false;
        }
        if (rule.requireTeam() && !ownerTeam.isEmpty() && !ownerTeam.equals(playerTeam)) {
            return false;
        }
        if (rule.assignRoleToChunk()) {
            return !ownerTeam.isEmpty() && ownerTeam.equals(playerTeam);
        }
        return true;
    }

    private static ChunkRuleSavedData getData(Level level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                ChunkRuleSavedData::load,
                ChunkRuleSavedData::new,
                DATA_KEY
        );
    }

    private static String sanitizeName(String value) {
        if (value == null || value.isBlank()) {
            return "Unnamed Rule";
        }
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(64, trimmed.length()));
    }

    private static int computeChunkFaceY(Level level, ChunkPos chunkPos) {
        int sampleX = (chunkPos.x << 4) + 8;
        int sampleZ = (chunkPos.z << 4) + 8;
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ) - 1;
    }

    private static long packChunk(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    public record BuildAccessResult(boolean allowed, String reason) {
        static BuildAccessResult permit() {
            return new BuildAccessResult(true, "");
        }

        static BuildAccessResult deny(String reason) {
            return new BuildAccessResult(false, reason == null ? "Blocked by chunk rule." : reason);
        }
    }

    private record ValidationResult(boolean isAllowed, Component message) {
        static ValidationResult permit() {
            return new ValidationResult(true, Component.empty());
        }

        static ValidationResult deny(String message) {
            return new ValidationResult(false, Component.literal(message));
        }
    }

    private record SaveResult(int storedChunks, Component message) {
    }

    public record StoreFeedback(boolean success, String message) {
        static StoreFeedback success(String message) {
            return new StoreFeedback(true, message == null ? "" : message);
        }

        static StoreFeedback fail(String message) {
            return new StoreFeedback(false, message == null ? "" : message);
        }
    }

    private static final class ChunkRuleSavedData extends SavedData {
        private static final String KEY_DIMENSIONS = "dimensions";
        private static final String KEY_DIMENSION = "dimension";
        private static final String KEY_RULES = "rules";

        private final Map<String, Map<Long, StoredChunkRule>> rulesByDimension = new HashMap<>();

        static ChunkRuleSavedData load(CompoundTag tag) {
            ChunkRuleSavedData data = new ChunkRuleSavedData();
            ListTag dimensions = tag.getList(KEY_DIMENSIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < dimensions.size(); i++) {
                CompoundTag dimTag = dimensions.getCompound(i);
                String dimensionKey = dimTag.getString(KEY_DIMENSION);
                if (dimensionKey.isEmpty()) {
                    continue;
                }

                Map<Long, StoredChunkRule> rulesByChunk = new HashMap<>();
                ListTag rules = dimTag.getList(KEY_RULES, Tag.TAG_COMPOUND);
                for (int j = 0; j < rules.size(); j++) {
                    StoredChunkRule rule = StoredChunkRule.fromTag(rules.getCompound(j));
                    rulesByChunk.put(packChunk(rule.chunkX(), rule.chunkZ()), rule);
                }
                data.rulesByDimension.put(dimensionKey, rulesByChunk);
            }
            return data;
        }

        SaveResult storeRule(ServerPlayer player, CreateChunkRulePacket packet) {
            String dimensionKey = player.level().dimension().location().toString();
            Map<Long, StoredChunkRule> rulesByChunk = rulesByDimension.computeIfAbsent(dimensionKey, ignored -> new HashMap<>());
            String ownerTeam = player.getTeam() != null ? player.getTeam().getName() : "";

            int quota = Math.max(0, Math.min(packet.initialChunkQuota(), 4096));
            List<ChunkPos> chunks = packet.selectedChunks().stream().limit(quota).toList();
            int stored = 0;
            int skipped = 0;

            for (ChunkPos chunkPos : chunks) {
                long key = packChunk(chunkPos.x, chunkPos.z);
                if (rulesByChunk.containsKey(key)) {
                    skipped++;
                    continue;
                }

                int faceY = computeChunkFaceY(player.level(), chunkPos);
                StoredChunkRule rule = new StoredChunkRule(
                        player.getUUID(),
                    player.getGameProfile().getName(),
                        ownerTeam,
                        sanitizeName(packet.name()),
                        packet.assignRoleToChunk(),
                        packet.roleName().trim(),
                        packet.roleColorRgb(),
                        packet.requireTeam(),
                        Math.max(0, packet.buildHeightAboveFace()),
                        Math.max(0, packet.buildDepthBelowFace()),
                        quota,
                    packet.rewardResource(),
                    Math.max(0, packet.rewardAmount()),
                    packet.costResource(),
                    Math.max(0, packet.costAmount()),
                        packet.allowBuild(),
                        packet.chunkColorRgb(),
                        chunkPos.x,
                        chunkPos.z,
                        faceY,
                        System.currentTimeMillis()
                );
                rulesByChunk.put(key, rule);
                stored++;
            }

            if (stored > 0) {
                setDirty();
                LOGGER.info(
                        "[ap_chunkmanager] Stored {} chunk claims by {} in {} (skipped={})",
                        stored,
                        player.getGameProfile().getName(),
                        dimensionKey,
                        skipped
                );
            }

            if (stored == 0) {
                return new SaveResult(0, Component.literal("No new chunks stored (they may already be claimed)."));
            }
            if (skipped > 0) {
                return new SaveResult(stored, Component.literal(skipped + " chunks skipped (already claimed)."));
            }
            return new SaveResult(stored, Component.empty());
        }

        StoredChunkRule getRule(Level level, ChunkPos chunkPos) {
            String dimensionKey = level.dimension().location().toString();
            Map<Long, StoredChunkRule> rulesByChunk = rulesByDimension.get(dimensionKey);
            if (rulesByChunk == null) {
                return null;
            }
            return rulesByChunk.get(packChunk(chunkPos.x, chunkPos.z));
        }

        List<StoredChunkRule> getRulesForDimension(Level level) {
            String dimensionKey = level.dimension().location().toString();
            Map<Long, StoredChunkRule> rulesByChunk = rulesByDimension.getOrDefault(dimensionKey, Map.of());
            return List.copyOf(rulesByChunk.values());
        }

        boolean removeRule(Level level, int chunkX, int chunkZ) {
            String dimensionKey = level.dimension().location().toString();
            Map<Long, StoredChunkRule> rulesByChunk = rulesByDimension.get(dimensionKey);
            if (rulesByChunk == null) {
                return false;
            }

            StoredChunkRule removed = rulesByChunk.remove(packChunk(chunkX, chunkZ));
            if (removed != null) {
                if (rulesByChunk.isEmpty()) {
                    rulesByDimension.remove(dimensionKey);
                }
                setDirty();
                return true;
            }
            return false;
        }

        int clearDimension(Level level) {
            String dimensionKey = level.dimension().location().toString();
            Map<Long, StoredChunkRule> removed = rulesByDimension.remove(dimensionKey);
            int removedCount = removed == null ? 0 : removed.size();
            if (removedCount > 0) {
                setDirty();
            }
            return removedCount;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag dimensions = new ListTag();
            for (Map.Entry<String, Map<Long, StoredChunkRule>> entry : rulesByDimension.entrySet()) {
                CompoundTag dimTag = new CompoundTag();
                dimTag.putString(KEY_DIMENSION, entry.getKey());

                ListTag ruleList = new ListTag();
                for (StoredChunkRule rule : entry.getValue().values()) {
                    ruleList.add(rule.toTag());
                }
                dimTag.put(KEY_RULES, ruleList);
                dimensions.add(dimTag);
            }
            tag.put(KEY_DIMENSIONS, dimensions);
            return tag;
        }
    }

    public record StoredChunkRule(
            UUID owner,
            String ownerName,
            String ownerTeamName,
            String name,
            boolean assignRoleToChunk,
            String roleName,
            int roleColorRgb,
            boolean requireTeam,
            int buildHeightAboveFace,
            int buildDepthBelowFace,
            int initialChunkQuota,
            String rewardResource,
            int rewardAmount,
            String costResource,
            int costAmount,
            boolean allowBuild,
            int chunkColorRgb,
            int chunkX,
            int chunkZ,
            int faceY,
            long createdAtMs
    ) {
        private static final String KEY_OWNER = "owner";
        private static final String KEY_OWNER_NAME = "ownerName";
        private static final String KEY_OWNER_TEAM = "ownerTeam";
        private static final String KEY_NAME = "name";
        private static final String KEY_ASSIGN_ROLE = "assignRole";
        private static final String KEY_ROLE_NAME = "roleName";
        private static final String KEY_ROLE_COLOR = "roleColor";
        private static final String KEY_REQUIRE_TEAM = "requireTeam";
        private static final String KEY_BUILD_ABOVE = "buildAbove";
        private static final String KEY_BUILD_BELOW = "buildBelow";
        private static final String KEY_QUOTA = "quota";
        private static final String KEY_REWARD_RESOURCE = "rewardResource";
        private static final String KEY_REWARD_AMOUNT = "rewardAmount";
        private static final String KEY_COST_RESOURCE = "costResource";
        private static final String KEY_COST_AMOUNT = "costAmount";
        private static final String KEY_ALLOW_BUILD = "allowBuild";
        private static final String KEY_COLOR = "color";
        private static final String KEY_CHUNK_X = "chunkX";
        private static final String KEY_CHUNK_Z = "chunkZ";
        private static final String KEY_FACE_Y = "faceY";
        private static final String KEY_CREATED = "created";

        CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(KEY_OWNER, owner);
            tag.putString(KEY_OWNER_NAME, ownerName);
            tag.putString(KEY_OWNER_TEAM, ownerTeamName);
            tag.putString(KEY_NAME, name);
            tag.putBoolean(KEY_ASSIGN_ROLE, assignRoleToChunk);
            tag.putString(KEY_ROLE_NAME, roleName);
            tag.putInt(KEY_ROLE_COLOR, roleColorRgb);
            tag.putBoolean(KEY_REQUIRE_TEAM, requireTeam);
            tag.putInt(KEY_BUILD_ABOVE, buildHeightAboveFace);
            tag.putInt(KEY_BUILD_BELOW, buildDepthBelowFace);
            tag.putInt(KEY_QUOTA, initialChunkQuota);
            tag.putString(KEY_REWARD_RESOURCE, rewardResource);
            tag.putInt(KEY_REWARD_AMOUNT, rewardAmount);
            tag.putString(KEY_COST_RESOURCE, costResource);
            tag.putInt(KEY_COST_AMOUNT, costAmount);
            tag.putBoolean(KEY_ALLOW_BUILD, allowBuild);
            tag.putInt(KEY_COLOR, chunkColorRgb);
            tag.putInt(KEY_CHUNK_X, chunkX);
            tag.putInt(KEY_CHUNK_Z, chunkZ);
            tag.putInt(KEY_FACE_Y, faceY);
            tag.putLong(KEY_CREATED, createdAtMs);
            return tag;
        }

        static StoredChunkRule fromTag(CompoundTag tag) {
            return new StoredChunkRule(
                    tag.getUUID(KEY_OWNER),
                    tag.contains(KEY_OWNER_NAME) ? tag.getString(KEY_OWNER_NAME) : tag.getUUID(KEY_OWNER).toString(),
                    tag.getString(KEY_OWNER_TEAM),
                    tag.getString(KEY_NAME),
                    tag.getBoolean(KEY_ASSIGN_ROLE),
                    tag.contains(KEY_ROLE_NAME) ? tag.getString(KEY_ROLE_NAME) : "",
                    tag.contains(KEY_ROLE_COLOR) ? tag.getInt(KEY_ROLE_COLOR) : 0xFFFFFF,
                    tag.getBoolean(KEY_REQUIRE_TEAM),
                    tag.getInt(KEY_BUILD_ABOVE),
                    tag.getInt(KEY_BUILD_BELOW),
                    tag.getInt(KEY_QUOTA),
                    tag.contains(KEY_REWARD_RESOURCE) ? tag.getString(KEY_REWARD_RESOURCE) : "minecraft:air",
                    tag.contains(KEY_REWARD_AMOUNT) ? tag.getInt(KEY_REWARD_AMOUNT) : 0,
                    tag.contains(KEY_COST_RESOURCE) ? tag.getString(KEY_COST_RESOURCE) : "minecraft:air",
                    tag.contains(KEY_COST_AMOUNT) ? tag.getInt(KEY_COST_AMOUNT) : 0,
                    tag.getBoolean(KEY_ALLOW_BUILD),
                    tag.getInt(KEY_COLOR),
                    tag.getInt(KEY_CHUNK_X),
                    tag.getInt(KEY_CHUNK_Z),
                    tag.getInt(KEY_FACE_Y),
                    tag.getLong(KEY_CREATED)
            );
        }
    }

    public record ClaimInfo(boolean claimed, String chunkName, String teamName, String roleName, String ownerName) {
        public static ClaimInfo none() {
            return new ClaimInfo(false, "", "", "", "");
        }
    }
}
