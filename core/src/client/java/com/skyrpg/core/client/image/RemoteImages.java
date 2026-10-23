package com.skyrpg.core.client.image;

import com.mojang.blaze3d.platform.NativeImage;
import com.skyrpg.core.SkyRpgCore;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Pictures from web links that the server names (event splash art, billboards), as textures. Each link is loaded once
 * per session on a worker thread: the copy in {@code <game folder>/cache/skyrpg/remote} shows at once if there is one,
 * then the link is fetched again so a changed picture replaces it. https/http only, no private addresses, 8 MB at most,
 * scaled down to {@link #MAX_SIDE} px. Only the texture upload happens on the render thread.
 */
public final class RemoteImages {
    public static final int MAX_SIDE = 2048;
    private static final int MAX_DOWNLOAD = 8 * 1024 * 1024;
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;

    public enum State { LOADING, READY, FAILED }

    /** A picture: its texture and size once {@link State#READY}. */
    public static final class Entry {
        final String url;
        volatile State state = State.LOADING;
        volatile Identifier texture;
        volatile int width;
        volatile int height;

        Entry(String url) {
            this.url = url;
        }

        public State state() {
            return state;
        }

        public Identifier texture() {
            return texture;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public boolean ready() {
            return state == State.READY && texture != null;
        }
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static ExecutorService workers;

    private RemoteImages() {
    }

    /** The picture at a link, starting its load the first time. Render thread. */
    public static synchronized Entry get(String url) {
        Entry entry = ENTRIES.get(url);
        if (entry == null) {
            entry = new Entry(url);
            ENTRIES.put(url, entry);
            load(entry);
        }
        return entry;
    }

    private static synchronized ExecutorService workers() {
        if (workers == null) {
            workers = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "SkyRPG remote images");
                thread.setDaemon(true);
                return thread;
            });
        }
        return workers;
    }

    private static void load(Entry entry) {
        workers().execute(() -> {
            Path cached = cacheFile(entry.url);
            byte[] old = null;
            try {
                if (Files.isRegularFile(cached)) {
                    old = Files.readAllBytes(cached);
                    show(entry, decode(old));
                }
            } catch (IOException | RuntimeException e) {
                old = null;
            }
            try {
                byte[] fresh = download(entry.url);
                if (old != null && java.util.Arrays.equals(old, fresh)) {
                    return;
                }
                BufferedImage image = decode(fresh);
                Files.createDirectories(cached.getParent());
                Files.write(cached, fresh);
                show(entry, image);
            } catch (Exception e) {
                SkyRpgCore.LOGGER.warn("Couldn't load picture {}: {}", entry.url, e.getMessage());
                if (entry.state != State.READY) {
                    entry.state = State.FAILED;
                }
            }
        });
    }

    private static void show(Entry entry, BufferedImage image) {
        NativeImage pixels = toNative(image);
        int width = image.getWidth();
        int height = image.getHeight();
        Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            Identifier id = Identifier.fromNamespaceAndPath(SkyRpgCore.MOD_ID, "remote/" + sha1(entry.url).substring(0, 24)
                    + "_" + Long.toHexString(System.nanoTime()));
            Identifier previous = entry.texture;
            minecraft.getTextureManager().register(id, new DynamicTexture(() -> "skyrpg remote " + entry.url, pixels));
            entry.texture = id;
            entry.width = width;
            entry.height = height;
            entry.state = State.READY;
            if (previous != null) {
                minecraft.getTextureManager().release(previous);
            }
        });
    }

    private static Path cacheFile(String url) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("cache").resolve("skyrpg").resolve("remote").resolve(sha1(url) + ".img");
    }

    private static String sha1(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private static boolean blocked(InetAddress address) {
        return address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }

    private static byte[] download(String link) throws IOException {
        URI uri = URI.create(link);
        for (int hop = 0; hop < 4; hop++) {
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme))) {
                throw new IOException("not a web link");
            }
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (blocked(address)) {
                    throw new IOException("private address");
                }
            }
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", "SkyRPG/1.0");
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
                if (connection.getContentLengthLong() > MAX_DOWNLOAD) {
                    throw new IOException("too big");
                }
                try (InputStream in = connection.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
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

    private static BufferedImage decode(byte[] bytes) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null) {
            throw new IOException("not a picture");
        }
        if ((long) source.getWidth() * source.getHeight() > MAX_SOURCE_PIXELS) {
            throw new IOException("picture too large");
        }
        int width = source.getWidth();
        int height = source.getHeight();
        double scale = Math.min(1, (double) MAX_SIDE / Math.max(width, height));
        int nw = Math.max(1, (int) Math.round(width * scale));
        int nh = Math.max(1, (int) Math.round(height * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        if (scale < 1) {
            g.drawImage(source.getScaledInstance(nw, nh, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        } else {
            g.drawImage(source, 0, 0, null);
        }
        g.dispose();
        return out;
    }

    private static NativeImage toNative(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        NativeImage pixels = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                pixels.setPixelABGR(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
            }
        }
        return pixels;
    }
}
