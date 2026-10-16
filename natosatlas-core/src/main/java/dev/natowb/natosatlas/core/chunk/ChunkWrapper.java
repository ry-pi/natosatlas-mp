package dev.natowb.natosatlas.core.chunk;

import dev.natowb.natosatlas.client.platform.BlockAccess;

public abstract class ChunkWrapper {

    protected final Object chunk;
    protected final int worldHeight;

    public ChunkWrapper(Object chunk, int worldHeight) {
        this.chunk = chunk;
        this.worldHeight = worldHeight;
    }

    /** MP patch: true when (x,y,z) is a valid in-chunk coordinate for a world of the given height. */
    public static boolean inBounds(int x, int y, int z, int worldHeight) {
        return x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < worldHeight;
    }

    public int getWorldHeight() {
        return worldHeight;
    }

    /** MP patch: a chunk with no non-air block at all is treated as not received yet. */
    public boolean isAllAir() {
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                for (int y = worldHeight - 1; y >= 0; y--)
                    if (getBlockId(x, y, z) != 0) return false;
        return true;
    }

    public int getTopSolidBlockY(int x, int z) {

        int y = Math.min(worldHeight, 128) - 1;
        x &= 15;
        int z0 = z & 15;

        BlockAccess blocks = BlockAccess.get();

        for (; y > 0; --y) {
            int blockId = getBlockId(x, y, z0);
            if (blocks.isBlock(blockId, BlockAccess.BlockIdentifier.GLASS)) continue;
            if (blocks.blocksMovement(blockId) || blocks.isFluid(blockId)) return y + 1;
        }

        return -1;
    }

    public int computeFluidDepth(int x, int y, int z) {
        if (y < 0 || y >= worldHeight) return 0;
        x &= 15;
        z &= 15;
        int depth = 0;
        while (y > 0) {
            int id = getBlockId(x, y, z);
            if (!BlockAccess.get().isFluid(id)) break;
            depth++;
            y--;
        }
        return depth;
    }


    public abstract int getBlockId(int x, int y, int z);

    public abstract int getBlockMeta(int x, int y, int z);

    public abstract int getBlockLight(int x, int y, int z);

    public abstract int getSkyLight(int x, int y, int z);

}
