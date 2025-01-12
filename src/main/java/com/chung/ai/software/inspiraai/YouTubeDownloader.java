package com.chung.ai.software.inspiraai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class YouTubeDownloader {
    public String downloadAudio(String videoUrl, String outputPath, String ytDlpHome) {
        try {
            // Parse the video ID from the URL
            String videoId = null;
            Pattern pattern = Pattern.compile("v=([\\w-]+)");
            Matcher matcher = pattern.matcher(new URL(videoUrl).getQuery());
            if (matcher.find()) {
                videoId = matcher.group(1);
            } else {
                throw new IllegalArgumentException("Invalid YouTube URL: " + videoUrl);
            }

            String downloadFileName = videoId + ".mp3";
            // Command to execute youtube-dlp
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "cmd.exe", "/c", "run-yt-dlp.bat", "\"" + videoUrl + "\"", "\""+downloadFileName+"\""
            );

            processBuilder.directory(new File(ytDlpHome));
            // Log the working directory and command
            log.info("Working directory: " + processBuilder.directory().getAbsolutePath());
            log.info("Command: " + String.join(" ", processBuilder.command()));

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String generatedFileName = null;

            while ((line = reader.readLine()) != null) {
                log.info("Output: " + line);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Failed to download audio. Exit code: " + exitCode);
            }

            return downloadFileName;
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error downloading audio: " + e.getMessage(), e);
        }
    }

    public String downloadLatestVideo(String channelUrl, String ytDlpHome) {
        try {
            // Command to execute the batch file with the channel URL as an argument
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "cmd.exe", "/c", "run-yt-dlp-latest-video.bat", "\"" + channelUrl + "\""
            );

            processBuilder.directory(new File(ytDlpHome));
            // Log the working directory and command
            log.info("Working directory: " + processBuilder.directory().getAbsolutePath());
            log.info("Command: " + String.join(" ", processBuilder.command()));

            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String latestVideoFileName = null;

            while ((line = reader.readLine()) != null) {
                log.info("Output: " + line);
                // Capture the downloaded file name from the output
                if (line.contains(".mp4")) {
                    latestVideoFileName = line.trim();
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.info("Exit code: " + exitCode);
                throw new RuntimeException("Failed to download the latest video. Exit code: " + exitCode);
            }
            if (latestVideoFileName.contains("already is in target format mp4")) {
                log.info("latestVideoFileName: {}", latestVideoFileName);
                latestVideoFileName = extractFileName(latestVideoFileName);
                log.info("Video is already in target format mp4. Extracting the file name from the error message.");
//                Pattern pattern = Pattern.compile("\"([^\"]+\\.mp4)\"");
//                Matcher matcher = pattern.matcher(latestVideoFileName);
//                if (matcher.find()) {
//                    log.info("matcher.group(1): {}", matcher.group(1));
//                    return matcher.group(1);
//                }
            }
            return latestVideoFileName;
        } catch (IOException | InterruptedException e) {
            if (e.getMessage().contains("already is in target format mp4")) {
                log.info("Video is already in target format mp4. Extracting the file name from the error message.");
                Pattern pattern = Pattern.compile("\"([^\"]+\\.mp4)\"");
                Matcher matcher = pattern.matcher(e.getMessage());
                if (matcher.find()) {
                    return matcher.group(1);
                } else {
                    throw new RuntimeException("Error extracting file name from the error message: " + e.getMessage(), e);
                }
            } else {
                throw new RuntimeException("Error downloading the latest video: " + e.getMessage(), e);
            }
        }
    }

    public String extractFileName(String logMessage) {
        Pattern pattern = Pattern.compile("\\[VideoConvertor\\] Not converting media file \"([^\"]+\\.mp4)\"; already is in target format mp4");
        Matcher matcher = pattern.matcher(logMessage);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new IllegalArgumentException("Log message does not contain the expected format.");
        }
    }
}