package de.sty.i18n;

import com.ibm.icu.text.MessageFormat;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An i18n text template, which handles type-safe, named placeholders and reduces boilerplate in declarations.
 */
public final class I18nText {

    private static final Map<Class<?>, ResourceBundleCache> BUNDLE_CACHE = new ConcurrentHashMap<>();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)(,.*?)?\\}");

    private static LocaleProvider localeProvider = Locale::getDefault;
    private final Class<?> source;
    private final String key;
    private final String[] parameterNames;
    private final Class<?>[] parameterTypes;
    private final Map<Locale, MessageFormat> formatters = new ConcurrentHashMap<>();
    private final Map<Locale, String> processedTemplates = new ConcurrentHashMap<>();
    private volatile String resolvedKey;

    private I18nText(Class<?> source, String key, String[] parameterNames, Class<?>[] parameterTypes) {
        this.source = source;
        this.key = key;
        this.parameterNames = parameterNames;
        this.parameterTypes = parameterTypes;
    }

    /**
     * Sets the locale provider to be used when no locale is explicitly provided.
     *
     * @param provider The locale provider.
     */
    public static void setLocaleProvider(LocaleProvider provider) {
        localeProvider = Objects.requireNonNull(provider);
    }

    /**
     * Creates a new I18nText template.
     * The source class and the key are deduced from the calling class and field name.
     *
     * @param parameterNames The names of the placeholders in the template.
     * @return A new I18nText template.
     */
    public static I18nText ofField(String... parameterNames) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        // The key will be resolved lazily when first used, by searching for the field in caller class.
        return new I18nText(caller, null, parameterNames, null);
    }

    /**
     * Creates a new I18nText template.
     * The source class is automatically detected using the calling class.
     *
     * @param key            The key in the properties file.
     * @param parameterNames The names of the placeholders in the template.
     * @return A new I18nText template.
     */
    public static I18nText of(String key, String... parameterNames) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        String fullKey = caller.getSimpleName() + "." + key;
        return new I18nText(caller, fullKey, parameterNames, null);
    }

    /**
     * Creates a new I18nText template.
     *
     * @param source         The class that defines this i18n text. Used to find the properties file.
     * @param key            The key in the properties file.
     * @param parameterNames The names of the placeholders in the template.
     * @return A new I18nText template.
     */
    public static I18nText of(Object source, String key, String... parameterNames) {
        Class<?> sourceClass = source instanceof Class<?> ? (Class<?>) source : source.getClass();
        String fullKey = sourceClass.getSimpleName() + "." + key;
        return new I18nText(sourceClass, fullKey, parameterNames, null);
    }

    /**
     * Creates a new type-safe I18nText template with 1 parameter.
     */
    public static <T1> I18nText1<T1> of(String key, Class<T1> type1, String name1) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        I18nText base = new I18nText(caller, caller.getSimpleName() + "." + key, new String[]{name1}, new Class[]{type1});
        return new I18nText1<>() {
            @Override
            public String i18n(T1 p1) {
                return base.i18n(p1);
            }

            @Override
            public String i18n(Locale locale, T1 p1) {
                return base.i18n(locale, p1);
            }

            @Override
            public I18nText base() {
                return base;
            }

            @Override
            public String toString() {
                return base.toString();
            }
        };
    }

    /**
     * Creates a new type-safe I18nText template with 2 parameters.
     */
    public static <T1, T2> I18nText2<T1, T2> of(String key, Class<T1> type1, String name1, Class<T2> type2, String name2) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        I18nText base = new I18nText(caller, caller.getSimpleName() + "." + key, new String[]{name1, name2}, new Class[]{type1, type2});
        return new I18nText2<>() {
            @Override
            public String i18n(T1 p1, T2 p2) {
                return base.i18n(p1, p2);
            }

            @Override
            public String i18n(Locale locale, T1 p1, T2 p2) {
                return base.i18n(locale, p1, p2);
            }

            @Override
            public I18nText base() {
                return base;
            }

            @Override
            public String toString() {
                return base.toString();
            }
        };
    }

    /**
     * Creates a new type-safe I18nText template with 3 parameters.
     */
    public static <T1, T2, T3> I18nText3<T1, T2, T3> of(String key, Class<T1> type1, String name1, Class<T2> type2, String name2, Class<T3> type3, String name3) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        I18nText base = new I18nText(caller, caller.getSimpleName() + "." + key, new String[]{name1, name2, name3}, new Class[]{type1, type2, type3});
        return new I18nText3<>() {
            @Override
            public String i18n(T1 p1, T2 p2, T3 p3) {
                return base.i18n(p1, p2, p3);
            }

            @Override
            public String i18n(Locale locale, T1 p1, T2 p2, T3 p3) {
                return base.i18n(locale, p1, p2, p3);
            }

            @Override
            public I18nText base() {
                return base;
            }

            @Override
            public String toString() {
                return base.toString();
            }
        };
    }

    /**
     * Creates a new type-safe I18nText template with 1 parameter, deducing the key from the field name.
     */
    public static <T1> I18nText1<T1> ofField(Class<T1> type1, String name1) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        I18nText base = new I18nText(caller, null, new String[]{name1}, new Class[]{type1});
        return new I18nText1<>() {
            @Override
            public String i18n(T1 p1) {
                return base.i18n(p1);
            }

            @Override
            public String i18n(Locale locale, T1 p1) {
                return base.i18n(locale, p1);
            }

            @Override
            public I18nText base() {
                return base;
            }

            @Override
            public String toString() {
                return base.toString();
            }
        };
    }

    /**
     * Creates a new type-safe I18nText template with 2 parameters, deducing the key from the field name.
     */
    public static <T1, T2> I18nText2<T1, T2> ofField(Class<T1> type1, String name1, Class<T2> type2, String name2) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        I18nText base = new I18nText(caller, null, new String[]{name1, name2}, new Class[]{type1, type2});
        return new I18nText2<>() {
            @Override
            public String i18n(T1 p1, T2 p2) {
                return base.i18n(p1, p2);
            }

            @Override
            public String i18n(Locale locale, T1 p1, T2 p2) {
                return base.i18n(locale, p1, p2);
            }

            @Override
            public I18nText base() {
                return base;
            }

            @Override
            public String toString() {
                return base.toString();
            }
        };
    }

    /**
     * Creates a new type-safe I18nText template with 3 parameters, deducing the key from the field name.
     */
    public static <T1, T2, T3> I18nText3<T1, T2, T3> ofField(Class<T1> type1, String name1, Class<T2> type2, String name2, Class<T3> type3, String name3) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        I18nText base = new I18nText(caller, null, new String[]{name1, name2, name3}, new Class[]{type1, type2, type3});
        return new I18nText3<>() {
            @Override
            public String i18n(T1 p1, T2 p2, T3 p3) {
                return base.i18n(p1, p2, p3);
            }

            @Override
            public String i18n(Locale locale, T1 p1, T2 p2, T3 p3) {
                return base.i18n(locale, p1, p2, p3);
            }

            @Override
            public I18nText base() {
                return base;
            }

            @Override
            public String toString() {
                return base.toString();
            }
        };
    }

    /**
     * Resolves the i18n text for the default locale.
     *
     * @param args The values for the placeholders.
     * @return The formatted string.
     */
    public String i18n(Object... args) {
        return i18n(localeProvider.getLocale(), args);
    }

    /**
     * Resolves the i18n text for a specific locale.
     *
     * @param locale The locale to use.
     * @param args   The values for the placeholders.
     * @return The formatted string.
     * @throws IllegalArgumentException if the of is invalid or parameters are missing.
     */
    public String i18n(Locale locale, Object... args) {
        try {
            validateTypes(args);
            MessageFormat formatter = getFormatter(locale);
            if (formatter == null) {
                return getTemplate(locale); // Return the !key! or simple of if no placeholders
            }
            return formatter.format(args);
        } catch (Exception e) {
            return "!!ERROR: " + e.getMessage() + "!!";
        }
    }

    /**
     * Resolves the i18n text for the default locale using a map of arguments.
     *
     * @param args The map of placeholder names to values.
     * @return The formatted string.
     */
    public String i18n(Map<String, ?> args) {
        return i18n(localeProvider.getLocale(), args);
    }

    /**
     * Resolves the i18n text for a specific locale using a map of arguments.
     *
     * @param locale The locale to use.
     * @param args   The map of placeholder names to values.
     * @return The formatted string.
     * @throws IllegalArgumentException if the of is invalid or parameters are missing.
     */
    public String i18n(Locale locale, Map<String, ?> args) {
        try {
            MessageFormat formatter = getFormatter(locale);
            if (formatter == null) {
                return getTemplate(locale);
            }
            Object[] positionalArgs = new Object[parameterNames.length];
            for (int i = 0; i < parameterNames.length; i++) {
                Object val = args != null ? args.get(parameterNames[i]) : null;
                if (parameterTypes != null && val != null && !parameterTypes[i].isInstance(val)) {
                    throw new IllegalArgumentException("Type mismatch for parameter '" + parameterNames[i] + "': expected " + parameterTypes[i].getSimpleName() + " but got " + val.getClass().getSimpleName());
                }
                positionalArgs[i] = val;
            }
            return formatter.format(positionalArgs);
        } catch (Exception e) {
            return "!!ERROR: " + e.getMessage() + "!!";
        }
    }

    private void validateTypes(Object[] args) {
        if (parameterTypes == null || args == null) return;
        for (int i = 0; i < Math.min(parameterTypes.length, args.length); i++) {
            if (args[i] != null && !parameterTypes[i].isInstance(args[i])) {
                throw new IllegalArgumentException("Type mismatch for parameter '" + parameterNames[i] + "': expected " + parameterTypes[i].getSimpleName() + " but got " + args[i].getClass().getSimpleName());
            }
        }
    }

    private MessageFormat getFormatter(Locale locale) {
        return formatters.computeIfAbsent(locale, l -> {
            String template = getTemplate(l);
            if (template.startsWith("!") && template.endsWith("!")) {
                return null;
            }

            if (parameterNames.length == 0 && !template.contains("{")) {
                return null;
            }

            Map<String, Integer> nameToIndex = new HashMap<>();
            for (int i = 0; i < parameterNames.length; i++) {
                nameToIndex.put(parameterNames[i], i);
            }

            String processedTemplate = processedTemplates.computeIfAbsent(l, loc -> replacePlaceholders(template, nameToIndex));
            return new MessageFormat(processedTemplate, l);
        });
    }

    private String getResolvedKey() {
        if (resolvedKey != null) return resolvedKey;
        if (key != null) {
            resolvedKey = key;
            return resolvedKey;
        }

        // Deduce the key from the field name in the source class.
        // Must be thread-safe because it can happen long after
        // this instance got created when loading the source class
        // (which usually uses this instance as constant)
        synchronized (this) {
            if (resolvedKey != null) return resolvedKey;

            for (Field field : source.getDeclaredFields()) {
                if (isI18nField(field)) {
                    try {
                        field.setAccessible(true);
                        Object val = field.get(null);

                        if (i18nFieldMatchesThisInstance(val)) {
                            resolvedKey = source.getSimpleName() + "." + field.getName().toLowerCase(Locale.ROOT);
                            return resolvedKey;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            resolvedKey = source.getSimpleName() + ".unknown";
            return resolvedKey;
        }
    }

    private boolean isI18nField(Field field) {
        Class<?> type = field.getType();
        return type == I18nText.class ||
               type == I18nText1.class ||
               type == I18nText2.class ||
               type == I18nText3.class;
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean i18nFieldMatchesThisInstance(Object val) {
        if (val == this) return true;
        if (val instanceof I18nText1 && ((I18nText1<?>) val).base() == this) return true;
        if (val instanceof I18nText2 && ((I18nText2<?, ?>) val).base() == this) return true;
        if (val instanceof I18nText3 && ((I18nText3<?, ?, ?>) val).base() == this) return true;
        return false;
    }

    private String replacePlaceholders(String template, Map<String, Integer> nameToIndex) {
        StringBuilder result = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String format = matcher.group(2);
            Integer index = nameToIndex.get(placeholder);
            if (index != null) {
                matcher.appendReplacement(result, "{" + index + (format != null ? format : "") + "}");
            } else {
                matcher.appendReplacement(result, matcher.group(0));
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Validates all templates for this I18nText across all provided locales.
     *
     * @param locales The locales to validate.
     * @return A map of locale to error-message, or an empty map if all are valid.
     */
    public Map<Locale, String> validate(Locale... locales) {
        Map<Locale, String> errors = new HashMap<>();
        for (Locale locale : locales) {
            String template = getTemplate(locale);
            if (template.startsWith("!") && template.endsWith("!")) {
                errors.put(locale, "Missing key: " + getResolvedKey());
                continue;
            }

            // Check unbalanced braces
            int open = 0;
            for (int i = 0; i < template.length(); i++) {
                char c = template.charAt(i);
                if (c == '{') open++;
                else if (c == '}') open--;
                if (open < 0) break;
            }
            if (open != 0) {
                errors.put(locale, "Unbalanced curly braces in: " + template);
                continue;
            }

            Map<String, Integer> nameToIndex = new HashMap<>();
            for (int i = 0; i < parameterNames.length; i++) {
                nameToIndex.put(parameterNames[i], i);
            }

            // Check for unknown placeholders
            Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
            while (matcher.find()) {
                String placeholder = matcher.group(1);
                if (!nameToIndex.containsKey(placeholder)) {
                    errors.put(locale, "Unknown placeholder {" + placeholder + "} in: " + template);
                    break;
                }
            }
            if (errors.containsKey(locale)) continue;

            try {
                String processedTemplate = replacePlaceholders(template, nameToIndex);
                new MessageFormat(processedTemplate, locale);
            } catch (Exception e) {
                errors.put(locale, "Invalid ICU pattern: " + e.getMessage() + " in: " + template);
            }
        }
        return errors;
    }

    private String getTemplate(Locale locale) {
        String actualKey = getResolvedKey();
        try {
            ResourceBundle bundle = BUNDLE_CACHE.computeIfAbsent(source, ResourceBundleCache::new).getBundle(locale);
            return bundle.getString(actualKey);
        } catch (MissingResourceException e) {
            return "!" + actualKey + "!";
        }
    }

    @Override
    public String toString() {
        // Avoid output of templates by accident
        String bundleName = source.getPackageName() + ".i18n";
        return "[I18nText: bundle='" + bundleName + "', key='" + getResolvedKey() + "', parameterNames:'" + Arrays.toString(parameterNames) + "']";
    }

    /**
     * Interface to provide the locale to use.
     */
    @FunctionalInterface
    public interface LocaleProvider {
        Locale getLocale();
    }

    /**
     * Creates a type-safe I18nText with 1 parameter.
     */
    public interface I18nText1<T1> {
        String i18n(T1 p1);

        String i18n(Locale locale, T1 p1);

        I18nText base();
    }

    /**
     * Creates a type-safe I18nText with 2 parameters.
     */
    public interface I18nText2<T1, T2> {
        String i18n(T1 p1, T2 p2);

        String i18n(Locale locale, T1 p1, T2 p2);

        I18nText base();
    }

    /**
     * Creates a type-safe I18nText with 3 parameters.
     */
    public interface I18nText3<T1, T2, T3> {
        String i18n(T1 p1, T2 p2, T3 p3);

        String i18n(Locale locale, T1 p1, T2 p2, T3 p3);

        I18nText base();
    }

    private static class ResourceBundleCache {
        private final Class<?> source;
        private final Map<Locale, ResourceBundle> bundles = new ConcurrentHashMap<>();

        ResourceBundleCache(Class<?> source) {
            this.source = source;
        }

        ResourceBundle getBundle(Locale locale) {
            return bundles.computeIfAbsent(locale, l -> {
                String baseName = getBaseName();
                ResourceBundle.Control noFallback = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
                try {
                    return ResourceBundle.getBundle(baseName, l, source.getClassLoader(), noFallback);
                } catch (MissingResourceException e) {
                    // Fallback to "i18n" in the same package as source
                    return ResourceBundle.getBundle("i18n", l, source.getClassLoader(), noFallback);
                }
            });
        }

        private String getBaseName() {
            return source.getPackageName() + ".i18n";
        }
    }
}
