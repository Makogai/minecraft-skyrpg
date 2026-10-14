package com.skyrpg.fishing;

import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * What the fisher sees and hears: a big floating text display over the bobber (no client code needed): a yellow "!" while
 * a fish swims in and a bold red "!!!" at the bite, with a high "ding" for the fisher only. Bait shows as a small looping particle at the bobber.
 */
public final class BiteCues {
    public static final int NONE = 0;
    public static final int APPROACH = 1;
    public static final int BITE = 2;

    private BiteCues() {
    }

    private record Cue(FishingHook hook, Display.TextDisplay display) {
    }

    private static final String TAG = "skyrpg_bite_cue";
    private static final List<Cue> CUES = new ArrayList<>();

    public static void show(FishingHook hook, int state) {
        clear(hook);
        if (state == NONE || !(hook.level() instanceof ServerLevel level)) {
            return;
        }
        boolean bite = state == BITE;
        if (bite && hook.getPlayerOwner() instanceof ServerPlayer player) {
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.7f);
            Msg.playSound(player, SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1.5f);
        }
        // A big floating text over the bobber (a name tag was too small to read from the bank).
        Component text = Component.literal(bite ? "!!!" : "!").withStyle(bite ? ChatFormatting.RED : ChatFormatting.YELLOW, ChatFormatting.BOLD);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:text_display");
        ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE, text).result().ifPresent(encoded -> tag.put("text", encoded));
        tag.putString("billboard", "center");
        tag.putBoolean("see_through", true);
        tag.putBoolean("shadow", true);
        tag.putInt("background", 0);
        tag.putFloat("view_range", 1.5f);
        CompoundTag transformation = new CompoundTag();
        float scale = bite ? 4f : 3f;
        transformation.put("scale", floats(scale, scale, scale));
        transformation.put("translation", floats(0, 0, 0));
        transformation.put("left_rotation", floats(0, 0, 0, 1));
        transformation.put("right_rotation", floats(0, 0, 0, 1));
        tag.put("transformation", transformation);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        double x = hook.getX();
        double y = hook.getY() + (bite ? 1.4 : 1.1);
        double z = hook.getZ();
        Entity entity = EntityType.loadEntityRecursive(tag, level, new EntitySpawnRequest(EntitySpawnReason.COMMAND, false), e -> {
            e.snapTo(x, y, z, 0, 0);
            return e;
        });
        if (entity instanceof Display.TextDisplay display) {
            CUES.add(new Cue(hook, display));
            level.tryAddFreshEntityWithPassengers(display);
        }
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    private static void clear(FishingHook hook) {
        CUES.removeIf(cue -> {
            if (cue.hook() == hook) {
                cue.display().discard();
                return true;
            }
            return false;
        });
    }

    /** Every tick: marks of bobbers that are gone leave with them (and strays from a saved chunk are swept now and then). */
    public static void tick(MinecraftServer server) {
        CUES.removeIf(cue -> {
            if (cue.hook().isRemoved() || cue.display().isRemoved()) {
                cue.display().discard();
                return true;
            }
            return false;
        });
        if (server.getTickCount() % 100 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                for (Display.TextDisplay stray : level.getEntities(EntityTypes.TEXT_DISPLAY,
                        display -> display.entityTags().contains(TAG) && CUES.stream().noneMatch(cue -> cue.display() == display))) {
                    stray.discard();
                }
            }
        }
    }

    public static void stop() {
        CUES.forEach(cue -> cue.display().discard());
        CUES.clear();
    }

    /** The bait's particle at the bobber, a little every few ticks. */
    public static void baitParticles(FishingHook hook, FishingItems.BaitItem bait) {
        if (bait == null || hook.tickCount % 6 != 0 || !(hook.level() instanceof ServerLevel level)) {
            return;
        }
        ParticleOptions particle = bait.particle();
        level.sendParticles(particle, hook.getX(), hook.getY() + 0.15, hook.getZ(), 2, 0.12, 0.05, 0.12, 0.01);
    }
}
