package org.example.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public enum LocalizationManager {
    INSTANCE;

    public static LocalizationManager getInstance() { return INSTANCE; }

    private Locale current = Locale.ENGLISH;
    private ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", current);

    public Locale getCurrent() { return current; }

    public boolean isRussian() { return current.getLanguage().equals("ru"); }

    public void setRussian(boolean ru) {
        current = ru ? Locale.of("ru", "RU") : Locale.ENGLISH;
        bundle = ResourceBundle.getBundle("i18n.messages", current);
    }

    public String get(String key) {
        try { return bundle.getString(key); }
        catch (MissingResourceException e) { return "!" + key; }
    }

    public String get(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }
}
