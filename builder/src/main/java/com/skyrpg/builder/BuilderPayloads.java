package com.skyrpg.builder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The Builder over the network: the station menu, the blueprint library and uploads, and the holograms. */
public final class BuilderPayloads {
    private BuilderPayloads() {
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Menu.TYPE, Menu.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(LibraryList.TYPE, LibraryList.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(UploadResult.TYPE, UploadResult.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Geometry.TYPE, Geometry.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Ghost.TYPE, Ghost.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Progress.TYPE, Progress.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(UploadChunk.TYPE, UploadChunk.CODEC);
    }

    /** One material row: the item, how many are delivered and how many the build still needs. */
    public record Line(String item, int have, int need) {
    }

    /**
     * Everything the station menu shows. {@code open} opens the menu; otherwise it only refreshes an open one.
     * {@code done}/{@code total}: blocks already in place (planning) or placed so far (building) out of the blueprint's.
     */
    public record Menu(boolean open, BlockPos pos, int state, String blueprintId, String blueprintName, String author, int sizeX, int sizeY,
                       int sizeZ, int turns, boolean mirrored, int offX, int offY, int offZ, boolean preview, boolean clear, int done, int total,
                       int skipped, int inTheWay, int layer, List<Line> lines, int leftovers, String note, boolean creative, String helpers)
            implements CustomPacketPayload {
        public static final Type<Menu> TYPE = new Type<>(SkyRpgBuilder.id("menu"));
        public static final StreamCodec<FriendlyByteBuf, Menu> CODEC = StreamCodec.ofMember(Menu::write, Menu::read);

        public static final int EMPTY = Station.EMPTY;
        public static final int PLANNING = Station.PLANNING;
        public static final int BUILDING = Station.BUILDING;
        public static final int DONE = Station.DONE;

        private void write(FriendlyByteBuf buf) {
            buf.writeBoolean(open);
            buf.writeBlockPos(pos);
            buf.writeVarInt(state);
            buf.writeUtf(blueprintId);
            buf.writeUtf(blueprintName);
            buf.writeUtf(author);
            buf.writeVarInt(sizeX);
            buf.writeVarInt(sizeY);
            buf.writeVarInt(sizeZ);
            buf.writeVarInt(turns);
            buf.writeBoolean(mirrored);
            buf.writeVarInt(offX);
            buf.writeVarInt(offY);
            buf.writeVarInt(offZ);
            buf.writeBoolean(preview);
            buf.writeBoolean(clear);
            buf.writeVarInt(done);
            buf.writeVarInt(total);
            buf.writeVarInt(skipped);
            buf.writeVarInt(inTheWay);
            buf.writeVarInt(layer);
            buf.writeVarInt(lines.size());
            for (Line line : lines) {
                buf.writeUtf(line.item());
                buf.writeVarInt(line.have());
                buf.writeVarInt(line.need());
            }
            buf.writeVarInt(leftovers);
            buf.writeUtf(note);
            buf.writeBoolean(creative);
            buf.writeUtf(helpers);
        }

        private static Menu read(FriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            BlockPos pos = buf.readBlockPos();
            int state = buf.readVarInt();
            String id = buf.readUtf();
            String name = buf.readUtf();
            String author = buf.readUtf();
            int sx = buf.readVarInt();
            int sy = buf.readVarInt();
            int sz = buf.readVarInt();
            int turns = buf.readVarInt();
            boolean mirrored = buf.readBoolean();
            int ox = buf.readVarInt();
            int oy = buf.readVarInt();
            int oz = buf.readVarInt();
            boolean preview = buf.readBoolean();
            boolean clear = buf.readBoolean();
            int done = buf.readVarInt();
            int total = buf.readVarInt();
            int skipped = buf.readVarInt();
            int inTheWay = buf.readVarInt();
            int layer = buf.readVarInt();
            int count = Math.min(buf.readVarInt(), 4096);
            List<Line> lines = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                lines.add(new Line(buf.readUtf(), buf.readVarInt(), buf.readVarInt()));
            }
            return new Menu(open, pos, state, id, name, author, sx, sy, sz, turns, mirrored, ox, oy, oz, preview, clear, done, total, skipped,
                    inTheWay, layer, lines, buf.readVarInt(), buf.readUtf(), buf.readBoolean(), buf.readUtf());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** The blueprint library, for the picker. */
    public record LibraryList(List<Library.Info> entries, String player) implements CustomPacketPayload {
        public static final Type<LibraryList> TYPE = new Type<>(SkyRpgBuilder.id("library"));
        public static final StreamCodec<FriendlyByteBuf, LibraryList> CODEC = StreamCodec.ofMember(LibraryList::write, LibraryList::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeVarInt(entries.size());
            for (Library.Info info : entries) {
                buf.writeUtf(info.id());
                buf.writeUtf(info.name());
                buf.writeUtf(info.author());
                buf.writeVarInt(info.kind());
                buf.writeUtf(info.category());
                buf.writeVarInt(info.sizeX());
                buf.writeVarInt(info.sizeY());
                buf.writeVarInt(info.sizeZ());
                buf.writeVarInt(info.blocks());
            }
            buf.writeUtf(player);
        }

        private static LibraryList read(FriendlyByteBuf buf) {
            int count = Math.min(buf.readVarInt(), 4096);
            List<Library.Info> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Library.Info(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            return new LibraryList(entries, buf.readUtf());
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record UploadResult(boolean ok, String message, String id) implements CustomPacketPayload {
        public static final Type<UploadResult> TYPE = new Type<>(SkyRpgBuilder.id("upload_result"));
        public static final StreamCodec<FriendlyByteBuf, UploadResult> CODEC = StreamCodec.ofMember(
                (r, buf) -> {
                    buf.writeBoolean(r.ok);
                    buf.writeUtf(r.message);
                    buf.writeUtf(r.id);
                },
                buf -> new UploadResult(buf.readBoolean(), buf.readUtf(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A blueprint's blocks (no block entity data), sent once per player so the hologram and the layer view can draw it. */
    public record Geometry(Blueprint blueprint) implements CustomPacketPayload {
        public static final Type<Geometry> TYPE = new Type<>(SkyRpgBuilder.id("geometry"));
        public static final StreamCodec<FriendlyByteBuf, Geometry> CODEC = StreamCodec.ofMember(Geometry::write, Geometry::read);

        private void write(FriendlyByteBuf buf) {
            buf.writeUtf(blueprint.id);
            buf.writeUtf(blueprint.name);
            buf.writeVarInt(blueprint.sizeX);
            buf.writeVarInt(blueprint.sizeY);
            buf.writeVarInt(blueprint.sizeZ);
            buf.writeVarInt(blueprint.palette.size());
            for (BlockState state : blueprint.palette) {
                buf.writeVarInt(Block.getId(state));
            }
            buf.writeVarInt(blueprint.positions.length);
            int previous = 0;
            for (int position : blueprint.positions) {
                buf.writeVarInt(position - previous);
                previous = position;
            }
            for (int state : blueprint.states) {
                buf.writeVarInt(state);
            }
        }

        private static Geometry read(FriendlyByteBuf buf) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            int sx = buf.readVarInt();
            int sy = buf.readVarInt();
            int sz = buf.readVarInt();
            int paletteSize = Math.min(buf.readVarInt(), 65536);
            List<BlockState> palette = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) {
                palette.add(Block.stateById(buf.readVarInt()));
            }
            int count = Math.min(buf.readVarInt(), Blueprint.MAX_BLOCKS);
            int[] positions = new int[count];
            int previous = 0;
            for (int i = 0; i < count; i++) {
                previous += buf.readVarInt();
                positions[i] = previous;
            }
            int[] states = new int[count];
            for (int i = 0; i < count; i++) {
                states[i] = Math.min(buf.readVarInt(), Math.max(0, paletteSize - 1));
            }
            return new Geometry(new Blueprint(id, name, "", Blueprint.KIND_UPLOAD, sx, sy, sz, palette, positions, states, new HashMap<>()));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Where a station's hologram is. {@code mode}: {@link #HIDE} removes it, {@link #PLAN} shows the whole blueprint,
     * {@link #BUILD} shows what's left above {@code layer} with a scan plane.
     */
    public record Ghost(BlockPos station, int mode, String blueprintId, BlockPos origin, int turns, boolean mirrored, int layer)
            implements CustomPacketPayload {
        public static final int HIDE = 0;
        public static final int PLAN = 1;
        public static final int BUILD = 2;
        public static final Type<Ghost> TYPE = new Type<>(SkyRpgBuilder.id("ghost"));
        public static final StreamCodec<FriendlyByteBuf, Ghost> CODEC = StreamCodec.ofMember(
                (g, buf) -> {
                    buf.writeBlockPos(g.station);
                    buf.writeVarInt(g.mode);
                    buf.writeUtf(g.blueprintId);
                    buf.writeBlockPos(g.origin);
                    buf.writeVarInt(g.turns);
                    buf.writeBoolean(g.mirrored);
                    buf.writeVarInt(g.layer);
                },
                buf -> new Ghost(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(), buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(),
                        buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Something done in the station menu. {@code action} is one of the names {@link Stations#act} knows ("move", "turn",
     * "build"...); {@code x, y, z} and {@code text} are its arguments. The server checks everything again.
     */
    public record Action(BlockPos station, String action, int x, int y, int z, String text) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(SkyRpgBuilder.id("action"));
        public static final StreamCodec<FriendlyByteBuf, Action> CODEC = StreamCodec.ofMember(
                (a, buf) -> {
                    buf.writeBlockPos(a.station);
                    buf.writeUtf(a.action, 32);
                    buf.writeVarInt(a.x);
                    buf.writeVarInt(a.y);
                    buf.writeVarInt(a.z);
                    buf.writeUtf(a.text, 256);
                },
                buf -> new Action(buf.readBlockPos(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256)));

        public static Action of(BlockPos station, String action) {
            return new Action(station, action, 0, 0, 0, "");
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * A build going on near the player, for the progress overlay: sent twice a second while it builds and once more
     * when it finishes ({@code done == total}).
     */
    public record Progress(BlockPos station, String name, String blueprintId, int done, int total, int layer, int layers, String note)
            implements CustomPacketPayload {
        public static final Type<Progress> TYPE = new Type<>(SkyRpgBuilder.id("progress"));
        public static final StreamCodec<FriendlyByteBuf, Progress> CODEC = StreamCodec.ofMember(
                (p, buf) -> {
                    buf.writeBlockPos(p.station);
                    buf.writeUtf(p.name);
                    buf.writeUtf(p.blueprintId);
                    buf.writeVarInt(p.done);
                    buf.writeVarInt(p.total);
                    buf.writeVarInt(p.layer);
                    buf.writeVarInt(p.layers);
                    buf.writeUtf(p.note);
                },
                buf -> new Progress(buf.readBlockPos(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One piece of a schematic file being uploaded ({@link Library#CHUNK_BYTES} at most). */
    public record UploadChunk(int session, int index, int count, String fileName, byte[] data) implements CustomPacketPayload {
        public static final Type<UploadChunk> TYPE = new Type<>(SkyRpgBuilder.id("upload"));
        public static final StreamCodec<FriendlyByteBuf, UploadChunk> CODEC = StreamCodec.ofMember(
                (u, buf) -> {
                    buf.writeVarInt(u.session);
                    buf.writeVarInt(u.index);
                    buf.writeVarInt(u.count);
                    buf.writeUtf(u.fileName, 128);
                    buf.writeByteArray(u.data);
                },
                buf -> new UploadChunk(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(128), buf.readByteArray(Library.CHUNK_BYTES)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
