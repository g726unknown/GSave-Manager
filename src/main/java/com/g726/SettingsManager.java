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
    private static final Logger LOGGER = SomeUtils.getLogger();

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

class AppSettings {
    private boolean saveToLatestBranch = true;
    private java.util.Map<String, String> branchLimits = new java.util.HashMap<>();
    private String autoSaveMode = "OFF";
    private int autoSaveIntervalSeconds = 300;
    private int autoSaveDebounceSeconds = 5;

    public boolean isSaveToLatestBranch() {
        return saveToLatestBranch;
    }

    public void setSaveToLatestBranch(boolean saveToLatestBranch) {
        this.saveToLatestBranch = saveToLatestBranch;
    }

    public java.util.Map<String, String> getBranchLimits() {
        return branchLimits;
    }

    public void setBranchLimits(java.util.Map<String, String> branchLimits) {
        this.branchLimits = branchLimits == null ? new java.util.HashMap<>() : branchLimits;
    }

    public String getAutoSaveMode() {
        return autoSaveMode;
    }

    public void setAutoSaveMode(String autoSaveMode) {
        this.autoSaveMode = autoSaveMode == null ? "OFF" : autoSaveMode;
    }

    public int getAutoSaveIntervalSeconds() {
        return autoSaveIntervalSeconds;
    }

    public void setAutoSaveIntervalSeconds(int autoSaveIntervalSeconds) {
        this.autoSaveIntervalSeconds = autoSaveIntervalSeconds;
    }

    public int getAutoSaveDebounceSeconds() {
        return autoSaveDebounceSeconds;
    }

    public void setAutoSaveDebounceSeconds(int autoSaveDebounceSeconds) {
        this.autoSaveDebounceSeconds = autoSaveDebounceSeconds;
    }
}
