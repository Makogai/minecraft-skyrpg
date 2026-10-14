package com.skyrpg.welcome.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.management.ManagementFactory;
import java.util.Locale;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ParticleStatus;

/**
 * The four performance presets of the setup wizard. Each sets vanilla options (render and simulation distance, graphics
 * mode, particles, clouds, entity distance, frame rate, VSync, biome blend, ambient occlusion, entity shadows, the
 * background frame limit) and, if they are installed, nudges Sodium and Dynamic FPS (see {@link ModCompat}).
 */
enum Preset {
    POTATO("Potato", "For old laptops and integrated graphics. Short view distance, no clouds, minimal particles, 60 FPS cap.",
            6, 5, GraphicsPreset.FAST, ParticleStatus.MINIMAL, CloudStatus.OFF, 0.5, 60, true, 0, false, false, true, 0xFFC98F4F),
    BALANCED("Balanced", "A good mix on most computers. Medium view distance, fast clouds, fewer particles, 120 FPS cap.",
            10, 8, GraphicsPreset.FANCY, ParticleStatus.DECREASED, CloudStatus.FAST, 0.75, 120, true, 2, true, true, true, 0xFF5CE1E6),
    HIGH("High", "Looks great on a gaming PC. Long view distance, fancy clouds, all particles, 240 FPS cap.",
            16, 12, GraphicsPreset.FANCY, ParticleStatus.ALL, CloudStatus.FANCY, 1.0, 240, false, 4, true, true, false, 0xFFF2B33D),
    ULTRA("Ultra", "Everything up. Very long view distance, fabulous graphics, unlimited FPS. Needs a strong graphics card.",
            24, 16, GraphicsPreset.FABULOUS, ParticleStatus.ALL, CloudStatus.FANCY, 1.5, 260, false, 6, true, true, false, 0xFFB98CFF);

    final String label;
    final String description;
    final int renderDistance;
    final int simulationDistance;
    final GraphicsPreset graphics;
    final ParticleStatus particles;
    final CloudStatus clouds;
    final double entityDistance;
    /** 260 is "unlimited" in the options screen. */
    final int maxFps;
    final boolean vsync;
    final int biomeBlend;
    final boolean ambientOcclusion;
    final boolean entityShadows;
    /** Lower the frame rate when the window is in the background (Dynamic FPS, or vanilla's inactivity limit). */
    final boolean saveWhenIdle;
    final int color;

    Preset(String label, String description, int renderDistance, int simulationDistance, GraphicsPreset graphics, ParticleStatus particles,
           CloudStatus clouds, double entityDistance, int maxFps, boolean vsync, int biomeBlend, boolean ambientOcclusion,
           boolean entityShadows, boolean saveWhenIdle, int color) {
        this.label = label;
        this.description = description;
        this.renderDistance = renderDistance;
        this.simulationDistance = simulationDistance;
        this.graphics = graphics;
        this.particles = particles;
        this.clouds = clouds;
        this.entityDistance = entityDistance;
        this.maxFps = maxFps;
        this.vsync = vsync;
        this.biomeBlend = biomeBlend;
        this.ambientOcclusion = ambientOcclusion;
        this.entityShadows = entityShadows;
        this.saveWhenIdle = saveWhenIdle;
        this.color = color;
    }

    Component title() {
        return Component.literal(label);
    }

    /** Applies the preset to the running game and saves options.txt. */
    void apply(Minecraft minecraft) {
        Options options = minecraft.options;
        try {
            graphics.apply(minecraft);
        } catch (RuntimeException e) {
            SkyRpgWelcomeClient.LOGGER.warn("Couldn't apply graphics preset {}", graphics, e);
        }
        options.graphicsPreset().set(graphics);
        options.renderDistance().set(renderDistance);
        options.simulationDistance().set(simulationDistance);
        options.particles().set(particles);
        options.cloudStatus().set(clouds);
        options.entityDistanceScaling().set(entityDistance);
        options.framerateLimit().set(maxFps);
        options.enableVsync().set(vsync);
        options.biomeBlendRadius().set(biomeBlend);
        options.ambientOcclusion().set(ambientOcclusion);
        options.entityShadows().set(entityShadows);
        ModCompat.applyPerformance(this);
        options.save();
    }

    /** Whether the game is currently set the way this preset says (the main options only). */
    boolean matches(Minecraft minecraft) {
        Options options = minecraft.options;
        return options.renderDistance().get() == renderDistance
                && options.simulationDistance().get() == simulationDistance
                && options.particles().get() == particles
                && options.cloudStatus().get() == clouds
                && options.framerateLimit().get() == maxFps;
    }

    /** The preset that suits this computer, from memory, processor threads and graphics card. */
    static Preset suggested() {
        long ramGb = Machine.ramGb();
        int cores = Machine.cores();
        String gpu = Machine.gpu().toLowerCase(Locale.ROOT);
        boolean integrated = gpu.contains("intel") || gpu.contains("uhd") || gpu.contains("iris xe") || gpu.contains("radeon graphics")
                || gpu.contains("vega") || gpu.contains("llvmpipe") || gpu.contains("software") || gpu.contains("microsoft basic");
        boolean strong = gpu.matches(".*(rtx|rx [6-9]\\d{3}|gtx 1[0-9]{3}|arc a[5-9]|geforce.*(3[0-9]{3}|4[0-9]{3}|5[0-9]{3})).*");
        if (ramGb < 6 || cores <= 2) {
            return POTATO;
        }
        if (integrated) {
            return ramGb >= 8 && cores >= 6 ? BALANCED : POTATO;
        }
        if (strong && ramGb >= 16 && cores >= 8) {
            return HIGH;
        }
        return BALANCED;
    }

    /** What the suggestion was based on, for a line under the cards. */
    static String machineSummary() {
        return Machine.cores() + " threads, " + Machine.ramGb() + " GB RAM, " + Machine.gpu();
    }

    /** Facts about this computer. */
    static final class Machine {
        private Machine() {
        }

        static int cores() {
            return Runtime.getRuntime().availableProcessors();
        }

        static long ramGb() {
            try {
                if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
                    return Math.round(os.getTotalMemorySize() / (1024.0 * 1024 * 1024));
                }
            } catch (RuntimeException | LinkageError e) {
                // fall through
            }
            return Runtime.getRuntime().maxMemory() / (1024L * 1024 * 1024) * 2;
        }

        static String gpu() {
            try {
                var device = RenderSystem.tryGetDevice();
                if (device != null) {
                    String name = device.getDeviceInfo().name();
                    return name == null || name.isBlank() ? "unknown graphics card" : name;
                }
            } catch (RuntimeException | LinkageError e) {
                // fall through
            }
            return "unknown graphics card";
        }
    }
}
