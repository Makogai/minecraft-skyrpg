package com.skyrpg.mining;

import com.skyrpg.core.items.AbilityHandlers;
import com.skyrpg.core.items.Boosts;
import com.skyrpg.core.items.GearEffects;
import com.skyrpg.core.text.Msg;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * The mining gear effects (see core's {@link GearEffects}): talents, gear and boosts grant them by data; what they do is
 * here and in {@link MiningDrops}. Also the Echo Sense ability ({@code skyrpg_mining:echo_sense}).
 */
public final class MiningEffects {
    /** More Glimmer Shards (0.08 = +8%). */
    public static final Identifier SHARD_BONUS = SkyRpgMining.id("shard_bonus");
    /** Nodes you mine regrow this much faster (0.06 = 6% shorter, up to half). */
    public static final Identifier NODE_SPEED = SkyRpgMining.id("node_speed");
    /** Rare finds are this much more likely (1.0 = twice as likely). */
    public static final Identifier RARE_FINDS = SkyRpgMining.id("rare_finds");
    /** Chance that mining an ore also mines a touching ore of the same kind. */
    public static final Identifier VEIN_CHANCE = SkyRpgMining.id("vein_chance");
    /** Chance that an ore drops everything twice. */
    public static final Identifier DOUBLE_ORE = SkyRpgMining.id("double_ore");
    /** Night Vision while below y 40. */
    public static final Identifier NIGHT_VISION_BELOW = SkyRpgMining.id("night_vision_below");
    /** What you mine comes out smelted (raw ores become ingots). */
    public static final Identifier AUTO_SMELT = SkyRpgMining.id("auto_smelt");
    /** Every 50th ore gives +200 Mining Speed for 10 seconds. */
    public static final Identifier SKYWARD_STRIKES = SkyRpgMining.id("skyward_strikes");
    /** Darkness and Mining Fatigue can't touch you. */
    public static final Identifier DEEP_WARD = SkyRpgMining.id("deep_ward");
    /** A free Echo Sense every minute. */
    public static final Identifier PASSIVE_ECHO = SkyRpgMining.id("passive_echo");
    /** Soft motes of light around you. */
    public static final Identifier LUMEN_AURA = SkyRpgMining.id("lumen_aura");

    private static final int NIGHT_VISION_Y = 40;
    private static final int STRIKE_EVERY = 50;
    private static final int MAX_TRAILS = 8;
    private static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, SkyRpgMining.id("shard_ores"));
    private static final TagKey<Item> AUTO_SMELTS = TagKey.create(Registries.ITEM, SkyRpgMining.id("auto_smelt"));
    private static final Map<UUID, Integer> STRIKES = new HashMap<>();
    private static final Map<UUID, Echo> ECHOES = new HashMap<>();

    /** Trail colours by ore name ("deepslate_emberite_ore" has "emberite"); other ores use their map colour. */
    private static final Map<String, Integer> ORE_COLORS = Map.ofEntries(Map.entry("coal", 0x505050), Map.entry("copper", 0xE07A40),
            Map.entry("iron", 0xE8C8A8), Map.entry("gold", 0xFFD83A), Map.entry("redstone", 0xFF3030), Map.entry("lapis", 0x3A5CFF),
            Map.entry("diamond", 0x5CF0E8), Map.entry("emerald", 0x30E060), Map.entry("quartz", 0xF4ECE0),
            Map.entry("skyiron", 0x9CC8F0), Map.entry("duskquartz", 0xB896FF), Map.entry("emberite", 0xFF8A20),
            Map.entry("lumenite", 0xC8FAFF), Map.entry("debris", 0x8A5A48), Map.entry("hellforged", 0xFF3A1A));

    /** A running Echo Sense: the ores it found (and their colours) until a server tick, and what to say first. */
    private static final class Echo {
        final List<BlockPos> ores;
        final List<Integer> colors;
        final long until;
        Component announce;

        Echo(List<BlockPos> ores, List<Integer> colors, long until, Component announce) {
            this.ores = ores;
            this.colors = colors;
            this.until = until;
            this.announce = announce;
        }
    }

    private MiningEffects() {
    }

    static void init() {
        // "skyrpg_mining:echo_sense": params {"radius": 12, "seconds": 8}
        AbilityHandlers.register("skyrpg_mining:echo_sense", (player, params) ->
                echo(player, params.has("radius") ? params.get("radius").getAsInt() : 12, params.has("seconds") ? params.get("seconds").getAsInt() : 8, true));
    }

    static double value(ServerPlayer player, Identifier effect) {
        return GearEffects.value(player, effect);
    }

    /** Every 10 ticks: echo trails; every second: the timed effects. */
    static void tick(ServerPlayer player, long tick) {
        Echo echo = ECHOES.get(player.getUUID());
        if (echo != null) {
            if (tick > echo.until) {
                ECHOES.remove(player.getUUID());
            } else {
                trails(player, echo);
            }
        }
        if (tick % 20 != 0) {
            return;
        }
        if (value(player, NIGHT_VISION_BELOW) > 0 && player.getY() < NIGHT_VISION_Y) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
        }
        if (value(player, DEEP_WARD) > 0) {
            player.removeEffect(MobEffects.DARKNESS);
            player.removeEffect(MobEffects.MINING_FATIGUE);
        }
        if (value(player, PASSIVE_ECHO) > 0 && tick % 1200 == 0) {
            echo(player, 10, 4, false);
        }
        if (value(player, LUMEN_AURA) > 0) {
            player.level().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 2, 0.6, 0.6, 0.6, 0.01);
        }
    }

    /** After a player mined an ore: count towards Skyward Strikes. */
    static void oreMined(ServerPlayer player) {
        if (value(player, SKYWARD_STRIKES) <= 0) {
            return;
        }
        int count = STRIKES.merge(player.getUUID(), 1, Integer::sum);
        if (count >= STRIKE_EVERY) {
            STRIKES.put(player.getUUID(), 0);
            Boosts.give(player, "skyward_strikes", Component.literal("Skyward Strikes"), Map.of("mining_speed", 200.0), Map.of(), 200);
            player.sendOverlayMessage(Msg.text("⚡ Skyward Strikes! +200 Mining Speed for 10s", ChatFormatting.AQUA));
            Msg.playSound(player, SoundEvents.BELL_BLOCK, 0.6f, 1.6f);
        }
    }

    /**
     * Smelts a drop if the player has auto-smelt and it's in {@code #skyrpg_mining:auto_smelt} (raw ores; not logs or
     * sand), by its furnace recipe or else its blast furnace one. Anything else comes back as it was.
     */
    static ItemStack smelt(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty() || !stack.is(AUTO_SMELTS) || value(player, AUTO_SMELT) <= 0) {
            return stack;
        }
        ServerLevel level = player.level();
        SingleRecipeInput input = new SingleRecipeInput(stack.copyWithCount(1));
        RecipeManager recipes = level.getServer().getRecipeManager();
        ItemStack result = recipes.getRecipeFor(RecipeType.SMELTING, input, level).map(holder -> holder.value().assemble(input))
                .or(() -> recipes.getRecipeFor(RecipeType.BLASTING, input, level).map(holder -> holder.value().assemble(input)))
                .orElse(ItemStack.EMPTY);
        if (result.isEmpty()) {
            return stack;
        }
        return result.copyWithCount(Math.min(result.getMaxStackSize(), result.getCount() * stack.getCount()));
    }

    /**
     * Echo Sense: finds ores within {@code radius} and, for {@code seconds}, shows a short coloured trail from you towards
     * the nearest ones (up to 8), with a count on the action bar.
     */
    static boolean echo(ServerPlayer player, int radius, int seconds, boolean loud) {
        ServerLevel level = player.level();
        BlockPos center = player.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (level.getBlockState(pos).is(ORES)) {
                found.add(pos.immutable());
            }
        }
        if (found.isEmpty()) {
            if (loud) {
                player.sendOverlayMessage(Msg.text("Echo Sense: no ores within " + radius + " blocks", ChatFormatting.GRAY));
            }
            return true;
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        List<BlockPos> nearest = found.subList(0, Math.min(MAX_TRAILS, found.size()));
        List<Integer> colors = new ArrayList<>();
        for (BlockPos pos : nearest) {
            colors.add(color(level, pos));
        }
        BlockPos first = nearest.getFirst();
        // Said on the first trail tick, so the ability's own "Echo Sense!" doesn't cover it.
        Component announce = Msg.text("Echo Sense: " + found.size() + " ore" + (found.size() == 1 ? "" : "s") + " nearby, nearest "
                + Math.round(Math.sqrt(first.distSqr(center))) + " blocks away", ChatFormatting.AQUA);
        ECHOES.put(player.getUUID(), new Echo(List.copyOf(nearest), colors, level.getServer().getTickCount() + seconds * 20L, announce));
        Msg.playSound(player, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.7f);
        return true;
    }

    /** A dotted line from just in front of you towards each ore, up to 6 blocks long; only you see it. */
    private static void trails(ServerPlayer player, Echo echo) {
        if (echo.announce != null) {
            player.sendOverlayMessage(echo.announce);
            echo.announce = null;
        }
        Vec3 from = player.getEyePosition().add(0, -0.4, 0);
        for (int i = 0; i < echo.ores.size(); i++) {
            Vec3 ore = Vec3.atCenterOf(echo.ores.get(i));
            Vec3 direction = ore.subtract(from).normalize();
            double length = Math.min(6.0, from.distanceTo(ore) - 0.6);
            DustParticleOptions dust = new DustParticleOptions(echo.colors.get(i), 0.75f);
            for (double step = 1.2; step <= length; step += 0.5) {
                Vec3 at = from.add(direction.scale(step));
                player.level().sendParticles(player, dust, true, false, at.x, at.y, at.z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** A trail colour per ore: by its name (see ORE_COLORS), else its map colour, brightened. */
    private static int color(ServerLevel level, BlockPos pos) {
        String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
        for (Map.Entry<String, Integer> entry : ORE_COLORS.entrySet()) {
            if (path.contains(entry.getKey()) && !(entry.getKey().equals("quartz") && path.contains("duskquartz"))) {
                return entry.getValue();
            }
        }
        int rgb = level.getBlockState(pos).getMapColor(level, pos).col;
        int r = Math.min(255, ((rgb >> 16) & 0xFF) + 60);
        int g = Math.min(255, ((rgb >> 8) & 0xFF) + 60);
        int b = Math.min(255, (rgb & 0xFF) + 60);
        return r << 16 | g << 8 | b;
    }
}
