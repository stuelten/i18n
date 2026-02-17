#!/bin/bash
# bin/build.sh - Build and Test Script for i18n

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

show_help() {
  echo "Usage: $0 [OPTIONS] [TARGETS...]"
  echo ""
  echo "Targets:"
  echo "  clean          Clean build artifacts"
  echo "  java           Build standard Java artifacts"
  echo ""
  echo "Options:"
  echo "  --help         Show this help message"
  echo "  --skipTests    Skip running tests during build"
  echo "  -q, --quiet    Minimize output"
  echo "  -v, --verbose  Show detailed output"
  echo ""
  echo "Example:"
  echo "  $0 clean java"
}

# Handle options and targets
CLEAN=false
BUILD_JAVA=false
SKIP_TESTS=false
TARGETS_PROVIDED=false

# Collect non-option arguments as targets
while [[ $# -gt 0 ]]; do
  case $1 in
  --help)
    show_help
    exit 0
    ;;
  --skipTests)
    SKIP_TESTS=true
    shift
    ;;
  -q|--quiet)
    QUIET=true
    shift
    ;;
  -v|--verbose)
    VERBOSE=true
    shift
    ;;
  clean)
    CLEAN=true
    TARGETS_PROVIDED=true
    shift
    ;;
  java)
    BUILD_JAVA=true
    TARGETS_PROVIDED=true
    shift
    ;;
  *)
    show_help
    error "Unknown parameter or target: $1"
    ;;
  esac
done

# Default behavior if no targets provided: build java
if [[ "$TARGETS_PROVIDED" = "false" ]]; then
  BUILD_JAVA=true
fi

export QUIET
export VERBOSE

log "=== Building Maven Modules ==="

MVN_GOALS="install"
if [[ "$CLEAN" = "true" ]]; then
  MVN_GOALS="clean $MVN_GOALS"
fi

MVN_OPTS=""
if [ "$QUIET" = true ]; then
  MVN_OPTS="$MVN_OPTS --batch-mode --quiet"
fi
if [ "$VERBOSE" = true ]; then
  MVN_OPTS="$MVN_OPTS --debug"
fi

# shellcheck disable=SC2086
"$SCRIPT_DIR/../mvnw" -f "$SCRIPT_DIR/../i18n-parent/pom.xml" $MVN_OPTS $MVN_GOALS -DskipTests=$SKIP_TESTS $MAVEN_ARGS || error "Error building maven"

log ""
log "=== Build Completed Successfully ==="
