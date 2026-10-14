package com.skyrpg.utils.client.info;

import com.skyrpg.core.client.hud.HudAnchor;
import com.skyrpg.core.client.hud.HudWidget;
import com.skyrpg.core.client.ui.Draw;
import com.skyrpg.core.client.ui.Theme;
import com.skyrpg.core.items.ToolTiers;
import com.skyrpg.core.world.ToolGates;
import com.skyrpg.core.stats.HealthDisplay;
import com.skyrpg.core.stats.StatTypes;
import com.skyrpg.core.text.Msg;
import com.skyrpg.utils.client.SkyRpgUtilsClient;
import com.skyrpg.utils.client.UtilsSettings;
import com.skyrpg.utils.client.mixin.MannequinAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluids;

/**
 * A small box, top centre, about whatever you're looking at: its icon (or face), name and which mod it comes
 * from, plus what's useful to know: how grown a crop is, whether a planter is ready or a mining node has regrown, whether your
 * tool can harvest the block, a creature's or player's health, an NPC's role, a player's ping.
 */
public final class LookInfoWidget implements HudWidget {
    private static final int PAD = 5;
    private static final int ICON = 16;
    private static final int LINE = 10;
    private static final int MIN_WIDTH = 110;
    private static final int MAX_WIDTH = 240;

    /** What the box shows; worked out once a tick. {@code face} is a player or NPC to draw instead of an icon. */
    private record Info(ItemStack icon, Entity face, Component title, Component source, List<Component> lines, float health, Component healthText) {
    }

    private static final Map<String, String> MOD_NAMES = new HashMap<>();
    /** What the HUD editor shows; made on first use, since item stacks can't be made during mod startup. */
    private static Info sample;
    private static Info current;

    @Override
    public Identifier id() {
        return SkyRpgUtilsClient.id("look_info");
    }

    @Override
    public Component name() {
        return Component.literal("Look-at info");
    }

    @Override
    public HudAnchor defaultAnchor() {
        return HudAnchor.TOP_CENTER;
    }

    @Override
    public int defaultX() {
        return 0;
    }

    @Override
    public int defaultY() {
        return 4;
    }

    @Override
    public boolean visible() {
        return UtilsSettings.look().get() && current != null;
    }

    public static void tick() {
        current = LookTarget.entity() != null ? entityInfo(LookTarget.entity())
                : LookTarget.state() != null ? blockInfo(LookTarget.state()) : null;
    }

    private static Info shown(boolean editing) {
        if (current != null || !editing) {
            return current;
        }
        if (sample == null) {
            sample = new Info(new ItemStack(Items.GRASS_BLOCK), null, Component.literal("Grass Block"), Component.literal("Minecraft"),
                    List.of(Component.literal("✔ Harvestable").withColor(Theme.GREEN)), -1, null);
        }
        return sample;
    }

    private static int textX(Info info) {
        return info.icon() != null || info.face() != null ? PAD + ICON + 6 : PAD + 1;
    }

    @Override
    public int width(Font font) {
        Info info = shown(true);
        int text = Math.max(font.width(info.title()), font.width(info.source()));
        for (Component line : info.lines()) {
            text = Math.max(text, font.width(line));
        }
        if (info.health() >= 0) {
            text = Math.max(text, 60 + 6 + font.width(info.healthText()));
        }
        return Math.clamp(textX(info) + text + PAD + 1, MIN_WIDTH, MAX_WIDTH);
    }

    @Override
    public int height(Font font) {
        Info info = shown(true);
        int lines = 2 + info.lines().size() + (info.health() >= 0 ? 1 : 0);
        return Math.max(PAD * 2 + ICON, PAD * 2 + lines * LINE - 1);
    }

    @Override
    public void render(GuiGraphicsExtractor g, Font font, boolean editing) {
        Info info = shown(editing);
        if (info == null) {
            return;
        }
        int width = width(font);
        int height = height(font);
        Draw.panel(g, 0, 0, width, height, 0xE8121620, Theme.BORDER);
        if (info.face() instanceof AbstractClientPlayer player) {
            PlayerFaceExtractor.extractRenderState(g, player.getSkin(), PAD, PAD, ICON);
        } else if (info.face() instanceof Mannequin mannequin) {
            PlayerFaceExtractor.extractRenderState(g, mannequin.getProfile(), PAD, PAD, ICON);
        } else if (info.icon() != null) {
            Draw.item(g, info.icon(), PAD, PAD, 1f);
        }
        int x = textX(info);
        int maxText = width - x - PAD;
        int y = PAD;
        Draw.fitText(g, font, info.title(), x, y, maxText, Theme.TEXT);
        y += LINE;
        Draw.fitText(g, font, info.source(), x, y, maxText, Theme.BLUE);
        y += LINE;
        if (info.health() >= 0) {
            Draw.bar(g, x, y + 2, 60, 4, info.health(), Theme.RED);
            Draw.fitText(g, font, info.healthText(), x + 66, y, maxText - 66, Theme.TEXT);
            y += LINE;
        }
        for (Component line : info.lines()) {
            Draw.fitText(g, font, line, x, y, maxText, Theme.MUTED);
            y += LINE;
        }
    }

    // ---------------------------------------------------------------- blocks

    private static Info blockInfo(BlockState state) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemStack icon = new ItemStack(state.getBlock().asItem());
        if (icon.isEmpty()) {
            icon = state.getFluidState().is(Fluids.WATER) || state.getFluidState().is(Fluids.FLOWING_WATER) ? new ItemStack(Items.WATER_BUCKET)
                    : state.getFluidState().is(Fluids.LAVA) || state.getFluidState().is(Fluids.FLOWING_LAVA) ? new ItemStack(Items.LAVA_BUCKET) : null;
        }
        List<Component> lines = new ArrayList<>();
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && property.getName().equals("age")) {
                int value = state.getValue(age);
                int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(value);
                lines.add(value >= max ? Component.literal("✔ Fully grown").withColor(Theme.GREEN)
                        : Component.literal("Growth " + Math.round(100f * value / Math.max(1, max)) + "%").withColor(Theme.ACCENT));
            } else if (property instanceof BooleanProperty ripe && property.getName().equals("ripe")) {
                lines.add(state.getValue(ripe) ? Component.literal("✔ Ready to harvest").withColor(Theme.GREEN)
                        : Component.literal("⌛ Growing").withColor(Theme.ACCENT));
            } else if (property instanceof BooleanProperty depleted && property.getName().equals("depleted")) {
                lines.add(state.getValue(depleted) ? Component.literal("⌛ Regrowing").withColor(Theme.ACCENT)
                        : Component.literal(state.is(BlockTags.MINEABLE_WITH_AXE) ? "✔ Ready to chop" : "✔ Ready to mine").withColor(Theme.GREEN));
            }
        }
        int tier = ToolGates.required(state);
        if (tier > 0 && minecraft.player != null) {
            // SkyRPG ores and woods that need a tool tier (core's skyrpg_core:tool_tier/<n> tags).
            String tool = " " + ToolGates.toolName(state);
            lines.add(ToolGates.tierFor(minecraft.player.getMainHandItem(), state) >= tier
                    ? Component.literal("✔ Tier " + ToolTiers.roman(tier) + tool).withColor(Theme.GREEN)
                    : Component.literal("✘ Needs a Tier " + ToolTiers.roman(tier) + tool).withColor(Theme.RED));
        } else if (state.requiresCorrectToolForDrops() && minecraft.player != null) {
            lines.add(minecraft.player.hasCorrectToolForDrops(state) ? Component.literal("✔ Harvestable").withColor(Theme.GREEN)
                    : Component.literal("✘ Needs a better tool").withColor(Theme.RED));
        }
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return new Info(icon, null, state.getBlock().getName(), Component.literal(modName(id.getNamespace())), lines, -1, null);
    }

    // ---------------------------------------------------------------- entities

    private static Info entityInfo(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        Identifier type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        List<Component> lines = new ArrayList<>();
        if (entity instanceof ItemEntity item) {
            ItemStack stack = item.getItem();
            Component title = stack.getHoverName().copy().append(Component.literal(" ×" + stack.getCount()).withStyle(ChatFormatting.GRAY));
            Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return new Info(stack, null, title, Component.literal(modName(itemId.getNamespace())), lines, -1, null);
        }
        if (entity instanceof Mannequin mannequin) {
            Component role = ((MannequinAccess) mannequin).skyrpg$description();
            if (role != null && !role.getString().isBlank()) {
                lines.add(role);
            }
            return new Info(null, mannequin, entity.getDisplayName(), Component.literal("NPC"), lines, -1, null);
        }
        float health = -1;
        Component healthText = null;
        if (entity instanceof LivingEntity living) {
            // Tough bosses (core's HealthDisplay) have more health than vanilla's cap shows.
            double scale = StatTypes.HEALTH_SCALE * HealthDisplay.multiplier(living);
            double now = living.getHealth() * scale;
            double max = Math.max(1, living.getMaxHealth() * scale);
            health = (float) Math.clamp(now / max, 0, 1);
            healthText = Component.literal("❤ " + Msg.number(Math.ceil(now)) + "/" + Msg.number(Math.ceil(max)));
        }
        if (entity instanceof AbstractClientPlayer player) {
            PlayerInfo info = minecraft.getConnection() == null ? null : minecraft.getConnection().getPlayerInfo(player.getUUID());
            if (info != null) {
                lines.add(Component.literal("Ping " + info.getLatency() + " ms"));
            }
            return new Info(null, player, player.getDisplayName(), Component.literal("Player"), lines, health, healthText);
        }
        ItemStack icon = SpawnEggItem.byId(entity.getType()).map(item -> new ItemStack(item.value())).orElse(null);
        return new Info(icon, null, entity.getDisplayName(), Component.literal(modName(type.getNamespace())), lines, health, healthText);
    }

    /** "Minecraft", "SkyRPG Mining"...: the display name of the mod with this namespace. */
    private static String modName(String namespace) {
        return MOD_NAMES.computeIfAbsent(namespace, ns -> FabricLoader.getInstance().getModContainer(ns)
                .map(mod -> mod.getMetadata().getName())
                .orElse(ns.isEmpty() ? ns : ns.substring(0, 1).toUpperCase(Locale.ROOT) + ns.substring(1).replace('_', ' ')));
    }
}
