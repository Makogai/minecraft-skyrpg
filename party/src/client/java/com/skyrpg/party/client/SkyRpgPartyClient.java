package com.skyrpg.party.client;

import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.tab.SkyRpgTab;
import com.skyrpg.core.client.tab.TabPanel;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.party.Parties;
import com.skyrpg.party.PartyPayloads.MemberInfo;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;

/** Party on the client: the state from the server, the overlay, the K menu page and the party marks in the tab list. */
public class SkyRpgPartyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        PartyClient.init();
        HudManager.register(new PartyWidget());
        SkyRpgMenu.registerPage(new PartyPage());
        // Tab list: a green mark left of party members, and a panel with everyone's health.
        SkyRpgTab.registerMark(id -> PartyClient.isMember(id) ? 0xFF000000 | Parties.COLOR : null);
        SkyRpgTab.registerPanel(new TabPanel("Party", 0xFF000000 | Parties.COLOR, 90, player -> {
            List<TabPanel.Line> lines = new ArrayList<>();
            if (!PartyClient.inParty()) {
                lines.add(new TabPanel.Line("Not in a party", "/party invite", Theme.DIM));
                return lines;
            }
            for (MemberInfo member : PartyClient.state().members()) {
                String hp = member.online() ? Msg.number(Math.ceil(member.hp())) + "/" + Msg.number(Math.round(member.maxHp())) : "offline";
                String name = (PartyClient.isLeader(member.id()) ? "* " : "") + member.name();
                lines.add(new TabPanel.Line(name, hp, member.online() ? Theme.GREEN : Theme.DIM));
            }
            return lines;
        }));
    }
}
