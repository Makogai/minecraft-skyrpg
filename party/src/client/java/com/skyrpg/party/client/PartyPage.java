package com.skyrpg.party.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.skyrpg.core.client.menu.MenuPage;
import com.skyrpg.core.client.menu.PageContext;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.text.Msg;
import com.skyrpg.core.text.Notice;
import com.skyrpg.party.Parties;
import com.skyrpg.party.PartyPayloads;
import com.skyrpg.party.PartyPayloads.InviteInfo;
import com.skyrpg.party.PartyPayloads.MemberInfo;
import com.skyrpg.party.SkyRpgParty;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The K menu's Party page: the members (face, crown, online or offline, health, where they are; click one to set a waypoint
 * to them), buttons to kick, promote, leave and disband, a name field to invite, and pending invites to accept or deny.
 * The server does everything ({@link PartyPayloads.Action}); this page only asks.
 */
public final class PartyPage implements MenuPage {
    private static final int GAP = 4;
    private static final int CARD_H = 30;
    private static final int HEADING = 14;
    private static final int ACCENT = 0xFF000000 | Parties.COLOR;
    private static final int MAX_NAME = 16;

    private enum Act { TRACK, KICK, PROMOTE, LEAVE, DISBAND, CHAT, INVITE, ACCEPT, DENY, FOCUS }

    private record Hit(int x, int y, int w, int h, Act act, String arg) {
    }

    private final List<Hit> hits = new ArrayList<>();
    private double scroll;
    private int contentHeight;
    private int areaX;
    private int areaY;
    private int areaW;
    private int areaH;
    private String field = "";
    private boolean focused;
    private long lastEdit;
    /** A destructive button that was clicked once and waits for the second click. */
    private String armed = "";
    private long armedAt;

    @Override
    public Identifier id() {
        return SkyRpgParty.id("party");
    }

    @Override
    public Component title() {
        return Component.literal("Party");
    }

    @Override
    public Component subtitle() {
        PartyPayloads.State state = PartyClient.state();
        return Component.literal(state.inParty() ? "Your party, " + state.members().size() + " of " + state.capacity() : "Team up with up to 7 friends");
    }

    @Override
    public ItemStack icon() {
        return new ItemStack(Items.PLAYER_HEAD);
    }

    @Override
    public int order() {
        return 38;
    }

    @Override
    public void opened() {
        scroll = 0;
        focused = false;
        armed = "";
    }

    @Override
    public boolean canGoBack() {
        return focused;
    }

    @Override
    public boolean back(PageContext ctx) {
        if (focused) {
            focused = false;
            return true;
        }
        return false;
    }

    @Override
    public Component hint() {
        return focused ? Component.literal("Enter invite") : Component.empty();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(PageContext ctx) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        hits.clear();
        areaX = ctx.x();
        areaY = ctx.y();
        areaW = ctx.width() - 6;
        areaH = ctx.height();
        if (!armed.isEmpty() && Util.getMillis() - armedAt > 3000) {
            armed = "";
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - areaH));
        PartyPayloads.State state = PartyClient.state();
        g.enableScissor(areaX, areaY, areaX + ctx.width(), areaY + areaH);
        int y = areaY - (int) Math.round(scroll);
        int startY = y;
        if (!state.invites().isEmpty()) {
            y = drawInvites(ctx, state, y);
        }
        if (state.inParty()) {
            y = drawParty(ctx, state, y);
        } else {
            y = drawEmpty(ctx, y);
        }
        y = drawInvite(ctx, state, y);
        contentHeight = y - startY;
        g.disableScissor();
        if (contentHeight > areaH) {
            Draw.scrollbar(g, ctx.x() + ctx.width() + 4, areaY, areaH, contentHeight, scroll, Theme.DIM);
        }
    }

    private int heading(PageContext ctx, String text, int y) {
        Draw.text(ctx.graphics(), ctx.font(), text, areaX + 2, y + 4, Theme.DIM);
        int lineX = areaX + 2 + ctx.font().width(text) + 6;
        ctx.graphics().fill(lineX, y + 8, areaX + areaW - 2, y + 9, Theme.BORDER);
        return y + HEADING;
    }

    private int drawInvites(PageContext ctx, PartyPayloads.State state, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        y = heading(ctx, "PENDING INVITES", y);
        for (InviteInfo invite : state.invites()) {
            int w = areaW - 4;
            Draw.panel(g, areaX + 2, y, w, 22, Theme.CARD, Draw.mix(Theme.CARD_BORDER, ACCENT, 0.5f));
            g.fill(areaX + 3, y + 4, areaX + 5, y + 18, ACCENT);
            Draw.fitText(g, font, Component.literal(invite.fromName()).withStyle(Style.EMPTY.withBold(true)), areaX + 10, y + 3, w - 150, Theme.TEXT);
            Draw.text(g, font, "invited you  " + invite.secondsLeft() + "s left", areaX + 10, y + 12, Theme.MUTED);
            int bx = areaX + 2 + w - 4;
            bx -= button(ctx, "Deny", bx - (font.width("Deny") + 16), y + 4, true, Theme.RED, Act.DENY, invite.fromName(), null);
            bx -= 4;
            button(ctx, "Accept", bx - (font.width("Accept") + 16), y + 4, true, Theme.GREEN, Act.ACCEPT, invite.fromName(), null);
            y += 22 + 3;
        }
        return y + 2;
    }

    private int drawParty(PageContext ctx, PartyPayloads.State state, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        boolean leader = PartyClient.amLeader();
        y = heading(ctx, "MEMBERS", y);
        Draw.rightText(g, font, state.members().size() + " / " + state.capacity(), areaX + areaW - 4, y - HEADING + 4, Theme.MUTED);
        // action row: party chat toggle, leave, disband
        int bx = areaX + 2;
        bx += button(ctx, state.chatOn() ? "Party chat: ON" : "Party chat: off", bx, y, true, state.chatOn() ? ACCENT : Theme.MUTED, Act.CHAT, "",
                "Sends what you type in chat to the party instead of everyone. Same as /party chat.") + 4;
        bx += button(ctx, armed.equals("leave") ? "Click again to leave" : "Leave", bx, y, true, Theme.RED, Act.LEAVE, "", "Leave the party") + 4;
        if (leader) {
            button(ctx, armed.equals("disband") ? "Click again to disband" : "Disband", bx, y, true, Theme.RED, Act.DISBAND, "", "Break up the party for everyone");
        }
        y += 17;
        for (MemberInfo member : state.members()) {
            drawMember(ctx, member, y, leader);
            y += CARD_H + GAP;
        }
        return y + 2;
    }

    private void drawMember(PageContext ctx, MemberInfo member, int y, boolean iAmLeader) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        Minecraft minecraft = Minecraft.getInstance();
        boolean me = minecraft.player != null && member.id().equals(minecraft.player.getUUID());
        boolean offline = !member.online();
        boolean isLeader = PartyClient.isLeader(member.id());
        int x = areaX + 2;
        int w = areaW - 4;
        boolean inArea = ctx.hovered(areaX, areaY, areaW, areaH);
        boolean hovered = inArea && ctx.hovered(x, y, w, CARD_H);
        Draw.panel(g, x, y, w, CARD_H, hovered && !me ? Theme.CARD_HOVER : Theme.CARD, hovered && !me ? Draw.mix(Theme.CARD_BORDER, ACCENT, 0.6f) : Theme.CARD_BORDER);
        g.fill(x + 1, y + 4, x + 3, y + CARD_H - 4, offline ? Theme.DIM : isLeader ? Theme.ACCENT : ACCENT);
        // face
        PlayerFaceExtractor.extractRenderState(g, PartyClient.skin(member.id()), x + 8, y + 5, 20);
        if (offline) {
            g.fill(x + 8, y + 5, x + 28, y + 25, 0xB0121620);
        }
        int textX = x + 34;
        // name line
        int nx = textX;
        if (isLeader) {
            Component crown = Component.literal(Notice.CROWN).withStyle(Style.EMPTY.withFont(Notice.FONT).withColor(offline ? 0x7A6A3A : 0xFFFFFF));
            Draw.text(g, font, crown, nx, y + 5, 0xFFFFFFFF);
            nx += 11;
        }
        int barW = Mth.clamp(Math.round(areaW * 0.22f), 70, 120);
        int buttonsW = iAmLeader && !me ? (font.width("Promote") + 16) + (font.width("Kick") + 16) + 8 : 0;
        int barX = x + w - 6 - buttonsW - (buttonsW > 0 ? 6 : 0) - barW;
        int textRoom = barX - 8 - nx;
        Draw.fitText(g, font, Component.literal(member.name() + (me ? " (you)" : "")).withStyle(Style.EMPTY.withBold(!offline)), nx, y + 5, textRoom - (nx - textX), offline ? Theme.MUTED : Theme.TEXT);
        // status line
        Draw.fitText(g, font, status(member, minecraft), textX, y + 17, barX - 8 - textX, offline ? Theme.DIM : Theme.MUTED);
        // health
        float fraction = PartyClient.shownFraction(member);
        Draw.bar(g, barX, y + 10, barW, 10, offline ? Math.max(0.04f, fraction) : fraction, offline ? 0xFF4A5160 : PartyWidget.healthColor(fraction));
        String text = offline ? "Offline" : Msg.number(Math.ceil(member.hp())) + "/" + Msg.number(Math.round(member.maxHp()));
        Draw.fit(g, font, Component.literal(Draw.numberFor(font, text, barW - 4)), barX + barW / 2, y + 11, barW - 4, 1f, Draw.MIN_TEXT_SCALE,
                Draw.Align.CENTER, offline ? Theme.MUTED : Theme.TEXT, !offline);
        // leader buttons
        int bx = x + w - 6 - buttonsW;
        if (buttonsW > 0) {
            String promoteLabel = "Promote";
            int pw = font.width(promoteLabel) + 16;
            button(ctx, promoteLabel, bx, y + 8, true, Theme.ACCENT, Act.PROMOTE, member.name(), "Make " + member.name() + " the party leader");
            String kickLabel = armed.equals("kick:" + member.name()) ? "Sure?" : "Kick";
            button(ctx, kickLabel, bx + pw + 8, y + 8, true, Theme.RED, Act.KICK, member.name(), "Remove " + member.name() + " from the party");
        }
        if (!me) {
            hits.add(0, new Hit(x, y, w, CARD_H, Act.TRACK, member.name()));
            if (hovered && !ctx.hovered(bx, y + 8, buttonsW, 14)) {
                g.requestCursor(CursorTypes.POINTING_HAND);
                ctx.tooltip(List.of(Component.literal("Click to set a waypoint to " + member.name()),
                        Component.literal(offline ? "Where they were last seen" : "Follows them as they move on your compass and map").withColor(0x8D96A8)), ACCENT);
            }
        }
    }

    private static String status(MemberInfo member, Minecraft minecraft) {
        String where = PartyClient.dimensionName(member.dimension());
        String place = member.dimension().isEmpty() ? "" : where + "  " + member.x() + ", " + member.y() + ", " + member.z();
        if (!member.online()) {
            return place.isEmpty() ? "Offline" : "Offline  " + place;
        }
        if (minecraft.player != null && member.dimension().equals(PartyClient.myDimension())) {
            double dx = member.x() - minecraft.player.getX();
            double dz = member.z() - minecraft.player.getZ();
            return place + "  " + Math.round(Math.sqrt(dx * dx + dz * dz)) + " m";
        }
        return place;
    }

    private int drawEmpty(PageContext ctx, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int boxH = 64;
        Draw.panel(g, areaX + 2, y, areaW - 4, boxH, Theme.CARD, Theme.CARD_BORDER);
        g.fill(areaX + 3, y + 1, areaX + areaW - 3, y + 2, Draw.withAlpha(ACCENT, 0.7f));
        Draw.item(g, icon(), areaX + 10, y + 10, 2f);
        Draw.text(g, font, "You are not in a party", areaX + 50, y + 12, Theme.TEXT);
        int ty = y + 26;
        for (var line : Draw.wrap(font, Component.literal("A party is saved: log out and come back tomorrow and you are still in it. Party members "
                + "see each other's health in an overlay, share a chat tab and cannot hurt each other. Invite someone below."), areaW - 64)) {
            g.text(font, line, areaX + 50, ty, Theme.MUTED, false);
            ty += 10;
        }
        return y + boxH + 6;
    }

    private int drawInvite(PageContext ctx, PartyPayloads.State state, int y) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        y = heading(ctx, "INVITE A PLAYER", y);
        boolean full = state.inParty() && state.members().size() >= state.capacity();
        int fieldW = Math.min(180, areaW - 90);
        int fx = areaX + 2;
        boolean hovered = ctx.hovered(fx, y, fieldW, 16) && ctx.hovered(areaX, areaY, areaW, areaH);
        Draw.panel(g, fx, y, fieldW, 16, Theme.INPUT, focused ? Theme.ACCENT : hovered ? Draw.mix(Theme.CARD_BORDER, Theme.ACCENT, 0.35f) : Theme.CARD_BORDER);
        if (field.isEmpty()) {
            Draw.text(g, font, "Player name", fx + 5, y + 4, Theme.DIM);
        } else {
            Draw.text(g, font, field, fx + 5, y + 4, Theme.TEXT);
        }
        if (focused && ((Util.getMillis() - lastEdit) / 530) % 2 == 0) {
            int cx = fx + 5 + font.width(field);
            g.fill(cx, y + 3, cx + 1, y + 13, Theme.ACCENT);
        }
        hits.add(new Hit(fx, y, fieldW, 16, Act.FOCUS, ""));
        if (hovered) {
            g.requestCursor(CursorTypes.IBEAM);
        }
        button(ctx, "Invite", fx + fieldW + 6, y + 1, !field.isBlank() && !full, Theme.GREEN, Act.INVITE, "",
                full ? "The party is full" : "They have 60 seconds to accept");
        y += 22;
        for (var line : Draw.wrap(font, Component.literal("Invites expire after 60 seconds. You can also use /party invite <player>, /party chat and /pc <message>."), areaW - 8)) {
            g.text(font, line, areaX + 4, y, Theme.DIM, false);
            y += 10;
        }
        return y + 4;
    }

    /** Draws a button and records its hit area; returns its width. */
    private int button(PageContext ctx, String label, int bx, int by, boolean enabled, int color, Act act, String arg, String tip) {
        GuiGraphicsExtractor g = ctx.graphics();
        Font font = ctx.font();
        int bw = font.width(label) + 16;
        boolean hovered = ctx.hovered(bx, by, bw, 13) && ctx.hovered(areaX, areaY, areaW, areaH);
        Draw.panel(g, bx, by, bw, 13, enabled && hovered ? Theme.CARD_HOVER : Theme.CARD,
                enabled && hovered ? Draw.mix(Theme.CARD_BORDER, color, 0.8f) : Theme.CARD_BORDER);
        Draw.text(g, font, label, bx + 8, by + 3, enabled ? (hovered ? color : Theme.TEXT) : Theme.DIM);
        if (enabled) {
            hits.add(new Hit(bx, by, bw, 13, act, arg));
        }
        if (hovered) {
            if (enabled) {
                g.requestCursor(CursorTypes.POINTING_HAND);
            }
            if (tip != null) {
                ctx.tooltip(List.of(Component.literal(tip)), color);
            }
        }
        return bw;
    }

    // ------------------------------------------------------------------ input

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.35f));
    }

    @Override
    public boolean mouseClicked(PageContext ctx, double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT || !ctx.hovered(areaX, areaY, areaW, areaH)) {
            return false;
        }
        boolean wasFocused = focused;
        focused = false;
        // The last hit drawn is on top (buttons are drawn after their card, the card hit goes first).
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (x >= hit.x() && x < hit.x() + hit.w() && y >= hit.y() && y < hit.y() + hit.h()) {
                run(hit);
                click();
                return true;
            }
        }
        return wasFocused;
    }

    private void run(Hit hit) {
        switch (hit.act()) {
            case FOCUS -> {
                focused = true;
                lastEdit = Util.getMillis();
            }
            case INVITE -> sendInvite();
            case TRACK -> PartyClient.send("track", hit.arg());
            case ACCEPT -> PartyClient.send("accept", hit.arg());
            case DENY -> PartyClient.send("deny", hit.arg());
            case PROMOTE -> PartyClient.send("promote", hit.arg());
            case CHAT -> PartyClient.send("chat", "");
            case KICK -> confirm("kick:" + hit.arg(), () -> PartyClient.send("kick", hit.arg()));
            case LEAVE -> confirm("leave", () -> PartyClient.send("leave", ""));
            case DISBAND -> confirm("disband", () -> PartyClient.send("disband", ""));
        }
    }

    private void confirm(String key, Runnable action) {
        if (armed.equals(key)) {
            armed = "";
            action.run();
        } else {
            armed = key;
            armedAt = Util.getMillis();
        }
    }

    private void sendInvite() {
        String name = field.strip();
        if (!name.isEmpty()) {
            PartyClient.send("invite", name);
            field = "";
        }
        focused = false;
    }

    @Override
    public boolean keyPressed(PageContext ctx, KeyEvent event) {
        if (!focused) {
            return false;
        }
        lastEdit = Util.getMillis();
        switch (event.key()) {
            case InputConstants.KEY_BACKSPACE -> {
                if (!field.isEmpty()) {
                    field = event.hasControlDown() ? "" : field.substring(0, field.length() - 1);
                }
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> sendInvite();
            case InputConstants.KEY_V -> {
                if (event.hasControlDown()) {
                    String paste = Minecraft.getInstance().keyboardHandler.getClipboard().replaceAll("[^A-Za-z0-9_]", "");
                    field = (field + paste).substring(0, Math.min(MAX_NAME, (field + paste).length()));
                }
            }
            default -> { }
        }
        return true;
    }

    @Override
    public boolean charTyped(PageContext ctx, CharacterEvent event) {
        if (!focused) {
            return false;
        }
        String typed = event.codepointAsString();
        if (typed.matches("[A-Za-z0-9_]") && field.length() < MAX_NAME) {
            field += typed;
            lastEdit = Util.getMillis();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(PageContext ctx, double x, double y, double amount) {
        if (contentHeight <= areaH) {
            return false;
        }
        scroll = Mth.clamp(scroll - amount * 14, 0, contentHeight - areaH);
        return true;
    }
}
