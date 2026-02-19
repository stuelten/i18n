package de.sty.i18n.generator;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Command(name = "i18n-generator", mixinStandardHelpOptions = true, version = "0.1",
        description = "Generates a type-safe I18n class from a properties file.")
public class I18nGenerator implements Callable<Integer> {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(?:[^{}]+|\\{(?:[^{}]+|\\{[^{}]*})*})*}");
    @Parameters(index = "0", description = "The properties file to parse.")
    private Path propertiesFile;
    @Option(names = {"-o", "--output"}, description = "The output Java file. If not specified, prints to stdout.")
    private Path outputFile;
    @Option(names = {"-p", "--package"}, description = "The package name for the generated class.")
    private String packageName;
    @Option(names = {"-c", "--class"}, description = "The class name for the generated class. Defaults to the properties filename.")
    private String className;
    @Option(names = {"--android"}, description = "Generate Android-style R class with integer IDs.")
    private boolean androidStyle;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new I18nGenerator()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        if (!Files.exists(propertiesFile)) {
            System.err.println("File not found: " + propertiesFile);
            return 1;
        }

        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
            props.load(reader);
        }

        if (className == null) {
            String filename = propertiesFile.getFileName().toString();
            int dotIndex = filename.indexOf('.');
            className = (dotIndex == -1) ? filename : filename.substring(0, dotIndex);
            // Basic sanitization
            className = className.replaceAll("[^a-zA-Z0-9_]", "");
        }

        List<I18nEntry> entries = new ArrayList<>();
        List<String> keys = props.stringPropertyNames().stream().sorted().collect(Collectors.toList());
        for (String key : keys) {
            String value = props.getProperty(key);
            entries.add(parseEntry(key, value));
        }

        String code = generateCode(packageName, className, entries, androidStyle);

        if (outputFile != null) {
            Files.createDirectories(outputFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                writer.write(code);
            }
            System.out.println("Generated: " + outputFile);
        } else {
            System.out.println(code);
        }

        return 0;
    }

    private I18nEntry parseEntry(String key, String value) {
        List<String> topLevelPlaceholders = findTopLevelPlaceholders(value);
        Map<String, Placeholder> placeholders = new LinkedHashMap<>();
        for (String content : topLevelPlaceholders) {
            // content is without the outer braces
            // ICU format: {name, type, style}
            String[] parts = content.split(",", 2);
            String firstPart = parts[0].trim();

            String name = firstPart;
            String typeStr = "String";

            if (firstPart.contains(":")) {
                int colonIndex = firstPart.indexOf(':');
                name = firstPart.substring(0, colonIndex).trim();
                typeStr = firstPart.substring(colonIndex + 1).trim();
            } else if (parts.length > 1) {
                String remainder = parts[1].trim();
                String formatType = remainder.split(",")[0].trim();
                if ("number".equals(formatType)) {
                    typeStr = "Number";
                } else if ("date".equals(formatType) || "time".equals(formatType)) {
                    typeStr = "java.util.Date";
                }
            }

            placeholders.putIfAbsent(name, new Placeholder(name, typeStr));
        }
        return new I18nEntry(key, new ArrayList<>(placeholders.values()));
    }

    private List<String> findTopLevelPlaceholders(String value) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    result.add(value.substring(start + 1, i));
                    start = -1;
                }
            }
        }
        return result;
    }

    private String generateCode(String packageName, String className, List<I18nEntry> entries, boolean androidStyle) {
        StringBuilder sb = new StringBuilder();
        if (packageName != null && !packageName.isEmpty()) {
            sb.append("package ").append(packageName).append(";\n\n");
        }

        sb.append("import de.sty.i18n.I18nText;\n");
        sb.append("import java.util.Locale;\n");
        if (androidStyle) {
            sb.append("import java.util.Map;\n");
            sb.append("import java.util.HashMap;\n");
        }
        sb.append("\n");
        sb.append("public class ").append(className).append(" {\n\n");

        if (androidStyle) {
            generateAndroidStyle(sb, entries);
        } else {
            generateStandardStyle(sb, className, entries);
        }

        sb.append("}\n");
        return sb.toString();
    }

    private void generateStandardStyle(StringBuilder sb, String className, List<I18nEntry> entries) {
        for (I18nEntry entry : entries) {
            String constantName = entry.key.replace('.', '_').toUpperCase();
            // Constant declaration
            sb.append("    /**\n");
            sb.append("     * Key: {@code ").append(entry.key).append("}<br>\n");
            sb.append("     * Placeholders: ").append(entry.placeholders.stream().map(p -> p.name + " (" + p.javaType + ")").collect(Collectors.joining(", "))).append("\n");
            sb.append("     */\n");
            sb.append("    public static final I18nText ").append(constantName).append(" = I18nText.of(");
            if (entry.key.contains(".")) {
                sb.append("\"").append(entry.key).append("\"");
            } else {
                sb.append(className).append(".class, \"").append(entry.key).append("\"");
            }
            for (Placeholder p : entry.placeholders) {
                sb.append(", \"").append(p.name).append("\"");
            }
            sb.append(");\n\n");

            // Type-safe method
            sb.append("    public static String ").append(toMethodName(entry.key)).append("(");
            boolean first = true;
            for (Placeholder p : entry.placeholders) {
                if (!first) sb.append(", ");
                sb.append(p.javaType).append(" ").append(p.name);
                first = false;
            }
            sb.append(") {\n");
            sb.append("        return ").append(constantName).append(".i18n(");
            first = true;
            for (Placeholder p : entry.placeholders) {
                if (!first) sb.append(", ");
                sb.append(p.name);
                first = false;
            }
            sb.append(");\n");
            sb.append("    }\n\n");

            // Type-safe method with Locale
            sb.append("    public static String ").append(toMethodName(entry.key)).append("(Locale locale");
            for (Placeholder p : entry.placeholders) {
                sb.append(", ").append(p.javaType).append(" ").append(p.name);
            }
            sb.append(") {\n");
            sb.append("        return ").append(constantName).append(".i18n(locale");
            for (Placeholder p : entry.placeholders) {
                sb.append(", ").append(p.name);
            }
            sb.append(");\n");
            sb.append("    }\n\n");
        }
    }

    private void generateAndroidStyle(StringBuilder sb, List<I18nEntry> entries) {
        sb.append("    public static final class R {\n");
        sb.append("        public static final class string {\n");
        int id = 0x7f010001;
        for (I18nEntry entry : entries) {
            String constantName = entry.key.replace('.', '_').toLowerCase();
            sb.append("            public static final int ").append(constantName).append(" = ").append(String.format("0x%x", id++)).append(";\n");
        }
        sb.append("        }\n");
        sb.append("    }\n\n");

        sb.append("    private static final Map<Integer, I18nText> ID_MAP = new HashMap<>();\n\n");
        sb.append("    static {\n");
        for (I18nEntry entry : entries) {
            String constantName = entry.key.replace('.', '_').toLowerCase();
            sb.append("        ID_MAP.put(R.string.").append(constantName).append(", I18nText.of(\"").append(entry.key).append("\"");
            for (Placeholder p : entry.placeholders) {
                sb.append(", \"").append(p.name).append("\"");
            }
            sb.append("));\n");
        }
        sb.append("    }\n\n");

        sb.append("    public static String getString(int id, Object... args) {\n");
        sb.append("        I18nText text = ID_MAP.get(id);\n");
        sb.append("        if (text == null) throw new IllegalArgumentException(\"Unknown ID: \" + id);\n");
        sb.append("        return text.i18n(args);\n");
        sb.append("    }\n\n");

        sb.append("    public static String getString(Locale locale, int id, Object... args) {\n");
        sb.append("        I18nText text = ID_MAP.get(id);\n");
        sb.append("        if (text == null) throw new IllegalArgumentException(\"Unknown ID: \" + id);\n");
        sb.append("        return text.i18n(locale, args);\n");
        sb.append("    }\n\n");

        // Also add type-safe variants that use IDs internally?
        // The user said "R-IDs variant", so they probably want to use the IDs.
    }

    private String toMethodName(String key) {
        String[] parts = key.split("\\.");
        String lastPart = parts[parts.length - 1];
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : lastPart.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    sb.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static class I18nEntry {
        final String key;
        final List<Placeholder> placeholders;

        I18nEntry(String key, List<Placeholder> placeholders) {
            this.key = key;
            this.placeholders = placeholders;
        }
    }

    private static class Placeholder {
        final String name;
        final String javaType;

        Placeholder(String name, String typeStr) {
            this.name = name;
            this.javaType = mapType(typeStr);
        }

        private String mapType(String typeStr) {
            return switch (typeStr) {
                case "String" -> "String";
                case "Number" -> "Number";
                case "Integer" -> "Integer";
                case "Long" -> "Long";
                case "Double" -> "Double";
                case "Boolean" -> "Boolean";
                case "Date", "java.util.Date" -> "java.util.Date";
                case "List" -> "java.util.List<?>";
                default -> typeStr;
            };
        }
    }
}
