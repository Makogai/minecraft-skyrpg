package com.skyrpg.welcome.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** The pack version for the title screen footer: from a pack.toml near the game folder if there is one, else this mod's version. */
final class PackInfo {
    private static String cached;

    private PackInfo() {
    }

    static String modVersion() {
        return FabricLoader.getInstance().getModContainer("skyrpg_welcome")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
    }

    /** The version in pack.toml (packwiz), or the mod version. */
    static String packVersion() {
        if (cached == null) {
            cached = readPackToml();
            if (cached.isEmpty()) {
                cached = modVersion();
            }
        }
        return cached;
    }

    private static String readPackToml() {
        Path game = FabricLoader.getInstance().getGameDir();
        for (Path candidate : new Path[]{game.resolve("pack.toml"), game.getParent() == null ? game.resolve("pack.toml") : game.getParent().resolve("pack.toml")}) {
            if (Files.isRegularFile(candidate)) {
                try {
                    for (String line : Files.readAllLines(candidate)) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("version")) {
                            int a = trimmed.indexOf('"');
                            int b = trimmed.lastIndexOf('"');
                            if (a >= 0 && b > a) {
                                return trimmed.substring(a + 1, b);
                            }
                        }
                    }
                } catch (IOException e) {
                    // try the next one
                }
            }
        }
        return "";
    }
}
