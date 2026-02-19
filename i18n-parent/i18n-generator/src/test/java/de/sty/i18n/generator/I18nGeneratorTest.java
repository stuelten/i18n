package de.sty.i18n.generator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class I18nGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    void testGenerator() throws Exception {
        Path propsFile = tempDir.resolve("messages.properties");
        Files.writeString(propsFile,
                """
                        helloWorldMsg=Hello {name:String}
                        priceTag=Price is {amount, number, currency} on {date, date}
                        simple=Just {text}
                        pluralMsg={count, plural, one{one item} other{# items}}
                        listMsg=Items: {items:List}
                        """
        );

        Path outputFile = tempDir.resolve("Messages.java");
        
        I18nGenerator generator = new I18nGenerator();
        CommandLineProxy.execute(generator, propsFile.toString(), "-o", outputFile.toString(), "-p", "com.example", "-c", "Messages");

        assertThat(outputFile).exists();
        String content = Files.readString(outputFile);
        
        assertThat(content).contains("package com.example;");
        assertThat(content).contains("public class Messages {");
        
        // helloWorldMsg
        assertThat(content).contains("public static final I18nText HELLOWORLDMSG = I18nText.of(\"helloWorldMsg\", \"name\");");
        assertThat(content).contains("public static String helloWorldMsg(String name) {");
        
        // priceTag
        assertThat(content).contains("public static final I18nText PRICETAG = I18nText.of(\"priceTag\", \"amount\", \"date\");");
        assertThat(content).contains("public static String priceTag(Number amount, java.util.Date date) {");
        
        // simple
        assertThat(content).contains("public static final I18nText SIMPLE = I18nText.of(\"simple\", \"text\");");
        assertThat(content).contains("public static String simple(String text) {");
        
        // pluralMsg
        assertThat(content).contains("public static final I18nText PLURALMSG = I18nText.of(\"pluralMsg\", \"count\");");
        assertThat(content).contains("public static String pluralMsg(String count) {");

        // listMsg
        assertThat(content).contains("public static final I18nText LISTMSG = I18nText.of(\"listMsg\", \"items\");");
        assertThat(content).contains("public static String listMsg(java.util.List<?> items) {");
    }

    private static class CommandLineProxy {
        static void execute(I18nGenerator generator, String... args) {
            // We use a proxy because I18nGenerator.main calls System.exit
            new picocli.CommandLine(generator).execute(args);
        }
    }
}
