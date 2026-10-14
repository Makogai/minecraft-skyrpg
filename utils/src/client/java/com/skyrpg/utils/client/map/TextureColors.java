package com.skyrpg.utils.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import com.skyrpg.utils.client.mixin.SpriteContentsAccess;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Each block's colour as seen from above: the average of its top texture (or, for plants and the like, of
 * whatever it shows), tinted the way the game tints it (grass, leaves, water-loving plants...). Worked out
 * once per block state and cached. Blocks whose texture can't be read fall back to vanilla's map colours.
 */
final class TextureColors {
    /** Average colour (RGB) and the tint index to apply (-1 for none); {@code rgb < 0} means "use the map colour". */
    private record Look(int rgb, int tint) {
    }

    private static final Look NONE = new Look(-1, -1);
    private static final Map<BlockState, Look> CACHE = new IdentityHashMap<>();

    private TextureColors() {
    }

    static void clear() {
        CACHE.clear();
    }

    /** The colour (RGB) of this block at this spot, or -1 to use its map colour. */
    static int color(ClientLevel level, BlockState state, BlockPos pos) {
        Look look = CACHE.computeIfAbsent(state, TextureColors::look);
        if (look.rgb() < 0) {
            return -1;
        }
        if (look.tint() < 0) {
            return look.rgb();
        }
        BlockTintSource source = Minecraft.getInstance().getBlockColors().getTintSource(state, look.tint());
        if (source == null) {
            return look.rgb();
        }
        return multiply(look.rgb(), source.colorInWorld(state, level, pos));
    }

    private static Look look(BlockState state) {
        try {
            BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
            List<BlockStateModelPart> parts = new ArrayList<>();
            model.collectParts(RandomSource.create(42L), parts);
            BakedQuad quad = firstQuad(parts, Direction.UP);
            if (quad == null) {
                quad = firstQuad(parts, null);
            }
            TextureAtlasSprite sprite = quad != null ? quad.materialInfo().sprite() : model.particleMaterial().sprite();
            int tint = quad != null ? quad.materialInfo().tintIndex() : -1;
            int rgb = average(sprite.contents());
            return rgb < 0 ? NONE : new Look(rgb, tint);
        } catch (RuntimeException e) {
            return NONE;
        }
    }

    private static BakedQuad firstQuad(List<BlockStateModelPart> parts, Direction side) {
        for (BlockStateModelPart part : parts) {
            List<BakedQuad> quads = part.getQuads(side);
            if (!quads.isEmpty()) {
                return quads.getFirst();
            }
        }
        return null;
    }

    /** The average colour of the sprite's first frame, weighted by opacity (-1 if it's nearly all see-through). */
    private static int average(SpriteContents contents) {
        NativeImage image = ((SpriteContentsAccess) contents).skyrpg$originalImage();
        int width = Math.min(contents.width(), image.getWidth());
        int height = Math.min(contents.height(), image.getHeight());
        long r = 0;
        long g = 0;
        long b = 0;
        long weight = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getPixel(x, y);
                int a = argb >>> 24;
                if (a == 0) {
                    continue;
                }
                r += (long) ((argb >> 16) & 0xFF) * a;
                g += (long) ((argb >> 8) & 0xFF) * a;
                b += (long) (argb & 0xFF) * a;
                weight += a;
            }
        }
        if (weight < (long) width * height * 255 / 10) {
            return -1;
        }
        return (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
    }

    private static int multiply(int rgb, int tint) {
        int r = ((rgb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((rgb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (rgb & 0xFF) * (tint & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }
}
