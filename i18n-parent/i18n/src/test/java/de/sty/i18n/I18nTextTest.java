package de.sty.i18n;

import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class I18nTextTest {

    private final static I18nText GREETING = I18nText.ofField("name");
    private final static I18nText BIGGER_THAN = I18nText.ofField("given", "expected");
    private final static I18nText COMPLEX = I18nText.ofField("time", "user", "action", "ip");
    private final static I18nText PLURAL = I18nText.ofField("count");
    private final static I18nText BROKEN_BRACES = I18nText.ofField("name");
    private final static I18nText UNKNOWN_PLACEHOLDER = I18nText.ofField("name");
    private final static I18nText INVALID_PATTERN = I18nText.ofField("count");
    private final static I18nText CURRENCY = I18nText.ofField("amount");
    private final static I18nText.I18nText1<String> TYPED_GREETING = I18nText.ofField(String.class, "name");
    private final static I18nText.I18nText2<Long, Long> TYPED_BIGGER_THAN = I18nText.ofField(Long.class, "given", Long.class, "expected");
    private final static I18nText.I18nText2<Double, java.util.Date> TYPED_PRICE_TAG = I18nText.ofField(Double.class, "amount", java.util.Date.class, "date");
    private final static I18nText.I18nText3<String, String, String> TYPED_THREE = I18nText.ofField(String.class, "p1", String.class, "p2", String.class, "p3");

    @Test
    void testI18nWithMapDefaultLocale() {
        try {
            I18nText.setLocaleProvider(() -> Locale.ENGLISH);
            I18nText greeting = I18nText.of(I18nTextTest.class, "greeting", "name");
            assertThat(greeting.i18n(Map.of("name", "Paul"))).isEqualTo("Hello Paul!");
        } finally {
            I18nText.setLocaleProvider(Locale::getDefault);
        }
    }

    @Test
    void testExplicitKeyTypedFactories() {
        // 2 parameters
        I18nText.I18nText2<String, String> t2 = I18nText.of("bigger_than", String.class, "given", String.class, "expected");
        assertThat(t2.i18n(Locale.ENGLISH, "A", "B")).isEqualTo("A is not bigger than B!");

        // 3 parameters
        I18nText.I18nText3<String, String, String> t3 = I18nText.of("typed_three", String.class, "p1", String.class, "p2", String.class, "p3");
        assertThat(t3.i18n(Locale.ENGLISH, "a", "b", "c")).isEqualTo("Three: a, b, c");
        assertThat(t3.base().toString()).contains("I18nTextTest.typed_three");
    }

    @Test
    void testI18nText3AndI18nNamed3() {
        // Deduced key is I18nTextTest.typed_three
        I18nText base = TYPED_THREE.base();
        // Key is included in toString representation; avoid calling private internals
        assertThat(base.toString()).contains("I18nTextTest.typed_three");

        // Let's create an explicit one to verify the logic without relying on field deduction if it fails in some environments
        I18nText.I18nText3<String, String, String> explicit = I18nText.of("typed_three", String.class, "p1", String.class, "p2", String.class, "p3");
        assertThat(explicit.i18n(Locale.ENGLISH, "a", "b", "c")).isEqualTo("Three: a, b, c");

        // Also test I18nText3.i18n(T1, T2, T3)
        try {
            I18nText.setLocaleProvider(() -> Locale.ENGLISH);
            assertThat(explicit.i18n("a", "b", "c")).isEqualTo("Three: a, b, c");
        } finally {
            I18nText.setLocaleProvider(Locale::getDefault);
        }
    }

    @Test
    void testGetTemplate() {
        // Verify the code branch where the formatter is null (no placeholders)
        assertThat(I18nText.of("plain").i18n(Locale.ENGLISH)).isEqualTo("Just plain");
        assertThat(I18nText.of("plain").i18n(Locale.GERMAN)).isEqualTo("Einfach nur Text");
    }

    @Test
    void testGreeting() {
        assertThat(GREETING.i18n(Locale.ENGLISH, "Paul")).isEqualTo("Hello Paul!");
        assertThat(GREETING.i18n(Locale.GERMAN, "Paul")).isEqualTo("Guten Tag, Paul!");
    }

    @Test
    void testBiggerThan() {
        assertThat(BIGGER_THAN.i18n(Locale.ENGLISH, 5L, 10L)).isEqualTo("5 is not bigger than 10!");
        assertThat(BIGGER_THAN.i18n(Locale.GERMAN, 5L, 10L)).isEqualTo("Erwartet wird 10, aber gegeben wurde 5!");
    }

    @Test
    void testToStringDoesNotRevealTemplate() {
        assertThat(GREETING.toString()).contains("bundle='de.sty.i18n.i18n'").contains("key='I18nTextTest.greeting'").doesNotContain("Hello").doesNotContain("Guten Tag");
    }

    @Test
    void testMissingKey() {
        I18nText missing = I18nText.of("missing_key");
        assertThat(missing.i18n()).isEqualTo("!I18nTextTest.missing_key!");
    }

    @Test
    void testAutoDeduceKey() {
        assertThat(GREETING.i18n(Locale.ENGLISH, "Paul")).isEqualTo("Hello Paul!");
        // The GREETING field name is GREETING, so it should resolve to I18nTextTest.greeting
        // Our implementation uses field.getName().toLowerCase()
        assertThat(GREETING.toString()).contains("I18nTextTest.greeting");
    }

    @Test
    void testI18nWithMap() {
        I18nText greeting = I18nText.of(I18nTextTest.class, "greeting", "name");
        assertThat(greeting.i18n(Locale.ENGLISH, Map.of("name", "Paul"))).isEqualTo("Hello Paul!");
        assertThat(greeting.i18n(Locale.GERMAN, Map.of("name", "Paul"))).isEqualTo("Guten Tag, Paul!");
    }

    @Test
    void testI18nWithMapMultiple() {
        I18nText biggerThan = I18nText.of(I18nTextTest.class, "bigger_than", "given", "expected");
        Map<String, Object> params = Map.of("given", 5L, "expected", 10L);
        assertThat(biggerThan.i18n(Locale.ENGLISH, params)).isEqualTo("5 is not bigger than 10!");
        assertThat(biggerThan.i18n(Locale.GERMAN, params)).isEqualTo("Erwartet wird 10, aber gegeben wurde 5!");
    }

    @Test
    void testComplexFormatting() {
        Calendar cal = Calendar.getInstance();
        cal.set(2024, Calendar.MAY, 10, 14, 30, 0);
        java.util.Date date = cal.getTime();

        Map<String, Object> params = Map.of("time", date, "user", "admin", "action", "logged in", "ip", "127.0.0.1");

        // EN: At {time, time, short} on {time, date, medium}, user {user} {action} from {ip}.
        // At 2:30 PM on May 10, 2024, user admin logged in from 127.0.0.1.
        // Note: Exact format might vary by environment, so we use "contains" for key parts
        String enResult = COMPLEX.i18n(Locale.ENGLISH, params);
        assertThat(enResult).contains("2:30");
        assertThat(enResult).contains("PM");
        assertThat(enResult).contains("May 10, 2024");
        assertThat(enResult).contains("user admin logged in from 127.0.0.1");

        // DE: Am {time, date, medium} um {time, time, short} hat Benutzer {user} {action} von {ip} ausgeführt.
        // Am 10.05.2024 um 14:30 hat Benutzer admin logged in von 127.0.0.1 ausgeführt.
        String deResult = COMPLEX.i18n(Locale.GERMAN, params);
        assertThat(deResult).contains("14:30");
        // No AM/PM with german
        assertThat(deResult).contains("10.05.2024");
        assertThat(deResult).contains("Benutzer admin");
        assertThat(deResult).contains("logged in");
        assertThat(deResult).contains("von 127.0.0.1");
    }

    @Test
    void testPluralFormatting() {
        assertThat(PLURAL.i18n(Locale.ENGLISH, 1)).isEqualTo("1 item");
        assertThat(PLURAL.i18n(Locale.ENGLISH, 2)).isEqualTo("2 items");
        assertThat(PLURAL.i18n(Locale.ENGLISH, 0)).isEqualTo("0 items");
    }

    @Test
    void testValidation() {
        assertThat(GREETING.validate(Locale.ENGLISH)).isEmpty();
        assertThat(COMPLEX.validate(Locale.ENGLISH)).isEmpty();
        assertThat(PLURAL.validate(Locale.ENGLISH)).isEmpty();

        assertThat(BROKEN_BRACES.validate(Locale.ENGLISH)).anySatisfy((locale, message) -> {
            assertThat(message).containsAnyOf("Unbalanced curly braces", "Unmatched '{' braces");
            assertThat(message).contains("Hello {name");
        });

        assertThat(UNKNOWN_PLACEHOLDER.validate(Locale.ENGLISH)).containsEntry(Locale.ENGLISH, "Unknown placeholder {friend} in: Hello {friend}!");

        assertThat(INVALID_PATTERN.validate(Locale.ENGLISH)).containsKey(Locale.ENGLISH);
        assertThat(INVALID_PATTERN.validate(Locale.ENGLISH).get(Locale.ENGLISH)).contains("Invalid ICU pattern");

        I18nText missingKey = I18nText.of(this, "absolutely_missing");
        assertThat(missingKey.validate(Locale.ENGLISH)).containsEntry(Locale.ENGLISH, "Missing key: I18nTextTest.absolutely_missing");
    }

    @Test
    void testErrorHandlingDuringFormatting() {
        String result = BROKEN_BRACES.i18n(Locale.ENGLISH, "Paul");
        assertThat(result).startsWith("!!ERROR:");
    }

    @Test
    void testCurrencyFormatting() {
        // US English uses $ prefix
        String enUsResult = CURRENCY.i18n(Locale.US, 1234.56);
        assertThat(enUsResult).contains("$").contains("1,234.56");

        // Germany (usually) uses € suffix
        String deDeResult = CURRENCY.i18n(Locale.GERMANY, 1234.56);
        // ICU/Java might use different characters for space or € depending on their version,
        // but it should contain the currency symbol and the formatted number.
        assertThat(deDeResult).contains("1.234,56").contains("€");
    }

    @Test
    void testLocaleProvider() {
        try {
            // Set a provider that always returns German
            I18nText.setLocaleProvider(() -> Locale.GERMAN);
            assertThat(GREETING.i18n("Paul")).isEqualTo("Guten Tag, Paul!");

            // Set a provider that always returns English
            I18nText.setLocaleProvider(() -> Locale.ENGLISH);
            assertThat(GREETING.i18n("Paul")).isEqualTo("Hello Paul!");
        } finally {
            // Reset to default behavior
            I18nText.setLocaleProvider(Locale::getDefault);
        }
    }

    @Test
    void testTypeSafeApi() {
        assertThat(TYPED_GREETING.i18n(Locale.ENGLISH, "Paul")).isEqualTo("Hello Paul!");
        assertThat(TYPED_BIGGER_THAN.i18n(Locale.ENGLISH, 5L, 10L)).isEqualTo("5 is not bigger than 10!");

        // Typed currency and date
        java.util.Date date = new java.util.GregorianCalendar(2024, java.util.Calendar.MAY, 10).getTime();
        String enResult = TYPED_PRICE_TAG.i18n(Locale.US, 1234.56, date);
        assertThat(enResult).contains("$1,234.56").contains("May 10, 2024");

        String deResult = TYPED_PRICE_TAG.i18n(Locale.GERMANY, 1234.56, date);
        assertThat(deResult).contains("1.234,56").contains("€").contains("10. Mai 2024");

        // Key deduction for typed i18nNamed
        assertThat(TYPED_GREETING.toString()).contains("bundle='de.sty.i18n.i18n'").contains("I18nTextTest.typed_greeting");
        assertThat(TYPED_PRICE_TAG.toString()).contains("bundle='de.sty.i18n.i18n'").contains("I18nTextTest.typed_price_tag");
    }

    @Test
    void testTypeMismatch() {
        I18nText.I18nText1<Double> typedCurrency = I18nText.of("currency", Double.class, "amount");
        // This will allow Double. But if we try to call the underlying base with a wrong type:
        String errorResult = typedCurrency.base().i18n(Locale.ENGLISH, "not a double");
        assertThat(errorResult).contains("!!ERROR: Type mismatch for parameter 'amount': expected Double but got String!!");
    }

    @Test
    void testTypeMismatchInMap() {
        // We use I18nText.of(String, Class, String) to get a typed template, then access the field "base"
        I18nText base = I18nText.of("currency", Double.class, "amount").base();
        String result = base.i18n(Locale.ENGLISH, Map.of("amount", "not a double"));
        assertThat(result).contains("!!ERROR: Type mismatch for parameter 'amount': expected Double but got String!!");
    }
}
