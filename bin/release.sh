#!/bin/bash
# release.sh
#
# Performs the build and prepares the release.
# Can be run locally or in CI.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Source common functions
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

show_help() {
  echo "Usage: $0 [OPTIONS] [major|minor|VERSION]"
  echo ""
  echo "Performs the build and prepares the release."
  echo "Can be run locally or in CI."
  echo ""
  echo "Options:"
  echo "  -h, --help    Show this help message"
  echo "  --push        Force pushing to git (default in CI)"
  echo "  --no-push     Disable pushing to git (default locally)"
  echo ""
  echo "Arguments:"
  echo "  major|minor   Force a major or minor version increment."
  echo "  VERSION       The explicit version to release."
}

# Handle options
VERSION=""
SEMVER_ARG=""
PUSH_ARG="--no-push"
[ "$GITHUB_ACTIONS" == "true" ] && PUSH_ARG="--push"

while [[ $# -gt 0 ]]; do
  case $1 in
  -h | --help)
    show_help
    exit 0
    ;;
  --push)
    PUSH_ARG="--push"
    shift
    ;;
  --no-push)
    PUSH_ARG="--no-push"
    shift
    ;;
  major | minor)
    if [ -n "$VERSION" ] || [ -n "$SEMVER_ARG" ]; then
      show_help
      error "Too many arguments: $1"
    fi
    SEMVER_ARG=$1
    shift
    ;;
  -*)
    show_help
    error "Unknown parameter $1"
    ;;
  *)
    if [ -z "$VERSION" ] && [ -z "$SEMVER_ARG" ]; then
      VERSION=$1
      shift
    else
      show_help
      error "Too many arguments: $1"
    fi
    ;;
  esac
done

# Increase version automatically of not given as a parameter
if [ -z "$VERSION" ]; then
  log "Increasing version..."
  VERSION=$("$SCRIPT_DIR/version-increase-semver.sh" "$SEMVER_ARG")
fi

log "Building and releasing version $VERSION"

# 1. Create and checkout release branch
"$SCRIPT_DIR/release-branch-create.sh" $PUSH_ARG "$VERSION"

# 2. Build and Deploy project
log "Building and deploying project..."
"$SCRIPT_DIR/build.sh" --quiet clean java

# If PUSH is enabled, also deploy to the maven registry
if [ "$PUSH_ARG" == "--push" ]; then
    log "Deploying artifacts to Maven registry..."
    ./mvnw -f i18n-parent/pom.xml deploy -DskipTests=true --batch-mode --quiet || error "Error deploying to Maven registry"
fi

# 3. Verify the build output
log "Checking build artifacts..."
JAR_ARTIFACTS=(
  "i18n-parent/i18n/target/i18n.jar"
  "i18n-parent/i18n-scanner/target/i18n-scanner.jar"
)

# Note: We use find_jar from common.sh to get actual paths if needed, 
# but for release tagging we might want to be explicit or use find_jar.
# However, build.sh with maven-shade-plugin as configured in i18n-scanner 
# produces i18n-scanner.jar in target.

RELEASE_ARTIFACTS=()
for art in "${JAR_ARTIFACTS[@]}"; do
    # If the exact name doesn't exist, try to find it (it might have version in name)
    if [ ! -f "$art" ]; then
        # Try to find it using find_jar logic
        module=$(dirname "$(dirname "$art")")
        artifactId=$(basename "$art" .jar)
        found_jar=$(find_jar "$module" "$artifactId")
        RELEASE_ARTIFACTS+=("$found_jar")
    else
        RELEASE_ARTIFACTS+=("$art")
    fi
done

log "Build successful."

# 4. Tag and Release
"$SCRIPT_DIR/release-tag.sh" $PUSH_ARG "$VERSION" "${RELEASE_ARTIFACTS[@]}"

# 5. Bump to the next "X.Y.Z-SNAPSHOT" on main
NEXT_SNAPSHOT=$(echo "$VERSION" | awk -F. '{print $1"."($2+1)".0-SNAPSHOT"}')
"$SCRIPT_DIR/release-post.sh" $PUSH_ARG "$NEXT_SNAPSHOT"

log "Release $VERSION completed successfully."
