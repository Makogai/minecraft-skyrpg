package com.skyrpg.builder.client;

import com.skyrpg.core.client.config.Setting;
import com.skyrpg.core.client.config.SkyRpgSettings;
import java.util.List;

/** The Builder category on the SkyRPG Settings page: how the 3D previews look and how sharp they are. */
final class BuilderSettings {
    static final String CATEGORY = "Builder";
    static final String REAL = "Real";
    static final String SIMPLE = "Simple";
    static final String LOW = "Low";
    static final String MEDIUM = "Medium";
    static final String HIGH = "High";

    static Setting style;
    static Setting quality;

    private BuilderSettings() {
    }

    static void register() {
        SkyRpgSettings.category(CATEGORY).description("Builder Station previews: how they look and how sharp they are");
        style = SkyRpgSettings.choice("builder.preview_style", CATEGORY, "Preview style",
                "Real: every block as it looks, torches, flowers and water included. Simple: plain coloured cubes, lighter to draw",
                List.of(REAL, SIMPLE), REAL, value -> {
                    Previews.realStyle = REAL.equals(value);
                }).section("Previews").keywords("hologram", "3d", "model", "spinning");
        quality = SkyRpgSettings.choice("builder.preview_quality", CATEGORY, "Preview quality",
                "How sharp the turning 3D previews are (blueprint cards, the station and the build panel). Lower is lighter on slower computers",
                List.of(LOW, MEDIUM, HIGH), HIGH, value -> Previews.qualityChanged())
                .section("Previews").keywords("performance", "resolution", "fps", "lag");
        Previews.realStyle = style.is(REAL);
    }

    /** The Real / Simple buttons in the station and the picker change the setting too. */
    static void toggleStyle() {
        if (style != null) {
            style.setChoice(Previews.realStyle ? SIMPLE : REAL);
        } else {
            Previews.realStyle = !Previews.realStyle;
        }
    }

    /** Picture size multiplier: Low 1×, Medium 1.5×, High 2× (a pixel per screen pixel at the usual GUI scale). */
    static double scale() {
        if (quality == null || quality.is(HIGH)) {
            return 2;
        }
        return quality.is(MEDIUM) ? 1.5 : 1;
    }

    /** More angles (smoother turning) at higher quality. */
    static int frames(int low, int high) {
        if (quality == null || quality.is(HIGH)) {
            return high;
        }
        return quality.is(MEDIUM) ? (low + high) / 2 : low;
    }
}
