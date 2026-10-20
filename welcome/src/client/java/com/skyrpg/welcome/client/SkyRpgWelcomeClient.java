package com.skyrpg.welcome.client;

import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-only. Replaces the title screen with the SkyRPG one, runs the first-time setup once, and adds the "SkyRPG
 * title screen" and "Show the setup again" settings. Everything it can do with other mods (Sodium, Iris, Dynamic FPS,
 * Mod Menu, Zoomify, the SkyRPG wiki) is optional and looked up at runtime.
 */
public class SkyRpgWelcomeClient implements ClientModInitializer {
    public static final String MOD_ID = "skyrpg_welcome";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static Setting titleScreen;
    private static boolean wizardShown;
    private static boolean openSetupNextTick;
    /** When "Play" was last pressed, so a failed connection in the next minute gets the friendly hint. */
    private static long playStartedAt;
    private static String playAddress = "";
    private static int[] hintButtons = new int[8];

    @Override
    public void onInitializeClient() {
        WelcomeConfig.load();
        titleScreen = SkyRpgSettings.toggle("welcome.title_screen", SkyRpgSettings.INTERFACE, "SkyRPG title screen",
                "The SkyRPG start screen with the Play button, instead of the plain Minecraft one. Takes effect the next time the title screen opens", true, null)
                .keywords("menu", "main menu", "welcome", "start");
        SkyRpgSettings.action("welcome.setup", SkyRpgSettings.INTERFACE, "Show the setup again",
                "Run the first-time setup again: performance preset, shaders, resource packs, keys and the main settings", "Open",
                () -> Minecraft.getInstance().gui.setScreen(new SetupScreen(Minecraft.getInstance().gui.screen(), false)))
                .keywords("welcome", "wizard", "first time", "preset", "performance");

        // The server in everyone's Multiplayer list too (old addresses moved to the new one).
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STARTED.register(SkyRpgWelcomeClient::addToServerList);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("welcome")
                .executes(ctx -> {
                    openSetupNextTick = true;
                    return 1;
                })));
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (openSetupNextTick && minecraft.player != null) {
                openSetupNextTick = false;
                minecraft.gui.setScreen(new SetupScreen(minecraft.gui.screen(), false));
            }
        });

        // A failed "Play" gets a hint (the server may be asleep), with a way to the Multiplayer screen.
        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) -> {
            if (screen instanceof DisconnectedScreen && isPlayFailure(screen)) {
                ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) -> drawPlayHint(minecraft, s, g, mouseX, mouseY));
                ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !hintClick(minecraft, s, event.x(), event.y()));
            }
        });

        DevCapture.startIfRequested();
    }

    /** Called for every screen the game opens: the SkyRPG title (or the first-time setup) in place of the vanilla title, else null. */
    public static Screen replaceTitle(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean vanillaTitle = screen != null && screen.getClass() == TitleScreen.class;
        boolean closingToTitle = screen == null && minecraft.level == null && minecraft.player == null;
        if (!vanillaTitle && !closingToTitle) {
            return null;
        }
        boolean skyTitle = titleScreen == null || titleScreen.get();
        if (!wizardShown && !WelcomeConfig.setupDone()) {
            wizardShown = true;
            Screen behind = skyTitle ? new SkyTitleScreen() : vanillaTitle ? screen : new TitleScreen(false);
            return new SetupScreen(behind, true);
        }
        return skyTitle ? new SkyTitleScreen() : null;
    }

    /** Puts the server in the Multiplayer list once (entries with an old address are pointed at the new one). */
    private static void addToServerList(Minecraft minecraft) {
        String address = WelcomeConfig.serverAddress();
        if (address.isEmpty()) {
            return;
        }
        try {
            net.minecraft.client.multiplayer.ServerList list = new net.minecraft.client.multiplayer.ServerList(minecraft);
            list.load();
            boolean found = false;
            boolean changed = false;
            for (int i = 0; i < list.size(); i++) {
                ServerData entry = list.get(i);
                if (WelcomeConfig.isOldAddress(entry.ip)) {
                    entry.ip = address;
                    changed = true;
                }
                found |= entry.ip.equalsIgnoreCase(address);
            }
            if (!found) {
                list.add(new ServerData(WelcomeConfig.serverName(), address, ServerData.Type.OTHER), false);
                changed = true;
            }
            if (changed) {
                list.save();
            }
        } catch (RuntimeException e) {
            LOGGER.warn("Couldn't add {} to the server list", address, e);
        }
    }

    /** Joins the server directly; a failure shows the vanilla disconnect screen plus our hint. */
    static void joinServer(Screen parent, String address) {
        Minecraft minecraft = Minecraft.getInstance();
        playStartedAt = Util.getMillis();
        playAddress = address;
        ServerAddress parsed = ServerAddress.parseString(address);
        ServerData data = new ServerData(WelcomeConfig.serverName(), address, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(parent, minecraft, parsed, data, false, null);
    }

    // ------------------------------------------------------------------ failed Play

    private static boolean isPlayFailure(Screen screen) {
        return Util.getMillis() - playStartedAt < 90_000 && !playAddress.isEmpty()
                && screen.getTitle().getContents() instanceof TranslatableContents contents && contents.getKey().equals("connect.failed");
    }

    private static void drawPlayHint(Minecraft minecraft, Screen screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int w = Math.min(screen.width - 20, 340);
        int x = (screen.width - w) / 2;
        int y = 10;
        List<FormattedCharSequence> lines = Draw.wrap(minecraft.font, Component.literal(
                "Can't reach the " + WelcomeConfig.serverName() + " server. It may be starting up or offline: try again in a minute."), w - 20);
        int h = 14 + lines.size() * 10 + 4 + 18 + 8;
        Draw.shadow(g, x, y, w, h, 6, 0.5f);
        Draw.panel(g, x, y, w, h, Theme.WINDOW, Theme.BORDER);
        g.fill(x + 2, y, x + w - 2, y + 1, Theme.ACCENT);
        int ty = y + 9;
        for (FormattedCharSequence line : lines) {
            g.text(minecraft.font, line, x + 10, ty, Theme.TEXT, false);
            ty += 10;
        }
        int by = ty + 4;
        int bw = (w - 30) / 2;
        hintButtons = new int[]{x + 10, by, bw, 18, x + 20 + bw, by, bw, 18};
        for (int i = 0; i < 2; i++) {
            int bx = hintButtons[i * 4];
            boolean hover = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 18;
            Draw.panel(g, bx, by, bw, 18, hover ? Theme.CARD_HOVER : Theme.CARD, hover ? Theme.ACCENT : Theme.CARD_BORDER);
            Draw.fit(g, minecraft.font, Component.literal(i == 0 ? "Try again" : "Open Multiplayer"), bx + bw / 2, by + 5, bw - 8, Draw.Align.CENTER, Theme.TEXT);
        }
    }

    private static boolean hintClick(Minecraft minecraft, Screen screen, double mx, double my) {
        for (int i = 0; i < 2; i++) {
            int bx = hintButtons[i * 4];
            int by = hintButtons[i * 4 + 1];
            if (mx >= bx && mx < bx + hintButtons[i * 4 + 2] && my >= by && my < by + 18) {
                Ui.click(1.2f);
                if (i == 0) {
                    joinServer(parentOf(screen), playAddress);
                } else {
                    minecraft.gui.setScreen(new JoinMultiplayerScreen(parentOf(screen)));
                }
                return true;
            }
        }
        return false;
    }

    /** The screen behind the disconnect screen: the title screen. */
    private static Screen parentOf(Screen disconnected) {
        return titleScreen == null || titleScreen.get() ? new SkyTitleScreen() : new TitleScreen(false);
    }
}
