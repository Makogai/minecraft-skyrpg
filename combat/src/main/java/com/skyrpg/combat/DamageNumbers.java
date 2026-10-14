package com.skyrpg.combat;

import com.skyrpg.core.stats.Crits;
import com.skyrpg.core.stats.StatTypes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Floating damage numbers: when a player hurts a mob, the damage (in SkyRPG's health units, like the health bar) pops
 * up in front of it as a short-lived text display. Crits are gold with sparkles. The
 * displays are tagged; any that outlive their moment (a chunk saved mid-flight) are swept up a few seconds later.
 */
public final class DamageNumbers {
    private static final String TAG = "skyrpg_damage_number";
    private static final int LIFETIME = 16;
    private static final List<Shown> SHOWN = new ArrayList<>();
    private static final Set<UUID> LIVE = new HashSet<>();

    private record Shown(Display.TextDisplay display, long born) {
    }

    private DamageNumbers() {
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (damageTaken <= 0 || entity instanceof Player || !(entity.level() instanceof ServerLevel level)) {
                return;
            }
            ServerPlayer attacker = source.getEntity() instanceof ServerPlayer direct ? direct
                    : source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner ? owner : null;
            if (attacker != null) {
                // Tough bosses lose less health than the hit was worth: show the hit (as their nameplate shows their health).
                MobLevels.MobInfo info = MobLevels.info(entity);
                double toughness = info == null ? 1 : Math.max(1, info.toughness());
                show(level, entity, attacker, damageTaken * StatTypes.HEALTH_SCALE * toughness, Crits.wasCrit());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (Iterator<Shown> it = SHOWN.iterator(); it.hasNext(); ) {
                Shown shown = it.next();
                long age = server.getTickCount() - shown.born();
                Display.TextDisplay display = shown.display();
                // They stay put: moving a display (teleport or transformation) made it vanish on clients.
                if (age >= LIFETIME || display.isRemoved()) {
                    display.discard();
                    LIVE.remove(display.getUUID());
                    it.remove();
                }
            }
            // Leftovers loaded back from a chunk saved mid-flight.
            if (server.getTickCount() % 100 == 0) {
                for (ServerLevel level : server.getAllLevels()) {
                    for (Display.TextDisplay stray : level.getEntities(EntityTypes.TEXT_DISPLAY,
                            display -> display.entityTags().contains(TAG) && !LIVE.contains(display.getUUID()))) {
                        stray.discard();
                    }
                }
            }
        });
    }

    private static void show(ServerLevel level, LivingEntity target, ServerPlayer attacker, double amount, boolean crit) {
        String number = amount >= 10 ? String.format("%,d", Math.round(amount)) : String.format("%.1f", amount);
        MutableComponent text = crit
                ? Component.literal("✧").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(number).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                        .append(Component.literal("✧").withStyle(ChatFormatting.WHITE))
                : Component.literal(number).withStyle(ChatFormatting.GRAY);
        // Built from NBT the way /summon does it: a display built with its setters never showed up on clients.
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:text_display");
        ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, text).result().ifPresent(encoded -> tag.put("text", encoded));
        tag.putString("billboard", "center");
        tag.putBoolean("see_through", true);
        tag.putBoolean("shadow", true);
        tag.putInt("background", 0);
        tag.putFloat("view_range", 0.4f);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        // In front of the mob's middle, towards whoever hit it (clear of the nameplate overhead).
        Vec3 toward = attacker.position().subtract(target.position()).multiply(1, 0, 1).normalize().scale(target.getBbWidth() * 0.5 + 0.3);
        double spread = target.getBbWidth() * 0.5;
        double x = target.getX() + toward.x + (level.getRandom().nextDouble() - 0.5) * spread;
        double y = target.getY() + target.getBbHeight() * (0.35 + level.getRandom().nextDouble() * 0.3);
        double z = target.getZ() + toward.z + (level.getRandom().nextDouble() - 0.5) * spread;
        Entity entity = EntityType.loadEntityRecursive(tag, level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), e -> {
            e.snapTo(x, y, z, 0, 0);
            return e;
        });
        if (entity instanceof Display.TextDisplay display) {
            LIVE.add(display.getUUID());
            SHOWN.add(new Shown(display, level.getServer().getTickCount()));
            level.tryAddFreshEntityWithPassengers(display);
        }
    }
}
