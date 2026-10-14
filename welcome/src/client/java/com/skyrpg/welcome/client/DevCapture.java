package com.skyrpg.welcome.client;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

/**
 * Dev-only: with the environment variable {@code SKYRPG_WELCOME_CAPTURE} set, the game takes screenshots of the title
 * screen and of every setup step a few seconds after start-up, into {@code screenshots/} of the game folder, at each
 * GUI scale listed in {@code SKYRPG_WELCOME_SCALES} (default "2,3"). The title screen shows before any server is
 * joined, so the server-driven /devtools screenshot cannot reach it. Does nothing for players.
 *
 * <pre>
 * SKYRPG_WELCOME_CAPTURE=1 ./gradlew :dev:runClient -Pcam
 * </pre>
 */
final class DevCapture {
    private static final String SAMPLE_NEWS = "{\"latest\":\"\",\"items\":["
            + "{\"date\":\"2026-10-14\",\"title\":\"Fishing rework\",\"text\":\"Hotspots, baits and sea creatures to hunt.\"},"
            + "{\"date\":\"2026-10-10\",\"title\":\"Tools wear out\",\"text\":\"Repair them at the anvil. Unbreakable keeps a tool for good.\"},"
            + "{\"date\":\"2026-10-03\",\"title\":\"Waystones\",\"text\":\"Pick up, move and rename them.\"}]}";

    private static final List<Runnable> QUEUE = new ArrayList<>();
    private static int wait = 150;

    private DevCapture() {
    }

    static void startIfRequested() {
        String request = System.getenv("SKYRPG_WELCOME_CAPTURE");
        if (request == null || request.isBlank()) {
            return;
        }
        String scales = System.getenv("SKYRPG_WELCOME_SCALES");
        List<Integer> list = new ArrayList<>();
        for (String part : (scales == null || scales.isBlank() ? "2,3" : scales).split(",")) {
            list.add(Integer.parseInt(part.trim()));
        }
        if (request.contains("news")) {
            NewsFeed.parse(SAMPLE_NEWS);
        }
        for (int scale : list) {
            QUEUE.add(() -> Minecraft.getInstance().options.guiScale().set(scale));
            QUEUE.add(() -> show(new SkyTitleScreen()));
            QUEUE.add(() -> shot("welcome-title-gui" + scale));
            for (int step = 0; step < SetupScreen.stepCount(); step++) {
                int index = step;
                QUEUE.add(() -> show(new SetupScreen(new SkyTitleScreen(), false).at(index)));
                QUEUE.add(() -> shot("welcome-step" + index + "-gui" + scale));
            }
        }
        if (request.contains("act")) {
            act(request.contains("join"));
        }
        QUEUE.add(() -> show(new SkyTitleScreen()));
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            if (QUEUE.isEmpty() || minecraft.gui.screen() == null && minecraft.level != null) {
                return;
            }
            if (wait-- > 0) {
                return;
            }
            // Shots wait for the screen's animations; everything else is quick.
            Runnable next = QUEUE.remove(0);
            next.run();
            wait = 40;
        });
    }

    /** Drives the wizard like a player would and logs what changed ("[welcome-test]" lines in the game log). */
    private static void act(boolean join) {
        SetupScreen[] wizard = new SetupScreen[1];
        QUEUE.add(() -> {
            wizard[0] = new SetupScreen(new SkyTitleScreen(), false).at(1);
            show(wizard[0]);
        });
        QUEUE.add(() -> {
            wizard[0].choosePreset(Preset.POTATO);
            report("after Potato");
        });
        QUEUE.add(() -> shot("welcome-act-potato"));
        QUEUE.add(() -> {
            wizard[0].choosePreset(Preset.BALANCED);
            report("after Balanced");
        });
        QUEUE.add(() -> {
            wizard[0].choosePreset(Preset.ULTRA);
            report("after Ultra");
        });
        QUEUE.add(() -> {
            wizard[0].choosePreset(Preset.HIGH);
            report("after High");
        });
        QUEUE.add(() -> {
            wizard[0].chooseShader("MakeUpUltraFast.zip");
            report("after choosing MakeUpUltraFast");
        });
        QUEUE.add(() -> report("shaders 40 ticks later"));
        QUEUE.add(() -> shot("welcome-act-shaders"));
        QUEUE.add(() -> {
            wizard[0].chooseShader("");
            report("after shaders off");
        });
        QUEUE.add(() -> show(new SkyTitleScreen()));
        QUEUE.add(() -> SkyRpgWelcomeClient.joinServer(new SkyTitleScreen(), "127.0.0.1:1"));
        for (int i = 0; i < 4; i++) {
            QUEUE.add(() -> { });
        }
        QUEUE.add(() -> shot("welcome-act-failplay"));
        QUEUE.add(() -> show(new SetupScreen(new SkyTitleScreen(), true).at(3)));
        QUEUE.add(() -> shot("welcome-act-firstrun-controls"));
        if (join) {
            QUEUE.add(() -> show(new SkyTitleScreen()));
            QUEUE.add(() -> SkyRpgWelcomeClient.joinServer(new SkyTitleScreen(), "localhost:25566"));
            for (int i = 0; i < 8; i++) {
                QUEUE.add(() -> { });
            }
            QUEUE.add(() -> shot("welcome-act-joined"));
        }
    }

    private static void report(String what) {
        Minecraft minecraft = Minecraft.getInstance();
        var o = minecraft.options;
        java.nio.file.Path config = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        String dyn = "";
        try {
            dyn = java.nio.file.Files.readString(config.resolve("dynamic_fps.json")).replaceAll("[ \n\r]+", "");
        } catch (java.io.IOException e) {
            dyn = "(none)";
        }
        SkyRpgWelcomeClient.LOGGER.info("[welcome-test] {}: view={} sim={} graphics={} particles={} clouds={} entity={} fps={} vsync={} idle={} | iris enabled={} pack={} | dynamic_fps.json={} | sodium={} iris={}",
                what, o.renderDistance().get(), o.simulationDistance().get(), o.graphicsPreset().get(), o.particles().get(), o.cloudStatus().get(),
                o.entityDistanceScaling().get(), o.framerateLimit().get(), o.enableVsync().get(), o.inactivityFpsLimit().get(),
                ModCompat.shadersEnabled(), ModCompat.shaderPack(), dyn, ModCompat.loaded("sodium"), ModCompat.irisLoaded());
    }

    private static void show(Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    private static void shot(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.gameRenderer.mainRenderTarget(), 1, message -> { });
    }
}
