#!/usr/bin/env bash
set -euo pipefail

# Ensure YTDL_HOME is respected for PATH if provided
if [[ -n "${YTDL_HOME:-}" ]]; then
  export PATH="$YTDL_HOME:$PATH"
fi

# If YTDL_BIN is set, try to use it directly
if [[ -n "${YTDL_BIN:-}" && -x "$YTDL_BIN" ]]; then
  YT_DLP_BIN="$YTDL_BIN"
else
  YT_DLP_BIN="$(command -v yt-dlp || true)"
fi

# Usage: ./run-yt-dlp.sh <YouTube URL> <output_filename>

if [[ $# -lt 2 ]]; then
  echo "Usage: $(basename "$0") <YouTube URL> <output_filename>" >&2
  echo "Example: $(basename "$0") https://www.youtube.com/watch?v=ABC123 ABC123.mp3" >&2
  exit 1
fi

url="$1"
desired="$2"

echo "Running yt-dlp with URL: $url"

if [[ -z "$YT_DLP_BIN" ]]; then
  echo "Error: yt-dlp not found in PATH. Set YTDL_HOME to the folder containing yt-dlp or install it (e.g., brew install yt-dlp)." >&2
  echo "Effective PATH: $PATH" >&2
  exit 127
fi

# Extract audio as mp3 directly to the desired filename
# Place -o before -- and URL so yt-dlp treats it as an option (fix for macOS)
cmd=("$YT_DLP_BIN" -x --audio-format mp3 --no-progress --newline --restrict-filenames -o "$desired" -- "$url")
echo "yt-dlp command: ${cmd[*]}"
if ! "${cmd[@]}"; then
  code=$?
  echo "yt-dlp failed with exit code $code" >&2
  exit $code
fi

# Fallback: if yt-dlp still produced a title-based file like "* [<id>].mp3", try to detect and rename to desired
if [[ ! -f "$desired" ]]; then
  # Try to find any mp3 that contains the desired basename (without extension) in square brackets
  base_noext="${desired%.mp3}"
  cand=$(ls -1 *"[$base_noext]"*.mp3 2>/dev/null | head -n1 || true)
  if [[ -n "${cand:-}" && -f "$cand" ]]; then
    mv -f "$cand" "$desired"
  fi
fi
