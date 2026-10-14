package com.skyrpg.pets.client;

import com.skyrpg.pets.PetRarity;
import com.skyrpg.pets.PetType;
import com.skyrpg.pets.Pets;
import com.skyrpg.vfx.client.VfxClient;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Rarity auras: every ~4 ticks, an effect at each nearby pet's world position. Also the client tick clock for track cleanup. */
final class PetEffects {
    private static final Identifier[] AURAS = new Identifier[PetRarity.values().length];
    private static final double RANGE_SQ = 32.0 * 32.0;
    private static long tick;

    static {
        for (PetRarity r : PetRarity.values()) {
            AURAS[r.ordinal()] = Identifier.fromNamespaceAndPath("skyrpg_vfx", "pet_aura_" + r.name().toLowerCase(Locale.ROOT));
        }
    }

    private PetEffects() {
    }

    static long clientTick() {
        return tick;
    }

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(PetEffects::tick);
    }

    private static void tick(Minecraft mc) {
        tick++;
        if (mc.level == null) {
            PetTrack.clear();
            return;
        }
        if (tick % 100 == 0) {
            PetTrack.prune(tick);
        }
        if (tick % 4 != 0 || !VfxClient.enabled()) {
            return;
        }
        Vec3 eye = mc.getCameraEntity() == null ? mc.player.position() : mc.getCameraEntity().position();
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.isInvisible() || player.isSpectator()) {
                continue;
            }
            // Your own pet isn't drawn in first person, and its aura would spawn right beside the camera.
            if (player == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }
            ItemStack pet = player.getAttachedOrElse(Pets.ACTIVE, ItemStack.EMPTY);
            PetType type = Pets.type(pet);
            if (type == null) {
                continue;
            }
            PetTrack track = PetTrack.find(player.getId());
            double x;
            double y;
            double z;
            if (track != null && track.hasPos && tick - track.lastSeen < 10) {
                x = track.lastX;
                y = track.lastY;
                z = track.lastZ;
            } else {
                x = player.getX();
                y = player.getY() + 1.4;
                z = player.getZ();
            }
            if (eye.distanceToSqr(x, y, z) > RANGE_SQ) {
                continue;
            }
            PetRarity rarity = Pets.rarity(pet);
            VfxClient.play(AURAS[(rarity == null ? PetRarity.COMMON : rarity).ordinal()], x, y, z);
        }
    }
}
