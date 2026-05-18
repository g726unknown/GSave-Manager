package com.g726;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class JsonManager {

    private static final String CONFIG_FILE = "archives_config.json";
    private static final Logger LOGGER = LoggingManager.getLogger();

    private static final Gson gson = new GsonBuilder()
            .registerTypeHierarchyAdapter(Path.class, new PathAdapter())
            .registerTypeAdapter(java.time.LocalDateTime.class, new LocalDateTimeAdapter()) 
            .setPrettyPrinting()
            .create();

    public static void saveToJson(List<GameArchive> archives) {
        try (Writer writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(archives, writer);
            LOGGER.info("存档配置已成功保存到: " + CONFIG_FILE);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "保存 JSON 失败", e);
        }
    }

    public static List<GameArchive> loadFromJson() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Reader reader = new FileReader(file)) {
            Type listType = new TypeToken<ArrayList<GameArchive>>() {
            }.getType();
            return gson.fromJson(reader, listType);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "读取 JSON 失败", e);
            return new ArrayList<>();
        }
    }

    private static class PathAdapter implements JsonSerializer<Path>, JsonDeserializer<Path> {
        @Override
        public JsonElement serialize(Path src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toAbsolutePath().toString());
        }

        @Override
        public Path deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            return Paths.get(json.getAsString());
        }
    }

    private static class LocalDateTimeAdapter implements JsonSerializer<LocalDateTime>, JsonDeserializer<LocalDateTime> {
        // 定义一个你喜欢的日期时间格式
        private static final java.time.format.DateTimeFormatter formatter = 
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        @Override
        public JsonElement serialize(LocalDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            // 保存到 JSON 时，格式化为字符串
            return new JsonPrimitive(src.format(formatter));
        }

        @Override
        public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) {
            // 从 JSON 读取时，解析回 LocalDateTime 对象
            return LocalDateTime.parse(json.getAsString(), formatter);
        }
    }
}
