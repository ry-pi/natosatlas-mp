package dev.natowb.natosatlas.stationapi;

import dev.natowb.natosatlas.core.mapper.ColorMapper;
import dev.natowb.natosatlas.client.platform.BlockAccess;
import net.minecraft.block.Block;

public class STBlockAccess extends BlockAccess {


    public STBlockAccess() {
        registerBlockIdentifier(BlockIdentifier.GLASS, Block.GLASS.id);
        registerBlockIdentifier(BlockIdentifier.GRASS, Block.GRASS_BLOCK.id);
        registerBlockIdentifier(BlockIdentifier.SNOW, Block.SNOW.id);
        registerBlockIdentifier(BlockIdentifier.LEAVES, Block.LEAVES.id);
    }

    @Override
    public int getColor(int blockId, int blockMeta) {

        int overrideColor = ColorMapper.getOverrideColor(Block.BLOCKS[blockId].getTranslationKey());

        if (overrideColor != -1) {
            return overrideColor;
        }

        if (blockId == Block.WOOL.id) {
            return ColorMapper.getWoolColor(blockMeta);
        }

        if (Block.BLOCKS[blockId].material == null) {
            return 0xFF800080;
        }
        return Block.BLOCKS[blockId].material.mapColor.color;
    }

    // mp3: true-colour sampling from the active texture pack's terrain.png
    private static final dev.natowb.natosatlas.core.chunk.TrueColorCache TRUE_COLORS = new dev.natowb.natosatlas.core.chunk.TrueColorCache();
    private static Object loadedPack;
    private static boolean atlasFailed;

    private static synchronized void ensureAtlas() {
        net.minecraft.client.Minecraft mc = (net.minecraft.client.Minecraft) net.fabricmc.loader.api.FabricLoader.getInstance().getGameInstance();
        if (mc == null || mc.texturePacks == null) return;
        Object pack = mc.texturePacks.selected;
        if (pack == loadedPack && (TRUE_COLORS.hasAtlas() || atlasFailed)) return;
        loadedPack = pack;
        atlasFailed = true;
        try (java.io.InputStream in = mc.texturePacks.selected.getResource("/terrain.png")) {
            if (in == null) return;
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            if (img == null) return;
            int w = img.getWidth(), h = img.getHeight();
            TRUE_COLORS.setAtlas(img.getRGB(0, 0, w, h, null, 0, w), w, h);
            atlasFailed = false;
        } catch (Throwable t) {
            dev.natowb.natosatlas.core.util.LogUtil.warn("[NatosAtlas] true colour: could not read terrain.png: {}", t);
        }
    }

    @Override
    public int getTrueColor(int blockId, int blockMeta) {
        if (blockId <= 0 || blockId >= Block.BLOCKS.length || Block.BLOCKS[blockId] == null) return -1;
        ensureAtlas();
        if (!TRUE_COLORS.hasAtlas()) return -1;
        return TRUE_COLORS.get(blockId, blockMeta, () -> {
            try {
                return TRUE_COLORS.averageTile(Block.BLOCKS[blockId].getTexture(1, blockMeta));
            } catch (Throwable t) {
                return -1;
            }
        });
    }

    @Override
    public boolean isFluid(int blockId) {
        Block block = Block.BLOCKS[blockId];
        if (block == null) {
            return false;
        }

        if (block.material == null) {
            return false;
        }

        return block.material.isFluid();
    }

    @Override
    public boolean blocksMovement(int blockId) {
        Block block = Block.BLOCKS[blockId];
        if (block == null) {
            return false;
        }

        if (block.material == null) {
            return false;
        }

        return block.material.blocksMovement();
    }

}
