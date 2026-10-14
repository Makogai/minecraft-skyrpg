package com.skyrpg.skills;

import com.skyrpg.core.data.JsonDataLoader;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatType;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.stats.Stats;
import com.skyrpg.core.world.MobTags;
import com.skyrpg.core.world.PlacedBlocks;
import com.skyrpg.skills.talent.Talents;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Skills like Farming and Mining that level up from playing, Hypixel SkyBlock style. */
public class SkyRpgSkills implements ModInitializer {
    public static final String MOD_ID = "skyrpg_skills";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        PlayerSkills.init();
        // Other mods (shops, mining) read levels through core without depending on this mod.
        SkillLevels.install((player, id) -> {
            Skill skill = Skills.get(id);
            return skill == null ? 0 : PlayerSkills.getLevel(player, skill);
        });
        SkillLevels.installXp((player, id, xp) -> {
            Skill skill = Skills.get(id);
            if (skill != null) {
                PlayerSkills.gainXp(player, skill, xp);
            }
        });
        JsonDataLoader.register(id("skills"), Skills.DIRECTORY, Skills::load);
        JsonDataLoader.register(id("skill_xp"), SkillXpSources.DIRECTORY, SkillXpSources::load);
        JsonDataLoader.register(id("talents"), Talents.DIRECTORY, Talents::load);
        Talents.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> SkillCommands.register(dispatcher));

        // Clients need the skill definitions to draw menus; send them on join and whenever data reloads.
        PayloadTypeRegistry.clientboundPlay().register(SkillDefinitionsPayload.TYPE, SkillDefinitionsPayload.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendDefinitions(handler.player));
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) ->
                server.getPlayerList().getPlayers().forEach(SkyRpgSkills::sendDefinitions));

        // Every skill level gives its rewards ("+4 Farming Fortune per level") as stats.
        Stats.registerSource(new Stats.Source() {
            @Override
            public String name() {
                return "Skills";
            }

            @Override
            public void contribute(ServerPlayer player, Stats.Contributor out) {
                for (Skill skill : Skills.all()) {
                    int level = PlayerSkills.getLevel(player, skill);
                    if (level > 0) {
                        skill.rewardsPerLevel().forEach((statId, perLevel) -> {
                            StatType stat = StatTypes.get(statId);
                            if (stat != null) {
                                out.add(stat, perLevel * level);
                            }
                        });
                    }
                }
            }
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            // Blocks players placed give no XP (no place-and-break farming).
            if (player instanceof ServerPlayer serverPlayer && !serverPlayer.isCreative() && !PlacedBlocks.isPlaced(level, pos)) {
                SkillXpSources.forBlock(state).forEach(grant -> grant(serverPlayer, grant));
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof ServerPlayer killer && !(entity instanceof Player) && !killer.isCreative()) {
                // Mobs from spawners give a quarter of the XP (no AFK spawner levelling).
                double share = entity.entityTags().contains(MobTags.FARMED) ? FARMED_XP_SHARE : 1;
                SkillXpSources.forEntity(entity.getType()).forEach(grant ->
                        grant(killer, new SkillXpSources.Grant(grant.skill(), grant.xp() * share, grant.matureOnly())));
            }
        });
    }

    private static void sendDefinitions(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, SkillDefinitionsPayload.TYPE)) {
            ServerPlayNetworking.send(player, SkillDefinitionsPayload.current());
        }
    }

    private static void grant(ServerPlayer player, SkillXpSources.Grant grant) {
        Skill skill = Skills.get(grant.skill());
        if (skill != null) {
            PlayerSkills.gainXp(player, skill, grant.xp());
        }
    }

    private static final double FARMED_XP_SHARE = 0.25;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
