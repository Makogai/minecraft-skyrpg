package com.skyrpg.fishing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.entity.EntityTypeTest;

/**
 * Sea creatures: sometimes a bite is a creature, pulled out of the water at you. Which ones can come up depends on your
 * Fishing level, by data ({@code data/<ns>/skyrpg/sea_creatures/*.json}):
 * <pre>
 * {"entity": "minecraft:drowned", "name": "Drowned Sailor", "level": 1, "weight": 10, "health": 1.5, "damage": 1.0,
 *  "xp": 25, "scales": 1}
 * </pre>
 * Optional, for bigger or stranger ones:
 * <ul>
 *   <li>{@code "fluid": "lava"}: only comes up while lava fishing ({@link LavaFishing}); water ones only from water.</li>
 *   <li>{@code "size"}: a slime's or magma cube's size (above 1 it splits when killed, and the pieces are plain mobs).</li>
 *   <li>{@code "scale"}: the Scale attribute (2 = twice as big).</li>
 *   <li>{@code "base_health"} / {@code "base_damage"}: replace the body's own (in half hearts) before the
 *       {@code "health"} / {@code "damage"} multipliers.</li>
 *   <li>{@code "boss_bar": true}: a boss bar for everyone near it, and a warning in chat when it comes up.</li>
 *   <li>{@code "color"}: its name's colour ({@code "gold"}; aqua by default).</li>
 * </ul>
 * Killing one pays Fishing XP and Tidescales (and a lava one can bring a Molten Pearl). They're named and tagged, and
 * leave after two minutes (or right away after a restart), so nobody keeps one as a pet.
 */
public final class SeaCreatures {
    public static final String DIRECTORY = "skyrpg/sea_creatures";
    public static final String TAG = "skyrpg_sea_creature";
    private static final int LIFETIME = 2400;
    private static final double BAR_RANGE_SQR = 48 * 48;
    private static final Identifier HEALTH = SkyRpgFishing.id("sea_creature_health");
    private static final Identifier DAMAGE = SkyRpgFishing.id("sea_creature_damage");

    record Creature(Identifier entity, String name, int level, int weight, double health, double damage, double xp, int scales,
                    boolean lava, int size, double scale, double baseHealth, double baseDamage, boolean bossBar, ChatFormatting color) {
    }

    private record Live(Creature creature, long born) {
    }

    private record Bar(Mob mob, ServerBossEvent event) {
    }

    private static List<Creature> creatures = List.of();
    private static final Map<UUID, Live> LIVE = new HashMap<>();
    private static final Map<UUID, Bar> BARS = new HashMap<>();

    private SeaCreatures() {
    }

    static void load(Map<Identifier, JsonElement> files) {
        List<Creature> loaded = new ArrayList<>();
        files.forEach((id, element) -> {
            try {
                JsonObject json = element.getAsJsonObject();
                String fluid = json.has("fluid") ? json.get("fluid").getAsString() : "water";
                if (!fluid.equals("water") && !fluid.equals("lava")) {
                    throw new IllegalArgumentException("unknown fluid " + fluid);
                }
                loaded.add(new Creature(Identifier.parse(json.get("entity").getAsString()), json.get("name").getAsString(),
                        json.has("level") ? json.get("level").getAsInt() : 0, json.has("weight") ? json.get("weight").getAsInt() : 10,
                        num(json, "health", 1), num(json, "damage", 1), num(json, "xp", 20), json.has("scales") ? json.get("scales").getAsInt() : 1,
                        fluid.equals("lava"), json.has("size") ? json.get("size").getAsInt() : 0, num(json, "scale", 0),
                        num(json, "base_health", 0), num(json, "base_damage", 0),
                        json.has("boss_bar") && json.get("boss_bar").getAsBoolean(), color(json)));
            } catch (RuntimeException e) {
                SkyRpgFishing.LOGGER.warn("Skipping sea creature {}: {}", id, e.getMessage());
            }
        });
        creatures = List.copyOf(loaded);
    }

    private static double num(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    private static ChatFormatting color(JsonObject json) {
        if (!json.has("color")) {
            return ChatFormatting.AQUA;
        }
        ChatFormatting color = ChatFormatting.valueOf(json.get("color").getAsString().toUpperCase(Locale.ROOT));
        if (color.ordinal() > ChatFormatting.WHITE.ordinal()) {
            throw new IllegalArgumentException("not a colour: " + color);
        }
        return color;
    }

    static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            Bar bar = BARS.remove(entity.getUUID());
            if (bar != null) {
                bar.event().removeAllPlayers();
            }
            Live live = LIVE.remove(entity.getUUID());
            ServerPlayer killer = source.getEntity() instanceof ServerPlayer direct ? direct
                    : source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner ? owner : null;
            if (live == null || killer == null) {
                return;
            }
            SkillLevels.giveXp(killer, SkillLevels.FISHING, live.creature().xp());
            Catching.scales(killer, live.creature().scales());
            Catching.rareFinds(killer, 1, live.creature().lava());
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                expire(server);
            }
            if (server.getTickCount() % 5 == 0 && !BARS.isEmpty()) {
                updateBars();
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            BARS.values().forEach(bar -> bar.event().removeAllPlayers());
            BARS.clear();
        });
    }

    /** Pulls a creature out of the water (or the lava) at the player. False if none fits their level. */
    static boolean surface(FishingHook hook, ServerPlayer player, boolean lava) {
        int fishing = SkillLevels.level(player, SkillLevels.FISHING);
        List<Creature> open = creatures.stream().filter(c -> c.lava() == lava && c.level() <= fishing).toList();
        int total = open.stream().mapToInt(Creature::weight).sum();
        if (total <= 0) {
            return false;
        }
        int pick = player.getRandom().nextInt(total);
        Creature creature = null;
        for (Creature candidate : open) {
            pick -= candidate.weight();
            if (pick < 0) {
                creature = candidate;
                break;
            }
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(creature.entity()).orElse(null);
        ServerLevel level = player.level();
        Entity entity = type == null ? null : type.create(level, EntitySpawnReason.TRIGGERED);
        if (!(entity instanceof Mob mob)) {
            return false;
        }
        mob.snapTo(hook.getX(), hook.getY(), hook.getZ(), player.getRandom().nextFloat() * 360, 0);
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.TRIGGERED, null);
        if (creature.size() > 0 && mob instanceof AbstractCubeMob cube) {
            cube.setSize(creature.size(), true);
        }
        base(mob, Attributes.SCALE, creature.scale());
        base(mob, Attributes.MAX_HEALTH, creature.baseHealth());
        base(mob, Attributes.ATTACK_DAMAGE, creature.baseDamage());
        mob.addTag(TAG);
        Component name = Component.literal(creature.name()).withStyle(creature.color());
        mob.setCustomName(name);
        mob.setCustomNameVisible(true);
        boost(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH, creature.health() - 1);
        boost(mob.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE, creature.damage() - 1);
        mob.setHealth(mob.getMaxHealth());
        double dx = player.getX() - hook.getX();
        double dy = player.getY() - hook.getY();
        double dz = player.getZ() - hook.getZ();
        mob.setDeltaMovement(dx * 0.12, dy * 0.1 + Math.sqrt(Math.sqrt(dx * dx + dy * dy + dz * dz)) * 0.1, dz * 0.12);
        LIVE.put(mob.getUUID(), new Live(creature, level.getServer().getTickCount()));
        level.addFreshEntity(mob);
        mob.setTarget(player);
        player.sendOverlayMessage(Msg.text("A " + creature.name() + " surfaced!", creature.color()));
        if (lava) {
            level.sendParticles(ParticleTypes.LAVA, hook.getX(), hook.getY() + 0.3, hook.getZ(), 12, 0.4, 0.2, 0.4, 0.1);
            Msg.playSound(player, SoundEvents.LAVA_POP, 1.0f, 0.6f);
        } else {
            Msg.playSound(player, SoundEvents.GENERIC_SPLASH, 1.0f, 0.7f);
        }
        if (creature.bossBar()) {
            ServerBossEvent bar = new ServerBossEvent(UUID.randomUUID(), name, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
            bar.addPlayer(player);
            BARS.put(mob.getUUID(), new Bar(mob, bar));
            Notice.of(Notice.Kind.FISHING).icon(Notice.SKULL).tag("SEA CREATURE").color(Notice.rgb(creature.color()))
                    .send(player, Component.literal("The " + (lava ? "lava" : "water") + " heaves... ").withStyle(ChatFormatting.GRAY)
                    .append(name.copy().withStyle(ChatFormatting.BOLD))
                    .append(Component.literal(" rises!").withStyle(ChatFormatting.GRAY)));
            Msg.playSound(player, SoundEvents.WITHER_SPAWN, 0.5f, 1.4f);
        }
        return true;
    }

    /** Sets an attribute's base value, if the creature gives one (and the body has the attribute). */
    private static void base(Mob mob, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null && value > 0) {
            instance.setBaseValue(value);
        }
    }

    private static void boost(AttributeInstance attribute, Identifier id, double amount) {
        if (attribute != null && amount != 0) {
            attribute.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Boss bars follow their creature's health and show to everyone within 48 blocks; gone with the creature. */
    private static void updateBars() {
        BARS.values().removeIf(bar -> {
            Mob mob = bar.mob();
            if (mob.isRemoved() || !mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
                bar.event().removeAllPlayers();
                return true;
            }
            bar.event().setProgress(Mth.clamp(mob.getHealth() / mob.getMaxHealth(), 0f, 1f));
            for (ServerPlayer player : List.copyOf(bar.event().getPlayers())) {
                if (player.isRemoved() || player.level() != level || player.distanceToSqr(mob) > BAR_RANGE_SQR) {
                    bar.event().removePlayer(player);
                }
            }
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(mob) <= BAR_RANGE_SQR) {
                    bar.event().addPlayer(player);
                }
            }
            return false;
        });
    }

    /** Creatures past their two minutes leave; untracked ones (from before a restart) leave at once. */
    private static void expire(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            for (Mob mob : level.getEntities(EntityTypeTest.forClass(Mob.class), mob -> mob.entityTags().contains(TAG))) {
                Live live = LIVE.get(mob.getUUID());
                if (live == null || now - live.born() > LIFETIME) {
                    boolean lava = live != null && live.creature().lava();
                    level.sendParticles(lava ? ParticleTypes.FLAME : ParticleTypes.SPLASH, mob.getX(), mob.getY() + 0.5, mob.getZ(), 20,
                            0.4, 0.4, 0.4, lava ? 0.02 : 0.1);
                    mob.discard();
                    LIVE.remove(mob.getUUID());
                }
            }
        }
        // Ones that left some other way (unloaded with their chunk).
        LIVE.values().removeIf(live -> now - live.born() > LIFETIME * 2L);
    }
}
