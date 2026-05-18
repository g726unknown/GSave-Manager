package com.g726;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SettingsManager {
    private static final String SETTINGS_FILE = "settings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger LOGGER = LoggingManager.getLogger();

    private SettingsManager() {
    }

    public static AppSettings load() {
        Path path = Paths.get(SETTINGS_FILE);
        if (!Files.exists(path)) {
            return new AppSettings();
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            AppSettings settings = GSON.fromJson(reader, AppSettings.class);
            if (settings == null) {
                return new AppSettings();
            }
            return settings;
        } catch (IOException | JsonParseException e) {
            LOGGER.log(Level.WARNING, "读取 settings.json 失败，将使用默认设置。", e);
            return new AppSettings();
        }
    }

    public static void save(AppSettings settings) {
        Path path = Paths.get(SETTINGS_FILE);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(settings, writer);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "保存 settings.json 失败。", e);
        }
    }
}
