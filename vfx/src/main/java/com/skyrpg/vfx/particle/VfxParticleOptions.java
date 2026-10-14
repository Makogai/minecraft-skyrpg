package com.skyrpg.vfx.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.StreamCodec;

/**
 * Parameters of one SkyRPG VFX particle. Works with {@code /particle} too, e.g.
 * <pre>/particle skyrpg_vfx:ember{color:"#FF8A2A",color_end:"#00FF2000",size:0.2,lifetime:30,gravity:-0.05,fullbright:true} ~ ~1 ~</pre>
 *
 * @param color    ARGB at birth (alpha included)
 * @param colorEnd ARGB at death; the particle blends from {@code color} to this over its life
 * @param size     width in blocks at birth
 * @param sizeEnd  width in blocks at death
 * @param lifetime ticks
 * @param gravity  1 = vanilla falling-item gravity; negative floats up
 * @param drag     fraction of speed lost per tick (0 = none, 0.1 = heavy)
 * @param spin     degrees per tick
 * @param sway     side-to-side flutter, in blocks per tick (leaves, snow, spores)
 * @param fadeIn   fraction of the life spent fading in (0 = pops in at full colour)
 * @param flags    bit 0 fullbright, bit 1 collides with blocks, bits 2-4 {@link Orient}
 */
public record VfxParticleOptions(ParticleType<VfxParticleOptions> type, int color, int colorEnd, float size, float sizeEnd, int lifetime,
                                 float gravity, float drag, float spin, float sway, float fadeIn, int flags) implements ParticleOptions {
    public static final int FULLBRIGHT = 1;
    public static final int COLLIDE = 2;
    private static final int ORIENT_SHIFT = 2;

    /** "#RRGGBB", "#AARRGGBB" or a plain ARGB int. */
    public static final Codec<Integer> COLOR = Codec.withAlternative(
            Codec.STRING.comapFlatMap(VfxParticleOptions::readHex, VfxParticleOptions::hex), Codec.INT);
    private static final Codec<Orient> ORIENT = Codec.STRING.comapFlatMap(name -> {
        Orient orient = Orient.parse(name);
        return orient != null ? DataResult.success(orient) : DataResult.error(() -> "Unknown orient " + name);
    }, Orient::id);

    public static int flags(boolean fullbright, boolean collide, Orient orient) {
        return (fullbright ? FULLBRIGHT : 0) | (collide ? COLLIDE : 0) | (orient.ordinal() << ORIENT_SHIFT);
    }

    public boolean fullbright() {
        return (flags & FULLBRIGHT) != 0;
    }

    public boolean collide() {
        return (flags & COLLIDE) != 0;
    }

    public Orient orient() {
        int index = (flags >> ORIENT_SHIFT) & 7;
        return index < Orient.VALUES.length ? Orient.VALUES[index] : Orient.AUTO;
    }

    @Override
    public ParticleType<VfxParticleOptions> getType() {
        return type;
    }

    public static MapCodec<VfxParticleOptions> codec(ParticleType<VfxParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                COLOR.optionalFieldOf("color", 0xFFFFFFFF).forGetter(VfxParticleOptions::color),
                COLOR.optionalFieldOf("color_end").forGetter(o -> Optional.of(o.colorEnd())),
                Codec.FLOAT.optionalFieldOf("size", 0.2f).forGetter(VfxParticleOptions::size),
                Codec.FLOAT.optionalFieldOf("size_end").forGetter(o -> Optional.of(o.sizeEnd())),
                Codec.INT.optionalFieldOf("lifetime", 20).forGetter(VfxParticleOptions::lifetime),
                Codec.FLOAT.optionalFieldOf("gravity", 0f).forGetter(VfxParticleOptions::gravity),
                Codec.FLOAT.optionalFieldOf("drag", 0.02f).forGetter(VfxParticleOptions::drag),
                Codec.FLOAT.optionalFieldOf("spin", 0f).forGetter(VfxParticleOptions::spin),
                Codec.FLOAT.optionalFieldOf("sway", 0f).forGetter(VfxParticleOptions::sway),
                Codec.FLOAT.optionalFieldOf("fade_in", 0f).forGetter(VfxParticleOptions::fadeIn),
                Codec.BOOL.optionalFieldOf("fullbright", false).forGetter(VfxParticleOptions::fullbright),
                Codec.BOOL.optionalFieldOf("collide", false).forGetter(VfxParticleOptions::collide),
                ORIENT.optionalFieldOf("orient", Orient.AUTO).forGetter(VfxParticleOptions::orient)
        ).apply(i, (color, colorEnd, size, sizeEnd, lifetime, gravity, drag, spin, sway, fadeIn, fullbright, collide, orient) ->
                new VfxParticleOptions(type, color, colorEnd.orElse(color), size, sizeEnd.orElse(size), Math.max(1, lifetime), gravity, drag,
                        spin, sway, fadeIn, flags(fullbright, collide, orient))));
    }

    public static StreamCodec<ByteBuf, VfxParticleOptions> streamCodec(ParticleType<VfxParticleOptions> type) {
        return new StreamCodec<>() {
            @Override
            public VfxParticleOptions decode(ByteBuf buf) {
                return new VfxParticleOptions(type, buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(), buf.readUnsignedShort(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readUnsignedByte());
            }

            @Override
            public void encode(ByteBuf buf, VfxParticleOptions o) {
                buf.writeInt(o.color);
                buf.writeInt(o.colorEnd);
                buf.writeFloat(o.size);
                buf.writeFloat(o.sizeEnd);
                buf.writeShort(Math.min(o.lifetime, 65535));
                buf.writeFloat(o.gravity);
                buf.writeFloat(o.drag);
                buf.writeFloat(o.spin);
                buf.writeFloat(o.sway);
                buf.writeFloat(o.fadeIn);
                buf.writeByte(o.flags);
            }
        };
    }

    /** Parses "#RRGGBB" (opaque) or "#AARRGGBB". */
    public static DataResult<Integer> readHex(String text) {
        String hex = text.startsWith("#") ? text.substring(1) : text;
        try {
            if (hex.length() == 6) {
                return DataResult.success(0xFF000000 | Integer.parseUnsignedInt(hex, 16));
            }
            if (hex.length() == 8) {
                return DataResult.success((int) Long.parseLong(hex, 16));
            }
        } catch (NumberFormatException ignored) {
            // falls through to the error
        }
        return DataResult.error(() -> "Not a colour: " + text + " (use #RRGGBB or #AARRGGBB)");
    }

    public static String hex(int argb) {
        return (argb >>> 24) == 0xFF ? String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF) : String.format(Locale.ROOT, "#%08X", argb);
    }
}
