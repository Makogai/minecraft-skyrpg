package com.skyrpg.items.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.SkyItems;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/**
 * Particles around the weapon or tool in a player's hand, for every player nearby: an item definition's
 * {@code "held_particles": [{"particle": "minecraft:electric_spark", "chance": 0.3, "rise": 0.02}]} spawns that particle
 * near the hand with that chance each tick, scattered along where the blade or head would be. Only simple particles
 * (no options) work. Client-side only; switch off under Settings > Gameplay.
 */
final class HeldParticles {
    private static final double RANGE = 48;
    private static final Map<String, SimpleParticleType> TYPES = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static Setting enabled;

    private HeldParticles() {
    }

    static void init() {
        enabled = SkyRpgSettings.toggle("gameplay.held_particles", SkyRpgSettings.GAMEPLAY, "Weapon particles",
                "Sparks, embers and motes around the strongest weapons and tools in players' hands", true, null);
        ClientTickEvents.END_CLIENT_TICK.register(HeldParticles::tick);
    }

    private static void tick(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused() || (enabled != null && !enabled.get())) {
            return;
        }
        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player.isInvisible() || player.isSpectator() || player.distanceToSqr(minecraft.player) > RANGE * RANGE) {
                continue;
            }
            ItemDefinition def = SkyItems.definition(player.getMainHandItem());
            if (def == null || !def.json().has("held_particles")) {
                continue;
            }
            // Not your own in first person: they'd be right in your face. Others see them on you, and so do you in F5.
            boolean firstPerson = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
            if (firstPerson) {
                continue;
            }
            for (JsonElement element : def.json().getAsJsonArray("held_particles")) {
                JsonObject spec = element.getAsJsonObject();
                double chance = spec.has("chance") ? spec.get("chance").getAsDouble() : 0.2;
                if (RANDOM.nextDouble() >= chance) {
                    continue;
                }
                SimpleParticleType type = type(spec.has("particle") ? spec.get("particle").getAsString() : "");
                if (type != null) {
                    spawn(minecraft, player, type, spec.has("rise") ? spec.get("rise").getAsDouble() : 0.01, firstPerson);
                }
            }
        }
    }

    private static void spawn(Minecraft minecraft, AbstractClientPlayer player, SimpleParticleType type, double rise, boolean firstPerson) {
        float yaw = (firstPerson ? player.getYRot() : player.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        if (player.getMainArm() == HumanoidArm.LEFT) {
            right = right.scale(-1);
        }
        // From the hand up along the blade: a random point on a short line rising forwards from the grip.
        double along = RANDOM.nextDouble();
        Vec3 hand = player.position().add(0, player.isCrouching() ? 0.85 : 1.05, 0).add(right.scale(0.38)).add(forward.scale(0.3));
        Vec3 at = hand.add(forward.scale(0.35 * along)).add(0, 0.7 * along, 0)
                .add((RANDOM.nextDouble() - 0.5) * 0.25, (RANDOM.nextDouble() - 0.5) * 0.2, (RANDOM.nextDouble() - 0.5) * 0.25);
        minecraft.level.addParticle(type, at.x, at.y, at.z, (RANDOM.nextDouble() - 0.5) * 0.02, rise, (RANDOM.nextDouble() - 0.5) * 0.02);
    }

    private static SimpleParticleType type(String id) {
        return TYPES.computeIfAbsent(id, key -> {
            Identifier parsed = Identifier.tryParse(key);
            ParticleType<?> type = parsed == null ? null : BuiltInRegistries.PARTICLE_TYPE.getValue(parsed);
            return type instanceof SimpleParticleType simple ? simple : null;
        });
    }
}
