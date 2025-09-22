Install yt-dlp
  https://sourceforge.net/projects/yt-dlp.mirror/
Install ffmpeg
  
Set PATH to include the home directory where yt-dlp and ffmpeg are installed (see below)

java -DAWS_ACCESS_KEY_ID=<YOUR_ACCESS_KEY> -DAWS_REGION=<YOUR_REGION> -DAWS_SECRET_ACCESS_KEY=<YOUR_SECRET_ACCES_KEY> -DOPEN_AI_API_KEY=<YOUR_OPENAI_KEY> -DS3_BUCKET_NAME=<YOUR_S3_BUCKET> -DYTDL_HOME=<YOUR_YTDL_HOME> -jar inspiraai-0.0.1-SNAPSHOT.jar

macOS quick start
- Ensure yt-dlp and ffmpeg are on PATH. With Homebrew (Apple Silicon):
  - brew install yt-dlp ffmpeg
  - echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile && source ~/.zprofile
- Or put binaries in ~/bin and add: export PATH="$HOME/bin:$PATH" to ~/.zshrc then: source ~/.zshrc

Provided scripts (macOS):
- ./run-yt-dlp.sh <YouTube URL> <output_filename>
  Example: ./run-yt-dlp.sh "https://www.youtube.com/watch?v=ABC123" "ABC123.mp3"
  Extracts audio to mp3 and saves exactly to the provided filename.
- ./run-download-yt-video.sh <YouTube URL>
  Downloads and recodes to MP4 named by video id.
- ./run-yt-dlp-latest-video.sh <Channel or Playlist URL>
  Finds the latest video and downloads MP4.

Mark as executable if needed: chmod +x run-*.sh


Notes about YTDL_HOME and PATH
- The application now augments the PATH of the spawned scripts with the YTDL_HOME directory (if set), on all operating systems.
- Additionally, the app will attempt to auto-detect yt-dlp in common locations (e.g., /opt/homebrew/bin, /usr/local/bin) when YTDL_HOME is not set.
- This helps when running from IDEs or services where your interactive shell profile is not sourced.
- If yt-dlp or ffmpeg binaries live in YTDL_HOME, set it when launching the app, for example:
  - export YTDL_HOME="$HOME/bin"  # macOS/Linux
  - On Windows (PowerShell): $env:YTDL_HOME = "C:\\path\\to\\tools"
- You can still rely on your system PATH (e.g., via Homebrew). YTDL_HOME is a fallback to assist discovery.

Troubleshooting (Exit code 127: yt-dlp not found)
- Set YTDL_HOME to the folder that contains the yt-dlp binary:
  - Apple Silicon Homebrew: export YTDL_HOME="/opt/homebrew/bin"
  - Intel mac/Homebrew: export YTDL_HOME="/usr/local/bin"
  - Custom: export YTDL_HOME="$HOME/bin"
- Or pass via JVM: -DYTDL_HOME="/opt/homebrew/bin"
- Verify: which yt-dlp should show a path inside the directory you configured.
