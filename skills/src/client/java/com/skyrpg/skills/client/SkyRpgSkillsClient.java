package com.skyrpg.skills.client;

import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.menu.ProfileStat;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.tab.SkyRpgTab;
import com.skyrpg.core.client.tab.TabPanel;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.skills.SkillLevels;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.skills.PlayerSkills;
import com.skyrpg.skills.Skill;
import com.skyrpg.skills.SkillDefinitionsPayload;
import com.skyrpg.skills.Skills;
import com.skyrpg.skills.talent.TalentPayloads;
import com.skyrpg.skills.talent.Talents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class SkyRpgSkillsClient implements ClientModInitializer {
    /** The server's "+4 Farming (54/125)" action-bar text; redundant once the HUD widget shows it. */
    private static final Pattern XP_ACTION_BAR = Pattern.compile("^\\+[\\d,.]+ [A-Za-z ]+ \\((MAX|[\\d,.]+/[\\d,.]+)\\)$");

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SkillDefinitionsPayload.TYPE, (payload, context) ->
                ClientSkills.set(payload.skills().stream().map(SkillDefinitionsPayload.Entry::toSkill).toList()));
        ClientTickEvents.END_CLIENT_TICK.register(SkillTracker::tick);
        // On the client, skill definitions come from the server's payload rather than data packs (e.g. shops' level locks).
        SkillLevels.install((player, id) -> {
            Skill skill = player.level().isClientSide()
                    ? ClientSkills.all().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null)
                    : Skills.get(id);
            return skill == null ? 0 : PlayerSkills.getLevel(player, skill);
        });

        SkyRpgMenu.registerPage(new SkillsPage());
        SkyRpgMenu.registerPage(new TalentsPage());
        ClientPlayNetworking.registerGlobalReceiver(TalentPayloads.Definitions.TYPE, (payload, context) -> Talents.replace(payload.files()));
        SkyRpgMenu.registerProfileStat(new ProfileStat("Skill Average", StatTypes.LEVEL_ICON, Theme.AQUA, 1,
                player -> String.format(Locale.ROOT, "%.1f", ClientSkills.average(player))));
        SkyRpgTab.registerPanel(new TabPanel("Skills", Theme.AQUA, 10, player -> {
            List<TabPanel.Line> lines = new ArrayList<>();
            for (Skill skill : ClientSkills.all()) {
                lines.add(new TabPanel.Line(skill.name(), Msg.roman(PlayerSkills.getLevel(player, skill)), 0xFF000000 | skill.color().getValue()));
            }
            lines.add(new TabPanel.Line("Average", String.format(Locale.ROOT, "%.1f", ClientSkills.average(player)), Theme.TEXT));
            return lines;
        }));

        HudManager.register(new SkillProgressWidget());
        Setting hideActionBar = SkyRpgSettings.toggle("skills.hide_action_bar", SkyRpgSettings.HUD, "Hide skill XP text",
                "The skill progress box replaces the \"+4 Farming\" text above the hotbar", true, null);
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) ->
                !(overlay && hideActionBar.get() && XP_ACTION_BAR.matcher(message.getString()).matches()));
    }
}
