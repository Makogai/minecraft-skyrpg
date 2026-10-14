package com.skyrpg.magic;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Casting is client input, server decision: the client sends what the player did (press, release, pick a spell, open the slots)
 * and the server checks the wand, mana and cooldown again. The server sends back the definitions (for tooltips, the wheel and
 * the HUD), cooldowns, a cancelled charge and screen shakes.
 */
public final class MagicPayloads {
    private static final StreamCodec<ByteBuf, Map<String, String>> JSON_MAP =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);

    private MagicPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Release.TYPE, Release.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Select.TYPE, Select.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(OpenSlots.TYPE, OpenSlots.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Definitions.TYPE, Definitions.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Cooldown.TYPE, Cooldown.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ChargeCancel.TYPE, ChargeCancel.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Shake.TYPE, Shake.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, context) -> Casting.press(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(Release.TYPE, (payload, context) -> Casting.release(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(Select.TYPE, (payload, context) -> Casting.select(context.player(), payload.slot()));
        ServerPlayNetworking.registerGlobalReceiver(OpenSlots.TYPE, (payload, context) -> WandMenu.open(context.player()));
    }

    static void sendDefinitions(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, Definitions.TYPE)) {
            ServerPlayNetworking.send(player, Definitions.current());
        }
    }

    /** The player pressed the cast button with a wand: cast the selected spell, or start charging it. */
    public record Press() implements CustomPacketPayload {
        public static final Type<Press> TYPE = new Type<>(SkyRpgMagic.id("press"));
        public static final StreamCodec<ByteBuf, Press> CODEC = StreamCodec.unit(new Press());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The cast button was let go: release a charged spell (the server measured the charge itself). */
    public record Release() implements CustomPacketPayload {
        public static final Type<Release> TYPE = new Type<>(SkyRpgMagic.id("release"));
        public static final StreamCodec<ByteBuf, Release> CODEC = StreamCodec.unit(new Release());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Select the spell in this slot of the held wand. */
    public record Select(int slot) implements CustomPacketPayload {
        public static final Type<Select> TYPE = new Type<>(SkyRpgMagic.id("select"));
        public static final StreamCodec<ByteBuf, Select> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Select::slot, Select::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Open the held wand's spell slots. */
    public record OpenSlots() implements CustomPacketPayload {
        public static final Type<OpenSlots> TYPE = new Type<>(SkyRpgMagic.id("open_slots"));
        public static final StreamCodec<ByteBuf, OpenSlots> CODEC = StreamCodec.unit(new OpenSlots());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The spells and wands, as the original JSON. Sent on join and after every /reload. */
    public record Definitions(Map<String, String> spells, Map<String, String> wands) implements CustomPacketPayload {
        public static final Type<Definitions> TYPE = new Type<>(SkyRpgMagic.id("definitions"));
        public static final StreamCodec<ByteBuf, Definitions> CODEC = StreamCodec.composite(
                JSON_MAP, Definitions::spells, JSON_MAP, Definitions::wands, Definitions::new);

        static Definitions current() {
            Map<String, String> spells = new LinkedHashMap<>();
            Magic.spells().forEach(def -> spells.put(def.id().toString(), def.json().toString()));
            Map<String, String> wands = new LinkedHashMap<>();
            // A wand's file has no JSON of its own to send, so rebuild it.
            Magic.wands().forEach(def -> wands.put(def.id().toString(), wandJson(def)));
            return new Definitions(spells, wands);
        }

        private static String wandJson(WandDef def) {
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("slots", def.slots());
            json.addProperty("mana_discount", def.manaDiscount());
            com.google.gson.JsonObject power = new com.google.gson.JsonObject();
            def.elementPower().forEach((element, value) -> power.addProperty(element.id(), value));
            json.add("element_power", power);
            json.addProperty("slow_bonus", def.slowBonus());
            json.addProperty("chain_bonus", def.chainBonus());
            json.addProperty("cooldown_discount", def.cooldownDiscount());
            return json.toString();
        }

        public void apply() {
            Magic.replace(parse(spells), parse(wands));
        }

        private static Map<Identifier, JsonElement> parse(Map<String, String> files) {
            Map<Identifier, JsonElement> parsed = new LinkedHashMap<>();
            files.entrySet().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> parsed.put(Identifier.parse(entry.getKey()), JsonParser.parseString(entry.getValue())));
            return parsed;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A spell was cast: it can't be cast again for this long. */
    public record Cooldown(String spell, int ticks) implements CustomPacketPayload {
        public static final Type<Cooldown> TYPE = new Type<>(SkyRpgMagic.id("cooldown"));
        public static final StreamCodec<ByteBuf, Cooldown> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Cooldown::spell, ByteBufCodecs.VAR_INT, Cooldown::ticks, Cooldown::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The server did not start (or ended) the charge the client began: stop the charge ring. */
    public record ChargeCancel() implements CustomPacketPayload {
        public static final Type<ChargeCancel> TYPE = new Type<>(SkyRpgMagic.id("charge_cancel"));
        public static final StreamCodec<ByteBuf, ChargeCancel> CODEC = StreamCodec.unit(new ChargeCancel());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Shake the camera (a big explosion nearby). */
    public record Shake(float amount, int ticks) implements CustomPacketPayload {
        public static final Type<Shake> TYPE = new Type<>(SkyRpgMagic.id("shake"));
        public static final StreamCodec<ByteBuf, Shake> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Shake::amount, ByteBufCodecs.VAR_INT, Shake::ticks, Shake::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
