package dev.adapt.v3.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.adapt.v3.Adapt;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// Reads the shared config repo (github.com/cooldood-dev/adapt-config): the contents API lists the *.json files,
// and each one is downloaded straight from raw.githubusercontent.com into the local configs folder, after which it
// loads like any other named config (so the user's keybinds are left untouched, same as a local load).
public final class OnlineConfigs {
    public enum State {
        IDLE,
        LOADING,
        LOADED,
        ERROR
    }

    public static final String REPO = "cooldood-dev/adapt-config";
    private static final String LIST_URL = "https://api.github.com/repos/" + REPO + "/contents/";
    private static final String RAW_BASE = "https://raw.githubusercontent.com/" + REPO + "/main/";
    private static final OnlineConfigs INSTANCE = new OnlineConfigs();

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10L)).build();
    private volatile State state = State.IDLE;
    private volatile List<String> names = List.of();
    private volatile String error = "";
    private volatile String downloading;

    public static OnlineConfigs getInstance() {
        return INSTANCE;
    }

    public State getState() {
        return this.state;
    }

    public List<String> getNames() {
        return this.names;
    }

    public String getError() {
        return this.error;
    }

    public boolean isDownloading(String name) {
        return name != null && name.equals(this.downloading);
    }

    // Fetches the list of online configs. No-op while already loading, or when loaded unless forced (used to retry).
    public void refresh(boolean force) {
        if (this.state == State.LOADING || !force && this.state == State.LOADED) {
            return;
        }

        this.state = State.LOADING;
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(LIST_URL))
                    .timeout(Duration.ofSeconds(12L))
                    .header("User-Agent", "adaptv3/3.1")
                    .header("Accept", "application/vnd.github+json")
                    .GET()
                    .build();
                HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    this.fail("adapt-config returned HTTP " + response.statusCode());
                    return;
                }

                JsonElement parsed = JsonParser.parseString(response.body());
                List<String> list = new ArrayList<>();
                if (parsed.isJsonArray()) {
                    for (JsonElement element : parsed.getAsJsonArray()) {
                        if (!element.isJsonObject()) {
                            continue;
                        }

                        JsonObject object = element.getAsJsonObject();
                        String fileName = object.has("name") ? object.get("name").getAsString() : "";
                        String type = object.has("type") ? object.get("type").getAsString() : "";
                        if ("file".equals(type) && fileName.endsWith(".json")) {
                            String name = fileName.substring(0, fileName.length() - 5);
                            if (ConfigManager.isValidConfigName(name)) {
                                list.add(name);
                            }
                        }
                    }
                }

                list.sort(String.CASE_INSENSITIVE_ORDER);
                this.names = List.copyOf(list);
                this.error = "";
                this.state = State.LOADED;
            } catch (Exception exception) {
                this.fail(exception.getMessage() == null ? "Couldn't reach adapt-config" : exception.getMessage());
            }
        });
    }

    private void fail(String message) {
        this.error = message;
        this.state = State.ERROR;
    }

    // Downloads one online config into the local configs folder. Returns true on success. The caller loads it afterwards.
    public CompletableFuture<Boolean> download(String name) {
        this.downloading = name;
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!ConfigManager.isValidConfigName(name)) {
                    return false;
                }

                HttpRequest request = HttpRequest.newBuilder(URI.create(RAW_BASE + name + ".json"))
                    .timeout(Duration.ofSeconds(12L))
                    .header("User-Agent", "adaptv3/3.1")
                    .GET()
                    .build();
                HttpResponse<String> response = this.http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    return false;
                }

                // only write it if it is actually a config object, so a 404 page or garbage never lands in the folder
                JsonElement parsed = JsonParser.parseString(response.body());
                if (!parsed.isJsonObject()) {
                    return false;
                }

                ConfigManager configManager = Adapt.getInstance().getConfigManager();
                Files.createDirectories(configManager.getConfigsDir());
                Files.writeString(configManager.resolveConfigPath(name), response.body());
                return true;
            } catch (Exception exception) {
                Adapt.LOGGER.error("Failed to download online config " + name, exception);
                return false;
            } finally {
                this.downloading = null;
            }
        });
    }
}
