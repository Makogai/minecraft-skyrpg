package com.skyrpg.events.billboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyrpg.events.SkyRpgEvents;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

/**
 * Billboards (see {@link BillboardEntity}): the entity type {@code skyrpg_events:billboard}, the Billboard item that places
 * one, and the editor: right-clicking sends {@link Edit} (the current settings), the client's editor answers with
 * {@link Configure}, which is checked here (owner or op, within reach, a known size and look, web links only).
 */
public final class Billboards {
    public static final EntityType<BillboardEntity> TYPE;
    public static final Item ITEM;
    private static final int MAX_IMAGES = 4;
    private static final int MAX_LINK = 400;

    static {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, SkyRpgEvents.id("billboard"));
        TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<BillboardEntity>of(BillboardEntity::new, MobCategory.MISC)
                .sized(1f, 1f).clientTrackingRange(16).updateInterval(Integer.MAX_VALUE).fireImmune().build(key));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, SkyRpgEvents.id("billboard"));
        ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BillboardItem(new Item.Properties().setId(itemKey).stacksTo(16)));
    }

    private Billboards() {
    }

    public static void init() {
        PayloadTypeRegistry.clientboundPlay().register(Edit.TYPE, Edit.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Configure.TYPE, Configure.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Configure.TYPE, (payload, context) -> context.server().execute(() ->
                configure(context.player(), payload)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output -> output.accept(ITEM));
    }

    /** Opens the editor on the player's screen with the billboard's current settings. */
    static void openEditor(ServerPlayer player, BillboardEntity board) {
        JsonObject json = new JsonObject();
        json.addProperty("width", (int) board.screenWidth());
        json.addProperty("theme", board.theme());
        json.addProperty("seconds", board.seconds());
        json.addProperty("stand", board.standing());
        JsonArray images = new JsonArray();
        board.images().forEach(images::add);
        json.add("images", images);
        ServerPlayNetworking.send(player, new Edit(board.getId(), json.toString()));
    }

    private static void configure(ServerPlayer player, Configure payload) {
        Entity entity = player.level().getEntity(payload.entity());
        if (!(entity instanceof BillboardEntity board) || !board.mayEdit(player) || board.distanceToSqr(player) > 24 * 24) {
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(payload.json()).getAsJsonObject();
            int width = json.get("width").getAsInt();
            int theme = json.get("theme").getAsInt();
            int seconds = Math.max(3, Math.min(120, json.get("seconds").getAsInt()));
            if (!BillboardTheme.WIDTHS.contains(width) || theme < 0 || theme >= BillboardTheme.ALL.size()) {
                return;
            }
            List<String> images = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("images")) {
                String link = element.getAsString().trim();
                if (link.isEmpty()) {
                    continue;
                }
                if (!(link.startsWith("https://") || link.startsWith("http://")) || link.length() > MAX_LINK || link.contains("\n")) {
                    player.sendSystemMessage(Component.literal("Skipped a picture: links must start with https:// and be under " + MAX_LINK + " characters."));
                    continue;
                }
                if (images.size() < MAX_IMAGES) {
                    images.add(link);
                }
            }
            // A bigger board must still fit where it is.
            int old = (int) board.screenWidth();
            board.configure(width, theme, seconds, images);
            if (width > old && !player.level().noCollision(board, board.getBoundingBox().deflate(0.05))) {
                board.configure(old, theme, seconds, images);
                player.sendSystemMessage(Component.literal("Not enough room for a " + width + "-wide billboard here; kept it at " + old + "."));
            }
        } catch (RuntimeException e) {
            SkyRpgEvents.LOGGER.warn("Bad billboard settings from {}: {}", player.getGameProfile().name(), e.getMessage());
        }
    }

    /** Server to client: open the editor for this billboard (entity id) with these settings (JSON). */
    public record Edit(int entity, String json) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(SkyRpgEvents.id("billboard_edit"));
        public static final StreamCodec<ByteBuf, Edit> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Edit::entity,
                ByteBufCodecs.stringUtf8(1 << 13), Edit::json, Edit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the editor's new settings (JSON: width, theme, seconds, images). */
    public record Configure(int entity, String json) implements CustomPacketPayload {
        public static final Type<Configure> TYPE = new Type<>(SkyRpgEvents.id("billboard_configure"));
        public static final StreamCodec<ByteBuf, Configure> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Configure::entity,
                ByteBufCodecs.stringUtf8(1 << 13), Configure::json, Configure::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
