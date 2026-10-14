package com.skyrpg.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.stats.HealthDisplay;
import com.skyrpg.core.stats.IncomingDamage;
import com.skyrpg.core.world.MobTags;
import io.netty.buffer.ByteBuf;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

/**
 * Mob levels: every hostile mob that spawns in the survival world gets a level from how far it is from world spawn,
 * how deep it is and which dimension it's in. Each level adds health and damage, and pays more Combat XP and loot (see
 * {@link CombatRewards}). The level (and whether it's an Elite) is an entity attachment, saved and synced to clients for
 * the nameplates. The numbers are data: {@code data/<ns>/skyrpg/mob_levels/*.json} (the last one loaded wins), so a
 * public server can make the world harsher without a rebuild.
 */
public final class MobLevels {
    public static final String DIRECTORY = "skyrpg/mob_levels";

    /**
     * A mob's level, whether it's an Elite, and its toughness: a boss tougher than vanilla's health cap takes
     * 1/toughness of the damage, and its nameplate shows its health times that.
     */
    public record MobInfo(int level, boolean elite, float toughness) {
        public static final Codec<MobInfo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("level").forGetter(MobInfo::level),
                Codec.BOOL.optionalFieldOf("elite", false).forGetter(MobInfo::elite),
                Codec.FLOAT.optionalFieldOf("toughness", 1f).forGetter(MobInfo::toughness)).apply(instance, MobInfo::new));
        public static final StreamCodec<ByteBuf, MobInfo> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, MobInfo::level, ByteBufCodecs.BOOL, MobInfo::elite, ByteBufCodecs.FLOAT, MobInfo::toughness,
                MobInfo::new);

        public MobInfo(int level, boolean elite) {
            this(level, elite, 1f);
        }
    }

    public static final AttachmentType<MobInfo> INFO = AttachmentRegistry.create(SkyRpgCombat.id("mob"), builder -> builder
            .persistent(MobInfo.CODEC)
            .syncWith(MobInfo.STREAM_CODEC, AttachmentSyncPredicate.all()));

    /** The tuning, from data. */
    public record Tuning(double blocksPerLevel, int surfaceCap, int belowZero, int deep, int netherBase, int endBase, int max,
                         double healthPerLevel, double damagePerLevel) {
        static final Tuning DEFAULT = new Tuning(120, 30, 5, 10, 20, 40, 50, 0.08, 0.05);
    }

    private static final Identifier HEALTH = SkyRpgCombat.id("level_health");
    private static final Identifier DAMAGE = SkyRpgCombat.id("level_damage");
    private static Tuning tuning = Tuning.DEFAULT;

    private MobLevels() {
    }

    /** Registers the attachment during mod init, on both sides, so it syncs from the first connection. */
    static void init() {
        // Loading the class registers INFO. Tough bosses take less damage (core's IncomingDamage, applied by the stats mod).
        IncomingDamage.register(entity -> {
            MobInfo info = entity.getAttached(INFO);
            return info == null || info.toughness() <= 1 ? 1 : 1 / info.toughness();
        });
        HealthDisplay.register(entity -> {
            MobInfo info = entity.getAttached(INFO);
            return info == null ? 1 : Math.max(1, info.toughness());
        });
    }

    /** Marks a mob from a spawner as farmed (see core's MobTags): normal loot, little XP, no SkyRPG rewards. */
    public static void markFarmed(Mob mob, EntitySpawnReason reason) {
        if (EntitySpawnReason.isSpawner(reason)) {
            mob.addTag(MobTags.FARMED);
        }
    }

    public static boolean farmed(Entity entity) {
        return entity.entityTags().contains(MobTags.FARMED);
    }

    static void load(Map<Identifier, JsonElement> files) {
        Tuning loaded = Tuning.DEFAULT;
        for (Map.Entry<Identifier, JsonElement> file : files.entrySet()) {
            try {
                JsonObject json = file.getValue().getAsJsonObject();
                Tuning d = Tuning.DEFAULT;
                loaded = new Tuning(num(json, "blocks_per_level", d.blocksPerLevel()), (int) num(json, "surface_cap", d.surfaceCap()),
                        (int) num(json, "below_y0", d.belowZero()), (int) num(json, "below_y_minus32", d.deep()),
                        (int) num(json, "nether_base", d.netherBase()), (int) num(json, "end_base", d.endBase()), (int) num(json, "max", d.max()),
                        num(json, "health_per_level", d.healthPerLevel()), num(json, "damage_per_level", d.damagePerLevel()));
            } catch (RuntimeException e) {
                SkyRpgCombat.LOGGER.warn("Skipping mob levels {}: {}", file.getKey(), e.getMessage());
            }
        }
        tuning = loaded;
    }

    private static double num(JsonObject json, String key, double fallback) {
        return json.has(key) ? json.get(key).getAsDouble() : fallback;
    }

    /** The mob's info, or null if it has no level (passive mobs, the hub, spawn eggs and commands). */
    public static MobInfo info(Entity entity) {
        return entity.getAttached(INFO);
    }

    public static int level(Entity entity) {
        MobInfo info = info(entity);
        return info == null ? 0 : info.level();
    }

    /** From the spawn hook: give a freshly spawned hostile mob its level. */
    public static void assign(Mob mob, EntitySpawnReason reason) {
        if (!(mob instanceof Enemy) || !(mob.level() instanceof ServerLevel level) || info(mob) != null || !levelled(reason)
                || level.dimension().identifier().getNamespace().equals("skyrpg_hub")) {
            return;
        }
        set(mob, new MobInfo(levelAt(level, mob.blockPosition()), false));
    }

    /** Natural spawns, and mobs from commands and spawn eggs too, so test mobs look and fight like real ones. */
    private static boolean levelled(EntitySpawnReason reason) {
        return java.util.EnumSet.of(EntitySpawnReason.NATURAL, EntitySpawnReason.CHUNK_GENERATION, EntitySpawnReason.SPAWNER,
                EntitySpawnReason.STRUCTURE, EntitySpawnReason.JOCKEY, EntitySpawnReason.EVENT, EntitySpawnReason.REINFORCEMENT,
                EntitySpawnReason.PATROL, EntitySpawnReason.TRIAL_SPAWNER, EntitySpawnReason.MOB_SUMMONED, EntitySpawnReason.COMMAND,
                EntitySpawnReason.SPAWN_ITEM_USE, EntitySpawnReason.DISPENSER).contains(reason);
    }

    /** What level a mob spawning here gets. */
    public static int levelAt(ServerLevel level, BlockPos pos) {
        Tuning t = tuning;
        BlockPos spawn = level.getRespawnData().pos();
        double distance = Math.sqrt(Math.pow(pos.getX() - spawn.getX(), 2) + Math.pow(pos.getZ() - spawn.getZ(), 2));
        int result;
        if (level.dimension() == Level.NETHER) {
            result = t.netherBase() + (int) (distance / (t.blocksPerLevel() * 1.5));
        } else if (level.dimension() == Level.END) {
            result = t.endBase() + (int) (distance / (t.blocksPerLevel() * 4));
        } else {
            result = Math.min(t.surfaceCap(), 1 + (int) (distance / t.blocksPerLevel()));
            if (pos.getY() < -32) {
                result += t.deep();
            } else if (pos.getY() < 0) {
                result += t.belowZero();
            }
        }
        // Danger regions and the like add their own levels, past the usual cap.
        return Math.max(1, Math.min(t.max(), result) + com.skyrpg.core.world.MobLevelling.bonusAt(level, pos));
    }

    /** Sets a mob's level and Elite flag, and its health and damage to match. */
    public static void set(Mob mob, MobInfo info) {
        mob.setAttached(INFO, info);
        int above = info.level() - 1;
        boost(mob.getAttribute(Attributes.MAX_HEALTH), HEALTH, above * tuning.healthPerLevel());
        boost(mob.getAttribute(Attributes.ATTACK_DAMAGE), DAMAGE, above * tuning.damagePerLevel());
        mob.setHealth(mob.getMaxHealth());
    }

    private static void boost(AttributeInstance attribute, Identifier id, double amount) {
        if (attribute != null && amount > 0) {
            attribute.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
}
