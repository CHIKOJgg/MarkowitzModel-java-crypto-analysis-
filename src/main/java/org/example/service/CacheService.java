package org.example.service;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;

public class CacheService {

    private static final String CACHE_DIR = "cache/";
    private static final long TTL_MILLIS = 6 * 60 * 60 * 1000; // 6 часов

    public CacheService() {
        new File(CACHE_DIR).mkdirs();
    }

    public String get(String key) {
        try {
            Path path = Paths.get(CACHE_DIR + key + ".json");

            if (!Files.exists(path)) return null;

            long lastModified = Files.getLastModifiedTime(path).toMillis();

            if (Instant.now().toEpochMilli() - lastModified > TTL_MILLIS) {
                return null; // устарел
            }

            return Files.readString(path);

        } catch (Exception e) {
            return null;
        }
    }

    public void put(String key, String data) {
        try {
            Path path = Paths.get(CACHE_DIR + key + ".json");
            Files.writeString(path, data);
        } catch (Exception ignored) {}
    }
}