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

echo Latest Video: %latestVideo%

REM Extract the video ID from the filename
for /f "tokens=2 delims=[]" %%i in ("%latestVideo%") do set "videoId=%%i"

REM Extract the video ID and file extension from the filename
REM for %%i in ("%latestVideo%") do (
REM    set "videoId=%%~ni"
REM    set "fileExt=%%~xi"
REM )

echo Latest Video ID: %videoId%
echo Latest Video File Extension: %fileExt%

REM Remove the leading dot from the file extension
set "fileExt=%fileExt:~1%"

REM Define the video URL
set "videoUrl=https://www.youtube.com/watch?v=%videoId%"
echo Latest Video URL: %videoUrl%
REM Download the video using the extracted file extension
yt-dlp -S res,ext:%fileExt% --recode mp4 -o "%videoId%.%fileExt%" %videoUrl%

endlocal