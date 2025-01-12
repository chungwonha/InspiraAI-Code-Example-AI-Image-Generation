@echo off
setlocal

REM Check if the channel URL is provided as an argument
if "%1"=="" (
    echo Usage: %0 [YouTube Channel Playlist URL]
    exit /b 1
)

REM Define the YouTube channel URL from the first argument
set "channelUrl=%1"

REM Get the latest video filename
for /f "tokens=*" %%i in ('yt-dlp --playlist-items 1 --print filename %channelUrl%') do set "latestVideo=%%i"

REM Extract the video ID from the filename
for /f "tokens=2 delims=[]" %%i in ("%latestVideo%") do set "videoId=%%i"

REM Define the video URL
set "videoUrl=https://www.youtube.com/watch?v=%videoId%"

REM Download the video
yt-dlp -S res,ext:mp4:m4a --recode mp4 -o "%videoId%.mp4" %videoUrl%

endlocal