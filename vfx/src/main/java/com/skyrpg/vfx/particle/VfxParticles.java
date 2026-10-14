package com.skyrpg.vfx.particle;

import com.skyrpg.vfx.SkyRpgVfx;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Registers one particle type per {@link VfxParticleKind}: {@code skyrpg_vfx:spark}, {@code skyrpg_vfx:glow}... */
public final class VfxParticles {
    private static final Map<VfxParticleKind, ParticleType<VfxParticleOptions>> TYPES = new EnumMap<>(VfxParticleKind.class);
    private static final Map<ParticleType<?>, VfxParticleKind> KINDS = new IdentityHashMap<>();

    private VfxParticles() {
    }

    public static void register() {
        for (VfxParticleKind kind : VfxParticleKind.values()) {
            // alwaysSpawn = false: vanilla's own particle distance and settings rules apply to server-sent ones.
            ParticleType<VfxParticleOptions> type = FabricParticleTypes.complex(false, VfxParticleOptions::codec, VfxParticleOptions::streamCodec);
            Registry.register(BuiltInRegistries.PARTICLE_TYPE, SkyRpgVfx.id(kind.id()), type);
            TYPES.put(kind, type);
            KINDS.put(type, kind);
        }
    }

    public static ParticleType<VfxParticleOptions> type(VfxParticleKind kind) {
        return TYPES.get(kind);
    }

    /** The kind behind one of our particle types, or null for anything else (vanilla, other mods). */
    public static VfxParticleKind kind(ParticleType<?> type) {
        return KINDS.get(type);
    }
}
