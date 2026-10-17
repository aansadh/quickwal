
#!/usr/bin/env bash

set -Eeuo pipefail

# Export a repository into a single text file for AI-assisted development.
#
# Usage:
#   ./export_codebase.sh [project_directory] [output_file]
#
# Examples:
#   ./export_codebase.sh . ./currSnapshot.txt
#   ./export_codebase.sh . ~/projects/QuickWal/currSnapshot.txt
#   ./export_codebase.sh . "$HOME/QuickWal_context.txt"
#
# Optional:
#   MAX_FILE_BYTES=0          No per-file size limit (default).
#   MAX_FILE_BYTES=5242880    Limit individual files to 5 MiB.

PROJECT_ARG="${1:-.}"
OUTPUT_ARG="${2:-$HOME/codebase_context.txt}"
MAX_FILE_BYTES="${MAX_FILE_BYTES:-0}"

die() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

# ------------------------------------------------------------
# 1. Validate inputs and resolve paths
# ------------------------------------------------------------

command -v realpath >/dev/null 2>&1 ||
    die "realpath is required."

command -v find >/dev/null 2>&1 ||
    die "find is required."

command -v file >/dev/null 2>&1 ||
    die "'file' is required."

command -v git >/dev/null 2>&1 ||
    die "git is required."

[[ -d "$PROJECT_ARG" ]] ||
    die "Project directory does not exist: $PROJECT_ARG"

[[ "$MAX_FILE_BYTES" =~ ^[0-9]+$ ]] ||
    die "MAX_FILE_BYTES must be 0 or a positive integer."

PROJECT_DIR="$(realpath "$PROJECT_ARG")"

# Expand ~/ explicitly because tilde expansion does not happen
# inside a quoted shell variable.
if [[ "$OUTPUT_ARG" == "~/"* ]]; then
    OUTPUT_ARG="$HOME/${OUTPUT_ARG#~/}"
fi

# Convert relative output paths to absolute paths.
if [[ "$OUTPUT_ARG" != /* ]]; then
    OUTPUT_ARG="$PWD/$OUTPUT_ARG"
fi

OUTPUT_FILE="$(realpath -m -- "$OUTPUT_ARG")"
OUTPUT_PARENT="$(dirname "$OUTPUT_FILE")"

[[ -d "$OUTPUT_PARENT" ]] ||
    die "Output directory does not exist: $OUTPUT_PARENT"

[[ ! -d "$OUTPUT_FILE" ]] ||
    die "Output path is a directory: $OUTPUT_FILE"

[[ "$OUTPUT_FILE" != "$PROJECT_DIR" ]] ||
    die "Output path cannot be the project directory."

# Temporary output is created in the destination directory so that
# the completed export can replace the destination atomically.
TMP_LIST="$(mktemp)"
TMP_OUTPUT="$(mktemp "$OUTPUT_PARENT/.codebase-export.XXXXXX")"

cleanup() {
    rm -f -- "$TMP_LIST" "$TMP_OUTPUT"
}

trap cleanup EXIT

# ------------------------------------------------------------
# 2. Directory exclusion rules
# ------------------------------------------------------------

# These directories are excluded wherever they occur.
#
# IMPORTANT:
# Do NOT add "out" here. Java source packages such as
# com/example/port/out contain legitimate source files.

PRUNE_ANYWHERE=(
    .git
    node_modules
    .gradle
    .idea
    .vscode
    vendor
    __pycache__
    .pytest_cache
    .mypy_cache
    .ruff_cache
    .venv
    venv
    coverage
    .next
    .nuxt
    .terraform
    .serverless
)

# Common generated directories excluded only at project root.
PRUNE_ROOT_ONLY=(
    target
    build
    dist
)

# ------------------------------------------------------------
# 3. Sensitive-file filtering
# ------------------------------------------------------------

is_sensitive_name() {
    local name="${1##*/}"
    name="${name,,}"

    case "$name" in
        .env|.env.*)
            case "$name" in
                .env.example|.env.sample|.env.template|.env.dist)
                    return 1
                    ;;
                *)
                    return 0
                    ;;
            esac
            ;;

        id_rsa|id_ed25519|id_ecdsa|id_dsa)
            return 0
            ;;

        *.pem|*.key|*.p12|*.pfx|*.jks|*.keystore)
            return 0
            ;;

        *credentials*|*secret*|*private_key*|*private-key*)
            return 0
            ;;

        *service-account*.json|*service_account*.json)
            return 0
            ;;
    esac

    return 1
}

# Exclude the final output and temporary output from the scan.
# Paths are compared after resolving them to absolute paths.
is_export_file() {
    local candidate="$1"

    [[ "$candidate" == "$OUTPUT_FILE" ||
       "$candidate" == "$TMP_OUTPUT" ]]
}

# ------------------------------------------------------------
# 4. Recursively discover files
# ------------------------------------------------------------

echo "Scanning project: $PROJECT_DIR"

FIND_ARGS=("$PROJECT_DIR" -type d '(')
FIRST=true

for dir in "${PRUNE_ANYWHERE[@]}"; do
    if [[ "$FIRST" != true ]]; then
        FIND_ARGS+=(-o)
    fi

    FIRST=false
    FIND_ARGS+=(-name "$dir")
done

# Restrict target/build/dist exclusions to the project root.
for dir in "${PRUNE_ROOT_ONLY[@]}"; do
    FIND_ARGS+=(-o -path "$PROJECT_DIR/$dir")
done

FIND_ARGS+=(')' -prune -o -type f -print0)

# Do not filter by file extension. This includes extensionless
# configuration files and unfamiliar source formats.
#
# NUL delimiters safely handle spaces and special characters in paths.
# The output snapshot is excluded before it enters the manifest.

if ! find "${FIND_ARGS[@]}" > /dev/null 2>&1; then
    die "Unable to scan project directory."
fi

find "${FIND_ARGS[@]}" |
    while IFS= read -r -d '' file_path; do
        canonical_path="$(realpath -m -- "$file_path")"

        if is_export_file "$canonical_path"; then
            continue
        fi

        if is_sensitive_name "$canonical_path"; then
            continue
        fi

        printf '%s\0' "$canonical_path"
    done |
    sort -zu > "$TMP_LIST"

FILE_COUNT="$(tr -cd '\0' < "$TMP_LIST" | wc -c | tr -d ' ')"

echo "Candidate files: $FILE_COUNT"
echo "Output: $OUTPUT_FILE"

# ------------------------------------------------------------
# 5. Generate the context pack
# ------------------------------------------------------------

{
    echo "# CODEBASE CONTEXT PACK"
    echo
    echo "Project root: $PROJECT_DIR"
    echo "Generated: $(date -Is)"
    echo "Candidate files: $FILE_COUNT"

    if (( MAX_FILE_BYTES == 0 )); then
        echo "Per-file size limit: none"
    else
        echo "Per-file size limit: $MAX_FILE_BYTES bytes"
    fi

    echo "Output file is excluded from the scan."
    echo "Sensitive-looking filenames are excluded."
    echo

    # --------------------------------------------------------
    echo "============================================================"
    echo "# 1. REPOSITORY OVERVIEW"
    echo "============================================================"
    echo

    echo "Root-level files and directories:"

    find "$PROJECT_DIR" \
        -mindepth 1 -maxdepth 1 \
        ! -name '.git' \
        ! -name 'target' \
        ! -name 'build' \
        ! -name 'dist' \
        -printf '%f\n' 2>/dev/null |
        sort

    echo

    # --------------------------------------------------------
    echo "============================================================"
    echo "# 2. GIT CONTEXT"
    echo "============================================================"
    echo

    if git -C "$PROJECT_DIR" rev-parse --is-inside-work-tree \
        >/dev/null 2>&1; then

        echo "## Current branch"
        git -C "$PROJECT_DIR" branch --show-current 2>&1 || true
        echo

        echo "## Working-tree status"
        git -C "$PROJECT_DIR" status --short --branch 2>&1 || true
        echo

        echo "## Recent commits"
        git -C "$PROJECT_DIR" log -5 \
            --date=short \
            --pretty=format:'%h %ad %s' 2>&1 || true
        echo
        echo

        echo "## Unstaged diff"
        git -C "$PROJECT_DIR" \
            diff --no-ext-diff -- . 2>&1 || true
        echo

        echo "## Staged diff"
        git -C "$PROJECT_DIR" \
            diff --cached --no-ext-diff -- . 2>&1 || true
        echo

        echo "Note: Git diffs can contain secrets."
        echo "Review the export before sharing it."
        echo

    else
        echo "No Git repository detected."
        echo
    fi

    # --------------------------------------------------------
    echo "============================================================"
    echo "# 3. FILE MANIFEST"
    echo "============================================================"
    echo

    while IFS= read -r -d '' file_path; do
        printf '%s\n' "${file_path#"$PROJECT_DIR"/}"
    done < "$TMP_LIST"

    # --------------------------------------------------------
    echo
    echo "============================================================"
    echo "# 4. FILE CONTENTS"
    echo "============================================================"

    while IFS= read -r -d '' file_path; do
        relative_path="${file_path#"$PROJECT_DIR"/}"

        echo
        echo "================================================================"
        printf 'FILE: %s\n' "$relative_path"
        echo "================================================================"

        if [[ ! -r "$file_path" ]]; then
            echo "[OMITTED: File is not readable]"
            continue
        fi

        file_size="$(wc -c < "$file_path" | tr -d ' ')"

        if (( MAX_FILE_BYTES > 0 &&
              file_size > MAX_FILE_BYTES )); then
            printf '[OMITTED: File is %s bytes; limit is %s bytes]\n' \
                "$file_size" "$MAX_FILE_BYTES"
            continue
        fi

        mime_type="$(
            file -b --mime-type -- "$file_path" 2>/dev/null || true
        )"

        case "$mime_type" in
            text/*|application/json|application/xml|\
            application/javascript|application/x-sh|\
            application/sql|application/toml|\
            application/yaml|application/x-yaml|\
            application/x-empty|application/x-httpd-php|\
            application/x-ruby|application/x-perl)

                cat -- "$file_path"
                echo
                ;;

            *)
                printf \
                    '[OMITTED: Binary or unsupported MIME type: %s; size: %s bytes]\n' \
                    "${mime_type:-unknown}" "$file_size"
                ;;
        esac

    done < "$TMP_LIST"

    # --------------------------------------------------------
    echo
    echo "============================================================"
    echo "# 5. EXPORT SUMMARY"
    echo "============================================================"
    echo
    echo "Candidate files: $FILE_COUNT"
    echo "Binary and oversized files are marked in the contents."
    echo "Sensitive-looking filenames are excluded from the manifest."
    echo "Review this export for secrets before sharing."
    echo

} > "$TMP_OUTPUT"

# Replace the destination only after the export completes.
mv -f -- "$TMP_OUTPUT" "$OUTPUT_FILE"

echo
echo "Export complete: $OUTPUT_FILE"
echo "Candidate files: $FILE_COUNT"
echo "Output size: $(du -h "$OUTPUT_FILE" | cut -f1)"
