package com.skyrpg.chat.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.skyrpg.chat.SkyRpgChat;
import com.skyrpg.chat.Stickers;
import com.skyrpg.chat.client.ImageRefs.Kind;
import com.skyrpg.chat.client.ImageRefs.Ref;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Every picture the chat can show. An {@link Entry} per picture remembers its state and size for good (that is all the
 * chat layout needs); the GPU texture is kept for the most recently drawn few and reloaded from the disk cache when it
 * was dropped.
 *
 * <p>Links are downloaded on a worker thread (timeouts, 4 MB cap, redirects re-checked, private addresses refused),
 * decoded with ImageIO (PNG, JPEG, GIF first frame), scaled down to at most 512 px and saved as a PNG under
 * {@code <game folder>/cache/skyrpg_chat/images}. Only the texture upload happens on the render thread.
 */
public final class ImageCache {
    public static final int MAX_DOWNLOAD = 4 * 1024 * 1024;
    public static final int LINK_MAX_SIDE = 512;
    public static final int SHARED_MAX_SIDE = 640;
    private static final int MAX_SOURCE_PIXELS = 40_000_000;
    private static final int MAX_LIVE_TEXTURES = 32;
    private static final long NET_WAIT_MS = 25_000;
    private static final long DISK_KEEP_BYTES = 96L * 1024 * 1024;
    private static final long DISK_KEEP_MS = 30L * 24 * 3600 * 1000;

    public enum State { LOADING, READY, FAILED }

    public static final class Entry {
        public final Ref ref;
        final long created = System.currentTimeMillis();
        volatile State state = State.LOADING;
        volatile int width;
        volatile int height;
        volatile Identifier texture;
        volatile String error = "";
        volatile long lastUse;
        volatile boolean reloading;

        Entry(Ref ref) {
            this.ref = ref;
        }

        public State state() {
            if (state == State.LOADING && ref.kind() == Kind.NET && System.currentTimeMillis() - created > NET_WAIT_MS) {
                return State.FAILED;
            }
            return state;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public String error() {
            return state == State.LOADING ? "image not received" : error;
        }
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Set<String> APPROVED = new HashSet<>();
    private static final AtomicBoolean RELAYOUT = new AtomicBoolean();
    private static ExecutorService workers;
    private static Path dir;

    private ImageCache() {
    }

    // ------------------------------------------------------------------ lookup

    /** The entry for a picture, starting its load if it has none yet. Render thread. */
    public static synchronized Entry request(Ref ref) {
        Entry entry = ENTRIES.get(ref.key());
        if (entry != null) {
            return entry;
        }
        entry = new Entry(ref);
        ENTRIES.put(ref.key(), entry);
        switch (ref.kind()) {
            case STICKER -> {
                entry.width = Stickers.SIZE;
                entry.height = Stickers.SIZE;
                entry.texture = Identifier.fromNamespaceAndPath(SkyRpgChat.MOD_ID, "textures/sticker/" + ref.value() + ".png");
                entry.state = State.READY;
            }
            case URL -> start(entry, null);
            case NET -> {
                // Normally the bytes arrived before the chat line; if not, they are still on their way. Failing both,
                // a copy from an earlier session may be in the disk cache.
                Entry waiting = entry;
                if (Files.exists(file(ref.key()))) {
                    start(waiting, null);
                }
            }
        }
        return entry;
    }

    public static synchronized Entry peek(String key) {
        return ENTRIES.get(key);
    }

    /** A link that isn't on the whitelist loads once the player has clicked it. */
    public static synchronized boolean approved(String url) {
        return APPROVED.contains(url);
    }

    public static synchronized void approve(String url) {
        APPROVED.add(url);
        Entry old = ENTRIES.get("url:" + url);
        if (old != null && old.state == State.FAILED) {
            ENTRIES.remove("url:" + url);
        }
    }

    /** A shared screenshot arrived from the server. */
    static void supply(String id, byte[] bytes) {
        Entry entry;
        synchronized (ImageCache.class) {
            Ref ref = new Ref(Kind.NET, id);
            entry = ENTRIES.computeIfAbsent(ref.key(), k -> new Entry(ref));
            if (entry.state == State.READY) {
                return;
            }
            entry.state = State.LOADING;
        }
        start(entry, bytes);
    }

    // ------------------------------------------------------------------ loading

    private static synchronized ExecutorService workers() {
        if (workers == null) {
            workers = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "SkyRPG chat images");
                thread.setDaemon(true);
                return thread;
            });
        }
        return workers;
    }

    private static Path dir() {
        if (dir == null) {
            dir = FabricLoader.getInstance().getGameDir().resolve("cache").resolve("skyrpg_chat").resolve("images");
        }
        return dir;
    }

    private static Path file(String key) {
        return dir().resolve(sha1(key).substring(0, 32) + ".png");
    }

    private static String sha1(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Loads an entry on a worker: from {@code supplied} bytes if given, else the disk cache, else (links) the network. */
    private static void start(Entry entry, byte[] supplied) {
        workers().execute(() -> {
            try {
                Path cached = file(entry.ref.key());
                BufferedImage image = null;
                if (supplied == null && Files.isRegularFile(cached)) {
                    try {
                        image = ImageIO.read(cached.toFile());
                    } catch (IOException | RuntimeException e) {
                        image = null;
                    }
                }
                boolean fresh = false;
                if (image == null) {
                    byte[] bytes = supplied;
                    if (bytes == null) {
                        if (entry.ref.kind() != Kind.URL) {
                            return;
                        }
                        bytes = download(entry.ref.value(), !approved(entry.ref.value()));
                    }
                    int side = entry.ref.kind() == Kind.URL ? LINK_MAX_SIDE : SHARED_MAX_SIDE;
                    image = decode(bytes, side);
                    fresh = true;
                }
                if (fresh) {
                    try {
                        Files.createDirectories(cached.getParent());
                        ImageIO.write(image, "png", cached.toFile());
                    } catch (IOException | RuntimeException e) {
                        SkyRpgChat.LOGGER.debug("Couldn't cache a chat image", e);
                    }
                }
                NativeImage pixels = toNative(image);
                BufferedImage shown = image;
                Minecraft.getInstance().execute(() -> upload(entry, pixels, shown.getWidth(), shown.getHeight(), true));
            } catch (Exception e) {
                entry.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                entry.state = State.FAILED;
                Minecraft.getInstance().execute(ImageCache::relayout);
            }
        });
    }

    /** Render thread: makes the texture and tells the chat to lay the message out again. */
    private static void upload(Entry entry, NativeImage pixels, int width, int height, boolean relayout) {
        Identifier id = Identifier.fromNamespaceAndPath(SkyRpgChat.MOD_ID, "img/" + sha1(entry.ref.key()).substring(0, 24));
        Minecraft minecraft = Minecraft.getInstance();
        Identifier old = entry.texture;
        if (old != null && entry.ref.kind() != Kind.STICKER) {
            minecraft.getTextureManager().release(old);
        }
        minecraft.getTextureManager().register(id, new DynamicTexture(() -> "skyrpg_chat " + entry.ref.key(), pixels));
        entry.texture = id;
        entry.width = width;
        entry.height = height;
        entry.lastUse = System.nanoTime();
        entry.reloading = false;
        entry.state = State.READY;
        trimTextures(entry);
        if (relayout) {
            relayout();
        }
    }

    /** Drops the least recently drawn textures beyond the limit (their entries keep size and state). */
    private static synchronized void trimTextures(Entry keep) {
        int live = 0;
        for (Entry entry : ENTRIES.values()) {
            if (entry.texture != null && entry.ref.kind() != Kind.STICKER) {
                live++;
            }
        }
        while (live > MAX_LIVE_TEXTURES) {
            Entry oldest = null;
            for (Entry entry : ENTRIES.values()) {
                if (entry != keep && entry.texture != null && entry.ref.kind() != Kind.STICKER && (oldest == null || entry.lastUse < oldest.lastUse)) {
                    oldest = entry;
                }
            }
            if (oldest == null) {
                return;
            }
            Minecraft.getInstance().getTextureManager().release(oldest.texture);
            oldest.texture = null;
            live--;
        }
    }

    /** A texture that was dropped comes back from the disk cache (no relayout: its size is known). */
    static void reload(Entry entry) {
        if (entry.reloading || entry.state != State.READY) {
            return;
        }
        entry.reloading = true;
        workers().execute(() -> {
            try {
                BufferedImage image = ImageIO.read(file(entry.ref.key()).toFile());
                if (image == null) {
                    throw new IOException("not cached");
                }
                NativeImage pixels = toNative(image);
                Minecraft.getInstance().execute(() -> upload(entry, pixels, image.getWidth(), image.getHeight(), false));
            } catch (Exception e) {
                entry.reloading = false;
                entry.error = "no longer cached";
                entry.state = State.FAILED;
            }
        });
    }

    /** Asks the chat to lay every message out again (once per tick), keeping the scroll position. */
    static void relayout() {
        if (!RELAYOUT.compareAndSet(false, true)) {
            return;
        }
        Minecraft.getInstance().execute(() -> {
            RELAYOUT.set(false);
            ChatImageRender.relayoutChat();
        });
    }

    // ------------------------------------------------------------------ network and decoding

    private static boolean blocked(InetAddress address) {
        return address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    /** Fetches a link: http(s) only, whitelisted hosts only unless the player approved it, no private addresses. */
    private static byte[] download(String link, boolean whitelistOnly) throws IOException {
        URI uri = URI.create(link);
        for (int hop = 0; hop < 4; hop++) {
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
                throw new IOException("not a web link");
            }
            if (whitelistOnly && !ImageRefs.whitelisted(host)) {
                throw new IOException("host not allowed: " + host);
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (blocked(address)) {
                    throw new IOException("private address");
                }
            }
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("User-Agent", "SkyRPG-Chat/1.0");
                connection.setRequestProperty("Accept", "image/png,image/jpeg,image/gif");
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        throw new IOException("bad redirect");
                    }
                    uri = uri.resolve(location);
                    continue;
                }
                if (code != 200) {
                    throw new IOException("HTTP " + code);
                }
                String type = connection.getContentType();
                if (type != null && !type.toLowerCase(java.util.Locale.ROOT).startsWith("image/") && !type.startsWith("application/octet")) {
                    throw new IOException("not a picture (" + type + ")");
                }
                long length = connection.getContentLengthLong();
                if (length > MAX_DOWNLOAD) {
                    throw new IOException("too big");
                }
                try (InputStream in = connection.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream((int) Math.max(1024, Math.min(length, MAX_DOWNLOAD)));
                    byte[] buffer = new byte[16384];
                    int total = 0;
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        total += read;
                        if (total > MAX_DOWNLOAD) {
                            throw new IOException("too big");
                        }
                        out.write(buffer, 0, read);
                    }
                    return out.toByteArray();
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("too many redirects");
    }

    /** PNG, JPEG or GIF (first frame) to an ARGB picture no bigger than {@code maxSide} on its long side. */
    static BufferedImage decode(byte[] bytes, int maxSide) throws IOException {
        BufferedImage source;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("unknown picture format");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!format.contains("png") && !format.contains("jpeg") && !format.contains("jpg") && !format.contains("gif")) {
                    throw new IOException("unsupported format " + format);
                }
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_SOURCE_PIXELS) {
                    throw new IOException("picture too large");
                }
                source = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        int width = source.getWidth();
        int height = source.getHeight();
        int longSide = Math.max(width, height);
        BufferedImage out;
        if (longSide > maxSide) {
            double scale = (double) maxSide / longSide;
            int nw = Math.max(1, (int) Math.round(width * scale));
            int nh = Math.max(1, (int) Math.round(height * scale));
            Image scaled = source.getScaledInstance(nw, nh, Image.SCALE_AREA_AVERAGING);
            out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.drawImage(scaled, 0, 0, null);
            g.dispose();
        } else {
            out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.drawImage(source, 0, 0, null);
            g.dispose();
        }
        return out;
    }

    private static NativeImage toNative(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        NativeImage pixels = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
                pixels.setPixelABGR(x, y, abgr);
            }
        }
        return pixels;
    }

    /** Deletes the oldest cached pictures when the cache folder grows past its limit. Called once at startup. */
    static void trimDisk() {
        workers().execute(() -> {
            try {
                if (!Files.isDirectory(dir())) {
                    return;
                }
                java.util.List<Path> files;
                try (var stream = Files.list(dir())) {
                    files = stream.filter(Files::isRegularFile).sorted(java.util.Comparator.comparingLong(path -> path.toFile().lastModified())).toList();
                }
                long total = 0;
                for (Path path : files) {
                    total += Files.size(path);
                }
                long now = System.currentTimeMillis();
                for (Path path : files) {
                    if (total > DISK_KEEP_BYTES || now - path.toFile().lastModified() > DISK_KEEP_MS) {
                        total -= Files.size(path);
                        Files.deleteIfExists(path);
                    }
                }
            } catch (IOException | RuntimeException e) {
                SkyRpgChat.LOGGER.debug("Couldn't trim the chat image cache", e);
            }
        });
    }
}
