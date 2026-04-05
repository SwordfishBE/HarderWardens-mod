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
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModrinthUpdateChecker {

    private static final String PROJECT_ID = "YYlTINeM";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
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
                .header("User-Agent", HarderWardensMod.MOD_NAME.replace(" ", "") + "/" + currentVersion())
                .GET()
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                HarderWardensMod.LOGGER.debug("{} Update check returned HTTP {}.",
                        HarderWardensMod.LOG_PREFIX, response.statusCode());
                return;
            }

            Optional<String> latestVersion = extractLatestVersion(response.body());
            if (latestVersion.isEmpty()) {
                HarderWardensMod.LOGGER.debug("{} Update check returned no usable versions.",
                        HarderWardensMod.LOG_PREFIX);
                return;
            }

            String currentVersion = currentVersion();
            String newestVersion = latestVersion.get();
            if (isNewerVersion(newestVersion, currentVersion)) {
                HarderWardensMod.LOGGER.info("{} New version available: {} (current: {})",
                        HarderWardensMod.LOG_PREFIX, newestVersion, currentVersion);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            HarderWardensMod.LOGGER.debug("{} Update check failed.", HarderWardensMod.LOG_PREFIX, e);
        }
    }

    private static Optional<String> extractLatestVersion(String responseBody) {
        JsonElement root = JsonParser.parseString(responseBody);
        if (!root.isJsonArray()) {
            return Optional.empty();
        }

        JsonArray versions = root.getAsJsonArray();
        VersionCandidate newestCompatible = null;
        VersionCandidate newestRelease = null;
        String currentMinecraftVersion = currentMinecraftVersion();

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
            if (!"release".equalsIgnoreCase(versionType)) {
                continue;
            }

            Instant publishedAt = getPublishedAt(versionObject);
            if (publishedAt == null) {
                continue;
            }

            VersionCandidate candidate = new VersionCandidate(versionNumber, publishedAt);
            if (newestRelease == null || candidate.publishedAt().isAfter(newestRelease.publishedAt())) {
                newestRelease = candidate;
            }

            if (jsonArrayContains(versionObject, "loaders", "fabric")
                    && jsonArrayContains(versionObject, "game_versions", currentMinecraftVersion)
                    && (newestCompatible == null || candidate.publishedAt().isAfter(newestCompatible.publishedAt()))) {
                newestCompatible = candidate;
            }
        }

        if (newestCompatible != null) {
            return Optional.of(newestCompatible.versionNumber());
        }

        return Optional.ofNullable(newestRelease).map(VersionCandidate::versionNumber);
    }

    private static boolean jsonArrayContains(JsonObject object, String key, String expectedValue) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray()) {
            return false;
        }

        for (JsonElement element : value.getAsJsonArray()) {
            if (element != null && element.isJsonPrimitive() && expectedValue.equalsIgnoreCase(element.getAsString())) {
                return true;
            }
        }

        return false;
    }

    private static Instant getPublishedAt(JsonObject object) {
        String publishedAt = getString(object, "date_published");
        if (publishedAt == null || publishedAt.isBlank()) {
            return null;
        }

        try {
            return Instant.parse(publishedAt);
        } catch (RuntimeException ignored) {
            return null;
        }
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

    private static String currentMinecraftVersion() {
        return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    private static boolean isNewerVersion(String candidate, String current) {
        try {
            Version candidateVersion = Version.parse(candidate);
            Version currentVersion = Version.parse(current);
            return candidateVersion.compareTo(currentVersion) > 0;
        } catch (VersionParsingException e) {
            HarderWardensMod.LOGGER.debug("{} Could not compare versions. candidate='{}', current='{}'.",
                    HarderWardensMod.LOG_PREFIX, candidate, current, e);
            return false;
        }
    }

    private record VersionCandidate(String versionNumber, Instant publishedAt) {
    }
}
