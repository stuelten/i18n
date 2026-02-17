package de.sty.i18n.scanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class I18nScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void testScan() throws IOException {
        Path srcMain = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcMain);
        
        Path javaFile = srcMain.resolve("MyClass.java");
        Files.writeString(javaFile, """
            package com.example;
            import de.sty.i18n.I18nText;
            public class MyClass {
                private static final I18nText GREETING = I18nText.ofField("name");
                private static final I18nText WELCOME = I18nText.of("welcome_msg", "user");
    
                public void test() {
                    // I18nText.of("commented", "out");
                    String s = "I18nText.of(\\"string_literal\\", \\"param\\")";
                }
            }
            """);

        Path resources = tempDir.resolve("src/main/resources/com/example");
        Files.createDirectories(resources);
        Files.writeString(resources.resolve("i18n_en.properties"), """
            MyClass.greeting=Hello {name}!
            MyClass.welcome_msg=Welcome {user}!
            """);
        Files.writeString(resources.resolve("i18n_de.properties"), """
            MyClass.greeting=Hallo {name}!
            MyClass.welcome_msg=Willkommen {user}!
            """);

        I18nScanner scanner = new I18nScanner();
        Map<String, List<I18nScanner.I18nUsage>> results = scanner.scan(tempDir);

        assertThat(results).containsKey("com.example.MyClass");
        List<I18nScanner.I18nUsage> usages = results.get("com.example.MyClass");
        
        // GREETING and WELCOME
        assertThat(usages).hasSize(2);
        
        for (I18nScanner.I18nUsage usage : usages) {
            assertThat(usage.validate(java.util.List.of(java.util.Locale.ENGLISH, java.util.Locale.GERMAN))).isEmpty();
        }
    }
}
