package com.skyrpg.combat.mob;

import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.core.world.MobLevelling;
import com.skyrpg.vfx.Vfx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Ender Wyrm's summoning (docs/design/end_game.md). A player uses a Void Sigil in the End ({@link VoidSigilItem}); if the
 * Ender Dragon is dead and no Wyrm (or ritual) is running in the dimension, a 6.5 second ritual plays over the spot: a
 * spiral of void light rises from a growing ring, the ground and screens shake, and the sky tears open. Then
 * {@link #summon} brings the Wyrm in. If everyone has left or died by then the ritual fizzles and the sigil is handed back.
 * Ops can also use {@code /wyrm summon} (no ritual, any dimension).
 */
public final class EnderWyrms {
    /** Ritual length in ticks. */
    private static final int RITUAL_TICKS = 130;
    private static final double RITUAL_RADIUS = 60;

    private record Ritual(ServerLevel level, Vec3 center, UUID summoner, long start) {
    }

    private static final List<Ritual> RITUALS = new ArrayList<>();

    private EnderWyrms() {
    }

    static void init() {
        ServerTickEvents.END_SERVER_TICK.register(EnderWyrms::tickRituals);
    }

    /** Whether a Wyrm is already alive in this level. */
    public static boolean active(ServerLevel level) {
        return !level.getEntities(EndMobs.ENDER_WYRM, new AABB(-3.0E7, level.getMinY(), -3.0E7, 3.0E7, level.getMaxY(), 3.0E7), EnderWyrmEntity::isAlive).isEmpty();
    }

    /** Whether a summoning ritual is playing in this level. */
    public static boolean ritualActive(ServerLevel level) {
        return RITUALS.stream().anyMatch(r -> r.level == level);
    }

    /** Why the sigil can't be used here, or null if it can. */
    public static String cannotStart(ServerLevel level, ServerPlayer player) {
        if (level.dimension() != Level.END) {
            return "The sigil is cold. It only answers in the End.";
        }
        EnderDragonFight fight = level.getDragonFight();
        if (fight != null && (!fight.hasPreviouslyKilledDragon() || !level.getDragons().isEmpty())) {
            return "The Ender Dragon's roar still fills the air. Slay it first.";
        }
        if (active(level)) {
            return "An Ender Wyrm is already stirring here.";
        }
        if (ritualActive(level)) {
            return "The void is already answering.";
        }
        return null;
    }

    /** Starts the ritual over the player; the caller spends the sigil. */
    public static boolean startRitual(ServerLevel level, ServerPlayer player) {
        if (cannotStart(level, player) != null) {
            return false;
        }
        RITUALS.add(new Ritual(level, player.position(), player.getUUID(), level.getGameTime()));
        return true;
    }

    private static void tickRituals(MinecraftServer server) {
        for (Iterator<Ritual> it = RITUALS.iterator(); it.hasNext(); ) {
            Ritual ritual = it.next();
            ServerLevel level = ritual.level;
            int age = (int) (level.getGameTime() - ritual.start);
            List<ServerPlayer> near = nearby(level, ritual.center, RITUAL_RADIUS);
            if (age >= 40 && near.isEmpty()) {
                it.remove();
                fizzle(server, ritual);
                continue;
            }
            playRitual(level, ritual.center, age, near);
            if (age >= RITUAL_TICKS) {
                it.remove();
                if (summon(level, ritual.center, ritual.summoner) == null) {
                    fizzle(server, ritual);
                }
            }
        }
    }

    private static List<ServerPlayer> nearby(ServerLevel level, Vec3 centre, double radius) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && player.distanceToSqr(centre) < radius * radius) {
                list.add(player);
            }
        }
        return list;
    }

    private static void fizzle(MinecraftServer server, Ritual ritual) {
        ServerLevel level = ritual.level;
        level.sendParticles(ParticleTypes.SMOKE, ritual.center.x, ritual.center.y + 1, ritual.center.z, 30, 1.5, 1, 1.5, 0.02);
        level.playSound(null, ritual.center.x, ritual.center.y, ritual.center.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2f, 0.6f);
        ServerPlayer summoner = server.getPlayerList().getPlayer(ritual.summoner);
        if (summoner != null) {
            giveBack(summoner);
            summoner.sendSystemMessage(Notice.of(Notice.Kind.COMBAT).tag("ENDER WYRM").build(Msg.text("The void lost interest. Your Void Sigil is returned.", ChatFormatting.GRAY)));
        }
    }

    /** Hands a Void Sigil back (a ritual that fizzled, or a Wyrm that withdrew with nobody left to fight it). */
    static void giveBack(ServerPlayer player) {
        ItemStack sigil = new ItemStack(EndMobs.VOID_SIGIL);
        if (!player.getInventory().add(sigil)) {
            player.drop(sigil, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
    }

    /** One tick of the ritual: a rising spiral over a ring that shrinks, rumbling ground, a flash and a bolt before it ends. */
    private static void playRitual(ServerLevel level, Vec3 c, int age, List<ServerPlayer> near) {
        if (age == 0) {
            Component text = Notice.of(Notice.Kind.COMBAT).icon(Notice.SKULL).tag("ENDER WYRM").color(0xB06AE8)
                    .build(Msg.text("The Void Sigil burns violet. ", ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC).append(Msg.text("Something vast turns in the dark...", ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            for (ServerPlayer player : near) {
                player.sendSystemMessage(text);
                player.connection.send(new ClientboundSetTitlesAnimationPacket(8, 50, 20));
                player.connection.send(new ClientboundSetSubtitleTextPacket(Msg.text("Hold your ground", ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                player.connection.send(new ClientboundSetTitleTextPacket(Msg.text("The void stirs", ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
            }
            level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3f, 0.5f);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 1.5f, 0.5f);
        }
        double progress = Math.min(1.0, age / (double) RITUAL_TICKS);
        // the ring on the ground closes in
        double ring = 9 - 6 * progress;
        int points = 36;
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points + age * 0.04;
            level.sendParticles(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL, c.x + Math.cos(a) * ring, c.y + 0.15, c.z + Math.sin(a) * ring, 1, 0, 0.02, 0, 0.0);
        }
        // two spirals climb into the sky
        for (int arm = 0; arm < 2; arm++) {
            for (int k = 0; k < 4; k++) {
                double h = ((age * 0.45 + k * 4) % 26);
                double a = age * 0.22 + arm * Math.PI + h * 0.5;
                double r = 2.2 * (1 - h / 40);
                level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1f), c.x + Math.cos(a) * r, c.y + h, c.z + Math.sin(a) * r, 0, 0, 0.05, 0, 1.0);
                level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + h, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
            }
        }
        level.sendParticles(ParticleTypes.PORTAL, c.x, c.y + 1, c.z, 6, 1.2, 1.2, 1.2, 0.6);
        if (age % 20 == 0) {
            float pitch = 0.5f + (float) progress * 0.9f;
            level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 2.5f, pitch);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDERMAN_STARE, SoundSource.HOSTILE, 1.5f, pitch * 0.7f);
            for (ServerPlayer player : near) {
                Vfx.shake(player, 0.15f + (float) progress * 0.5f, 22);
            }
        }
        if (age == 90) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6f, 0.5f);
            for (ServerPlayer player : near) {
                Vfx.flash(player, 0x55B06AE8, 10);
                Vfx.shake(player, 1.2f, 30);
            }
        }
        if (age == 110 || age == 122) {
            strikeBolt(level, c.add(Math.cos(age) * 5, 0, Math.sin(age) * 5));
        }
        if (age == RITUAL_TICKS - 1) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 14, c.z, 1, 0, 0, 0, 0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4f, 0.7f);
            for (ServerPlayer player : near) {
                Vfx.flash(player, 0x88FFFFFF, 12);
                Vfx.shake(player, 1.6f, 30);
            }
        }
    }

    private static void strikeBolt(ServerLevel level, Vec3 at) {
        LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.snapTo(at.x, at.y, at.z);
            level.addFreshEntity(bolt);
        }
    }

    /**
     * Summons the Ender Wyrm to fight over {@code arenaCentre} (where the sigil was used): it appears 28 blocks off and 18 above
     * and circles the centre. Returns null if one is already alive here.
     */
    public static EnderWyrmEntity summon(ServerLevel level, Vec3 arenaCentre) {
        return summon(level, arenaCentre, null);
    }

    /** As above; {@code summoner} (may be null) gets the sigil back if the Wyrm withdraws with nobody left to fight it. */
    public static EnderWyrmEntity summon(ServerLevel level, Vec3 arenaCentre, UUID summoner) {
        if (active(level)) {
            return null;
        }
        EnderWyrmEntity wyrm = EndMobs.ENDER_WYRM.create(level, EntitySpawnReason.EVENT);
        if (wyrm == null) {
            return null;
        }
        wyrm.snapTo(arenaCentre.x + 28, arenaCentre.y + 18, arenaCentre.z, 90f, 0f);
        wyrm.setCenter(arenaCentre);
        wyrm.setSummoner(summoner);
        wyrm.finalizeSpawn(level, level.getCurrentDifficultyAt(wyrm.blockPosition()), EntitySpawnReason.EVENT, null);
        wyrm.setPersistenceRequired();
        wyrm.setHealth(wyrm.getMaxHealth());
        // beyond vanilla's 1024 health cap it takes half damage instead (see MobLevelling.boss): about 2000 effective
        MobLevelling.boss(wyrm, 100, 2f);
        level.addFreshEntity(wyrm);
        wyrm.announce(level);
        return wyrm;
    }

    /** Used by the entity's title helper: the id the Wyrm's kills are filed under (BossKills, quests). */
    public static final Identifier BOSS_ID = Identifier.fromNamespaceAndPath("skyrpg_combat", "ender_wyrm");
}
