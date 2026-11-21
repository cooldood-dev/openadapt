package dev.adapt.v3.feature.module.impl.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.adapt.v3.feature.property.impl.BooleanProperty;
import dev.adapt.v3.hud.HudModule;
import dev.adapt.v3.hud.HudStyle;
import dev.adapt.v3.ui.render.Fonts;
import dev.adapt.v3.ui.render.Render2D;
import dev.adapt.v3.ui.render.SmoothTexture;
import dev.adapt.v3.ui.theme.Theme;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

// A now-playing card driven by the Windows media session (Spotify, a browser, any player). A hidden PowerShell helper
// (assets/adaptv3/media.ps1) streams the current track as tab-separated lines; this decodes the cover art into a texture
// and pulls synced lyrics from lrclib.net. Modern UI only for now - renders nothing while OldSchool is active.
public final class MediaPlayerHud extends HudModule {
    private static final Identifier COVER_ID = Identifier.fromNamespaceAndPath("adaptv3", "textures/dynamic/media_cover");
    private static final Pattern LRC_LINE = Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)]\\s*(.*)");
    private static final float CARD_W = 164.0F;
    private static final float INSET = 6.0F;
    private static final float COVER = 36.0F;
    private static final float BASE_H = 46.0F;
    private static final float LINE_H = 11.0F;
    private static final float LYRIC_TOP = 4.0F;
    private static final float LYRIC_BOTTOM = 3.0F;
    private static final long STALE_MS = 5000L;
    private static final long LYRIC_RETRY_MS = 15000L;
    private static final int COVER_MAX = 128;
    private static final int MUTED = 0xFFB4B4B4;
    private static final int TRACK_BG = 0x33FFFFFF;
    // current line bright, upcoming lines fading out, like the reference
    private static final int LYRIC_CURRENT = -1;
    private static final int LYRIC_NEXT = 0xFF9A9A9A;
    private static final int LYRIC_DIM = 0xFF6A6A6A;

    public final BooleanProperty cover = new BooleanProperty("Cover", true);
    public final BooleanProperty lyrics = new BooleanProperty("Lyrics", true);
    public final BooleanProperty progress = new BooleanProperty("Progress", true);
    public final BooleanProperty hidePaused = new BooleanProperty("Hide Paused", false);

    private static String script;
    private volatile Track track;
    private volatile Lyrics lyricData;
    private volatile String lyricsKey = "";
    private volatile long lyricRetryAt;
    private final AtomicReference<Cover> pendingCover = new AtomicReference<>();
    private volatile Process process;
    private volatile int generation;
    private Thread reader;
    private Thread shutdownHook;
    private ThreadPoolExecutor worker;
    private NativeImage coverImage;
    private SmoothTexture coverTexture;
    private boolean coverLoaded;
    private volatile float lyricBand;

    public MediaPlayerHud() {
        super("MediaPlayer", "Now-playing card with cover art and synced lyrics.", 0.006F, 0.72F);
        this.addProperties(this.cover, this.lyrics, this.progress, this.hidePaused);
    }

    @Override
    public float getWidth() {
        return CARD_W;
    }

    @Override
    public float getHeight() {
        return BASE_H + this.lyricBand;
    }

    @Override
    public boolean shouldRender() {
        return Theme.isModern();
    }

    // ---- lifecycle -------------------------------------------------------

    @Override
    public void onEnable() {
        this.start();
    }

    @Override
    public void onDisable() {
        this.stop();
    }

    private static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private synchronized void start() {
        if (this.reader != null || !windows()) {
            return;
        }

        int id = ++this.generation;
        Thread thread = new Thread(() -> this.run(id), "Adapt MediaPlayer reader");
        thread.setDaemon(true);
        this.reader = thread;
        if (this.shutdownHook == null) {
            this.shutdownHook = new Thread(this::kill, "Adapt MediaPlayer shutdown");
            try {
                Runtime.getRuntime().addShutdownHook(this.shutdownHook);
            } catch (IllegalStateException ignored) {
            }
        }

        thread.start();
    }

    private synchronized void stop() {
        this.generation++;
        Thread thread = this.reader;
        this.reader = null;
        if (thread != null) {
            thread.interrupt();
        }

        this.kill();
        if (this.shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
            } catch (IllegalStateException ignored) {
            }

            this.shutdownHook = null;
        }

        if (this.worker != null) {
            this.worker.shutdownNow();
            this.worker = null;
        }

        this.track = null;
        this.lyricData = null;
        this.lyricsKey = "";
        this.lyricRetryAt = 0L;
        this.pendingCover.set(new Cover(null));
    }

    private void kill() {
        Process current = this.process;
        this.process = null;
        if (current != null) {
            current.destroy();
        }
    }

    private boolean alive(int id) {
        return this.generation == id && this.isEnabled();
    }

    private void run(int id) {
        long wait = 2000L;
        CoverState covers = new CoverState();
        while (this.alive(id)) {
            long started = System.currentTimeMillis();
            Process current = null;
            try {
                current = this.launch(id);
                if (current == null) {
                    return;
                }

                this.read(current, id, covers);
            } catch (Exception ignored) {
            } finally {
                if (current != null) {
                    current.destroy();
                    if (this.process == current) {
                        this.process = null;
                    }
                }
            }

            if (!this.alive(id)) {
                return;
            }

            this.track = null;
            wait = System.currentTimeMillis() - started > 60000L ? 2000L : Math.min(30000L, wait * 2L);
            try {
                Thread.sleep(wait);
            } catch (InterruptedException interrupted) {
                return;
            }
        }
    }

    private Process launch(int id) throws IOException {
        String source = script();
        if (source == null) {
            return null;
        }

        String full = "$parentId = " + processId() + "\n" + source;
        String encoded = Base64.getEncoder().encodeToString(full.getBytes(StandardCharsets.UTF_16LE));
        ProcessBuilder builder = new ProcessBuilder(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded
        );
        builder.redirectErrorStream(true);
        Process started = builder.start();
        synchronized (this) {
            if (this.generation != id) {
                started.destroy();
                return null;
            }

            this.process = started;
        }

        try {
            started.getOutputStream().close();
        } catch (IOException ignored) {
        }

        return started;
    }

    private void read(Process current, int id, CoverState covers) throws IOException {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(current.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = input.readLine()) != null) {
                if (!this.alive(id) || this.process != current) {
                    return;
                }

                long now = System.currentTimeMillis();
                Track next = parse(line, now);
                if (next == null) {
                    continue;
                }

                this.track = next;
                if (this.cover.getValue()) {
                    Cover loaded = loadCover(covers, next.coverPath, now);
                    if (loaded != null && this.alive(id)) {
                        this.pendingCover.set(loaded);
                    }
                }

                this.updateLyrics(next, now, id);
            }
        }
    }

    // ---- parsing ---------------------------------------------------------

    static Track parse(String line, long now) {
        if (line == null) {
            return null;
        }

        String text = line.startsWith("﻿") ? line.substring(1) : line;
        String[] parts = text.split("\t", -1);
        if (parts.length < 7 || parts[0].trim().isEmpty()) {
            return null;
        }

        long duration = Math.max(0L, number(parts[4]));
        long position = Math.max(0L, Math.min(number(parts[3]), duration > 0L ? duration : Long.MAX_VALUE));
        return new Track(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[6].trim(), position, duration, "1".equals(parts[5].trim()), now);
    }

    private static long number(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException failure) {
            return 0L;
        }
    }

    static long position(Track track, long now) {
        long value = track.position + (track.playing ? Math.max(0L, now - track.receivedAt) : 0L);
        return track.duration > 0L ? Math.min(value, track.duration) : value;
    }

    static String clock(long ms) {
        long seconds = Math.max(0L, ms) / 1000L;
        return seconds / 60L + ":" + String.format(Locale.ROOT, "%02d", seconds % 60L);
    }

    // ---- cover art -------------------------------------------------------

    static Cover loadCover(CoverState state, String path, long now) {
        if (!path.equals(state.path)) {
            state.path = path;
            state.tries = 0;
            state.retryAt = 0L;
            state.done = false;
        }

        if (state.done || now < state.retryAt) {
            return null;
        }

        if (path.isEmpty()) {
            state.done = true;
            return new Cover(null);
        }

        try {
            int[] pixels = decode(new File(path));
            state.done = true;
            return new Cover(pixels);
        } catch (Exception | LinkageError failure) {
            state.tries++;
            if (state.tries < 4) {
                state.retryAt = now + (500L << (state.tries - 1));
                return null;
            }

            state.done = true;
            return new Cover(null);
        }
    }

    static int[] decode(File file) throws IOException {
        if (!file.isFile() || file.length() <= 0L) {
            throw new IOException("cover not ready");
        }

        BufferedImage image = ImageIO.read(file);
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("no image reader accepted the file");
        }

        int side = Math.min(image.getWidth(), image.getHeight());
        int sx = (image.getWidth() - side) / 2;
        int sy = (image.getHeight() - side) / 2;
        int target = Math.min(side, COVER_MAX);
        BufferedImage scaled = new BufferedImage(target, target, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(image, 0, 0, target, target, sx, sy, sx + side, sy + side, null);
        graphics.dispose();
        int[] pixels = new int[target * target];
        scaled.getRGB(0, 0, target, target, pixels, 0, target);
        return pixels;
    }

    // Builds the cover texture on the render thread; call once per frame before drawing.
    private void uploadCover() {
        Cover next = this.pendingCover.getAndSet(null);
        if (next == null) {
            return;
        }

        if (next.pixels == null) {
            this.dropTexture();
            return;
        }

        int size = next.size;
        try {
            NativeImage image = new NativeImage(size, size, true);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    image.setPixel(x, y, next.pixels[y * size + x]);
                }
            }

            this.dropTexture();
            this.coverImage = image;
            this.coverTexture = new SmoothTexture(COVER_ID.toString(), image);
            mc.getTextureManager().register(COVER_ID, this.coverTexture);
            this.coverTexture.upload();
            this.coverLoaded = true;
        } catch (Exception | LinkageError failure) {
            this.dropTexture();
        }
    }

    private void dropTexture() {
        if (this.coverLoaded) {
            try {
                mc.getTextureManager().release(COVER_ID);
            } catch (Exception ignored) {
            }
        }

        this.coverLoaded = false;
        this.coverImage = null;
        this.coverTexture = null;
    }

    // ---- lyrics ----------------------------------------------------------

    private void updateLyrics(Track next, long now, int id) {
        if (!this.lyrics.getValue()) {
            return;
        }

        String key = next.key();
        Lyrics current = this.lyricData;
        boolean retry = key.equals(this.lyricsKey)
            && this.lyricRetryAt > 0L
            && now >= this.lyricRetryAt
            && (current == null || current.lines.length == 0);
        if (key.equals(this.lyricsKey) && !retry) {
            return;
        }

        this.lyricsKey = key;
        this.lyricRetryAt = 0L;
        this.lyricData = null;
        ThreadPoolExecutor executor = this.executor(id);
        if (executor != null) {
            String title = next.title;
            String artist = next.artist;
            String album = next.album;
            long duration = next.duration;
            executor.execute(() -> this.fetchLyrics(key, title, artist, album, duration, id));
        }
    }

    private synchronized ThreadPoolExecutor executor(int id) {
        if (this.generation != id) {
            return null;
        }

        if (this.worker == null || this.worker.isShutdown()) {
            this.worker = new ThreadPoolExecutor(1, 1, 30L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "Adapt MediaPlayer lyrics");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());
            this.worker.allowCoreThreadTimeOut(true);
        }

        return this.worker;
    }

    private void fetchLyrics(String key, String title, String artist, String album, long duration, int id) {
        try {
            String query = "track_name=" + URLEncoder.encode(title, "UTF-8") + "&artist_name=" + URLEncoder.encode(artist, "UTF-8");
            String body = get("https://lrclib.net/api/get?" + query + "&album_name=" + URLEncoder.encode(album, "UTF-8")
                + "&duration=" + Math.round(duration / 1000.0));
            String synced = body == null ? null : synced(JsonParser.parseString(body));
            if (synced == null) {
                body = get("https://lrclib.net/api/search?" + query);
                JsonElement found = body == null ? null : JsonParser.parseString(body);
                if (found != null && found.isJsonArray()) {
                    JsonArray results = found.getAsJsonArray();
                    for (int i = 0; i < results.size() && synced == null; i++) {
                        synced = synced(results.get(i));
                    }
                }
            }

            Lyrics result = parseLyrics(key, synced);
            if (this.alive(id) && key.equals(this.lyricsKey)) {
                this.lyricData = result;
            }
        } catch (Exception failure) {
            if (this.alive(id) && key.equals(this.lyricsKey)) {
                this.lyricRetryAt = System.currentTimeMillis() + LYRIC_RETRY_MS;
            }
        }
    }

    static String synced(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }

        JsonObject object = element.getAsJsonObject();
        JsonElement value = object.get("syncedLyrics");
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    static Lyrics parseLyrics(String key, String text) {
        List<Long> times = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        if (text != null) {
            for (String raw : text.split("\n")) {
                Matcher matcher = LRC_LINE.matcher(raw.trim());
                if (!matcher.matches()) {
                    continue;
                }

                try {
                    times.add(Long.parseLong(matcher.group(1)) * 60000L + Math.round(Double.parseDouble(matcher.group(2)) * 1000.0));
                    lines.add(matcher.group(3).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        long[] stamps = new long[times.size()];
        for (int i = 0; i < stamps.length; i++) {
            stamps[i] = times.get(i);
        }

        return new Lyrics(key, stamps, lines.toArray(new String[0]));
    }

    static int lyricIndex(long[] times, long position) {
        int index = -1;
        for (int i = 0; i < times.length && times[i] <= position; i++) {
            index = i;
        }

        return index;
    }

    private static String get(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)new URL(address).openConnection();
        try {
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "Adapt+ (Minecraft HUD)");
            int code = connection.getResponseCode();
            if (code >= 500 || code == 429) {
                throw new IOException("lyrics request failed with " + code);
            }

            if (code != 200) {
                return null;
            }

            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }

                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    // ---- rendering (modern only) ----------------------------------------

    @Override
    public void render(GuiGraphicsExtractor graphics, float partialTick, boolean editing) {
        if (!Theme.isModern()) {
            return;
        }

        this.uploadCover();
        long now = System.currentTimeMillis();
        Track current = this.track;
        if (current != null && (now - current.receivedAt > STALE_MS || this.hidePaused.getValue() && !current.playing)) {
            current = null;
        }

        long elapsed = current == null ? 0L : position(current, now);

        // Build the synced lyric rows: the active line bright, the next lines fading out. Empty when there are no synced
        // lyrics for this track, which leaves just the plain card.
        List<String> rowText = new ArrayList<>();
        List<Integer> rowColor = new ArrayList<>();
        Lyrics words = this.lyricData;
        if (current != null && this.lyrics.getValue() && words != null && words.lines.length > 0 && words.key.equals(current.key())) {
            int index = lyricIndex(words.times, elapsed);
            if (index < 0) {
                // before the first timestamp: preview the opening lines, dimmed
                addRow(rowText, rowColor, words, 0, LYRIC_NEXT);
                addRow(rowText, rowColor, words, 1, LYRIC_DIM);
            } else {
                addRow(rowText, rowColor, words, index, LYRIC_CURRENT);
                addRow(rowText, rowColor, words, index + 1, LYRIC_NEXT);
                addRow(rowText, rowColor, words, index + 2, LYRIC_DIM);
            }
        }

        float band = rowText.isEmpty() ? 0.0F : LYRIC_TOP + rowText.size() * LINE_H + LYRIC_BOTTOM;
        this.lyricBand = band;
        float height = BASE_H + band;
        HudStyle.drawCard(graphics, 0.0F, 0.0F, CARD_W, height);
        if (current == null) {
            Fonts.SMALL.drawVCentered(graphics, "Nothing playing", INSET, BASE_H / 2.0F, MUTED);
            return;
        }

        boolean art = this.cover.getValue() && this.coverLoaded;
        float left = art ? INSET * 2.0F + COVER : INSET;
        float right = CARD_W - INSET;
        if (art) {
            Render2D.blitRoundedTexture(graphics, COVER_ID, INSET, INSET, COVER, COVER, 4.0F, 0.0F, 0.0F, 1.0F, 1.0F, -1);
            graphics.nextStratum();
        }

        Fonts.SEMIBOLD.draw(graphics, Fonts.SEMIBOLD.ellipsize(current.title, right - left), left, 7.0F, -1);
        Fonts.SMALL.draw(graphics, Fonts.SMALL.ellipsize(current.artist, right - left), left, 18.0F, MUTED);
        if (this.progress.getValue() && current.duration > 0L) {
            float amount = Math.min(1.0F, elapsed / (float)current.duration);
            float room = right - left;
            Render2D.fillRoundedRect(graphics, left, 31.0F, room, 2.0F, 1.0F, TRACK_BG);
            if (amount > 0.0F) {
                Render2D.fillRoundedRect(graphics, left, 31.0F, Math.max(2.0F, room * amount), 2.0F, 1.0F, Theme.getAccentColor());
            }

            Fonts.SMALL.draw(graphics, clock(elapsed), left, 35.0F, 0xFF9A9A9A);
            Fonts.SMALL.drawRightAligned(graphics, clock(current.duration), right, 35.0F, 0xFF9A9A9A);
        }

        if (!rowText.isEmpty()) {
            Render2D.fillRect(graphics, INSET, BASE_H - 1.0F, CARD_W - INSET * 2.0F, 0.5F, 0x14FFFFFF);
            float textWidth = CARD_W - INSET * 2.0F;
            for (int i = 0; i < rowText.size(); i++) {
                int color = rowColor.get(i);
                Fonts font = color == LYRIC_CURRENT ? Fonts.MEDIUM : Fonts.SMALL;
                float y = BASE_H + LYRIC_TOP + i * LINE_H;
                font.draw(graphics, font.ellipsize(rowText.get(i), textWidth), INSET, y, color);
            }
        }
    }

    // Appends one lyric row (an empty timed line becomes a music note, matching how lyric apps mark instrumental gaps).
    private static void addRow(List<String> text, List<Integer> color, Lyrics words, int index, int tint) {
        if (index >= 0 && index < words.lines.length) {
            String line = words.lines[index];
            text.add(line.isEmpty() ? "• • •" : line);
            color.add(tint);
        }
    }

    private static synchronized String script() {
        if (script == null) {
            try (InputStream input = MediaPlayerHud.class.getResourceAsStream("/assets/adaptv3/media.ps1")) {
                if (input == null) {
                    return null;
                }

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }

                script = new String(output.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException failure) {
                return null;
            }
        }

        return script;
    }

    private static long processId() {
        try {
            String name = ManagementFactory.getRuntimeMXBean().getName();
            return Long.parseLong(name.substring(0, name.indexOf('@')));
        } catch (Exception failure) {
            return 0L;
        }
    }

    // ---- data holders ----------------------------------------------------

    static final class Track {
        final String title;
        final String artist;
        final String album;
        final String coverPath;
        final long position;
        final long duration;
        final boolean playing;
        final long receivedAt;

        Track(String title, String artist, String album, String coverPath, long position, long duration, boolean playing, long receivedAt) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.coverPath = coverPath;
            this.position = position;
            this.duration = duration;
            this.playing = playing;
            this.receivedAt = receivedAt;
        }

        String key() {
            return this.title + "|" + this.artist;
        }
    }

    static final class Lyrics {
        final String key;
        final long[] times;
        final String[] lines;

        Lyrics(String key, long[] times, String[] lines) {
            this.key = key;
            this.times = times;
            this.lines = lines;
        }
    }

    static final class Cover {
        final int[] pixels;
        final int size;

        Cover(int[] pixels) {
            this.pixels = pixels;
            this.size = pixels == null ? 0 : (int)Math.round(Math.sqrt(pixels.length));
        }
    }

    static final class CoverState {
        String path = "";
        int tries;
        long retryAt;
        boolean done;
    }
}
