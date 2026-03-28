package net.harderwardens.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.harderwardens.HarderWardensMod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModrinthUpdateChecker {

    private static final String PROJECT_ID = "YYlTINeM";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private static final AtomicBoolean CHECK_STARTED = new AtomicBoolean(false);

    private ModrinthUpdateChecker() {
    }

    public static void checkOnceAsync() {
        if (!CHECK_STARTED.compareAndSet(false, true)) {
            return;
        }

        Thread thread = new Thread(ModrinthUpdateChecker::checkForUpdate, "harder-wardens-modrinth-update-check");
        thread.setDaemon(true);
        thread.start();
    }

    private static void checkForUpdate() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version"))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "HarderWardens/" + currentVersion())
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                HarderWardensMod.LOGGER.debug("[HarderWardens] Update check returned HTTP {}.", response.statusCode());
                return;
            }

            Optional<String> latestVersion = extractLatestVersion(response.body());
            if (latestVersion.isEmpty()) {
                HarderWardensMod.LOGGER.debug("[HarderWardens] Update check returned no usable versions.");
                return;
            }

            String currentVersion = currentVersion();
            String newestVersion = latestVersion.get();
            if (isNewerVersion(newestVersion, currentVersion)) {
                HarderWardensMod.LOGGER.debug("[HarderWardens] New version available: {} (current: {})",
                        newestVersion, currentVersion);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            HarderWardensMod.LOGGER.debug("[HarderWardens] Update check failed.", e);
        }
    }

    private static Optional<String> extractLatestVersion(String responseBody) {
        JsonElement root = JsonParser.parseString(responseBody);
        if (!root.isJsonArray()) {
            return Optional.empty();
        }

        JsonArray versions = root.getAsJsonArray();
        String fallback = null;

        for (JsonElement versionElement : versions) {
            if (!versionElement.isJsonObject()) {
                continue;
            }

            JsonObject versionObject = versionElement.getAsJsonObject();
            String versionNumber = getString(versionObject, "version_number");
            if (versionNumber == null || versionNumber.isBlank()) {
                continue;
            }

            String versionType = getString(versionObject, "version_type");
            if ("release".equalsIgnoreCase(versionType)) {
                return Optional.of(versionNumber);
            }

            if (fallback == null) {
                fallback = versionNumber;
            }
        }

        return Optional.ofNullable(fallback);
    }

    private static String getString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }

        return value.getAsString();
    }

    private static String currentVersion() {
        return FabricLoader.getInstance()
                .getModContainer(HarderWardensMod.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static boolean isNewerVersion(String candidate, String current) {
        try {
            Version candidateVersion = Version.parse(candidate);
            Version currentVersion = Version.parse(current);
            return candidateVersion.compareTo(currentVersion) > 0;
        } catch (VersionParsingException ignored) {
            return !candidate.equals(current);
        }
    }
}
