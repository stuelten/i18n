## I18n Project

This project provides a robust, type-safe, and developer-friendly internationalization (i18n) system for Java applications. It is divided into two modules:

- **`fileserv-i18n`**: The core library for handling i18n texts with named placeholders and ICU4J support.
- **`fileserv-i18n-scanner`**: A source code scanner that uses syntactical analysis (JavaParser) to validate i18n usages.
- **`fileserv-i18n-example`**: Example project demonstrating the use of the library.

### 0. Build Instructions

To build the entire i18n project and install it to your local Maven repository:

```bash
mvn clean install -pl fileserv-i18n-parent -am
```

This will build both the core library and the scanner tool.

---

#### 1. Core Library (`fileserv-i18n`)

The core library features zero-boilerplate declarations and advanced formatting.

##### 1.1. Declaration & Usage

Declare i18n texts as `static final` fields. Use `ofField` for automatic key deduction.

```java
public class MyService {
    // Key: "MyService.greeting", Parameter: "name"
    private static final I18nText GREETING = I18nText.ofField("name");

    public String sayHello(String name) {
        return GREETING.i18n(name);
    }
}
```

##### 1.2. Type Safety

Use typed interfaces for compile-time checking:

```java
private static final I18nText1<String> GREETING = I18nText.ofField(String.class, "name");
// Only Strings are allowed
String text = GREETING.i18n("Paul");
```

##### 1.3. Advanced Formatting (ICU4J)

Supports plurals, currencies, and dates directly in `.properties` files:

```properties
MyService.items_found={count, plural, one{# item} other{# items}}
MyService.price={amount, number, currency}
```

#### 2. Source Code Scanner (`fileserv-i18n-scanner`)

Ensure all `I18nText` instances have corresponding entries in property files.

##### 2.1. Running the Scanner

Run from the command line:

```bash
mvn exec:java -pl fileserv-i18n-parent/fileserv-i18n-scanner -Dexec.mainClass="scanner.de.sty.i18n.I18nScanner" -Dexec.args="."
```

##### 2.2. Automated Validation in Tests

Integrate into unit tests:

```java
@Test
void scanAllI18nTexts() throws IOException {
    I18nScanner scanner = new I18nScanner();
    Map<String, List<I18nUsage>> usages = scanner.scan(Paths.get("src/main/java"));
    // ... validate usages
}
```

#### 3. Web Environments

Configure a custom `LocaleProvider` to resolve locales per request (e.g., from HTTP headers):

```
I18nText.setLocaleProvider(() -> UserContext.getCurrentLocale());
```

---

## I18nText How-To Guide

The `fileserv-i18n` module provides a powerful, type-safe, and developer-friendly way
to handle internationalized texts in Java applications.
It uses ICU4J for advanced formatting (plurals, genders, currencies, etc.)
and features automatic key deduction to reduce boilerplate.

#### Key Features

- **Zero Boilerplate**: Automatic class detection and key deduction from field names.
- **Type Safety**: Optional typed wrappers for compile-time parameter checking.
- **Performance**: Cached `MessageFormat` instances and pre-processed templates.
- **Robustness**: Graceful error handling instead of crashes at runtime.
- **ICU4J Power**: Full support for complex i18n patterns.
- **Template Protection**: `toString()` is overridden to prevent leaking templates into logs by accident.

---

#### 1. Basic Usage

##### 1.1. Declaration

Declare your i18n texts as `static final` fields.
You can use `ofField` to automatically use the field name as the key.

```java
public class MyService {
    // Automatically uses the key "MyService.greeting" and the parameter "name"
    private static final I18nText GREETING = I18nText.ofField("name");

    // Explicit key "welcome_msg" (Full key: "MyService.welcome_msg")
    private static final I18nText WELCOME = I18nText.of("welcome_msg", "user");
}
```

##### 1.2. Properties File

Create a properties file in the same package as your class.
The file should be named `i18n.properties`
(or `i18n_<LANG>.properties`, e.g. `ì18n_EN.properties`).

**File:** `src/main/resources/com/example/i18n.properties`

```properties
MyService.greeting=Hello {name}!
MyService.welcome_msg=Welcome to internationalization, {user}!
```

##### 1.3. Usage in Code

```java
String text = GREETING.i18n("World"); // Returns "Hello World!"
```

---

#### 2. Type Safety

To avoid runtime errors, use the typed interfaces `I18nText1`, `I18nText2`, or `I18nText3`.

```java
// Declaration with type info
private static final I18nText1<String> GREETING = I18nText.ofField(String.class, "name");

// Usage - only Strings are allowed at compile time
String text = GREETING.i18n("Paul"); 
```

##### 2.1. Type-Safe Currencies and Dates

You can combine type safety with ICU formatting for robust templates.

```java
// Declaration
private static final I18nText2<Double, Date> PRICE = I18nText.ofField(Double.class, "amount", Date.class, "date");

// Property
// MyService.price_tag=The price is {amount, number, currency} on {date, date, long}.

// Usage
String text = PRICE.i18n(1234.56, new Date());
```

---

#### 3. Advanced Formatting (ICU4J)

Since the module uses ICU4J,
you can use complex formatting directly in your properties files.

##### 3.1. Plurals

**Property:**

```properties
MyService.items_found={count, plural, one{# item found} other{# items found}}
```

**Code:**

```java
class MyService {
    private static final I18nText ITEMS = I18nText.ofField("count");
    void serve() {
        System.out.println(ITEMS.i18n(1)); // "1 item found"
        System.out.println(ITEMS.i18n(5)); // "5 items found"
    }
}
```

##### 3.2. Currencies and Dates

**Property:**

```properties
MyService.price_tag=The price is {amount, number, currency} on {date, date, long}.
```

**Code:**

```java
class MyService {
    private static final I18nText PRICE = I18nText.ofField("amount", "date");

    void serve() {
        System.out.println(PRICE.i18n(1234.56, new Date()));
    }
}
```

---

#### 4. Web Environments and Locale Providers

In web applications, you typically want to resolve the locale based on the current HTTP request.

```java
class MyStartup {
    void onStartUp() {
        // Initialize once (e.g., in a startup listener)
        I18nText.setLocaleProvider(() -> {
                // Logic to get the locale from the current request/session context
                return UserContext.getCurrentLocale(); 
        });
        
        // Now all .i18n() calls without an explicit Locale parameter will use this provider
        String text = GREETING.i18n("Paul");
    }
}
```

---

#### 5. Unit Testing and Validation

You can validate your templates against various locales
to find broken patterns or missing keys early in your CI/CD pipeline.

##### 5.1. Validating a single I18nText

Use the `validate()` method in your unit tests:

```java
@Test
void validateGreeting() {
    Map<Locale, String> errors = GREETING.validate(Locale.ENGLISH, Locale.GERMAN);
    assertThat(errors).isEmpty();
}
```

---

### 6. Source Code Scanning

To ensure that all `I18nText` instances in your code have corresponding entries in the property files,
you can use the `I18nScanner` tool.
This tool is located in the `fileserv-i18n-scanner` module.

It uses syntactical analysis (JavaParser) to find all `I18nText` declarations and `i18n(...)` calls,
making it much more robust than simple regex scanners.

#### 6.1. Running the Scanner

The scanner can be run via Maven or as a standalone executable.

**Via Maven:**

```bash
mvn exec:java -pl fileserv-i18n-parent/fileserv-i18n-scanner -Dexec.args=". --locales en,de"
```

**As a Standalone Executable:**

After building the project, you can find the executable in the `target` directory:

```bash
./fileserv-i18n-parent/fileserv-i18n-scanner/target/i18n-scanner . --locales en,de
```

**Usage Help:**

```bash
./fileserv-i18n-parent/fileserv-i18n-scanner/target/i18n-scanner --help
```

*Note: If the executable bit is not set, run `chmod +x target/i18n-scanner`.*

#### 6.2. Automated Project-Wide Unit Test

You can integrate the scanner into your unit tests:

```java
@Test
void scanAllI18nTexts() throws IOException {
    I18nScanner scanner = new I18nScanner();
    // Scan the current project's source directory
    Map<String, List<I18nScanner.I18nUsage>> usages = scanner.scan(Paths.get("src/main/java"));

    List<String> allErrors = new ArrayList<>();
    for (List<I18nScanner.I18nUsage> classUsages : usages.values()) {
        for (I18nScanner.I18nUsage usage : classUsages) {
            // Validate against English and German
            Map<Locale, String> errors = usage.validate(List.of(Locale.ENGLISH, Locale.GERMAN));
            errors.forEach((locale, error) -> 
                allErrors.add(usage.sourceFile + ":" + usage.lineNumber + " [" + locale + "] " + error)
            );
        }
    }

    assertThat(allErrors).as("Found i18n errors").isEmpty();
}
```
