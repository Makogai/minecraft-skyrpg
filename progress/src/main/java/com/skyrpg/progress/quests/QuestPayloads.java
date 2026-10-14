package com.skyrpg.progress.quests;

import com.skyrpg.progress.SkyRpgProgress;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The questline's packets: the quest data to clients, toasts, and the Journal's buttons back to the server. */
public final class QuestPayloads {
    private QuestPayloads() {
    }

    /** Server to client: every chapter and quest as JSON (see {@link QuestData#loadJson}). */
    public record Defs(String json) implements CustomPacketPayload {
        public static final Type<Defs> TYPE = new Type<>(SkyRpgProgress.id("quest_defs"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Defs> CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(900_000), Defs::json, Defs::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: a banner for a new quest or a completed quest or chapter. {@code icon} is an item id. */
    public record Toast(String icon, String label, String title, String subtitle, int color) implements CustomPacketPayload {
        public static final Type<Toast> TYPE = new Type<>(SkyRpgProgress.id("quest_toast"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Toast> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Toast::icon, ByteBufCodecs.STRING_UTF8, Toast::label, ByteBufCodecs.STRING_UTF8, Toast::title,
                ByteBufCodecs.STRING_UTF8, Toast::subtitle, ByteBufCodecs.INT, Toast::color, Toast::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: {@code track <quest>} (empty quest = the quest's first open objective), {@code untrack}, {@code abandon <quest>}, {@code opened}. */
    public record Action(String kind, String quest, int objective) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(SkyRpgProgress.id("quest_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Action::kind, ByteBufCodecs.STRING_UTF8, Action::quest, ByteBufCodecs.VAR_INT, Action::objective,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Defs.TYPE, Defs.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Toast.TYPE, Toast.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
    }
}
