@echo off

echo Running yt-dlp with URL: %1
yt-dlp -x --audio-format mp3 %1 -o %2
if %errorlevel% neq 0 (
    echo yt-dlp failed with exit code %errorlevel%
    exit /b %errorlevel%
)

exit /b

