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

# Usage: ./run-yt-dlp-latest-video.sh <YouTube Channel Playlist URL>

if [[ $# -lt 1 ]]; then
  echo "Usage: $(basename "$0") [YouTube Channel Playlist URL]" >&2
  exit 1
fi

channelUrl="$1"

if [[ -z "$YT_DLP_BIN" ]]; then
  echo "Error: yt-dlp not found in PATH. Set YTDL_HOME to the folder containing yt-dlp or install it (e.g., brew install yt-dlp)." >&2
  echo "Effective PATH: $PATH" >&2
  exit 127
fi

# Get the latest video ID using yt-dlp. We ask for the ID explicitly.
videoId="$("$YT_DLP_BIN" --playlist-items 1 --get-id -- "$channelUrl" | head -n1)"

if [[ -z "$videoId" ]]; then
  echo "Could not find latest video for: $channelUrl" >&2
  exit 1
fi

echo "Latest Video ID: $videoId"
videoUrl="https://www.youtube.com/watch?v=${videoId}"
echo "Latest Video URL: $videoUrl"

# Determine the best extension by asking yt-dlp for an mp4 output, but if you want to strictly mirror the .bat behavior that depends on printed filename,
# we can try to fetch a filename and parse extension. We'll prefer mp4 as a safe default.
# Try to get planned filename for mp4 first; if none, use title.ext from default template.
filename="${videoId}.mp4"

# If you prefer exact behavior similar to Windows script, uncomment the following block to print filename and derive extension.
# latestFilename="$(yt-dlp --playlist-items 1 --print filename -- "$channelUrl" | head -n1 || true)"
# if [[ -n "$latestFilename" ]]; then
#   ext="${latestFilename##*.}"
#   # guard against unparsed strings
#   if [[ "$ext" != "$latestFilename" && -n "$ext" ]]; then
#     filename="${videoId}.${ext}"
#   fi
# fi

# Download with sort preference and recode to mp4 to ensure compatibility
"$YT_DLP_BIN" -S res,ext:mp4:m4a --recode mp4 -o "$filename" -- "$videoUrl"