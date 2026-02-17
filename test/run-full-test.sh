#!/bin/bash
# test/run-full-test.sh - Build project and run all tests

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../bin" && pwd)"
TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions
# shellcheck source=../bin/common.sh
source "$SCRIPT_DIR/common.sh"

# Parse arguments
while [[ $# -gt 0 ]]; do
  case $1 in
  -h | --help)
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  -q, --quiet      Minimize output"
    echo "  -v, --verbose    Detailed output"
    exit 0
    ;;
  -q | --quiet)
    QUIET=true
    shift
    ;;
  -v | --verbose)
    VERBOSE=true
    shift
    ;;
  *)
    echo "Unknown option: $1"
    exit 1
    ;;
  esac
done

export QUIET
export VERBOSE

# 1. Build project and run unit tests
log "Building project and running unit tests..."
"$SCRIPT_DIR/build.sh" clean java

# 2. Run all test scripts (including CLI tests)
log "Running integration and script tests..."
"$TEST_DIR"/run-test-scripts.sh

log "=== Full Test Completed Successfully ==="
