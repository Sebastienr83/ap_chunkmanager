package net.mcreator.ap_chunkmanager.map;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public final class ChunkManagerMapItemFactory {
    private ChunkManagerMapItemFactory() {
    }

    public static ItemStack createMapItem(Level level, double worldX, double worldZ, byte scale) {
        if (level == null) {
            return ItemStack.EMPTY;
        }

        int blockSize = 128 * (1 << scale);
        int gridX = Math.floorDiv(Mth.floor(worldX), blockSize);
        int gridZ = Math.floorDiv(Mth.floor(worldZ), blockSize);
        int centerX = (gridX * blockSize) + (blockSize / 2);
        int centerZ = (gridZ * blockSize) + (blockSize / 2);

        ItemStack mapItem = MapItem.create(level, centerX, centerZ, scale, true, true);
        MapItemSavedData mapData = MapItem.getSavedData(mapItem, level);
        if (mapData == null) {
            return ItemStack.EMPTY;
        }

        populateMap(level, mapData);
        mapData.setDirty();
        return mapItem;
    }

    public static void populateMap(Level level, MapItemSavedData mapData) {
        if (level == null || mapData == null) {
            return;
        }

        int scaleMultiplier = 1 << mapData.scale;
        int startX = mapData.centerX - 64 * scaleMultiplier;
        int startZ = mapData.centerZ - 64 * scaleMultiplier;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int z = 0; z < 128; z++) {
            for (int x = 0; x < 128; x++) {
                int sampleX = startX + (x * scaleMultiplier) + (scaleMultiplier / 2);
                int sampleZ = startZ + (z * scaleMultiplier) + (scaleMultiplier / 2);

                if (!level.hasChunk(sampleX >> 4, sampleZ >> 4)) {
                    continue;
                }

                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ) - 1;
                mutablePos.set(sampleX, y, sampleZ);
                BlockState state = level.getBlockState(mutablePos);
                MapColor mapColor = state.getMapColor(level, mutablePos);

                if (mapColor == null || mapColor == MapColor.NONE) {
                    continue;
                }

                mapData.updateColor(x, z, (byte) (mapColor.id * 4 + 2));
            }
        }
    }
}