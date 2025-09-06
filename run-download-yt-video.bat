@echo off
setlocal

REM Check if the YouTube URL is provided as an argument
if "%1"=="" (
    echo Usage: %0 [YouTube URL]
    exit /b 1
)

REM Define the YouTube URL from the first argument
set "youtubeUrl=%1"
echo YouTube URL: %youtubeUrl%

REM Extract the video ID from the URL
for /f "tokens=2 delims==&" %%i in ("%youtubeUrl:*v=%") do set "videoId=%%i"
echo Video ID: %videoId%
REM Download the video using yt-dlp with the video ID as the file name
yt-dlp -S res,ext:mp4:m4a --recode mp4 -o "%videoId%.mp4" %youtubeUrl%

endlocal