package com.skyrpg.vfx.client.particle;

import com.skyrpg.vfx.particle.VfxParticleKind;
import com.skyrpg.vfx.particle.VfxParticleOptions;
import com.skyrpg.vfx.particle.VfxParticles;
import net.fabricmc.fabric.api.client.particle.v1.FabricSpriteSet;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.util.RandomSource;

/**
 * Hooks every VFX particle type up to {@link VfxParticle} and its sprite set ({@code assets/skyrpg_vfx/particles/<kind>.json}
 * lists the frames).
 */
public final class VfxParticleProviders {
    private VfxParticleProviders() {
    }

    public static void register() {
        for (VfxParticleKind kind : VfxParticleKind.values()) {
            ParticleProviderRegistry.getInstance().register(VfxParticles.type(kind),
                    (ParticleProviderRegistry.PendingParticleProvider<VfxParticleOptions>) sprites -> new Provider(sprites, kind));
        }
    }

    private record Provider(FabricSpriteSet sprites, VfxParticleKind kind) implements ParticleProvider<VfxParticleOptions> {
        @Override
        public Particle createParticle(VfxParticleOptions options, ClientLevel level, double x, double y, double z, double xd, double yd,
                                       double zd, RandomSource random) {
            return new VfxParticle(level, x, y, z, xd, yd, zd, options, sprites, kind);
        }
    }
}
