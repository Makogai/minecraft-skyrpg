package com.skyrpg.items.effects;

import com.skyrpg.core.items.GearEffects;
import com.skyrpg.core.items.Boosts;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.items.ItemDefinition;
import com.skyrpg.items.ItemRegistry;
import com.skyrpg.items.ItemSet;
import com.skyrpg.items.SkyItems;
import com.skyrpg.items.SkyRpgItems;
import com.skyrpg.items.ability.Abilities;
import com.skyrpg.items.accessory.AccessorySlot;
import com.skyrpg.items.accessory.Accessories;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * The gear side of {@link GearEffects}: full armour sets, each armour piece in its slot, the held item (when it isn't
 * armour or an accessory), worn accessories and running boosts; strengths add up. Also does the
 * effects that belong to no skill ({@code skyrpg_items:fire_immunity}, {@code skyrpg_items:lava_immunity},
 * {@code skyrpg_items:night_vision}), and the {@code boost} ability type: stats and effects for a few seconds.
 */
public final class GearEffectSources {
    /** Fire Resistance, renewed every second. */
    public static final Identifier FIRE_IMMUNITY = SkyRpgItems.id("fire_immunity");
    /** No fire or lava damage at all, checked on every hit (no gap while the potion effect comes and goes), and never alight. */
    public static final Identifier LAVA_IMMUNITY = SkyRpgItems.id("lava_immunity");
    public static final Identifier NIGHT_VISION = SkyRpgItems.id("night_vision");

    private GearEffectSources() {
    }

    public static void init() {
        GearEffects.addSource(GearEffectSources::all);
        BoostStore.init();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FIRE) && GearEffects.has(player, LAVA_IMMUNITY)));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                server.getPlayerList().getPlayers().forEach(GearEffectSources::tick);
            }
        });
        // "boost": params {"stats": {"farming_fortune": 100}, "effects": {"skyrpg_farming:double_crops": 1}, "seconds": 10}
        Abilities.register("boost", (player, ability) -> {
            Map<String, Double> stats = new HashMap<>();
            if (ability.params().has("stats")) {
                ability.params().getAsJsonObject("stats").entrySet().forEach(e -> stats.put(e.getKey(), e.getValue().getAsDouble()));
            }
            Map<Identifier, Double> effects = new HashMap<>();
            if (ability.params().has("effects")) {
                ability.params().getAsJsonObject("effects").entrySet().forEach(e -> effects.put(Identifier.parse(e.getKey()), e.getValue().getAsDouble()));
            }
            int seconds = (int) ability.param("seconds", 10);
            Boosts.give(player, "ability:" + ability.name(), Component.literal(ability.name()), stats, effects, seconds * 20);
            Notice.of(Notice.Kind.ABILITY).tag(ability.name().toUpperCase(java.util.Locale.ROOT)).send(player, describe(stats, seconds));
            return true;
        });
    }

    /** "+100 Farming Fortune for 10s". */
    private static Component describe(Map<String, Double> stats, int seconds) {
        StringBuilder text = new StringBuilder();
        stats.forEach((id, amount) -> {
            StatType stat = StatTypes.get(id);
            if (stat != null) {
                text.append(text.isEmpty() ? "" : ", ").append(stat.formatBonus(amount)).append(' ').append(stat.name());
            }
        });
        return Msg.text((text.isEmpty() ? "Active" : text.toString()) + " for " + seconds + "s", ChatFormatting.YELLOW);
    }

    public static Map<Identifier, Double> all(ServerPlayer player) {
        Map<Identifier, Double> effects = new LinkedHashMap<>();
        for (EquipmentSlot slot : SkyItems.ARMOR_SLOTS) {
            ItemDefinition def = SkyItems.definition(player.getItemBySlot(slot));
            if (def != null && SkyItems.armorSlot(def.item()) == slot) {
                merge(effects, def.effects());
            }
        }
        ItemDefinition held = SkyItems.definition(player.getMainHandItem());
        if (held != null && SkyItems.armorSlot(held.item()) == null && AccessorySlot.of(player.getMainHandItem()) == null) {
            merge(effects, held.effects());
        }
        for (AccessorySlot slot : AccessorySlot.values()) {
            ItemDefinition def = SkyItems.definition(Accessories.worn(player, slot));
            if (def != null) {
                merge(effects, def.effects());
            }
        }
        SkyItems.wornPieces(player).forEach((setId, count) -> {
            ItemSet set = ItemRegistry.set(setId);
            if (set != null && count >= set.pieces()) {
                merge(effects, set.bonusEffects());
            }
        });
        long now = player.level().getServer().overworld().getGameTime();
        for (BoostStore.Active boost : BoostStore.active(player)) {
            if (boost.expires() > now) {
                boost.effects().forEach((id, value) -> effects.merge(Identifier.parse(id), value, Double::sum));
            }
        }
        return effects;
    }

    private static void merge(Map<Identifier, Double> into, Map<Identifier, Double> from) {
        from.forEach((id, value) -> into.merge(id, value, Double::sum));
    }

    private static void tick(ServerPlayer player) {
        BoostStore.tick(player);
        if (GearEffects.has(player, FIRE_IMMUNITY)) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, false));
        }
        if (player.isOnFire() && GearEffects.has(player, LAVA_IMMUNITY)) {
            player.clearFire();
        }
        if (GearEffects.has(player, NIGHT_VISION)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
        }
    }
}
