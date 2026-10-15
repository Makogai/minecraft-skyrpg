package com.skyrpg.brainrots.client;

import com.mojang.authlib.GameProfile;
import com.skyrpg.brainrots.BrainrotEntity;
import com.skyrpg.brainrots.BrainrotKind;
import com.skyrpg.brainrots.SkyRpgBrainrots;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * Client-only models to draw in menus and on the HUD: a brainrot of each kind, and stand-ins for players (their
 * skin, and their armour and held items when they're near enough to be seen). None of them is ever added to the
 * world; each gets an entity id no real entity has, because rendering asks for one. Made once per world.
 */
final class Puppets {
    private static final Map<BrainrotKind, BrainrotEntity> BRAINROTS = new EnumMap<>(BrainrotKind.class);
    private static final Map<UUID, CardPlayer> PLAYERS = new HashMap<>();
    private static ClientLevel level;

    private Puppets() {
    }

    private static ClientLevel level() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            BRAINROTS.clear();
            PLAYERS.clear();
            level = current;
        }
        return current;
    }

    static BrainrotEntity brainrot(BrainrotKind kind) {
        ClientLevel world = level();
        if (world == null) {
            return null;
        }
        return BRAINROTS.computeIfAbsent(kind, k -> {
            BrainrotEntity entity = SkyRpgBrainrots.BRAINROT.create(world, EntitySpawnReason.LOAD);
            if (entity != null) {
                entity.setId(-20_000 - k.ordinal());
                entity.setKind(k, false);
            }
            return entity;
        });
    }

    /** A stand-in for this player, dressed like them if they're in sight right now. */
    static Player player(UUID uuid, String name) {
        ClientLevel world = level();
        if (world == null) {
            return null;
        }
        CardPlayer puppet = PLAYERS.computeIfAbsent(uuid, id -> {
            Minecraft minecraft = Minecraft.getInstance();
            PlayerInfo info = minecraft.getConnection() != null ? minecraft.getConnection().getPlayerInfo(id) : null;
            GameProfile profile = info != null ? info.getProfile() : new GameProfile(id, name);
            return new CardPlayer(world, profile, -30_000 - PLAYERS.size());
        });
        Player real = world.getPlayerByUUID(uuid);
        if (real != null) {
            puppet.copyLook(real);
        }
        return puppet;
    }

    /** A client player that isn't in the world, with every skin layer on until it can copy the real one's. */
    private static final class CardPlayer extends RemotePlayer {
        CardPlayer(ClientLevel level, GameProfile profile, int id) {
            super(level, profile);
            setId(id);
            entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        }

        void copyLook(Player real) {
            entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, real.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION));
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                setItemSlot(slot, real.getItemBySlot(slot).copy());
            }
        }
    }
}
