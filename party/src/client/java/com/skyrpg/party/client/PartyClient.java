package com.skyrpg.party.client;

import com.skyrpg.party.PartyPayloads;
import com.skyrpg.party.PartyPayloads.MemberInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerSkin;

/** What the client knows about its party: the last state from the server, smoothed health and skins (cached, so offline members keep their face). */
final class PartyClient {
    private static PartyPayloads.State state = PartyPayloads.State.EMPTY;
    private static final Map<UUID, Float> SHOWN = new HashMap<>();
    private static final Map<UUID, PlayerSkin> SKINS = new HashMap<>();
    private static long lastReceived;

    private PartyClient() {
    }

    static void init() {
        ClientPlayNetworking.registerGlobalReceiver(PartyPayloads.State.TYPE, (payload, context) -> context.client().execute(() -> {
            state = payload;
            lastReceived = Util.getMillis();
        }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            state = PartyPayloads.State.EMPTY;
            SHOWN.clear();
        });
    }

    static PartyPayloads.State state() {
        return state;
    }

    static boolean inParty() {
        return state.inParty();
    }

    static boolean isLeader(UUID id) {
        return state.leader().equals(id);
    }

    static boolean amLeader() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && inParty() && isLeader(minecraft.player.getUUID());
    }

    static boolean isMember(UUID id) {
        for (MemberInfo member : state.members()) {
            if (member.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    /** The other members, in party order (the leader is not moved). */
    static List<MemberInfo> others() {
        Minecraft minecraft = Minecraft.getInstance();
        UUID me = minecraft.player == null ? null : minecraft.player.getUUID();
        List<MemberInfo> list = new ArrayList<>();
        for (MemberInfo member : state.members()) {
            if (!member.id().equals(me)) {
                list.add(member);
            }
        }
        return list;
    }

    /** The health fraction to draw: moves smoothly towards the real one. */
    static float shownFraction(MemberInfo member) {
        float target = member.maxHp() <= 0 ? 0 : Mth.clamp(member.hp() / member.maxHp(), 0f, 1f);
        float current = SHOWN.getOrDefault(member.id(), target);
        current += (target - current) * 0.18f;
        if (Math.abs(target - current) < 0.002f) {
            current = target;
        }
        SHOWN.put(member.id(), current);
        return current;
    }

    /** The skin to draw: the live one while the player is in the tab list, else the last one seen, else the default. */
    static PlayerSkin skin(UUID id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(id);
            if (info != null) {
                PlayerSkin skin = info.getSkin();
                SKINS.put(id, skin);
                return skin;
            }
        }
        PlayerSkin cached = SKINS.get(id);
        return cached != null ? cached : DefaultPlayerSkin.get(id);
    }

    /** "Hub", "Nether", "The End", "Overworld", or a tidied-up id for another mod's dimension. */
    static String dimensionName(String id) {
        return switch (id) {
            case "" -> "Unknown";
            case "minecraft:overworld" -> "Overworld";
            case "minecraft:the_nether" -> "Nether";
            case "minecraft:the_end" -> "The End";
            case "skyrpg_hub:hub" -> "Hub";
            default -> {
                String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                String text = path.replace('_', ' ').replace('/', ' ');
                yield text.isEmpty() ? id : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
            }
        };
    }

    static String myDimension() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? "" : minecraft.level.dimension().identifier().toString();
    }

    static void send(String action, String arg) {
        ClientPlayNetworking.send(new PartyPayloads.Action(action, arg));
    }

    static long age() {
        return Util.getMillis() - lastReceived;
    }
}
