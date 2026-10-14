package com.skyrpg.combat;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyrpg.combat.mob.WorldMobs;
import com.skyrpg.combat.slayer.Slayers;
import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.world.MobLevelling;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Danger and loot in the survival world: mob levels, Elites, damage numbers, Valor Marks, rare drops, world bosses, slayers and spawners. */
public class SkyRpgCombat implements ModInitializer {
    public static final String MOD_ID = "skyrpg_combat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CombatItems.init();
        MobLevels.init();
        // Other mods (the world mod's dungeons) set levels through core.
        MobLevelling.install(new MobLevelling.Backend() {
            @Override
            public void level(Mob mob, int level, boolean elite) {
                MobLevels.set(mob, new MobLevels.MobInfo(level, false));
                if (elite) {
                    Elites.makeElite(mob);
                }
            }

            @Override
            public void boss(Mob mob, int level, float toughness) {
                mob.setAttached(MobLevels.INFO, new MobLevels.MobInfo(level, false, toughness));
            }

            @Override
            public int levelOf(Entity entity) {
                return MobLevels.level(entity);
            }
        });
        Elites.init();
        CombatRewards.init();
        CombatEffects.init();
        TrainingDummy.init();
        WorldBoss.init();
        JsonDataLoader.register(id("world_bosses"), WorldBoss.DIRECTORY, WorldBoss::load);
        DamageNumbers.init();
        JsonDataLoader.register(id("mob_levels"), MobLevels.DIRECTORY, MobLevels::load);
        Slayers.init();
        WorldMobs.init();
        com.skyrpg.combat.mob.DeepMobs.init(); // Sporeling and Fire Beetle (The Deep)
        com.skyrpg.combat.mob.DwarvenMobs.init(); // Dwarven Construct and the Iron Thane (The Deep)
        com.skyrpg.combat.mob.NetherMobs.init(); // Bone Giant and the Magma Tyrant (the Nether)
        com.skyrpg.combat.mob.EndMobs.init(); // the Ender Wyrm (the End's final boss) and /wyrm
        // /worldboss start [id] | stop | auto on|off
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("worldboss")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("start")
                        .executes(ctx -> report(ctx.getSource(), WorldBoss.start(ctx.getSource().getServer(), null), "A world boss is coming",
                                "Can't start one (already on, no bosses, or nobody in the overworld)"))
                        .then(Commands.argument("id", StringArgumentType.word()).executes(ctx -> report(ctx.getSource(),
                                WorldBoss.start(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "id")), "A world boss is coming",
                                "Can't start that one"))))
                .then(Commands.literal("stop").executes(ctx -> report(ctx.getSource(), WorldBoss.stop(), "World boss stopped", "No world boss is on")))
                .then(Commands.literal("auto")
                        .then(Commands.literal("on").executes(ctx -> {
                            WorldBoss.setAuto(true);
                            return report(ctx.getSource(), true, "World bosses come on their own again", "");
                        }))
                        .then(Commands.literal("off").executes(ctx -> {
                            WorldBoss.setAuto(false);
                            return report(ctx.getSource(), true, "World bosses only come with /worldboss start", "");
                        })))));
        // /elite <mobs>: make mobs Elites (testing, or a surprise for a friend).
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> dispatcher.register(Commands.literal("elite")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("mobs", EntityArgument.entities()).executes(ctx -> {
                    int count = 0;
                    for (Entity entity : EntityArgument.getEntities(ctx, "mobs")) {
                        if (entity instanceof Mob mob && !mob.entityTags().contains(Elites.TAG)) {
                            Elites.makeElite(mob);
                            count++;
                        }
                    }
                    int made = count;
                    ctx.getSource().sendSuccess(() -> Component.literal("Made " + made + " Elite" + (made == 1 ? "" : "s")), true);
                    return made;
                }))));
    }

    private static int report(net.minecraft.commands.CommandSourceStack source, boolean ok, String yes, String no) {
        if (ok) {
            source.sendSuccess(() -> Component.literal(yes), true);
            return 1;
        }
        source.sendFailure(Component.literal(no));
        return 0;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
