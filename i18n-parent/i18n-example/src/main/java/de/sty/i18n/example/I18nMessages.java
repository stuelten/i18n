package de.sty.i18n.example;

import de.sty.i18n.I18nText;
import java.util.Locale;

public class I18nMessages {

    public static final I18nText I18NEXAMPLE_GREETING = I18nText.of("I18nExample.greeting", "name");

    public static String greeting(String name) {
        return I18NEXAMPLE_GREETING.i18n(name);
    }

    public static String greeting(Locale locale, String name) {
        return I18NEXAMPLE_GREETING.i18n(locale, name);
    }

    public static final I18nText I18NEXAMPLE_ITEMS_COUNT = I18nText.of("I18nExample.items_count", "count", "items");

    public static String itemsCount(Number count, java.util.List<?> items) {
        return I18NEXAMPLE_ITEMS_COUNT.i18n(count, items);
    }

    public static String itemsCount(Locale locale, Number count, java.util.List<?> items) {
        return I18NEXAMPLE_ITEMS_COUNT.i18n(locale, count, items);
    }

    public static final I18nText I18NEXAMPLE_PRICE_TAG = I18nText.of("I18nExample.price_tag", "amount", "date");

    public static String priceTag(Number amount, java.util.Date date) {
        return I18NEXAMPLE_PRICE_TAG.i18n(amount, date);
    }

    public static String priceTag(Locale locale, Number amount, java.util.Date date) {
        return I18NEXAMPLE_PRICE_TAG.i18n(locale, amount, date);
    }

    public static final I18nText I18NEXAMPLE_TYPED_GREETING = I18nText.of("I18nExample.typed_greeting", "name");

    public static String typedGreeting(String name) {
        return I18NEXAMPLE_TYPED_GREETING.i18n(name);
    }

    public static String typedGreeting(Locale locale, String name) {
        return I18NEXAMPLE_TYPED_GREETING.i18n(locale, name);
    }

    public static final I18nText I18NEXAMPLE_WELCOME_MSG = I18nText.of("I18nExample.welcome_msg", "user");

    public static String welcomeMsg(String user) {
        return I18NEXAMPLE_WELCOME_MSG.i18n(user);
    }

    public static String welcomeMsg(Locale locale, String user) {
        return I18NEXAMPLE_WELCOME_MSG.i18n(locale, user);
    }

}
