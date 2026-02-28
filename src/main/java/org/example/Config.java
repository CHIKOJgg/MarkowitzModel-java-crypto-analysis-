package org.example;

import java.io.*;
import java.util.*;

public class Config {
    private static final Properties props = new Properties();

    static {
        try {
            props.load(new FileInputStream("config.properties"));
        } catch (Exception e) {
            throw new RuntimeException("Нет config.properties файла!");
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}