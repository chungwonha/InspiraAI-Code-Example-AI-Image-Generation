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

# Usage: ./run-download-yt-video.sh <YouTube URL>

if [[ $# -lt 1 ]]; then
  echo "Usage: $(basename "$0") [YouTube URL]" >&2
  exit 1
fi

youtubeUrl="$1"
echo "YouTube URL: $youtubeUrl"

# Extract video ID from typical YouTube URLs (handles v=, youtu.be/, shorts/, and embed/ forms)
# Falls back to full URL filename sanitization if unknown format.
extract_video_id() {
  local url="$1"
  local id=""
  if [[ "$url" =~ v=([^&#]+) ]]; then
    id="${BASH_REMATCH[1]}"
  elif [[ "$url" =~ youtu\.be/([^?&#/]+) ]]; then
    id="${BASH_REMATCH[1]}"
  elif [[ "$url" =~ /shorts/([^?&#/]+) ]]; then
    id="${BASH_REMATCH[1]}"
  elif [[ "$url" =~ /embed/([^?&#/]+) ]]; then
    id="${BASH_REMATCH[1]}"
  fi
  printf '%s' "$id"
}

videoId="$(extract_video_id "$youtubeUrl")"
if [[ -z "$videoId" ]]; then
  # As a fallback, ask yt-dlp to print the ID
  if command -v yt-dlp >/dev/null 2>&1; then
    videoId="$(yt-dlp --get-id -- "$youtubeUrl" | head -n1)"
  fi
fi

if [[ -z "$videoId" ]]; then
  echo "Could not extract video ID from URL: $youtubeUrl" >&2
  exit 1
fi

echo "Video ID: $videoId"

# Ensure yt-dlp exists
if [[ -z "$YT_DLP_BIN" ]]; then
  echo "Error: yt-dlp not found in PATH. Set YTDL_HOME to the folder containing yt-dlp or install it (e.g., brew install yt-dlp)." >&2
  echo "Effective PATH: $PATH" >&2
  exit 127
fi

# Prefer mp4, recode if needed; pick best reasonable resolution/audio combo
"$YT_DLP_BIN" -S res,ext:mp4:m4a --recode mp4 -o "${videoId}.mp4" -- "$youtubeUrl"