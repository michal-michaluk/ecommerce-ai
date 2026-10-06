#!/bin/bash
# Post-generate hook: nests dotted package directory into proper path
# macOS cargo-generate 0.23.x creates "com.example.test/" instead of "com/example/test/"
set -euo pipefail

MICRO_NAME="${1:-}"

nest_dotted_dir() {
    local base="$1"
    if [ ! -d "$base" ]; then
        return 0
    fi

    find "$base" -depth -type d -name '*.*' -maxdepth 1 -print0 | while IFS= read -r -d '' dotted; do
        local rel="${dotted#$base/}"
        local nested="$base/$(echo "$rel" | tr '.' '/')"

        if [ "$dotted" != "$nested" ] && [ ! -d "$nested" ]; then
            mkdir -p "$(dirname "$nested")"
            mv "$dotted"/* "$dotted"/.* "$nested/" 2>/dev/null || true
            rmdir "$dotted" 2>/dev/null || true
        fi
    done
}

# Primary: use micro_name to nest correctly
if [ -n "$MICRO_NAME" ]; then
    PACKAGE_PATH=$(echo "$MICRO_NAME" | tr '.' '/')
    for src_base in "src/main/java" "src/test/java"; do
        if [ -d "$src_base/$PACKAGE_PATH" ]; then
            continue 2
        fi
        # Try to find the dotted directory
        find "$src_base" -depth -type d -name "$(echo "$MICRO_NAME" | sed 's/\./\\./g')" -maxdepth 3 | while IFS= read -r -d $'\n' dotted; do
            if [ -d "$dotted" ]; then
                mkdir -p "$src_base/$PACKAGE_PATH"
                shopt -s dotglob
                mv "$dotted"/* "$src_base/$PACKAGE_PATH/" 2>/dev/null || true
                shopt -u dotglob
                rmdir "$dotted" 2>/dev/null || true
            fi
        done
    done
fi

# Fallback: find any dotted dirs and nest them
nest_dotted_dir "src/main/java"
nest_dotted_dir "src/test/java"
