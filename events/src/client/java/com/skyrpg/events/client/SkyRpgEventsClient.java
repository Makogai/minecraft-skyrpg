package com.skyrpg.events.client;

import com.skyrpg.core.client.hud.Banners;
import com.skyrpg.core.client.hud.HudManager;
import com.skyrpg.core.client.menu.SkyRpgMenu;
import com.skyrpg.core.client.tab.SkyRpgTab;
import com.skyrpg.core.client.tab.TabPanel;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.events.contract.ContractPayloads;
import com.skyrpg.events.net.EventPayloads;
import com.skyrpg.events.net.ShowcasePayloads;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * The client side of SkyRPG Events: the Events HUD widget, banners for announcements, the winner
 * board, the sidebar and an Events panel in the tab list.
 */
public class SkyRpgEventsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HudManager.register(new EventWidget());
        HudManager.register(new ContractsPage.Widget());
        SkyRpgMenu.registerPage(new ContractsPage());
        ClientPlayNetworking.registerGlobalReceiver(ContractPayloads.Board.TYPE, (payload, context) -> ContractsPage.receive(payload));
        WinnerBoard.init();
        SplashScreen.install();
        com.skyrpg.events.client.billboard.BillboardClient.init();
        ClientPlayNetworking.registerGlobalReceiver(ShowcasePayloads.Splash.TYPE, (payload, context) ->
                context.client().execute(() -> SplashScreen.offer(payload.json())));
        Sidebar.init();

        ClientPlayNetworking.registerGlobalReceiver(EventPayloads.State.TYPE, (payload, context) -> ClientEvents.apply(payload));
        ClientPlayNetworking.registerGlobalReceiver(EventPayloads.Results.TYPE, (payload, context) -> WinnerBoard.show(payload));
        ClientPlayNetworking.registerGlobalReceiver(EventPayloads.ShowBoard.TYPE, (payload, context) -> Sidebar.onBoard(payload));
        ClientPlayNetworking.registerGlobalReceiver(EventPayloads.Announce.TYPE, (payload, context) -> {
            Banners.show(new Banners.Banner(Ui.icon(payload.icon()), payload.label(), payload.title(), payload.subtitle(), payload.color()));
            payload.sound().ifPresent(sound -> Ui.play(sound, payload.pitch(), payload.volume()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientEvents.clear();
            WinnerBoard.clear();
            Sidebar.clear();
        });

        SkyRpgTab.registerPanel(new TabPanel("Events", Theme.PURPLE, 50, player -> {
            List<TabPanel.Line> lines = new ArrayList<>();
            // Two lines per event, so the name gets the panel's width: "Timber Rush ... 4:02", then "#3 · 1,234 logs".
            for (EventPayloads.EventView view : ClientEvents.events()) {
                long toStart = ClientEvents.millisToStart(view);
                String place = view.selfPlace() > 0 ? "#" + view.selfPlace() + " · " + Msg.number(view.selfScore()) + " " + view.unit()
                        : view.joined() ? "No score yet" : "Not joined";
                String clock = toStart > 0 ? "in " + Ui.clock(toStart) : Ui.clock(ClientEvents.millisLeft(view));
                lines.add(new TabPanel.Line(view.name(), clock, view.joined() ? view.color() : Theme.MUTED));
                lines.add(new TabPanel.Line("  " + place, "", Theme.DIM));
            }
            if (lines.isEmpty()) {
                lines.add(new TabPanel.Line("No event running", "", Theme.DIM));
            }
            return lines;
        }));
    }
}
