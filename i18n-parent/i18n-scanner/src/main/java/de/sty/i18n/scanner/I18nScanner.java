package de.sty.i18n.scanner;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scanner to check for I18nTexts without templates in properties files.
 */
@Command(name = "i18n-scanner", mixinStandardHelpOptions = true, version = "0.7.0",
        description = "Scans Java source files for I18nText usages and validates them against properties files.")
public class I18nScanner implements Callable<Integer> {

    @Parameters(index = "0", description = "The root directory to scan.", defaultValue = ".")
    private Path root;

    @Option(names = {"-l", "--locales"}, description = "Comma-separated list of locales to validate (e.g. en,de).", defaultValue = "en,de")
    private String localesStr;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new I18nScanner()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws IOException {
        System.out.println("Scanning for i18n texts in: " + root.toAbsolutePath());

        Map<String, List<I18nUsage>> usages = scan(root);

        List<Locale> locales = Arrays.stream(localesStr.split(","))
                .map(Locale::forLanguageTag)
                .collect(Collectors.toList());

        int totalErrors = 0;
        for (List<I18nUsage> classUsages : usages.values()) {
            for (I18nUsage usage : classUsages) {
                Map<Locale, String> errors = usage.validate(locales);
                if (!errors.isEmpty()) {
                    totalErrors++;
                    System.err.println("Error in " + usage.sourceFile + ":" + usage.lineNumber);
                    System.err.println("  Key: " + usage.key);
                    errors.forEach((locale, error) -> System.err.println("  [" + locale + "] " + error));
                }
            }
        }

        if (totalErrors == 0) {
            System.out.println("No missing or broken i18n templates found for locales: " + localesStr);
            return 0;
        } else {
            System.err.println("Found " + totalErrors + " i18n errors.");
            return 1;
        }
    }

    public Map<String, List<I18nUsage>> scan(Path root) throws IOException {
        Map<String, List<I18nUsage>> allUsages = new HashMap<>();

        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> javaFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            for (Path file : javaFiles) {
                List<I18nUsage> fileUsages = scanFile(file);
                if (!fileUsages.isEmpty()) {
                    String className = getFullClassName(file, fileUsages.get(0).packageName);
                    allUsages.computeIfAbsent(className, k -> new ArrayList<>()).addAll(fileUsages);
                }
            }
        }

        return allUsages;
    }

    private List<I18nUsage> scanFile(Path file) throws IOException {
        List<I18nUsage> usages = new ArrayList<>();
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(file);
        } catch (Exception e) {
            System.err.println("Could not parse " + file + ": " + e.getMessage());
            return usages;
        }

        String packageName = cu.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse("");
        
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(clazz -> {
            String simpleClassName = clazz.getNameAsString();
            
            // 1. Find fields declared with ofField
            clazz.findAll(FieldDeclaration.class).forEach(field -> {
                field.getVariables().forEach(var -> {
                    var.getInitializer().ifPresent(init -> {
                        if (init.isMethodCallExpr()) {
                            MethodCallExpr call = init.asMethodCallExpr();
                            if (isI18nFactoryCall(call, "ofField")) {
                                String key = simpleClassName + "." + var.getNameAsString().toLowerCase(Locale.ROOT);
                                int line = var.getBegin().map(p -> p.line).orElse(-1);
                                usages.add(new I18nUsage(file, line, key, simpleClassName, packageName));
                            } else if (isI18nFactoryCall(call, "of")) {
                                // 2. Find fields declared with explicit i18n key
                                if (call.getArguments().isNonEmpty() && call.getArgument(0).isStringLiteralExpr()) {
                                    String shortKey = call.getArgument(0).asStringLiteralExpr().getValue();
                                    String key = simpleClassName + "." + shortKey;
                                    int line = var.getBegin().map(p -> p.line).orElse(-1);
                                    usages.add(new I18nUsage(file, line, key, simpleClassName, packageName));
                                }
                            }
                        }
                    });
                });
            });

            // 3. Find inline explicit of calls (not only in field initializers)
            clazz.findAll(MethodCallExpr.class).forEach(call -> {
                if (isI18nFactoryCall(call, "of")) {
                    // Check if it's the variant with a string key as first argument
                    // I18nText.of("key", ...)
                    if (call.getArguments().isNonEmpty() && call.getArgument(0).isStringLiteralExpr()) {
                         String shortKey = call.getArgument(0).asStringLiteralExpr().getValue();
                         String key = simpleClassName + "." + shortKey;
                         int line = call.getBegin().map(p -> p.line).orElse(-1);
                         
                         // Avoid duplicates if already caught as field initializer
                         boolean duplicate = usages.stream().anyMatch(u -> u.lineNumber == line && u.key.equals(key));
                         if (!duplicate) {
                             usages.add(new I18nUsage(file, line, key, simpleClassName, packageName));
                         }
                    }
                }
            });
        });

        return usages;
    }

    private boolean isI18nFactoryCall(MethodCallExpr call, String methodName) {
        if (!call.getNameAsString().equals(methodName)) return false;
        
        return call.getScope().map(scope -> scope.toString().equals("I18nText")).orElse(false)
                || call.getScope().isEmpty(); // Simple name call if static import used
    }

    private String getFullClassName(Path file, String packageName) {
        String name = file.getFileName().toString().replace(".java", "");
        return packageName.isEmpty() ? name : packageName + "." + name;
    }

    public static class I18nUsage {
        public final Path sourceFile;
        public final int lineNumber;
        public final String key;
        public final String simpleClassName;
        public final String packageName;

        public I18nUsage(Path sourceFile, int lineNumber, String key, String simpleClassName, String packageName) {
            this.sourceFile = sourceFile;
            this.lineNumber = lineNumber;
            this.key = key;
            this.simpleClassName = simpleClassName;
            this.packageName = packageName;
        }

        public Map<Locale, String> validate(List<Locale> locales) {
            Map<Locale, String> errors = new HashMap<>();
            for (Locale locale : locales) {
                try {
                    String baseName = packageName.isEmpty() ? "i18n" : packageName + ".i18n";
                    // Using a dummy I18nText instance to trigger its validation logic if possible, 
                    // or just manually check properties.
                    // Since I18nText's validate() requires an instance and we only have the key,
                    // we re-implement the resource bundle lookup logic here.
                    
                    ResourceBundle bundle = loadBundle(baseName, locale);
                    if (!bundle.containsKey(key)) {
                        errors.put(locale, "Missing key: " + key);
                    }
                } catch (MissingResourceException e) {
                    errors.put(locale, "Missing resource bundle: " + e.getMessage());
                }
            }
            return errors;
        }

        private ResourceBundle loadBundle(String baseName, Locale locale) {
            // We need to look in src/main/resources or src/test/resources
            // This is a bit tricky from the scanner. We try to deduce resource path from source path.
            Path resourcePath = deduceResourcePath(sourceFile);
            ClassLoader loader = new ResourceClassLoader(resourcePath);
            try {
                return ResourceBundle.getBundle(baseName, locale, loader);
            } catch (MissingResourceException e) {
                 // Fallback to "i18n" in the same package
                 return ResourceBundle.getBundle("i18n", locale, loader);
            }
        }

        private Path deduceResourcePath(Path sourceFile) {
            String s = sourceFile.toString();
            if (s.contains("/src/main/java/")) {
                return Paths.get(s.substring(0, s.indexOf("/src/main/java/")), "src", "main", "resources");
            }
            if (s.contains("/src/test/java/")) {
                return Paths.get(s.substring(0, s.indexOf("/src/test/java/")), "src", "test", "resources");
            }
            return sourceFile.getParent();
        }
    }

    private static class ResourceClassLoader extends ClassLoader {
        private final Path resourceRoot;

        ResourceClassLoader(Path resourceRoot) {
            this.resourceRoot = resourceRoot;
        }

        @Override
        public java.net.URL getResource(String name) {
            Path file = resourceRoot.resolve(name);
            if (Files.exists(file)) {
                try {
                    return file.toUri().toURL();
                } catch (Exception e) {
                    return null;
                }
            }
            return super.getResource(name);
        }
    }
}
