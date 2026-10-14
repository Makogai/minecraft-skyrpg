package com.skyrpg.utils.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * One square of the map, {@link #SIZE} blocks a side at one pixel per block: its pixels, the texture they're
 * uploaded to, and what has changed since the last upload and the last save. Unmapped pixels are transparent.
 */
final class MapTile {
    static final int SIZE = 256;

    final int rx;
    final int rz;
    final Identifier id;
    final NativeImage image;
    final DynamicTexture texture;
    /** Pixels changed since the last upload. */
    boolean dirty;
    /** Pixels changed since the last save. */
    boolean unsaved;
    long lastUsed;

    MapTile(int rx, int rz, Identifier id, NativeImage image) {
        this.rx = rx;
        this.rz = rz;
        this.id = id;
        this.image = image;
        this.texture = new DynamicTexture(id::toString, image);
    }

    void set(int x, int z, int argb) {
        if (image.getPixel(x, z) != argb) {
            image.setPixel(x, z, argb);
            dirty = true;
            unsaved = true;
        }
    }
}
