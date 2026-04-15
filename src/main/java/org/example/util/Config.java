package org.example.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Reads / writes config.properties at the project root.
 * API key can also be overridden at runtime via {@link #set}.
 */
public class Config {

    private static final String CONFIG_FILE = "config.properties";
    private static final Properties props = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (Exception ignored) {
            // Running without a config file is fine – key can be set via UI
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }

    /** Override / add a property at runtime (does NOT persist to disk automatically). */
    public static void set(String key, String value) {
        props.setProperty(key, value);
    }

    /** Persist current properties back to config.properties. */
    public static void save() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Crypto Portfolio Optimizer config");
        } catch (Exception e) {
            throw new RuntimeException("Could not save config.properties", e);
        }
    }
}
