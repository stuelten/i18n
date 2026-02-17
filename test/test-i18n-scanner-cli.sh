#!/bin/bash
# test/test-i18n-scanner-cli.sh - CLI test for i18n-scanner

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../bin" && pwd)"
TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions
# shellcheck source=../bin/common.sh
source "$SCRIPT_DIR/common.sh"

test_i18n_scanner_cli() {
    log "Testing i18n-scanner CLI..."

    # 1. Build the scanner (ensure we have the executable jar)
    "$SCRIPT_DIR/build.sh" java --skipTests --quiet

    # Find the scanner jar
    SCANNER_JAR=$(find_jar "i18n-parent/i18n-scanner" "i18n-scanner")
    log "Found scanner jar: $SCANNER_JAR"

    # 2. Create a temporary test project
    local tmp_project
    tmp_project=$(mktemp -d)
    log "Created temporary project at $tmp_project"

    # Create a Java file with i18n usages
    mkdir -p "$tmp_project/src/main/java/com/test"
    cat <<EOF > "$tmp_project/src/main/java/com/test/TestClass.java"
package com.test;
import de.sty.i18n.I18nText;
public class TestClass {
    private static final I18nText T1 = I18nText.ofField("t1");
    private static final I18nText T2 = I18nText.of("t2");
    
    public void method() {
        I18nText.of("inline_key").i18n();
    }
}
EOF

    # Create properties files
    mkdir -p "$tmp_project/src/main/resources/com/test"
    cat <<EOF > "$tmp_project/src/main/resources/com/test/i18n_en.properties"
TestClass.t1=Key 1 EN
TestClass.t2=Key 2 EN
TestClass.inline_key=Inline Key EN
EOF
    cat <<EOF > "$tmp_project/src/main/resources/com/test/i18n_de.properties"
TestClass.t1=Key 1 DE
TestClass.t2=Key 2 DE
TestClass.inline_key=Inline Key DE
EOF

    # 3. Run the scanner - should pass
    log "Running scanner on valid project..."
    java -jar "$SCANNER_JAR" "$tmp_project" --locales en,de || error "Scanner failed on valid project"

    # 4. Introduce an error (missing key in DE)
    log "Introducing i18n error..."
    cat <<EOF > "$tmp_project/src/main/resources/com/test/i18n_de.properties"
TestClass.t1=Key 1 DE
# TestClass.t2=Key 2 DE
TestClass.inline_key=Inline Key DE
EOF

    # 5. Run the scanner - should fail
    log "Running scanner on invalid project (should fail)..."
    if java -jar "$SCANNER_JAR" "$tmp_project" --locales en,de 2>/dev/null; then
        error "Scanner should have failed but exited with 0"
    else
        log "Scanner failed as expected"
    fi

    # Cleanup
    rm -rf "$tmp_project"
    log "CLI test passed"
}

test_i18n_scanner_cli
