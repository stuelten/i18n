package de.sty.i18n.example;

import de.sty.i18n.I18nText;
import de.sty.i18n.I18nText.I18nText1;
import de.sty.i18n.I18nText.I18nText2;

import java.util.Date;
import java.util.Locale;

public class I18nExample {

    // 1. Basic usage with field name as the key (I18nExample.greeting)
    private static final I18nText GREETING = I18nText.ofField("name");

    // 2. Explicit key (I18nExample.welcome_msg)
    private static final I18nText WELCOME = I18nText.of("welcome_msg", "user");

    // 3. Type-safe declaration (1 parameter)
    private static final I18nText1<String> TYPED_GREETING = I18nText.ofField(String.class, "name");

    // 4. Type-safe declaration (2 parameters: Double and Date)
    private static final I18nText2<Double, Date> PRICE_TAG = I18nText.ofField(Double.class, "amount", Date.class, "date");

    // 5. Plural support
    private static final I18nText ITEMS_COUNT = I18nText.ofField("count");

    public static void main(String[] args) {
        System.out.println("--- I18n Library Examples ---");

        // Use default locale
        runExamples(Locale.getDefault());

        // Use other locales
        runExamples(Locale.ENGLISH);
        runExamples(Locale.GERMAN);
        runExamples(Locale.FRENCH);
        runExamples(Locale.forLanguageTag("cs"));
        runExamples(Locale.forLanguageTag("pl"));
        runExamples(Locale.forLanguageTag("ar"));

        // A locale without ressource bundel/properties file uses en by default
        runExamples(Locale.forLanguageTag("th"));
    }

    private static void runExamples(Locale locale) {
        System.out.println("\nLocale: " + locale.getDisplayName(Locale.ENGLISH));

        // Basic usage
        System.out.println("GREETING: " + GREETING.i18n(locale, "Peter-Paul"));
        System.out.println("WELCOME:  " + WELCOME.i18n(locale, "Developer"));

        // Type-safe usage
        System.out.println("TYPED_GREETING: " + TYPED_GREETING.i18n(locale, "Zoë-Marie"));

        // Price and Date formatting (using ICU4J power)
        System.out.println("PRICE_TAG: " + PRICE_TAG.i18n(locale, 1234.56, new Date()));

        // Plurals are funny in different languages
        int[] counts = {0, 1, 2, 3, 5, 11, 22, 100, 101, 102, 103, 111};
        for (int count : counts) {
            System.out.println("ITEMS (" + count + "): " + ITEMS_COUNT.i18n(locale, count));
        }
    }
}
