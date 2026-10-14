package com.skyrpg.world;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Where one danger region is: a blob around a centre, its edge wobbling with three sine waves so no two look alike.
 * Shared by the server (levels, spawns, terrain) and the client (the red wash on the map), which gets these from
 * the server and never needs the world seed.
 */
public record RegionShape(String type, String name, int color, int danger, int levelBonus, int cx, int cz, int radius, float p1, float p2,
                          float p3, int fog, int fogDensity) {
    public static final StreamCodec<ByteBuf, RegionShape> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RegionShape decode(ByteBuf buf) {
            return new RegionShape(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readInt(), buf.readByte(),
                    buf.readShort(), buf.readInt(), buf.readInt(), buf.readShort(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readInt(), buf.readByte());
        }

        @Override
        public void encode(ByteBuf buf, RegionShape shape) {
            ByteBufCodecs.STRING_UTF8.encode(buf, shape.type());
            ByteBufCodecs.STRING_UTF8.encode(buf, shape.name());
            buf.writeInt(shape.color());
            buf.writeByte(shape.danger());
            buf.writeShort(shape.levelBonus());
            buf.writeInt(shape.cx());
            buf.writeInt(shape.cz());
            buf.writeShort(shape.radius());
            buf.writeFloat(shape.p1());
            buf.writeFloat(shape.p2());
            buf.writeFloat(shape.p3());
            buf.writeInt(shape.fog());
            buf.writeByte(shape.fogDensity());
        }
    };

    /** The key that tells two regions of the same type apart ("blightlands@120,-340"). */
    public String key() {
        return type + "@" + cx + "," + cz;
    }

    /** How far inside the edge (x, z) is, in blocks; negative outside. */
    public double depth(int x, int z) {
        double dx = x - cx;
        double dz = z - cz;
        double distanceSq = dx * dx + dz * dz;
        double reach = radius * 1.45;
        if (distanceSq > reach * reach) {
            return -reach;
        }
        double angle = Math.atan2(dz, dx);
        double edge = radius * (1 + 0.22 * Math.sin(3 * angle + p1) + 0.13 * Math.sin(5 * angle + p2) + 0.07 * Math.sin(9 * angle + p3));
        return edge - Math.sqrt(distanceSq);
    }

    public boolean contains(int x, int z) {
        return depth(x, z) > 0;
    }

    /** "☠☠☠" for danger 3. */
    public String skulls() {
        return "☠".repeat(Math.max(1, danger));
    }
}
