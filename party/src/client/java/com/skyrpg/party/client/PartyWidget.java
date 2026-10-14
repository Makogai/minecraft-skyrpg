package com.skyrpg.party.client;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.party.Parties;
import com.skyrpg.party.PartyPayloads.MemberInfo;
import com.skyrpg.party.SkyRpgParty;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * The party overlay: one compact row per other member with their face, name, a health bar with numbers and their status
 * (crown for the leader, a dimension tag when they are somewhere else, greyed out and "Offline" when logged off). A bar under
 * a quarter turns red and pulses.
 */
final class PartyWidget implements HudWidget {
    static final int WIDTH = 132;
    static final int ROW = 22;
    private static final int HEADER = 12;
    private static final int PAD = 4;
    private static final int FACE = 16;
    private static final int ACCENT = 0xFF000000 | Parties.COLOR;
    private static final int OFFLINE_BAR = 0xFF4A5160;

    private static final List<MemberInfo> SAMPLE = List.of(
            new MemberInfo(new UUID(1, 1), "Philip", true, 3640, 4000, "minecraft:overworld", 0, 64, 0),
            new MemberInfo(new UUID(2, 2), "Alex", true, 520, 2400, "minecraft:the_nether", 0, 64, 0),
            new MemberInfo(new UUID(3, 3), "Steve", false, 1800, 2000, "minecraft:overworld", 0, 64, 0));

    @Override
    public Identifier id() {
        return SkyRpgParty.id("party");
    }

    @Override
    public Component name() {
        return Component.literal("Party");
    }

    private static List<MemberInfo> members(boolean editing) {
        return editing && !PartyClient.inParty() ? SAMPLE : PartyClient.others();
    }

    @Override
    public int width(Font font) {
        return WIDTH;
    }

    @Override
    public int height(Font font) {
        int rows = Math.max(1, members(net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof com.skyrpg.core.client.hud.HudEditorScreen).size());
        return HEADER + rows * ROW + 2;
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.MIDDLE_LEFT;
    }

    @Override
    public int defaultX() {
        return 4;
    }

    @Override
    public int defaultY() {
        return -48;
    }

    @Override
    public boolean visible() {
        return PartyClient.inParty() && !PartyClient.others().isEmpty();
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        List<MemberInfo> members = members(editing);
        int height = HEADER + Math.max(1, members.size()) * ROW + 2;
        Draw.rounded(g, 0, 0, WIDTH, height, 0xB00C0F16);
        g.fill(0, 2, 1, height - 2, ACCENT);
        Draw.text(g, font, "PARTY", PAD + 2, 3, ACCENT);
        String count = (members.size() + 1) + "/" + PartyClient.state().capacity();
        Draw.rightText(g, font, count, WIDTH - PAD - 2, 3, Theme.DIM);
        g.fill(PAD, HEADER - 1, WIDTH - PAD, HEADER, 0x30FFFFFF);
        String mine = PartyClient.myDimension();
        for (int i = 0; i < members.size(); i++) {
            drawRow(g, font, members.get(i), HEADER + i * ROW, mine, editing && !PartyClient.inParty() && i == 0);
        }
    }

    private static void drawRow(GuiGraphicsExtractor g, Font font, MemberInfo member, int y, String mineDimension, boolean sampleLeader) {
        boolean offline = !member.online();
        boolean leader = sampleLeader || PartyClient.isLeader(member.id());
        // face
        int faceY = y + (ROW - FACE) / 2;
        PlayerFaceExtractor.extractRenderState(g, PartyClient.skin(member.id()), PAD + 1, faceY, FACE);
        if (offline) {
            g.fill(PAD + 1, faceY, PAD + 1 + FACE, faceY + FACE, 0xB0121620);
        }
        int textX = PAD + 1 + FACE + 4;
        int right = WIDTH - PAD - 2;
        int room = right - textX;

        // name line: crown, name, and where they are when that is somewhere else
        int nameColor = offline ? Theme.DIM : Theme.TEXT;
        int x = textX;
        if (leader) {
            Component crown = Component.literal(Notice.CROWN).withStyle(Style.EMPTY.withFont(Notice.FONT).withColor(offline ? 0x7A6A3A : 0xFFFFFF));
            Draw.text(g, font, crown, x, y + 2, 0xFFFFFFFF);
            x += 11;
        }
        String label = null;
        int labelWidth = 0;
        if (!offline && !member.dimension().isEmpty() && !member.dimension().equals(mineDimension)) {
            label = PartyClient.dimensionName(member.dimension());
            labelWidth = (int) Math.ceil(font.width(label) * 0.75f);
        }
        int nameRoom = right - x - (label != null ? labelWidth + 4 : 0);
        Draw.fitText(g, font, member.name(), x, y + 2, nameRoom, nameColor);
        if (label != null) {
            Draw.scaledText(g, font, Component.literal(label), right - labelWidth, y + 3, 0.75f, Draw.withAlpha(Theme.AQUA, 0.95f));
        }

        // health bar
        int barY = y + 12;
        float fraction = PartyClient.shownFraction(member);
        int color;
        String text;
        if (offline) {
            color = OFFLINE_BAR;
            text = "Offline";
        } else {
            color = healthColor(fraction);
            text = Msg.number(Math.ceil(member.hp())) + "/" + Msg.number(Math.round(member.maxHp()));
        }
        Draw.bar(g, textX, barY, room, 9, offline ? Math.max(0.04f, fraction) : fraction, color);
        String shown = Draw.numberFor(font, text, room - 4);
        Draw.fit(g, font, Component.literal(shown), textX + room / 2, barY + 1, room - 4, 1f, Draw.MIN_TEXT_SCALE, Draw.Align.CENTER,
                offline ? Theme.MUTED : Theme.TEXT, !offline);
    }

    /** Green, then gold, then red; under a quarter it pulses towards a light red. */
    static int healthColor(float fraction) {
        if (fraction < 0.25f) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(Util.getMillis() / 120.0);
            return Draw.mix(0xFFE5484D, 0xFFFF9A9A, pulse * 0.6f);
        }
        if (fraction < 0.5f) {
            return Theme.ACCENT;
        }
        return Theme.GREEN;
    }
}
