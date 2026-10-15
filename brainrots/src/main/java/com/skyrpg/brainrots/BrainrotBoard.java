package com.skyrpg.brainrots;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.skyrpg.core.leaderboard.Leaderboards;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The brainrot collectors leaderboard: every player who has caught one, with how many kinds they've found, how many
 * catches in all and how many kinds of each rarity, plus their name and skin so the hub podium can show them while
 * they're offline. Kept on the overworld; updated on each catch and when a player joins (which also fills it in for
 * albums from before it existed). Ranked by kinds found, then catches, then who got there first.
 */
public final class BrainrotBoard extends SavedData {
    public static final Identifier ID = SkyRpgBrainrots.id("collectors");

    record Row(UUID id, String name, String skin, String signature, int found, int catches, List<Integer> rarities, long reached) {
        static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(Row::id),
                Codec.STRING.fieldOf("name").forGetter(Row::name),
                Codec.STRING.optionalFieldOf("skin", "").forGetter(Row::skin),
                Codec.STRING.optionalFieldOf("signature", "").forGetter(Row::signature),
                Codec.INT.fieldOf("found").forGetter(Row::found),
                Codec.INT.fieldOf("catches").forGetter(Row::catches),
                Codec.INT.listOf().fieldOf("rarities").forGetter(Row::rarities),
                Codec.LONG.fieldOf("reached").forGetter(Row::reached)).apply(i, Row::new));
    }

    static final Codec<BrainrotBoard> CODEC = RecordCodecBuilder.create(i -> i.group(
            Row.CODEC.listOf().optionalFieldOf("rows", List.of()).forGetter(b -> List.copyOf(b.rows.values()))).apply(i, BrainrotBoard::new));
    static final SavedDataType<BrainrotBoard> TYPE = new SavedDataType<>(ID, BrainrotBoard::new, CODEC, null);

    private static final Comparator<Row> RANKING = Comparator.comparingInt(Row::found).reversed()
            .thenComparing(Comparator.comparingInt(Row::catches).reversed())
            .thenComparingLong(Row::reached);

    private final Map<UUID, Row> rows = new HashMap<>();

    private BrainrotBoard() {
    }

    private BrainrotBoard(List<Row> saved) {
        for (Row row : saved) {
            rows.put(row.id(), row);
        }
    }

    static BrainrotBoard get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Brings this player's row up to date with their album (and their current name and skin). */
    static void update(ServerPlayer player) {
        Album album = player.getAttachedOrElse(Album.ATTACHMENT, Album.EMPTY);
        if (album.discovered() == 0) {
            return;
        }
        BrainrotBoard board = get(player.level().getServer());
        int[] rarities = new int[BrainrotKind.Rarity.values().length];
        for (BrainrotKind kind : BrainrotKind.values()) {
            if (album.has(kind)) {
                rarities[kind.rarity.ordinal()]++;
            }
        }
        int found = album.discovered();
        int catches = album.caught().values().stream().mapToInt(Integer::intValue).sum();
        Row old = board.rows.get(player.getUUID());
        long reached = old != null && old.found() == found ? old.reached() : System.currentTimeMillis();
        String[] skin = Leaderboards.skinOf(player);
        Row row = new Row(player.getUUID(), player.getGameProfile().name(), skin[0], skin[1], found, catches,
                java.util.Arrays.stream(rarities).boxed().toList(), reached);
        if (!row.equals(old)) {
            board.rows.put(row.id(), row);
            board.setDirty();
            Leaderboards.changed(ID);
        }
    }

    // ------------------------------------------------------------------ the board

    /** What the hub podium shows. */
    static final Leaderboards.Board BOARD = new Leaderboards.Board() {
        @Override
        public Identifier id() {
            return ID;
        }

        @Override
        public Component title() {
            return Component.literal("BRAINROT COLLECTORS").withColor(0xFF7AD9);
        }

        @Override
        public Component subtitle() {
            return Component.literal("Most brainrots found (press J for your album)").withColor(0x8D96A8);
        }

        @Override
        public List<Leaderboards.Entry> top(MinecraftServer server, int count) {
            List<Row> ranked = new ArrayList<>(get(server).rows.values());
            ranked.sort(RANKING);
            int total = BrainrotKind.values().length;
            int[] perRarity = new int[BrainrotKind.Rarity.values().length];
            for (BrainrotKind kind : BrainrotKind.values()) {
                perRarity[kind.rarity.ordinal()]++;
            }
            List<Leaderboards.Entry> top = new ArrayList<>();
            for (Row row : ranked.subList(0, Math.min(count, ranked.size()))) {
                // "12 / 15 brainrots", "37 caught", then each rarity's count in its colour.
                Component found = Component.literal(row.found() + " / " + total).withColor(0xFFFFFF)
                        .append(Component.literal(" brainrots").withColor(0xFF7AD9));
                Component catches = Component.literal(row.catches() + " caught in all").withColor(0xB4BCCB);
                MutableComponent rarities = Component.empty();
                for (BrainrotKind.Rarity rarity : BrainrotKind.Rarity.values()) {
                    int have = rarity.ordinal() < row.rarities().size() ? row.rarities().get(rarity.ordinal()) : 0;
                    if (rarity.ordinal() > 0) {
                        rarities.append(Component.literal(" "));
                    }
                    rarities.append(Component.literal("●").withColor(rarity.color & 0xFFFFFF))
                            .append(Component.literal(have + "/" + perRarity[rarity.ordinal()]).withColor(have > 0 ? rarity.color & 0xFFFFFF : 0x5A6272));
                }
                top.add(new Leaderboards.Entry(row.id(), row.name(), row.skin(), row.signature(), List.of(found, catches, rarities)));
            }
            return top;
        }
    };
}
