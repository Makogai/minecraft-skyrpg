package com.skyrpg.vfx;

import com.skyrpg.vfx.net.VfxPayloads;
import com.skyrpg.vfx.particle.VfxParticles;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data-driven particle effects: emitters, shapes and custom particles that attach to mobs, armour sets, tools and pets.
 * Server side: the particle types, loading and syncing the effect data, {@link Vfx} and /vfx. Format and how-tos:
 * docs/design/vfx.md.
 */
public class SkyRpgVfx implements ModInitializer {
    public static final String MOD_ID = "skyrpg_vfx";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        VfxParticles.register();
        VfxPayloads.register();
        VfxServer.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> VfxCommands.register(dispatcher));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
