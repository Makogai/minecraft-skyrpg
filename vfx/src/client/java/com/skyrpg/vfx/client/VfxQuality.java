package com.skyrpg.vfx.client;

import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.vfx.SkyRpgVfx;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;

/**
 * The "Effects quality" setting (Settings > Effects): Off draws nothing and skips all VFX work, Low thins everything
 * out, High is the full look. Vanilla's own Particles option (Decreased, Minimal) scales on top.
 *
 * @param countScale       multiplies every emitter's count and rate
 * @param bindingScale     multiplies every binding's rate (ambient effects on gear and mobs)
 * @param rangeScale       multiplies effect and binding draw ranges
 * @param particlesPerTick hard cap on new particles per tick
 * @param maxAlive         ambient effects stop when this many VFX particles are alive (one-shots get 30% more room)
 * @param bindingPlays     cap on ambient effect plays per tick
 */
public record VfxQuality(String name, float countScale, float bindingScale, float rangeScale, int particlesPerTick, int maxAlive,
                         int bindingPlays) {
    public static final VfxQuality OFF = new VfxQuality("Off", 0, 0, 0, 0, 0, 0);
    public static final VfxQuality LOW = new VfxQuality("Low", 0.5f, 0.45f, 0.65f, 120, 1200, 32);
    public static final VfxQuality HIGH = new VfxQuality("High", 1, 1, 1, 400, 3500, 96);

    private static Setting setting;

    static void register() {
        setting = SkyRpgSettings.choice("vfx.quality", "Effects", "Effects quality",
                        "Particle effects on gear, mobs, pets and abilities. Off saves the most frames.",
                        List.of(OFF.name, LOW.name, HIGH.name), HIGH.name, value -> {
                            if (OFF.name.equals(value)) {
                                VfxRuntime.clear();
                            }
                        })
                .section("Particles")
                .keywords("vfx", "particles", "aura", "performance", "fps");
        SkyRpgSettings.category("Effects").icon(SkyRpgVfx.id("textures/gui/vfx.png")).description("Particle effects and auras");
    }

    public static VfxQuality current() {
        if (setting == null) {
            return HIGH;
        }
        return switch (setting.choice()) {
            case "Off" -> OFF;
            case "Low" -> LOW;
            default -> HIGH;
        };
    }

    public boolean off() {
        return this == OFF;
    }

    /** Vanilla's Particles option: All 1, Decreased 0.6, Minimal 0.25. */
    public static float vanillaScale(Minecraft minecraft) {
        ParticleStatus status = minecraft.options.particles().get();
        return switch (status) {
            case ALL -> 1;
            case DECREASED -> 0.6f;
            case MINIMAL -> 0.25f;
        };
    }
}
